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
    private QualifiedCoordinates coordinates;
    private long timestamp;
    private short fix;
    private short sat;
    private float speed;
    private float course;
    private boolean fix3d;

    /*
     * POOL
     */

    private static final Location[] pool = new Location[8];
    private static int countFree;

    public static Location newInstance(QualifiedCoordinates coordinates,
                                       final long timestamp, final int fix) {
        return newInstance(coordinates, timestamp, fix, -1);
    }

    public synchronized static Location newInstance(QualifiedCoordinates coordinates,
                                                    final long timestamp, final int fix,
                                                    final int sat) {
        Location result;

        if (countFree == 0) {
            result = new Location(coordinates, timestamp, fix, sat);
        } else {
            result = pool[--countFree];
            pool[countFree] = null;
            result.coordinates = coordinates;
            result.timestamp = timestamp;
            result.fix = (short) fix;
            result.sat = (short) sat;
        }

        return result;
    }

    public synchronized static void releaseInstance(Location location) {
        if (location != null) {
            if (countFree < pool.length) {
                pool[countFree++] = location;
            }
            QualifiedCoordinates.releaseInstance(location.getQualifiedCoordinates());
            location.coordinates = null;
        }
    }

    /*
     * ~POOL
     */

    public Location clone() {
        Location l = newInstance(coordinates.clone(), timestamp, fix, sat);
        l.setCourse(course);
        l.setSpeed(speed);
        l.setFix3d(fix3d);
        return l;
    }

    private Location(QualifiedCoordinates coordinates,
                     final long timestamp, final int fix, final int sat) {
        this.coordinates = coordinates;
        this.timestamp = timestamp;
        this.fix = (short) fix;
        this.sat = (short) sat;
        this.speed = this.course = -1F;
    }

    protected Location(QualifiedCoordinates coordinates,
                       final long timestamp, final int fix) {
        this(coordinates, timestamp, fix, -1);
    }

    public QualifiedCoordinates getQualifiedCoordinates() {
        return coordinates;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getFix() {
        return fix;
    }

    public int getSat() {
        return sat;
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
        return fix3d;
    }

    public void setFix3d(boolean fix3d) {
        this.fix3d = fix3d;
    }
}
