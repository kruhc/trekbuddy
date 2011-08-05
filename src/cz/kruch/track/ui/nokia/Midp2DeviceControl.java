// @LICENSE@

package cz.kruch.track.ui.nokia;

/**
 * Generic implementation. I doubt it works well...
 *
 * @author kruhc@seznam.cz
 */
final class Midp2DeviceControl extends DeviceControl {

    private int period;

    Midp2DeviceControl() {
        if (cz.kruch.track.TrackingMIDlet.sonim) {
            this.name = "MIDP2/Sonim";
            this.period = 1; // minimum possible 1 ms
        } else {
            this.name = "MIDP2";
            this.period = 5000; // 5 sec
        }
    }

    /** @overriden */
    boolean isSchedulable() {
        return !cz.kruch.track.TrackingMIDlet.sonim;
    }

    /** @overriden */
    void turnOn() {
        cz.kruch.track.ui.Desktop.display.flashBacklight(period);
    }

    /** @overriden */
    void turnOff() {
        cz.kruch.track.ui.Desktop.display.flashBacklight(0);
    }
}
