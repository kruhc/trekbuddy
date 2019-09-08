// @LICENSE@

package cz.kruch.track.ui.nokia;

/**
 * Device control implementation for Sonim phones.
 *
 * @author kruhc@seznam.cz
 */
final class SonimDeviceControl extends DeviceControl {

    private com.sonimtech.j2me.BacklightController controller;

    SonimDeviceControl() {
        this.name = "Sonim";
    }

    /** @overriden */
    void turnOn() {
        controller = new com.sonimtech.j2me.BacklightController();
        controller.turnOnBacklight(com.sonimtech.j2me.BacklightController.BACKLIGHTPERMANENT);
    }

    /** @overriden */
    void turnOff() {
        controller.closeBacklight();
        controller = null;
    }
}

