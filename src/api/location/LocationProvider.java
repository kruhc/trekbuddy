// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.location;

public abstract class LocationProvider {
    public static final int AVAILABLE = 1;
    public static final int TEMPORARILY_UNAVAILABLE = 2;
    public static final int OUT_OF_SERVICE = 3;

    private String name;
    private LocationListener listener;

    protected LocationProvider(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    protected synchronized LocationListener getListener() {
        return listener;
    }

    protected synchronized void setListener(LocationListener listener) {
        this.listener = listener;
    }

    public abstract int start() throws LocationException;
    public abstract void stop() throws LocationException;
    public abstract Object getImpl();
    public abstract LocationException getException();
    public abstract void setLocationListener(LocationListener listener,
                                             int interval, int timeout, int maxAge);

    protected synchronized void notifyListener(int newState) {
        if (listener != null) {
            try {
                listener.providerStateChanged(this, newState);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    protected synchronized void notifyListener(Location location) {
        if (listener != null) {
            try {
               listener.locationUpdated(this, location);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
