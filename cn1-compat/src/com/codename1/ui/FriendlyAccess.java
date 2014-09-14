package com.codename1.ui;

import java.io.InputStream;

public class FriendlyAccess {

    private static com.codename1.impl.CodenameOneImplementation impl;

    public static com.codename1.impl.CodenameOneImplementation getImplementation() {
        if (impl == null) {
            impl = Display.getInstance().getImplementation();
        }
        return impl;
    }

    public static com.codename1.location.LocationManager getLocationManager() {
        return getImplementation().getLocationManager();        
    }

    public static InputStream getResourceAsStream(final String resource) {
        return getImplementation().getResourceAsStream(null, resource);
    }

    public static Object execute(final String action, final Object[] params) {
        return getImplementation().execute(action, params);
    }

    // returns global native graphics
    public static Object getNativeGraphics() {
        return getImplementation().getNativeGraphics();
    }

    // returns global native graphics
    public static Object getNativeGraphics(final Graphics graphics) {
        return graphics.getGraphics();
    }

    // returns object's native graphics
    public static Object getNativeGraphics(final Image image) {
        return image.getGraphics().getGraphics();
    }
}
