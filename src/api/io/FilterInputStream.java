package api.io;

//#if __CN1__ || __RIM50__

import java.io.InputStream;
import java.io.IOException;

public class FilterInputStream extends InputStream {

    protected volatile InputStream in;

    public FilterInputStream(InputStream in) {
        this.in = in;
    }

    public int available() throws IOException {
        return in.available();
    }

    public void close() throws IOException {
        InputStream localIn = in;
        in = null;
        if (localIn != null) {
            localIn.close();
        }
    }

    public void mark(int markLimit) {
        in.mark(markLimit);
    }

    public boolean markSupported() {
        return in.markSupported();
    }

    public int read() throws IOException {
        return in.read();
    }

    public int read(byte[] bytes) throws IOException {
        return in.read(bytes);
    }

    public int read(byte[] bytes, int offset, int length) throws IOException {
        return in.read(bytes, offset, length);
    }

    public void reset() throws IOException {
        in.reset();
    }

    public long skip(long amount) throws IOException {
        return in.skip(amount);
    }

    /* EXTENSION */
    public InputStream setInputStream(InputStream in) {
        this.in = null; // gc hint
        this.in = in;
        return this;
    }
}

//#endif