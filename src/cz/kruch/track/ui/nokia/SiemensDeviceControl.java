// @LICENSE@

package cz.kruch.track.ui.nokia;

/**
 * Device control implementation for Siemens phones.
 *
 * @author kruhc@seznam.cz
 */
final class SiemensDeviceControl extends DeviceControl {

    SiemensDeviceControl() {
        this.name = "Siemens";
        this.cellIdProperty = "Siemens.CID";
    }

    /** @overriden */
    void turnOn() {
        com.siemens.mp.game.Light.setLightOn();
    }

    /** @overriden */
    void turnOff() {
        com.siemens.mp.game.Light.setLightOff();
    }
}
