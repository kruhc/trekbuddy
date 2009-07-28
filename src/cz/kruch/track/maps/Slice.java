// @LICENSE@

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
    public static Image NO_IMAGE = Image.createImage(4, 4);

    private static final int MAX_XY_VALUE = 0x000fffff; // 1M
    private static final int MAX_WH_VALUE = 0x00000fff; // 4096

    private Image image;
    private int wx, hy;

    /**
     * Constructor for TB, J2N map slice.
     *
     * @param token slice path (as token)
     * @throws InvalidMapException when anything goes wrong
     */
    Slice(CharArrayTokenizer.Token token) throws InvalidMapException {
        this.parseXy(token);
    }

    /**
     * Constructor for GPSka map slice.
     *
     * @deprecated
     */
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

        if (this.image == NO_IMAGE) {
            return;
        }

        this.image = image;
    }

    public final int getX() {
        return wx & 0x000fffff;
    }

    public final int getY() {
        return hy & 0x000fffff;
    }

    public final int getWidth() {
        return (wx >> 20) & 0x00000fff;
    }

    public final int getHeight() {
        return (hy >> 20) & 0x00000fff;
    }

    public final boolean isWithin(final int x, final int y) {
        final int _x = getX();
        final int dx = x - _x;
        if (x >= _x && dx < getWidth()) {
            final int dy = y - getY();
            if (dy >= 0 && dy < getHeight()) {
                return true;
            }
        }
        return false;
    }

    public String toString() {
        final StringBuffer sb = new StringBuffer(32);
        NavigationScreens.append(sb, getX()).append('-');
        NavigationScreens.append(sb, getY()).append(' ');
        NavigationScreens.append(sb, getWidth()).append('x');
        NavigationScreens.append(sb, getHeight());

        return sb.toString();
    }

    public final StringBuffer appendPath(final StringBuffer sb) {
        sb.append('_');
        NavigationScreens.append(sb, getX());
        sb.append('_');
        NavigationScreens.append(sb, getY());

        return sb;
    }

    final void setDimensions(final int xmax, final int ymax, int xi, int yi) throws InvalidMapException {
        final int x = getX();
        if (x + xi > xmax) {
            xi = xmax - x;
        }
        final int y = getY();
        if (y + yi > ymax) {
            yi = ymax - y;
        }
        wx |= as12b(xi) << 20;
        hy |= as12b(yi) << 20;
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

    private void parseXy(final CharArrayTokenizer.Token token) throws InvalidMapException {
        int p0 = -1, p1 = -1;
        int i = token.begin;
        final char[] array = token.array;
        for (final int N = token.begin + token.length - 4/* extension length */; i < N; i++) {
            if ('_' == array[i]) {
                p0 = p1;
                p1 = i;
            }
        }
        if (p0 > -1 && p1 > -1) {
            wx = as20b(parseInt(array, p0 + 1, p1));
            hy = as20b(parseInt(array, p1 + 1, i));
        } else {
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_INVALID_SLICE_NAME) + token.toString());
        }
    }

    private static int as20b(final int i) throws InvalidMapException {
        if (i <= MAX_XY_VALUE) {
            return i;
        }
        throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_SLICE_TOO_BIG) + ": " + i);
    }

    private static int as12b(final int i) throws InvalidMapException {
        if (i <= MAX_WH_VALUE) {
            return i;
        }
        throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_SLICE_TOO_BIG) + ": " + i);
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

    private static int parseInt(final char[] value, int offset, final int end) {
        if (offset == end) {
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

