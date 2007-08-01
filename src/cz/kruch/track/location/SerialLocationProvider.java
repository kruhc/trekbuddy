// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

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

public class SerialLocationProvider extends StreamReadingLocationProvider implements Runnable {
    private static final long WATCHER_PERIOD = 15 * 1000;

    private Thread thread;

    protected volatile String url;
    private volatile boolean go;
    private volatile StreamConnection connection;

    private long timestamp;
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

            // start watcher
            startWatcher();

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
            thread = null;
            connection = null;

            // stop NMEA log
            stopNmeaLog();

            // stop watcher
            stopWatcher();

            // update status
            notifyListener(LocationProvider.OUT_OF_SERVICE);
        }
    }

    public int start() throws LocationException {
        // start thread
        (new Thread(this)).start();
        
        return LocationProvider._STARTING;
    }

    public void stop() throws LocationException {
        if (go) {
            go = false;
            if (connection != null) {
                try {
                    connection.close(); // TODO null check?
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        if (thread != null) {
            try {
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
        Desktop.timer.schedule(watcher = new TimerTask() {
            public void run() {
                boolean notify = false;
                synchronized (this) {
                    if (System.currentTimeMillis() > (timestamp + WATCHER_PERIOD)) {
                        if (lastState != LocationProvider.TEMPORARILY_UNAVAILABLE) {
                            lastState = LocationProvider.TEMPORARILY_UNAVAILABLE;
                            notify = true;
                        }
                    }
                }
                if (notify) {
                    notifyListener(lastState);
                }
            }
        }, WATCHER_PERIOD, WATCHER_PERIOD); // delay = period = 15 sec
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
        InputStream in = null;

        // open connection
        /*StreamConnection */connection = (StreamConnection) Connector.open(url, Connector.READ);

        try {
            // open stream for reading
            in = new BufferedInputStream(connection.openInputStream(), BUFFER_SIZE);

            // read NMEA until error or stop request
            for (; go ;) {

                Location location = null;

                // get next location
                try {
                    location = nextLocation(in);
                /*} catch (AssertionFailedException e) { // never happens, see nextLocation(...)

                    // warn
                    Desktop.showWarning(e.getMessage(), null, null);

                    // ignore
                    continue;

                } */
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
                        timestamp = System.currentTimeMillis();
                    } else {
                        if (lastState != LocationProvider.TEMPORARILY_UNAVAILABLE) {
                            lastState = LocationProvider.TEMPORARILY_UNAVAILABLE;
                            stateChange = true;
                        }
                    }
                }

                // stateChange about state, if necessary
                if (stateChange) {
                    notifyListener(lastState);
                }

                // send new location
                notifyListener(location);

            } // for (; go ;)

        } finally {

            // close anyway
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
            }

            // close anyway
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
