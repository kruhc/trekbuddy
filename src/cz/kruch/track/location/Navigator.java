// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import api.location.Location;
import api.location.QualifiedCoordinates;

public interface Navigator {
    public boolean isTracking();
    public Location getLocation();
    public QualifiedCoordinates getPointer();
    public Waypoint[] getPath();
    public void setPath(Waypoint[] path);
    public void setNavigateTo(int pathIdx);
    public int getNavigateTo();
    public void addWaypoint(Waypoint wpt);
    public void recordWaypoint(Waypoint wpt);
}
