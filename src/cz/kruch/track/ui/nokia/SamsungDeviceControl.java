// (c) Copyright 2006-2007  Hewlett-Packard Development Company, L.P. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui.nokia;

/**
 * Device control implementation for Siemens phones.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class SamsungDeviceControl extends DeviceControl {

    SamsungDeviceControl() {
    }

    boolean isSchedulable() {
        return true;
    }

    boolean forceOff() {
        return true;
    }

    void turnOn() {
        com.samsung.util.LCDLight.on(10000);
    }

    void turnOff() {
        com.samsung.util.LCDLight.off();
    }
}
