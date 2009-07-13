// @LICENSE@

package cz.kruch.track.ui.nokia;

import cz.kruch.track.ui.Desktop;
import cz.kruch.track.Resources;

import java.util.TimerTask;

/**
 * Device control - backlight (for now).
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public class DeviceControl extends TimerTask {
    
    private static DeviceControl instance;

    protected int backlight;

    protected String name;
    protected String cellIdProperty, lacProperty;

    private TimerTask task;

    public static void initialize() {
//#ifdef __ALL__
        if (cz.kruch.track.TrackingMIDlet.symbian) {
            try {
                instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.S60DeviceControl").newInstance();
            } catch (Throwable t) {
                // user denied connection
            }
        }
        if (instance == null) {
            try {
                Class.forName("com.nokia.mid.ui.DirectUtils");
                if (cz.kruch.track.TrackingMIDlet.jbed) {
                    // do nothing
                } else if (cz.kruch.track.TrackingMIDlet.sonyEricssonEx) {
                    instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.SonyEricssonDeviceControl").newInstance();
                } else {
                    instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.NokiaDeviceControl").newInstance();
                }
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
                Class.forName("mmpp.media.BackLight");
                instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.LgDeviceControl").newInstance();
                cz.kruch.track.TrackingMIDlet.lg = true;
            } catch (Throwable t) {
                // ignore
            }
        }
//#endif
//#ifdef __RIM__
        if (instance == null) {
            try {
                Class.forName("net.rim.device.api.system.Backlight");
                instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.BlackberryDeviceControl").newInstance();
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

    public static String getName() {
        return instance.name;
    }

    public static void setBacklight() {
        instance.nextLevel();
        instance.sync();
    }

    public static void flash() {
        if (instance.backlight == 0) {
            Desktop.display.flashBacklight(1);
        }
    }

    public static String getGsmCellId() {
        return instance.getCellId();
    }

    public static String getGsmLac() {
        return instance.getLac();
    }

    final void confirm(String message) {
        Desktop.showConfirmation(message, null);
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

    void sync() {
        confirm(backlight == 0 ? Resources.getString(Resources.DESKTOP_MSG_BACKLIGHT_OFF) : Resources.getString(Resources.DESKTOP_MSG_BACKLIGHT_ON));
    }

    void close() {
        // restore original setting?
        // ...
        // cancel running task?
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    boolean forceOff() {
        return false;
    }

    void nextLevel() {
        if (backlight == 0) {
            backlight = 1;
            if (isSchedulable()) {
                Desktop.timer.scheduleAtFixedRate(task = new DeviceControl(), 7500L, 7500L);
            }
        } else {
            backlight = 0;
            if (/*isSchedulable()*/task != null) {
                task.cancel();
                task = null;
            }
        }
        if (backlight == 0) {
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

    public void run() {
        if (instance.backlight != 0) {
            instance.turnOn();
        }
    }
}
