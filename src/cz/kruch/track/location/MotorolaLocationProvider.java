// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.util.CharArrayTokenizer;

import javax.microedition.io.Connector;
import java.io.IOException;

import api.location.LocationException;
import api.location.LocationProvider;
import api.location.QualifiedCoordinates;
import api.location.Location;

public final class MotorolaLocationProvider
        extends api.location.LocationProvider
        implements com.motorola.location.PositionListener {

    private com.motorola.location.PositionSource impl;
/*
    private LocationListenerAdapter adapter;
*/

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

    public int start() throws LocationException {
        try {
            impl = (com.motorola.location.PositionSource) Connector.open("location://");
            impl.addPositionListener(/*adapter*/this);
            impl.generatePosition(accuracy, age, timeout);
        } catch (IOException e) {
            throw new LocationException(e);
        }

        lastState = _STARTING;
        notifyListener(_STARTING); // trick to start GPX tracklog

        return lastState;
    }

    public void stop() throws LocationException {
        impl.close();
    }

    public void setLocationListener(api.location.LocationListener listener, int interval, int timeout, int maxAge) {
        if (listener == null) {
            impl.removePositionListener(/*adapter*/this);
        } else {
/*
            adapter = new LocationListenerAdapter(listener);
*/
            setListener(listener);
        }
    }

/*
    private final class LocationListenerAdapter implements com.motorola.location.PositionListener {

        public LocationListenerAdapter(api.location.LocationListener listener) {
            setListener(listener);
        }
*/

        public void newPosition(com.motorola.location.AggregatePosition aggregatePosition) {
            try {
                if (aggregatePosition == null || aggregatePosition.hasLatLon() == false) {
                    // signal state change
                    if (lastState != LocationProvider.TEMPORARILY_UNAVAILABLE) {
                        lastState = LocationProvider.TEMPORARILY_UNAVAILABLE;
                        notifyListener(lastState);
                    }
                } else {
                    // signal state change
                    if (lastState != LocationProvider.AVAILABLE) {
                        lastState = LocationProvider.AVAILABLE;
                        notifyListener(lastState);
                    }

                    double lat = aggregatePosition.getLatitude() ;
                    double lon = aggregatePosition.getLongitude();
                    float alt = aggregatePosition.hasAltitude() ? aggregatePosition.getAltitude() : -1F;

                    // create up-to-date location
                    QualifiedCoordinates qc = QualifiedCoordinates.newInstance(lat / 60 * 0.00001, lon / 60 * 0.00001, alt);
                    qc.setAccuracy(aggregatePosition.getLatLonAccuracy());
                    Location location = Location.newInstance(qc, aggregatePosition.getTimeStamp(), 1);
                    if (aggregatePosition.hasTravelDirection()) {
                        location.setCourse(aggregatePosition.getTravelDirection());
                    }
                    if (aggregatePosition.hasSpeed()) {
                        location.setSpeed((float) aggregatePosition.getSpeed() / 1000);
                    }

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
/*
    }
*/
}
