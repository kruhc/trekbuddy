// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import api.location.LocationProvider;
import api.location.LocationException;
import api.location.LocationListener;
import api.location.Location;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;
import cz.kruch.track.ui.Desktop;
import cz.kruch.track.TrackingMIDlet;
import cz.kruch.j2se.io.BufferedInputStream;
import cz.kruch.j2se.io.BufferedOutputStream;

import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Ticker;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.Connector;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Vector;
import java.util.Timer;
import java.util.TimerTask;

public class Jsr82LocationProvider extends StreamReadingLocationProvider implements Runnable {
    private static final int WATCHER_PERIOD = 60 * 1000;

    private Thread thread;

    private String btname = null;
    private String btspp = null;
    private volatile boolean go = false;

    private Timer watcher;
    private final Object sync = new Object();
    private long timestamp = 0;
    private int state = LocationProvider._STARTING;

    private api.file.File nmeaFc;
    private OutputStream nmeaObserver;

    public Jsr82LocationProvider(/*Callback recordingCallback*/) {
        super(Config.LOCATION_PROVIDER_JSR82);
/*
        this.recordingCallback = recordingCallback;
*/
    }

    public boolean isRestartable() {
        return true;
    }

    public void run() {
        try {
            // start with last known?
            if (btspp == null) {
                try {
                    javax.bluetooth.LocalDevice.getLocalDevice();
                } catch (javax.bluetooth.BluetoothStateException e) {
                    throw new LocationException("Bluetooth radio disabled?");
                }
                go = true;
                btspp = Config.getSafeInstance().getBtServiceUrl();
                thread = Thread.currentThread();
            }

            // start gpx
            notifyListener(LocationProvider._STARTING);

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

            // be ready for restart
            btspp = null;

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

        return LocationProvider._STARTING;
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
        if (isTracklog() && Config.TRACKLOG_FORMAT_NMEA.equals(Config.getSafeInstance().getTracklogsFormat())) {
            if (nmeaFc != null) {
                return; // already started, probably just reconnected
            }
            String path = Config.getSafeInstance().getTracklogsDir() + "/trekbuddy-" + GpxTracklog.dateToFileDate(System.currentTimeMillis()) + ".nmea";
            try {
                nmeaFc = new api.file.File(Connector.open(path, Connector.WRITE));
                nmeaFc.create();
                nmeaObserver = new BufferedOutputStream(nmeaFc.openOutputStream(), 512);

                // set stream 'observer'
                setObserver(nmeaObserver);

/*
                // signal recording has started
                recordingCallback.invoke(new Integer(GpxTracklog.CODE_RECORDING_START), null);
*/

            } catch (Throwable t) {
                Desktop.showError("Failed to start NMEA log.", t, null);
            }
        }
    }

    public void stopNmeaLog() {

/*
        // signal recording is stopping
        recordingCallback.invoke(new Integer(GpxTracklog.CODE_RECORDING_STOP), null);
*/
        if (go) {
            return;
        }

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
            in = new BufferedInputStream(connection.openInputStream(), BUFFER_SIZE);

            // read NMEA until error or stop request
            for (; go ;) {

                Location location = null;

                // get next location
                try {
                    location = nextLocation(in);
                /*} catch (AssertionFailedException e) { // never happens, see nextLocation(...)

                    // warn
                    Desktop.showWarning(e.getMessage(), null, null);

                    // ignore
                    continue;

                } */
                } catch (IOException e) {

                    // record exception
                    setException(new LocationException(e));

                    /*
                     * location is null, therefore the loop quits
                     */

                } catch (Exception e) {

                    // record exception
                    if (e instanceof InterruptedException) {
                        // probably stop request
                    } else {
                        // record exception
                        setException(e instanceof LocationException ? (LocationException) e : new LocationException(e));
                    }

                    // ignore
                    continue;
                }

                // end of data?
                if (location == null) {
                    break;
                }

                boolean stateChange = false;

                // is position valid?
                synchronized (sync) {
                    if (location.getFix() > 0) {
                        // fix state - we may be in TEMPORARILY_UNAVAILABLE state
                        if (state != LocationProvider.AVAILABLE) {
                            state = LocationProvider.AVAILABLE;
                            stateChange = true;
                        }
                        timestamp = System.currentTimeMillis();
                    } else {
                        if (state != LocationProvider.TEMPORARILY_UNAVAILABLE) {
                            state = LocationProvider.TEMPORARILY_UNAVAILABLE;
                            stateChange = true;
                        }
                    }
                }

                // stateChange about state, if necessary
                if (stateChange) {
                    notifyListener(state);
                }

                // send new location
                notifyListener(location);

            } // for (; go ;)

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

    private final class Discoverer extends List
            implements javax.bluetooth.DiscoveryListener, CommandListener, Runnable {

        // intentionally not static... ehmm - why?
        private javax.bluetooth.UUID[] uuidSet = {
/*
            new javax.bluetooth.UUID(0x1101),  // SPP
            new javax.bluetooth.UUID(0x0003),  // RFCOMM
            new javax.bluetooth.UUID(0x0100)   // L2CAP
*/
            new javax.bluetooth.UUID(0x0100)   // L2CAP
        };

        private javax.bluetooth.DiscoveryAgent agent;
        private javax.bluetooth.RemoteDevice device;
        private Vector devices = new Vector();
        private String url;
        private int transactionID = 0;
        private boolean inquiryCompleted;

        private boolean cancel = false;

        private Command cmdBack = new Command("Cancel", Command.BACK, 1);
        private Command cmdRefresh = new Command("Refresh", Command.SCREEN, 1);
        private Command cmdConnect = new Command("Connect", Command.ITEM, 1);

        public Discoverer() {
            super("DeviceSelection", List.IMPLICIT);
            this.setCommandListener(this);
            this.removeCommand(List.SELECT_COMMAND);
        }

        public void start() throws LocationException {
            Desktop.display.setCurrent(this);
            try {
                goDevices();
            } catch (LocationException e) {
                Desktop.display.setCurrent(Desktop.screen);
                throw e;
            }
        }

        private void letsGo(boolean ok) {
            // restore screen anyway
            Desktop.display.setCurrent(Desktop.screen);

            // good to go or not
            if (ok) {
                // start
                go = true;
                btspp = url;
                thread = new Thread(Jsr82LocationProvider.this);
                thread.start();
                // update bt device info
                Config.getSafeInstance().setBtDeviceName(btname);
                Config.getSafeInstance().setBtServiceUrl(btspp);
                try {
                    Config.getSafeInstance().update();
                } catch (ConfigurationException e) {
                    Desktop.showError("Failed to update config", e, null);
                }
            } else {
                notifyListener(LocationProvider._CANCELLED);
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
                Desktop.showError("Service search failed", e, null);
                showDevices();
            }
        }

        private void setupCommands(boolean ready) {
            removeCommand(cmdBack);
            removeCommand(cmdRefresh);
            removeCommand(cmdConnect);
            addCommand(cmdBack);
            if (ready) {
                addCommand(cmdRefresh);
                if (devices.size() > 0) {
                    addCommand(cmdConnect);
                }
            }
        }

        public void run() {
            setTicker(new Ticker("Resolving names"));
            for (int N = devices.size(), i = 0; i < N; i++) {
                String name;
                javax.bluetooth.RemoteDevice remoteDevice = ((javax.bluetooth.RemoteDevice) devices.elementAt(i));
                try {
                    name = remoteDevice.getFriendlyName(false);
                } catch (Throwable t) {
                    name = "#" + remoteDevice.getBluetoothAddress();
                }
                append(name, null);
            }
            setTicker(null);
        }

        public void deviceDiscovered(javax.bluetooth.RemoteDevice remoteDevice, javax.bluetooth.DeviceClass deviceClass) {
            devices.addElement(remoteDevice);
            append("#" + remoteDevice.getBluetoothAddress(), null); // show bt adresses just to signal we are finding any
        }

        public void servicesDiscovered(int transId, javax.bluetooth.ServiceRecord[] serviceRecords) {
            if (url == null) {
                try {
                    for (int N = serviceRecords.length, i = 0; i < N && url == null; i++) {
                        url = serviceRecords[i].getConnectionURL(javax.bluetooth.ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
                    }
                } catch (Throwable t) {
                    // what to do?
                }
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
                    Desktop.showWarning("Service not found (" + respMsg + ").",
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
                    String codeStr;
                    switch (discType) {
                        case javax.bluetooth.DiscoveryListener.INQUIRY_COMPLETED:
                            codeStr = "INQUIRY_COMPLETED";
                            break;
                        case javax.bluetooth.DiscoveryListener.INQUIRY_ERROR:
                            codeStr = "INQUIRY_ERROR";
                            break;
                        case javax.bluetooth.DiscoveryListener.INQUIRY_TERMINATED:
                            codeStr = "INQUIRY_TERMINATED";
                            break;
                        default:
                            codeStr = "UNKNONW";
                    }
                    Desktop.showError("No devices discovered (" + codeStr + ")", null, null);
                }
            } else {
                // resolve names in a thread
                (new Thread(this)).start();
            }
        }

        public void commandAction(Command command, Displayable displayable) {
            if (command == cmdConnect) { /* device selection */
                btname = getString(getSelectedIndex());
                device = (javax.bluetooth.RemoteDevice) devices.elementAt(getSelectedIndex());
                if (TrackingMIDlet.hasFlag("bt_service_search")) {
                    goServices();
                } else {
                    url = "btspp://" + device.getBluetoothAddress() + ":1";
                    letsGo(true);
                }
            } else if (command.getCommandType() == Command.BACK) {
                cancel = true;
                if (transactionID > 0) {
                    agent.cancelServiceSearch(transactionID);
                }
                if (!inquiryCompleted) {
                    agent.cancelInquiry(this);
                }
                if (device == null) { /* quit BT explorer */
                    if (inquiryCompleted) {
                        letsGo(false);
                    }
                } else { /* offer device selection */
                    showDevices();
                }
            } else if (command == cmdRefresh) { /* refresh device list */
                try {
                    goDevices();
                } catch (LocationException e) {
                    Desktop.showError("Unable to restart discovery", e, null);
                }
            }
        }
    }
}
