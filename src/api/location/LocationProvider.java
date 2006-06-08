// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.location;

import java.io.IOException;

public abstract class LocationProvider {
    public static final int AVAILABLE = 1;
    public static final int TEMPORARILY_UNAVAILABLE = 2;
    public static final int OUT_OF_SERVICE = 3;

    public abstract void start() throws IOException;
    public abstract void stop() throws IOException;

    public abstract void setLocationListener(LocationListener listener,
                                             int interval,
                                             int timeout,
                                             int maxAge);
}
