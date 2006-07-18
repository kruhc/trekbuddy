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
import cz.kruch.track.event.Callback;
import cz.kruch.j2se.io.BufferedInputStream;
import cz.kruch.j2se.io.BufferedOutputStream;

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
import java.io.OutputStream;
import java.util.Vector;
import java.util.Timer;
import java.util.TimerTask;

public class Jsr82LocationProvider extends StreamReadingLocationProvider implements Runnable {
    private static final int WATCHER_PERIOD = 60 * 1000;

    private Display display;
    private Displayable previous;
    private Callback recordingCallback;

    private Thread thread;

    private String btspp = null;
    private boolean go = false;

    private Timer watcher;
    private Object sync = new Object();
    private long timestamp;
    private int state;

    private FileConnection nmeaFc;
    private OutputStream nmeaObserver;

    public Jsr82LocationProvider(Display display, Callback recordingCallback) {
        super(Config.LOCATION_PROVIDER_JSR82);
        this.display = display;
        this.previous = display.getCurrent();
        this.recordingCallback = recordingCallback;
        this.timestamp = 0;
        this.state = LocationProvider.TEMPORARILY_UNAVAILABLE;
    }

    public void run() {
        try {
            // start gpx
            getListener().providerStateChanged(this, LocationProvider._STARTING);

            // start watcher
            startWatcher();

            // start NMEA log
            startNmeaLog();

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

            // stop NMEA log
            stopNmeaLog();

            // stop watcher
            stopWatcher();

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

        // start BT discovery
        (new Discoverer()).start();

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
                    notifyListener(state);
                }
            }
        }, WATCHER_PERIOD, WATCHER_PERIOD); // delay = period = 1 min
    }

    private void stopWatcher() {
        if (watcher != null) {
            watcher.cancel();
            watcher = null;
        }
    }

    private void startNmeaLog() {
        if (Config.getSafeInstance().isTracklogsOn() && Config.TRACKLOG_FORMAT_NMEA.equals(Config.getSafeInstance().getTracklogsFormat())) {
            String path = Config.getSafeInstance().getTracklogsDir() + "/nmea-" + Long.toString(System.currentTimeMillis()) + ".log";
            try {
                nmeaFc = (FileConnection) Connector.open(path, Connector.WRITE);
                nmeaFc.create();
                nmeaObserver = new BufferedOutputStream(nmeaFc.openOutputStream(), 512);

                // set stream 'observer'
                setObserver(nmeaObserver);

                // signal recording has started
                recordingCallback.invoke(new Integer(1), null);

            } catch (Throwable t) {
                Desktop.showError(display, "Failed to start NMEA log", t, null);
            }
        }
    }

    public void stopNmeaLog() {

        // signal recording is stopping
        recordingCallback.invoke(new Integer(0), null);

        // clear stream 'observer'
        setObserver(null);

        if (nmeaObserver != null) {
            try {
                nmeaObserver.close();
            } catch (IOException e) {
            }
            nmeaObserver = null;
        }
        if (nmeaFc != null) {
            try {
                nmeaFc.close();
            } catch (IOException e) {
            }
            nmeaFc = null;
        }
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
                NmeaParser.Record gga;
                try {
                    gga = NmeaParser.parseGGA(ggaSentence.toCharArray());
                } catch (Throwable t) {
                    // ignore
                    continue;
                }

                // create location instance
                Location location = new Location(new QualifiedCoordinates(gga.lat, gga.lon, gga.altitude),
                                                 gga.timestamp, gga.fix, gga.sat, gga.hdop);

                boolean notify = false;

                // is position valid?
                if (gga.fix > 0) {

                    // read RMC
                    String rmcSentence = nextSentence(in, HEADER_RMC);
                    if (rmcSentence == null) {
                        break;
                    }

                    // parse RMC
                    try {
                        NmeaParser.Record rmc = NmeaParser.parseRMC(rmcSentence.toCharArray());
                        if (rmc.timestamp == gga.timestamp) {
                            location.setCourse(rmc.angle);
                            location.setSpeed(rmc.speed);
                        }
                    } catch (Throwable t) {
                        // ignore
                    }

                    // fix state - we may be in TEMPORARILY_UNAVAILABLE state
                    synchronized (sync) {
                        if (state != LocationProvider.AVAILABLE) {
                            state = LocationProvider.AVAILABLE;
                            notify = true;
                        }
                        timestamp = System.currentTimeMillis();
                    }
                } else {
                    synchronized (sync) {
                        if (state != LocationProvider.TEMPORARILY_UNAVAILABLE) {
                            state = LocationProvider.TEMPORARILY_UNAVAILABLE;
                            notify = true;
                        }
                    }
                }

                // notify about state, if necessary
                if (notify) {
                    notifyListener(state);
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

    private class Discoverer extends List implements javax.bluetooth.DiscoveryListener, CommandListener {
        // intentionally not static
        private javax.bluetooth.UUID[] uuidSet = {
            new javax.bluetooth.UUID(0x1101),
            new javax.bluetooth.UUID(0x0003)
        };

        private javax.bluetooth.DiscoveryAgent agent;
        private javax.bluetooth.RemoteDevice device;
        private Vector devices = new Vector();
        private String url;
        private int transactionID;
        private boolean inquiryCompleted;

        private boolean cancel = false;

        private Command cmdBack = new Command("Cancel", Command.BACK, 1);
        private Command cmdRefresh = new Command("Refresh", Command.SCREEN, List.SELECT_COMMAND.getPriority() + 1);

        public Discoverer() {
            super("DeviceSelection", List.IMPLICIT);
            this.setCommandListener(this);
        }

        public void start() throws LocationException {
            display.setCurrent(this);
            try {
                goDevices();
            } catch (LocationException e) {
                display.setCurrent(previous);
                throw e;
            }
        }

        private void letsGo(boolean ok) {
            // restore screen anyway
            display.setCurrent(previous);

            // good to go or not
            if (ok) {
                go = true;
                btspp = url;
                thread = new Thread(Jsr82LocationProvider.this);
                thread.start();
            } else {
                notifyListener(LocationProvider.OUT_OF_SERVICE);
            }
        }

        private void goDevices() throws LocationException {
            // reset UI and state
            deleteAll();
            devices.removeAllElements();
            url = null;
            device = null;
            inquiryCompleted = false;
            cancel = false;

            // update UI
            setTicker(new Ticker("Looking for devices..."));
            setupCommands(false);

            // start inquiry
            try {
                agent = javax.bluetooth.LocalDevice.getLocalDevice().getDiscoveryAgent();
                agent.startInquiry(javax.bluetooth.DiscoveryAgent.GIAC, this);
            } catch (javax.bluetooth.BluetoothStateException e) {
                throw new LocationException(e);
            }
        }

        private void showDevices() {
            // partial reset
            device = null;

            // update UI
            setTicker(null);
            setTitle("DeviceSelection");
            setupCommands(true);
        }

        private void goServices() {
            // update UI
            setTitle("ServiceSearch");
            setTicker(new Ticker("Searching service..."));
            setupCommands(false);

            // start search
            try {
                transactionID = agent.searchServices(null, uuidSet, device, this);
            } catch (javax.bluetooth.BluetoothStateException e) {
                Desktop.showError(display, "Service search failed", e, null);
                showDevices();
            }
        }

        private void setupCommands(boolean ready) {
            if (ready) {
                removeCommand(cmdBack);
                removeCommand(cmdRefresh);
                removeCommand(List.SELECT_COMMAND);
                addCommand(cmdBack);
                addCommand(cmdRefresh);
                if (devices.size() > 0) {
                    addCommand(List.SELECT_COMMAND);
                }
            } else {
                removeCommand(cmdBack);
                removeCommand(cmdRefresh);
                removeCommand(List.SELECT_COMMAND);
                addCommand(cmdBack);
            }
        }

        public void deviceDiscovered(javax.bluetooth.RemoteDevice remoteDevice, javax.bluetooth.DeviceClass deviceClass) {
            devices.addElement(remoteDevice);
            append("#" + remoteDevice.getBluetoothAddress(), null); // show bt adresses just to signal we are finding any
        }

        public void servicesDiscovered(int i, javax.bluetooth.ServiceRecord[] serviceRecords) {
                try {
                    url = serviceRecords[0].getConnectionURL(javax.bluetooth.ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
                    agent.cancelServiceSearch(transactionID);
/* called from serviceSearchCompleted
                    letsGo(true);
*/
                } catch (ArrayIndexOutOfBoundsException e) {
                } catch (NullPointerException e) {
            }
        }

        public void serviceSearchCompleted(int transID, int respCode) {
            transactionID = 0;

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

                if (respCode != SERVICE_SEARCH_TERMINATED) {
                    Desktop.showWarning(display, "Service not found (" + respMsg + ")",
                                        null, null);
                    // update UI
                    setTicker(null);

                    // offer device selection
                    showDevices();
                }
            } else {
                letsGo(true);
            }
        }

        public void inquiryCompleted(int discType) {
            inquiryCompleted = true;

            // update UI
            setTicker(null);
            deleteAll();
            setupCommands(true);

            if (devices.size() == 0) {
                if (cancel) {
                    letsGo(false);
                } else {
                    Desktop.showError(display, "No devices discovered", null, null);
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
            if (command == List.SELECT_COMMAND) { /* device selection */
                device = (javax.bluetooth.RemoteDevice) devices.elementAt(getSelectedIndex());
                goServices();
            } else if (command.getCommandType() == Command.BACK) {
                cancel = true;
                if (transactionID > 0) {
                    agent.cancelServiceSearch(transactionID);
                }
                if (inquiryCompleted == false) {
                    agent.cancelInquiry(this);
                }
                if (device == null) { /* quit BT explorer */
                    if (inquiryCompleted) {
                        letsGo(false);
                    }
                } else { /* offer device selection */
                    showDevices();
                }
            } else if ("Refresh".equals(command.getLabel())) { /* refresh device list */
                try {
                    goDevices();
                } catch (LocationException e) {
                    Desktop.showError(display, "Unable to restart discovery",
                                      e, null);
                }
            }
        }
    }
}
