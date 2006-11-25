// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Font;
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

    public Console() {
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
        graphics.setColor(0, 0, 0);
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
    }

    public void show(String text) {
        if (text == null) {
            return;
        }
        y++;
        Graphics g = isS65 ? getGraphics() : graphics;
        g.setFont(font);
        g.setColor(255, 255, 255);
        g.drawString(text, BORDER, y * h, 0/*Graphics.TOP | Graphics.LEFT*/);
        flushGraphics();
    }

    public void result(int code, String text) {

        // hack
        text = "*";
        // ~hack

        int x = width - BORDER - font.stringWidth(text);
        Graphics g = isS65 ? getGraphics() : graphics;
        if (code == 0) {
            g.setColor(0, 255, 0);
        } else if (code == -1) {
            g.setColor(255, 0, 0);
            errors++;
        } else {
            g.setColor(255, 185, 0);
            skips++;
        }
        g.setFont(font);
        g.drawString(text, x, y * h, 0/*Graphics.TOP | Graphics.LEFT*/);
        flushGraphics();
    }
}
