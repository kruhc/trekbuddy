// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.io;

import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.AssertionFailedException;

import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public final class LineReader extends InputStreamReader {
    public static final String EMPTY_LINE = "";

    private static final int MAX_LEN = 512;

    private char[] buffer;
    private int count;
    private int position;

    private CharArrayTokenizer.Token token;

    public LineReader(InputStream in) {
        super(in);
        this.buffer = new char[MAX_LEN];
    }

    public LineReader(InputStream in, final boolean tokenized) {
        this(in);
        this.token = new CharArrayTokenizer.Token();
        this.token.array = buffer;
    }

    public String readLine(final boolean ignoreLF) throws IOException {
        if (count < 0) {
            return null;
        }

        final char[] _buffer = buffer;
        int offset = this.position;
        int chars = 0;
        String result = null;

        for ( ; offset < MAX_LEN; ) {
            int c;
            if (offset == count) {
                int _count = read(_buffer, offset, MAX_LEN - offset);
                if (_count == -1) {
                    count = c = -1;
                } else {
                    count += _count;
                    c = _buffer[offset++];
                }
            } else {
                c = _buffer[offset++];
            }

            if (c == -1) {
                break;
            }

            if (c == '\r') {
                // '\n' should follow
            } else if (c == '\n') {
                if (chars == 0) {
                    result = EMPTY_LINE;
                }
                break;
            } else {
                chars++;
            }
        }

        if (offset >= MAX_LEN) {
            throw new AssertionFailedException("NMEA line longer than " + MAX_LEN);
        }

        if (chars != 0) {
            result = new String(_buffer, position, chars);
        }

        position = offset;

        if (position > MAX_LEN >> 1 && count > -1) {
            System.arraycopy(_buffer, position, _buffer, 0, count - position);
            count -= position;
            position = 0;
        }

        return result;
    }

    public CharArrayTokenizer.Token readToken(final boolean ignoreLF) throws IOException {
        if (token == null) {
            throw new IllegalStateException("Not in token mode");
        }
        if (count == -1) {
            return null;
        }

        final char[] _buffer = buffer;
        final int maxlen = _buffer.length;

        if (position > (maxlen >> 1)) {
            System.arraycopy(_buffer, position, _buffer, 0, count - position);
            count -= position;
            position = 0;
        }

        int offset = position;
        int chars = 0;
//        CharArrayTokenizer.Token result = null;

        for ( ; offset < maxlen; ) {
            int c;
            if (offset == count) {
                int _count = read(_buffer, offset, maxlen - offset);
                if (_count == -1) {
                    count = c = -1;
                } else {
                    count += _count;
                    c = _buffer[offset++];
                }
            } else {
                c = _buffer[offset++];
            }

            if (c == -1) {
                if (chars == 0) {
                    return null;
                } else {
                    break;
                }
            }

            if (c == '\r') {
                // '\n' should follow
            } else if (c == '\n') {
                break;
            } else {
                chars++;
            }
        }

        if (offset >= maxlen) {
            throw new IllegalStateException("Line length > " + maxlen);
        }

//        if (chars != 0) {
            token.begin = position;
            token.length = chars;
//            result = token;
//        }

        position = offset;

//        return result;
        return token;
    }

    public void close() throws IOException {
        super.close();
        /* gc hints */
        buffer = null;
        token = null;
    }
}
