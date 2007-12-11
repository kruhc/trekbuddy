/*
 * Copyright 2006-2007 Ales Pour <kruhc@seznam.cz>.
 * All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package cz.kruch.track.io;

import cz.kruch.track.util.CharArrayTokenizer;

import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Line reading class for text files (calibration files, NMEA logs, ...).
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
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
/*
    }

    public LineReader(InputStream in, final boolean tokenized) {
        this(in);
*/
        this.token = new CharArrayTokenizer.Token();
        this.token.array = buffer;
    }

    public String readLine(final boolean ignoreLF) throws IOException {
/*
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
            throw new IllegalStateException("NMEA line longer than " + MAX_LEN);
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
*/
        CharArrayTokenizer.Token result = readToken(ignoreLF);
        if (result == null) {
            return null;
        }
        if (result.isEmpty()) {
            return EMPTY_LINE;
        }
        return result.toString();
    }

    public CharArrayTokenizer.Token readToken(final boolean ignoreLF) throws IOException {
/* always true, see contructor
        if (token == null) {
            throw new IllegalStateException("Not in token mode");
        }
*/
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

        for ( ; offset < maxlen; ) {
            final int c;
            if (offset == count) {
                final int _count = read(_buffer, offset, maxlen - offset);
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
        buffer = null; // gc hint
        token = null; // gc hint
    }
}
