// @LICENSE@

package cz.kruch.track.ui.nokia;

import java.io.IOException;

/**
 * Device control implementation for S60/UIQ phones.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class S60DeviceControl extends NokiaDeviceControl {

    private cz.kruch.track.device.SymbianService.Inactivity inactivity;

    S60DeviceControl() throws IOException {
        if (cz.kruch.track.TrackingMIDlet.uiq) {
            this.name = "UIQ";
        } else {
            this.name = "S60";
            this.cellIdProperty = "com.nokia.mid.cellid";
        }
        try {
            this.inactivity = cz.kruch.track.device.SymbianService.openInactivity();
        } catch (Exception e) { // IOE or SE
            // service not running/accessible
        }
    }


    /** @Override */
    protected void setLights() {
        if (inactivity != null) {
            inactivity.setLights(values[backlight]);
        }
    }

    /** @Override */
    void close() {
        if (inactivity != null) {
            inactivity.close();
        }
        super.close();
    }
}
