package javax.microedition.io;

import java.io.OutputStream;
import java.io.IOException;
import java.io.DataOutputStream;

public interface OutputConnection extends Connection {

    OutputStream openOutputStream() throws IOException;
    
    DataOutputStream openDataOutputStream() throws IOException;
}
