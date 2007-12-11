/*
 * @(#)BufferedOutputStream.java	1.31 03/01/23
 *
 * Copyright 2003 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package cz.kruch.j2se.io;

import java.io.OutputStream;
import java.io.IOException;

/**
 * Buffered output stream.
 */
public final class BufferedOutputStream extends OutputStream {
    private OutputStream out;
    private byte buf[];
    private int count;

    /**
     * Constructor.
     *
     * @param out underlying output stream
     * @param size buffer size
     * @throws IllegalArgumentException if size &lt;= 0.
     */
    public BufferedOutputStream(OutputStream out, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        }
        this.out = out;
        this.buf = new byte[size];
    }

    /*
     * OutputStream contract
     */

    public void write(int b) throws IOException {
        if (count >= buf.length) {
            flushBuffer();
        }
        buf[count++] = (byte) b;
    }

    public void write(byte b[], int off, int len) throws IOException {
        if (len >= buf.length) {
            /* If the request length exceeds the size of the output buffer,
               flush the output buffer and then write the data directly.
               In this way buffered streams will cascade harmlessly. */
            flushBuffer();
            out.write(b, off, len);
            return;
        }
        if (len > buf.length - count) {
            flushBuffer();
        }
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    public void flush() throws IOException {
        flushBuffer();
        out.flush();
    }

    public void close() throws IOException {
        try {
            flush();
        } catch (IOException e) {
            // ignored
        }
        buf = null; // gc hint
        out.close();
    }

    /*
     * private stuff
     */

    private void flushBuffer() throws IOException {
        if (count > 0) {
            out.write(buf, 0, count);
            count = 0;
        }
    }
}
