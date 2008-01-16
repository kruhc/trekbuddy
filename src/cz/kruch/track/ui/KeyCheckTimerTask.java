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

package cz.kruch.track.ui;

import java.util.TimerTask;

final class KeyCheckTimerTask extends TimerTask {

    private Desktop desktop;

    KeyCheckTimerTask(Desktop desktop) {
        this.desktop = desktop;
    }

    /*
    * "A key's bit will be 1 if the key is currently down or has
    * been pressed at least once since the last time this method
    * was called."
    *
    * Therefore the dummy getKeyStates() call before invoking run().
    */
    public void run() {
        desktop.getKeyStates(); // trick
        SmartRunnable.getInstance().callSerially(desktop);
    }
}
