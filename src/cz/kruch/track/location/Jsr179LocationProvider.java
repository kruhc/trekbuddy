// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import cz.kruch.track.configuration.Config;

public class Jsr179LocationProvider extends api.location.LocationProvider {
    private javax.microedition.location.LocationProvider impl;
    private LocationListenerAdapter adapter;

    public Jsr179LocationProvider() {
        super(Config.LOCATION_PROVIDER_JSR179);
    }

    public Object getImpl() {
        return impl;
    }

    public int start() throws api.location.LocationException {
        try {
            javax.microedition.location.Criteria criteria = new javax.microedition.location.Criteria();
            criteria.setAltitudeRequired(true);
            criteria.setSpeedAndCourseRequired(true);
            impl = javax.microedition.location.LocationProvider.getInstance(criteria);
            if (impl == null) {
                impl = javax.microedition.location.LocationProvider.getInstance(null);
                setException(new api.location.LocationException("Default criteria used"));
            }
            impl.setLocationListener(adapter, Config.getSafeInstance().getLocationInterval(),
                                     -1, -1);
        } catch (Exception e) {
            throw new api.location.LocationException(e);
        }

        notifyListener(impl.getState());

        return impl.getState();
    }

    public void stop() throws api.location.LocationException {
        // anything to do?
    }

    public void setLocationListener(api.location.LocationListener listener, int interval, int timeout, int maxAge) {
        if (listener == null) {
            impl.setLocationListener(null, interval, timeout, maxAge);
        } else {
            adapter = new LocationListenerAdapter(listener);
        }
    }

    private final class LocationListenerAdapter implements javax.microedition.location.LocationListener {

        public LocationListenerAdapter(api.location.LocationListener listener) {
            setListener(listener);
        }

        public void locationUpdated(javax.microedition.location.LocationProvider locationProvider, javax.microedition.location.Location xlocation) {
            if (xlocation.isValid()) {
                javax.microedition.location.QualifiedCoordinates xc = xlocation.getQualifiedCoordinates();
                api.location.QualifiedCoordinates c = new api.location.QualifiedCoordinates(xc.getLatitude(),
                                                                                            xc.getLongitude(),
                                                                                            xc.getAltitude());

                api.location.Location location = new api.location.Location(c, xlocation.getTimestamp(), 1);
                location.setCourse(xlocation.getCourse());
                location.setSpeed(xlocation.getSpeed());

                notifyListener(location);

            } else {
                notifyListener(api.location.LocationProvider.TEMPORARILY_UNAVAILABLE);
            }
        }

        public void providerStateChanged(javax.microedition.location.LocationProvider locationProvider, int i) {
            notifyListener(i);
        }
    }
}
