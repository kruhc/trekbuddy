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

    private static final int BUFF_SIZE  = 512;
    private static final int MAX_LINE   = 128;

    private char[] buffer;
    private int count;
    private int position;

    private CharArrayTokenizer.Token token;

    public LineReader(InputStream in) {
        super(in);
        this.buffer = new char[BUFF_SIZE];
/*
    }

    public LineReader(InputStream in, final boolean tokenized) {
        this(in);
*/
        this.token = new CharArrayTokenizer.Token();
        this.token.array = buffer;
    }

    public String readLine(final boolean ignoreLF) throws IOException {
        final CharArrayTokenizer.Token result = readToken(ignoreLF);
        if (result != null) {
            if (!result.isEmpty()) {
                return result.toString();
            }
            return EMPTY_LINE;
        }
        return null;
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

        if (position > BUFF_SIZE - MAX_LINE) { // reaching end of buffer
            System.arraycopy(buffer, position, buffer, 0, count - position);
            count -= position;
            position = 0;
        }

        final char[] _buffer = buffer;
        int offset = position;
        int chars = 0;

        for ( ; offset < BUFF_SIZE; ) {
            final int c;
            if (offset == count) {
                final int _count = read(_buffer, offset, BUFF_SIZE - offset);
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

        if (offset >= BUFF_SIZE) {
            throw new IllegalStateException("Line length > " + MAX_LINE);
        }

        token.begin = position;
        token.length = chars;
        position = offset;

        return token;
    }
}
