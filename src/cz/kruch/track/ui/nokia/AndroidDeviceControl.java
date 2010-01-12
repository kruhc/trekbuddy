// @LICENSE@

package cz.kruch.track.ui.nokia;

final class AndroidDeviceControl extends DeviceControl {

    private android.os.PowerManager.WakeLock wl;
    private android.app.ProgressDialog ticker;

    AndroidDeviceControl() {
        this.name = "Android";
    }

    /** @Override */
    void turnOn() {
        android.os.PowerManager pm = (android.os.PowerManager) org.microemu.android.MicroEmulator.context.getSystemService(android.content.Context.POWER_SERVICE);
        wl = pm.newWakeLock(android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "TrekBuddy");
        wl.acquire();
    }

    /** @Override */
    void turnOff() {
        wl.release();
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

//    /** @Override */
//    String getCardURL() throws Exception {
//        return android.os.Environment.getExternalStorageDirectory().toURL().toString();
//    }
}
