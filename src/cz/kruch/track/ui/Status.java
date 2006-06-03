// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.location.Position;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

class Status extends Bar{
    private int h;

    public Status(int gx, int gy, int width, int height) {
        super(gx, gy, width, height);
        this.h = font.getHeight();
    }

    public void render(Graphics graphics) {
        if (info == null) {
            // log
//            System.out.println(COMPONENT_NAME + " [debug] no message to render");

            return;
        }

        // draw position
        graphics.drawImage(bar, gx, height - h, Graphics.TOP | Graphics.LEFT);
        graphics.setColor(255, 255, 255);
        graphics.setFont(font);
        graphics.drawString(info, gx, height - h, Graphics.TOP | Graphics.LEFT);
    }
}
