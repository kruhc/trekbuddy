// @LICENSE@

package cz.kruch.track.ui.nokia;

/**
 * Device control implementation for Nokia phones with Nokia UI API 1.4+.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class NokiaUi14DeviceControl extends NokiaDeviceControl {

    NokiaUi14DeviceControl() {
        this.name = "Nokia+";
        this.values[0] = 10; // min backlight
    }

    /** @Override */
    protected void setLights() {

        // set required lights
        super.setLights();

        // prevent screensaver
        if (backlight == 0) {
            if (task != null) {
                task.cancel();
                task = null;
            }
        } else {
            if (task == null) {
                cz.kruch.track.ui.Desktop.scheduleAtFixedRate(task = new DeviceControl(), REFRESH_PERIOD, REFRESH_PERIOD);
            }
        }
    }

    /** @Override */
    void turnOn() {
        com.nokia.mid.ui.DeviceControl.resetUserInactivityTime();
    }
}
