// @LICENSE@

package cz.kruch.track.ui.nokia;

/**
 * Device control implementation for SonyEricsson phones.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class SonyEricssonDeviceControl extends DeviceControl {

    private boolean jp6plus;

    SonyEricssonDeviceControl() {
        this.cellIdProperty = "com.sonyericsson.net.cellid";
        this.lacProperty = "com.sonyericsson.net.lac";
        try {
            Class.forName("java.rmi.Remote");
            this.name = "SonyEricsson/JP6+";
            this.jp6plus = true;
        } catch (ClassNotFoundException e) {
            this.name = "SonyEricsson";
        }
    }

    /** @Override */
    String getCellId() {
        return hexToDec(super.getCellId());
    }

    /** @Override */
    String getLac() {
        return hexToDec(super.getLac());
    }

    /** @Override */
    boolean isSchedulable() {
        return true;
    }

    /** @Override */
    void turnOn() {
        cz.kruch.track.ui.Desktop.display.flashBacklight(1);
        com.nokia.mid.ui.DeviceControl.setLights(0, 100);
    }

    /** @Override */
    void turnOff() {
        cz.kruch.track.ui.Desktop.display.flashBacklight(0);
//        com.nokia.mid.ui.DeviceControl.setLights(0, 0);  /* intentionally commented out */
    }

    /** @Override */
    void confirm() {
        if (jp6plus) {
            if (!cz.kruch.track.configuration.Config.powerSave) {
                cz.kruch.track.ui.Desktop.display.vibrate(100);
            }
        } else {
            cz.kruch.track.ui.Desktop.showConfirmation(cz.kruch.track.Resources.getString(backlight == STATUS_OFF ? cz.kruch.track.Resources.DESKTOP_MSG_BACKLIGHT_OFF : cz.kruch.track.Resources.DESKTOP_MSG_BACKLIGHT_ON), null);
        }
    }

    private static String hexToDec(final String id) {
        if (id != null && id.length() != 0) {
            try {
                return Integer.toString(Integer.parseInt(id, 16));
            } catch (Exception e) {
                return id;
            }
        }
        return null;
    }
}
