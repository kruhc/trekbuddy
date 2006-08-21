// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import javax.microedition.lcdui.Graphics;

final class Status extends Bar {

    public Status(int gx, int gy, int width, int height) {
        super(gx, gy, width, height);
    }

    public void render(Graphics graphics) {
        if (!visible || info == null) {
            return;
        }

        // draw status info
        graphics.drawImage(bar, gx, height - bh, Graphics.TOP | Graphics.LEFT);
        graphics.drawString(info, gx, height - bh, Graphics.TOP | Graphics.LEFT);
    }

    public int[] getClip() {
        if (!visible && !update)
            return null;

        return new int[]{ gx, height - bh, width, bh };
    }
}
