// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import api.location.LocationProvider;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

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

    /** @deprecated change return value */
    protected String nextSentence(InputStream in, char[] header) throws IOException {
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

                sb[pos++] = ch;

                if (pos >= 0x80) {
                    throw new IOException("Hmm, is this really NMEA stream?");
                }

                if (pos == hlen) { // check header
                    for (int i = 0; i < hlen; i++) {
                        if (sb[i] != header[i])  {
                            match = false;
                            break;
                        }
                    }
                }
            }

            c = in.read();
        }

        if (nl) {
            return new String(sb, 0, pos);
        }

        return null;
    }
}
