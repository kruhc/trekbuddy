// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import api.location.LocationProvider;

import java.io.InputStream;
import java.io.IOException;

public abstract class StreamReadingLocationProvider extends LocationProvider {
    protected StreamReadingLocationProvider(String name) {
        super(name);
    }

    protected String nextGGA(InputStream in) throws IOException {
        String line = readLine(in);
        while (line != null) {
            if (line.startsWith("$GPGGA")) {
                break;
            }
            line = readLine(in);
        }

        return line;
    }

    private String readLine(InputStream in) throws IOException {
        StringBuffer sb = new StringBuffer();
        boolean nl = false;
        int c = in.read();
        while (c > -1) {
            char ch = (char) c;
            sb.append(ch);
            nl = ch == '\n';
            if (nl) break;
            c = in.read();
        }

        if (nl) {
            return sb.toString();
        }

        return null;
    }
}
