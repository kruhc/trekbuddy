// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import cz.kruch.track.configuration.Config;

public class Jsr179LocationProvider extends api.location.LocationProvider implements Runnable {
    private javax.microedition.location.LocationProvider impl;

    public Jsr179LocationProvider() {
        super(Config.LOCATION_PROVIDER_JSR179);
    }

    public Object getImpl() {
        return impl;
    }

    public void start() throws api.location.LocationException {
        try {
            impl = javax.microedition.location.LocationProvider.getInstance(null);
        } catch (javax.microedition.location.LocationException e) {
            throw new api.location.LocationException(e);
        }
    }

    public void stop() throws api.location.LocationException {
    }

    public void setLocationListener(api.location.LocationListener listener, int interval, int timeout, int maxAge) {
        impl.setLocationListener(new LocationListenerAdapter(listener),
                                 interval, timeout, maxAge);
        if (listener != null) {
            listener.providerStateChanged(Jsr179LocationProvider.this, impl.getState());
        }
    }

    public void run() {
    }

    private class LocationListenerAdapter implements javax.microedition.location.LocationListener {
        private api.location.LocationListener listener;

        public LocationListenerAdapter(api.location.LocationListener listener) {
            this.listener = listener;
        }

        public void locationUpdated(javax.microedition.location.LocationProvider locationProvider, javax.microedition.location.Location xlocation) {
            if (listener != null) {
                javax.microedition.location.QualifiedCoordinates xc = xlocation.getQualifiedCoordinates();
                api.location.QualifiedCoordinates c = new api.location.QualifiedCoordinates(xc.getLatitude(),
                                                                                            xc.getLongitude(),
                                                                                            xc.getAltitude());
                long t = xlocation.getTimestamp();
                SimplifiedLocation l = new SimplifiedLocation(c, t);
                listener.locationUpdated(Jsr179LocationProvider.this, l);
            }
        }

        public void providerStateChanged(javax.microedition.location.LocationProvider locationProvider, int i) {
            if (listener != null) {
                listener.providerStateChanged(Jsr179LocationProvider.this, i);
            }
        }
    }
}
