// @LICENSE@

package cz.kruch.track.ui.nokia;

/**
 * Device control implementation for Nokia phones.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
class NokiaDeviceControl extends DeviceControl {

    protected final int[] values;

    private int last;

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
            if (last == 0) {
                nextLevel();
            } else {
                invertLevel();
            }
            confirm();
        }
    }

    /** @Override */
    void sync() {
        if (presses == 0) {
            nextLevel();
            confirm();
        }
        presses = 0;
    }

    /** @Override */
    void nextLevel() {
        last = ++backlight;
        if (backlight == values.length) {
            backlight = 0;
        }
        setLights();
    }

    protected void setLights() {
        com.nokia.mid.ui.DeviceControl.setLights(0, values[backlight]);
    }

    private void invertLevel() {
        if (backlight == 0) {
            backlight = last;
        } else {
            backlight = 0;
        }
        setLights();
    }
}
