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

package cz.kruch.track.ui.nokia;

import cz.kruch.track.ui.Desktop;
import cz.kruch.track.Resources;

import java.util.TimerTask;

/**
 * Device control - backlight (for now).
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public abstract class DeviceControl extends TimerTask {
    
    private static DeviceControl instance;

    protected volatile int backlight;

    /** @deprecated make instance member of Desktop... ??? */
    public static void initialize() {
//#ifdef __ALL__
        try {
            Class.forName("com.nokia.mid.ui.DirectUtils");
            if (System.getProperty("com.sonyericsson.imei") == null) {
                if (cz.kruch.track.TrackingMIDlet.symbian) {
                    instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.S60DeviceControl").newInstance();
                } else {
                    instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.NokiaDeviceControl").newInstance();
                }
            } else {
                instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.SonyEricssonDeviceControl").newInstance();
            }
        } catch (Throwable t) {
            // ignore
        }
        if (instance == null) {
            try {
                Class.forName("com.siemens.mp.game.Light");
                instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.SiemensDeviceControl").newInstance();
            } catch (Throwable t) {
            }
        }
        if (instance == null) {
            try {
                Class.forName("com.samsung.util.LCDLight");
                instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.SamsungDeviceControl").newInstance();
            } catch (Throwable t) {
            }
        }
        if (instance == null) {
            try {
                Class.forName("com.motorola.multimedia.Lighting");
                instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.MotorolaDeviceControl").newInstance();
            } catch (Throwable t) {
            }
        }
        if (instance == null) {
            try {
                Class.forName("mmpp.media.BackLight");
                instance = (DeviceControl) Class.forName("cz.kruch.track.ui.nokia.LgDeviceControl").newInstance();
            } catch (Throwable t) {
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
        if (instance != null) {
            instance.close();
        }
    }

    public static void setBacklight() {
        if (instance != null) {
            instance.nextLevel();
            instance.sync();
        }
    }

    public static void flash() {
        if (instance != null) {
            if (instance.backlight == 0) {
                cz.kruch.track.ui.Desktop.display.flashBacklight(1);
            }
        }
    }

    protected final void confirm(String message) {
        Desktop.showConfirmation(message, null);
    }

    void sync() {
        confirm(backlight == 0 ? Resources.getString(Resources.DESKTOP_MSG_BACKLIGHT_OFF) : Resources.getString(Resources.DESKTOP_MSG_BACKLIGHT_ON));
    }

    void close() {
    }

    boolean forceOff() {
        return false;
    }

    void nextLevel() {
        if (backlight == 0) {
            backlight = 1;
            if (isSchedulable()) {
                cz.kruch.track.ui.Desktop.timer.scheduleAtFixedRate(this, 7500L, 7500L);
            }
        } else {
            backlight = 0;
            if (isSchedulable()) {
                cancel();
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

    abstract boolean isSchedulable();
    abstract void turnOn();
    abstract void turnOff();

    public void run() {
        if (backlight != 0) {
            turnOn();
        }
    }
}
