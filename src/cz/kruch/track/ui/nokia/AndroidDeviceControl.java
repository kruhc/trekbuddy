// @LICENSE@

package cz.kruch.track.ui.nokia;

final class AndroidDeviceControl extends DeviceControl {

    private android.os.PowerManager.WakeLock wl;

    AndroidDeviceControl() {
        this.name = "Android";
    }

    /** @overriden */
    void turnOn() {
        android.os.PowerManager pm = (android.os.PowerManager) org.microemu.android.MicroEmulator.context.getSystemService(android.content.Context.POWER_SERVICE);
        wl = pm.newWakeLock(android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "TrekBuddy");
        wl.acquire();
    }

    /** @overriden */
    void turnOff() {
        wl.release();
     }
}
