// @LICENSE@

package cz.kruch.track.ui.nokia;

/**
 * Device control implementation for Motorola phones with multimedia.Lighting API.
 *
 * @author kruhc@seznam.cz
 */
final class MotorolaDeviceControl extends DeviceControl {

    MotorolaDeviceControl() {
        this.name = "Motorola";
        this.cellIdProperty = "CellID";
        this.lacProperty = "LocAreaCode";
    }

    /** @overriden */
    void turnOn() {
        com.motorola.multimedia.Lighting.backlightOn();
    }

    /** @overriden */
    void turnOff() {
        com.motorola.multimedia.Lighting.backlightOff();
    }
}
