// (c) Copyright 2006-2007  Hewlett-Packard Development Company, L.P. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui.nokia;

/**
 * Device control implementation for Blackberry phones.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class BlackberryDeviceControl extends DeviceControl {

    BlackberryDeviceControl() {
    }

    boolean isSchedulable() {
        return true;
    }

    void turnOn() {
        net.rim.device.api.system.Backlight.enable(true);
    }

    void turnOff() {
        net.rim.device.api.system.Backlight.enable(false);
    }

    /** @overriden */
    public void run() {
        if (backlight != 0) {
            net.rim.device.api.system.Backlight.enable(false);
            net.rim.device.api.system.Backlight.enable(true);
        }
    }
}
