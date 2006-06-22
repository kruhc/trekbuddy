// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import api.location.LocationProvider;
import api.location.LocationListener;
import api.location.LocationException;

import javax.microedition.io.Connector;
import javax.microedition.lcdui.Display;
import java.io.InputStream;
import java.io.IOException;

import cz.kruch.track.util.NmeaParser;
import cz.kruch.track.util.Logger;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.ui.FileBrowser;
import cz.kruch.track.event.Callback;
import cz.kruch.j2se.io.BufferedInputStream;

public class SimulatorLocationProvider extends StreamReadingLocationProvider implements Runnable {
    private static final Logger log = new Logger("Simulator");

    private Display display;

    private Thread thread;
    private boolean go;

    private String url;
    private int delay;

    private int interval;
    private int timeout;
    private int maxAge;

    public SimulatorLocationProvider(Display display) {
        super(Config.LOCATION_PROVIDER_SIMULATOR);
        this.display = display;
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

    public void start() throws LocationException {
        go = true;
        thread = new Thread(SimulatorLocationProvider.this);
        thread.start();
    }

    public void stop() throws LocationException {
        if (log.isEnabled()) log.debug("stop request");
        go = false;
        try {
            thread.interrupt();
            thread.join();
        } catch (InterruptedException e) {
        }
    }

    public void run() {
        if (log.isEnabled()) log.info("simulator task starting");

        InputStream in = null;

        try {
            // select tracklog
            Selector selector = new Selector();
            selector.go();

            // notify
            notifyListener(LocationProvider.AVAILABLE);

            // open file and stream
            in = new BufferedInputStream(Connector.openInputStream(url));

            for (; go ;) {

                // read GGA
                String nmea = nextGGA(in);
                if (nmea == null) {
                    if (log.isEnabled()) log.warn("end of file");

                    // send current status
                    notifyListener(LocationProvider.OUT_OF_SERVICE);

                    break;
                }

                // send new location
                try {
                    notifyListener(NmeaParser.parse(nmea));
                } catch (Exception e) {
                    if (log.isEnabled()) log.warn("corrupted record: " + nmea + "\n" + e.toString());
                }

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
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }

            notifyListener(LocationProvider.OUT_OF_SERVICE);
        }

        if (log.isEnabled()) log.info("simulator task ended");
    }

    private class Selector implements Callback {
        private boolean finished = false;

        public void go() {
            (new FileBrowser("SelectTracklog", display, this)).show();

            while (!finished) {
                Thread.yield();
            }
        }

        public void invoke(Object result, Throwable throwable) {
            if (log.isEnabled()) log.debug("file browser notification - result:'" + result + "';throwable:" + throwable);

            url = (String) result;
            finished = true;
        }
    }
}
