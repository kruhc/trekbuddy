// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.location;

public class Location {
    protected Location() {
    }

    public QualifiedCoordinates getQualifiedCoordinates() {
        return null;
    }

    public long getTimestamp() {
        return System.currentTimeMillis();
    }
}
