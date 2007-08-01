// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui.nokia;

final class SiemensDeviceControl extends DeviceControl {

    SiemensDeviceControl() {
    }

    void nextLevel() {
        try {
            if (backlight == 0) {
                backlight = 1;
                com.siemens.mp.game.Light.setLightOn();
            } else {
                backlight = 0;
                com.siemens.mp.game.Light.setLightOff();
            }
            confirm("Backlight " + (backlight == 0 ? "off" : "on"));
        } catch (Throwable t) {
        }
    }
}
