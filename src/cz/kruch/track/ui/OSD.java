// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import javax.microedition.lcdui.Graphics;

class OSD extends Bar {
    private static final String NO_INFO = "Lon: ? Lat: ?";

    public OSD(int gx, int gy, int width, int height) {
        super(gx, gy, width, height);
    }

    public void render(Graphics graphics) {
        if (info == null) {
            info = NO_INFO;

        }
        // draw position
        graphics.drawImage(bar, gx, gy, Graphics.TOP | Graphics.LEFT);
        graphics.setColor(255, 255, 255);
        graphics.setFont(font);
        graphics.drawString(info, gx, gy, Graphics.TOP | Graphics.LEFT);
    }
}
