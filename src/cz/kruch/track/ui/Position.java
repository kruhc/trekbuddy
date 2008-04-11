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

package cz.kruch.track.ui;

/**
 * Pixel position in map.
 *
 * @author Ales Pour <kruhc@seznam.cz>
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

    public Position clone() {
        return new Position(getX(), getY());
    }

    public boolean equals(Object obj) {
        if (obj instanceof Position) {
            final Position position = (Position) obj;
            return x == position.x && y == position.y;
        }

        return false;
    }

    // debug
    public String toString() {
        return "X=" + getX() + " Y=" + getY();
    }
    // ~debug
}
