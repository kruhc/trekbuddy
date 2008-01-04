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

import api.location.LocationException;
import api.location.QualifiedCoordinates;
import api.location.Location;
import api.location.CartesianCoordinates;
import api.location.Datum;

import java.io.IOException;

import javax.microedition.io.Connector;

import cz.kruch.track.util.Mercator;
import cz.kruch.track.configuration.Config;

/**
 * O2 Germany provider implementation.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class O2GermanyLocationProvider
        extends api.location.LocationProvider
        implements javax.wireless.messaging.MessageListener, Runnable {

    private javax.wireless.messaging.MessageConnection impl;
    private int[] x, y, avg;
    private int offset;

    private volatile Object trigger;
    private volatile Thread thread;
    private volatile boolean go;

    private static final Mercator.ProjectionSetup[] zones = {
        new Mercator.ProjectionSetup("GK 2", null, 6D, 0D, 1D, 2500000, 0),
        new Mercator.ProjectionSetup("GK 3", null, 9D, 0D, 1D, 3500000, 0),
        new Mercator.ProjectionSetup("GK 4", null, 12D, 0D, 1D, 4500000, 0),
        new Mercator.ProjectionSetup("GK 5", null, 15D, 0D, 1D, 5500000, 0)
    };
    private static final Datum potsdam = Config.getDatum("Potsdam");

    public O2GermanyLocationProvider() {
        super("O2 Germany");
        this.x = new int[Config.o2Depth];
        this.y = new int[Config.o2Depth];
        this.avg = new int[2];
    }

    public Object getImpl() {
        return impl;
    }

    public int start() throws LocationException {
        try {
            this.impl = (javax.wireless.messaging.MessageConnection) Connector.open("cbs://:221", Connector.READ);
            this.impl.setMessageListener(this);
        } catch (Exception e) {
            throw new LocationException(e);
        }

        // start service thread
        (new Thread(this)).start();

        // notify
        notifyListener(lastState = _STARTING); // trick to start GPX tracklog

        return lastState;
    }

    public void stop() throws LocationException {
        // remove listener and gc-free native provider
        try {
            impl.setMessageListener(null);
            impl.close();
        } catch (IOException e) {
            // ignore
        }
        impl = null;

        // shutdown service thread
        synchronized (this) {
            go = false;
            notify();
        }

        // wait for finish
        if (thread != null) {
            try {
                thread.interrupt();
                thread.join();
            } catch (InterruptedException e) {
                // should never happen
            }
        }
    }

    public void notifyIncomingMessage(javax.wireless.messaging.MessageConnection messageConnection) {
        synchronized (this) {
            trigger = Boolean.TRUE;
            notify();
        }
    }

    public void run() {
        // let's roll
        go = true;
        thread = Thread.currentThread();

        // signal state change
        notifyListener(lastState = TEMPORARILY_UNAVAILABLE);

        try {

            // pop messages until end
            for (; go ;) {
                synchronized (this) {
                    while (go && trigger == null) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            // ignore
                        }
                    }
                    trigger = null;
                }

                if (!go) break;

                // result
                int state = TEMPORARILY_UNAVAILABLE;
                QualifiedCoordinates coords = null;

                // get and parse message
                javax.wireless.messaging.Message message = impl.receive();
                if (message instanceof javax.wireless.messaging.TextMessage) {
                    String text = ((javax.wireless.messaging.TextMessage) message).getPayloadText();
                    if (text != null && text.length() == 12) {
                        final int x = Integer.parseInt(text.substring(0, 6)) * 10;
                        final int y = Integer.parseInt(text.substring(6)) * 10;
                        final int[] avg = update(x, y);
                        final int idx = (avg[0] - 2500000) / 1000000;
                        if (idx >=0 && idx <= 3) {
                            CartesianCoordinates xy = CartesianCoordinates.newInstance(null, avg[0], avg[1]);
                            QualifiedCoordinates localQc = Mercator.MercatortoLL(xy, potsdam.ellipsoid, zones[idx]);
                            coords = potsdam.toWgs84(localQc);
                            QualifiedCoordinates.releaseInstance(localQc);
                            CartesianCoordinates.releaseInstance(xy);
                            state = AVAILABLE;
                        }
                    }
                }

                // signal state change
                if (lastState != state) {
                    notifyListener(lastState = state);
                }

                // create location
                Location location;
                if (coords != null) {
                    location = Location.newInstance(coords, System.currentTimeMillis(), 1);
                } else {
                    coords = QualifiedCoordinates.newInstance(Double.NaN, Double.NaN);
                    location = Location.newInstance(coords, System.currentTimeMillis(), 0);
                }

                // notify
                notifyListener(location);
            }

        } catch (Throwable t) {

            // record
            setThrowable(t);

        } finally {

            // be ready for restart
            go = false;

            // signal state change
            notifyListener(lastState = OUT_OF_SERVICE);
        }
    }

    public void setLocationListener(api.location.LocationListener listener, int interval, int timeout, int maxAge) {
        if (listener == null) {
            try {
                impl.setMessageListener(null);
            } catch (IOException e) {
                // ignore - this is bad, this method should throw LocationException
            }
        } else {
            setListener(listener);
        }
    }

    private int[] update(final int easting, final int northing) {
        final int[] x = this.x;
        final int[] y = this.y;
        final int l = x.length;
        int avgx = 0;
        int avgy = 0;
        int c = 0;

        x[offset] = easting;
        y[offset] = northing;
        offset++;
        if (offset == l) {
            offset = 0;
        }
        for (int i = l; --i >= 0; ) {
            if (x[i] == 0 || y[i] == 0)
                continue;
            avgx += x[i];
            avgy += y[i];
            c++;
        }
        avg[0] = avgx / c;
        avg[1] = avgy / c;

        return avg;
    }
}