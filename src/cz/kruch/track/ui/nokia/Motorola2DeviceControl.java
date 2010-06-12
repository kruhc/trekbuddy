// @LICENSE@

package cz.kruch.track.ui.nokia;

/**
 * Device control implementation for Motorola phones with FunLight API.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class Motorola2DeviceControl extends DeviceControl {

    private boolean hasControl;

    Motorola2DeviceControl() {
        this.name = "Motorola/Funlight";
        this.cellIdProperty = "CellID";
        this.lacProperty = "LocAreaCode";
    }

    /** @overriden */
    void turnOn() {
        if (hasControl) {
            com.motorola.funlight.FunLight.getRegion(1).setColor(com.motorola.funlight.FunLight.ON);
        } else {
            hasControl = true;
            com.motorola.funlight.FunLight.getControl();
            cz.kruch.track.ui.Desktop.timer.scheduleAtFixedRate(this, 0, 100);
        }
    }

    /** @overriden */
    void turnOff() {
        hasControl = false;
        com.motorola.funlight.FunLight.releaseControl();
    }

    /** @overriden */
    public void run() {
        turnOn();
    }
}
