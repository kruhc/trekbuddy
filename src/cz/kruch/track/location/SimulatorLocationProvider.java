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
import cz.kruch.track.configuration.Config;

public class SimulatorLocationProvider extends LocationProvider implements Runnable {
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
        return this;
    }

    public void start() throws LocationException {
        try {
            fc = (FileConnection) Connector.open(path, Connector.READ);
            in = new BufferedInputStream(fc.openInputStream());

            go = true;
            thread = new Thread(this);
            thread.start();

            System.out.println("simulator started");
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
        }

        try {
            in.close();
            in = null;
            fc.close();
            fc = null;
        } catch (IOException e) {
            // ignore
        }

        System.out.println("simulator stopped");
    }

    public void run() {
        System.out.println("simulator task starting");

        boolean announced = false;

        for (; go ;) {
            // if any listener
            if (listener != null) {

                if (!announced) {
                    announced = true;
                    listener.providerStateChanged(this, LocationProvider.AVAILABLE);
                }

                try {
                    String nmea = nextGGA();
                    if (nmea == null) {
                        System.out.println("end of file");
                        listener.providerStateChanged(this, LocationProvider.TEMPORARILY_UNAVAILABLE);
                        break;
                    }
                    try {
                        listener.locationUpdated(this, NmeaParser.parse(nmea));
                    } catch (Exception e) {
                        System.err.println("corrupted record: " + nmea + "\n" + e.toString());
                    }
                } catch (Exception e) {
                    System.out.println("ERROR! " + e.toString());
                    e.printStackTrace();
                    listener.providerStateChanged(this, LocationProvider.OUT_OF_SERVICE);
                    break;
                }
            }

            // interval elapse
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
            }
        }

        System.out.println("simulator task ended");
    }

    private String nextGGA() throws IOException {
        String line = readLine();
        while (line != null) {
            if (line.startsWith("$GPGGA")) {
                break;
            }
            line = readLine();
        }

        return line;
    }

    private String readLine() throws IOException {
        StringBuffer sb = new StringBuffer();
        boolean nl = false;
        int c = in.read();
        while (c > -1) {
            char ch = (char) c;
            sb.append(ch);
            nl = ch == '\n';
            if (nl) break;
            c = in.read();
        }

        if (nl) {
            return sb.toString();
        }

        return null;
    }
}
