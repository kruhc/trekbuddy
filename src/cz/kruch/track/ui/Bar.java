// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

abstract class Bar {
    protected static final int BORDER = 2;
    
    protected int gx, gy;
    protected int width, height;
    protected Image bar;
    protected Font font;

    protected String info;
    protected boolean ok;

    protected Bar(int gx, int gy, int width, int height) {
        this.gx = gx;
        this.gy = gy;
        this.width = width;
        this.height = height;
        this.font = Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        init();
    }

    private void init() {
        int h = font.getHeight();
        int[] shadow = new int[width * h];
        for (int N = shadow.length, i = 0; i < N; i++) {
            shadow[i] = 0xaf7f7f7f;
        }
        bar = Image.createRGBImage(shadow, width, h, true);
    }

    public void setInfo(String info, boolean ok) {
        this.info = info;
        this.ok = ok;
    }

    public abstract void render(Graphics graphics);
}
