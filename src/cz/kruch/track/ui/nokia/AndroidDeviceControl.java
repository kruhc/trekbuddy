// @LICENSE@

package cz.kruch.track.ui.nokia;

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
            wl = ((android.os.PowerManager) org.microemu.android.MicroEmulator.context.getSystemService(android.content.Context.POWER_SERVICE)).newWakeLock((wake++ % 2 == 0 ? android.os.PowerManager.SCREEN_DIM_WAKE_LOCK : android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK) | android.os.PowerManager.ON_AFTER_RELEASE, "TrekBuddy");
        }
        wl.acquire();
        wl.release();
    }

    /** @Override */
    void turnOff() {
        wl = null;
     }

    /** @Override */
    void useTicker(Object list, final String msg) {
        ((org.microemu.android.MicroEmulatorActivity) org.microemu.android.MicroEmulator.context).post(new Runnable() {
            public void run() {
                if (msg != null) {
                    ticker = android.app.ProgressDialog.show(org.microemu.android.MicroEmulator.context,
                                                             "", msg, true);
                } else {
                    if (ticker != null) {
                        ticker.dismiss();
                    }
                }
            }
        });
    }

    /** @Override */
    void sense(api.location.LocationListener listener) {
        this.listener = listener;
        final android.hardware.SensorManager sm = (android.hardware.SensorManager) org.microemu.android.MicroEmulator.context.getSystemService(android.content.Context.SENSOR_SERVICE);
        sensor = sm.getDefaultSensor(android.hardware.Sensor.TYPE_ORIENTATION);
        if (sensor != null) {
            sm.registerListener(this, sensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    /** @Override */
    void nonsense(api.location.LocationListener listener) {
        final android.hardware.SensorManager sm = (android.hardware.SensorManager) org.microemu.android.MicroEmulator.context.getSystemService(android.content.Context.SENSOR_SERVICE);
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
