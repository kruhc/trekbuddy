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
                   android.hardware.SensorListener,
                   Runnable {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("AndroidLocationProvider");
//#endif

    private android.location.LocationManager manager;
    private android.hardware.SensorManager sensors;
    private android.os.Looper looper;

    public AndroidLocationProvider() {
        super("Internal");
    }

    public int start() throws LocationException {
        try {
            manager = (android.location.LocationManager) org.microemu.android.MicroEmulator.context.getSystemService(android.content.Context.LOCATION_SERVICE);
            if (manager == null) {
                throw new LocationException("Service not found");
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

    public void sense() throws LocationException {
        try {
            sensors = (android.hardware.SensorManager) org.microemu.android.MicroEmulator.context.getSystemService(android.content.Context.SENSOR_SERVICE);
            if (sensors == null) {
                throw new LocationException("Service not found");
            }
            sensors.registerListener(this, android.hardware.SensorManager.SENSOR_ORIENTATION,
                                     android.hardware.SensorManager.SENSOR_DELAY_NORMAL);
        } catch (LocationException e) {
            throw e;
        } catch (Exception e) {
            throw new LocationException(e);
        }
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
            qc.setAlt((float) l.getAltitude());
        }
        if (l.hasAccuracy()) {
            qc.setHorizontalAccuracy(l.getAccuracy());
        }
        int sat = -1;
        final android.os.Bundle extras = l.getExtras();
        if (extras != null) {
            sat = extras.getInt("satellites", -1);
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

    public void onStatusChanged(String string, int i, android.os.Bundle bundle) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("onStatusChanged");
//#endif
        if (isGo()) {
            switch (i) {
                case android.location.LocationProvider.OUT_OF_SERVICE:
                    i = OUT_OF_SERVICE;
                break;
                case android.location.LocationProvider.TEMPORARILY_UNAVAILABLE:
                    i = TEMPORARILY_UNAVAILABLE;
                break;
                case android.location.LocationProvider.AVAILABLE:
                    i = AVAILABLE;
                break;
            }
            notifyListener(i);
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

    public void onSensorChanged(int sensor, float[] values) {
        // notify
        notifySenser((int) values[0]);
    }

    public void onAccuracyChanged(int arg0, int arg1) {
        // TODO
    }
}
