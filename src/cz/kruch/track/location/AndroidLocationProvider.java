// @LICENSE@

package cz.kruch.track.location;

import api.location.LocationProvider;
import api.location.LocationException;
import api.location.QualifiedCoordinates;
import api.location.Location;

/**
 * Android provider implementation.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */

public final class AndroidLocationProvider
        extends api.location.LocationProvider
        implements android.location.LocationListener,
                   android.location.GpsStatus.Listener, Runnable {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("AndroidLocationProvider");
//#endif
    private static final String KEY_SATELLITES = "satellites";

    private android.location.LocationManager manager;
    private android.location.GpsStatus gpsStatus;
    private android.os.Looper looper;

    private volatile int sat, status;

    public AndroidLocationProvider() {
        super("Internal");
        this.sat = -1;
        this.status = -1;
    }

    public int start() throws LocationException {
        try {
            manager = (android.location.LocationManager) org.microemu.android.MicroEmulator.context.getSystemService(android.content.Context.LOCATION_SERVICE);
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

        try {
            // set listener
            android.os.Looper.prepare();
            looper = android.os.Looper.myLooper();
            manager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER,
                                           0, 0, this, looper);
            manager.addGpsStatusListener(this);

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

            // remove listener and gc-free native provider
            try {
                manager.removeGpsStatusListener(this);
                manager.removeUpdates(this);
            } catch (Exception e) {
                // ignore
            }
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
        int sat = -1;
        final android.os.Bundle extras = l.getExtras();
        if (extras != null) {
            sat = extras.getInt(KEY_SATELLITES, -1);
        }
        if (sat == -1 && this.sat != -1) { // fallback to value from onStatusChanged or onGpsStatusChanged 
            sat = this.sat;
        }
        final Location location = Location.newInstance(qc, l.getTime(), 1, sat);
        if (l.hasBearing()) {
            location.setCourse(l.getBearing());
        }
        if (l.hasSpeed()) {
            location.setSpeed(l.getSpeed());
        }

        // signal state change
        if (updateLastState(AVAILABLE)) {
            notifyListener(AVAILABLE);
        }

        // notify
        notifyListener(location);

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
                break;
            }
            if (extras != null) {
                final int cnt = extras.getInt(KEY_SATELLITES, -1);
                if (cnt > 0) {
                    this.sat = cnt;
                }
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
        switch (event) {
            case android.location.GpsStatus.GPS_EVENT_SATELLITE_STATUS: {
                gpsStatus = manager.getGpsStatus(gpsStatus);
                if (gpsStatus != null) {
                    int cnt = 0;
                    for (android.location.GpsSatellite satellite : gpsStatus.getSatellites()) {
                        cnt++;
                    }
                    if (cnt > 0) {
                        this.sat = cnt;
                    }
                }
            } break;
        }
    }
}
