package cz.kruch.track.ui.nokia;

import com.codename1.impl.ExtendedImplementation;

//#ifdef __CN1__

final class WPDeviceControl extends DeviceControl {

    private boolean isTicker;

    WPDeviceControl() {
        this.name = "WP";
    }

    void turnOn() {
        ExtendedImplementation.i().lockScreen();
    }

    void turnOff() {
        ExtendedImplementation.i().unlockScreen();
    }

    void useTicker(final Object list, final String msg) {
        if (msg != null) {
            ExtendedImplementation.exec("show-progress", new Object[]{ msg });
            isTicker = true;
        } else {
            if (isTicker) {
                ExtendedImplementation.exec("show-progress", new Object[]{ null });
                isTicker = false;
            }
        }
    }
}

//#endif