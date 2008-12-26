// @LICENSE@

package cz.kruch.track.ui.nokia;

import cz.kruch.track.Resources;

/**
 * Device control implementation for Nokia phones.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
class NokiaDeviceControl extends DeviceControl {

    protected final int[] values;

    NokiaDeviceControl() {
        if (cz.kruch.track.TrackingMIDlet.s60nd) {
            values = new int[]{ 0, 100 };
        } else {
            values = new int[]{ 0, 10, 25, 50, 100 };
        }
    }

    /** @overriden */
    void nextLevel() {
        backlight++;
        if (backlight == values.length) {
            backlight = 0;
        }
        setLights();
    }

    protected void setLights() {
        com.nokia.mid.ui.DeviceControl.setLights(0, values[backlight]);
    }

    /** @overriden */
    void sync() {
        confirm(backlight == 0 ? Resources.getString(Resources.DESKTOP_MSG_BACKLIGHT_OFF) : Resources.getString(Resources.DESKTOP_MSG_BACKLIGHT_ON) + " (" + values[backlight] + "%)");
    }
}
