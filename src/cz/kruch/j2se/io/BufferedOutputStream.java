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
 * The class implements a buffered output stream. By setting up such
 * an output stream, an application can write bytes to the underlying
 * output stream without necessarily causing a call to the underlying
 * system for each byte written.
 *
 * @author Arthur van Hoff
 * @version 1.31, 01/23/03
 * @since JDK1.0
 */
public final class BufferedOutputStream extends /* FilterOutputStream */ OutputStream {

    /**
     * Underlying stream.
     */
    private OutputStream out;

    /**
     * The internal buffer where data is stored.
     */
    private byte buf[];

    /**
     * The number of valid bytes in the buffer. This value is always
     * in the range <tt>0</tt> through <tt>buf.length</tt>; elements
     * <tt>buf[0]</tt> through <tt>buf[count-1]</tt> contain valid
     * byte data.
     */
    private int count;

    /**
     * Creates a new buffered output stream to write data to the
     * specified underlying output stream with the specified buffer
     * size.
     *
     * @param out  the underlying output stream.
     * @param size the buffer size.
     * @throws IllegalArgumentException if size &lt;= 0.
     */
    public BufferedOutputStream(OutputStream out, int size) {
        this.out = out; /* super(out); */
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        }
        buf = new byte[size];
    }

    /**
     * Flush the internal buffer
     */
    private void flushBuffer() throws IOException {
        if (count > 0) {
            out.write(buf, 0, count);
            count = 0;
        }
    }

    /**
     * Writes the specified byte to this buffered output stream.
     *
     * @param b the byte to be written.
     * @throws IOException if an I/O error occurs.
     */
    public void write(int b) throws IOException {
        if (count >= buf.length) {
            flushBuffer();
        }
        buf[count++] = (byte) b;
    }

    /**
     * Writes <code>len</code> bytes from the specified byte array
     * starting at offset <code>off</code> to this buffered output stream.
     * <p/>
     * <p> Ordinarily this method stores bytes from the given array into this
     * stream's buffer, flushing the buffer to the underlying output stream as
     * needed.  If the requested length is at least as large as this stream's
     * buffer, however, then this method will flush the buffer and write the
     * bytes directly to the underlying output stream.  Thus redundant
     * <code>BufferedOutputStream</code>s will not copy data unnecessarily.
     *
     * @param b   the data.
     * @param off the start offset in the data.
     * @param len the number of bytes to write.
     * @throws IOException if an I/O error occurs.
     */
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

    /**
     * Flushes this buffered output stream. This forces any buffered
     * output bytes to be written out to the underlying output stream.
     *
     * @throws IOException if an I/O error occurs.
     * @see java.io.FilterOutputStream#out
     */
    public void flush() throws IOException {
        flushBuffer();
        out.flush();
    }

    /**
     * Closes this output stream and releases any system resources
     * associated with the stream.
     * <p/>
     * The <code>close</code> method of <code>FilterOutputStream</code>
     * calls its <code>flush</code> method, and then calls the
     * <code>close</code> method of its underlying output stream.
     *
     * @throws IOException if an I/O error occurs.
     * @see java.io.FilterOutputStream#flush()
     * @see java.io.FilterOutputStream#out
     */
    public void close() throws IOException {
        try {
            flush();
        } catch (IOException ignored) {
        }
        out.close();
    }
}
