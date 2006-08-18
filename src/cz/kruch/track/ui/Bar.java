// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.TrackingMIDlet;

import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

abstract class Bar {
    protected static final int BORDER = 2;
    
    protected int gx, gy;
    protected int width, height;
    protected Image bar;

    protected volatile String info;
    protected volatile boolean ok;

    protected boolean visible = true;

    protected Bar(int gx, int gy, int width, int height) {
        this.gx = gx;
        this.gy = gy;
        this.width = width;
        this.height = height;
        init();
    }

    private void init() {
        int h = Desktop.font.getHeight();
        int color = TrackingMIDlet.numAlphaLevels() > 2 ? 0x807f7f7f : 0xff7f7f7f;
        int[] shadow = new int[width * h];
        for (int i = shadow.length; --i >= 0; ) {
            shadow[i] = color;
        }
        bar = Image.createRGBImage(shadow, width, h, true);
    }

    public void setInfo(String info, boolean ok) {
        this.info = info;
        this.ok = ok;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public abstract int[] getClip();

    public abstract void render(Graphics graphics);
}
