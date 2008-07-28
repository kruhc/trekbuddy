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
public final class LineReader {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("LineReader");
//#endif

    public static final String EMPTY_LINE = "";

    private static final int DEFAULT_BUFF_SIZE  = 1024;
    private static final int MAX_LINE           = 256;

    private char[] buffer;
    private int count;
    private int position;

    private CharArrayTokenizer.Token token;

    private static InfiniteInputStream iin;
    private static InputStreamReader iir;

    private static void ensureReader(InputStream in) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("get reader for " + in);
//#endif

        if (iin == null) {
            iin = new InfiniteInputStream(in);
        } else {
            iin.use(in);
        }
        if (iir == null) {
            iir = new InputStreamReader(iin);
        }
    }

    public LineReader(InputStream in) {
        this(in, DEFAULT_BUFF_SIZE);
    }

    public LineReader(InputStream in, int buffSize) {
        ensureReader(in);
        this.buffer = new char[buffSize];
        this.token = new CharArrayTokenizer.Token();
        this.token.array = buffer;
    }

    public void close() throws IOException {
        iin.close();
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

        final char[] buffer = this.buffer;
        final int BUFF_SIZE = buffer.length;
        final CharArrayTokenizer.Token token = this.token;

        if (position > BUFF_SIZE - MAX_LINE) { // reaching end of buffer
            System.arraycopy(buffer, position, buffer, 0, count - position);
            count -= position;
            position = 0;
        }

        int count = this.count;
        int offset = this.position;
        int chars = 0;

        while (offset < BUFF_SIZE) {
            final int c;
            if (offset < count) {
                c = buffer[offset++];
            } else {
                final int _count = iir.read(buffer, offset, BUFF_SIZE - offset);
                if (_count == -1) {
                    this.count = -1;
                    if (chars == 0) {
                        return null;
                    } else {
                        break;
                    }
                } else {
                    this.count += _count;
                    count += _count;
                    c = buffer[offset++];
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

        if (offset < BUFF_SIZE) {

            // prepare token
            token.begin = position;
            token.length = chars;

            // store input buffer position
            this.position = offset;

            return token;
        }

        throw new IllegalStateException("Line length > " + MAX_LINE);
    }
}
