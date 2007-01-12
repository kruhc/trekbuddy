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
            // prepare criteria
            int timeout = -1;
            int interval = Config.getSafeInstance().getLocationInterval();
            javax.microedition.location.Criteria criteria = new javax.microedition.location.Criteria();

            // adjust criteria for current device
//#ifdef __A780__
            if (cz.kruch.track.TrackingMIDlet.isA780()) {
                /* from http://www.kiu.weite-welt.com/de.schoar.blog/?p=186 */
                interval = timeout = 2;
                /* from bikeator */
                criteria.setHorizontalAccuracy(javax.microedition.location.Criteria.NO_REQUIREMENT);
                criteria.setPreferredPowerConsumption(javax.microedition.location.Criteria.POWER_USAGE_HIGH);
            }
            else
//#endif            
            {
                criteria.setAltitudeRequired(true);
                criteria.setSpeedAndCourseRequired(true);
            }

            // init provider
            impl = javax.microedition.location.LocationProvider.getInstance(criteria);
            if (impl == null) {
                impl = javax.microedition.location.LocationProvider.getInstance(null);
                setException(new api.location.LocationException("Default criteria used"));
            }
            impl.setLocationListener(adapter, interval, timeout, -1);

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

                if (Float.isNaN(spd)) {
                    spd = -1F;
                } else if (cz.kruch.track.TrackingMIDlet.isSxg75()) {
                    spd *= 2;
                }
//#ifdef __A780__
                  else if (cz.kruch.track.TrackingMIDlet.isA780()) {
                    spd *= 2;
                }
//#endif
                if (Float.isNaN(alt)) {
                    alt = -1F;
                } else if (cz.kruch.track.TrackingMIDlet.isSxg75()) {
                    alt -= 540;
                }
                if (Float.isNaN(course)) {
                    course = -1F;
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
