// @LICENSE@

package api.io;

import java.io.OutputStream;
import java.io.IOException;

/**
 * Buffered output stream.
 */
public final class BufferedOutputStream extends OutputStream {
    /** underlying stream */
    private OutputStream out;
    /** write buffer */
    private byte[] buffer;
    /** number of bytes in the buffer */
    private int count;
    /** autoflush */
    private boolean autoflush;

    /**
     * Constructor.
     *
     * @param out underlying output stream
     * @param size buffer size
     * @param autoflush
     * @throws IllegalArgumentException if size <= 0.
     */
    public BufferedOutputStream(OutputStream out, int size, boolean autoflush) {
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        }
        this.out = out;
        this.buffer = new byte[size];
        this.autoflush = autoflush;
    }

    /*
     * OutputStream contract
     */

    public void write(int b) throws IOException {
        if (count >= buffer.length) {
            flushBuffer();
        }
        buffer[count++] = (byte) b;
    }

    public void write(byte[] b, int off, int len) throws IOException {
        if (len >= buffer.length) {
            flushBuffer();
            out.write(b, off, len);
            return;
        }
        if (len > buffer.length - count) {
            flushBuffer();
        }
        System.arraycopy(b, off, buffer, count, len);
        count += len;
    }

    public void flush() throws IOException {
        flushBuffer();
        out.flush();
    }

    public void close() throws IOException {
        flush();
        out.close();
        out = null;
    }

    /*
     * private stuff
     */

    private void flushBuffer() throws IOException {
        if (count > 0) {
            out.write(buffer, 0, count);
            count = 0;
            if (autoflush) {
                out.flush();
            }
        }
    }
}
