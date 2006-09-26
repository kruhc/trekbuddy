// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui.nokia;

import cz.kruch.track.ui.Desktop;

public final class DeviceControl {

    private static int backlight = 0;

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
}
