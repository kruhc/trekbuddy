// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps;

import cz.kruch.track.AssertionFailedException;

import javax.microedition.lcdui.Image;

public final class Slice {
    public static final String PNG_EXT = ".png";

    private Image image;
    private Object closure;

    public Slice(String path, final boolean obsolete) throws InvalidMapException {
        this.x = this.y = this.width = this.height = -1;
        if (obsolete) { // single slice of gpska map
            x = y = 0;
        } else { // standard slice
            parseXy(path);
        }
    }

    public synchronized Image getImage() {
        return image;
    }

    public synchronized void setImage(Image image) {
        // assertion
        if (this.image != null && image != null) {
            throw new AssertionFailedException("Replacing image in slice " + this);
        }

        this.image = image;
    }

    public Object getClosure() {
        return closure;
    }

    public void setClosure(Object closure) {
        this.closure = closure;
    }

    public short getX() {
        return x;
    }

    public short getY() {
        return y;
    }

    public short getWidth() {
        return width;
    }

    public short getHeight() {
        return height;
    }

    public void doFinal(int mapWidth, int mapHeight, int xi, int yi) throws InvalidMapException {
        fixDimension(mapWidth, mapHeight, xi, yi);
    }

    public boolean isWithin(final int x, final int y) {
        final int dx = x - this.x;
        final int dy = y - this.y;

        return (dx >= 0 && dx < this.width && dy >= 0 && dy < this.height);
    }

    public String toString() {
        return (new StringBuffer(16)).append(x).append('-').append(y).append(' ').append(width).append('x').append(height).toString();
    }

    /*
     * THIS IS FROM Calibration.Best CLASS.
     */

    private short width, height;
    private short x, y;

    public static String getBasename(String path) throws InvalidMapException {
//            char[] n = path.toCharArray();
        int p0 = -1, p1 = -1;
        int i = 0;
//            for (int N = n.length - 4; i < N; i++) {
//                if ('_' == n[i]) {
        for (int N = path.length() - 4; i < N; i++) {
            if ('_' == path.charAt(i)) {
                p0 = p1;
                p1 = i;
            }
        }
        if (p0 == -1 || p1 == -1) {
            throw new InvalidMapException("Invalid slice filename: " + path);
        }

        return path.substring(0, p0);
    }

    public String getPath() {
        return (new StringBuffer(16)).append('_').append(x).append('_').append(y).append(PNG_EXT).toString();
    }

    public StringBuffer appendPath(StringBuffer sb) {
        return sb.append('_').append(x).append('_').append(y).append(PNG_EXT);
    }

    private void fixDimension(int xNext, int yNext, final int xs, final int ys) throws InvalidMapException {
        if (x + xs < xNext) {
            xNext = x + xs;
        }
        if (y + ys < yNext) {
            yNext = y + ys;
        }
        width = asShort(xNext - x);
        height = asShort(yNext - y);
    }

    private void parseXy(String path) throws InvalidMapException {
//            char[] n = path.toCharArray();
        int p0 = -1, p1 = -1;
        int i = 0;
//            for (int N = n.length - 4; i < N; i++) {
//                if ('_' == n[i]) {
        for (int N = path.length() - 4; i < N; i++) {
            if ('_' == path.charAt(i)) {
                p0 = p1;
                p1 = i;
            }
        }
        if (p0 == -1 || p1 == -1) {
            throw new InvalidMapException("Invalid slice filename: " + path);
        }

        x = asShort(parseInt(path, p0 + 1, p1));
        y = asShort(parseInt(path, p1 + 1, i));
    }

    private static short asShort(final int i) throws InvalidMapException {
        if (i > Short.MAX_VALUE) {
            throw new InvalidMapException("Slice too big: " + i);
        }

        return (short) i;
    }

    private static int parseInt(String value, int offset, final int end) {
        if (offset == end || value == null) {
            throw new NumberFormatException("No input");
        }

        int result = 0;

        while (offset < end) {
//                char ch = value[offset++];
            final char ch = value.charAt(offset++);
            if (ch >= '0' && ch <= '9') {
                result *= 10;
                result += ch - '0';
            } else {
                throw new NumberFormatException("Not a digit: " + ch);
            }
        }

        return result;
    }
}

