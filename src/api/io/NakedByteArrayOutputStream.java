package api.io;

import java.io.ByteArrayOutputStream;

public final class NakedByteArrayOutputStream extends ByteArrayOutputStream {

    public NakedByteArrayOutputStream(int size) {
        super(size);
    }

    public byte[] getBuf() {
        return super.buf;
    }

    public int getCount() {
        return super.count;
    }
}
