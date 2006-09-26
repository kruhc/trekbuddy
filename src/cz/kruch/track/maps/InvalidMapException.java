// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps;

import java.io.IOException;

public class InvalidMapException extends IOException {
    public InvalidMapException(String string) {
        super(string);
    }

    public InvalidMapException(Throwable throwable) {
        super(throwable.toString());
    }

    public InvalidMapException(String message, Throwable throwable) {
        super(message + ": " + throwable.toString());
    }
}
