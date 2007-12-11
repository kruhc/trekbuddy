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
 * Base class for {@link OSD} and {@link Status}.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
abstract class Bar {
    protected static final int BORDER = 2;
    
    protected final int gx, gy;
    protected int width, height;
    protected final int bh;

    protected int[] clip;

    protected boolean visible;
    protected boolean update;

    protected Bar(int gx, int gy, int width, int height) {
        this.gx = gx;
        this.gy = gy;
        this.width = width;
        this.height = height;
        this.visible = true;
        this.bh = Desktop.font.getHeight();
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        this.update = true;
    }

    public abstract int[] getClip();
    public abstract void render(Graphics graphics);
}
