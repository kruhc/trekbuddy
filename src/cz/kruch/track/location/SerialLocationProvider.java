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

package cz.kruch.track.location;

import api.location.LocationException;
import api.location.LocationProvider;
import api.location.Location;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.ui.Desktop;

import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import java.io.OutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.TimerTask;

/**
 * Serial port (comm, btspp) location provider implemenation.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public class SerialLocationProvider
        extends StreamReadingLocationProvider
        implements Runnable {
    
    private static final long MAX_PARSE_PERIOD = 15 * 1000; // 15 sec
    private static final long MAX_STALL_PERIOD = 60 * 1000; // 1 min

    protected volatile String url;

    private volatile InputStream stream;
    private volatile StreamConnection connection;

    private TimerTask watcher;

    private volatile long last;

    public SerialLocationProvider() throws LocationException {
        this("Serial");
    }

    /* package access */
    SerialLocationProvider(String name) throws LocationException {
        super(name);
    }

    public boolean isRestartable() {
        return !(getThrowable() instanceof RuntimeException);
    }

    protected String getKnownUrl() {
        if (Config.locationProvider == Config.LOCATION_PROVIDER_HGE100) {
            return "comm:AT5;baudrate=9600";
        }
        return Config.commUrl;
    }

    protected void refresh() {
    }

    protected void startKeepAlive(StreamConnection c) {
    }

    protected void stopKeepAlive() {
    }

    public void run() {
        // be gentle and safe
        if (restarts++ > 0) {

            // not so fast
            if (lastState == LocationProvider._STALLED) { // give hardware a while
                refresh();
            } else { // take your time (5 sec)
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }

        // start with last known?
        if (url == null) {
            url = getKnownUrl();
        }

        // reset last I/O stamp
        lastIO = System.currentTimeMillis();

        // let's roll
        baby();

        try {

            // main loop
            gps();

        } catch (Throwable t) {

//#ifdef __LOG__
            t.printStackTrace();
//#endif

            // record
            setStatus("Top level error");
            setThrowable(t);

        } finally {

            // almost dead
            zombie();
        }
    }

    public int start() throws LocationException {
        // start thread
        (new Thread(this)).start();
        
        return LocationProvider._STARTING;
    }

    /* this provider has special shutdown sequence */
    public void stop() throws LocationException {

        // die gracefully
        die();

        // non-blocking forcible kill
        (new Thread(new UniversalSoldier(UniversalSoldier.MODE_KILLER))).start();
    }

    private void startWatcher() {
        if (watcher == null) {
            watcher = new UniversalSoldier(UniversalSoldier.MODE_WATCHER);
            Desktop.timer.schedule(watcher, 5000, 5000); // delay = period = 5 sec
        }
    }

    private void stopWatcher() {
        if (watcher != null) {
            watcher.cancel();
            watcher = null;
        }
    }

    private void gps() throws IOException {
        final boolean isHge100 = cz.kruch.track.TrackingMIDlet.sonyEricsson && url.indexOf("AT5") > -1;
        final boolean rw = isHge100 || Config.btKeepAlive != 0;

        try {
            // open connection
            connection = (StreamConnection) Connector.open(url, rw ? Connector.READ_WRITE : Connector.READ, true);

            // still good to go
            if (!isGo()) {
                throw new IOException("interrupted");
            }

            // HGE-100 hack
            if (isHge100) {
                OutputStream os = connection.openOutputStream();
                os.write("$STA\r\n".getBytes());
                os.close();
            }

            // start keep-alive
            startKeepAlive(connection);

            // still good to go
            if (!isGo()) {
                throw new IOException("interrupted");
            }

            // open stream for reading
            stream = connection.openInputStream();

            // clear error
            setStatus(null);
            setThrowable(null);

            // reset data
            reset();

            // start watcher
            startWatcher();

            // read NMEA until error or stop request
            while (isGo()) {

                Location location = null;

                try {

                    // get next location
                    location = nextLocation(stream, observer);

                } catch (IOException e) {
//#ifdef __LOG__
                    e.printStackTrace();
//#endif

                    // record
                    setStatus("I/O error");
                    setThrowable(e);

                    /*
                     * location is null - loop ends
                     */

                } catch (Throwable t) {
//#ifdef __LOG__
                    t.printStackTrace();
//#endif

                    // stop request?
                    if (t instanceof InterruptedException) {
                        break;
                    }

                    // record
                    setThrowable(t);

                    // counter
                    errors++;

                    // ignore - let's go on
                    continue;
                }

                // end of data?
                if (location == null) {
                    break;
                }

                // new location timestamp
                last = System.currentTimeMillis();

                // send new location
                notifyListener(location);

                // state change?
                final int newState = location.getFix() > 0 ? AVAILABLE : TEMPORARILY_UNAVAILABLE;
                if (lastState != newState) {
                    lastState = newState;
                    notifyListener(lastState);
                }

            } // for (; go ;)

        } finally {

            // stop watcher
            stopWatcher();

            // stop keep-alive
            stopKeepAlive();

            // cleanup
            synchronized (this) {

                // close input stream
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (Exception e) {
                        // ignore
                    }
                    stream = null;
                }

                // close serial/bt connection
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (Exception e) {
                        // ignore
                    }
                    connection = null;
                }
            }

            // can GC help?!?
            System.gc();
        }
    }

    private final class UniversalSoldier extends TimerTask {
        private static final int MODE_WATCHER       = 0;
        private static final int MODE_KILLER        = 1;

        private int mode;

        public UniversalSoldier(int mode) {
            this.mode = mode;
        }

        public void run() {
            switch (mode) {
                case MODE_WATCHER: {
                    boolean notify = false;
                    final long now = System.currentTimeMillis();

                    if (now > (lastIO + MAX_STALL_PERIOD)) {
                        if (lastState != _STALLED) {
                            lastState = _STALLED;
                            notify = true;
                        }
                    } else if (now > (last + MAX_PARSE_PERIOD)) {
                        if (lastState != TEMPORARILY_UNAVAILABLE && lastState != _STARTING) {
                            lastState = TEMPORARILY_UNAVAILABLE;
                            notify = true;
                        }
                    }

                    if (notify) {
                        notifyListener(lastState);
                    }
                } break;

                case MODE_KILLER: { // kill current connection
                    synchronized (SerialLocationProvider.this) {
                        if (stream != null) {
                            try {
                                stream.close(); // hopefully forces a thread blocked in read() to receive IOException
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                    }
                } break;
            }
        }
    }
}
