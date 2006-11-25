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

    private OutputStream observer;
    private char[] buffer;
/*
    private int order = 0;
*/

    protected StreamReadingLocationProvider(String name) {
        super(name);
        this.buffer = new char[128];
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
            char[] s = nextSentence(in);
            if (s == null) {
                return null;
            }
            if (isType(s, HEADER_GGA)) {
                gga = NmeaParser.parseGGA(s);
            } else if (isType(s, HEADER_RMC)) {
                rmc = NmeaParser.parseRMC(s);
            } else {
                continue;
            }
            // sync
            if (/*order == 0 && */rmc != null && gga != null) {
                int i = rmc.timestamp - gga.timestamp;
/*
                switch (i) {
                    case 0:
                        order = 1;
                        break;
                    case 1000:
                        gga = null;
                        break;
                    case -1000:
                        rmc = null;
                        break;
                }
*/
                if (i > 0) {
                    gga = null;
                } else if (i < 0) {
                    rmc = null;
                }
            }
        }

        // new location
        Location location = null;

        // combine
/*
        if (rmc.timestamp == gga.timestamp) {
*/
            long datetime = rmc.date + rmc.timestamp;
            location = new Location(new QualifiedCoordinates(rmc.lat, rmc.lon, gga.altitude),
                                    datetime, gga.fix, gga.sat, gga.hdop);
            location.setCourse(rmc.angle);
            location.setSpeed(rmc.speed);
/*
        } else {
            long datetime = rmc.date + rmc.timestamp;
            location = new Location(new QualifiedCoordinates(rmc.lat, rmc.lon),
                                    datetime, rmc.status == 'A' ? 1 : 0);
            location.setCourse(rmc.angle);
            location.setSpeed(rmc.speed);
        }
*/
        rmc = gga = null;

        return location;
    }

    protected final char[] nextSentence(InputStream in) throws IOException {
        int pos = 0;

        boolean nl = false;
        boolean match = false;

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
                buffer[pos++] = ch;

                // weird content check
                if (pos >= 0x80) {
                    throw new IOException("Hmm, is this really NMEA stream?");
                }
            }

            c = in.read();
        }

        if (nl) {
            char[] result = new char[pos];
            System.arraycopy(buffer, 0, result, 0, pos);
            
            return result;
        }

        if (c == -1) {
            setStatus("End of stream");
        }

        return null;
    }

    private boolean isType(char[] sentence, char[] header) {
        if (sentence.length < header.length) {
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
