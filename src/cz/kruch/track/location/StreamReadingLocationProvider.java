/*
 * Copyright 2006-2007 Ales Pour <kruhc@seznam.cz>. All Rights Reserved.
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

import api.location.LocationProvider;
import api.location.Location;
import api.location.QualifiedCoordinates;
import api.location.LocationException;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import cz.kruch.track.util.NmeaParser;

/**
 * Base class for Serial and Simulator location provider.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public abstract class StreamReadingLocationProvider extends LocationProvider {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("StreamReadingLocationProvider");
//#endif

    private static final char[] HEADER_GGA = { '$', 'G', 'P', 'G', 'G', 'A' };
    private static final char[] HEADER_RMC = { '$', 'G', 'P', 'R', 'M', 'C' };
    private static final char[] HEADER_SKIP = { '$', 'G', 'P', 'G', 'S' };

    private static final int LINE_SIZE = 128;

    protected static final int BUFFER_SIZE = 2048;

    public static int syncs, mismatches, checksums, restarts;

    private OutputStream observer;
    private char[] line;
/*
    private int order = 0;
*/

    protected StreamReadingLocationProvider(String name) {
        super(name);
        this.line = new char[LINE_SIZE];
        syncs = mismatches = checksums = restarts = 0;
    }

    protected void setObserver(OutputStream observer) {
        this.observer = observer;
    }

    protected final Location nextLocation(InputStream in) throws IOException, LocationException {
        // records
        NmeaParser.Record gga = null;
        NmeaParser.Record rmc = null;

        // get pair
        while (gga == null || rmc == null) {
            final int l = nextSentence(in);
            if (l == -1) {
                return null;
            }
            if (isType(line, l, HEADER_GGA)) {
                if (!validate(line, l)) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.warn("Invalid NMEA!");
//#endif
                    checksums++;
                    continue;
                }
                gga = NmeaParser.parseGGA(line, l);
            } else if (isType(line, l, HEADER_RMC)) {
                if (!validate(line, l)) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.warn("Invalid NMEA!");
//#endif
                    checksums++;
                    continue;
                }
                rmc = NmeaParser.parseRMC(line, l);
            } else {
                continue;
            }
            // sync
            if (rmc != null && gga != null) {
                int i = rmc.timestamp - gga.timestamp;
                if (i > 0) {
                    gga = null;
                    syncs++;
                } else if (i < 0) {
                    rmc = null;
                    syncs++;
                }
            }
        }

        // new location
        Location location;

        // combine
        final long datetime = rmc.date + rmc.timestamp;
        if (rmc.timestamp == gga.timestamp) {
            QualifiedCoordinates qc = QualifiedCoordinates.newInstance(rmc.lat, rmc.lon, gga.altitude);
            qc.setAccuracy(gga.hdop * 5);
            location = Location.newInstance(qc, datetime, gga.fix, gga.sat);
        } else {
            location = Location.newInstance(QualifiedCoordinates.newInstance(rmc.lat, rmc.lon),
                                            datetime, rmc.status == 'A' ? 1 : 0);
            mismatches++;
        }
        location.setCourse(rmc.angle);
        location.setSpeed(rmc.speed);

        // gc hint
        rmc = null;
        gga = null;

        return location;
    }

    private int nextSentence(InputStream in) throws IOException {
        int pos = 0;

        boolean nl = false;
        boolean match = false;

        OutputStream observer = this.observer;
        char[] line = this.line;

        int c = in.read();
        while (c > -1) {
            if (observer != null) {
                try {
                    observer.write(c);
                } catch (Exception e) {
                    // ignore
                }
            }
            if (c == '$') { // beginning of NMEA sentence
                pos = 0;
                match = true; // lie, for now :-)
            }
            if (match) { // header already matched or not yet enough data for header check
                char ch = (char) c;
                nl = (ch == '\n' || ch == '\r');

                if (nl) break;

                // add char to array
                line[pos++] = ch;

                // weird content check
                if (pos >= LINE_SIZE) {

                    // record state
                    setThrowable(new LocationException("NMEA sentence too long"));

                    // reset
                    pos = 0;
                    match = false;
                }

                // skip sentence we do not want - GSA/GSV are spam
                if (pos == 5) { // '$GPGS...'
                    int i = 5;
                    for ( ; --i >= 0; ) {
                        if (line[i] != HEADER_SKIP[i]) {
                            break;
                        }
                    }
                    if (i < 0) {
                        // reset
                        pos = 0;
                        match = false;
                    }
                }
            }

            c = in.read();
        }

        if (nl) {
            return pos;
        }

        if (c == -1) {
            setStatus("End of stream");
        }

        return -1;
    }

    private static boolean validate(char[] raw, final int length) {
        int result = 0;
        for (int i = 1; i < length; i++) {
            final byte b = (byte) (raw[i] & 0x00ff);
            if (b == '*') {
                if (length - i >= 2) {
                    byte hi = (byte) (raw[i + 1] & 0x00ff);
                    byte lo = (byte) (raw[i + 2] & 0x00ff);
                    if (hi >= '0' && hi <= '9') {
                        hi -= '0';
                    } else if (hi >= 'A' && hi <= 'F') {
                        hi -= 'A' - 10;
                    }
                    if (lo >= '0' && lo <= '9') {
                        lo -= '0';
                    } else if (lo >= 'A' && lo <= 'F') {
                        lo -= 'A' - 10;
                    }
                    return result == (hi << 4) + lo;
                }
                break;
            } else {
                result ^= b;
            }
        }

        return false;
    }

    private static boolean isType(char[] sentence, final int length, char[] header) {
        if (length < header.length) {
            return false;
        }

        for (int i = header.length; --i >= 0; ) {
            if (sentence[i] != header[i]) {
                return false;
            }
        }

        return true;
    }
}
