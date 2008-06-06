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

/**
 * Represents a set of basic location information.
 * 
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class Location {
    private static final int FIX3D_MASK = 0x00800000;

    private QualifiedCoordinates coordinates;
    private long timestamp;
    private int fixsat;
    private float speed;
    private float course;

    /*
     * POOL
     */

    private static final Location[] pool = new Location[8];
    private static int countFree;

    public static Location newInstance(final QualifiedCoordinates coordinates,
                                       final long timestamp, final int fix) {
        return newInstance(coordinates, timestamp, fix, -1);
    }

    public static synchronized Location newInstance(final QualifiedCoordinates coordinates,
                                                    final long timestamp, final int fix,
                                                    final int sat) {
        final Location result;

        if (countFree == 0) {
            result = new Location(coordinates, timestamp, fix, sat);
        } else {
            result = pool[--countFree];
            pool[countFree] = null;
            result.coordinates = coordinates;
            result.timestamp = timestamp;
            result.fixsat = ((fix << 8) & 0x0000ff00) | (sat & 0x000000ff);
            result.speed = result.course = Float.NaN;
        }

        return result;
    }

    public static synchronized void releaseInstance(final Location location) {
        if (location != null) {
            if (countFree < pool.length) {
                pool[countFree++] = location;
            }
            QualifiedCoordinates.releaseInstance(location.coordinates);
            location.coordinates = null;
        }
    }

    /*
     * ~POOL
     */

    public Location clone() {
        final Location l = newInstance(coordinates.clone(), timestamp,
                                       getFix(), getSat());
        l.setCourse(course);
        l.setSpeed(speed);
        l.setFix3d(isFix3d());
        
        return l;
    }

    private Location(QualifiedCoordinates coordinates,
                     final long timestamp, final int fix, final int sat) {
        this.coordinates = coordinates;
        this.timestamp = timestamp;
        this.fixsat = ((fix << 8) & 0x0000ff00) | (sat & 0x000000ff);
        this.speed = this.course = Float.NaN;
    }

    public QualifiedCoordinates getQualifiedCoordinates() {
        return coordinates;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getFix() {
        return (byte) ((fixsat >> 8) & 0x000000ff);
    }

    public int getSat() {
        return (byte) (fixsat & 0x000000ff);
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public float getCourse() {
        return course;
    }

    public void setCourse(float course) {
        this.course = course;
    }

    public boolean isFix3d() {
        return (this.fixsat & FIX3D_MASK) != 0;
    }

    public void setFix3d(boolean fix3d) {
        if (fix3d)
            this.fixsat |= FIX3D_MASK;
        else
            this.fixsat &= ~FIX3D_MASK;
    }

    public boolean isSpeedValid() {
        final float accuracy = getQualifiedCoordinates().getHorizontalAccuracy();
        final float speed = this.speed;
        if (!Float.isNaN(accuracy) && !Float.isNaN(speed)) {
            if (speed > (accuracy / 10)) {
                return true;
            }
        }
        return false;
    }
}
