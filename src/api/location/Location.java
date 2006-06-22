// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.location;

import java.util.Date;

public class Location {
    private QualifiedCoordinates coordinates;
    private long timestamp;
    private int fix;
    private int sat;

    public Location(QualifiedCoordinates coordinates, long timestamp, int fix) {
        this(coordinates, timestamp, fix, -1);
    }

    public Location(QualifiedCoordinates coordinates, long timestamp, int fix, int sat) {
        this.coordinates = coordinates;
        this.timestamp = timestamp;
        this.fix = fix;
        this.sat = sat;
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

    // debug
    public String toString() {
        return (new Date(timestamp)).toString() + ": " + coordinates.toString() + ";" + fix + ";" + sat;
    }
    // ~debug
}
