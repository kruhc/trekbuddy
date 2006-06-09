// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import api.location.Location;
import api.location.QualifiedCoordinates;

import java.util.Date;

public class SimplifiedLocation extends Location {
    private QualifiedCoordinates qualifiedCoordinates;
    private long timestamp;

    public SimplifiedLocation(QualifiedCoordinates qualifiedCoordinates, long timestamp) {
        this.qualifiedCoordinates = qualifiedCoordinates;
        this.timestamp = timestamp;
    }

    public QualifiedCoordinates getQualifiedCoordinates() {
        return qualifiedCoordinates;
    }

    public long getTimestamp() {
        return timestamp;
    }

    // debug

    public String toString() {
        return (new Date(timestamp)).toString() + ": " + qualifiedCoordinates.toString();
    }
    // ~debug
}
