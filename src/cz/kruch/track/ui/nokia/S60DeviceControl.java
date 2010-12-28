// @LICENSE@

package cz.kruch.track.ui.nokia;

/**
 * Device control implementation for S60/UIQ phones.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class S60DeviceControl extends NokiaDeviceControl {

    private cz.kruch.track.device.SymbianService.Inactivity inactivity;
    private String lastError;

    S60DeviceControl() {
        if (cz.kruch.track.TrackingMIDlet.uiq) {
            this.name = "UIQ";
        } else {
            this.name = "S60";
            this.cellIdProperty = "com.nokia.mid.cellid";
            this.lacProperty = "com.nokia.mid.lac";
        }
    }


    /** @Override */
    protected void setLights() {
        if (inactivity == null) {
            try {
                this.inactivity = cz.kruch.track.device.SymbianService.openInactivity();
            } catch (Exception e) { // IOE or SE
                this.lastError = "Service not accessible. " + e.toString();
            }
        }
        if (inactivity != null) {
            try {
                inactivity.setLights(values[backlight]);
            } catch (Exception e) { // IOE or SE
                lastError = "Service not accessible. " + e.toString();
            }
        }
    }

    /** @Override */
    void confirm() {
        if (lastError == null) {
            super.confirm();
        } else {
            cz.kruch.track.ui.Desktop.showError(lastError, null, null);
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
