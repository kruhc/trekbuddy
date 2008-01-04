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
import api.location.LocationListener;
import api.location.Location;
import api.file.File;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.ui.Desktop;
import cz.kruch.track.Resources;
import cz.kruch.j2se.io.BufferedOutputStream;
import cz.kruch.j2se.io.BufferedInputStream;

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
public class SerialLocationProvider extends StreamReadingLocationProvider implements Runnable {
    private static final long WATCHER_PERIOD = 15 * 1000;

    protected volatile String url;

    private volatile Thread thread;
    private volatile InputStream stream;
    private volatile StreamConnection connection;

    private volatile boolean go;
    private volatile long last;
    
    private volatile TimerTask watcher;
    private volatile OutputStream nmealog;

    public SerialLocationProvider() throws LocationException {
        this("Serial");
    }

    /* package access */
    SerialLocationProvider(String name) throws LocationException {
        super(name);
        this.lastState = LocationProvider._STARTING;
    }

    public boolean isRestartable() {
        return true;
    }

    protected String getKnownUrl() {
        return Config.commUrl;
    }

    protected void refresh() {
    }

    public void run() {
        // diagnostics
        restarts++;

        // be gentle and safe
        if (restarts > 1) {

            // wait for previous thread to die
            if (thread != null) {
                if (thread.isAlive()) {
                    setThrowable(new IllegalStateException("Previous connection still active"));
                    try {
                        thread.join();
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
                thread = null; // gc hint
            }

            // give hardware a while
            if (lastState == LocationProvider._STALLED) {
                refresh();
            }
        }

        // start with last known?
        if (url == null) {
            url = getKnownUrl();
        }

        // let's roll
        go = true;
        thread = Thread.currentThread();

        try {
            // notify
            notifyListener(this.lastState = LocationProvider._STARTING);

            // start NMEA log
            startNmeaLog();

            // main loop
            gps();

        } catch (Throwable t) {

//#ifdef __LOG__
            t.printStackTrace();
//#endif

            // record
            setThrowable(t);

        } finally {

            // stop NMEA log
            stopNmeaLog();

            // be ready for restart
            go = false;
            url = null;
            thread = null;

            // update status TODO useless - listener has already been cleared
            notifyListener(LocationProvider.OUT_OF_SERVICE);
        }
    }

    public int start() throws LocationException {
        // start thread
        (new Thread(this)).start();
        
        return LocationProvider._STARTING;
    }

    public void stop() throws LocationException {
        // stop
        go = false;

        // another attempt to wake-up the thread
        if (thread != null) {
            thread.interrupt();
        }

        // close connection
        synchronized (this) {
            if (stream != null) {
                try {
                    stream.close(); // hopefully forces a thread blocked in read() to receive IOException
                } catch (IOException e) {
                    // ignore
                }
            }
            if (connection != null) {
                try {
                    connection.close(); // seems to help too
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    public void setLocationListener(LocationListener listener, int interval, int timeout, int maxAge) {
        setListener(listener);
    }

    public Object getImpl() {
        return null;
    }

    private void startWatcher() {
        if (watcher == null) {
            watcher = new AvailabilityWatcher();
            Desktop.timer.schedule(watcher, WATCHER_PERIOD, WATCHER_PERIOD); // delay = period = 15 sec
        }
    }

    private void stopWatcher() {
        if (watcher != null) {
            watcher.cancel();
            watcher = null;
        }
    }

    private void startNmeaLog() {
        // use NMEA tracklog?
        if (isTracklog() && Config.TRACKLOG_FORMAT_NMEA.equals(Config.tracklogFormat)) {

            // not yet started
            if (nmealog == null) {

                // output file
                File file = null;

                try {
                    // create file
                    file = File.open(Connector.open(Config.getFolderNmea() + GpxTracklog.dateToFileDate(System.currentTimeMillis()) + ".nmea", Connector.READ_WRITE));
                    if (!file.exists()) {
                        file.create();
                    }

                    // create output
                    nmealog = new BufferedOutputStream(file.openOutputStream(), BUFFER_SIZE);

/* fix
                    // signal recording has started
                    recordingCallback.invoke(new Integer(GpxTracklog.CODE_RECORDING_START), null);
*/
                    notifyListener(true);

                } catch (Throwable t) {

                    // show error
                    Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_START_TRACKLOG_FAILED), t, null);

                } finally {

                    // close file
                    if (file != null) {
                        try {
                            file.close();
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }
        }
    }

    private void stopNmeaLog() {
/*
        // signal recording is stopping
        recordingCallback.invoke(new Integer(GpxTracklog.CODE_RECORDING_STOP), null);
*/
        // TODO useless - listener has already been cleared in Desktop.stopTracking()
        notifyListener(false);

        // close nmea log
        if (nmealog != null) {
            try {
                nmealog.close();
            } catch (IOException e) {
                // ignore
            }
            nmealog = null;
        }
    }

    private void gps() throws IOException {
        final boolean isHge100 = cz.kruch.track.TrackingMIDlet.sonyEricsson && url.indexOf("AT5") > -1;

        try {
            // open connection
            connection = (StreamConnection) Connector.open(url, isHge100 ? Connector.READ_WRITE : Connector.READ, true);

            // HGE-100 hack
            if (isHge100) {
                OutputStream os = connection.openOutputStream();
                os.write("$STA\r\n".getBytes());
                os.close();
            }

            // open stream for reading
            stream = new BufferedInputStream(connection.openInputStream(), BUFFER_SIZE);

            // clear error
            setThrowable(null);

            // start watcher
            startWatcher();

            // read NMEA until error or stop request
            for (; go ;) {

                Location location = null;

                try {

                    // get next location
                    location = nextLocation(stream, nmealog);

                } catch (IOException e) {
//#ifdef __LOG__
                    e.printStackTrace();
//#endif

                    // record
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

                    // ignore - let's go on
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
                    last = System.currentTimeMillis();
                }
                if (stateChange) {
                    notifyListener(lastState);
                }

                // send new location
                notifyListener(location);

            } // for (; go ;)

        } finally {

            // stop watcher
            stopWatcher();

            // cleanup
            synchronized (this) {

                // close input stream
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException e) {
                        // ignore
                    } finally {
                        stream = null;
                    }
                }

                // close serial/bt connection
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (IOException e) {
                        // ignore
                    } finally {
                        connection = null;
                    }
                }
            }
        }
    }

    private final class AvailabilityWatcher extends TimerTask {

        /** to avoid generation of $1 class */
        public AvailabilityWatcher() {
        }

        public void run() {
            boolean notify = false;
            synchronized (SerialLocationProvider.this) {
                if (System.currentTimeMillis() > (last + WATCHER_PERIOD)) {
                    if (lastState != LocationProvider._STALLED) {
                        lastState = LocationProvider._STALLED;
                        notify = true;
                    }
                }
            }
            if (notify) {
                notifyListener(lastState);
            }
        }
    }
}
