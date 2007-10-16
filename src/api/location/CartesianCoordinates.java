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

package api.location;

public final class CartesianCoordinates implements GeodeticPosition {
    public double easting, northing;
    public char[] zone;

    /*
     * POOL
     */

    private static final CartesianCoordinates[] pool = new CartesianCoordinates[8];
    private static int countFree;

    public synchronized static CartesianCoordinates newInstance(char[] zone,
                                                                final double easting,
                                                                final double northing) {
        CartesianCoordinates result;

        if (countFree == 0) {
            result = new CartesianCoordinates(zone, easting, northing);
        } else {
            result = pool[--countFree];
            pool[countFree] = null;
            result.zone = zone;
            result.easting = easting;
            result.northing = northing;
        }

        return result;
    }

    public synchronized static void releaseInstance(CartesianCoordinates utm) {
        if (countFree < pool.length) {
            pool[countFree++] = utm;
        }
    }

    /*
     * ~POOL
     */

    private CartesianCoordinates(char[] zone, final double easting, final double northing) {
        this.zone = zone;
        this.easting = easting;
        this.northing = northing;
    }

    public double getH() {
        return easting;
    }

    public double getV() {
        return northing;
    }

//#ifdef __LOG__
    public String toString() {
        return (new String(zone) + " " + easting + " " + northing);
    }
//#endif
}
