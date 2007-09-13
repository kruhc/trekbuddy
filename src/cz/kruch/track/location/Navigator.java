/*
 * Copyright 2006-2007 Ales Pour <kruhc@seznam.cz>. All Rights Reserved.
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

import api.location.Location;
import api.location.QualifiedCoordinates;
import cz.kruch.track.maps.Map;

import java.util.Vector;

public interface Navigator {
    public boolean isTracking();

    public Location getLocation();
    public QualifiedCoordinates getPointer();

    public Waypoint getNavigateTo();
    public void setNavigateTo(Vector wpts, int fromIndex, int toIndex);
    public int getWptAzimuth();
    public float getWptDistance();

    public void goTo(Waypoint wpt);

    public void saveLocation(Location l);
    public long getTracklogTime();
    public String getTracklogCreator();

    public Map getMap();
}
