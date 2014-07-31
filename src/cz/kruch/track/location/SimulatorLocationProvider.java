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

import javax.microedition.io.Connector;

public final class SimulatorLocationProvider
        extends StreamReadingLocationProvider
        implements Runnable, Callback {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Simulator");
//#endif

    private volatile String url;
    private int delay;

    public SimulatorLocationProvider() {
        super("Simulator");
        this.delay = Config.simulatorDelay;
        if (this.delay < 25) {
            this.delay = 25;
        }
    }

    public int start() throws LocationException {
//#ifndef __EMULATOR__
        (new FileBrowser(Resources.getString(Resources.DESKTOP_MSG_NMEA_PLAYBACK),
                         this, Config.FOLDER_NMEA,
                         new String[]{ ".nmea" })).show();
//#else
        try {
            invoke(File.open("file:///mmc/trekbuddy/tracks-nmea/track.nmea"), null, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
//#endif

        return LocationProvider._STARTING;
    }

    public void invoke(Object result, Throwable throwable, Object source) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("playback selection: " + result);
//#endif

        synchronized (this) {
            if (result != null) {
                url = ((String[])result)[1];
            } else {
                url = null;
            }
        }

        // restore screen after file browser
        Desktop.display.setCurrent(Desktop.screen);

        (new Thread(this)).start();
    }

    public void run() {

        // yes, thread is always started
        if (url == null) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("simulator task cancelled");
//#endif
            notifyListener(LocationProvider.OUT_OF_SERVICE);
            return;
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("simulator task starting; file: " + url);
//#endif

        // statistics
        restarts++;

        // just born
        baby();

        InputStream in = null;
        try {

            // open input
            in = Connector.openInputStream(url);

            while (isGo()) {

                Location location;

                // get next location
                try {

                    location = nextLocation(in, null);

                } catch (Throwable t) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("Failed to get location.", t);
                    t.printStackTrace();
//#endif

                    // record exception
                    if (t instanceof InterruptedException) {
                        setStatus("interrupted");
                        // probably stop request
                    } else {
                        // record
                        setThrowable(t);
                    }

                    // counter
                    errors++;
                    
                    // ignore
                    continue;
                }

                // end of data?
                if (location == null) {
                    break;
                }

                // notify listener
                notifyListener2(location);

                // interval elapse
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    // ignore
                }
            }

        } catch (Throwable t) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("I/O error? ", t);
            t.printStackTrace();
//#endif

            if (t instanceof InterruptedException) {
                setStatus("interrupted");
                // stop request
            }  else {
                // record
                setThrowable(t);
            }

        } finally {

            // close the stream
            File.closeQuietly(in);

            // zombie
            notifyListener(OUT_OF_SERVICE);
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("simulator task ended");
//#endif
    }
}
