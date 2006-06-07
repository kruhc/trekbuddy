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

public class SimulatorLocationProvider extends LocationProvider implements Runnable {
    private String path;
    private Thread thread;
    private boolean go;
    private FileConnection fc;
    private InputStream in;

    private LocationListener locationListener;
    private int interval;
    private int timeout;
    private int maxAge;

    public SimulatorLocationProvider(String path) {
        this.path = path;
    }

    public void setLocationListener(LocationListener locationListener, int interval, int timeout, int maxAge) {
        this.locationListener = locationListener;
        this.interval = interval;
        this.timeout = timeout;
        this.maxAge = maxAge;
    }

    public void start() throws IOException {
        fc = (FileConnection) Connector.open(path, Connector.READ);
        in = new BufferedInputStream(fc.openInputStream());

        go = true;
        thread = new Thread(this);
        thread.start();
        System.out.println("simulator started");
    }

    public void stop() throws IOException {
        go = false;
        try {
            thread.interrupt();
            thread.join();
        } catch (InterruptedException e) {
        }

        in.close();
        fc.close();
        System.out.println("simulator stopped");
    }

    public void run() {
        System.out.println("simulator task starting");

        final LocationListener l = locationListener;
        l.providerStateChanged(this, LocationProvider.AVAILABLE);

        for (; go ;) {
            // if any listener
            if (l != null) {
//                System.out.println("simulator sending location update");
                try {
                    String nmea = nextGGA();
//                    System.out.println("NMEA: " + nmea);
                    if (nmea == null) {
                        System.out.println("end of file");
                        l.providerStateChanged(this, LocationProvider.TEMPORARILY_UNAVAILABLE);
                        break;
                    }
                    try {
                        l.locationUpdated(this, NmeaParser.parse(nmea));
                    } catch (Exception e) {
                        System.err.println("corrupted record: " + nmea + "\n" + e.toString());
                    }
                } catch (Exception e) {
                    System.out.println("ERROR! " + e.toString());
                    e.printStackTrace();
                    l.providerStateChanged(this, LocationProvider.OUT_OF_SERVICE);
                    break;
                }
            }

            // interval elapse
            try {
                Thread.sleep(/*interval * 1000*/ 250);
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
