// @LICENSE@

package cz.kruch.track.location;

import api.location.LocationException;
import api.location.LocationProvider;
import api.location.Location;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Displayable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;

import cz.kruch.track.Resources;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;
import cz.kruch.track.ui.Desktop;

public class AndroidBluetoothLocationProvider
        extends StreamReadingLocationProvider
        implements Runnable {
    private static final String SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB";

    private String url;
    private long last;

    public AndroidBluetoothLocationProvider() throws LocationException {
        super("Bluetooth");
    }

    public boolean isRestartable() {
        return !(getThrowable() instanceof RuntimeException);
    }

    public int start() throws LocationException {
        // start BT discovery
        (new Discoverer()).start();

        return LocationProvider._STARTING;
    }

    public void run() {
        // be gentle and safe
        if (restarts++ > 0) {

            // debug
            setStatus("refresh");

//            // not so fast
//            if (getLastState() == LocationProvider._STALLED) { // give hardware a while
//                refresh();
//            } else { // take your time (5 sec)
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    // ignore
                }
//            }
        }

        // start with last known?
        if (url == null) {
            url = Config.btServiceUrl;
        }

        // reset last I/O stamp
        setLastIO(System.currentTimeMillis());

        // let's roll
        baby();

        // required thread state
        android.os.Looper.prepare();

        try {

            // main loop
            gps();

        } catch (Throwable t) {

//#ifdef __LOG__
            t.printStackTrace();
//#endif

            // record
            setStatus("top level error");
            setThrowable(t);

        } finally {

            // almost dead
            zombie();
        }
    }

    private void gps() throws IOException {

        // reset data
        reset();

        // inputs
        android.bluetooth.BluetoothSocket socket = null;
        InputStream stream = null;

        try {
            // debug
            setStatus("check Bluetooth status");

            // BT on?
            final android.bluetooth.BluetoothAdapter adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) {

                // no restart
                setThrowable(new RuntimeException(Resources.getString(Resources.DESKTOP_MSG_BT_OFF)));

                // notify user
                Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_BT_OFF), null, null);

                // nicer way of quiting 
                return;
            }

            // debug
            setStatus("opening connection");

            // get device, create socket, connect
            final android.bluetooth.BluetoothDevice device = android.bluetooth.BluetoothAdapter.getDefaultAdapter().getRemoteDevice(url);
            try {
                socket = (android.bluetooth.BluetoothSocket) device.getClass().getMethod("createRfcommSocket", new Class[]{ int.class }).invoke(device, Integer.valueOf(1));
            } catch (Exception e) {
//#ifdef __LOG__
                e.printStackTrace();
//#endif
            }
            if (socket == null) {
                socket = device.createRfcommSocketToServiceRecord(java.util.UUID.fromString(SPP_UUID));
            }
            socket.connect();

            // debug
            setStatus("opening input stream");

            // open stream for reading
            stream = socket.getInputStream();

            // debug
            setStatus("stream opened");

            // clear throwable
            setThrowable(null);

            // read NMEA until error or stop request
            while (isGo()) {

                Location location = null;

                try {

                    // get next location
                    location = nextLocation(stream, observer);

                } catch (IOException e) {
//#ifdef __LOG__
                    e.printStackTrace();
//#endif

                    // record
                    setStatus("I/O error");
                    setThrowable(e);

                    /*
                     * location is null - loop ends
                     */

                } catch (Throwable t) {
//#ifdef __LOG__
                    t.printStackTrace();
//#endif

                    // stop request?
                    if (t instanceof InterruptedException) {
                        setStatus("interrupted");
                        break;
                    }

                    // record
                    setThrowable(t);

                    // counter
                    errors++;

                    // blocked?
                    if (t instanceof SecurityException) {
                        break;
                    }

                    // ignore - let's go on
                    continue;
                }

                // end of data?
                if (location == null) {
                    break;
                }

                // new location timestamp
                _setLast(System.currentTimeMillis());

                // send new location
                notifyListener(location);

                // state change?
                final int newState = location.getFix() > 0 ? AVAILABLE : TEMPORARILY_UNAVAILABLE;
                if (updateLastState(newState)) {
                    notifyListener(newState);
                }

            } // for (; go ;)

        } finally {

            // debug
            setStatus("stopping");

            // debug
            setStatus("closing stream and connection");

            // cleanup
            synchronized (this) {

                // close input stream
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (Exception e) {
                        // ignore
                    }
                    stream = null;
                }

                // close serial/bt connection
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (Exception e) {
                        // ignore
                    }
                    socket = null;
                }

            }

            // debug
            setStatus("stream and connection closed");

        }
    }

    private synchronized long _getLast() {
        return last;
    }

    private synchronized void _setLast(long last) {
        this.last = last;
    }

    private final class Discoverer implements CommandListener, Runnable {

        private String btaddres, btname;

        private final Vector devices = new Vector();

        private final Command cmdBack = new Command(Resources.getString(Resources.CMD_CANCEL), Desktop.CANCEL_CMD_TYPE, 1);
        private final Command cmdConnect = new Command(Resources.getString(Resources.DESKTOP_CMD_CONNECT), Command.SCREEN, 0);

        private List pane;

        public Discoverer() {
            this.pane = new List(Resources.getString(Resources.DESKTOP_MSG_SELECT_DEVICE), List.IMPLICIT);
            this.pane.setCommandListener(this);
            this.pane.removeCommand(List.SELECT_COMMAND);
        }

        public void start() throws LocationException {
            (new Thread(this)).start();
        }

        public void run() {
            // quit status
            boolean quit = true;

            // required thread state
            android.os.Looper.prepare();

            // BT supported and turned on check
            android.bluetooth.BluetoothAdapter adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) {

                // notify user
                Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_BT_OFF), null, null);

            } else {

                // find paired devices
                java.util.Set paired = android.bluetooth.BluetoothAdapter.getDefaultAdapter().getBondedDevices();
                if (paired.size() > 0) {
                    for (java.util.Iterator it = paired.iterator(); it.hasNext(); ) {
                        final android.bluetooth.BluetoothDevice device = (android.bluetooth.BluetoothDevice) it.next();
                        devices.addElement(device);
                        pane.append(device.getName(), null);
                    }
                    setupCommands(true);
                    Desktop.display.setCurrent(pane);
                    quit = false;
                } else {
                    Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_NO_DEVICES_DISCOVERED), null, null);
                }
            }

            // quit?
            if (quit) {
                letsGo(false);
            }
        }

        private void letsGo(boolean ok) {
            // restore screen anyway
            Desktop.display.setCurrent(Desktop.screen);

            // good to go or not
            if (ok) {

                // update bt device info
                Config.btDeviceName = btname;
                Config.btServiceUrl = btaddres;
                try {
                    Config.update(Config.VARS_090);
                } catch (ConfigurationException e) {
                    Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_CFG_UPDATE_FAILED), e, null);
                }

                // start
                AndroidBluetoothLocationProvider.this.url = btaddres;
                final Thread thread = new Thread(AndroidBluetoothLocationProvider.this);
                thread.start();

            } else {
                notifyListener(LocationProvider._CANCELLED);
            }
        }

        private void setupCommands(boolean ready) {
            pane.removeCommand(cmdBack);
            pane.removeCommand(cmdConnect);
            if (ready) {
                if (devices.size() > 0) {
                    pane.setSelectCommand(cmdConnect);
                }
            }
            pane.addCommand(cmdBack);
        }

        public void commandAction(Command command, Displayable displayable) {
            final int type = command.getCommandType();
            if (type == Command.SCREEN) {
                btname = pane.getString(pane.getSelectedIndex());
                btaddres = ((android.bluetooth.BluetoothDevice) devices.elementAt(pane.getSelectedIndex())).getAddress();
                letsGo(true);
            } else if (type == Desktop.CANCEL_CMD_TYPE)  {
                letsGo(false);
            }
        }
    }
}