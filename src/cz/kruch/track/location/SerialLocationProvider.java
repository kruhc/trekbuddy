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

    private volatile boolean go;
    private volatile Thread thread;
    private volatile StreamConnection connection;
    private volatile InputStream stream;

    private volatile long last;
    private TimerTask watcher;

    private File nmeaFile;
    private OutputStream nmeaObserver;

    public SerialLocationProvider() throws LocationException {
        this(Config.LOCATION_PROVIDER_SERIAL);
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

    public void run() {
        // diagnostics
        restarts++;

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

            // GPS
            gps();

        } catch (Throwable t) {

            // record
            setThrowable(t);

        } finally {

            // be ready for restart
            go = false;
            url = null;

            // no connection/stream held
            connection = null;
            stream = null;

            // stop NMEA log
            stopNmeaLog();

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
        // if running, stop it
        if (go) {
            go = false;
            
            /*
             * this forces a thread blocked in read() to receive IOException
             */
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        // wait for finish
        if (thread != null) {
            try {
                thread.interrupt();
                thread.join();
            } catch (InterruptedException e) {
                // should never happen
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
            watcher = new TimerTask() {
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
            };
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
            if (nmeaFile == null) {
                String path = Config.getFolderNmea() + GpxTracklog.dateToFileDate(System.currentTimeMillis()) + ".nmea";

                try {
                    // create file
                    nmeaFile = File.open(Connector.open(path, Connector.READ_WRITE));
                    if (!nmeaFile.exists()) {
                        nmeaFile.create();
                    }

                    // create writer
                    nmeaObserver = new BufferedOutputStream(nmeaFile.openOutputStream(), 1024);

                    // set stream 'observer'
                    setObserver(nmeaObserver);

/* fix
                    // signal recording has started
                    recordingCallback.invoke(new Integer(GpxTracklog.CODE_RECORDING_START), null);
*/
                    notifyListener(true);

                } catch (Throwable t) {
                    Desktop.showError("Failed to start NMEA log.", t, null);
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

        // clear stream 'observer'
        setObserver(null);

        // close writer
        if (nmeaObserver != null) {
            try {
                nmeaObserver.close();
            } catch (IOException e) {
                // ignore
            }
            nmeaObserver = null;
        }

        // close file
        if (nmeaFile != null) {
            try {
                nmeaFile.close();
            } catch (IOException e) {
                // ignore
            }
            nmeaFile = null;
        }
    }

    private void gps() throws IOException {
        try {
            // open connection
            connection = (StreamConnection) Connector.open(url, Connector.READ, true);

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
                    location = nextLocation(stream);

                } catch (IOException e) {

                    // record
                    setThrowable(e);

                    /*
                     * location is null, therefore the loop quits
                     */

                } catch (Throwable t) {

                    // record
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

                boolean stateChange = false;

                // is position valid?
                synchronized (this) {
                    if (location.getFix() > 0) {
                        if (lastState != LocationProvider.AVAILABLE) {
                            lastState = LocationProvider.AVAILABLE;
                            stateChange = true;
                        }
                    } else {
                        if (lastState != LocationProvider.TEMPORARILY_UNAVAILABLE) {
                            lastState = LocationProvider.TEMPORARILY_UNAVAILABLE;
                            stateChange = true;
                        }
                    }
                    last = System.currentTimeMillis();
                }

                // stateChange about state, if necessary
                if (stateChange) {
                    notifyListener(lastState);
                }

                // send new location
                notifyListener(location);

            } // for (; go ;)

        } finally {
                            
            // stop watcher
            stopWatcher();

            // close anyway
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    // ignore
                } finally {
                    stream = null;
                }
            }

            // close anyway
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
