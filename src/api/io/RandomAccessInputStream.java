 // @LICENSE@
 
package api.io;

//#ifdef __ANDROID__

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

public final class RandomAccessInputStream extends InputStream {

    private RandomAccessFile file;

    public RandomAccessInputStream(String url) throws FileNotFoundException {
        this.file = new RandomAccessFile(url.substring("file://".length()), "r");
    }

    public int read() throws IOException {
        return file.read();
    }

    public int read(byte[] buffer) throws IOException {
        return file.read(buffer, 0, buffer.length);
    }

    public int read(byte[] buffer, int offset, int length) throws IOException {
        return file.read(buffer, offset, length);
    }

    public long skip(long n) throws IOException {
        if (n > 0) {
            file.seek(file.getFilePointer() + n);
        }
        return n;
    }

    public int available() throws IOException {
        return 0;
    }

    public void close() throws IOException {
        try {
            file.close();
        } finally {
            file = null;
        }
    }

    public synchronized void mark(int marklimit) {
        // OK
    }

    public boolean markSupported() {
        return false; // hack
    }

    public synchronized void reset() throws IOException {
        file.seek(0);
    }
}

 //#endif
