// @LICENSE@

package api.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * Buffered input stream.
 */
public final class BufferedInputStream extends InputStream {
    /** underlying stream */
    private InputStream in;
    /** read buffer */
    private byte[] buffer;
    /** number of bytes in the buffer */
    private int count;
    /** current position in the buffer */
    private int position;

    /**
     * Constructor.
     *
     * @param in underlying stream - "not null" check removed <b>may be <code>null</code>!!!</b> (since 0.9.5, for reuse) // TODO redesign
     * @param size buffer size
     */
    public BufferedInputStream(InputStream in, int size) {
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
        this.position = this.count = 0;

        return this;
    }

    /*
     * InputStream contract
     */

    public int read() throws IOException {
/* // original variant
        if (pos >= count) {
            fillBuffer();
            if (pos >= count) {
                return -1;
            }
        }

        return buffer[pos++] & 0xff;
*/
        /* Optimistic variant first, at the expense of duplicated code... */
        if (position < count) {
            return buffer[position++] & 0xff;
        }

        final byte[] buffer = this.buffer;
        
        /* buffer is depleted, fill it */
        position = 0;
        count = in.read(buffer, 0, buffer.length);

        /* got something */
        if (position < count) {
            return buffer[position++] & 0xff;
        }

        return -1;
    }

    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    public int read(byte[] b, int off, int len) throws IOException {
        int n = 0;

        if (len > 0) {
            final int avail = count - position;
            if (avail > 0) {
                n = avail < len ? avail : len;
                System.arraycopy(buffer, position, b, off, n);
                position += n;
            } else {
                n = in.read(b, off, len);
            }
        }

        return n;
    }

    public long skip(long n) throws IOException {
        if (n > 0) {
            final long avail = count - position;
            if (avail > 0) {
                n = avail < n ? avail : n;
                position += n;
            } else { 
                n = in.skip(n);
            }
        }

        return n;
    }

    public int available() throws IOException {
        final int avail = count - position;
        if (avail > 0) {
            return avail;
        }
        return in.available();
    }

    public void close() throws IOException {
        position = count = 0; // prepare for reuse
        if (in != null) { // beware it may be null
            try {
                in.close();
            } finally {
                in = null; // gc hint
            }
        }
    }
}
