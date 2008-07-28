// (c) Copyright 2006-2007  Hewlett-Packard Development Company, L.P. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui.nokia;

/**
 * Device control implementation for Siemens phones.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class MotorolaDeviceControl extends DeviceControl {

    MotorolaDeviceControl() {
    }

    void turnOn() {
        com.motorola.multimedia.Lighting.backlightOn();
    }

    void turnOff() {
        com.motorola.multimedia.Lighting.backlightOff();
    }
}
