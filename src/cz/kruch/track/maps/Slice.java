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

    Slice() {
    }

    public final synchronized Image getImage() {
        return image;
    }

    public final synchronized void setImage(Image image) {
        // assertion
        if (this.image != null && image != null) {
            throw new IllegalStateException("Replacing image in tile " + this);
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

    public final long getXyLong() {
        return (long) getX() << 20 | getY();
    }

    // TODO
    public int hashCode() {
        return super.hashCode();
    }

    public boolean equals(Object object) {
        if (object instanceof Slice) {
            Slice s = (Slice) object;
            return s.wx == wx && s.hy == hy;
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

    final void setRect(final int x, final int y, final int w, final int h) {
        wx = w << 20 | x;
        hy = h << 20 | y;
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

    static long parseXyLong(final CharArrayTokenizer.Token token) throws InvalidMapException {
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
            return (long) as20b(parseInt(array, p0 + 1, p1)) << 20 | as20b(parseInt(array, p1 + 1, i));
        } else {
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_INVALID_SLICE_NAME) + token.toString());
        }
    }
}

