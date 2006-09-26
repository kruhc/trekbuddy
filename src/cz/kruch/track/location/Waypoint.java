// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import api.location.Location;
import api.location.QualifiedCoordinates;

public final class Waypoint extends Location {
    private String name;
    private String comment;
    private Object userObject;
    private String linkPath = null;

    public Waypoint(Location location, String name, String comment) {
        super(location);
        this.name = name;
        this.comment = comment;
    }

    public Waypoint(QualifiedCoordinates qc, String name, String comment) {
        this(qc, name, comment, -1);
    }

    public Waypoint(QualifiedCoordinates qc, String name, String comment, long time) {
        super(qc, time, -1);
        this.name = name;
        this.comment = comment;
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
