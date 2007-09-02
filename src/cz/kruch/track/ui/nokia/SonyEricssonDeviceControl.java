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

/**
 * Device control implementation for SonyEricsson phones.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class SonyEricssonDeviceControl extends DeviceControl {

    SonyEricssonDeviceControl() {
        cz.kruch.track.ui.Desktop.timer.scheduleAtFixedRate(this, 0L, 5000L);
    }

    void nextLevel() {
        try {
            if (backlight == 0) {
                backlight = 1;
            } else {
                backlight = 0;
            }
            confirm("Backlight " + (backlight == 0 ? "off" : "on"));
        } catch (Throwable t) {
        }
    }

    void close() {
        cancel();
    }

    public void run() {
        if (backlight != 0) {
            cz.kruch.track.ui.Desktop.display.flashBacklight(1);
            com.nokia.mid.ui.DeviceControl.setLights(0, 100);
        }
    }
}
