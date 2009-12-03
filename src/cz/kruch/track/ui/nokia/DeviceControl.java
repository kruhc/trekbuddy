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

    protected static final int STATUS_OFF = 0;
    protected static final int STATUS_ON  = 1;

    private static DeviceControl instance;

    protected int backlight;

    protected String name;
    protected String cellIdProperty, lacProperty;

    protected TimerTask task;

    //
    // public interface
    //

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

    public static String getName() {
        return instance.name;
    }

    public static void getBacklight() {
        instance.sync();
    }

    public static void setBacklight() {
        instance.nextLevel();
    }

    public static void flash() {
        if (instance.backlight == STATUS_OFF) {
            Desktop.display.flashBacklight(1);
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
        instance.useTicker(displayable, ticker);
    }

    //
    // implementation
    //

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
        confirm(backlight == STATUS_OFF ? Resources.getString(Resources.DESKTOP_MSG_BACKLIGHT_OFF) : Resources.getString(Resources.DESKTOP_MSG_BACKLIGHT_ON));
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
        // invert status and put timer task into proper state (for schedulable control)
        if (backlight == STATUS_OFF) {
            backlight = STATUS_ON;
            if (isSchedulable()) {
                Desktop.timer.scheduleAtFixedRate(task = new DeviceControl(), 7500L, 7500L);
            }
        } else {
            backlight = STATUS_OFF;
            if (/*isSchedulable()*/task != null) {
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

    //
    // interface contract
    //

    public void run() {
        if (instance.backlight != STATUS_OFF) {
            instance.turnOn();
        }
    }
}
