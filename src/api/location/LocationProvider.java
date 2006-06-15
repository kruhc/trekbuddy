// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.location;

public abstract class LocationProvider {
    public static final int AVAILABLE = 1;
    public static final int TEMPORARILY_UNAVAILABLE = 2;
    public static final int OUT_OF_SERVICE = 3;

    private String name;

    protected LocationProvider(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public abstract void start() throws LocationException;
    public abstract void stop() throws LocationException;

    public abstract void setLocationListener(LocationListener listener,
                                             int interval,
                                             int timeout,
                                             int maxAge);

    public abstract Object getImpl();

    public LocationException getException() { return null; }
}
