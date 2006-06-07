// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps;

import java.io.IOException;

public class InvalidMapException extends IOException {
    public InvalidMapException() {
    }

    public InvalidMapException(String string) {
        super(string);
    }
}
