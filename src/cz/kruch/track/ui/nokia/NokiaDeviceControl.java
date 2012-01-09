// @LICENSE@

package cz.kruch.track.ui.nokia;

import cz.kruch.track.configuration.ConfigurationException;

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
            if (cz.kruch.track.configuration.Config.nokiaBacklightLast != 0) {
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
        cz.kruch.track.configuration.Config.nokiaBacklightLast = backlight;
        try {
            cz.kruch.track.configuration.Config.update(cz.kruch.track.configuration.Config.VARS_090);
        } catch (ConfigurationException e) {
            // ignore
        }
    }

    /** @Override */
    String level() {
        return Integer.toString(values[backlight]) + "%";
    }

    private void invertLevel() {
        if (backlight == 0) {
            backlight = cz.kruch.track.configuration.Config.nokiaBacklightLast;
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
