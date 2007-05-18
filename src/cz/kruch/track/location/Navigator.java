// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import api.location.Location;
import api.location.QualifiedCoordinates;
import cz.kruch.track.maps.Map;

public interface Navigator {
    public boolean isTracking();

    public Location getLocation();
    public QualifiedCoordinates getPointer();

    public Waypoint getNavigateTo();
    public void setNavigateTo(Waypoint wpt);
    public int getWptAzimuth();
    public float getWptDistance();

    public void saveLocation(Location l);
    public long getTracklogTime();
    public String getTracklogCreator();

    public Map getMap();
}
