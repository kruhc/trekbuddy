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
import java.util.Timer;
import java.util.TimerTask;

public class SerialLocationProvider extends StreamReadingLocationProvider implements Runnable {
    private static final int WATCHER_PERIOD = 15 * 1000;

    protected Thread thread;

    protected volatile String url;
    protected volatile boolean go;

    private Timer watcher;
    private long timestamp;

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

    public void run() {
        // start with last known?
        if (url == null) {
            if (this instanceof Jsr82LocationProvider) {
                url = Config.btServiceUrl;
            } else {
                url = Config.commUrl;
            }
        }

        // let's roll
        thread = Thread.currentThread();
        go = true;

        try {
            // notify
            notifyListener(LocationProvider._STARTING);

            // start watcher
            startWatcher();

            // start NMEA log
            startNmeaLog();

            // GPS
            gps();

        } catch (Exception e) {

            if (e instanceof InterruptedException) {
                // probably stop request
            } else {
                // record exception
                setException(e instanceof LocationException ? (LocationException) e : new LocationException(e));
            }

        } finally {

            // be ready for restart
            url = null;
            thread = null;

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
            try {
                thread.interrupt();
                thread.join();
            } catch (InterruptedException e) {
            }
            thread = null;
        }
    }

    public void setLocationListener(LocationListener listener, int interval, int timeout, int maxAge) {
        setListener(listener);
    }

    public Object getImpl() {
        return null;
    }

    private void startWatcher() {
        watcher = new Timer();
        watcher.schedule(new TimerTask() {
            public void run() {
                boolean notify = false;
                synchronized (this) {
                    if (System.currentTimeMillis() > (timestamp + WATCHER_PERIOD)) {
                        lastState = LocationProvider.TEMPORARILY_UNAVAILABLE;
                        notify = true;
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
        if (isTracklog() && Config.TRACKLOG_FORMAT_NMEA.equals(Config.tracklogFormat)) {
            if (nmeaFile == null) { // not yet started
                String path = Config.getFolderNmea() + GpxTracklog.dateToFileDate(System.currentTimeMillis()) + ".nmea";
                try {
                    nmeaFile = File.open(Connector.open(path, Connector.READ_WRITE));
                    if (!nmeaFile.exists()) {
                        nmeaFile.create();
                    }
                    nmeaObserver = new BufferedOutputStream(nmeaFile.openOutputStream(), 512);

                    // set stream 'observer'
                    setObserver(nmeaObserver);

/* fix
                // signal recording has started
                recordingCallback.invoke(new Integer(GpxTracklog.CODE_RECORDING_START), null);
*/

                } catch (Throwable t) {
                    Desktop.showError("Failed to start NMEA log.", t, null);
                }
            }
        }
    }

    public void stopNmeaLog() {

/*
        // signal recording is stopping
        recordingCallback.invoke(new Integer(GpxTracklog.CODE_RECORDING_STOP), null);
*/
        if (go) {
            return;
        }

        // clear stream 'observer'
        setObserver(null);

        if (nmeaObserver != null) {
            try {
                nmeaObserver.close();
            } catch (IOException e) {
                // ignore
            }
            nmeaObserver = null;
        }
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
        // open connection
        StreamConnection connection = (StreamConnection) Connector.open(url, Connector.READ);
        InputStream in = null;

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

                    // record exception
                    setException(new LocationException(e));

                    /*
                     * location is null, therefore the loop quits
                     */

                } catch (Exception e) {

                    // record exception
                    if (e instanceof InterruptedException) {
                        // probably stop request
                    } else {
                        // record exception
                        setException(e instanceof LocationException ? (LocationException) e : new LocationException(e));
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
                }
            }

            // close anyway
            try {
                connection.close();
            } catch (IOException e) {
            }
        }
    }
}
