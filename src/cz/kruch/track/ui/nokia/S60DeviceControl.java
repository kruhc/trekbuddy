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
    private boolean initialized;

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
        if (!initialized) {
            try {
                this.inactivity = cz.kruch.track.device.SymbianService.openInactivity();
            } catch (Exception e) { // IOE or SE
                this.lastError = "Service not accessible.\nDetail: " + e.toString();
                this.name += " (-)";
                this.values[0] = 10; // min backlight
            } finally {
                initialized = true;
            }
        }

        /*
         * Either use service or try priodically calling setLights
         */

        if (inactivity != null) {
            try {
                inactivity.setLights(values[backlight]);
            } catch (Exception e) { // IOE or SE
                lastError = "Service not accessible. " + e.toString();
            }
        } else {
            super.setLights();
            handleInvert();
        }
    }

    /** @Override */
    protected void handleInvert() {
        if (backlight == 0) {
            if (task != null) {
                task.cancel();
                task = null;
            }
        } else {
            if (task == null) {
                cz.kruch.track.ui.Desktop.timer.scheduleAtFixedRate(task = new DeviceControl(), REFRESH_PERIOD, REFRESH_PERIOD);
            }
        }
    }

    /** @Override */
    void confirm() {
        if (lastError == null) {
            super.confirm();
        } else {
            cz.kruch.track.ui.Desktop.showWarning(lastError, null, null);
            lastError = null;
        }
    }

    /** @Override */
    void close() {
        if (inactivity != null) {
            inactivity.close();
            inactivity = null;
        }
        super.close();
    }

    /** @Override */
    void turnOn() {
        super.setLights();
    }
}
