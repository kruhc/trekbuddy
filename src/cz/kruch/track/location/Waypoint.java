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

package cz.kruch.track.location;

import api.location.QualifiedCoordinates;

/**
 * Represents a set of basic.
 * TODO This is practically <b>Landmark</b> from JSR-179
 * and so it should be moved to {@link api.location} package.  
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class Waypoint {
    private QualifiedCoordinates coordinates;
    private String name;
    private String comment;
    private String sym;
    private long timestamp;

    private Object userObject;
    private String linkPath;

    public Waypoint(QualifiedCoordinates qc, String name, String comment, String sym) {
        this.coordinates = qc;
        this.name = name;
        this.comment = comment;
        this.sym = sym;
        this.timestamp = -1;
    }

    public Waypoint(QualifiedCoordinates qc, String name, String comment, long timestamp) {
        this.coordinates = qc;
        this.name = name;
        this.comment = comment;
        this.timestamp = timestamp;
    }

    public QualifiedCoordinates getQualifiedCoordinates() {
        return coordinates;
    }

    public void setQualifiedCoordinates(QualifiedCoordinates coordinates) {
        this.coordinates = coordinates;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getSym() {
        return sym;
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

    public String toString() {
        if (userObject == null || !cz.kruch.track.configuration.Config.preferGsName)
            return name;
        return userObject.toString();
    }
}
