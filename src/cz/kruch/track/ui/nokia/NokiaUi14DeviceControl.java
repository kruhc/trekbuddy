// @LICENSE@

package cz.kruch.track.ui.nokia;

/**
 * Device control implementation for Nokia phones with Nokia UI API 1.4+.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class NokiaUi14DeviceControl extends NokiaDeviceControl {

    NokiaUi14DeviceControl() {
        this.name = "Nokia/UI 1.4+";
    }

    /** @Override */
    boolean isSchedulable() {
        return true;
    }

    /** @Override */
    void turnOn() {
        com.nokia.mid.ui.DeviceControl.resetUserInactivityTime();
    }
}
