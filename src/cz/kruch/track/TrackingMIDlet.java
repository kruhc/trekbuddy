// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track;

import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;
import javax.microedition.lcdui.Display;

import cz.kruch.track.ui.Desktop;
import cz.kruch.track.ui.Console;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;

import java.io.UnsupportedEncodingException;

public class TrackingMIDlet extends MIDlet {

    private static String APP_NAME = Desktop.APP_TITLE + " (C) 2006 Ales Pour";

    private Desktop desktop;

    // system info
    private static boolean emulator;
    private static boolean jsr179;
    private static boolean jsr82;

    static {
        try {
            APP_NAME = Desktop.APP_TITLE + " " + new String(new byte[]{ (byte) 0xc2, (byte) 0xa9 }, "UTF-8") + " " + "2006 Ales Pour";
        } catch (UnsupportedEncodingException e) {
        }
    }

    public TrackingMIDlet() {
        this.desktop = null;

        // detect environment
        TrackingMIDlet.emulator = "true".equals(getAppProperty("Is-Emulator"));
        try {
            Class clazz = Class.forName("javax.microedition.location.LocationProvider");
            TrackingMIDlet.jsr179 = true;
        } catch (ClassNotFoundException e) {
        }
        try {
            Class clazz = Class.forName("javax.bluetooth.DiscoveryAgent");
            TrackingMIDlet.jsr82 = true;
        } catch (ClassNotFoundException e) {
        }
    }

    protected void startApp() throws MIDletStateChangeException {
        Display display = Display.getDisplay(this);

        // create desktop if it does not exist
        if (desktop == null) {

            // 1. show boot screen
            Console console = new Console();
            display.setCurrent(console);
            console.show(APP_NAME);
            console.show("");
            console.show("initializing...");

            // 1a. show vital info
            console.show("total memory: " + Runtime.getRuntime().totalMemory());
            console.show("free memory:  " + Runtime.getRuntime().freeMemory());

            // 2. load configuration
            try {
                console.show("loading configuration...");
                Config.getInstance();
                console.result(0, "ok");
            } catch (ConfigurationException e) {
                console.result(-1, "failed");
            }

/*
            // SE, WTK fix
            Config.getSafeInstance().setBookPath("file:///E/slices.tar");

*/
/*
            // BENQ fix
            Config.getSafeInstance().setBookPath("file:///0:/Misc/slices-big.tar");
*/

            // 3. create desktop
            desktop = new Desktop(display);

            // 4. read default map
            console.show("loading default map...");
            try {
                desktop.initMap();
                console.result(0, "ok");
            } catch (Exception e) {
                e.printStackTrace();
                console.result(-1, "failed");
            }

            // 5. preparing desktop
            console.show("preparing desktop...");
            try {
                desktop.initGui();
                console.result(0, "ok");
            } catch (Exception e) {
                e.printStackTrace();
                console.result(-1, "failed");
            }

            // 5. about to show desktop
            console.show("starting...");
            console.delay();
        }

        // show application desktop
        display.setCurrent(desktop);
    }

    protected void pauseApp() {
        // TODO
    }

    protected void destroyApp(boolean b) throws MIDletStateChangeException {
        // TODO
    }

    /*
     * Environment info.
     */

    public static boolean isEmulator() {
        return emulator;
    }

    public static boolean isJsr179() {
        return jsr179;
    }

    public static boolean isJsr82() {
        return jsr82;
    }
}
