// @LICENSE@

package cz.kruch.track.ui.nokia;

import java.util.TimerTask;

/**
 * Device specific control.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public class DeviceControl extends TimerTask {

    protected static final int REFRESH_PERIOD = 4500; // minimum screensaver timeout is usually 5 secs
    protected static final int STATUS_OFF = 0;
    protected static final int STATUS_ON  = 1;

    private static DeviceControl instance;
    private static String sensorStatus;

    protected int backlight, presses;

    protected String name;
    protected String cellIdProperty, lacProperty;

    protected TimerTask task;

    //
    // public interface
    //

    public static void initialize() {
//#ifdef __SYMBIAN__
        if (instance == null) {
            try {
                if (cz.kruch.track.TrackingMIDlet.nokiaui14) {
                    instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.NokiaUi14DeviceControl").newInstance();
                } else {
                    instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.S60DeviceControl").newInstance();
                }
            } catch (Throwable t) {
                // ignore
            }
        }
//#elifdef __ALL__
        if (instance == null) {
            try {
                Class.forName("com.samsung.util.LCDLight");
                instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.SamsungDeviceControl").newInstance();
                cz.kruch.track.TrackingMIDlet.samsung = true;
            } catch (Throwable t) {
                // ignore
            }
        }
        if (instance == null) {
            try {
                Class.forName("com.motorola.multimedia.Lighting");
                instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.MotorolaDeviceControl").newInstance();
                cz.kruch.track.TrackingMIDlet.motorola = true;
            } catch (Throwable t) {
                // ignore
            }
        }
        if (instance == null) {
            try {
                Class.forName("com.motorola.funlight.FunLight");
                instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.Motorola2DeviceControl").newInstance();
                cz.kruch.track.TrackingMIDlet.motorola = true;
            } catch (Throwable t) {
                // ignore
            }
        }
        if (instance == null) {
            try {
                Class.forName("mmpp.media.BackLight");
                instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.LgDeviceControl").newInstance();
                cz.kruch.track.TrackingMIDlet.lg = true;
            } catch (Throwable t) {
                // ignore
            }
        }
        if (instance == null) {
            try {
                Class.forName("com.siemens.mp.game.Light");
                instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.SiemensDeviceControl").newInstance();
            } catch (Throwable t) {
                // ignore
            }
        }
        if (instance == null) {
            try {
                Class.forName("com.nokia.mid.ui.DeviceControl");
                if (cz.kruch.track.TrackingMIDlet.sonyEricssonEx) {
                    instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.SonyEricssonDeviceControl").newInstance();
                } else {
                    instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.NokiaDeviceControl").newInstance();
                }
            } catch (Throwable t) {
                // ignore
            }
        }
//#elifdef __RIM__
        if (instance == null) {
            try {
                Class.forName("net.rim.device.api.system.Backlight");
                instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.BlackberryDeviceControl").newInstance();
            } catch (Throwable t) {
                // ignore
            }
        }
//#elifdef __ANDROID__
        if (instance == null) {
            try {
                instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.AndroidDeviceControl").newInstance();
            } catch (Throwable t) {
                // ignore
            }
        }
//#endif
        if (instance == null) {
            try {
                instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.Midp2DeviceControl").newInstance();
            } catch (Throwable t) {
                // ignore
            }
        }
    }

    public static void destroy() {
        instance.close();
    }

    public static void senseOn(api.location.LocationListener listener) {
        instance.sense(listener);
    }

    public static void senseOff(api.location.LocationListener listener) {
        instance.nonsense(listener);
    }

    public static String getName() {
        return instance.name;
    }

    public static void getBacklight() {
        instance.sync();
    }

    public static void setBacklight() {
        instance.next();
    }

    public static String getLevel() {
        return instance.level();
    }

    public static void flash() {
        if (instance.backlight == STATUS_OFF) {
            cz.kruch.track.ui.Desktop.display.flashBacklight(1);
        }
    }

    public static String getGsmCellId() {
        return instance.getCellId();
    }

    public static String getGsmLac() {
        return instance.getLac();
    }

    public static void setTicker(javax.microedition.lcdui.Displayable displayable,
                                 String ticker) {
        if (displayable != null) {
            instance.useTicker(displayable, ticker);
        }
    }

    public static int getBacklightStatus() {
        return instance.backlight;
    }

    public static String getSensorStatus() {
        return sensorStatus;
    }

//#ifdef __RIM__

    public static void loadAltDatadir() {
        instance.loadDatadir();
    }

    public static void saveAltDatadir() {
        instance.saveDatadir();
    }

//#endif
    
    //
    // default implementation
    //

//#ifndef __ANDROID__
    private cz.kruch.track.event.Callback sensor;
//#endif

    void sense(api.location.LocationListener listener) {
//#ifndef __ANDROID__
        if (cz.kruch.track.TrackingMIDlet.jsr179) {
            try {
                sensor = (cz.kruch.track.event.Callback) Class.forName("cz.kruch.track.location.Jsr179OrientationProvider").newInstance();
                SensorAction.exec(SensorAction.ACTION_START, sensor, listener);
            } catch (Throwable t) {
                // ignore
            }
        }
//#endif
    }

    void nonsense(api.location.LocationListener listener) {
//#ifndef __ANDROID__
        if (sensor != null) {
            SensorAction.exec(SensorAction.ACTION_STOP, sensor, listener);
            sensor = null; // gc hint
        }
//#endif
    }

    String getCellId() {
        if (cellIdProperty != null) {
            return System.getProperty(cellIdProperty);
        }
        return null;
    }

    String getLac() {
        if (lacProperty != null) {
            return System.getProperty(lacProperty);
        }
        return null;
    }

    void close() {
        // cancel running task, if any
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    boolean forceOff() {
        return false;
    }

    void next() {
        if (presses++ == 0) {
            nextLevel();
            confirm();
        }
    }

    void sync() {
        if (presses == 0) {
            nextLevel();
            confirm();
        }
        presses = 0;
    }

    void confirm() {
        cz.kruch.track.ui.Desktop.vibrate(100);
    }

    String level() {
        return backlight == STATUS_OFF ? "off" : "on";
    }

    void nextLevel() {
        // invert status and put timer task into proper state (for schedulable control)
        if (backlight == STATUS_OFF) {
            backlight = STATUS_ON;
            if (isSchedulable()) {
                cz.kruch.track.ui.Desktop.scheduleAtFixedRate(task = new DeviceControl(),
                                                              REFRESH_PERIOD, REFRESH_PERIOD);
            }
        } else {
            backlight = STATUS_OFF;
            if (task != null/*isSchedulable()*/) {
                task.cancel();
                task = null;
            }
        }
        if (backlight == STATUS_OFF) {
            if (isSchedulable()) {
                if (forceOff()) {
                    turnOff();
                }
            } else {
                turnOff();
            }
        } else {
            turnOn();
        }
    }

    boolean isSchedulable() {
        return false;
    }
    
    void turnOn() {
        throw new IllegalStateException("override");
    }

    void turnOff() {
        throw new IllegalStateException("override");
    }

    void useTicker(Object list, String msg) {
        if (msg != null) {
            ((javax.microedition.lcdui.Displayable) list).setTicker(new javax.microedition.lcdui.Ticker(msg));
        } else {
            ((javax.microedition.lcdui.Displayable) list).setTicker(null);
        }
    }

//#ifdef __RIM__

    void loadDatadir() {
    }

    void saveDatadir() {
    }

//#endif

    //
    // interface contract
    //

    public void run() {
        if (instance.backlight != STATUS_OFF) {
            instance.turnOn();
        }
    }

    //
    // sensor helper
    //

//#ifndef __ANDROID__

    private static class SensorAction implements Runnable {
        static final int ACTION_START = 0;
        static final int ACTION_STOP  = 1;

        private int action;
        private cz.kruch.track.event.Callback callback;
        private api.location.LocationListener listener;

        static void exec(final int action,
                         final cz.kruch.track.event.Callback callback,
                         final api.location.LocationListener listener) {
            cz.kruch.track.ui.Desktop.getDiskWorker().enqueue(new SensorAction(action, callback, listener));
        }

        private SensorAction(int action,
                             cz.kruch.track.event.Callback callback,
                             api.location.LocationListener listener) {
            this.action = action;
            this.callback = callback;
            this.listener = listener;
        }

        public void run() {
            switch (action) {
                case ACTION_START: {
                    try {
                        final String[] status = new String[1];
                        callback.invoke(new Integer(2), null, status);
                        sensorStatus = status[0];
                        callback.invoke(new Integer(0), null, listener);
                    } catch (Throwable t) {
                        // ignore
                    }
                } break;
                case ACTION_STOP: {
                    callback.invoke(new Integer(1), null, null);
                } break;
            }
        }
    }

//#endif

}
