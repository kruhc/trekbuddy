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

public abstract class StreamReadingLocationProvider extends LocationProvider {
    protected static final char[] HEADER_GGA = "$GPGGA".toCharArray();
    protected static final char[] HEADER_RMC = "$GPRMC".toCharArray();

    protected static final int BUFFER_SIZE = 512;

    private static final int LINE_SIZE = 128;

    public static int syncs;
    public static int mismatches;

    private OutputStream observer;
    private char[] line;
/*
    private int order = 0;
*/

    protected StreamReadingLocationProvider(String name) {
        super(name);
        this.line = new char[LINE_SIZE];
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
            final int l = nextSentence(in);
            if (l == -1) {
                return null;
            }
            if (isType(line, l, HEADER_GGA)) {
                gga = NmeaParser.parseGGA(line, l);
            } else if (isType(line, l, HEADER_RMC)) {
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
            location = Location.newInstance(QualifiedCoordinates.newInstance(rmc.lat, rmc.lon, gga.altitude),
                                            datetime, gga.fix, gga.sat, gga.hdop * 5);
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
                observer.write(c);
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
