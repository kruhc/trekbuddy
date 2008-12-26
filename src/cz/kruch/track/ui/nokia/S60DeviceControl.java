// @LICENSE@

package cz.kruch.track.ui.nokia;

import cz.kruch.track.device.SymbianService;

/**
 * Device control implementation for S60 phones.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class S60DeviceControl extends NokiaDeviceControl {

    private SymbianService.Inactivity inactivity;

    S60DeviceControl() {
        this.inactivity = SymbianService.openInactivity();
    }

    /** @overriden */
    protected void setLights() {
        inactivity.setLights(values[backlight]);
        super.setLights();
    }

    /** @overriden */
    void close() {
        inactivity.close();
        super.close();
    }
}
