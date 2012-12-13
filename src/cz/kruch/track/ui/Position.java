// @LICENSE@

package cz.kruch.track.ui;

/**
 * Pixel position in map.
 *
 * @author kruhc@seznam.cz
 */
public final class Position {

    /*
     * POOL
     */

    private static final Position[] pool = new Position[4];
    private static int countFree;

    public static synchronized Position newInstance(final int x, final int y) {
        final Position result;

        if (countFree == 0) {
            result = new Position(x, y);
        } else {
            result = pool[--countFree];
            pool[countFree] = null;
            result.setXy(x, y);
        }

        return result;
    }

    public static synchronized void releaseInstance(final Position p) {
        if (p != null && countFree < pool.length) {
            pool[countFree++] = p;
        }
    }

    /*
     * ~POOL
     */

    private int x, y;

    public Position(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setXy(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public Position _clone() {
        return new Position(getX(), getY());
    }

    public boolean equals(Object obj) {
        if (obj instanceof Position) {
            final Position position = (Position) obj;
            return x == position.x && y == position.y;
        }

        return false;
    }

//#ifdef __LOG__
    public String toString() {
        return "Position@" + hashCode() + " {X=" + getX() + " Y=" + getY() + "}";
    }
//#endif
}
