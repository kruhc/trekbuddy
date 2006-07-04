// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import api.location.LocationProvider;
import api.location.LocationException;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

public abstract class StreamReadingLocationProvider extends LocationProvider {
    protected static final String HEADER_GGA = "$GPGGA";
    protected static final String HEADER_RMC = "$GPRMC";

    private OutputStreamWriter observer;
    private LocationException exception;

    protected StreamReadingLocationProvider(String name) {
        super(name);
    }

    public synchronized LocationException getException() {
        return exception;
    }

    protected synchronized void setException(LocationException exception) {
        this.exception = exception;
    }

    protected synchronized void setObserver(OutputStreamWriter observer) {
        this.observer = observer;
    }

    protected String nextSentence(InputStream in, String header) throws IOException {
        String line = readLine(in);
        while (line != null) {
            if (header == null) {
                break;
            }
            if (line.startsWith(header)) {
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
            if (sb.length() > 512) throw new IOException("Hmm, is this really NMEA tracklog?");

            char ch = (char) c;
            sb.append(ch);

            nl = (ch == '\n' || ch == '\r');
            if (nl) break;

            c = in.read();
        }

        if (nl) {
            String line = sb.toString();

            // update observer
            if (observer != null) {
                try {
                    observer.write(line);
                    observer.flush();
                } catch (IOException e) {
                    // ignore observer problems
                }
            }

            return line;
        }

        return null;
    }
}
