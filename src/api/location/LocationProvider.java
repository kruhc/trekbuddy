// @LICENSE@

package api.location;

import java.io.OutputStream;

/**
 * Location provider abstraction.
 * 
 * @author Ales Pour <kruhc@seznam.cz>
 */
public abstract class LocationProvider {
    public static final int _STARTING               = 0x0; // non-JSR_179
    public static final int AVAILABLE               = 0x01;
    public static final int TEMPORARILY_UNAVAILABLE = 0x02;
    public static final int OUT_OF_SERVICE          = 0x03;
    public static final int _STALLED                = 0x04; // non-JSR_179
    public static final int _CANCELLED              = 0x05; // non-JSR_179

    public static int syncs, mismatches, invalids, checksums,
                      restarts, stalls, errors, pings, maxavail;

    private String name;
    private volatile LocationListener listener;
    private volatile Throwable throwable;
    private volatile Object status;

    protected volatile OutputStream observer;

    private boolean go;
    private int lastState;
    private long last;

    protected LocationProvider(String name) {
        this.name = name;
        syncs = mismatches = invalids = checksums = restarts = stalls = errors = pings = maxavail = 0;
    }

    public String getName() {
        return name;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    protected void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    public Object getStatus() {
        return status;
    }

    protected void setStatus(Object status) {
        this.status = status;
    }

    protected synchronized long getLast() {
        return last;
    }

    protected synchronized void setLast(long last) {
        this.last = last;
    }

    public boolean isRestartable() {
        return false;
    }

    public void setObserver(OutputStream observer) {
        this.observer = observer;
    }

    protected synchronized int getLastState() {
        return lastState;
    }

    private synchronized int setLastState(int state) {
        lastState = state;
        return state;
    }

    protected synchronized boolean updateLastState(int state) {
        final boolean changed = lastState != state;
        lastState = state;
        return changed;
    }

    public abstract int start() throws LocationException;

    public void stop() throws LocationException {
        die();
    }

    public final void setLocationListener(LocationListener listener) {
        this.listener = listener;
    }

    public final synchronized boolean isGo() {
        return go;
    }

    /**
     * Should only be called from provider thread, at the very beginning.
     */
    protected final void baby() {
        // debug
        setStatus("starting");

        // reset
        cz.kruch.track.util.NmeaParser.reset(); // FIXME move to startTracking or some
        setLast(0L);

        // just about to start
        synchronized (this) {
            go = true;
        }

        // signal state change
        notifyListener(setLastState(_STARTING)); // trick to start tracklog

/* TODO dangerous?
        // try to catch up with UI
        Thread.yield();
*/
    }

    /**
     * Should only be called from provider thread, at the very end.
     */
    protected final void zombie() {
        // debug
        setStatus("zombie");

        // be ready for restart
        synchronized (this) {
            go = false;
        }

        // signal state change
        notifyListener(setLastState(OUT_OF_SERVICE));
    }

    protected final void die() {
        // debug
        setStatus("requesting stop");
        
        // shutdown provider thread
        synchronized (this) {
            go = false;
            notify();
        }
    }

    protected final void notifyListener(final Location location) {
        if (listener != null) {
            try {
               listener.locationUpdated(this, location);
            } catch (Throwable t) {
                setThrowable(t);
            }
        }
    }

    protected final void notifyListener2(final Location location) {
        if (location.getFix() > 0) {
            if (updateLastState(AVAILABLE)) {
                notifyListener(AVAILABLE);
            }
        } else {
            if (updateLastState(TEMPORARILY_UNAVAILABLE)) {
                notifyListener(TEMPORARILY_UNAVAILABLE);
            }
        }
        notifyListener(location);
    }

    protected final void notifyListener(final int newState) {
        if (listener != null) {
            try {
                listener.providerStateChanged(this, newState);
            } catch (Throwable t) {
                setThrowable(t);
            }
        }
    }
}
