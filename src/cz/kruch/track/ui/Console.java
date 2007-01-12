// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.game.GameCanvas;

public final class Console extends GameCanvas {
    // spacing from horizontal edges
    private static final int BORDER = 2;

    private int y, h, width;
    private Font font;
    private Graphics graphics;
    private int errors;
    private int skips;

    // relict
    private boolean isS65 = false;

    public Console(Display display) {
        super(true);
        this.setFullScreenMode(true);
        this.y = -1;
        this.errors = this.skips = 0;
        this.width = getWidth();
        this.font = Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        this.h = font.getHeight();
        this.graphics = getGraphics();
//#ifdef __S65__
        this.isS65 = cz.kruch.track.TrackingMIDlet.isS65();
//#endif
        graphics.setColor(0x0);
        graphics.fillRect(0, 0, width, getHeight());
    }

    public void delay() {
        long delay = errors > 0 ? 1500 : (skips > 0 ? 750 : 0);
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
            }
        }

        // we are about to be gone forever....
        graphics = null;
        font = null;
    }

    public void show(String text) {
        if (text == null) {
            return;
        }
        y++;
        Graphics g = graphics;
//#ifdef __S65__
        if (isS65) {
            g = getGraphics();
        }
//#endif
        g.setFont(font);
        g.setColor(0x00FFFFFF);
        g.drawString(text, BORDER, y * h, 0/*Graphics.TOP | Graphics.LEFT*/);
        flushGraphics();
    }

    public void result(int code, String text) {

        // hack
        text = "*";
        // ~hack

        int x = width - BORDER - font.stringWidth(text);
        Graphics g = graphics;
//#ifdef __S65__
        if (isS65) {
            g = getGraphics();
        }
//#endif
        if (code == 0) {
            g.setColor(0x0000FF00);
        } else if (code == -1) {
            g.setColor(0x00FF0000);
            errors++;
        } else {
            g.setColor(0x00FFB900);
            skips++;
        }
        g.setFont(font);
        g.drawString(text, x, y * h, 0/*Graphics.TOP | Graphics.LEFT*/);
        flushGraphics();
    }
}
