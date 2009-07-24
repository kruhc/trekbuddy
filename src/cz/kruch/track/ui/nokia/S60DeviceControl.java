// @LICENSE@

package cz.kruch.track.ui.nokia;

import cz.kruch.track.device.SymbianService;
import cz.kruch.track.ui.Desktop;

import java.io.IOException;

/**
 * Device control implementation for S60/UIQ phones.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class S60DeviceControl extends NokiaDeviceControl {

    private SymbianService.Inactivity inactivity;

    S60DeviceControl() throws IOException {
        if (cz.kruch.track.TrackingMIDlet.uiq) {
            this.name = "UIQ";
        } else {
            if (cz.kruch.track.TrackingMIDlet.s60rdfp2) {
                this.name = "S60 (3rd FP2)";
                this.values[0] = 10; // N5800 workaround???
            } else {
                this.name = "S60";
                try {
                    this.inactivity = SymbianService.openInactivity();
                } catch (Exception e) { // IOE or SE
                    // service not running/accessible
                }
            }
            this.cellIdProperty = "com.nokia.mid.cellid";
        }
    }


    /** @Override */
    protected void setLights() {

        // old S60 or UIQ and service avail
        if (inactivity != null) {
            inactivity.setLights(values[backlight]);
        }

        // S60, not UIQ
        if (cz.kruch.track.TrackingMIDlet.nokia) {

            // set light level via Nokia UI API
            super.setLights();

            // service either not avail or not needed (S60 3rd FP2+)
            if (inactivity == null) {
                if (backlight == STATUS_OFF) {
                    if (task != null) {
                        task.cancel();
                        task = null;
                    }
                } else if (task == null) {
                    Desktop.timer.scheduleAtFixedRate(task = new DeviceControl(), 5000L, 5000L);
                }
            }
        }
    }

    /** @Override */
    void close() {
        if (inactivity != null) {
            inactivity.close();
        }
        super.close();
    }

    /** @Override */
    public void run() {
        super.setLights();
    }
}
