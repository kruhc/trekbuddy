// @LICENSE@

package cz.kruch.track.ui.nokia;

import cz.kruch.track.device.SymbianService;

import java.io.IOException;

/**
 * Device control implementation for S60 phones.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class S60DeviceControl extends NokiaDeviceControl {

    private SymbianService.Inactivity inactivity;

    S60DeviceControl() throws IOException {
        if (cz.kruch.track.TrackingMIDlet.uiq) {
            this.name = "Symbian/UIQ";
        } else {
            this.name = "Symbian/S60";
            this.cellIdProperty = "com.nokia.mid.cellid";
        }
        this.inactivity = SymbianService.openInactivity();
    }

    /** @overriden */
    protected void setLights() {
        inactivity.setLights(values[backlight]);
        if (cz.kruch.track.TrackingMIDlet.nokia) {
            super.setLights();
        }
    }

    /** @overriden */
    void close() {
        inactivity.close();
        super.close();
    }
}
