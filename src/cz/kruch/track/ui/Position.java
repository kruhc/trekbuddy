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

//#ifndef __NOBJPOOL__

    private static final Position[] pool = new Position[4];
    private static int countFree;

//#endif

    public static Position newInstance(final int x, final int y) {
        Position result = null;
//#ifndef __NOBJPOOL__
        synchronized (pool) {
            if (countFree > 0) {
                result = pool[--countFree];
                pool[countFree] = null;
            }
        }
//#endif
        if (result == null) {
            result = new Position();
        }
        result.x = x;
        result.y = y;

        return result;
    }

    public static void releaseInstance(final Position p) {
//#ifndef __NOBJPOOL__
        if (p != null) {
            synchronized (pool) {
                if (countFree < pool.length) {
                    pool[countFree++] = p;
                }
            }
        }
//#endif
    }

    /*
     * ~POOL
     */

    private int x, y;

    private Position() {
    }

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
