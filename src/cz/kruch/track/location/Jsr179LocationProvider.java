// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import cz.kruch.track.configuration.Config;

import api.location.Location;
import api.location.LocationException;

public class Jsr179LocationProvider extends api.location.LocationProvider {
    private javax.microedition.location.LocationProvider impl;
    private LocationListenerAdapter adapter;

    public Jsr179LocationProvider() {
        super(Config.LOCATION_PROVIDER_JSR179);
    }

    public Object getImpl() {
        return impl;
    }

    public void start() throws api.location.LocationException {
        try {
            impl = javax.microedition.location.LocationProvider.getInstance(null);
            impl.setLocationListener(adapter, -1, -1, -1);
        } catch (javax.microedition.location.LocationException e) {
            throw new api.location.LocationException(e);
        }

        // notify listener on current status
        notifyListener(impl.getState());
    }

    public void stop() throws api.location.LocationException {
    }

    public void setLocationListener(api.location.LocationListener listener, int interval, int timeout, int maxAge) {
        if (listener == null) {
            impl.setLocationListener(null, interval, timeout, maxAge);
        } else {
            adapter = new LocationListenerAdapter(listener);
        }
    }

    public LocationException getException() {
        return null;
    }

    private class LocationListenerAdapter implements javax.microedition.location.LocationListener {

        public LocationListenerAdapter(api.location.LocationListener listener) {
            setListener(listener);
        }

        public void locationUpdated(javax.microedition.location.LocationProvider locationProvider, javax.microedition.location.Location xlocation) {
            javax.microedition.location.QualifiedCoordinates xc = xlocation.getQualifiedCoordinates();
            api.location.QualifiedCoordinates c = new api.location.QualifiedCoordinates(xc.getLatitude(),
                                                                                        xc.getLongitude(),
                                                                                        xc.getAltitude());
            long t = xlocation.getTimestamp();
            int f = xlocation.isValid() ? 1 : 0;

            notifyListener(new Location(c, t, f));
        }

        public void providerStateChanged(javax.microedition.location.LocationProvider locationProvider, int i) {
            notifyListener(i);
        }
    }
}
