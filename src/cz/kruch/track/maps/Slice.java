/*
 * Copyright 2006-2007 Ales Pour <kruhc@seznam.cz>.
 * All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package cz.kruch.track.maps;

import cz.kruch.track.Resources;
import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.ui.NavigationScreens;

import javax.microedition.lcdui.Image;

/**
 * Represents map tile.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public class Slice {
    public static final String PNG_EXT = ".png";

    private int wh, xy;
    private Image image;

    /** Constructor for TB, J2N map slice. */
/*
    Slice(String path) throws InvalidMapException {
        parseXy(path);
    }
*/

    /** Constructor for TB, J2N map slice. */
    Slice(CharArrayTokenizer.Token token) throws InvalidMapException {
        parseXy(token);
    }

    /** Constructor for GPSka map slice. */
    Slice() {
    }

    public final synchronized Image getImage() {
        return image;
    }

    public final synchronized void setImage(Image image) {
        // assertion
        if (this.image != null && image != null) {
            throw new IllegalStateException("Replacing image in slice " + this);
        }

        this.image = image;
    }

    public final int getX() {
        return (xy >> 16) & 0x0000ffff;
    }

    public final int getY() {
        return xy & 0x0000ffff;
    }

    public final int getWidth() {
        return (wh >> 16) & 0x0000ffff;
    }

    public final int getHeight() {
        return wh & 0x0000ffff;
    }

    public final boolean isWithin(final int x, final int y) {
        final int dx = x - getX();
        if (x >= getX() && dx < getWidth()) {
            final int dy = y - getY();
            if (dy >= 0 && dy < getHeight()) {
                return true;
            }
        }
        return false;
    }

    // TODO optimize
    public String toString() {
        return (new StringBuffer(16)).append(getX()).append('-').append(getY()).append(' ').append(getWidth()).append('x').append(getHeight()).toString();
    }

    public static String getBasename(String path) throws InvalidMapException {
        int p0 = -1, p1 = -1;
        int i = 0;
        for (int N = path.length() - 4; i < N; i++) {
            if ('_' == path.charAt(i)) {
                p0 = p1;
                p1 = i;
            }
        }
        if (p0 == -1 || p1 == -1) {
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_INVALID_SLICE_NAME) + ": " + path);
        }

        return path.substring(0, p0);
    }

//#ifdef __LOG__
    public String getPath() {
        return (new StringBuffer(16)).append('_').append(getX()).append('_').append(getY()).append(PNG_EXT).toString();
    }
//#endif

    public final StringBuffer appendPath(StringBuffer sb) {
        sb.append('_');
        NavigationScreens.append(sb, getX());
        sb.append('_');
        NavigationScreens.append(sb, getY());

        return sb.append(PNG_EXT);
    }

    final void doFinal(int xmax, int ymax, int xi, int yi) throws InvalidMapException {
        final int x = getX();
        final int y = getY();
        if (x + xi > xmax) {
            xi = xmax - x;
        }
        if (y + yi > ymax) {
            yi = ymax - y;
        }
        wh = asShort(xi) << 16 | asShort(yi);
    }
    
/*
    private void parseXy(String path) throws InvalidMapException {
        int p0 = -1, p1 = -1;
        int i = 0;
        for (final int N = path.length() - 4; i < N; i++) {
            if ('_' == path.charAt(i)) {
                p0 = p1;
                p1 = i;
            }
        }
        if (p0 == -1 || p1 == -1) {
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_INVALID_SLICE_NAME) + ": " + path);
        }
        xy = asShort(parseInt(path, p0 + 1, p1)) << 16 | asShort(parseInt(path, p1 + 1, i));
    }
*/

    private void parseXy(CharArrayTokenizer.Token token) throws InvalidMapException {
        int p0 = -1, p1 = -1;
        int i = token.begin;
        for (final int N = token.begin + token.length - 4; i < N; i++) {
            if ('_' == token.array[i]) {
                p0 = p1;
                p1 = i;
            }
        }
        if (p0 == -1 || p1 == -1) {
            throw new InvalidMapException("Invalid slice filename: " + token.toString());
        }
        xy = asShort(parseInt(token.array, p0 + 1, p1)) << 16 | asShort(parseInt(token.array, p1 + 1, i));
    }

    private static int asShort(final int i) throws InvalidMapException {
        if (i > Short.MAX_VALUE) {
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_SLICE_TOO_BIG) + ": " + i);
        }

        return i;
    }

/*
    private static int parseInt(String value, int offset, final int end) {
        if (offset == end || value == null) {
            throw new NumberFormatException("No input");
        }

        int result = 0;

        while (offset < end) {
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
*/

    private static int parseInt(char[] value, int offset, final int end) {
        if (offset == end || value == null) {
            throw new NumberFormatException("No input");
        }

        int result = 0;

        while (offset < end) {
            final char ch = value[offset++];
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

