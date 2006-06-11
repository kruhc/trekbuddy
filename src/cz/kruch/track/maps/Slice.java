// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps;

import cz.kruch.track.ui.Position;

import javax.microedition.lcdui.Image;
import java.util.Enumeration;

public class Slice {
    private Calibration calibration;
    private Image image;

    // slice absolute position
    private Position position;

/*
    // slice range (absolute)
    private int minx, maxx, miny, maxy;
*/

    public Slice(Calibration calibration) {
        this.calibration = calibration;
    }

    public Calibration getCalibration() {
        return calibration;
    }

    public Image getImage() {
        return image;
    }

    public void setImage(Image image) {
        this.image = image;
    }

    public int getWidth() {
        return calibration.getWidth();
    }

    public int getHeight() {
        return calibration.getHeight();
    }

    public void absolutizePosition(Calibration parent) {
        position = calibration.computeAbsolutePosition(parent);
    }

    public Position getAbsolutePosition() {
        return position;
    }

    public boolean isWithin(int x, int y) {
        int minx = position.getX();
        int maxx = minx + getWidth();
        int miny = position.getY();
        int maxy = miny + getHeight();

        return (x >= minx && x <= maxx && y >= miny && y <= maxy);
    }

    public boolean equals(Object obj) {
        if (obj instanceof Slice) {
            return getAbsolutePosition().equals(((Slice) obj).getAbsolutePosition());
        }

        return false;
    }

    // debug
    public String toString() {
        return "Slice " + getCalibration().getPath() + " pos " + position + " " + getWidth() + "x" + getHeight() + " " + image;
    }
    // ~debug
}
