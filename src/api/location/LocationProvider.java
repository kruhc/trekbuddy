/*
 * Copyright 2006-2007 Ales Pour <kruhc@seznam.cz>.
 * All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package api.location;

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
    public static final int _STALLED                = 0x82; // non-JSR_179
    public static final int _CANCELLED              = 0x83; // non-JSR_179

    private String name;
    private boolean tracklog;
    private LocationListener listener;
    private Throwable throwable;
    private Object status;

    protected volatile int lastState;

    protected LocationProvider(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    protected synchronized void setListener(LocationListener listener) {
        this.listener = listener;
    }

    public synchronized Throwable getThrowable() {
        return throwable;
    }

    protected synchronized void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    public boolean isTracklog() {
        return tracklog;
    }

    public void setTracklog(boolean tracklog) {
        this.tracklog = tracklog;
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

    public abstract int start() throws LocationException;
    public abstract void stop() throws LocationException;
    public abstract Object getImpl();
    public abstract void setLocationListener(LocationListener listener,
                                             int interval, int timeout, int maxAge);

    protected synchronized void notifyListener(Location location) {
        if (listener != null) {
            try {
               listener.locationUpdated(this, location);
            } catch (Throwable t) {
                setThrowable(t);
            }
        }
    }

    protected synchronized void notifyListener(boolean isRecording) {
        if (listener != null) {
            try {
                listener.tracklogStateChanged(this, isRecording);
            } catch (Throwable t) {
                setThrowable(t);
            }
        }
    }

    protected synchronized void notifyListener(int newState) {
        if (listener != null) {
            try {
                listener.providerStateChanged(this, newState);
            } catch (Throwable t) {
                setThrowable(t);
            }
        }
    }
}
