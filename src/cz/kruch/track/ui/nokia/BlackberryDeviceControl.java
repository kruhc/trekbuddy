// @LICENSE@

package cz.kruch.track.ui.nokia;

/**
 * Device control implementation for Blackberry phones.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class BlackberryDeviceControl extends DeviceControl {

    BlackberryDeviceControl() {
        this.name = "Blackberry";
    }

    boolean isSchedulable() {
        return true;
    }

    /** @overriden */
    String getCellId() {
        final net.rim.device.api.system.GPRSInfo.GPRSCellInfo cellInfo = net.rim.device.api.system.GPRSInfo.getCellInfo();
        if (cellInfo != null) {
            return Integer.toString(cellInfo.getCellId());
        }
        return null;
    }

    /** @overriden */
    String getLac() {
        final net.rim.device.api.system.GPRSInfo.GPRSCellInfo cellInfo = net.rim.device.api.system.GPRSInfo.getCellInfo();
        if (cellInfo != null) {
            return Integer.toString(cellInfo.getLAC());
        }
        return null;
    }

    /** @overriden */
    void turnOn() {
        net.rim.device.api.system.Backlight.enable(true);
    }

    /** @overriden */
    void turnOff() {
        net.rim.device.api.system.Backlight.enable(false);
    }

    /** @overriden */
    public void run() {
        if (backlight != 0) {
            net.rim.device.api.system.Backlight.enable(false);
            net.rim.device.api.system.Backlight.enable(true);
        }
    }
}
