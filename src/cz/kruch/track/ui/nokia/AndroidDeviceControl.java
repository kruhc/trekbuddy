// @LICENSE@

package cz.kruch.track.ui.nokia;

//#ifdef __ANDROID__

import cz.kruch.track.configuration.Config;

final class AndroidDeviceControl
        extends DeviceControl
        implements android.hardware.SensorEventListener {

    private android.os.PowerManager.WakeLock wl;
    private android.app.ProgressDialog ticker;
    private android.hardware.Sensor sensor;

    private api.location.LocationListener listener;

    private final int[] values;

    AndroidDeviceControl() {
        this.name = "Android";
        this.values = new int[]{ 0, 10, 25, 50, 100 };
    }

    @Override
    boolean isSchedulable() {
        return true;
    }

    @Override
    boolean forceOff() {
        return true;
    }

    @Override
    void turnOn() {
        if (wl == null) {
            wl = ((android.os.PowerManager) cz.kruch.track.TrackingMIDlet.getActivity().getSystemService(android.content.Context.POWER_SERVICE)).newWakeLock(android.os.PowerManager.SCREEN_DIM_WAKE_LOCK | android.os.PowerManager.ON_AFTER_RELEASE, "TrekBuddy");
        }
//        android.util.Log.i("TrekBuddy", "[app] wake lock refresh");
        wl.acquire();
        wl.release();
    }

    @Override
    void turnOff() {
//        android.util.Log.i("TrekBuddy", "[app] wake lock off");
        wl = null;
     }

    @Override
    void sync() {
//        android.util.Log.i("TrekBuddy", "[app] sync; presses " + presses);
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
//        android.util.Log.i("TrekBuddy", "[app] next level idx " + backlight);
    }

    @Override
    String level() {
        if (backlight == 0) {
            return "off";
        }
        return Integer.toString(values[backlight]).concat("%");
    }

    private void invertLevel() {
//        android.util.Log.i("TrekBuddy", "[app] invert level");
        if (backlight == 0) {
            backlight = Config.nokiaBacklightLast;
        } else {
            backlight = 0;
        }
        setLights();
    }

    private void setLights() {
//        android.util.Log.i("TrekBuddy", "[app] set brightness to level " + backlight + " (idx)");
        if (backlight == 0) {
//            android.util.Log.i("TrekBuddy", "[app] task = " + task);
            if (task != null) {
                task.cancel();
                task = null;
            }
        } else {
            final float value = ((float) values[backlight]) / 100;
//            android.util.Log.i("TrekBuddy", "[app] set screenBrightness to: " + value);
            cz.kruch.track.TrackingMIDlet.getActivity().post(new Runnable() {
                @Override
                public void run() {
                    final android.view.WindowManager.LayoutParams layout = cz.kruch.track.TrackingMIDlet.getActivity().getWindow().getAttributes();
                    layout.screenBrightness = value;
                    cz.kruch.track.TrackingMIDlet.getActivity().getWindow().setAttributes(layout);
                }
            });
//            android.util.Log.i("TrekBuddy", "[app] task = " + task);
            if (task == null) {
                cz.kruch.track.ui.Desktop.scheduleAtFixedRate(task = new DeviceControl(),
                                                              REFRESH_PERIOD, REFRESH_PERIOD);
            }
        }
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
        // notify
        notifySenser((int) event.values[0]);
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
