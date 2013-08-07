package cz.kruch.track.ui.nokia;

//#ifdef __CN1__

final class WPDeviceControl extends DeviceControl {

    private boolean isTicker;

    WPDeviceControl() {
        this.name = "WP8";
    }

    void turnOn() {
        com.codename1.ui.FriendlyAccess.getImplementation().execute("backlight", new Object[]{ true });
    }

    void turnOff() {
        com.codename1.ui.FriendlyAccess.getImplementation().execute("backlight", new Object[]{ false });
    }

    void useTicker(final Object list, final String msg) {
        if (msg != null) {
            com.codename1.ui.FriendlyAccess.getImplementation().execute("show-progress", new Object[]{ msg });
            isTicker = true;
        } else {
            if (isTicker) {
                com.codename1.ui.FriendlyAccess.getImplementation().execute("show-progress", new Object[]{ null });
                isTicker = false;
            }
        }
    }
}

//#endif