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

    public static int syncs, mismatches, checksums, restarts, stalls, errors, pings;
    
    private String name;
    private LocationListener listener;
    private Throwable throwable;
    private Object status;

    protected volatile OutputStream observer;
    protected volatile Thread thread;
    protected volatile boolean go;
    
    private volatile int lastState;

    protected LocationProvider(String name) {
        this.name = name;
        syncs = mismatches = checksums = restarts = stalls = errors = pings = 0;
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

    public boolean isRestartable() {
        return false;
    }

    public void setObserver(OutputStream observer) {
        this.observer = observer;
    }

    public synchronized int getLastState() {
        return lastState;
    }

    public synchronized boolean updateLastState(int state) {
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

    public final boolean isGo() {
        synchronized (this) {
            return go;
        }
    }
    
    protected final void baby() {
        // debug
        setStatus("starting");
        
        // wait for previous thread to die... oh yeah, shit happens sometimes
        if (thread != null) {
            if (thread.isAlive()) {
                setThrowable(new IllegalStateException("Previous connection still active"));
                thread.interrupt();
            }
            try {
                thread.join();
            } catch (InterruptedException e) {
                // ignore
            }
            thread = null; // gc hint
        }

        // just about to start
        go = true;
        thread = Thread.currentThread();

        // signal state change
        notifyListener(lastState = _STARTING); // trick to start tracklog

        // try to catch up with UI
        Thread.yield();
    }

    protected final void zombie() {
        // debug
        setStatus("zombie");

        // be ready for restart
        synchronized (this) {
            go = false;
        }

        // signal state change
        notifyListener(lastState = OUT_OF_SERVICE);
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

    protected final void notifyListener(final boolean isRecording) {
        if (listener != null) {
            try {
                listener.tracklogStateChanged(this, isRecording);
            } catch (Throwable t) {
                setThrowable(t);
            }
        }
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
