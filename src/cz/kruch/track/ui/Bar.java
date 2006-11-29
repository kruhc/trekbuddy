// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Graphics;

abstract class Bar {
    protected static final int BORDER = 2;
    
    protected int gx, gy;
    protected int width, height;
    protected Image bar;
    protected int bh;

    protected volatile String info;
    protected volatile boolean ok;

    protected boolean visible = true;
    protected boolean update = false;

    protected Bar(int gx, int gy, int width, int height, Image bar) {
        this.gx = gx;
        this.gy = gy;
        this.width = width;
        this.height = height;
        this.bar = bar;
        this.bh = Desktop.font.getHeight();
    }

    public void resize(int width, int height, Image bar) {
        this.width = width;
        this.height = height;
        this.bar = bar;
    }

    public void setInfo(String info, boolean ok) {
        this.info = null;
        this.info = info;
        this.ok = ok;
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
