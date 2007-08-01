// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui.nokia;

final class Midp2DeviceControl extends DeviceControl {
    private long last;

    Midp2DeviceControl() {
        schedule(this, 1000L, 1000L);
    }

    void nextLevel() {
        try {
            if (backlight == 0) {
                backlight = 1;
            } else {
                backlight = 0;
            }
            cz.kruch.track.ui.Desktop.display.flashBacklight(backlight);
            confirm("Backlight " + (backlight == 0 ? "off" : "on"));
        } catch (Throwable t) {
        }
    }


    void sync() {
        last = System.currentTimeMillis();
    }

    void close() {
        cancel();
    }

    public void run() {
        if (backlight != 0) {
            final long t = System.currentTimeMillis();
            if (t - last > 5000L) {
                cz.kruch.track.ui.Desktop.display.flashBacklight(1);
                last = t;
            }
        }
    }
}
