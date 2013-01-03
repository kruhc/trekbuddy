 // @LICENSE@
 
package api.io;

//#ifdef __ANDROID__

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

public class RandomAccessInputStream extends InputStream {

    private RandomAccessFile file;

    public RandomAccessInputStream(String url) throws FileNotFoundException {
        this.file = new RandomAccessFile(url.substring("file://".length()), "r");
    }

    public int read() throws IOException {
        return file.read();
    }

    public int read(byte[] buffer) throws IOException {
        return file.read(buffer);
    }

    public int read(byte[] buffer, int offset, int length) throws IOException {
        return file.read(buffer, offset, length);
    }

    public long skip(long n) throws IOException {
        return file.skipBytes((int) n);
    }

    public int available() throws IOException {
        return 0;
    }

    public void close() throws IOException {
        file.close();
    }

    public synchronized void mark(int marklimit) {
        // OK
    }

    public synchronized void reset() throws IOException {
        file.seek(0);
    }

    public boolean markSupported() {
        return true;
    }
}

 //#endif
