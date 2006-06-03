// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps;

import cz.kruch.track.location.Position;

import javax.microedition.lcdui.Image;

public class Slice {
    private Calibration calibration;
    private Image image;
    private Position position;

    // slice absolute position
    protected int absx;
    protected int absy;

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

    public void setAbsolutePosition(int x, int y) {
        absx = x;
        absy = y;
        position = new Position(x, y);
    }

    public Position getAbsolutePosition() {
        return position;
    }

    public boolean isWithin(int x, int y) {
        return (x >= absx && x <= (absx + getWidth()) && y >= absy && y <= (absy + getHeight()));
    }

    public boolean equals(Object obj) {
        if (obj instanceof Slice) {
            return getAbsolutePosition().equals(((Slice) obj).getAbsolutePosition());
        }

        return false;
    }

    // debug
    public String toString() {
        return "Slice " + absx + "x" + absy + " " + getWidth() + "x" + getHeight() + " " + image;
    }
    // ~debug
}
