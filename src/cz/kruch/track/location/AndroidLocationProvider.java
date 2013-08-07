// @LICENSE@

package cz.kruch.track.location;

//#ifdef __ANDROID__

import api.location.LocationProvider;
import api.location.LocationException;
import api.location.QualifiedCoordinates;
import api.location.Location;
import cz.kruch.track.event.Callback;
import cz.kruch.track.util.NmeaParser;
import cz.kruch.track.configuration.Config;

import java.io.OutputStream;
import java.io.IOException;
import java.util.TimerTask;

/**
 * Android provider implementation.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */

public final class AndroidLocationProvider
        extends api.location.LocationProvider
        implements android.location.LocationListener,
                   android.location.GpsStatus.Listener,
                   Runnable {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("AndroidLocationProvider");
//#endif
    private static final String TAG = cz.kruch.track.TrackingMIDlet.APP_TITLE;
    private static final String KEY_SATELLITES = "satellites";
    private static final byte[] CRLF = { '\r', '\n' };

    private android.location.LocationManager manager;
    private android.location.GpsStatus gpsStatus;
    private android.os.Looper looper;
    private TimerTask watcher;
//#ifndef __BACKPORT__
    private cz.kruch.track.event.Callback nmeaer;
//#endif

    private volatile int sat, status;
    private volatile boolean hasNmea;

    private final char[] raw;
    private int extraSat, extraFix, extraFixQuality;

    public AndroidLocationProvider() {
        super("Internal");
        this.status = android.location.LocationProvider.OUT_OF_SERVICE;
        this.raw = new char[NmeaParser.MAX_SENTENCE_LENGTH];        
    }

    public int start() throws LocationException {
        try {
            manager = (android.location.LocationManager) cz.kruch.track.TrackingMIDlet.getActivity().getSystemService(android.content.Context.LOCATION_SERVICE);
            if (manager == null) {
                throw new LocationException("Location Service not found");
            }
            if (!manager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                throw new LocationException("GPS disabled");
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

        // reset
        extraSat = extraFix = extraFixQuality = 0;

        try {
            // create looper
            android.os.Looper.prepare();
            looper = android.os.Looper.myLooper();

            // add listeners
            manager.addGpsStatusListener(this);
//#ifndef __BACKPORT__
            try {
                nmeaer = (Callback) Class.forName("cz.kruch.track.location.AndroidNmeaListener").newInstance();
                nmeaer.invoke(new Integer(1), null, new Object[]{ this, manager });
            } catch (Exception e) {
                // ignore
            }
//#endif

            // get timing
            int interval = 0;
            try {
                interval = Integer.parseInt(Config.getLocationTimings(Config.LOCATION_PROVIDER_JSR179)) * 1000;
                if (interval <= 1000) {
                    interval = 0;
                }
            } catch (NumberFormatException e) {
                // ignore
            }
            android.util.Log.d(cz.kruch.track.TrackingMIDlet.APP_TITLE,
                               "location updates interval: " + (interval == 0 ? "max" : (interval + " ms")));

            // start location updates
            manager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER,
                                           interval, 0, this, looper);

            // start watcher
            cz.kruch.track.ui.Desktop.schedule(watcher = new TimerTask() {
                public void run() {
                    if (getLast() > 0 && System.currentTimeMillis() > (getLast() + 5000)) {
                        notifyListener2(Location.newInstance(QualifiedCoordinates.INVALID, 0, 0));
                    }
                }
            }, 15000, 1000);

            // status
            setStatus("running");

            // wait for end
            android.os.Looper.loop();

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("loop abandoned");
//#endif

        } catch (Throwable t) {

            // record
            setThrowable(t);

        } finally {

            // destroy watcher
            if (watcher != null) {
                watcher.cancel();
                watcher = null;
            }

            // remove listeners and cancel updated
            manager.removeGpsStatusListener(this);
//#ifndef __BACKPORT__
            try {
                nmeaer.invoke(new Integer(0), null, manager);
            } catch (Exception e) {
                // ignore
            }
//#endif
            manager.removeUpdates(this);
            manager = null;

            // almost dead
            zombie();
        }
    }

    public void stop() throws LocationException {
        // debug
        setStatus("requesting stop");

        // quit loop
        try {
            looper.quit();
        } catch (Exception e) { // NPE?
            // ignore
        } finally {
            looper = null;
        }
    }

    public void onLocationChanged(android.location.Location l) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("onLocationChanged");
//#endif

        // create up-to-date location
        final QualifiedCoordinates qc = QualifiedCoordinates.newInstance(l.getLatitude(),
                                                                         l.getLongitude());
        if (l.hasAltitude()) {
            qc.setAlt((float) l.getAltitude() + cz.kruch.track.configuration.Config.altCorrection);
        }
        if (l.hasAccuracy()) {
            qc.setHorizontalAccuracy(l.getAccuracy());
        }
        int sat = 0;
        final android.os.Bundle extras = l.getExtras();
        if (extras != null) {
            sat = extras.getInt(KEY_SATELLITES, 0);
        }
        if (sat == 0) { // fallback to value from onStatusChanged/onGpsStatusChanged/onNmeaReceived
            sat = this.sat;
        }
        final Location location = Location.newInstance(qc, l.getTime(), 1, sat);
        if (l.hasBearing()) {
            location.setCourse(l.getBearing());
        }
        if (l.hasSpeed()) {
            location.setSpeed(l.getSpeed());
        }
        location.updateFix(extraFix);
        location.updateFixQuality(extraFixQuality);

        // new location timestamp
        setLast(System.currentTimeMillis());

        // notify
        notifyListener2(location);

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("~onLocationUpdated");
//#endif
    }

    public void onStatusChanged(String provider, int status, android.os.Bundle extras) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("onStatusChanged");
//#endif

        if (isGo()) {
            switch (status) {
                case android.location.LocationProvider.OUT_OF_SERVICE:
                    status = OUT_OF_SERVICE;
                break;
                case android.location.LocationProvider.TEMPORARILY_UNAVAILABLE:
                    status = TEMPORARILY_UNAVAILABLE;
                break;
                case android.location.LocationProvider.AVAILABLE:
                    status = AVAILABLE;
                    if (!hasNmea) {
                        if (extras != null) {
                            final int cnt = extras.getInt(KEY_SATELLITES, 0);
                            if (cnt > 0) {
                                NmeaParser.satv = cnt;
                            }
                        }
                    }
                break;
            }
            if (this.status != status) {
                notifyListener(this.status = status);
            }
        }
    }

    public void onProviderEnabled(String string) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("onProviderEnabled");
//#endif
        // TODO
    }

    public void onProviderDisabled(String string) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("onProviderDisabled");
//#endif
        // TODO
    }

    public void onGpsStatusChanged(int event) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("onGpsStatusChanged");
//#endif

        if (hasNmea) {
            return;
        }

        switch (event) {
            case android.location.GpsStatus.GPS_EVENT_SATELLITE_STATUS: {
                gpsStatus = manager.getGpsStatus(gpsStatus);
                if (gpsStatus != null) {
                    /*
                     * only sats in view with SNR are available in GSV (Xperia/Android 4.0.4)
                     */
                    int cnt = 0, sat = 0;
                    for (android.location.GpsSatellite satellite : gpsStatus.getSatellites()) {
                        if (cnt < NmeaParser.MAX_SATS) {
                            NmeaParser.prns[cnt] = (byte) satellite.getPrn();
                            NmeaParser.snrs[cnt] = NmeaParser.normalizeSnr((int) satellite.getSnr());
                        }
                        cnt++;
                        if (satellite.usedInFix()) {
                            sat++;
                        }
                    }
                    if (cnt > 0) {
                        NmeaParser.satv = cnt;
                        NmeaParser.sata = cnt;
                        NmeaParser.resetPrnSnr();
                        this.sat = sat;
                    }
                }
            } break;
        }
    }

//#ifndef __BACKPORT__

    public void onNmeaReceived(long timestamp, String nmea) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("onNmeaReceived");
//#endif

        // set flag
        hasNmea = true;

        // enhance with raw NMEA
        int sat = 0;
        if (nmea != null) {
            try {
                parseNmea(nmea);
                if (extraSat > 0) {
                    sat = extraSat;
/*
                } else {
                    if (NmeaParser.sata != 0) {
                        sat = NmeaParser.sata;
                    }
*/
                }
                if (sat > 0) {
                    this.sat = sat;
                }
            } catch (Exception e) {
//#ifdef __LOG__
                android.util.Log.w(TAG, "NMEA parsing failed", e);
//#endif
                setThrowable(e);
                errors++;
            }
        }

        // NMEA logging
        if (nmea != null && observer != null) {
            try {
                logNmea(observer, nmea);
            } catch (Exception e) {
                setThrowable(e);
            }
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
                    if (NmeaParser.validate(line, idx - start)) {
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

//#endif /* !__BACKPORT__ */

}

//#endif
