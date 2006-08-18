// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps;

import cz.kruch.track.ui.Position;

import javax.microedition.lcdui.Image;
import java.util.Vector;

public final class Slice {
    private Calibration.Best calibration;
    private Image image;

    // slice absolute position
    protected int x = -1, y = -1; // protected for direct access from Map.doFinal()

    // slice width
    private int width = -1, height = -1;

    // slice range (absolute) - precomputed for better performance of isWithin
    private int maxx, maxy;

    // performance tricks
    private Object closure; // used for TarEntry for tar-ed maps

    public Slice(Calibration.Best calibration) {
        this.calibration = calibration;
    }

    public Slice(Calibration.Best calibration, Object closure) {
        this.calibration = calibration;
        this.closure = closure;
    }

    public Object getClosure() {
        return closure;
    }

    public synchronized Image getImage() {
        return image;
    }

    public synchronized void setImage(Image image) {
        this.image = image;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getURL() {
        return calibration.getPath();
    }

    public void doFinal(boolean friendly) throws InvalidMapException {
        calibration.computeAbsolutePosition(friendly);
        x = calibration.x;
        y = calibration.y;
    }

    public void doFinal(int mapWidth, int mapHeight, int xi, int yi) {
        calibration.fixDimension(mapWidth, mapHeight, xi, yi);
        width = calibration.width;
        height = calibration.height;
        maxx = x + width - 1;
        maxy = y + height - 1;
    }

    public boolean isWithin(int x, int y) {
        return (x >= this.x && x <= maxx && y >= this.y && y <= maxy);
    }

    // debug
    public String toString() {
        return "Slice " + getURL() + " pos " + x + "-" + y + " " + getWidth() + "x" + getHeight() + " " + image;
    }
    // ~debug
}
