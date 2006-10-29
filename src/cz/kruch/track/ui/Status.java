// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import javax.microedition.lcdui.Graphics;

final class Status extends Bar {

    private int[] clip;

    public Status(int gx, int gy, int width, int height) {
        super(gx, gy, width, height);
        this.clip = new int[]{ gx, -1, -1, -1 };
        resize(width, height);
    }

    public void render(Graphics graphics) {
        if (!visible || info == null) {
            return;
        }

        // draw status info
        graphics.drawImage(bar, gx, height - bh, 0/*Graphics.TOP | Graphics.LEFT*/);
        graphics.drawString(info, gx, height - bh, 0/*Graphics.TOP | Graphics.LEFT*/);
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
