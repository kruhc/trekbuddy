// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import api.location.LocationProvider;
import api.location.LocationListener;
import api.location.LocationException;
import api.location.Location;
import api.location.QualifiedCoordinates;

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

    public int start() throws LocationException {
        (new FileBrowser("PlaybackSelection", display, new Callback() {
            public void invoke(Object result, Throwable throwable) {
                if (log.isEnabled()) log.debug("playback selection: " + result);
                if (result != null) {
                    go = true;
                    url = (String) result;
                    thread = new Thread(SimulatorLocationProvider.this);
                    thread.start();
                } else {
                    notifyListener(LocationProvider.OUT_OF_SERVICE);
                }
            }
        })).show();

        return LocationProvider.TEMPORARILY_UNAVAILABLE;
    }

    public void stop() throws LocationException {
        if (log.isEnabled()) log.debug("stop request");
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

    public void run() {
        if (log.isEnabled()) log.info("simulator task starting; url " + url);

        InputStream in = null;

        try {
            // open file and stream
            in = new BufferedInputStream(Connector.openInputStream(url));

            // notify
            notifyListener(LocationProvider.AVAILABLE);

            for (; go ;) {

                // read GGA
                String ggaSentence = nextSentence(in, HEADER_GGA);
                if (ggaSentence == null) {
                    if (log.isEnabled()) log.debug("end-of-file");
                    break; // end-of-file
                }

                // read RMC
                String rmcSentence = nextSentence(in, HEADER_RMC);
                if (rmcSentence == null) {
                    if (log.isEnabled()) log.debug("end-of-file");
                    break; // end-of-file
                }

                // parse GGA
                NmeaParser.Record rec = null;
                try {
                    rec = NmeaParser.parseGGA(ggaSentence);
                } catch (Exception e) {
                    if (log.isEnabled()) log.warn("corrupted record: " + ggaSentence + "\n" + e.toString());
                    continue;
                }

                // prepare instance of location
                QualifiedCoordinates coordinates = new QualifiedCoordinates(rec.lat, rec.lon, rec.altitude);
                Location location = new Location(coordinates, rec.timestamp, rec.fix, rec.sat);

                // update location with angle and speed, if possible
                try {
                    NmeaParser.Record rmc = NmeaParser.parseRMC(rmcSentence);
                    if (log.isEnabled()) log.debug("RMC data: " + rmc.speed + ";" + rmc.angle);
                    if (log.isEnabled()) log.debug("GGA timestamp: " + rec.timestamp + "; RMC timestamp: " + rmc.timestamp);
                    if (rmc.timestamp == rec.timestamp) {
                        location.setCourse(rmc.angle);
                        location.setSpeed(rmc.speed);
                    }
                } catch (LocationException e) {
                    if (log.isEnabled()) log.debug("corrupted RMC: " + e.toString());
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
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }

            // notify
            notifyListener(LocationProvider.OUT_OF_SERVICE);
        }

        if (log.isEnabled()) log.info("simulator task ended");
    }
}
