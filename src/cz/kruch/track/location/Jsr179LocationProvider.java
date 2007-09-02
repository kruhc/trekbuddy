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

import cz.kruch.track.configuration.Config;
import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.ui.Desktop;
import cz.kruch.j2se.io.BufferedOutputStream;
import api.location.LocationException;
import api.location.QualifiedCoordinates;
import api.location.Location;
import api.location.LocationProvider;
import api.file.File;

import javax.microedition.io.Connector;
import java.io.OutputStream;
import java.io.IOException;

/**
 * Internal (JSR-179) provider implementation.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class Jsr179LocationProvider
        extends api.location.LocationProvider
        implements javax.microedition.location.LocationListener, Runnable {

    private javax.microedition.location.LocationProvider impl;

    private volatile Thread thread;
    private volatile boolean go;
    
    private File nmeaFile;
    private OutputStream nmeaWriter;
    
    public Jsr179LocationProvider() {
        super(Config.LOCATION_PROVIDER_JSR179);
    }

    public Object getImpl() {
        return impl;
    }

    public int start() throws LocationException {
        try {
            // prepare criteria
            CharArrayTokenizer tokenizer = new CharArrayTokenizer();
            tokenizer.init(Config.getLocationTimings(), false);
            final int interval = tokenizer.nextInt();
            final int timeout = tokenizer.nextInt();
            final int maxage = tokenizer.nextInt();
            tokenizer.dispose();
            javax.microedition.location.Criteria criteria = new javax.microedition.location.Criteria();

            // common criteria
            criteria.setAltitudeRequired(true);
            criteria.setSpeedAndCourseRequired(true);

            // adjust criteria for current device
//#ifdef __A780__
            if (cz.kruch.track.TrackingMIDlet.a780) {
                /* from bikeator */
                criteria.setPreferredPowerConsumption(javax.microedition.location.Criteria.POWER_USAGE_HIGH);
            }
//#endif

            // init provider
            impl = javax.microedition.location.LocationProvider.getInstance(criteria);
            if (impl == null) {
                impl = javax.microedition.location.LocationProvider.getInstance(null);
                setThrowable(new LocationException("Default criteria used"));
            }
            if (impl == null) {
                throw new LocationException("No provider instance");
            }

            // set listener
            impl.setLocationListener(this, interval, timeout, maxage);

        } catch (LocationException e) {
            throw e;
        } catch (Exception e) {
            throw new LocationException(e);
        }

        // start service thread
        (new Thread(this)).start();

        lastState = _STARTING;
        notifyListener(_STARTING); // trick to start GPX tracklog

        return lastState;
    }

    public void stop() throws LocationException {
        // remove listener and gc-free native provider
        impl.setLocationListener(null, -1, -1, -1);
        impl = null;

        // shutdown service thread
        synchronized (this) {
            go = false;
            notify();
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

    public void run() {
        // let's roll
        go = true;
        thread = Thread.currentThread();

        try {
            // start NMEA log
            startNmeaLog();

            // wait for end (kinda stupid variant of gps() from Serial provider ;-) )
            synchronized (this) {
                while (go) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
            }

        } catch (Throwable t) {

            // record
            setThrowable(t);

        } finally {

            // be ready for restart
            go = false;

            // stop NMEA tracklog
            stopNmeaLog();
        }
    }

    public void setLocationListener(api.location.LocationListener listener, int interval, int timeout, int maxAge) {
        if (listener == null) {
            impl.setLocationListener(null, interval, timeout, maxAge);
        } else {
            setListener(listener);
        }
    }

    private void startNmeaLog() {
        // use NMEA tracklog
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
                    nmeaWriter = new BufferedOutputStream(nmeaFile.openOutputStream(), 1024);

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

        // close writer
        if (nmeaWriter != null) {
            try {
                nmeaWriter.close();
            } catch (IOException e) {
                // ignore
            }
            nmeaWriter = null;
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

    private static final String APPLICATION_X_JSR179_LOCATION_NMEA = "application/X-jsr179-location-nmea";

    public void locationUpdated(javax.microedition.location.LocationProvider p,
                                javax.microedition.location.Location l) {
        // valid location?
        if (l.isValid()) {

            // signal state change
            if (lastState != LocationProvider.AVAILABLE) {
                lastState = LocationProvider.AVAILABLE;
                notifyListener(lastState);
            }

            // vars
            javax.microedition.location.QualifiedCoordinates xc = l.getQualifiedCoordinates();
            float spd = l.getSpeed();
            float alt = xc.getAltitude();
            float course = l.getCourse();
            float accuracy = xc.getHorizontalAccuracy();

            if (Float.isNaN(spd)) {
                spd = -1F;
            }
            if (Float.isNaN(alt)) {
                alt = -1F;
            } else if (cz.kruch.track.TrackingMIDlet.sxg75) {
                alt -= 540;
            }
            if (Float.isNaN(course)) {
                course = -1F;
            }
            if (Float.isNaN(accuracy)) {
                accuracy = -1F;
            }

            // create up-to-date location
            QualifiedCoordinates qc = QualifiedCoordinates.newInstance(xc.getLatitude(),
                                                                       xc.getLongitude(),
                                                                       alt);
            qc.setAccuracy(accuracy);
            Location location = Location.newInstance(qc, l.getTimestamp(), 1);
            location.setCourse(course);
            location.setSpeed(spd);

            // notify
            notifyListener(location);

        } else {

            // signal state change
            if (lastState != LocationProvider.TEMPORARILY_UNAVAILABLE) {
                lastState = LocationProvider.TEMPORARILY_UNAVAILABLE;
                notifyListener(lastState);
            }
        }

        // NMEA logging
        if (nmeaWriter != null) {
            String extra = l.getExtraInfo(APPLICATION_X_JSR179_LOCATION_NMEA);
            if (extra != null) {
                try {
                    if (extra.indexOf("\n$GP") > -1) {
                        nmeaWriter.write(extra.getBytes());
                    } else {
                        byte[] bytes = extra.getBytes();
                        for (int N = bytes.length, i = 0; i < N; i++) {
                            final byte b = bytes[i];
                            if (b == '$' && i != 0) {
                                nmeaWriter.write('\r');
                                nmeaWriter.write('\n');
                            }
                            nmeaWriter.write(b);
                        }
                        nmeaWriter.write('\r');
                        nmeaWriter.write('\n');
                    }
                } catch (Throwable t) {
                    setThrowable(t);
                }
            }
        }
    }

    public void providerStateChanged(javax.microedition.location.LocationProvider locationProvider, int i) {
        notifyListener(i);
    }
}
