// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Font;

public class Console extends Canvas {
    // spacing from horizontal edges
    private static final int BORDER = 2;

    private int y, h, width;
    private Image image;
    private Font font;
    private int errors;
    private int skips;

    public Console() {
        this.setFullScreenMode(true);
        this.y = -1;
        this.errors = this.skips = 0;
        this.width = getWidth();
        this.image = Image.createImage(width, getHeight());
        this.font = Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        this.h = font.getHeight();
        init();
    }

    private void init() {
        Graphics g = image.getGraphics();
        g.setColor(0, 0, 0);
        g.fillRect(0, 0, width, getHeight());
    }

    public void delay() {
        long delay = errors > 0 ? 2000 : (skips > 0 ? 750: 500);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
        }
    }

    public void show(String text) {
        y++;
        Graphics g = image.getGraphics();
        g.setFont(font);
        g.setColor(255, 255, 255);
        g.drawString(text, BORDER, y * h, Graphics.TOP | Graphics.LEFT);
        repaint();
    }

    public void result(int code, String text) {

        // hack
        text = "*";
        // ~hack

        int x = width - BORDER - font.stringWidth(text);
        Graphics g = image.getGraphics();
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
        g.drawString(text, x, y * h, Graphics.TOP | Graphics.LEFT);
        repaint();
    }

    protected void paint(Graphics graphics) {
        graphics.drawImage(image, 0, 0, Graphics.TOP | Graphics.LEFT);
    }
}
