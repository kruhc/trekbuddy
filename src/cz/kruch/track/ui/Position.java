// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

public final class Position {

    /*
     * POOL
     */

    private static final Position[] pool = new Position[4];
    private static int countFree;

    public synchronized static Position newInstance(int x, int y) {
        Position result;

        if (countFree == 0) {
            result = new Position(x, y);
        } else {
            result = pool[--countFree];
            if (result == null) throw new RuntimeException("NULL");
            result.x = (short) x;
            result.y = (short) y;
        }

        return result;
    }

    public synchronized static void releaseInstance(Position p) {
        if (countFree < pool.length && p != null) {
            pool[countFree++] = p;
        }
    }

    /*
     * ~POOL
     */

    protected short x, y;

    public Position(int x, int y) {
        this.x = (short) x;
        this.y = (short) y;
    }

    public void setXy(int x, int y) {
        this.x = (short) x;
        this.y = (short) y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public Position clone() {
        return new Position(x, y);
    }

    public boolean equals(Object obj) {
        if (obj instanceof Position) {
            Position position = (Position) obj;
            return x == position.x && y == position.y;
        }

        return false;
    }

    // debug
    public String toString() {
        return "X=" + x + " Y=" + y;
    }
    // ~debug
}
