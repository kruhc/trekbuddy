// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import api.location.LocationProvider;
import api.location.LocationListener;
import api.location.LocationException;

import javax.microedition.io.file.FileConnection;
import javax.microedition.io.Connector;
import java.io.InputStream;
import java.io.IOException;

import cz.kruch.j2se.io.BufferedInputStream;
import cz.kruch.track.util.NmeaParser;
import cz.kruch.track.util.Logger;
import cz.kruch.track.configuration.Config;

public class SimulatorLocationProvider extends StreamReadingLocationProvider implements Runnable {
    private static final Logger log = new Logger("Simulator");

    private String path;
    private int delay;

    private Thread thread;
    private boolean go;
    private FileConnection fc;
    private InputStream in;

    private volatile LocationListener listener;
    private int interval;
    private int timeout;
    private int maxAge;

    public SimulatorLocationProvider(String path, int delay) {
        super(Config.LOCATION_PROVIDER_SIMULATOR);
        this.path = path;
        this.delay = delay < 25 ? 25 : delay;
    }

    public void setLocationListener(LocationListener locationListener, int interval, int timeout, int maxAge) {
        this.listener = locationListener;
        this.interval = interval;
        this.timeout = timeout;
        this.maxAge = maxAge;
    }

    public Object getImpl() {
        return null;
    }

    public void start() throws LocationException {
        try {
            fc = (FileConnection) Connector.open(path, Connector.READ);
            in = new BufferedInputStream(fc.openInputStream());

            go = true;
            thread = new Thread(this);
            thread.start();

            log.debug("simulator started");

        } catch (IOException e) {
            throw new LocationException(e);
        }
    }

    public void stop() throws LocationException {
        go = false;
        try {
            thread.interrupt();
            thread.join();
        } catch (InterruptedException e) {
            log.warn("join interrupted: " + e.toString());
        }

        try {
            in.close();
            in = null;
            fc.close();
            fc = null;
        } catch (IOException e) {
            // ignore
        }

        log.info("simulator stopped");
    }

    public void run() {
        log.info("simulator task starting");

        // send current status
        if (listener != null) {
            listener.providerStateChanged(this, LocationProvider.AVAILABLE);
        }

        try {
            for (; go ;) {

                // read GGA
                String nmea = nextGGA(in);
                if (nmea == null) {
                    log.warn("end of file");

                    // send current status
                    listener.providerStateChanged(this, LocationProvider.OUT_OF_SERVICE);

                    break;
                }

                // send new location
                try {
                    listener.locationUpdated(this, NmeaParser.parse(nmea));
                } catch (Exception e) {
                    System.err.println("corrupted record: " + nmea + "\n" + e.toString());
                }

                // interval elapse
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                }
            }

        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                // probably stop request
            } else {
                log.error("simulator failed: " + e.toString());
            }
        }

        if (listener != null) {
            listener.providerStateChanged(this, LocationProvider.OUT_OF_SERVICE);
        }

        log.info("simulator task ended");
    }
}
