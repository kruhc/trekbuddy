// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

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

public final class Jsr179LocationProvider
        extends api.location.LocationProvider
        implements javax.microedition.location.LocationListener {

    private javax.microedition.location.LocationProvider impl;
    private File nmeaFile;
    private OutputStream nmeaWriter;

/*
    private LocationListenerAdapter adapter;
*/

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

            // adjust criteria for current device
//#ifdef __A780__
            if (cz.kruch.track.TrackingMIDlet.a780) {
                /* from bikeator */
                criteria.setHorizontalAccuracy(javax.microedition.location.Criteria.NO_REQUIREMENT);
                criteria.setPreferredPowerConsumption(javax.microedition.location.Criteria.POWER_USAGE_HIGH);
            }
            else
//#endif            
            {
                criteria.setAltitudeRequired(true);
                criteria.setSpeedAndCourseRequired(true);
            }

           // start NMEA log
            startNmeaLog();

            // init provider
            impl = javax.microedition.location.LocationProvider.getInstance(criteria);
            if (impl == null) {
                impl = javax.microedition.location.LocationProvider.getInstance(null);
                setThrowable(new LocationException("Default criteria used"));
            }
            if (impl == null) {
                throw new LocationException("No provider instance");
            }
            impl.setLocationListener(/*adapter*/this, interval, timeout, maxage);

        } catch (LocationException e) {
            throw e;
        } catch (Exception e) {
            throw new LocationException(e);
        }

        lastState = _STARTING;
        notifyListener(_STARTING); // trick to start GPX tracklog

        return lastState;
    }

    public void stop() throws LocationException {
        // remove listener and gc-free native provider
        impl.setLocationListener(null, -1, -1, -1);
        impl = null;

        // stop NMEA log
        stopNmeaLog();
    }

    public void setLocationListener(api.location.LocationListener listener, int interval, int timeout, int maxAge) {
        if (listener == null) {
            impl.setLocationListener(null, interval, timeout, maxAge);
        } else {
/*
            adapter = new LocationListenerAdapter(listener);
*/
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

/*
    private final class LocationListenerAdapter implements javax.microedition.location.LocationListener {
*/
        private static final String APPLICATION_X_JSR179_LOCATION_NMEA = "application/X-jsr179-location-nmea";

/*
        public LocationListenerAdapter(api.location.LocationListener listener) {
            setListener(listener);
        }
*/

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
/*
    }
*/
}
