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

import cz.kruch.track.device.SymbianService;

/**
 * Device control implementation for S60 phones.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class S60DeviceControl extends NokiaDeviceControl {

    private SymbianService.Inactivity inactivity;

    S60DeviceControl() {
        this.inactivity = SymbianService.openInactivity();
    }

    protected void setLights() {
        inactivity.setLights(VALUES[backlight]);
        super.setLights();
    }

    void close() {
        inactivity.close();
        super.close();
    }
}
