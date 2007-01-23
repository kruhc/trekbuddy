// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps;

import javax.microedition.lcdui.Image;

public final class Slice {
    private Calibration.Best calibration;
    private Image image;

/*
    // slice absolute position
    protected int x = -1, y = -1; // protected for direct access from Map.doFinal()

    // slice width
    private int width = -1, height = -1;
*/

    // slice range (absolute) - precomputed for better performance of isWithin
    private int maxx, maxy;

    // performance tricks
    private Object closure; // used for TarEntry for tar-ed maps

    public Slice(Calibration.Best calibration) {
        this.calibration = calibration;
    }

    public Object getClosure() {
        return closure;
    }

    public void setClosure(Object closure) {
        this.closure = closure;
    }

    public synchronized Image getImage() {
        return image;
    }

    public synchronized void setImage(Image image) {
        this.image = image;
    }

    public int getX() {
        return calibration.x;
    }

    public int getY() {
        return calibration.y;
    }

    public int getWidth() {
        return calibration.width;
    }

    public int getHeight() {
        return calibration.height;
    }

    public String getPath() {
        return calibration.getPath();
    }

    public StringBuffer appendPath(StringBuffer sb) {
        return calibration.appendPath(sb);
    }

    public void doFinal(int mapWidth, int mapHeight, int xi, int yi) {
        Calibration.Best _calibration = calibration;
        _calibration.fixDimension(mapWidth, mapHeight, xi, yi);
/*
        width = calibration.width;
        height = calibration.height;
*/
        maxx = _calibration.x + _calibration.width - 1;
        maxy = _calibration.y + _calibration.height - 1;
    }

    public boolean isWithin(int x, int y) {
        return (x >= calibration.x && x <= maxx && y >= calibration.y && y <= maxy);
    }

    public String toString() {
        return (new StringBuffer(16)).append(calibration.x).append('-').append(calibration.y).toString();
    }
}
