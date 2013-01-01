/*
*  Licensed to the Apache Software Foundation (ASF) under one or more
*  contributor license agreements.  See the NOTICE file distributed with
*  this work for additional information regarding copyright ownership.
*  The ASF licenses this file to You under the Apache License, Version 2.0
*  (the "License"); you may not use this file except in compliance with
*  the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software
*  distributed under the License is distributed on an "AS IS" BASIS,
*  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*  See the License for the specific language governing permissions and
*  limitations under the License.
*/

package api.io;

import java.io.InputStream;
import java.io.IOException;

/**
 * Wraps an existing {@link java.io.InputStream} and <em>buffers</em> the input.
 * Expensive interaction with the underlying input stream is minimized, since
 * most (smaller) requests can be satisfied by accessing the buffer alone. The
 * drawback is that some extra space is required to hold the buffer and that
 * copying takes place when filling that buffer, but this is usually outweighed
 * by the performance benefits.
 * <p/>
 * <p/>A typical application pattern for the class looks like this:<p/>
 * <p/>
 * <pre>
 * BufferedInputStream buf = new BufferedInputStream(new FileInputStream(&quot;file.java&quot;));
 * </pre>
 *
 * @see BufferedOutputStream
 */
public class BufferedInputStream extends InputStream {
    /***
     * The source input stream that is buffered.
     */
    protected volatile InputStream in;

    /**
     * The buffer containing the current bytes read from the target InputStream.
     */
    protected volatile byte[] buf;

    /**
     * The total number of bytes inside the byte array {@code buf}.
     */
    protected int count;

    /**
     * The current limit, which when passed, invalidates the current mark.
     */
    protected int marklimit;

    /**
     * The currently marked position. -1 indicates no mark has been set or the
     * mark has been invalidated.
     */
    protected int markpos = -1;

    /**
     * The current position within the byte array {@code buf}.
     */
    protected int pos;

    /**
     * Constructs a new {@code BufferedInputStream} on the {@link InputStream}
     * {@code in}. The default buffer size (8 KB) is allocated and all reads
     * can now be filtered through this stream.
     *
     * @param in the InputStream the buffer reads from.
     */
    public BufferedInputStream(InputStream in) {
        this.in = in;
        this.buf = new byte[8192];
    }

    /**
     * Constructs a new {@code BufferedInputStream} on the {@link InputStream}
     * {@code in}. The buffer size is specified by the parameter {@code size}
     * and all reads are now filtered through this stream.
     *
     * @param in   the input stream the buffer reads from.
     * @param size the size of buffer to allocate.
     * @throws IllegalArgumentException if {@code size < 0}.
     */
    public BufferedInputStream(InputStream in, int size) {
        this.in = in;
        if (size <= 0) {
            // K0058=size must be > 0
            throw new IllegalArgumentException("Size must be > 0"); //$NON-NLS-1$
        }
        buf = new byte[size];
    }

    /**
     * Returns the number of bytes that are available before this stream will
     * block. This method returns the number of bytes available in the buffer
     * plus those available in the source stream.
     *
     * @return the number of bytes available before blocking.
     * @throws java.io.IOException if this stream is closed.
     */
    public synchronized int available() throws IOException {
        InputStream localIn = in; // 'in' could be invalidated by close()
        if (localIn == null) {
            // K0059=Stream is closed
            throw new IOException("Stream is closed"); //$NON-NLS-1$
        }
/* // HACK // can be very slow when called frequently on some devices (Samsung, Nokia S40)
        return count - pos + localIn.available();
*/
        final int available = count - pos;
        if (available > 0) {
            return available;
        }
        return localIn.available();
    }

    /**
     * Closes this stream. The source stream is closed and any resources
     * associated with it are released.
     *
     * @throws IOException if an error occurs while closing this stream.
     */
    public void close() throws IOException {
/* // HACK
        buf = null;
*/
        InputStream localIn = in;
        in = null;
        if (localIn != null) {
            localIn.close();
        }
    }

    private int fillbuf(InputStream localIn, byte[] localBuf)
            throws IOException {
        if (markpos == -1 || (pos - markpos >= marklimit)) {
            /* Mark position not set or exceeded readlimit */
            int result = localIn.read(localBuf);
            if (result > 0) {
                markpos = -1;
                pos = 0;
                count = result == -1 ? 0 : result;
            }
            return result;
        }
        if (markpos == 0 && marklimit > localBuf.length) {
            /* Increase buffer size to accommodate the readlimit */
/* // HACK // resize not supported
            int newLength = localBuf.length * 2;
            if (newLength > marklimit) {
                newLength = marklimit;
            }
            byte[] newbuf = new byte[newLength];
            System.arraycopy(localBuf, 0, newbuf, 0, localBuf.length);
            // Reassign buf, which will invalidate any local references
            // FIXME: what if buf was null?
            localBuf = buf = newbuf;
*/
        } else if (markpos > 0) {
            System.arraycopy(localBuf, markpos, localBuf, 0,
                             localBuf.length - markpos);
        }
        /* Set the new position and mark position */
        pos -= markpos;
        count = markpos = 0;
        int bytesread = localIn.read(localBuf, pos, localBuf.length - pos);
        count = bytesread <= 0 ? pos : pos + bytesread;
        return bytesread;
    }

    /**
     * Sets a mark position in this stream. The parameter {@code readlimit}
     * indicates how many bytes can be read before a mark is invalidated.
     * Calling {@code reset()} will reposition the stream back to the marked
     * position if {@code readlimit} has not been surpassed. The underlying
     * buffer may be increased in size to allow {@code readlimit} number of
     * bytes to be supported.
     *
     * @param readlimit the number of bytes that can be read before the mark is
     *                  invalidated.
     * @see #reset()
     */
    public synchronized void mark(int readlimit) {
        marklimit = readlimit;
        markpos = pos;
        // HACK // limit mark to buffer size; pos is very probably 0
        if (marklimit > buf.length) {
            marklimit = buf.length;
        }
    }

    /**
     * Indicates whether {@code BufferedInputStream} supports the {@code mark()}
     * and {@code reset()} methods.
     *
     * @return {@code true} for BufferedInputStreams.
     * @see #mark(int)
     * @see #reset()
     */
    public boolean markSupported() {
        return true;
    }

    /**
     * Reads a single byte from this stream and returns it as an integer in the
     * range from 0 to 255. Returns -1 if the end of the source string has been
     * reached. If the internal buffer does not contain any available bytes then
     * it is filled from the source stream and the first byte is returned.
     *
     * @return the byte read or -1 if the end of the source stream has been
     *         reached.
     * @throws IOException if this stream is closed or another IOException occurs.
     */
    public synchronized int read() throws IOException {
        // Use local refs since buf and in may be invalidated by an
        // unsynchronized close()
        byte[] localBuf = buf;
        InputStream localIn = in;
        if (localIn == null) {
            // K0059=Stream is closed
            throw new IOException("Stream is closed"); //$NON-NLS-1$
        }

        /* Are there buffered bytes available? */
        if (pos >= count && fillbuf(localIn, localBuf) == -1) {
            return -1; /* no, fill buffer */
        }
/* // HACK // cannot happen - resize is commented out in fillbuf
        // localBuf may have been invalidated by fillbuf
        if (localBuf != buf) {
            localBuf = buf;
            if (localBuf == null) {
                // K0059=Stream is closed
                throw new IOException("Stream is closed"); //$NON-NLS-1$
            }
        }
*/
        /* Did filling the buffer fail with -1 (EOF)? */
        if (count - pos > 0) {
            return localBuf[pos++] & 0xFF;
        }
        return -1;
    }

    /**
     * JVM optimization. 
     */
    public int read(byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }

    /**
     * Reads at most {@code length} bytes from this stream and stores them in
     * byte array {@code buffer} starting at offset {@code offset}. Returns the
     * number of bytes actually read or -1 if no bytes were read and the end of
     * the stream was encountered. If all the buffered bytes have been used, a
     * mark has not been set and the requested number of bytes is larger than
     * the receiver's buffer size, this implementation bypasses the buffer and
     * simply places the results directly into {@code buffer}.
     *
     * @param buffer the byte array in which to store the bytes read.
     * @param offset the initial position in {@code buffer} to store the bytes read
     *               from this stream.
     * @param length the maximum number of bytes to store in {@code buffer}.
     * @return the number of bytes actually read or -1 if end of stream.
     * @throws IndexOutOfBoundsException if {@code offset < 0} or {@code length < 0}, or if
     *                                   {@code offset + length} is greater than the size of
     *                                   {@code buffer}.
     * @throws IOException               if the stream is already closed or another IOException
     *                                   occurs.
     */
    public synchronized int read(byte[] buffer, int offset, int length)
            throws IOException {
        // Use local ref since buf may be invalidated by an unsynchronized
        // close()
        byte[] localBuf = buf;
        // avoid int overflow
        if (offset > buffer.length - length || offset < 0 || length < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (length == 0) {
            return 0;
        }
        InputStream localIn = in;
        if (localIn == null) {
            // K0059=Stream is closed
            throw new IOException("Stream is closed"); //$NON-NLS-1$
        }

        int required;
        if (pos < count) {
            /* There are bytes available in the buffer. */
            int copylength = count - pos >= length ? length : count
                    - pos;
            System.arraycopy(localBuf, pos, buffer, offset, copylength);
            pos += copylength;
            if (copylength == length/* || localIn.available() == 0*/) {
                return copylength;
            }
            offset += copylength;
            required = length - copylength;
        } else {
            required = length;
        }

        while (true) {
            int read;
            /*
            * If we're not marked and the required size is greater than the
            * buffer, simply read the bytes directly bypassing the buffer.
            */
            if (markpos == -1 && required >= localBuf.length) {
                read = localIn.read(buffer, offset, required);
                if (read == -1) {
                    return required == length ? -1 : length - required;
                }
            } else {
                if (fillbuf(localIn, localBuf) == -1) {
                    return required == length ? -1 : length - required;
                }
/* // HACK // cannot happen - resize is commented out in fillbuf
                // localBuf may have been invalidated by fillbuf
                if (localBuf != buf) {
                    localBuf = buf;
                    if (localBuf == null) {
                        // K0059=Stream is closed
                        throw new IOException("Stream is closed"); //$NON-NLS-1$
                    }
                }
*/

                read = count - pos >= required ? required : count - pos;
                System.arraycopy(localBuf, pos, buffer, offset, read);
                pos += read;
            }
            required -= read;
/* // HACK // always satisfy after first read
            if (required == 0) {
                return length;
            }
            if (localIn.available() == 0) {
                return length - required;
            }
            offset += read;
*/
            return length - required;
        }
    }

    /**
     * Resets this stream to the last marked location.
     *
     * @throws IOException if this stream is closed, no mark has been set or the mark is
     *                     no longer valid because more than {@code readlimit} bytes
     *                     have been read since setting the mark.
     * @see #mark(int)
     */
    public synchronized void reset() throws IOException {
        if (in == null) {
            // K0059=Stream is closed
            throw new IOException("Stream is closed"); //$NON-NLS-1$
        }
        if (-1 == markpos) {
            // K005a=Mark has been invalidated.
            throw new IOException("Mark has been invalidated"); //$NON-NLS-1$
        }
        pos = markpos;
    }

    /**
     * Skips {@code amount} number of bytes in this stream. Subsequent
     * {@code read()}'s will not return these bytes unless {@code reset()} is
     * used.
     *
     * @param amount the number of bytes to skip. {@code skip} does nothing and
     *               returns 0 if {@code amount} is less than zero.
     * @return the number of bytes actually skipped.
     * @throws IOException if this stream is closed or another IOException occurs.
     */
    public synchronized long skip(long amount) throws IOException {
        // Use local refs since buf and in may be invalidated by an
        // unsynchronized close()
        byte[] localBuf = buf;
        InputStream localIn = in;
        if (amount < 1) {
            return 0;
        }
        if (localIn == null) {
            // K0059=Stream is closed
            throw new IOException("Stream is closed"); //$NON-NLS-1$
        }

        if (count - pos >= amount) {
            pos += amount;
            return amount;
        }
        long read = count - pos;
        pos = count;

        if (markpos != -1) {
            if (amount <= marklimit) {
                if (fillbuf(localIn, localBuf) == -1) {
                    return read;
                }
                if (count - pos >= amount - read) {
                    pos += amount - read;
                    return amount;
                }
                // Couldn't get all the bytes, skip what we read
                read += (count - pos);
                pos = count;
                return read;
            }
        }
        return read + localIn.skip(amount - read);
    }

    /**
     * Sets for reuse with another stream.
     * @param in new input stream
     * @return this
     */
    public InputStream setInputStream(InputStream in) {
        this.in = null; // gc hint
        this.in = in;
        pos = count = marklimit = 0;
        markpos = -1;
        return this;
    }
}
