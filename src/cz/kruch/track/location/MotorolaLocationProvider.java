// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.util.CharArrayTokenizer;

import javax.microedition.io.Connector;
import java.io.IOException;

public final class MotorolaLocationProvider extends api.location.LocationProvider {
    private com.motorola.location.PositionSource impl;
    private LocationListenerAdapter adapter;

    private int accuracy, age, timeout;

    public MotorolaLocationProvider() {
        super(Config.LOCATION_PROVIDER_MOTOROLA);

        // parse initialization params
        CharArrayTokenizer tokenizer = new CharArrayTokenizer();
        tokenizer.init(Config.getLocationTimings(), false);
        accuracy = tokenizer.nextInt();
        age = tokenizer.nextInt();
        timeout = tokenizer.nextInt();
        tokenizer.dispose();
    }

    public Object getImpl() {
        return impl;
    }

    public int start() throws api.location.LocationException {
        try {
            impl = (com.motorola.location.PositionSource) Connector.open("location://");
            impl.addPositionListener(adapter);
            impl.generatePosition(accuracy, age, timeout);
        } catch (IOException e) {
            throw new api.location.LocationException(e);
        }

        lastState = _STARTING;
        notifyListener(_STARTING); // trick to start GPX tracklog

        return lastState;
    }

    public void stop() throws api.location.LocationException {
        impl.close();
    }

    public void setLocationListener(api.location.LocationListener listener, int interval, int timeout, int maxAge) {
        if (listener == null) {
            impl.removePositionListener(adapter);
        } else {
            adapter = new LocationListenerAdapter(listener);
        }
    }

    private final class LocationListenerAdapter implements com.motorola.location.PositionListener {

        public LocationListenerAdapter(api.location.LocationListener listener) {
            setListener(listener);
        }

        public void newPosition(com.motorola.location.AggregatePosition aggregatePosition) {
            try {
                if (aggregatePosition == null || aggregatePosition.hasLatLon() == false) {
                    // signal state change
                    if (lastState != api.location.LocationProvider.TEMPORARILY_UNAVAILABLE) {
                        lastState = api.location.LocationProvider.TEMPORARILY_UNAVAILABLE;
                        notifyListener(lastState);
                    }
                } else {
                    // signal state change
                    if (lastState != api.location.LocationProvider.AVAILABLE) {
                        lastState = api.location.LocationProvider.AVAILABLE;
                        notifyListener(lastState);
                    }

                    double lat = aggregatePosition.getLatitude() ;
                    double lon = aggregatePosition.getLongitude();
                    float alt = aggregatePosition.hasAltitude() ? aggregatePosition.getAltitude() : -1F;

                    // create up-to-date location
                    api.location.QualifiedCoordinates qc = api.location.QualifiedCoordinates.newInstance(lat / 60 * 0.00001, lon / 60 * 0.00001, alt);

                    api.location.Location location = api.location.Location.newInstance(qc, aggregatePosition.getTimeStamp(), 1);
                    if (aggregatePosition.hasTravelDirection()) {
                        location.setCourse(aggregatePosition.getTravelDirection());
                    }
                    if (aggregatePosition.hasSpeed()) {
                        location.setSpeed((float) aggregatePosition.getSpeed() / 1000);
                    }
                    location.setAccuracy(aggregatePosition.getLatLonAccuracy());

                    // notify
                    notifyListener(location);
                }
            } catch (Throwable t) {

                // record problem
                setStatus(t.toString());

            } finally {

                // generate another fix
                impl.generatePosition(accuracy, age, timeout);

            }
        }
    }
}
