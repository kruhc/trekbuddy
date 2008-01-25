/*
 * @(#)BufferedInputStream.java	1.43 03/01/23
 *
 * Copyright 2003 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package cz.kruch.j2se.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * Buffered input stream. 
 */
public final class BufferedInputStream extends InputStream {
    /** underlying stream */
    private InputStream in;
    /** read buffer */
    private byte buffer[];
    /** number of bytes in the buffer */
    private int count;
    /** current position in the buffer */
    private int pos;

    /**
     * Constructor.
     *
     * @param in underlying stream - "not null" check removed <b>may be <code>null</code>!!!</b> (since 0.9.5, for reuse) // TODO redesign
     * @param size buffer size
     */
    public BufferedInputStream(InputStream in, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        }
        this.in = in;
        this.buffer = new byte[size];
    }

    /**
     * Reuses this stream with another underlying stream.
     * Intended to avoid allocation of a new instance.
     *
     * @param in new underlying stream
     * @return this stream
     */
    public InputStream setInputStream(InputStream in) {
        this.in = null; // gc hint
        this.in = in;
        this.pos = this.count = 0;

        return this;
    }

    /*
     * InputStream contract
     */

    public int read() throws IOException {
        if (pos >= count) {
            fill();
            if (pos >= count) {
                return -1;
            }
        }

        return buffer[pos++] & 0xff;
    }

    public int read(byte b[]) throws IOException {
        return read(b, 0, b.length);
    }

    public int read(byte b[], int off, int len) throws IOException {
        if ((off | len | (off + len) | (b.length - (off + len))) < 0) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        int n = read1(b, off, len);
        if (n <= 0) {
            return n;
        }

        while ((n < len) && (in.available() > 0)) {
            int n1 = read1(b, off + n, len - n);
            if (n1 <= 0) {
                break;
            }
            n += n1;
        }

        return n;
    }

    public long skip(long n) throws IOException {
        if (n <= 0) {
            return 0;
        }

        final long avail = count - pos;

        if (avail <= 0) {
            return in.skip(n);
        }

        final long skipped = avail < n ? avail : n;

        pos += skipped;

        return skipped;
    }

    public int available() throws IOException {
        return (count - pos) + in.available();
    }

    public void close() throws IOException {
        if (in != null) { // beware it may be null
            in.close();
            in = null; // gc hint
        }
        pos = count = 0; // prepare for reuse
    }

    /*
     * private stuff
     */

    private void fill() throws IOException {
        count = pos = 0;
        int n = in.read(buffer, pos, buffer.length - pos);
        if (n > 0) {
            count = n + pos;
        }
    }

    private int read1(byte[] b, int off, int len) throws IOException {
        int avail = count - pos;
        if (avail <= 0) {
            /* If the requested length is at least as large as the buffer, and
              if there is no mark/reset activity, do not bother to copy the
              bytes into the local buffer.  In this way buffered streams will
              cascade harmlessly. */
            if (len >= buffer.length) {
                return in.read(b, off, len);
            }
            fill();
            avail = count - pos;
            if (avail <= 0) {
                return -1;
            }
        }
        final int n = (avail < len) ? avail : len;
        System.arraycopy(buffer, pos, b, off, n);
        pos += n;

        return n;
    }
}
