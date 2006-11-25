// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.io;

import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public final class LineReader extends InputStreamReader {
    private StringBuffer sb;

    public LineReader(InputStream in) {
        super(in);
        this.sb = new StringBuffer();
    }

    public String readLine(boolean ignoreLF) throws IOException {
        int count = 0;
        sb.setLength(0);
        for (;;) {
            int c = read();
            if (c == -1) {
                break;
            } else if ((c == '\n') || (c == '\r')) {
                if (count > 0) {
                    break;
                }
            } else {
                sb.append((char) c);
                count++;
            }
            if (sb.length() > 512) {
                throw new IllegalStateException("Line length > 512");
            }
        }

        if (sb.length() == 0) {
            return null;
        }

        return sb.toString();
    }
}
