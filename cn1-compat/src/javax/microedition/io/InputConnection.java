package javax.microedition.io;

import java.io.InputStream;
import java.io.IOException;
import java.io.DataInputStream;

public interface InputConnection extends Connection {

    InputStream openInputStream() throws IOException;
    
    DataInputStream openDataInputStream() throws IOException;
}
