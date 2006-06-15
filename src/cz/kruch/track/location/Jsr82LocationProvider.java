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

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Ticker;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.Connector;
import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;

public class Jsr82LocationProvider extends StreamReadingLocationProvider implements Runnable {
    private static final Logger log = new Logger("Bluetooth");

    private static final int ACTION_GO      = 0;
    private static final int ACTION_REFRESH = 1;
    private static final int ACTION_CANCEL  = 2;

    private Display display;
    private Displayable previous;

    private Thread thread;
    private volatile LocationListener listener;
    private volatile LocationException exception;

    private String url = null;
    private boolean go = false;

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
                notifyListener(LocationProvider.AVAILABLE);

                // GPS
                gps();

            } else { // Cancel
                // TODO better code? signal Cancel through listener? or event?
                notifyListener(LocationProvider.OUT_OF_SERVICE);
            }
        } catch (Exception e) {
            // record exception
            exception = e instanceof LocationException ? (LocationException) e : new LocationException(e);

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

    private void gps() throws IOException {
        // open connection
        StreamConnection connection = (StreamConnection) Connector.open(url, Connector.READ);
        InputStream in = null;

        try {
            // open stream for reading
            in = connection.openInputStream();

            // read NMEA until error or stop request
            for (; go ;) {

                // read GGA
                String nmea = nextGGA(in);
                if (nmea == null) {
                    listener.providerStateChanged(this, LocationProvider.OUT_OF_SERVICE);
                    break;
                }

                // send new location
                try {
                    listener.locationUpdated(this, NmeaParser.parse(nmea));
                } catch (Exception e) {
                    log.error("corrupted record: " + nmea + "\n" + e.toString());
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

            // reset commands
            removeCommand(cmdBack);
            removeCommand(cmdRefresh);
            removeCommand(List.SELECT_COMMAND);
            addCommand(cmdBack);

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
                        Desktop.showWarning(display, e.getMessage());
                    } finally {
                        // cancel search (if started)
                        if (transactionID > 0) {
                            agent.cancelServiceSearch(transactionID);
                        }

                        // return to device selection
                        exception = null;
                        device = null;
                        previous();
                    }
                }
            }

            return retCode;
        }

        public void next() {
            setTitle("ServiceSearch");
            setTicker(new Ticker("Searching service..."));
        }

        public void previous() {
            setTitle("DeviceSelection");
        }

        public String getURL() throws LocationException {
            while (url == null && exception == null && retCode == -1) {
                Thread.yield();
            }

            if (exception == null) {
                return url;
            }

            throw exception;
        }

        public javax.bluetooth.RemoteDevice getDevice() throws LocationException {
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

            // reset commands
            removeCommand(cmdBack);
            removeCommand(cmdRefresh);
            removeCommand(List.SELECT_COMMAND);
            addCommand(cmdBack);
            addCommand(cmdRefresh);
            addCommand(List.SELECT_COMMAND);

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
