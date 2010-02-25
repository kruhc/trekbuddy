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

    void setColor(javax.microedition.lcdui.Graphics graphics, int argbcolor) {
        com.nokia.mid.ui.DirectUtils.getDirectGraphics(graphics).setARGBColor(argbcolor);
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
