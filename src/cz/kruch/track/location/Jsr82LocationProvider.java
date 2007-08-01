// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import api.location.LocationProvider;
import api.location.LocationException;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;
import cz.kruch.track.ui.Desktop;

import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Ticker;
import java.util.Vector;

public final class Jsr82LocationProvider extends SerialLocationProvider {

    public Jsr82LocationProvider() throws LocationException {
        super(Config.LOCATION_PROVIDER_JSR82);

        /* BT turned on check */
        try {
            javax.bluetooth.LocalDevice.getLocalDevice();
        } catch (javax.bluetooth.BluetoothStateException e) {
            throw new LocationException("Bluetooth turned off?");
        }
    }

    protected String getKnownUrl() {
        return Config.btServiceUrl;
    }

    public int start() throws LocationException {
        // start BT discovery
        (new Discoverer()).start();

        return LocationProvider._STARTING;
    }

    private final class Discoverer extends List
            implements javax.bluetooth.DiscoveryListener, CommandListener, Runnable {

        private final javax.bluetooth.UUID[] uuidSet = {
/*
            new javax.bluetooth.UUID(0x1101),  // SPP
            new javax.bluetooth.UUID(0x0003),  // RFCOMM
            new javax.bluetooth.UUID(0x0100)   // L2CAP
*/
            new javax.bluetooth.UUID(0x0100)   // L2CAP
        };

        private javax.bluetooth.DiscoveryAgent agent;
        private javax.bluetooth.RemoteDevice device;
        private String btspp, btname;
        private int transactionID;
        private boolean inquiryCompleted;

        private boolean cancel;

        private final Vector devices = new Vector();
        
        private final Command cmdBack = new Command("Cancel", Command.BACK, 1);
        private final Command cmdRefresh = new Command("Refresh", Command.SCREEN, 1);
        private final Command cmdConnect = new Command("Connect", Command.ITEM, 1);

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

                // update bt device info
                Config.btDeviceName = btname;
                Config.btServiceUrl = btspp;
                try {
                    Config.update(Config.VARS_090);
                } catch (ConfigurationException e) {
                    Desktop.showError("Failed to update config", e, null);
                }

                // start
                Jsr82LocationProvider.this.url = btspp;
                (new Thread(Jsr82LocationProvider.this)).start();

            } else {
                notifyListener(LocationProvider._CANCELLED);
            }
        }

        private void goDevices() throws LocationException {
            // reset UI and state
            deleteAll();
            devices.removeAllElements();
            btspp = null;
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
            if (btspp == null) {
                try {
                    for (int N = serviceRecords.length, i = 0; i < N && btspp == null; i++) {
                        btspp = serviceRecords[i].getConnectionURL(javax.bluetooth.ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
                    }
                } catch (Throwable t) {
                    // what to do?
                }
            }
        }

        public void serviceSearchCompleted(int transID, int respCode) {
            transactionID = 0;

            if (btspp == null) {
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
                if (cz.kruch.track.TrackingMIDlet.hasFlag("bt_service_search")) {
                    goServices();
                } else {
                    btspp = "btspp://" + device.getBluetoothAddress() + ":1";
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
