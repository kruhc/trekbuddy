// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import api.location.Location;

public final class Waypoint extends Location {
    private String name;
    private String comment;

    public Waypoint(Location location, String name, String comment) {
        super(location.getQualifiedCoordinates(), location.getTimestamp(),
              location.getFix(), location.getSat(), location.getHdop());
        this.name = name;
        this.comment = comment;
    }

    public String getName() {
        return name;
    }

    public String getComment() {
        return comment;
    }
}
