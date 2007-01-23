// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

final class Status extends Bar {

    private String status;

    public Status(int gx, int gy, int width, int height, Image bar) {
        super(gx, gy, width, height, bar);
        this.clip = new int[]{ gx, -1, -1, -1 };
        resize(width, height, bar);
    }

    public void render(Graphics graphics) {
        if (!visible || status == null) {
            return;
        }

        // draw status info
        graphics.drawImage(bar, gx, height - bh, 0/*Graphics.TOP | Graphics.LEFT*/);
        graphics.drawString(status, gx, height - bh, 0/*Graphics.TOP | Graphics.LEFT*/);
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
