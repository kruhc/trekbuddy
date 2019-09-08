// @LICENSE@

package cz.kruch.track.ui.nokia;

/**
 * Device control implementation for Samsung phones.
 *
 * @author kruhc@seznam.cz
 */
final class SamsungDeviceControl extends DeviceControl {

    SamsungDeviceControl() {
        this.name = "Samsung";
    }

    /** @overriden */
    boolean isSchedulable() {
        return true;
    }

    /** @overriden */
    boolean forceOff() {
        return true;
    }

    /** @overriden */
    void turnOn() {
        com.samsung.util.LCDLight.on(REFRESH_PERIOD * 2);
    }

    /** @overriden */
    void turnOff() {
        com.samsung.util.LCDLight.off();
    }
}
