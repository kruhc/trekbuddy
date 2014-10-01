// @LICENSE@

package cz.kruch.track.ui.nokia;

import cz.kruch.track.configuration.Config;

/**
 * Device control implementation for Nokia phones.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
class NokiaDeviceControl extends DeviceControl {

    protected final int[] values;

//    protected int last;

    NokiaDeviceControl() {
        this.name = "Nokia";
        this.cellIdProperty = "Cell-ID";
        this.lacProperty = "com.nokia.mid.lac";
        if (cz.kruch.track.TrackingMIDlet.s60nd) {
            this.values = new int[]{ 0, 100 };
        } else {
            this.values = new int[]{ 0, 10, 25, 50, 100 };
        }
    }

    /** @Override */
    void next() {
        if (presses++ == 0) {
            nextLevel();
            confirm();
        }
    }

    /** @Override */
    void sync() {
        if (presses == 0) {
            if (Config.nokiaBacklightLast != 0) {
                invertLevel();
                confirm();
            }
        }
        presses = 0;
    }

    /** @Override */
    void nextLevel() {
        if (++backlight == values.length) {
            backlight = 0;
        }
        setLights();
        Config.nokiaBacklightLast = backlight;
        Config.updateInBackground(Config.VARS_090);
    }

    /** @Override */
    String level() {
        if (backlight == 0) {
            return "off";
        }
        return Integer.toString(values[backlight]).concat("%");
    }

    /** @Override */
    void flash() {
        /* S60 - OK, S40 - it turns the screen off :( */
//#ifdef __SYMBIAN__
        super.flash();
//#endif
    }

    private void invertLevel() {
        if (backlight == 0) {
            backlight = Config.nokiaBacklightLast;
        } else {
            backlight = 0;
        }
        handleInvert();
    }

    protected void setLights() {
        com.nokia.mid.ui.DeviceControl.setLights(0, values[backlight]);
    }

    protected void handleInvert() {
        setLights();
    }
}
