// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import api.location.LocationProvider;
import api.location.LocationException;
import api.location.LocationListener;
import api.location.Location;
import api.location.QualifiedCoordinates;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.ui.Desktop;
import cz.kruch.track.util.NmeaParser;
import cz.kruch.track.util.Logger;
import cz.kruch.j2se.io.BufferedInputStream;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Ticker;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.Vector;
import java.util.Timer;
import java.util.TimerTask;

public class Jsr82LocationProvider extends StreamReadingLocationProvider implements Runnable {
    private static final Logger log = new Logger("Bluetooth");

    private static final int ACTION_GO      = 0;
    private static final int ACTION_REFRESH = 1;
    private static final int ACTION_CANCEL  = 2;

    private static final int WATCHER_PERIOD = 60 * 1000;

    private Display display;
    private Displayable previous;

    private Thread thread;

    private String btspp = null;
    private boolean go = false;

    private Timer watcher;
    private Object sync = new Object();
    private long timestamp;
    private int state;

    private Observer tracklog;

    public Jsr82LocationProvider(Display display) {
        super(Config.LOCATION_PROVIDER_JSR82);
        this.display = display;
        this.previous = display.getCurrent();
        this.timestamp = 0;
        this.state = LocationProvider.TEMPORARILY_UNAVAILABLE;
    }

    public void run() {
        try {
            // start gpx
            getListener().providerStateChanged(this, LocationProvider._STARTING);

            // start watcher
            startWatcher();

            // start tracklog - if set and NMEA format selected
            if (Config.getSafeInstance().isTracklogsOn() && Config.TRACKLOG_FORMAT_NMEA.equals(Config.getSafeInstance().getTracklogsFormat())) {
                tracklog = new Observer();
                setObserver(tracklog.getWriter());
            }

            // GPS
            gps();

        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                // probably stop request
            } else {
                // record exception
                setException(e instanceof LocationException ? (LocationException) e : new LocationException(e));
            }
        } finally {

            // stop tracklog
            if (tracklog != null) {
                try {
                    tracklog.close();
                } catch (IOException e1) {
                    // never happens
                }
                tracklog = null;
                setObserver(null);
            }

            // stop watcher
            if (watcher != null) {
                watcher.cancel();
                watcher = null;
            }

            // update status
            notifyListener(LocationProvider.OUT_OF_SERVICE);
        }
    }

    public int start() throws LocationException {
        // BT turned on?
        try {
            javax.bluetooth.LocalDevice.getLocalDevice();
        } catch (javax.bluetooth.BluetoothStateException e) {
            throw new LocationException("Bluetooth radio disabled?");
        }

        // show BT device browser
        display.setCurrent(new Discoverer());

        return LocationProvider.TEMPORARILY_UNAVAILABLE;
    }

    public void stop() throws LocationException {
        if (go) {
            go = false;
            try {
                thread.interrupt();
                thread.join();
            } catch (InterruptedException e) {
            }
            thread = null;
        }
    }

    public void setLocationListener(LocationListener listener, int interval, int timeout, int maxAge) {
        setListener(listener);
    }

    public Object getImpl() {
        return null;
    }

    private void startWatcher() {
        watcher = new Timer();
        watcher.schedule(new TimerTask() {
            public void run() {
                boolean notify = false;
                synchronized (sync) {
                    if (System.currentTimeMillis() > (timestamp + WATCHER_PERIOD)) {
                        state = LocationProvider.TEMPORARILY_UNAVAILABLE;
                        notify = true;
                    }
                }
                if (notify) {
                    notifyListener(LocationProvider.TEMPORARILY_UNAVAILABLE);
                }
            }
        }, WATCHER_PERIOD, WATCHER_PERIOD); // delay = period = 1 min
    }

    private void gps() throws IOException {
        // open connection
        StreamConnection connection = (StreamConnection) Connector.open(btspp, Connector.READ);
        InputStream in = null;

        try {
            // open stream for reading
            in = new BufferedInputStream(connection.openInputStream(), 512);

            // read NMEA until error or stop request
            for (; go ;) {

                // read GGA
                String ggaSentence = nextSentence(in, HEADER_GGA);
                if (ggaSentence == null) {
                    break;
                }

                // parse GGA
                NmeaParser.Record rec = null;
                try {
                    rec = NmeaParser.parseGGA(ggaSentence);
                } catch (Exception e) {
                    if (log.isEnabled()) log.error("corrupted record: " + ggaSentence + "\n" + e.toString());
                    continue;
                }

                // prepare location info
                QualifiedCoordinates coordinates = new QualifiedCoordinates(rec.lat, rec.lon, rec.altitude);
                Location location = new Location(coordinates, rec.timestamp, rec.fix, rec.sat);

                // is position valid?
                if (rec.fix > 0) {

                    // read and parse RMC
                    try {
                        String rmcSentence = nextSentence(in, HEADER_RMC);
                        if (rmcSentence == null) {
                            break;
                        }
                        NmeaParser.Record rmc = NmeaParser.parseRMC(rmcSentence);
                        if (rmc.timestamp == rec.timestamp) {
                            location.setCourse(rmc.angle);
                            location.setSpeed(rmc.speed);
                        }
                    } catch (Exception e) {
                        // ignore
                    }

                    // fix state - we may be in TEMPORARILY_UNAVAILABLE state
                    boolean notify = false;
                    synchronized (sync) {
                        if (state != LocationProvider.AVAILABLE) {
                            state = LocationProvider.AVAILABLE;
                            notify = true;
                        }
                        timestamp = System.currentTimeMillis();
                    }
                    if (notify) {
                        notifyListener(LocationProvider.AVAILABLE);
                    }
                }

                // send new location
                notifyListener(location);
            }
        } finally {

            // close anyway
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }

            // close anyway
            try {
                connection.close();
            } catch (IOException e) {
            }
        }
    }

    private class Observer {
        private FileConnection fc;
        private OutputStreamWriter writer;

        public Observer() {
            String path = Config.getSafeInstance().getTracklogsDir() + "/nmea-" + Long.toString(System.currentTimeMillis()) + ".log";
            try {
                fc = (FileConnection) Connector.open(path, Connector.WRITE);
                fc.create();
                writer = new OutputStreamWriter(fc.openOutputStream());
            } catch (IOException e) {
                Desktop.showError(display, "Failed to start tracklog", e, null);
            }
        }

        public OutputStreamWriter getWriter() {
            return writer;
        }

        public void close() throws IOException {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                }
                writer = null;
            }
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException e) {
                }
                fc = null;
            }
        }
    }

    private class Discoverer extends List implements javax.bluetooth.DiscoveryListener, CommandListener, Runnable {
        // intentionally not static
        private javax.bluetooth.UUID[] uuidSet = {
            new javax.bluetooth.UUID(0x1101),
            new javax.bluetooth.UUID(0x0003)
        };

        private javax.bluetooth.DiscoveryAgent agent;
        private javax.bluetooth.RemoteDevice device;
        private Vector devices = new Vector();
        private String url;
        private LocationException exception;
        private int retCode;
        private int transactionID;

        private boolean inquiryCompleted;

        private Command cmdBack = new Command("Cancel", Command.BACK, 1);
        private Command cmdRefresh = new Command("Refresh", Command.SCREEN, List.SELECT_COMMAND.getPriority() + 1);

        public Discoverer() {
            super("DeviceSelection", List.IMPLICIT);
            setCommandListener(this);
            (new Thread(this)).start();
        }

        public void run() {
            int action = ACTION_REFRESH;

            while (action == ACTION_REFRESH) {
                try {
                    action = go();
                } catch (javax.bluetooth.BluetoothStateException e) {
                    Desktop.showError(display, "Device discovery failed", e, null);
                } catch (LocationException e) { // no devices found
                    Desktop.showWarning(display, e.getMessage(), null, null);
                }
            }

            // clear 'status' exception
            setException(null);

            // restore screen
            display.setCurrent(previous);

            // good to go
            if (action == ACTION_GO) {
                go = true;
                btspp = url;
                thread = new Thread(Jsr82LocationProvider.this);
                thread.start();
            } else {
                notifyListener(LocationProvider.OUT_OF_SERVICE);
            }
        }

        public int go() throws LocationException, javax.bluetooth.BluetoothStateException {
            // reset
            deleteAll();
            devices.removeAllElements();
            exception = null;
            url = null;
            device = null;
            retCode = -1;
            transactionID = 0;
            inquiryCompleted = false;

            // setup commands
            setupCommands(false);

            // update UI
            setTicker(new Ticker("Looking for devices..."));

            // start inquiry
            agent = javax.bluetooth.LocalDevice.getLocalDevice().getDiscoveryAgent();
            agent.startInquiry(javax.bluetooth.DiscoveryAgent.GIAC, this);

            // wait until device and service is selected
            while (device == null && retCode == -1) {

                // wait for device selection
                getDevice();

                // device selected?
                if (device != null) {

                    // update UI
                    setTitle("ServiceSearch");
                    setTicker(new Ticker("Searching service..."));
                    setupCommands(false);

                    // search for service
                    try {
                        // start search
                        transactionID = agent.searchServices(null, uuidSet, device, this);

                        // wait for btspp URL
                        getURL();

                        // if we have url, quit the loop
                        if (url != null) {
                            return ACTION_GO;
                        }
                    } catch (javax.bluetooth.BluetoothStateException e) {
                        Desktop.showError(display, "Service search failed", e, null);
                    } catch (LocationException e) { // no service found at selected device, select another device
                        Desktop.showWarning(display, e.getMessage(), null, null);
                    } finally {

                        // cancel search (if started)
                        if (transactionID > 0) {
                            agent.cancelServiceSearch(transactionID);
                        }

                        // return to device selection
                        exception = null;
                        device = null;
                        retCode = -1;
                        transactionID = 0;
                        setTitle("DeviceSelection");
                        setupCommands(true);
                    }
                }
            }

            return retCode;
        }

        private void setupCommands(boolean ready) {
            if (ready) {
                removeCommand(cmdBack);
                removeCommand(cmdRefresh);
                removeCommand(List.SELECT_COMMAND);
                addCommand(cmdBack);
                addCommand(cmdRefresh);
                addCommand(List.SELECT_COMMAND);
            } else {
                removeCommand(cmdBack);
                removeCommand(cmdRefresh);
                removeCommand(List.SELECT_COMMAND);
                addCommand(cmdBack);
            }
        }

        private javax.bluetooth.RemoteDevice getDevice() throws LocationException {
            synchronized (this) {
                while (device == null && exception == null && retCode == -1) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                    }
                }
            }

            if (exception == null) {
                return device;
            }

            throw exception;
        }

        private String getURL() throws LocationException {
            synchronized (this) {
                while (url == null && exception == null && retCode == -1) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                    }
                }
            }

            if (exception == null) {
                return url;
            }

            throw exception;
        }

        public void deviceDiscovered(javax.bluetooth.RemoteDevice remoteDevice, javax.bluetooth.DeviceClass deviceClass) {
            devices.addElement(remoteDevice);
            append("#" + remoteDevice.getBluetoothAddress(), null); // show bt adresses just to signal we are finding any
        }

        public void servicesDiscovered(int i, javax.bluetooth.ServiceRecord[] serviceRecords) {
                try {
                    synchronized (this) {
                        url = serviceRecords[0].getConnectionURL(javax.bluetooth.ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
                        notify();
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                } catch (NullPointerException e) {
            }
        }

        public void serviceSearchCompleted(int transID, int respCode) {
            setTicker(null);

            synchronized (this) {
                if (url == null) {
                    String respMsg;

                    switch (respCode) {
                        case SERVICE_SEARCH_COMPLETED:
                            respMsg = "COMPLETED";
                            break;
                        case SERVICE_SEARCH_DEVICE_NOT_REACHABLE:
                            respMsg = "DEVICE NOT REACHABLE";
                            break;
                        case SERVICE_SEARCH_ERROR:
                            respMsg = "ERROR";
                            break;
                        case SERVICE_SEARCH_NO_RECORDS:
                            respMsg = "NO RECORDS";
                            break;
                        case SERVICE_SEARCH_TERMINATED:
                            respMsg = "TERMINATED";
                            break;
                        default:
                            respMsg = "UNKNOWN";
                    }

                    exception = new LocationException("Service not found (" + respMsg + ")");
                }

                notify();
            }
        }

        public void inquiryCompleted(int discType) {
            inquiryCompleted = true;

            setTicker(null); // stop "Looking for devices"
            deleteAll(); // delete temporary items (bt adresses)

            // make all commands available
            setupCommands(true);

            if (devices.size() == 0) {
                synchronized (this) {
                    exception = new LocationException("No devices discovered");
                    notify();
                }
            } else {
                setTicker(new Ticker("Resolving names"));
                for (int N = devices.size(), i = 0; i < N; i++) {
                    String name;
                    javax.bluetooth.RemoteDevice remoteDevice = ((javax.bluetooth.RemoteDevice) devices.elementAt(i));
                    try {
                        name = remoteDevice.getFriendlyName(true);
                    } catch (IOException e) {
                        name = "#" + remoteDevice.getBluetoothAddress();
                    }
                    append(name, null);
                }
                setTicker(null);
            }
        }

        public void commandAction(Command command, Displayable displayable) {
            if (command == List.SELECT_COMMAND) {
                /*
                 * device selection
                 */
                displayable.setTicker(null);
                if (device == null) {
                    agent.cancelInquiry(this);
                    synchronized (this) {
                        device = (javax.bluetooth.RemoteDevice) devices.elementAt(getSelectedIndex());
                        notify();
                    }
                }
            } else if (command.getCommandType() == Command.BACK) {
                if (device == null) {
                    /*
                     * cancel device discovery and quit device selection at all
                     */
                    agent.cancelInquiry(this);
                    if (inquiryCompleted  || (devices.size() == 0)) {
                        synchronized (this) {
                            retCode = ACTION_CANCEL;
                            notify();
                        }
                    }
                } else if (transactionID > 0) {
                    /*
                     * cancel service, offer device selection
                     */
                    agent.cancelServiceSearch(transactionID);
                    setTitle("DeviceSelection");
                    setupCommands(true);
                }
            } else if ("Refresh".equals(command.getLabel())) {
                /*
                 * refresh device selection
                 */
                agent.cancelInquiry(this);
                synchronized (this) {
                    retCode = ACTION_REFRESH;
                    notify();
                }
            }
        }
    }
}
