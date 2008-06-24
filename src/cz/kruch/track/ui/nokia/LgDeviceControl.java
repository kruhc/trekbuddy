// (c) Copyright 2006-2007  Hewlett-Packard Development Company, L.P. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui.nokia;

/**
 * Device control implementation for Siemens phones.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class LgDeviceControl extends DeviceControl {

    LgDeviceControl() {
    }

    boolean isSchedulable() {
        return false;
    }

    void turnOn() {
        mmpp.media.BackLight.on(0); // "If timeout is 0, turns on permanently."
    }

    void turnOff() {
        mmpp.media.BackLight.off();
    }
}
