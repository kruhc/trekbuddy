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
        this.name = "Nokia";
        this.cellIdProperty = "Cell-ID";
        if (cz.kruch.track.TrackingMIDlet.s60nd) {
            this.values = new int[]{ 0, 100 };
        } else {
            this.values = new int[]{ 0, 10, 25, 50, 100 };
        }
    }

    /** @Override */
    void nextLevel() {
        backlight++;
        if (backlight == values.length) {
            backlight = 0;
        }
        setLights();
    }

    /** @Override */
    void sync() {
        confirm(backlight == 0 ? Resources.getString(Resources.DESKTOP_MSG_BACKLIGHT_OFF) : Resources.getString(Resources.DESKTOP_MSG_BACKLIGHT_ON) + " (" + values[backlight] + "%)");
    }

    /** @Override */
    void setColor(javax.microedition.lcdui.Graphics graphics, int argbcolor) {
        com.nokia.mid.ui.DirectUtils.getDirectGraphics(graphics).setARGBColor(argbcolor);
    }

    protected void setLights() {
        com.nokia.mid.ui.DeviceControl.setLights(0, values[backlight]);
    }
}
