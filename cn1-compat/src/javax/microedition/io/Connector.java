package javax.microedition.io;

import javax.microedition.io.file.FileConnection;
import java.io.IOException;
import java.io.InputStream;

public final class Connector {
    public static final int READ = 1;
    public static final int WRITE = 2;
    public static final int READ_WRITE = 3;

    private Connector() {
    }

    public static Connection open(String name) throws IOException {
        return open(name, READ);
    }

    public static Connection open(String name, int mode) throws IOException {
        return open(name, mode, false);
    }

    public static Connection open(String name, int mode, boolean timeouts) throws IOException {
        if (name.startsWith(FileConnection.FILE_PROTOCOL)) {
            return new FileConnection(name, mode);
        }
        com.codename1.io.Log.p("Connector.open " + name + " not implemented", com.codename1.io.Log.ERROR);
        throw new ConnectionNotFoundException("not supported; " + name);
    }

    public static InputStream openInputStream(String name) throws IOException {
        if (name.startsWith(FileConnection.FILE_PROTOCOL)) {
            return new FileConnection(name, READ).openInputStream();
        }
        com.codename1.io.Log.p("Connector.openInputStream not implemented", com.codename1.io.Log.ERROR);
        throw new ConnectionNotFoundException("not supported; " + name);
    }
}
