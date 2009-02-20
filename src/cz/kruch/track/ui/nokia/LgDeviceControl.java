// @LICENSE@

package cz.kruch.track.ui.nokia;

/**
 * Device control implementation for LG phones.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class LgDeviceControl extends DeviceControl {

    LgDeviceControl() {
        this.name = "LG";
        this.cellIdProperty = "com.lge.net.cellid";
    }

    /** @overriden */
    boolean isSchedulable() {
        return true;
    }

    /** @overriden */
    void turnOn() {
        mmpp.media.BackLight.on(0); // SDK says: "If timeout is 0, turns on permanently."
    }

    /** @overriden */
    void turnOff() {
        mmpp.media.BackLight.off();
    }
}
