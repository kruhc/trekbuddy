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

import java.io.InputStream;
import java.io.IOException;

public class InfiniteInputStream extends InputStream {

    private InputStream in;
    private byte[] one;

    public InfiniteInputStream(InputStream in) {
        this.in = in;
        this.one = new byte[1];
    }

    public void use(InputStream in) throws IllegalStateException {
        if (this.in != null) {
            throw new IllegalStateException("Already in use");
        }
        this.in = in;
    }

    public int read() throws IOException {
        return read(one, 0 , 1);
    }

    public int read(byte[] b) throws IOException {
        return in.read(b);
    }

    public int read(byte[] b, int off, int len) throws IOException {
        return in.read(b, off, len);
    }

    public long skip(long n) throws IOException {
        return in.skip(n);
    }

    public int available() throws IOException {
        return in.available();
    }

    public synchronized void mark(int readlimit) {
        in.mark(readlimit);
    }

    public synchronized void reset() throws IOException {
        in.reset();
    }

    public boolean markSupported() {
        return in.markSupported();
    }

    public void close() throws IOException {
        in.close();
        in = null;
    }
}
