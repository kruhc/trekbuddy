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

import javax.microedition.lcdui.Graphics;

/**
 * Status bar.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class Status extends Bar {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Status");
//#endif

    private String status;

    Status(int gx, int gy, int width, int height) {
        super(gx, gy, width, height);
        this.clip = new int[]{ gx, -1, -1, -1 };
        resize(width, height);
    }

    public void render(Graphics graphics) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("render");
//#endif
        if (!visible || status == null) {
            return;
        }

        // draw status info
        graphics.drawImage(Desktop.bar, gx, height - bh, Graphics.TOP | Graphics.LEFT);
        graphics.drawString(status, gx, height - bh, Graphics.TOP | Graphics.LEFT);
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int[] getClip() {
        if (!visible && !update)
            return null;

        clip[1] = height - bh;
        clip[2] = width;
        clip[3] = bh;

        return clip;
    }
}
