// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import api.location.LocationProvider;
import api.location.LocationListener;
import api.location.LocationException;
import api.location.Location;

import javax.microedition.io.Connector;
import java.io.InputStream;
import java.io.IOException;

//#ifdef __LOG__
import cz.kruch.track.util.Logger;
//#endif
import cz.kruch.track.configuration.Config;
import cz.kruch.track.ui.FileBrowser;
import cz.kruch.track.ui.Desktop;
import cz.kruch.track.event.Callback;
import cz.kruch.track.AssertionFailedException;
import cz.kruch.j2se.io.BufferedInputStream;

public class SimulatorLocationProvider
        extends StreamReadingLocationProvider
        implements Runnable, Callback {
//#ifdef __LOG__
    private static final Logger log = new Logger("Simulator");
//#endif

    private Thread thread;
    private boolean go;
    private api.file.File file;
    private int delay;

    private int interval;
    private int timeout;
    private int maxAge;

    public SimulatorLocationProvider() {
        super(Config.LOCATION_PROVIDER_SIMULATOR);
        this.delay = Config.getSafeInstance().getSimulatorDelay();
        if (this.delay < 25) {
            this.delay = 25;
        }
    }

    public void setLocationListener(LocationListener locationListener, int interval, int timeout, int maxAge) {
        setListener(locationListener);
        this.interval = interval;
        this.timeout = timeout;
        this.maxAge = maxAge;
    }

    public Object getImpl() {
        return null;
    }

    public int start() throws LocationException {
        (new FileBrowser("PlaybackSelection", this, Desktop.screen)).show();

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

    public void invoke(Object result, Throwable throwable) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("playback selection: " + result);
//#endif

        if (result != null) {
            go = true;
            file = (api.file.File) result;
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
            in = new BufferedInputStream(file.openInputStream(), BUFFER_SIZE);

            // notify
            notifyListener(LocationProvider.AVAILABLE);

            for (; go ;) {

                Location location = null;

                // get next location
                try {
                    location = nextLocation(in);
                } catch (AssertionFailedException e) {
                    Desktop.showError(e.getMessage(), null, null);
                } catch (Exception e) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.warn("Failed to get location.", e);
//#endif

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

                // send the location
                notifyListener(location);

                // interval elapse
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                }
            }

        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                // stop request
            } else {
                // record exception
                setException(e instanceof LocationException ? (LocationException) e : new LocationException(e));
            }
        } finally {
            // close the stream
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }

            // close file connection
            try {
                file.close();
            } catch (IOException e) {
            }

            // notify
            notifyListener(LocationProvider.OUT_OF_SERVICE);
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.info("simulator task ended");
//#endif
    }
}
