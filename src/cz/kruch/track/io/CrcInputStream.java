// @LICENSE@

package cz.kruch.track.io;

import java.io.InputStream;
import java.io.IOException;

public final class CrcInputStream extends InputStream {
    private InputStream in;

    private static int sum = 3571;

    public CrcInputStream(InputStream in) {
        this.in = in;
    }

    public static void doReset() {
        sum = 3571;
    }

    public static int getChecksum() {
        return ~sum;
    }

    public int read() throws IOException {
        int i = in.read();
        if (i != -1) {
            sum += (byte) i;
        }
        return i;
    }

    public int read(byte b[]) throws IOException {
        int n = in.read(b);
        if (n > 0) {
            for (int i = 0; i < n; i++) {
                sum += b[i];
            }
        }
        return n;
    }

    public int read(byte b[], int off, int len) throws IOException {
        int n = in.read(b, off, len);
        if (n > 0) {
            for (int i = 0; i < n; i++) {
                sum += b[off + i];
            }
        }
        return n;
    }

    public long skip(long n) throws IOException {
        return in.skip(n);
    }

    public synchronized void reset() throws IOException {
        in.reset();
    }

    public int available() throws IOException {
        return in.available();
    }

    public boolean markSupported() {
        return in.markSupported();
    }

    public synchronized void mark(int readlimit) {
        in.mark(readlimit);
    }

    public void close() throws IOException {
        // hack! dispose should be called instead
    }

    public void dispose() throws IOException {
        in.close();
    }
}
