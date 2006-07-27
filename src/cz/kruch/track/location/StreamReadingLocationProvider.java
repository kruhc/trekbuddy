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
import cz.kruch.track.ui.Desktop;
import cz.kruch.track.AssertionFailedException;

public abstract class StreamReadingLocationProvider extends LocationProvider {
    protected static final char[] HEADER_GGA = "$GPGGA".toCharArray();
    protected static final char[] HEADER_RMC = "$GPRMC".toCharArray();

    private OutputStream observer;

    protected StreamReadingLocationProvider(String name) {
        super(name);
    }

    protected void setObserver(OutputStream observer) {
        this.observer = observer;
    }

    protected final Location nextLocation(InputStream in) throws IOException, LocationException {
        // read GGA
        char[] ggaSentence = nextSentence(in, HEADER_GGA);
        if (ggaSentence == null) {
            return null;
        }

        // read RMC
        char[] rmcSentence = nextSentence(in, HEADER_RMC);
        if (rmcSentence == null) {
            return null;
        }

        Location location = null;

        // parse GGA and RMC
        NmeaParser.Record gga = NmeaParser.parseGGA(ggaSentence);
        NmeaParser.Record rmc = NmeaParser.parseRMC(rmcSentence);
        if (rmc.timestamp == gga.timestamp) {
            long datetime = rmc.date + rmc.timestamp;
            location = new Location(new QualifiedCoordinates(gga.lat, gga.lon, gga.altitude),
                                    datetime, gga.fix, gga.sat, gga.hdop);
            location.setCourse(rmc.angle);
            location.setSpeed(rmc.speed);
        } else {
            throw new AssertionFailedException("Invalid NMEA flow");
        }

        return location;
    }

    protected final char[] nextSentence(InputStream in, char[] header) throws IOException {
        char[] sb = new char[0x80];
        int pos = 0;

        boolean nl = false;
        boolean match = false;
        int hlen = header.length;

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

                // header check
                if (pos < hlen) {
                    if (ch != header[pos]) {
                        match = false;
                    }
                }

                if (match) { // only if header still matches
                    // add char to array
                    sb[pos++] = ch;

                    // weird content check
                    if (pos >= 0x80) {
                        throw new IOException("Hmm, is this really NMEA stream?");
                    }
                }
            }

            c = in.read();
        }

        if (nl) {
            char[] result = new char[pos];
            System.arraycopy(sb, 0, result, 0, pos);
            return result;
        }

        return null;
    }
}
