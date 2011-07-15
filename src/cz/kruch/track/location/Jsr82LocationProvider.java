// @LICENSE@

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
    
    private static final String BLUETOOTH_ERROR_MSG = "Bluetooth error: ";

    private Refresher kar;

    public Jsr82LocationProvider() throws LocationException {
        super("Bluetooth");
        
        /* BT turned on check */
        try {
            javax.bluetooth.LocalDevice.getLocalDevice();
        } catch (Throwable t) {
            throw new LocationException(Resources.getString(Resources.DESKTOP_MSG_BT_OFF));
        }
    }

    protected void setThrowable(Throwable throwable) {
        if (throwable instanceof javax.bluetooth.BluetoothConnectionException) {
            javax.bluetooth.BluetoothConnectionException exception = (javax.bluetooth.BluetoothConnectionException) throwable;
            if (exception.getMessage() == null || exception.getMessage().length() == 0) {
                throwable = new javax.bluetooth.BluetoothConnectionException(exception.getStatus(), Integer.toString(exception.getStatus()));
            }
        }
        super.setThrowable(throwable);
    }

    protected String getKnownUrl() {
        return Config.btServiceUrl;
    }

    protected void refresh() {
        (new Refresher()).run();
    }

    protected void startKeepAlive() {
        if (Config.btKeepAlive != 0) {
            try {
                Desktop.schedule(kar = new Refresher(connection.openOutputStream()),
                                 Config.btKeepAlive, Config.btKeepAlive);
            } catch (Exception e) {
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
                } catch (Exception e) {
                    // ignore
                }
                os = null; // gc hint
            }
        }
    }

    private final class Discoverer implements javax.bluetooth.DiscoveryListener, CommandListener, Runnable {

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
        private boolean preknownUsed;

        private final Vector devices = new Vector();
        
        private final Command cmdBack = new Command(Resources.getString(Resources.CMD_CANCEL), Desktop.CANCEL_CMD_TYPE, 1);
        private final Command cmdRefresh = new Command(Resources.getString(Resources.DESKTOP_CMD_REFRESH), Command.SCREEN, 1);
        private final Command cmdConnect = new Command(Resources.getString(Resources.DESKTOP_CMD_CONNECT), Command.ITEM, 1);

        private List pane;

        public Discoverer() {
            this.pane = new List(Resources.getString(Resources.DESKTOP_MSG_SELECT_DEVICE), List.IMPLICIT);
            this.pane.setCommandListener(this);
            this.pane.removeCommand(List.SELECT_COMMAND);
        }

        public void start() throws LocationException {
            Desktop.display.setCurrent(pane);
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

            // gc hint
            device = null;

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
                final Thread thread = new Thread(Jsr82LocationProvider.this);
//#ifdef __ALL__
                if (cz.kruch.track.TrackingMIDlet.samsung) {
                    thread.setPriority(Thread.MIN_PRIORITY);
                }
//#endif
                thread.start();

            } else {
                notifyListener(LocationProvider._CANCELLED);
            }
        }

        private void goDevices() throws LocationException {
            // reset UI and state
            pane.deleteAll();
            devices.removeAllElements();
            btspp = null;
            device = null;
            inquiryCompleted = false;
            cancel = false;

            // update UI
            pane.setTicker(new Ticker(Resources.getString(Resources.DESKTOP_MSG_SEARCHING_DEVICES)));
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
            pane.setTicker(null);
            pane.setTitle(Resources.getString(Resources.DESKTOP_MSG_SELECT_DEVICE));
            setupCommands(true);
        }

        private void goServices() {
            // update UI
            pane.setTitle(Resources.getString(Resources.DESKTOP_MSG_SERVICE_SEARCH));
            pane.setTicker(new Ticker(Resources.getString(Resources.DESKTOP_MSG_SEARCHING_SERVICE)));
            setupCommands(false);

            // start search
            try {
                transactionID = agent.searchServices(null, uuidSet, device, this);
            } catch (javax.bluetooth.BluetoothStateException e) {
                Desktop.showError(BLUETOOTH_ERROR_MSG + "Service search failed", e, null);
                showDevices();
            }
        }

        private void setupCommands(boolean ready) {
            pane.removeCommand(cmdBack);
            pane.removeCommand(cmdRefresh);
            pane.removeCommand(cmdConnect);
            if (ready) {
                pane.addCommand(cmdRefresh);
                if (devices.size() > 0) {
                    pane.addCommand(cmdConnect);
                }
            }
            pane.addCommand(cmdBack);
        }

        private String getFixedAddress(javax.bluetooth.RemoteDevice device) {
            final String address = device.getBluetoothAddress();
            if (preknownUsed && cz.kruch.track.configuration.Config.btAddressWorkaround) {
                try {
                    final byte[] bytes = address.getBytes("UTF-8");
                    final StringBuffer sb = new StringBuffer(12);
                    for (int N = bytes.length, i = 0; i < N; ) {
                        sb.append((char) bytes[N - i - 2]).append((char) bytes[N - i - 1]);
                        i += 2;
                    }
                    return sb.toString();
                } catch (Exception e) {
                    // ignore
                }
            }
            return address;
        }

        public void run() {
            pane.setTicker(new Ticker(Resources.getString(Resources.DESKTOP_MSG_RESOLVING_NAMES)));
            for (int N = devices.size(), i = 0; i < N; i++) {
                final javax.bluetooth.RemoteDevice remoteDevice = ((javax.bluetooth.RemoteDevice) devices.elementAt(i));
                String name = null;
                try {
                    name = remoteDevice.getFriendlyName(false);
                } catch (Throwable t) {
                    // ignore
                }
                if (name == null || name.length() == 0) {
                    name = getFixedAddress(remoteDevice);
                }
                pane.append(name, null);
            }
            pane.setTicker(null);
        }

        public void deviceDiscovered(javax.bluetooth.RemoteDevice remoteDevice, javax.bluetooth.DeviceClass deviceClass) {
            devices.addElement(remoteDevice);
            pane.append(remoteDevice.getBluetoothAddress(), null); // show bt adresses just to signal we are finding any
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

                // something is wrong
                if (respCode != SERVICE_SEARCH_TERMINATED) {

                    // notify user
                    Desktop.showWarning(BLUETOOTH_ERROR_MSG + respMsg, null, null);

                    // update UI
                    pane.setTicker(null);

                    // offer device selection
                    showDevices();
                }
            } else {
                letsGo(true);
            }
        }

        public void inquiryCompleted(int discType) {
            // set flag
            inquiryCompleted = true;

            // fallback to preknown devices
            if (devices.size() == 0) {
                final javax.bluetooth.RemoteDevice[] preknown = agent.retrieveDevices(javax.bluetooth.DiscoveryAgent.PREKNOWN);
                if (preknown != null && preknown.length != 0) {
                    pane.setTitle(Resources.getString(Resources.DESKTOP_MSG_SELECT_DEVICE) + " (PREKNOWN)");
                    preknownUsed = true;
                    for (int N = preknown.length, i = 0; i < N; i++) {
                        devices.addElement(preknown[i]);
                        pane.append(getFixedAddress(preknown[i]), null); // show bt adresses just to signal we are finding any
                    }
                }
            }

            // update UI
            pane.setTicker(null);
            pane.deleteAll();
            
            // adjust commands
            setupCommands(true);

            // decide
            if (devices.size() == 0) {
                if (cancel) {
                    letsGo(false);
                } else {
                    final String codeStr;
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
/*
                (new Thread(this)).start();
*/              // can't we resolve names from impl callback?
                run();
            }
        }

        public void commandAction(Command command, Displayable displayable) {
            if (command == cmdConnect) { /* device selection */
                btname = pane.getString(pane.getSelectedIndex());
                device = (javax.bluetooth.RemoteDevice) devices.elementAt(pane.getSelectedIndex());
                if (Config.btDoServiceSearch) {
                    goServices();
                } else {
                    btspp = "btspp://" + getFixedAddress(device) + ":1";
                    letsGo(true);
                }
            } else if (command.getCommandType() == Desktop.CANCEL_CMD_TYPE) {
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
                    Desktop.showError(BLUETOOTH_ERROR_MSG + "Discovery restart failed", e, null);
                }
            }
        }
    }
}
