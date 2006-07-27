// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

public class Position {
    protected int x, y;

    public Position(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
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
