// @LICENSE@

package cz.kruch.track.ui.nokia;

/**
 * Generic implementation. I doubt it works well...
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class Midp2DeviceControl extends DeviceControl {

    Midp2DeviceControl() {
        this.name = "MIDP2";
    }

    /** @overriden */
    boolean isSchedulable() {
        return true;
    }

    /** @overriden */
    void turnOn() {
        cz.kruch.track.ui.Desktop.display.flashBacklight(5000); // 5 sec
    }

    /** @overriden */
    void turnOff() {
        cz.kruch.track.ui.Desktop.display.flashBacklight(0);
    }
}
