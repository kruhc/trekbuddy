// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import cz.kruch.track.configuration.Config;

public class Jsr179LocationProvider extends api.location.LocationProvider {
    private javax.microedition.location.LocationProvider impl;
    private LocationListenerAdapter adapter;
    private int lastState;

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

        lastState = _STARTING;
        notifyListener(_STARTING); // trick to start GPX tracklog

        return lastState;
    }

    public void stop() throws api.location.LocationException {
        impl = null;
    }

    public void setLocationListener(api.location.LocationListener listener, int interval, int timeout, int maxAge) {
        if (listener == null) {
            impl.setLocationListener(null, interval, timeout, maxAge);
        } else {
            adapter = new LocationListenerAdapter(listener);
        }
    }

    private final class LocationListenerAdapter implements javax.microedition.location.LocationListener {
        private static final String APPLICATION_X_JSR179_LOCATION_NMEA = "application/X-jsr179-location-nmea";

        public LocationListenerAdapter(api.location.LocationListener listener) {
            setListener(listener);
        }

        public void locationUpdated(javax.microedition.location.LocationProvider p,
                                    javax.microedition.location.Location l) {
            // valid location?
            if (l.isValid()) {

                // signal state change
                if (lastState != api.location.LocationProvider.AVAILABLE) {
                    lastState = api.location.LocationProvider.AVAILABLE;
                    notifyListener(lastState);
                }

                // vars
                javax.microedition.location.QualifiedCoordinates xc = l.getQualifiedCoordinates();
                float spd = l.getSpeed();
                float alt = xc.getAltitude();
                float course = l.getCourse();
                if (cz.kruch.track.TrackingMIDlet.isSxg75()) {
                    if (Float.isNaN(spd)) {
                        spd = -1F;
                    } else {
                        spd *= 2;
                    }
                    if (Float.isNaN(alt)) {
                        alt = -1F;
                    } else {
                        alt -= 540;
                    }
                    if (Float.isNaN(course)) {
                        course = -1F;
                    }
                }

                // create up-to-date location
                api.location.QualifiedCoordinates qc = new api.location.QualifiedCoordinates(xc.getLatitude(),
                                                                                             xc.getLongitude(),
                                                                                             alt);
                api.location.Location location = new api.location.Location(qc, l.getTimestamp(), 1);
                location.setCourse(course);
                location.setSpeed(spd);
                location.setHdop(1F);
                // DEBUG
                setStatus(new Float(xc.getHorizontalAccuracy()));

                // notify
                notifyListener(location);

            } else {

                // signal state change
                if (lastState != api.location.LocationProvider.TEMPORARILY_UNAVAILABLE) {
                    lastState = api.location.LocationProvider.TEMPORARILY_UNAVAILABLE;
                    notifyListener(lastState);
                }
            }

            // set raw NMEA to status
            setStatus(l.getExtraInfo(APPLICATION_X_JSR179_LOCATION_NMEA));
        }

        public void providerStateChanged(javax.microedition.location.LocationProvider locationProvider, int i) {
            notifyListener(i);
        }
    }
}
