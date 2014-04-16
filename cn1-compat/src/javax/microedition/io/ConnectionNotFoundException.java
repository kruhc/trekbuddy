package javax.microedition.io;

import java.io.IOException;

public class ConnectionNotFoundException extends IOException {

    public ConnectionNotFoundException() {
        super();
    }

    public ConnectionNotFoundException(String string) {
        super(string);
    }
}
