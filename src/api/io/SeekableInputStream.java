package api.io;

//#ifdef __RIM50__

import java.io.InputStream;
import java.io.IOException;

public final class SeekableInputStream extends FilterInputStream implements net.rim.device.api.io.Seekable {

    private net.rim.device.api.io.Seekable in;

    public SeekableInputStream(InputStream in) {
        super(in);
        this.in = (net.rim.device.api.io.Seekable) in;
    }

    public boolean markSupported() {
        return false; // hack
    }

    public void mark(int markLimit) {
        // nothing
    }

    public void reset() throws IOException {
        setPosition(0);
    }

    public long skip(long amount) throws IOException {
        setPosition(getPosition() + amount);
        return amount;
    }

    public void close() throws IOException {
        try {
            super.close();
        } finally {
            in = null;
        }
    }

    /* EXTENSION */
    public InputStream setInputStream(InputStream in) {
        super.setInputStream(in);
        this.in = (net.rim.device.api.io.Seekable) in;
        return this;
    }

    /*
     * net.rim.device.api.io.Seekable contract
     */

    public long getPosition() throws IOException {
        return in.getPosition();
    }

    public void setPosition(long position) throws IOException {
        in.setPosition(position);
    }
}

//#endif
