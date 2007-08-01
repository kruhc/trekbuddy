// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import api.location.Location;
import api.location.QualifiedCoordinates;

public final class Waypoint {
    private String name;
    private String comment;
    private QualifiedCoordinates coordinates;
    private long timestamp;

    private Object userObject;
    private String linkPath;

    public Waypoint(QualifiedCoordinates qc, String name, String comment) {
        this(qc, name, comment, -1);
    }

    public Waypoint(QualifiedCoordinates qc, String name, String comment, long timestamp) {
        this.name = name;
        this.comment = comment;
        this.timestamp = timestamp;
        this.coordinates = qc;
    }

    public QualifiedCoordinates getQualifiedCoordinates() {
        return coordinates;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getName() {
        return name;
    }

    public String getComment() {
        return comment;
    }

    public String getLinkPath() {
        return linkPath;
    }

    public void setLinkPath(String linkPath) {
        this.linkPath = linkPath;
    }

    public Object getUserObject() {
        return userObject;
    }

    public void setUserObject(Object userObject) {
        this.userObject = userObject;
    }
}
