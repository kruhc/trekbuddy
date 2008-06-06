// (c) Copyright 2006-2007  Hewlett-Packard Development Company, L.P. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui.nokia;

import cz.kruch.track.Resources;

/**
 * Device control implementation for Blackberry phones.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class BlackberryDeviceControl extends DeviceControl {

    private int defaultTimeout;

    BlackberryDeviceControl() {
        this.defaultTimeout = net.rim.device.api.system.Backlight.getTimeoutDefault();
        cz.kruch.track.ui.Desktop.timer.scheduleAtFixedRate(this, 0L, 60000L);
    }

    void close() {
        net.rim.device.api.system.Backlight.setTimeout(defaultTimeout);
    }

    void nextLevel() {
        if (backlight == 0) {
            backlight = 1;
            net.rim.device.api.system.Backlight.setTimeout(255);
            net.rim.device.api.system.Backlight.enable(true);
        } else {
            backlight = 0;
            net.rim.device.api.system.Backlight.setTimeout(defaultTimeout);
            net.rim.device.api.system.Backlight.enable(false);
        }
    }

    public void run() {
        if (backlight != 0) {
            net.rim.device.api.system.Backlight.enable(false);
            net.rim.device.api.system.Backlight.enable(true);
        }
    }
}
