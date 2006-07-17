// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.location;

public abstract class LocationProvider {
    public static final int _STARTING = 0; // non-JSR_179
    public static final int AVAILABLE = 1;
    public static final int TEMPORARILY_UNAVAILABLE = 2;
    public static final int OUT_OF_SERVICE = 3;

    private String name;
    private LocationListener listener;
    private LocationException exception;

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

    public synchronized LocationException getException() {
        return exception;
    }

    protected synchronized void setException(LocationException exception) {
        this.exception = exception;
    }

    public abstract int start() throws LocationException;
    public abstract void stop() throws LocationException;
    public abstract Object getImpl();
    public abstract void setLocationListener(LocationListener listener,
                                             int interval, int timeout, int maxAge);

    protected synchronized void notifyListener(int newState) {
        if (listener != null) {
            try {
                listener.providerStateChanged(this, newState);
            } catch (Throwable t) {
                if (t instanceof LocationException) {
                    setException((LocationException) t);
                } else {
                    setException(new LocationException(t.toString()));
                }
            }
        }
    }

    protected synchronized void notifyListener(Location location) {
        if (listener != null) {
            try {
               listener.locationUpdated(this, location);
            } catch (Throwable t) {
                if (t instanceof LocationException) {
                    setException((LocationException) t);
                } else {
                    setException(new LocationException(t.toString()));
                }
            }
        }
    }
}
