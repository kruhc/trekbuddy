// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.io;

import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public final class LineReader extends InputStreamReader {
    private char[] buffer;
    private int count, offset;

    public LineReader(InputStream in) {
        super(in);
        this.buffer = new char[512];
        this.count = this.offset = 0;
    }

    public String readLine(boolean ignoreLF) throws IOException {
        if (count < 0) {
            return null;
        }

        char[] _buffer = buffer;
        int _count = count;

        int chars = 0, start = -1;
        StringBuffer sb = null;

        for (;;) {
            int c;
            if (offset == _count) {
                if (start > -1) {
                    sb = new StringBuffer(chars).append(buffer, start, chars);
                    start = chars = 0;
                }
                _count = count = read(_buffer, 0, _buffer.length);
                if (_count == -1) {
                    c = -1;
                } else {
                    offset = 0;
                    c = _buffer[offset++];
                }
            } else {
                c = _buffer[offset++];
            }
            if (c == -1) {
                break;
            }
            if ((c == '\n') || (c == '\r')) {
                if (chars > 0 || sb != null) {
                    break;
                }
            } else {
                if (start < 0) {
                    start = offset - 1;
                }
                chars++;
            }
            if (chars > 512) {
                throw new IllegalStateException("Line length > 512");
            }
        }

        if (chars == 0 && sb == null) {
            return null;
        }

        if (sb == null) {
            return new String(buffer, start, chars);
        }

        return sb.append(buffer, start, chars).toString();
    }

    public void dispose() {
        buffer = null;
    }

    public void close() throws IOException {
        super.close();
        this.dispose();
    }
}
