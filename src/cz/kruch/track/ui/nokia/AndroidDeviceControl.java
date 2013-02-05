// @LICENSE@

package cz.kruch.track.ui.nokia;

//#ifdef __ANDROID__

final class AndroidDeviceControl
        extends DeviceControl
        implements android.hardware.SensorEventListener {

    private android.os.PowerManager.WakeLock wl;
    private android.app.ProgressDialog ticker;
    private android.hardware.Sensor sensor;

    private api.location.LocationListener listener;

    private int wake;

    AndroidDeviceControl() {
        this.name = "Android";
    }

    /** @Override */
    boolean isSchedulable() {
        return true;
    }

    /** @Override */
    boolean forceOff() {
        return true;
    }

    /** @Override */
    void turnOn() {
        if (wl == null) {
            wl = ((android.os.PowerManager) cz.kruch.track.TrackingMIDlet.getActivity().getSystemService(android.content.Context.POWER_SERVICE)).newWakeLock((wake++ % 2 == 0 ? android.os.PowerManager.SCREEN_DIM_WAKE_LOCK : android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK) | android.os.PowerManager.ON_AFTER_RELEASE, "TrekBuddy");
        }
        wl.acquire();
        wl.release();
    }

    /** @Override */
    void turnOff() {
        wl = null;
     }

    /** @Override */
    void useTicker(final Object list, final String msg) {
        cz.kruch.track.TrackingMIDlet.getActivity().post(new Runnable() {
            public void run() {
                if (msg != null) {
                    ticker = android.app.ProgressDialog.show(cz.kruch.track.TrackingMIDlet.getActivity(),
                                                             "", msg, true);
                } else {
                    if (ticker != null) {
                        ticker.dismiss();
                        ticker = null;
                    }
                }
            }
        });
    }

    /** @Override */
    void sense(final api.location.LocationListener listener) {
        this.listener = listener;
        final android.hardware.SensorManager sm = (android.hardware.SensorManager) cz.kruch.track.TrackingMIDlet.getActivity().getSystemService(android.content.Context.SENSOR_SERVICE);
        sensor = sm.getDefaultSensor(android.hardware.Sensor.TYPE_ORIENTATION);
        if (sensor != null) {
            sm.registerListener(this, sensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    /** @Override */
    void nonsense(final api.location.LocationListener listener) {
        final android.hardware.SensorManager sm = (android.hardware.SensorManager) cz.kruch.track.TrackingMIDlet.getActivity().getSystemService(android.content.Context.SENSOR_SERVICE);
        if (sensor != null) {
            sm.unregisterListener(this, sensor);
            sensor = null;
        }
        this.listener = null;
    }

    /** @Override */
    public void onSensorChanged(android.hardware.SensorEvent event) {
        // notify
        notifySenser((int) event.values[0]);
    }

    /** @Override */
    public void onAccuracyChanged(android.hardware.Sensor sensor, int accuracy) {
        // TODO
    }

    private void notifySenser(final int heading) {
        if (listener != null) {
            try {
                listener.orientationChanged(null, heading);
            } catch (Throwable t) {
                // TODO
            }
        }
    }
}

//#endif
