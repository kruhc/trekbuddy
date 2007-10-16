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

import cz.kruch.track.Resources;

/**
 * Generic implementation. I doubt it works well...
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class Midp2DeviceControl extends DeviceControl {

    Midp2DeviceControl() {
        cz.kruch.track.ui.Desktop.timer.scheduleAtFixedRate(this, 0L, 5000L);
    }

    void nextLevel() {
        try {
            if (backlight == 0) {
                backlight = 1;
            } else {
                backlight = 0;
            }
            confirm(backlight == 0 ? Resources.getString(Resources.DESKTOP_MSG_BACKLIGHT_OFF) : Resources.getString(Resources.DESKTOP_MSG_BACKLIGHT_ON));
        } catch (Throwable t) {
        }
    }

    void close() {
        cancel();
    }

    public void run() {
        if (backlight != 0) {
            cz.kruch.track.ui.Desktop.display.flashBacklight(1);
        }
    }
}
