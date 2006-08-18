// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import javax.microedition.lcdui.Graphics;

final class Status extends Bar {
    private int h;

    public Status(int gx, int gy, int width, int height) {
        super(gx, gy, width, height);
        this.h = bar.getHeight();
    }

    public void render(Graphics graphics) {
        if (!visible || info == null) {
            return;
        }

        // draw status info
        graphics.drawImage(bar, gx, height - h, Graphics.TOP | Graphics.LEFT);
        graphics.drawString(info, gx, height - h, Graphics.TOP | Graphics.LEFT);
    }

    public int[] getClip() {
        if (!visible)
            return null;

        return new int[]{ gx, height - h, width, h };
    }
}
