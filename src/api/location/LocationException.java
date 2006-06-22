// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.location;

public class LocationException extends Exception {
    public LocationException() {
    }

    public LocationException(String string) {
        super(string);
    }

    public LocationException(Exception exception) {
        super(exception.toString());
    }
}
