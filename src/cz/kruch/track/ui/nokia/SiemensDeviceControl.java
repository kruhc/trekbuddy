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
 * Device control implementation for Siemens phones.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class SiemensDeviceControl extends DeviceControl {

    SiemensDeviceControl() {
    }

    boolean isSchedulable() {
        return false;
    }

    void turnOn() {
        com.siemens.mp.game.Light.setLightOn();
    }

    void turnOff() {
        com.siemens.mp.game.Light.setLightOff();
    }
}
