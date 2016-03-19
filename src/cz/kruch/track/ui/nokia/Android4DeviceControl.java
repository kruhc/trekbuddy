// @LICENSE@

package cz.kruch.track.ui.nokia;

//#ifdef __ANDROID__

import cz.kruch.track.configuration.Config;

final class Android4DeviceControl
        extends DeviceControl
        implements android.hardware.SensorEventListener {

    private android.app.ProgressDialog ticker;
    private android.hardware.Sensor sensor;

    private api.location.LocationListener listener;

    private final int[] values;

    Android4DeviceControl() {
        this.name = "Android/4";
        this.values = new int[]{ 0, 10, 25, 50, 100 };
    }

    @Override
    void sync() {
        if (presses == 0) {
            if (Config.nokiaBacklightLast != 0) {
                invertLevel();
                confirm();
            }
        }
        presses = 0;
    }

    @Override
    void nextLevel() {
        if (++backlight == values.length) {
            backlight = 0;
        }
        setLights();
        Config.nokiaBacklightLast = backlight;
        Config.updateInBackground(Config.VARS_090);
    }

    @Override
    String level() {
        if (backlight == 0) {
            return "off";
        }
        return Integer.toString(values[backlight]).concat("%");
    }

    private void invertLevel() {
        if (backlight == 0) {
            backlight = Config.nokiaBacklightLast;
        } else {
            backlight = 0;
        }
        setLights();
    }

    private void setLights() {
        final float value = ((float) values[backlight]) / 100;
        android.util.Log.i("TrekBuddy", "[app] set screen brightness to: " + value);
        cz.kruch.track.TrackingMIDlet.getActivity().post(new Runnable() {

            @Override
            public void run() {
                final org.microemu.android.MicroEmulatorActivity activity = cz.kruch.track.TrackingMIDlet.getActivity();
                final android.view.Window window = activity.getWindow();
                if (backlight == 0) {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    final android.view.WindowManager.LayoutParams layout = window.getAttributes();
                    layout.screenBrightness = value;
                    window.setAttributes(layout);
                }
                android.widget.Toast.makeText(activity, "Backlight " + getLevel(),
                                              android.widget.Toast. LENGTH_SHORT).show();
            }
        });
    }

    @Override
    void useTicker(final Object list, final String msg) {
        cz.kruch.track.TrackingMIDlet.getActivity().post(new Runnable() {
            public void run() {
                if (msg != null) {
                    if (ticker == null) {
                        ticker = android.app.ProgressDialog.show(cz.kruch.track.TrackingMIDlet.getActivity(),
                                                                 "", msg, true);
                    } else { // should not happen but happens... :(
                        android.util.Log.w("TrekBuddy", "[app] progress dialog already shown");
                        ticker.setMessage(msg);
                    }
                } else {
                    if (ticker != null) {
                        ticker.dismiss();
                        ticker = null;
                    }
                }
            }
        });
    }

    @Override
    void sense(final api.location.LocationListener listener) {
        this.listener = listener;
        final android.hardware.SensorManager sm = (android.hardware.SensorManager) cz.kruch.track.TrackingMIDlet.getActivity().getSystemService(android.content.Context.SENSOR_SERVICE);
        sensor = sm.getDefaultSensor(android.hardware.Sensor.TYPE_ORIENTATION);
        if (sensor != null) {
            sm.registerListener(this, sensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    void nonsense(final api.location.LocationListener listener) {
        final android.hardware.SensorManager sm = (android.hardware.SensorManager) cz.kruch.track.TrackingMIDlet.getActivity().getSystemService(android.content.Context.SENSOR_SERVICE);
        if (sensor != null) {
            sm.unregisterListener(this, sensor);
            sensor = null;
        }
        this.listener = null;
    }

    @Override
    public void onSensorChanged(android.hardware.SensorEvent event) {
//        android.util.Log.w("TrekBuddy", "[app] azimuth = " + event.values[0] + "; orientation = " + cz.kruch.track.TrackingMIDlet.getActivity().orientation);
        int azimuth = (int) event.values[0];
        switch (cz.kruch.track.TrackingMIDlet.getActivity().orientation) {
            case 0: // portrait
                // nothing to do
                break;
            case 1: // landscape
                azimuth = (azimuth + 90) % 360;
                break;
            case 2: // portrait reversed
                azimuth = (azimuth + 180) % 360;
                break;
            case 3: // landscape reversed
                azimuth = (azimuth + 270) % 360;
                break;
        }
//        android.util.Log.w("TrekBuddy", "[app] fixed azimuth = " + azimuth);
        notifySenser(azimuth);
    }

    @Override
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
