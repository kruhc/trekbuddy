// @LICENSE@

package api.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * Buffered input stream. Reusable and configurable behaviour.
 */
public final class BufferedInputStream extends InputStream {
    /** underlying stream */
    private InputStream in;
    /** read buffer */
    private byte[] buf;
    /** number of bytes in the buffer */
    private int count;
    /** current position in the buffer */
    private int pos;
//#ifdef __MARKSUPPORT__
    /** current mark limit */
    private int marklimit;
    /** currently marked position */
    private int markpos;
    // nr of reads after mark()
    private int fillcount;
//#endif
    // allow autofill
    private boolean autofill;

    public BufferedInputStream(InputStream in, int size) {
        this.in = in;
        this.buf = new byte[size];
        this.autofill = true;
//#ifdef __MARKSUPPORT__
        this.markpos = -1;
//#endif
    }

    public InputStream setInputStream(InputStream in) {
        this.in = null; // gc hint
        this.in = in;
        this.pos = this.count = 0;
//#ifdef __MARKSUPPORT__
        this.fillcount = 0;
        this.markpos = -1;
//#endif

        return this;
    }

    public void setAutofill(boolean autofill) {
        this.autofill = autofill;
    }

    public int read() throws IOException {
/* // original implementation
        if (pos >= count) {
            fillBuffer();
            if (pos >= count) {
                return -1;
            }
        }

        return buffer[pos++] & 0xff;
*/

        /* Optimistic variant first, at the expense of duplicated code... */
        if (pos < count) {
            return buf[pos++] & 0xff;
        }

        /* buffer is depleted, fill it */
        if (autofill) {
            // fill(buf.length) inlined
            pos = 0;
//#ifdef __MARKSUPPORT__
            if (fillcount++ > 0) {
                markpos = -1;
            }
//#endif
            count = in.read(buf, 0, buf.length);
            // ~
        } else {
//#ifdef __MARKSUPPORT__
            markpos = -1;
//#endif
            return in.read();
        }

        /* got something? count is either -1 or greater than 0 */
        if (count > 0) {
            return buf[pos++] & 0xff;
//        } else if (count == 0) {
//            throw new RuntimeException("stream.read()=0");
        }

        return -1;
    }

    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    public int read(byte[] b, int off, int len) throws IOException {
        int n = 0;

        if (len > 0) {
            final int avail = count - pos;
            if (avail > 0) {
                n = avail < len ? avail : len;
                System.arraycopy(buf, pos, b, off, n);
                pos += n;
            } else {
//#ifdef __MARKSUPPORT__
                // fill(len) inlined
                pos = 0;
                if (fillcount++ > 0) {
                    markpos = -1;
                }
                n = count = in.read(buf, 0, autofill ? buf.length : len);
                // ~
                if (n > 0) { // count is either -1 or greater than 0
                    n = n < len ? n : len;
                    System.arraycopy(buf, 0, b, off, n);
                    pos += n;
                }
//#else
                if (autofill && len < buf.length) {
                    pos = 0;
                    n = count = in.read(buf, 0, buf.length);
                    if (n > 0) { // count is either -1 or greater than 0
                        n = n < len ? n : len;
                        System.arraycopy(buf, 0, b, off, n);
                        pos += n;
                    }
                } else {
                    n = in.read(b, off, len);
                }
//                if (n == 0) {
//                    throw new RuntimeException("stream.read([])=0");
//                }
//#endif
            }
        }

        return n;
    }

    public long skip(long n) throws IOException {
        if (n > 0) {
            final long avail = count - pos;
            if (avail > 0) {
                n = avail < n ? avail : n;
                pos += n;
            } else { 
                n = in.skip(n); /* should be read-through */
//#ifdef __MARKSUPPORT__
                if (n > 0) {
                    markpos = -1;
                }
//#endif
            }
        }

        return n;
    }

    public int available() throws IOException {
        final int n = count - pos;
        if (n > 0) {
            return n;
        }
        return in.available();
    }

    public void close() throws IOException {
        pos = count = 0; // prepare for reuse
//#ifdef __MARKSUPPORT__
        fillcount = 0;
        markpos = -1;
//#endif
        if (in != null) { // beware it may be null
            try {
                in.close();
            } finally {
                in = null; // gc hint
            }
        }
    }

//#ifdef __MARKSUPPORT__

    public void mark(int readlimit) {
        marksCount++;
        marklimit = readlimit;
        markpos = pos;
        fillcount = 0;
    }

    public boolean markSupported() {
        return true;
    }

    public void reset() throws IOException {
        resetsCount++;
        if (in == null) {
            throw new IOException("Stream is closed");
        }
        if (markpos < 0) {
            throw new IOException("Resetting to invalid mark");
        }
        pos = markpos;
    }

    /* stream characteristic */
    public static int marksCount, resetsCount;

//#endif

}
