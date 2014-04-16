package com.codename1.ui;

import java.io.InputStream;

public class FriendlyAccess {

    public static com.codename1.impl.CodenameOneImplementation getImplementation() {
        return Display.getInstance().getImplementation();
    }

    public static com.codename1.location.LocationManager getLocationManager() {
        return Display.getInstance().getLocationManager();        
    }

    public static InputStream getResourceAsStream(final String resource) {
        return getImplementation().getResourceAsStream(null, resource);
    }

    public static Object execute(final String action, final Object[] params) {
        return getImplementation().execute(action, params);
    }

    // DO NOT USE, this was experiment
    public static void flushGraphics() {
        getImplementation().paintDirty();
        getImplementation().flushGraphics();
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
