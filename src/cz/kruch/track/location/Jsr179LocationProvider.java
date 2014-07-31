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
 * @author kruhc@seznam.cz
 */
public final class Jsr179LocationProvider
        extends api.location.LocationProvider
        implements javax.microedition.location.LocationListener, Runnable {

    private static final byte[] CRLF = { '\r', '\n' };

    private javax.microedition.location.LocationProvider impl;
    private int interval, timeout, maxage;

    private final char[] raw;
    private int extraSat, extraFix, extraFixQuality;

    public Jsr179LocationProvider() {
        super("Internal");
        this.raw = new char[NmeaParser.MAX_SENTENCE_LENGTH];
//#ifdef __RIM50__
        bbStatus = bbError = 0;
//#endif
    }

    public int start() throws LocationException {
        try {
            // get listener params
            final CharArrayTokenizer tokenizer = new CharArrayTokenizer();
            tokenizer.init(Config.getLocationTimings(Config.LOCATION_PROVIDER_JSR179),
                           CharArrayTokenizer.DEFAULT_DELIMS, false);
            interval = tokenizer.nextInt();
            timeout = tokenizer.nextInt();
            maxage = tokenizer.nextInt();
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
            // common criteria
//#ifdef __RIM50__
            net.rim.device.api.gps.BlackBerryCriteria criteria = new net.rim.device.api.gps.BlackBerryCriteria();
            criteria.setMode(Config.assistedGps ? net.rim.device.api.gps.GPSInfo.GPS_MODE_ASSIST : net.rim.device.api.gps.GPSInfo.GPS_MODE_AUTONOMOUS); // use assisted or autonomous for first fix
            criteria.setSubsequentMode(net.rim.device.api.gps.GPSInfo.GPS_MODE_AUTONOMOUS); // then just use autonomous
            criteria.setSatelliteInfoRequired(true, false);
//#else
            javax.microedition.location.Criteria criteria = new javax.microedition.location.Criteria();
            criteria.setCostAllowed(Config.assistedGps);
            criteria.setPreferredPowerConsumption(Config.powerUsage);
//#endif
            criteria.setAltitudeRequired(true); /* may delay getting valid location? */
            criteria.setSpeedAndCourseRequired(true);

            // init provider
            impl = javax.microedition.location.LocationProvider.getInstance(criteria);
            if (impl == null) {
                impl = javax.microedition.location.LocationProvider.getInstance(null);
                criteria = null;
                setThrowable(new LocationException("Default criteria used"));
            }
            if (impl == null) {
                throw new LocationException(Resources.getString(Resources.DESKTOP_MSG_NO_PROVIDER_INSTANCE));
            }

            // set listener
            impl.setLocationListener(this, interval, timeout, maxage);

            // status
            setStatus("running");

            // wait for end
            synchronized (this) {
                while (isGo()) {
                    try {
//#ifndef __RIM50__
                        wait();
//#else
                        /*
                         * With (some) Blackberries, provider remains in AVAILABLE state
                         * but stops giving new locations when GPS view goes bad
                         * (when you enter building etc).
                         * The following code helps in these two possible scenarios:
                         * a) if you restore good GPS view within 1 min, locations start coming
                         * b) if GPS view is still bad after 1 min, the provider changes
                         *    its state to UNAVAILABLE (and lastGood is reseted to 0 - see providerStateChanged,
                         *    and thus there will be no more reset attemps.
                         * 2014-01-14: also see http://supportforums.blackberry.com/t5/Java-Development/Location-APIs-Start-to-finish/ta-p/571949
                         */
                        wait(15 * 1000);
                        if (lastGood > 0 && (System.currentTimeMillis() - lastGood) > (60 * 1000)) {
                            lastGood = 0;
                            impl.setLocationListener(null, -1, -1, -1);
                            impl.reset();
                            impl = null;
                            impl = javax.microedition.location.LocationProvider.getInstance(criteria);
                            impl.setLocationListener(this, interval, timeout, maxage);
                            restarts++;
                        }
//#endif
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
                impl.reset();
            } catch (Exception e) {
                // ignore
            }
            impl = null;
            
            // workaround for impl bug
//#ifdef __ALL__
            System.gc(); // unconditional!!!
//#endif            

            // almost dead
            zombie();
        }
    }

    private static final String APPLICATION_X_JSR179_LOCATION_NMEA = "application/X-jsr179-location-nmea";
    private static final String APPLICATION_X_JAVA_LOCATION_NMEA   = "application/X-java-location-nmea";

//#ifdef __RIM50__    
    public static int bbStatus, bbError;
    private volatile long lastGood;
//#endif

    public void locationUpdated(javax.microedition.location.LocationProvider p,
                                javax.microedition.location.Location l) {
//#ifdef __RIM50__

        // use extended location info
        final net.rim.device.api.gps.BlackBerryLocation bbl = (net.rim.device.api.gps.BlackBerryLocation) l;
        bbError = bbl.getError();
        bbStatus = bbl.getStatus();

        // continue flag
        boolean hasSomething = false;

        // get satellites info
        switch (bbStatus) {
//            case net.rim.device.api.gps.BlackBerryLocation.GPS_FIX_UNAVAILABLE:
            case net.rim.device.api.gps.BlackBerryLocation.GPS_FIX_PARTIAL:
            case net.rim.device.api.gps.BlackBerryLocation.GPS_FIX_COMPLETE:
                int sat = 0;
                try {
                    for (java.util.Enumeration e = bbl.getSatelliteInfo(); e.hasMoreElements(); ) {
                        final net.rim.device.api.gps.SatelliteInfo si = (net.rim.device.api.gps.SatelliteInfo) e.nextElement();
                        if (sat < NmeaParser.MAX_SATS && si.getId() > 0) { // si.isvalid() seems to strict ;-)
                            NmeaParser.prns[sat] = (byte) si.getId();
                            NmeaParser.snrs[sat] = NmeaParser.normalizeSnr(si.getSignalQuality());
                        }
                        sat++;
                    }
                } catch (NullPointerException e) {
                    // ignore
                }
                NmeaParser.satv = bbl.getSatelliteCount();
                NmeaParser.sata = sat;
                NmeaParser.resetPrnSnr();
                hasSomething = true;
            break;
        }

        // let's assume provider is in AVAILABLE state
        /*
         * 2014-01-14: can be also TEMPORARILY_UNAVAILABLE and fix PARTIAL
         * (only sat info), typically after cold start (tested on 9700)
         */
        lastGood = System.currentTimeMillis();

        // if we have nothing, just quit
        if (!hasSomething) {
            return;
        }

//#endif

        // get extra info
        String extra = l.getExtraInfo(APPLICATION_X_JSR179_LOCATION_NMEA);
        if (extra == null) { // try fallback to older implementation
            extra = l.getExtraInfo(APPLICATION_X_JAVA_LOCATION_NMEA);
        }

        // result
        final Location location;

        // fixable vars
        long timestamp = l.getTimestamp();
//#ifdef __RIM__
        // nothing to do?
//#else
        if (timestamp == 0 && Config.timeFix) { // TODO what devices?
            timestamp = System.currentTimeMillis();
        }
//#endif

        // valid location?
        if (l.isValid()) {

            // fixable vars
            final javax.microedition.location.QualifiedCoordinates xc = l.getQualifiedCoordinates();
            float alt = xc.getAltitude() + Config.altCorrection;
//#ifdef __RIM__
            if (Config.negativeAltFix) {
                alt *= -1;
            }
//#endif
            float speed = l.getSpeed();

            // enhance with raw NMEA
            int sat = 0;
            if (extra != null) {
                extraSat = extraFix = extraFixQuality = 0;
                try {
                    parseNmea(extra);
                    if (extraSat > 0) {
                        sat = extraSat;
/*
                    } else {
                        if (NmeaParser.sata != 0) {
                            sat = NmeaParser.sata;
                        }
*/
                    }
                } catch (Exception e) {
                    setThrowable(e);
                    errors++;
                }
            }

//#ifdef __ALL__

            // Sonim hacks
            if (cz.kruch.track.TrackingMIDlet.sonim) {
                speed /= 2;
                if (sat == 0) {
                    final String ss = System.getProperty("gps.satellites");
                    if (ss != null) {
                        try {
                            sat = Integer.parseInt(ss);
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                }
                if (extraFix == 0) {
                    final String sf = System.getProperty("gps.fix");
                    if (sf != null) {
                        try {
                            extraFix = Integer.parseInt(sf);
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                }
            }

//#endif

            // create up-to-date coordinates
            final QualifiedCoordinates qc = QualifiedCoordinates.newInstance(xc.getLatitude(),
                                                                             xc.getLongitude(),
                                                                             alt,
                                                                             xc.getHorizontalAccuracy(),
                                                                             xc.getVerticalAccuracy());

            // create location
//#ifdef __RIM50__
            final int fix = bbStatus == net.rim.device.api.gps.BlackBerryLocation.GPS_FIX_COMPLETE ? 1 : 0;
//#else
            final int fix = 1;
//#endif
            location = Location.newInstance(qc, timestamp, fix, sat);
            location.setCourse(l.getCourse());
            location.setSpeed(speed);
            location.updateFix(extraFix);
            location.updateFixQuality(extraFixQuality);

        } else {

            // create invalid location
            location = Location.newInstance(QualifiedCoordinates.INVALID, timestamp, 0);

        }

        // notify listener
        notifyListener2(location);

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
//#ifdef __RIM50__
            // it is normal that there is no new location when provider is out
            if (i != javax.microedition.location.LocationProvider.AVAILABLE) {
                lastGood = 0;
            }
//#endif
        }
    }

    private void parseNmea(final String extra) throws Exception {
        final char[] line = raw;
        final int length = extra.length();
        int start = 0;
        int idx = extra.indexOf("$GP");
        while (idx > -1) {
            if (idx != 0) {
                if (idx - start < NmeaParser.MAX_SENTENCE_LENGTH) {
                    extra.getChars(start, idx, line, 0);
//#ifdef __RIM__
                    if (true) { // CRC is stripped in NMEA on Blackberries
//#else
                    if (NmeaParser.validate(line, idx - start)) {
//#endif
                        final NmeaParser.Record rec = NmeaParser.parse(line, idx - start);
                        switch (rec.type) {
                            case NmeaParser.HEADER_GGA: {
                                extraSat = rec.sat;
                                extraFixQuality = rec.fix;
                            } break;
                            case NmeaParser.HEADER_GSA: {
                                extraFix = rec.fix;
                            } break;
                        }
                    } else {
                        if (NmeaParser.getType(line, idx - start) != -1) {
                            checksums++;
                        } else {
                            invalids++;
                        }
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
