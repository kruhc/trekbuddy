// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import api.location.LocationProvider;
import api.location.Location;
import api.location.QualifiedCoordinates;
import api.location.LocationException;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import cz.kruch.track.util.NmeaParser;
import cz.kruch.track.AssertionFailedException;

public abstract class StreamReadingLocationProvider extends LocationProvider {
    protected static final char[] HEADER_GGA = "$GPGGA".toCharArray();
    protected static final char[] HEADER_RMC = "$GPRMC".toCharArray();
    protected static final int BUFFER_SIZE = 512;

    public static int syncs;
    public static int mismatches;

    private OutputStream observer;
    private char[] buffer;
/*
    private int order = 0;
*/

    protected StreamReadingLocationProvider(String name) {
        super(name);
        this.buffer = new char[0x80];
        syncs = mismatches = 0;
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
            int l = nextSentence(in);
            if (l == -1) {
                return null;
            }
            if (isType(buffer, l, HEADER_GGA)) {
                gga = NmeaParser.parseGGA(buffer, l);
            } else if (isType(buffer, l, HEADER_RMC)) {
                rmc = NmeaParser.parseRMC(buffer, l);
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
        long datetime = rmc.date + rmc.timestamp;
        if (rmc.timestamp == gga.timestamp) {
            location = new Location(new QualifiedCoordinates(rmc.lat, rmc.lon, gga.altitude),
                                    datetime, gga.fix, gga.sat, gga.hdop);
        } else {
            location = new Location(new QualifiedCoordinates(rmc.lat, rmc.lon),
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

        OutputStream _observer = observer;
        char[] _buffer = buffer;

        int c = in.read();
        while (c > -1) {
            if (_observer != null) {
                _observer.write(c);
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
                _buffer[pos++] = ch;

                // weird content check
                if (pos >= 0x80) {
                    throw new IOException("Hmm, is this really NMEA stream?");
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

    private static boolean isType(char[] sentence, int length, char[] header) {
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
