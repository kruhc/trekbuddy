// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import api.location.LocationProvider;
import api.location.LocationException;
import api.location.LocationListener;
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
import java.io.OutputStream;
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
    private volatile LocationListener listener;
    private volatile LocationException exception;

    private String url = null;
    private boolean go = false;

    private Timer watcher;
    private long timestamp = 0;
    private int state = -1;

    private Observer tracklog;

    public Jsr82LocationProvider(Display display) {
        super(Config.LOCATION_PROVIDER_JSR82);
        this.display = display;
        this.previous = display.getCurrent();
    }

    public LocationException getException() {
        return exception;
    }

    public void run() {
        try {
            // show device/service discoverer
            Discoverer discoverer = new Discoverer();
            display.setCurrent(discoverer);

            // start discovery
            int go = discoverer.go();
            while (go == ACTION_REFRESH) {
                go = discoverer.go();
            }

            // restore screen
            display.setCurrent(previous);

            // start if ready
            if (go == ACTION_GO) {
                // update status
                state = LocationProvider.AVAILABLE;
                notifyListener(LocationProvider.AVAILABLE);

                // start watcher
                startWatcher();

                // start tracklog
                if (Config.getSafeInstance().isTracklogsOn()) {
                    observer = tracklog = new Observer();
                }

                // GPS
                gps();

            } else { // Cancel
                // TODO better code? signal Cancel through listener? or event?
                notifyListener(LocationProvider.OUT_OF_SERVICE);
            }

        } catch (Exception e) {
            // record exception
            exception = e instanceof LocationException ? (LocationException) e : new LocationException(e);

            // stop tracklog
            if (tracklog != null) {
                try {
                    tracklog.close();
                } catch (IOException e1) {
                    // never happens
                }
                observer = tracklog = null;
            }

            // stop watcher
            if (watcher != null) {
                watcher.cancel();
                watcher = null;
            }

            // restore screen
            display.setCurrent(previous);

            // update status
            notifyListener(LocationProvider.OUT_OF_SERVICE);
        }
    }

    public void start() throws LocationException {
        // BT turned on?
        try {
            javax.bluetooth.LocalDevice.getLocalDevice();
        } catch (javax.bluetooth.BluetoothStateException e) {
            throw new LocationException("Bluetooth radio disabled?");
        }

        go = true;
        thread = new Thread(this);
        thread.start();
    }

    public void stop() throws LocationException {
        go = false;
        try {
            thread.interrupt();
            thread.join();
        } catch (InterruptedException e) {
        }
        thread = null;
    }

    public void setLocationListener(LocationListener listener, int interval, int timeout, int maxAge) {
        this.listener = listener;
    }

    public Object getImpl() {
        return null;
    }

    private void startWatcher() {
        watcher = new Timer();
        watcher.schedule(new TimerTask() {
            public void run() {
                if (log.isEnabled()) log.debug("verify timestamp");
                long t = System.currentTimeMillis();
                if (t > (timestamp + WATCHER_PERIOD)) {
                    state = LocationProvider.TEMPORARILY_UNAVAILABLE;
                    notifyListener(LocationProvider.TEMPORARILY_UNAVAILABLE);
                }
            }
        }, WATCHER_PERIOD, WATCHER_PERIOD); // delay = period = 1 min
    }

    private void gps() throws IOException {
        // open connection
        StreamConnection connection = (StreamConnection) Connector.open(url, Connector.READ);
        InputStream in = null;

        try {
            // open stream for reading
            in = new BufferedInputStream(connection.openInputStream(), 128);

            // read NMEA until error or stop request
            for (; go ;) {

                // read GGA
                String nmea = nextGGA(in);
                if (nmea == null) {
                    listener.providerStateChanged(this, LocationProvider.OUT_OF_SERVICE);
                    break;
                }

                // fix state - we may be in TEMPORARILY_UNAVAILABLE state
                if (state != LocationProvider.AVAILABLE) {
                    if (log.isEnabled()) log.debug("refreshing state");
                    listener.providerStateChanged(Jsr82LocationProvider.this,
                                                  LocationProvider.AVAILABLE);
                    state = LocationProvider.AVAILABLE;
                }

                // upate timestamp
                timestamp = System.currentTimeMillis();

                // send new location
                try {
                    listener.locationUpdated(this, NmeaParser.parse(nmea));
                } catch (Exception e) {
                    if (log.isEnabled()) log.error("corrupted record: " + nmea + "\n" + e.toString());
                }
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

    private void notifyListener(final int newState) {
        if (listener != null) {
            display.callSerially(new Runnable() {
                public void run() {
                    listener.providerStateChanged(Jsr82LocationProvider.this, newState);
                }
            });
        }
    }

    private class Observer extends OutputStream {
        private FileConnection tracklogFileConnection;
        private OutputStream tracklogOutputStream;

        public Observer() {
            String path = Config.getSafeInstance().getTracklogsDir() + "/nmea-" + Long.toString(System.currentTimeMillis()) + ".log";
            try {
                tracklogFileConnection = (FileConnection) Connector.open(path, Connector.WRITE);
                tracklogFileConnection.create();
                tracklogOutputStream = tracklogFileConnection.openOutputStream();
            } catch (IOException e) {
                Desktop.showError(display, "Failed to start tracklog", e);
            }
        }

        public void write(int i) throws IOException {
            if (tracklogOutputStream != null) {
                tracklogOutputStream.write(i);
            }
        }

        public void write(byte[] bytes, int i, int i1) throws IOException {
            if (tracklogOutputStream != null) {
                tracklogOutputStream.write(bytes, i, i1);
            }
        }

        public void flush() throws IOException {
            if (tracklogOutputStream != null) {
                tracklogOutputStream.flush();
            }
        }

        public void close() throws IOException {
            if (tracklogOutputStream != null) {
                try {
                    tracklogOutputStream.close();
                } catch (IOException e) {
                }
                tracklogOutputStream = null;
            }
            if (tracklogFileConnection != null) {
                try {
                    tracklogFileConnection.close();
                } catch (IOException e) {
                }
                tracklogFileConnection = null;
            }
        }
    }

    private class Discoverer extends List implements javax.bluetooth.DiscoveryListener, CommandListener {
        // intentionally not static
        private javax.bluetooth.UUID[] uuidSet = { new javax.bluetooth.UUID(0x1101) };

        private Vector devices = new Vector();
        private volatile LocationException exception;
        private volatile int retCode;
        private javax.bluetooth.DiscoveryAgent agent;
        private javax.bluetooth.RemoteDevice device;

        private Command cmdBack = new Command("Cancel", Command.BACK, 1);
        private Command cmdRefresh = new Command("Refresh", Command.SCREEN, List.SELECT_COMMAND.getPriority() + 1);

        public Discoverer() {
            super("DeviceSelection", List.IMPLICIT);
            setCommandListener(this);
        }

        public int go() throws LocationException, javax.bluetooth.BluetoothStateException {
            // reset
            deleteAll();
            devices = new Vector();
            url = null;
            device = null;
            retCode = -1;

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
                device = getDevice();

                // device is selected (-1), otherwise CANCEL or REFRESH
                if (retCode == -1) {

                    // update UI
                    next();
                    setupCommands(false);

                    // search fore service
                    int transactionID = 0;
                    try {
                        transactionID = agent.searchServices(null, uuidSet, device, this);
                        url = getURL();
                        if (url != null) {
                            retCode = ACTION_GO;
                        }
                    } catch (javax.bluetooth.BluetoothStateException e) {
                        Desktop.showError(display, "Service search failed", e);
                    } catch (LocationException e) { // no service found at selected device, select another device
                        Desktop.showWarning(display, e.getMessage(), null);
                    } finally {
                        // cancel search (if started)
                        if (transactionID > 0) {
                            agent.cancelServiceSearch(transactionID);
                        }

                        // return to device selection
                        exception = null;
                        device = null;
                        previous();
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

        private void next() {
            setTitle("ServiceSearch");
            setTicker(new Ticker("Searching service..."));
        }

        private void previous() {
            setTitle("DeviceSelection");
        }

        private String getURL() throws LocationException {
            while (url == null && exception == null && retCode == -1) {
                Thread.yield();
            }

            if (exception == null) {
                return url;
            }

            throw exception;
        }

        private javax.bluetooth.RemoteDevice getDevice() throws LocationException {
            while (device == null && exception == null && retCode == -1) {
                Thread.yield();
            }

            if (exception == null) {
                return device;
            }

            throw exception;
        }

        public void deviceDiscovered(javax.bluetooth.RemoteDevice remoteDevice, javax.bluetooth.DeviceClass deviceClass) {
            devices.addElement(remoteDevice);
            append("#" + remoteDevice.getBluetoothAddress(), null); // show bt adresses just to signal we are finding any
        }

        public void servicesDiscovered(int i, javax.bluetooth.ServiceRecord[] serviceRecords) {
            try {
                url = serviceRecords[0].getConnectionURL(javax.bluetooth.ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
            } catch (ArrayIndexOutOfBoundsException e) {
            } catch (NullPointerException e) {
            }
        }

        public void serviceSearchCompleted(int transID, int respCode) {
            setTicker(null);

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
        }

        public void inquiryCompleted(int discType) {
            setTicker(null);
            deleteAll(); // delete temporary items (bt adresses)

            // all commands available
            setupCommands(true);

            if (devices.size() == 0) {
                exception = new LocationException("No devices discovered");
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
                displayable.setTicker(null);
                if (device == null) {
                    agent.cancelInquiry(this);
                    device = (javax.bluetooth.RemoteDevice) devices.elementAt(getSelectedIndex());
                }
            } else if (command.getCommandType() == Command.BACK) {
                if (device == null) {
                    agent.cancelInquiry(this);
                    retCode = ACTION_CANCEL;
                }
            } else if ("Refresh".equals(command.getLabel())) {
                retCode = ACTION_REFRESH;
            }
        }
    }
}
