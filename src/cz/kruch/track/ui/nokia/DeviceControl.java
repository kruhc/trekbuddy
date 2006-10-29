// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui.nokia;

import cz.kruch.track.ui.Desktop;

import java.util.Timer;
import java.util.TimerTask;

public final class DeviceControl extends TimerTask {

    private static final int NOKIA   = 0;
    private static final int SE      = 1;
    private static final int SIEMENS = 2;

    private static int phone = -1;
    private static int backlight = 0;
    private static Timer clock;

    private static DeviceControl instance;

    static {
        try {
            Class.forName("com.nokia.mid.ui.DeviceControl");
            if (System.getProperty("com.sonyericsson.imei") == null) {
                phone = NOKIA;
            } else {
                phone = SE;
            }
        } catch (ClassNotFoundException e) {
        } catch (NoClassDefFoundError e) {
        }
        if (phone == -1) {
            try {
                Class.forName("com.siemens.mp.game.Light");
                phone = SIEMENS;
            } catch (ClassNotFoundException e) {
            } catch (NoClassDefFoundError e) {
            }
        }
    }

    public static void destroy() {
        if (clock != null) {
            clock.cancel();
        }
    }

    public static void setBacklight() {
        switch (phone) {
            case NOKIA:
                setBacklightNokia();
                break;
            case SE:
                if (clock == null) {
                    clock = new Timer();
                }
                if (backlight == 0) {
                    instance = new DeviceControl();
                    clock.schedule(instance, 250, 750);
//                    clock.schedule(instance, 5000, 15000);
                }
                setBacklightSonyEricsson();
                if (backlight == 0) {
                    instance.cancel();
                }
                break;
            case SIEMENS:
                setBacklightSiemens();
                break;
        }
    }

    public static void setBacklightNokia() {
        try {
            backlight += 50;
            if (backlight == 150) {
                backlight = 0;
            }
            com.nokia.mid.ui.DeviceControl.setLights(0, backlight);
            Desktop.showConfirmation("Backlight " + backlight + "%", null);
        } catch (Throwable t) {
        }
    }

    public static void setBacklightSiemens() {
        try {
            if (backlight == 0) {
                com.siemens.mp.game.Light.setLightOn();
                backlight = 1;
            } else {
                com.siemens.mp.game.Light.setLightOff();
                backlight = 0;
            }
            Desktop.showConfirmation("Backlight " + (backlight == 0 ? "off" : "on"), null);
        } catch (Throwable t) {
        }
    }

    public static void setBacklightSonyEricsson() {
        try {
            if (backlight == 0) {
                Desktop.display.flashBacklight(1000);
//                com.nokia.mid.ui.DeviceControl.setLights(0, 100);
                backlight = 1;
            } else {
                Desktop.display.flashBacklight(0);
//                com.nokia.mid.ui.DeviceControl.setLights(0, 0); // for immediate effect
                backlight = 0;
            }
            Desktop.showConfirmation("Backlight " + (backlight == 0 ? "off" : "on"), null);
        } catch (Throwable t) {
        }
    }

    public void run() {
        if (phone == SE) {
            if (backlight == 1) {
                Desktop.display.flashBacklight(1000);
//                com.nokia.mid.ui.DeviceControl.setLights(0, 0);
//                com.nokia.mid.ui.DeviceControl.setLights(0, 100);
            }
        }
    }
}
