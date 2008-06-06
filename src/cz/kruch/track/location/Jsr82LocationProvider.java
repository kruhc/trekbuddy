/*
 * Copyright 2006-2007 Ales Pour <kruhc@seznam.cz>.
 * All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package cz.kruch.track.location;

import api.location.LocationProvider;
import api.location.LocationException;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;
import cz.kruch.track.ui.Desktop;
import cz.kruch.track.Resources;

import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Ticker;
import javax.microedition.io.StreamConnection;
import java.util.Vector;
import java.util.TimerTask;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Bluetooth GPS provider implementation.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class Jsr82LocationProvider extends SerialLocationProvider {

    private Refresher kar;

    public Jsr82LocationProvider() throws LocationException {
        super("Bluetooth");
        
        /* BT turned on check */
        try {
            javax.bluetooth.LocalDevice.getLocalDevice();
        } catch (IOException e) {
            throw new LocationException(Resources.getString(Resources.DESKTOP_MSG_BT_OFF));
        }
    }

    protected String getKnownUrl() {
        return Config.btServiceUrl;
    }

    protected void refresh() {
        stalls++;
        (new Refresher()).run();
    }

    protected void startKeepAlive(StreamConnection c) {
        if (Config.btKeepAlive != 0) {
            try {
                Desktop.timer.schedule(kar = new Refresher(c.openOutputStream()),
                                       Config.btKeepAlive, Config.btKeepAlive);
            } catch (IOException e) {
                // ignore
            }
        }
    }

    protected void stopKeepAlive() {
        if (kar != null) {
            kar.cancel();
            kar.close();
            kar = null; // gc hint
        }
    }

    public int start() throws LocationException {
        // start BT discovery
        (new Discoverer()).start();

        return LocationProvider._STARTING;
    }

    private static final class Refresher extends TimerTask implements javax.bluetooth.DiscoveryListener {
        private static final int MODE_REFRESH       = 0;
        private static final int MODE_KEEP_ALIVE    = 1;

        private OutputStream os;
        private int mode;
        private boolean done;

        public Refresher() {
            this.mode = MODE_REFRESH;
        }

        public Refresher(OutputStream os) {
            this.mode = MODE_KEEP_ALIVE;
            this.os = os;
        }

        public void deviceDiscovered(javax.bluetooth.RemoteDevice remoteDevice,
                                     javax.bluetooth.DeviceClass deviceClass) {
        }

        public void servicesDiscovered(int i, javax.bluetooth.ServiceRecord[] serviceRecords) {
        }

        public void serviceSearchCompleted(int i, int i1) {
        }

        public void inquiryCompleted(int i) {
            synchronized (this) {
                done = true;
                notify();
            }
        }
        
        public void run() {
            if (mode == MODE_REFRESH) {
                try {
                    javax.bluetooth.LocalDevice.getLocalDevice().getDiscoveryAgent().startInquiry(javax.bluetooth.DiscoveryAgent.GIAC, this);
                    synchronized (this) {
                        while (!done) {
                            try {
                                wait();
                            } catch (InterruptedException e) {
                                // should not happen
                            }
                        }
                    }
                } catch (javax.bluetooth.BluetoothStateException e) {
                    // ignore
                }
            } else {
                if (os != null) {
                    try {
                        os.write(0);
                        pings++;
                    } catch (IOException e) {
                        cancel();
                        close();
                    }
                }
            }
        }

        public void close() {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    // ignore
                }
                os = null; // gc hint
            }
        }
    }

    private final class Discoverer
            extends List
            implements javax.bluetooth.DiscoveryListener, CommandListener, Runnable {

        private final javax.bluetooth.UUID[] uuidSet = {
//            new javax.bluetooth.UUID(0x1101)  // SPP
//            new javax.bluetooth.UUID(0x0003)  // RFCOMM
            new javax.bluetooth.UUID(0x0100)  // L2CAP - seems most interoperable
        };

        private javax.bluetooth.DiscoveryAgent agent;
        private javax.bluetooth.RemoteDevice device;
        private String btspp, btname;
        private int transactionID;
        private boolean inquiryCompleted;

        private boolean cancel;

        private final Vector devices = new Vector();
        
        private final Command cmdBack = new Command(Resources.getString(Resources.CMD_CANCEL), Command.BACK, 1);
        private final Command cmdRefresh = new Command(Resources.getString(Resources.DESKTOP_CMD_REFRESH), Command.SCREEN, 1);
        private final Command cmdConnect = new Command(Resources.getString(Resources.DESKTOP_CMD_CONNECT), Command.ITEM, 1);

        public Discoverer() {
            super(Resources.getString(Resources.DESKTOP_MSG_SELECT_DEVICE), List.IMPLICIT);
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
                    Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_CFG_UPDATE_FAILED), e, null);
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
            setTicker(new Ticker(Resources.getString(Resources.DESKTOP_MSG_SEARCHING_DEVICES)));
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
            setTitle(Resources.getString(Resources.DESKTOP_MSG_SELECT_DEVICE));
            setupCommands(true);
        }

        private void goServices() {
            // update UI
            setTitle(Resources.getString(Resources.DESKTOP_MSG_SERVICE_SEARCH));
            setTicker(new Ticker(Resources.getString(Resources.DESKTOP_MSG_SEARCHING_SERVICE)));
            setupCommands(false);

            // start search
            try {
                transactionID = agent.searchServices(null, uuidSet, device, this);
            } catch (javax.bluetooth.BluetoothStateException e) {
                Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_SERVICE_SEARCH_FAILED), e, null);
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
            setTicker(new Ticker(Resources.getString(Resources.DESKTOP_MSG_RESOLVING_NAMES)));
            for (int N = devices.size(), i = 0; i < N; i++) {
                String name = null;
                javax.bluetooth.RemoteDevice remoteDevice = ((javax.bluetooth.RemoteDevice) devices.elementAt(i));
                try {
                    name = remoteDevice.getFriendlyName(false);
                    if (name == null) {
                        name = remoteDevice.getFriendlyName(true);
                    }
                } catch (Throwable t) {
                    //ignore
                }
                if (name == null) {
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
                    Desktop.showWarning(Resources.getString(Resources.DESKTOP_MSG_SERVICE_NOT_FOUND) + " (" + respMsg + ").",
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
                    Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_NO_DEVICES_DISCOVERED) + " (" + codeStr + ")", null, null);
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
                    Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_DISC_RESTART_FAILED), e, null);
                }
            }
        }
    }
}
