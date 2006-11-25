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
 * A <code>BufferedInputStream</code> adds
 * functionality to another input stream-namely,
 * the ability to buffer the input and to
 * support the <code>mark</code> and <code>reset</code>
 * methods. When  the <code>BufferedInputStream</code>
 * is created, an internal buffer array is
 * created. As bytes  from the stream are read
 * or skipped, the internal buffer is refilled
 * as necessary  from the contained input stream,
 * many bytes at a time. The <code>mark</code>
 * operation  remembers a point in the input
 * stream and the <code>reset</code> operation
 * causes all the  bytes read since the most
 * recent <code>mark</code> operation to be
 * reread before new bytes are  taken from
 * the contained input stream.
 *
 * @author Arthur van Hoff
 * @version 1.43, 01/23/03
 * @since JDK1.0
 */
public final class BufferedInputStream extends /* FilterInputStream */ InputStream {

    /**
     * Underlying stream.
     */
    private InputStream in;

    // for optimistic lie
    public static boolean useAvailableLie = true;
    private int available;

    /**
     * The internal buffer array where the data is stored. When necessary,
     * it may be replaced by another array of
     * a different size.
     */
    private byte buf[];

    /**
     * The index one greater than the index of the last valid byte in
     * the buffer.
     * This value is always
     * in the range <code>0</code> through <code>buf.length</code>;
     * elements <code>buf[0]</code>  through <code>buf[count-1]
     * </code>contain buffered input data obtained
     * from the underlying  input stream.
     */
    private int count;

    /**
     * The current position in the buffer. This is the index of the next
     * character to be read from the <code>buf</code> array.
     * <p/>
     * This value is always in the range <code>0</code>
     * through <code>count</code>. If it is less
     * than <code>count</code>, then  <code>buf[pos]</code>
     * is the next byte to be supplied as input;
     * if it is equal to <code>count</code>, then
     * the  next <code>read</code> or <code>skip</code>
     * operation will require more bytes to be
     * read from the contained  input stream.
     *
     * @see java.io.BufferedInputStream#buf
     */
    private int pos;

    /**
     * Creates a <code>BufferedInputStream</code>
     * with the specified buffer size,
     * and saves its  argument, the input stream
     * <code>in</code>, for later use.  An internal
     * buffer array of length  <code>size</code>
     * is created and stored in <code>buf</code>.
     *
     * @param in   the underlying input stream.
     * @param size the buffer size.
     * @throws IllegalArgumentException if size <= 0.
     */
    public BufferedInputStream(InputStream in, int size) {
        this.in = in; /* super(in); */
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        }
        buf = new byte[size];

        // prepare lie
        if (useAvailableLie) {
            available = size;
        } else {
            available = -1;
        }
    }

    /**
     * Fills the buffer with more data, taking into account
     * shuffling and other tricks for dealing with marks.
     * Assumes that it is being called by a synchronized method.
     * This method also assumes that all data has already been read in,
     * hence pos > count.
     */
    private void fill() throws IOException {
        count = pos = 0;
        int n = in.read(buf, pos, buf.length - pos);
        if (n > 0) {
            count = n + pos;
        }
    }

    /**
     * See
     * the general contract of the <code>read</code>
     * method of <code>InputStream</code>.
     *
     * @return the next byte of data, or <code>-1</code> if the end of the
     *         stream is reached.
     * @throws IOException if an I/O error occurs.
     * @see java.io.FilterInputStream#in
     */
    public int read() throws IOException {
        if (pos >= count) {
            fill();
            if (pos >= count) {
                return -1;
            }
        }

        return buf[pos++] & 0xff;
    }

    /**
     * Read characters into a portion of an array, reading from the underlying
     * stream at most once if necessary.
     */
    private int read1(byte[] b, int off, int len) throws IOException {
        int avail = count - pos;
        if (avail <= 0) {
            /* If the requested length is at least as large as the buffer, and
              if there is no mark/reset activity, do not bother to copy the
              bytes into the local buffer.  In this way buffered streams will
              cascade harmlessly. */
            if (len >= buf.length) {
                return in.read(b, off, len);
            }
            fill();
            avail = count - pos;
            if (avail <= 0) {
                return -1;
            }
        }
        int cnt = (avail < len) ? avail : len;
        System.arraycopy(buf, pos, b, off, cnt);
        pos += cnt;

        return cnt;
    }

    /**
     * Reads up to <code>byte.length</code> bytes of data from this
     * input stream into an array of bytes. This method blocks until some
     * input is available.
     * <p/>
     * This method simply performs the call
     * <code>read(b, 0, b.length)</code> and returns
     * the  result. It is important that it does
     * <i>not</i> do <code>in.read(b)</code> instead;
     * certain subclasses of  <code>FilterInputStream</code>
     * depend on the implementation strategy actually
     * used.
     *
     * @param b the buffer into which the data is read.
     * @return the total number of bytes read into the buffer, or
     *         <code>-1</code> if there is no more data because the end of
     *         the stream has been reached.
     * @throws IOException if an I/O error occurs.
     * @see java.io.FilterInputStream#read(byte[], int, int)
     */
    public int read(byte b[]) throws IOException {
        return read(b, 0, b.length);
    }

    /**
     * Reads bytes from this byte-input stream into the specified byte array,
     * starting at the given offset.
     * <p/>
     * <p> This method implements the general contract of the corresponding
     * <code>{@link InputStream#read(byte[], int, int) read}</code> method of
     * the <code>{@link InputStream}</code> class.  As an additional
     * convenience, it attempts to read as many bytes as possible by repeatedly
     * invoking the <code>read</code> method of the underlying stream.  This
     * iterated <code>read</code> continues until one of the following
     * conditions becomes true: <ul>
     * <p/>
     * <li> The specified number of bytes have been read,
     * <p/>
     * <li> The <code>read</code> method of the underlying stream returns
     * <code>-1</code>, indicating end-of-file, or
     * <p/>
     * <li> The <code>available</code> method of the underlying stream
     * returns zero, indicating that further input requests would block.
     * <p/>
     * </ul> If the first <code>read</code> on the underlying stream returns
     * <code>-1</code> to indicate end-of-file then this method returns
     * <code>-1</code>.  Otherwise this method returns the number of bytes
     * actually read.
     * <p/>
     * <p> Subclasses of this class are encouraged, but not required, to
     * attempt to read as many bytes as possible in the same fashion.
     *
     * @param b   destination buffer.
     * @param off offset at which to start storing bytes.
     * @param len maximum number of bytes to read.
     * @return the number of bytes read, or <code>-1</code> if the end of
     *         the stream has been reached.
     * @throws IOException if an I/O error occurs.
     */
    public int read(byte b[], int off, int len)
            throws IOException {
        if ((off | len | (off + len) | (b.length - (off + len))) < 0) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        int n = read1(b, off, len);
        if (n <= 0) return n;
        while ((n < len) && (in.available() > 0)) {
            int n1 = read1(b, off + n, len - n);
            if (n1 <= 0)
                break;
            n += n1;
        }

        return n;
    }

    /**
     * See the general contract of the <code>skip</code>
     * method of <code>InputStream</code>.
     *
     * @param n the number of bytes to be skipped.
     * @return the actual number of bytes skipped.
     * @throws IOException if an I/O error occurs.
     */
    public long skip(long n) throws IOException {
        if (n <= 0) {
            return 0;
        }
        long avail = count - pos;

        if (avail <= 0) {
            return in.skip(n);
        }

        long skipped = (avail < n) ? avail : n;
        pos += skipped;

        return skipped;
    }

    /**
     * Returns the number of bytes that can be read from this input
     * stream without blocking.
     * <p/>
     * The <code>available</code> method of
     * <code>BufferedInputStream</code> returns the sum of the the number
     * of bytes remaining to be read in the buffer
     * (<code>count&nbsp;- pos</code>)
     * and the result of calling the <code>available</code> method of the
     * underlying input stream.
     *
     * @return the number of bytes that can be read from this input
     *         stream without blocking.
     * @throws IOException if an I/O error occurs.
     * @see java.io.FilterInputStream#in
     */
    public int available() throws IOException {
        // lie upon first query
        if (available > -1) {
            int n = available;
            available = -1;
            return n;
        }

        return (count - pos) + in.available();
    }

    /**
     * Closes this input stream and releases any system resources
     * associated with the stream.
     *
     * @throws IOException if an I/O error occurs.
     */
    public void close() throws IOException {
        if (in == null) {
            return;
        }
        in.close();
    }

    /**
     * Reuse this with new stream.
     * @param in new input stream
     * @throws IOException
     */
    public void reuse(InputStream in) throws IOException {
        this.in = in;
        this.pos = this.count = 0;

        // prepare lie
        if (useAvailableLie) {
            available = buf.length;
        } else {
            available = -1;
        }
    }
}
