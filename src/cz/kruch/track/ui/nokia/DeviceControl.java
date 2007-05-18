// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui.nokia;

import cz.kruch.track.ui.Desktop;

import java.util.TimerTask;

public abstract class DeviceControl extends TimerTask {
    private static DeviceControl instance;

    protected static int backlight = 0;

    public static void initialize() {
        try {
            Class.forName("com.nokia.mid.ui.DeviceControl");
            if (System.getProperty("com.sonyericsson.imei") == null) {
                instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.NokiaDeviceControl").newInstance();
            } else {
                instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.SonyEricssonDeviceControl").newInstance();
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

    protected final void confirm(String message) {
        Desktop.showConfirmation(message, null);
    }

    abstract void nextLevel();
    void close() {}
    public void run() {}
}
