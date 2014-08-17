// @LICENSE@

package cz.kruch.track.location;

//#ifdef __ANDROID__

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

//#ifdef __BACKPORT__
import backport.android.bluetooth.*;
//#else
import android.bluetooth.*;
//#endif

public class AndroidBluetoothLocationProvider
        extends StreamReadingLocationProvider
        implements Runnable {
    private static final java.util.UUID SPP_UUID = java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private volatile String url;

    private BluetoothDevice device;

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

// TODO refresh after stall, alike in Serial provider?            
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

        // reset last I/O stamp // TODO move to common base
        setLastIO(System.currentTimeMillis());

        // let's roll
        baby();

        // required thread state (2.x only???)
        android.os.Looper.prepare();

        try {

            // main loop
            gps();

        } catch (Throwable t) {

            // record
            setStatus("main loop error");
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
        BluetoothSocket socket = null;
        InputStream stream = null;

        try {

            // initialize
            if (device == null) {

                // debug
                setStatus("check Bluetooth status");

                // BT on?
                final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null || !adapter.isEnabled()) {

                    // no restart
                    setThrowable(new RuntimeException(Resources.getString(Resources.DESKTOP_MSG_BT_OFF)));

                    // notify user
                    Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_BT_OFF), null, null);

                    // nicer way of quiting
                    return;
                }

                // debug
                setStatus("getting device");
                
                // get device
                this.device = adapter.getRemoteDevice(url);
            }

            // debug
            setStatus("creating socket");

            // create socket
            final BluetoothDevice device = this.device;
            try {
                socket = (BluetoothSocket) device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[]{ java.util.UUID.class }).invoke(device, SPP_UUID);
                sockType = "Insecure";
            } catch (Throwable t) {
                android.util.Log.e(cz.kruch.track.TrackingMIDlet.APP_TITLE, "createInsecureRfcommSocketToServiceRecord", t);
            }
            if (socket == null) {
                // is this 1.6 method?
                try {
                    socket = (BluetoothSocket) device.getClass().getMethod("createRfcommSocket", new Class[]{ int.class }).invoke(device, Integer.valueOf(1));
                    sockType = "Unspecified";
                } catch (Throwable t) {
                    android.util.Log.e(cz.kruch.track.TrackingMIDlet.APP_TITLE, "createRfcommSocket", t);
                }
            }
            if (socket == null) {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                sockType = "Secure";
            }
            android.util.Log.i(cz.kruch.track.TrackingMIDlet.APP_TITLE, "socketType: " + sockType);

            // debug
            setStatus("connecting");

            // connect the device; as per dev guide, call cancelDiscovery first - it helps and should be safe ;-)
            socket.connect();

            // debug
            setStatus("opening input stream");

            // open stream for reading
            stream = socket.getInputStream();

            // debug
            setStatus("stream opened");

            // TODO keep-alive
            // TODO watcher

            // clear throwable
            setThrowable(null);

            // read NMEA until error or stop request
            while (isGo()) {

                Location location = null;

                try {

                    // get next location
                    location = nextLocation(stream, observer);

                } catch (IOException e) {

                    // record
                    setStatus("I/O error");
                    setThrowable(e);

                    /*
                     * location is null - loop ends
                     */

                } catch (Throwable t) {

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
                setLast(System.currentTimeMillis());

                // notify listener
                notifyListener2(location);

            } // for (; go ;)

        } finally {

            // debug
            setStatus("stopping");

            // TODO stop watcher
            // TODO stop keep-alive

            // debug
            setStatus("closing stream and connection");

            // cleanup
            /*synchronized (this)*/ { // 2014-08-02: no keep-alive nor watcher -> no need to synchronize

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

                // forget device when stopping
                if (!isGo()) {
                    device = null;
                }
            }

            // debug
            setStatus("stream and connection closed");

        }
    }

    protected void setStatus(Object status) {
        super.setStatus(status);
        if (status != null) {
            android.util.Log.i(cz.kruch.track.TrackingMIDlet.APP_TITLE, "status: " + status);
        }
    }

    protected void setThrowable(Throwable throwable) {
        super.setThrowable(throwable);
        if (throwable != null) {
            android.util.Log.e(cz.kruch.track.TrackingMIDlet.APP_TITLE, "exception occured", throwable);
        }
    }

    private final class Discoverer implements CommandListener, Runnable {

        private String btaddres, btname;

        private final Vector<BluetoothDevice> devices = new Vector<BluetoothDevice>();

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

            // required thread state (2.x only???)
            android.os.Looper.prepare();

            // BT supported and turned on check
            final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) {

                // notify user
                Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_BT_OFF), null, null);

            } else {

                // find paired devices
                final java.util.Set<BluetoothDevice> paired = adapter.getBondedDevices();
                if (paired.size() > 0) {
                    for (java.util.Iterator<BluetoothDevice> it = paired.iterator(); it.hasNext(); ) {
                        final BluetoothDevice device = it.next();
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
                Config.updateInBackground(Config.VARS_090);

                // start
                AndroidBluetoothLocationProvider.this.url = btaddres;
                (new Thread(AndroidBluetoothLocationProvider.this)).start();

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
                btaddres = devices.elementAt(pane.getSelectedIndex()).getAddress();
                letsGo(true);
            } else if (type == Desktop.CANCEL_CMD_TYPE)  {
                letsGo(false);
            }
        }
    }

    public static String sockType;
}

//#endif
