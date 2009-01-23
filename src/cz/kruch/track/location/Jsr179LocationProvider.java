// @LICENSE@

package cz.kruch.track.location;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.util.NmeaParser;
import cz.kruch.track.Resources;
import api.location.LocationException;
import api.location.QualifiedCoordinates;
import api.location.Location;
import api.location.LocationProvider;

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

    private static final byte[] CRLF = { '\r', '\n' };

    private javax.microedition.location.LocationProvider impl;
    private int interval, timeout, maxage;

    private final char[] raw;
    private int extraSat, extraFix;

    public Jsr179LocationProvider() {
        super("Internal");
        this.raw = new char[NmeaParser.MAX_SENTENCE_LENGTH];
        this.extraSat = this.extraFix = -1;
    }

    public int start() throws LocationException {
        try {
            // prepare criteria
            CharArrayTokenizer tokenizer = new CharArrayTokenizer();
            tokenizer.init(Config.getLocationTimings(Config.LOCATION_PROVIDER_JSR179), false);
            interval = tokenizer.nextInt();
            timeout = tokenizer.nextInt();
            maxage = tokenizer.nextInt();
            tokenizer = null; // gc hint

            // common criteria
            final javax.microedition.location.Criteria criteria = new javax.microedition.location.Criteria();
            criteria.setAltitudeRequired(true);
            criteria.setSpeedAndCourseRequired(true);

            // adjust criteria for current device
//#ifdef __ALL__
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
                throw new LocationException(Resources.getString(Resources.DESKTOP_MSG_NO_PROVIDER_INSTANCE));
            }

        } catch (LocationException e) {
            throw e;
        } catch (Exception e) {
            throw new LocationException(e);
        }

        // start service thread
        (new Thread(this)).start();

        return LocationProvider._STARTING;
    }

    public void run() {
        // statistics
        restarts++;

        // let's roll
        baby();

        try {
            // set listener
            impl.setLocationListener(this, interval, timeout, maxage);

            // status
            setStatus("running");

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

            // remove listener and gc-free native provider
            try {
                impl.setLocationListener(null, -1, -1, -1);
            } catch (Exception e) {
                // ignore
            }
            impl = null;

            // almost dead
            zombie();
        }
    }

    private static final String APPLICATION_X_JSR179_LOCATION_NMEA = "application/X-jsr179-location-nmea";

    public void locationUpdated(javax.microedition.location.LocationProvider p,
                                javax.microedition.location.Location l) {

        // get extra info
        final String extra = l.getExtraInfo(APPLICATION_X_JSR179_LOCATION_NMEA);

        // valid location?
        if (l.isValid()) {

            // enhance with raw NMEA
            int sat = -1;
            if (extra != null) {
                extraSat = extraFix = -1;
                try {
                    parseNmea(raw, extra);
                    if (extraSat > 0) {
                        sat = extraSat;
                    } else {
                        if (NmeaParser.sata != 0) {
                            sat = NmeaParser.sata;
                        }
                    }
                } catch (Exception e) {
                    setThrowable(e);
                }
            }

            // vars
            javax.microedition.location.QualifiedCoordinates xc = l.getQualifiedCoordinates();
            final float spd = l.getSpeed();
            /*final*/ float alt = xc.getAltitude();
            final float course = l.getCourse();
            final float accuracy = xc.getHorizontalAccuracy();

            if (Float.isNaN(alt)) {
                alt = Float.NaN;
            } else if (cz.kruch.track.TrackingMIDlet.brew) {
                alt -= 540;
            }

            // create up-to-date location
            QualifiedCoordinates qc = QualifiedCoordinates.newInstance(xc.getLatitude(),
                                                                       xc.getLongitude(),
                                                                       alt);
            qc.setHorizontalAccuracy(accuracy);
            final Location location = Location.newInstance(qc, l.getTimestamp(), 1, sat);
            location.setCourse(course);
            location.setSpeed(spd);
            location.setFix3d(extraFix == 3);

            // signal state change
            if (updateLastState(AVAILABLE)) {
                notifyListener(AVAILABLE);
            }

            // notify
            notifyListener(location);

        } else {

            // signal state change
            if (updateLastState(TEMPORARILY_UNAVAILABLE)) {
                notifyListener(TEMPORARILY_UNAVAILABLE);
            }
        }

        // NMEA logging
        if (extra != null && observer != null) {
            try {
                logNmea(observer, extra);
            } catch (Exception e) {
                setThrowable(e);
            }
        }
    }

    public void providerStateChanged(javax.microedition.location.LocationProvider locationProvider, int i) {
        if (isGo()) {
            notifyListener(i);
        }
    }

    private void parseNmea(final char[] line, final String extra) throws Exception {
        final int length = extra.length();
        int start = 0;
        int idx = extra.indexOf("$GP");
        while (idx != -1) {
            if (idx != 0) {
                if (idx - start < NmeaParser.MAX_SENTENCE_LENGTH) {
                    extra.getChars(start, idx, line, 0);
                    final NmeaParser.Record rec = NmeaParser.parse(line, idx - start);
                    switch (rec.type) {
                        case NmeaParser.HEADER_GGA: {
                            extraSat = rec.sat;
                        } break;
                        case NmeaParser.HEADER_GSA: {
                            extraFix = rec.fix;
                        } break;
                    }
                }
            }
            start = idx;
            if (start < length) {
                idx = extra.indexOf("$GP", start + 3);
                if (idx == -1) {
                    idx = length;
                }
            } else {
                break;
            }
        }
    }

    private static void logNmea(final OutputStream out,
                                final String extra) throws IOException {
        final byte[] bytes = extra.getBytes();
        final int N = bytes.length;
        int offset = 0;
        boolean crlf = false;
        for (int i = 0; i < N; i++) {
            final byte b = bytes[i];
            switch (b) {
                case '$': {
                    if (i != 0) {
                        out.write(bytes, offset, i - offset);
                        offset = i;
                        if (!crlf) {
                            out.write(CRLF);
                        } else {
                            crlf = false;
                        }
                    }
                } break;
                case '\r':
                case '\n': {
                    crlf = true;
                } break;
            }
        }
        if (offset < bytes.length) { // always true
            out.write(bytes, offset, bytes.length - offset);
            if (!crlf)  {
                out.write(CRLF);
            }
        }
    }
}
