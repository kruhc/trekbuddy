// @LICENSE@

package cz.kruch.track.ui.nokia;

/**
 * Device control implementation for SonyEricsson phones.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class SonyEricssonDeviceControl extends DeviceControl {

    SonyEricssonDeviceControl() {
        this.name = "SonyEricsson";
        this.cellIdProperty = "com.sonyericsson.net.cellid";
        this.lacProperty = "com.sonyericsson.net.lac";
    }

    /** @overriden */
    String getCellId() {
        return hexToDec(super.getCellId());
    }

    /** @overriden */
    String getLac() {
        return hexToDec(super.getLac());
    }

    /** @overriden */
    boolean isSchedulable() {
        return true;
    }

    /** @overriden */
    void turnOn() {
        cz.kruch.track.ui.Desktop.display.flashBacklight(1);
        com.nokia.mid.ui.DeviceControl.setLights(0, 100);
    }

    /** @overriden */
    void turnOff() {
        cz.kruch.track.ui.Desktop.display.flashBacklight(0);
//        com.nokia.mid.ui.DeviceControl.setLights(0, 0);
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
