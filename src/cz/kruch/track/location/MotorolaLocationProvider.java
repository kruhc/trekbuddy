/*
 * Copyright 2006-2007 Ales Pour <kruhc@seznam.cz>.
 * All Rights Reserved.
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

import cz.kruch.track.configuration.Config;
import cz.kruch.track.util.CharArrayTokenizer;

import javax.microedition.io.Connector;
import java.io.IOException;

import api.location.LocationException;
import api.location.LocationProvider;
import api.location.QualifiedCoordinates;
import api.location.Location;

/**
 * Motorola (Location API) provider implementation.
 *  
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class MotorolaLocationProvider
        extends api.location.LocationProvider
        implements com.motorola.location.PositionListener {

    private com.motorola.location.PositionSource impl;
/*
    private LocationListenerAdapter adapter;
*/

    private int accuracy, age, timeout;

    public MotorolaLocationProvider() {
        super("Motorola");
    }

    public Object getImpl() {
        return impl;
    }

    public int start() throws LocationException {
        try {
            // parse initialization params
            CharArrayTokenizer tokenizer = new CharArrayTokenizer();
            tokenizer.init(Config.getLocationTimings(), false);
            this.accuracy = tokenizer.nextInt();
            this.age = tokenizer.nextInt();
            this.timeout = tokenizer.nextInt();
            tokenizer.dispose();

            // init provider
            impl = (com.motorola.location.PositionSource) Connector.open("location://");
            impl.addPositionListener(this);
            impl.generatePosition(accuracy, age, timeout);

        } catch (IOException e) {
            throw new LocationException(e);
        }

        // notify
        notifyListener(lastState = _STARTING); // trick to start GPX tracklog

        return lastState;
    }

    public void stop() throws LocationException {
        impl.close();
    }

    public void setLocationListener(api.location.LocationListener listener, int interval, int timeout, int maxAge) {
        if (listener == null) {
            impl.removePositionListener(this);
        } else {
            setListener(listener);
        }
    }

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
                qc.setHorizontalAccuracy(aggregatePosition.getLatLonAccuracy());
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
}
