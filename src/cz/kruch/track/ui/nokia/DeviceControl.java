// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui.nokia;

import cz.kruch.track.ui.Desktop;

import java.util.TimerTask;

public abstract class DeviceControl extends TimerTask {
    private static DeviceControl instance;

    protected volatile int backlight;

    /** @deprecated make instance member of Desktop */
    public static void initialize() {
//#ifndef __RIM__
        try {
            Class.forName("com.nokia.mid.ui.DeviceControl");
            if (System.getProperty("com.sonyericsson.imei") == null) {
                instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.NokiaDeviceControl").newInstance();
            }
        } catch (Throwable t) {
        }
        if (instance == null) {
            try {
                Class.forName("com.siemens.mp.game.Light");
                instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.SiemensDeviceControl").newInstance();
            } catch (Throwable t) {
            }
        }
//#endif
        if (instance == null) {
            try {
                instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.Midp2DeviceControl").newInstance();
            } catch (Throwable t) {
            }
        }
    }

    public static void destroy() {
        if (instance != null) {
            instance.close();
        }
    }

    public static void setBacklight() {
        if (instance != null) {
            instance.nextLevel();
        }
    }

    public static void flash() {
        if (instance != null) {
            if (instance.backlight == 0) {
                cz.kruch.track.ui.Desktop.display.flashBacklight(1);
            }
        }
    }

    public static void keyReleased() {
        if (instance != null) {
            instance.sync();
        }
    }

    protected final void confirm(String message) {
        Desktop.showConfirmation(message, null);
    }

    protected final void schedule(TimerTask task, final long delay, final long period) {
        Desktop.timer.scheduleAtFixedRate(task, delay, period);
    }

    abstract void nextLevel();
    void close() {}
    void sync() {}
    public void run() {}
}
