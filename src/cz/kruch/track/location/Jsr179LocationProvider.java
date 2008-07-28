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
import cz.kruch.track.util.NmeaParser;
import cz.kruch.track.Resources;
import api.location.LocationException;
import api.location.QualifiedCoordinates;
import api.location.Location;
import api.location.LocationProvider;

import java.io.OutputStream;
import java.io.IOException;

/**
 * Internal (JSR-179) provider implementation.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class Jsr179LocationProvider
        extends api.location.LocationProvider
        implements javax.microedition.location.LocationListener, Runnable {

    private static final byte[] CRLF = { '\r', '\n' };

    private javax.microedition.location.LocationProvider impl;
    private int interval, timeout, maxage;

    private final char[] line;

    public Jsr179LocationProvider() {
        super("Internal");
        this.line = new char[NmeaParser.MAX_SENTENCE_LENGTH];
    }

    public int start() throws LocationException {
        try {
            // prepare criteria
            CharArrayTokenizer tokenizer = new CharArrayTokenizer();
            tokenizer.init(Config.getLocationTimings(Config.LOCATION_PROVIDER_JSR179), false);
            interval = tokenizer.nextInt();
            timeout = tokenizer.nextInt();
            maxage = tokenizer.nextInt();
            tokenizer = null; // gc hint
            final javax.microedition.location.Criteria criteria = new javax.microedition.location.Criteria();

            // common criteria
            criteria.setAltitudeRequired(true);
            criteria.setSpeedAndCourseRequired(true);

            // adjust criteria for current device
//#ifdef __ALL__
            if (cz.kruch.track.TrackingMIDlet.a780) {
                /* from bikeator */
                criteria.setPreferredPowerConsumption(javax.microedition.location.Criteria.POWER_USAGE_HIGH);
            }
//#endif

            // init provider
            impl = javax.microedition.location.LocationProvider.getInstance(criteria);
            if (impl == null) {
                impl = javax.microedition.location.LocationProvider.getInstance(null);
                setThrowable(new LocationException("Default criteria used"));
            }
            if (impl == null) {
                throw new LocationException(Resources.getString(Resources.DESKTOP_MSG_NO_PROVIDER_INSTANCE));
            }

        } catch (LocationException e) {
            throw e;
        } catch (Exception e) {
            throw new LocationException(e);
        }

        // start service thread
        (new Thread(this)).start();

        return LocationProvider._STARTING;
    }

    public void stop() throws LocationException {
        // wait for thread to die
        die();
    }

    public void run() {
        // statistics
        restarts++;

        // let's roll
        baby();

        try {
            // set listener
            impl.setLocationListener(this, interval, timeout, maxage);

            // wait for end (kinda stupid variant of gps() from Serial provider ;-) )
            synchronized (this) {
                while (go) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
            }

        } catch (Throwable t) {

            // record
            setThrowable(t);

        } finally {

            // remove listener and gc-free native provider
            impl.setLocationListener(null, -1, -1, -1);
            impl = null;

            // almost dead
            zombie();
        }
    }

    private static final String APPLICATION_X_JSR179_LOCATION_NMEA = "application/X-jsr179-location-nmea";

    public void locationUpdated(javax.microedition.location.LocationProvider p,
                                javax.microedition.location.Location l) {

        // get extra info
        final String extra = l.getExtraInfo(APPLICATION_X_JSR179_LOCATION_NMEA);

        // valid location?
        if (l.isValid()) {

            // enhance with raw NMEA
            int sat = -1;
            if (extra != null) {
                try {
                    final NmeaParser.Record gga = parseNmea(line, extra);
                    if (gga != null) {
                        sat = gga.sat;
                    } else {
                        sat = NmeaParser.sata;
                    }
                } catch (Exception e) {
                    setThrowable(e);
                }
            }

            // vars
            javax.microedition.location.QualifiedCoordinates xc = l.getQualifiedCoordinates();
            float spd = l.getSpeed();
            float alt = xc.getAltitude();
            float course = l.getCourse();
            float accuracy = xc.getHorizontalAccuracy();

            if (Float.isNaN(alt)) {
                alt = Float.NaN;
            } else if (cz.kruch.track.TrackingMIDlet.sxg75) {
                alt -= 540;
            }

            // create up-to-date location
            QualifiedCoordinates qc = QualifiedCoordinates.newInstance(xc.getLatitude(),
                                                                       xc.getLongitude(),
                                                                       alt);
            qc.setHorizontalAccuracy(accuracy);
            Location location = Location.newInstance(qc, l.getTimestamp(), 1, sat);
            location.setCourse(course);
            location.setSpeed(spd);

            // signal state change
            if (lastState != AVAILABLE) {
                notifyListener(lastState = AVAILABLE);
            }

            // notify
            notifyListener(location);

        } else {

            // signal state change
            if (lastState != TEMPORARILY_UNAVAILABLE) {
                notifyListener(lastState = TEMPORARILY_UNAVAILABLE);
            }
        }

        // NMEA logging
        if (extra != null && observer != null) {
            try {
                logNmea(observer, extra);
            } catch (Exception e) {
                setThrowable(e);
            }
        }
    }

    public void providerStateChanged(javax.microedition.location.LocationProvider locationProvider, int i) {
        if (isGo()) {
            notifyListener(i);
        }
    }

    private static NmeaParser.Record parseNmea(final char[] line,
                                               final String extra) throws Exception {
        final int length = extra.length();
        int start = 0;
        int idx = extra.indexOf("$GP");
        while (idx > -1) {
            if (idx != 0) {
                if (idx - start < NmeaParser.MAX_SENTENCE_LENGTH) {
                    extra.getChars(start, idx, line, 0);
                    final NmeaParser.Record rec = NmeaParser.parse(line, idx - start);
                    if (rec.type == NmeaParser.HEADER_GGA) {
                        return rec;
                    }
                }
            }
            start = idx;
            idx = extra.indexOf("$GP", start + 3);
        }
        if (start < length) { // always true
            if (length - start < NmeaParser.MAX_SENTENCE_LENGTH) {
                extra.getChars(start, length, line, 0);
                final NmeaParser.Record rec = NmeaParser.parse(line, length - start);
                if (rec.type == NmeaParser.HEADER_GGA) {
                    return rec;
                }
            }
        }

        return null;
    }

    private static void logNmea(final OutputStream out,
                                final String extra) throws IOException {
        final byte[] bytes = extra.getBytes();
        final int N = bytes.length;
        int offset = 0;
        boolean crlf = false;
        for (int i = 0; i < N; i++) {
            final byte b = bytes[i];
            switch (b) {
                case '$': {
                    if (i != 0) {
                        out.write(bytes, offset, i - offset);
                        offset = i;
                        if (!crlf) {
                            out.write(CRLF);
                        } else {
                            crlf = false;
                        }
                    }
                } break;
                case '\r':
                case '\n': {
                    crlf = true;
                } break;
            }
        }
        if (offset < bytes.length) { // always true
            out.write(bytes, offset, bytes.length - offset);
            if (!crlf)  {
                out.write(CRLF);
            }
        }
    }
}
