// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import javax.microedition.lcdui.Graphics;

abstract class Bar {
    protected static final int BORDER = 2;
    
    protected int gx, gy;
    protected int width, height;
    protected int bh;

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
