/*
 * Copyright 2006-2007 Ales Pour <kruhc@seznam.cz>. All Rights Reserved.
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

package cz.kruch.track.location;

import api.location.LocationProvider;
import api.location.LocationListener;
import api.location.LocationException;
import api.location.Location;
import api.file.File;

import java.io.InputStream;
import java.io.IOException;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.ui.FileBrowser;
import cz.kruch.track.ui.Desktop;
import cz.kruch.track.event.Callback;
import cz.kruch.track.Resources;

public final class SimulatorLocationProvider
        extends StreamReadingLocationProvider
        implements Runnable, Callback {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Simulator");
//#endif

    private Thread thread;
    private boolean go;
    private File file;
    private int delay;

    public SimulatorLocationProvider() {
        super("Simulator");
        this.delay = Config.simulatorDelay;
        if (this.delay < 25) {
            this.delay = 25;
        }
    }

    public void setLocationListener(LocationListener locationListener,
                                    int interval, int timeout, int maxAge) {
        setListener(locationListener);
    }

    public Object getImpl() {
        return null;
    }

    public int start() throws LocationException {
        (new FileBrowser(Resources.getString(Resources.DESKTOP_MSG_NMEA_PLAYBACK), this, Desktop.screen)).show();

        return LocationProvider.TEMPORARILY_UNAVAILABLE;
    }

    public void stop() throws LocationException {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("stop request");
//#endif

        if (go) {
            go = false;
            try {
                thread.interrupt();
                thread.join();
            } catch (InterruptedException e) {
            }
            thread = null;
        }
    }

    public void invoke(Object result, Throwable throwable, Object source) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("playback selection: " + result);
//#endif

        if (result != null) {
            go = true;
            file = (File) result;
            thread = new Thread(this);
            thread.start();
        } else {
            notifyListener(LocationProvider.OUT_OF_SERVICE);
        }
    }

    public void run() {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("simulator task starting; url " + file.getURL());
//#endif

        InputStream in = null;

        try {
            // for start gpx
            notifyListener(LocationProvider._STARTING);

            // open input
            in = file.openInputStream();

            // notify
            notifyListener(LocationProvider.TEMPORARILY_UNAVAILABLE);

            for (; go ;) {

                Location location = null;

                // get next location
                try {
                    location = nextLocation(in, null);
                } catch (IllegalStateException e) {
                    Desktop.showError(e.getMessage(), null, null);
                } catch (Throwable t) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.warn("Failed to get location.", t);
                    t.printStackTrace();
//#endif

                    // record exception
                    if (t instanceof InterruptedException) {
                        // probably stop request
                    } else {
                        // record
                        setThrowable(t);
                    }

                    // ignore
                    continue;
                }

                // end of data?
                if (location == null) {
                    break;
                }

                // state change?
                boolean stateChange = false;
                synchronized (this) {
                    final int newState = location.getFix() > 0 ? LocationProvider.AVAILABLE : LocationProvider.TEMPORARILY_UNAVAILABLE;
                    if (lastState != newState) {
                        lastState = newState;
                        stateChange = true;
                    }
                }
                if (stateChange) {
                    notifyListener(lastState);
                }

                // send the location
                notifyListener(location);

                // interval elapse
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        } catch (Throwable t) {
            if (t instanceof InterruptedException) {
                // stop request
            }  else {
                // record
                setThrowable(t);
            }
        } finally {
            // close the stream
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            // close file connection
            try {
                file.close();
            } catch (IOException e) {
                // ignore
            }
            file = null; // gc hint

            // notify
            notifyListener(LocationProvider.OUT_OF_SERVICE);
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.info("simulator task ended");
//#endif
    }
}
