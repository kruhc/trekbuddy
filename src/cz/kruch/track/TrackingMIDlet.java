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

import com.ice.tar.TarInputStream;

public class TrackingMIDlet extends MIDlet {

    private static String APP_NAME = Desktop.APP_TITLE + " (C) 2006 KrUcH";

    private Desktop desktop;

    // system info
    private static String flags;
/*
    private static boolean emulator;
*/
    private static boolean logEnabled;
    private static boolean jsr179;
    private static boolean jsr82;
    private static int numAlphaLevels = 2;

    static {
        try {
            APP_NAME = Desktop.APP_TITLE + " " + new String(new byte[]{ (byte) 0xc2, (byte) 0xa9 }, "UTF-8") + " " + "2006 KrUcH";
        } catch (UnsupportedEncodingException e) {
        }
    }

    public TrackingMIDlet() {
        this.desktop = null;

        // detect environment
        TrackingMIDlet.flags = getAppProperty("App-Flags");
/*
        TrackingMIDlet.emulator = "true".equals(getAppProperty("Is-Emulator"));
*/
        TrackingMIDlet.logEnabled = "true".equals(getAppProperty("Log-Enable"));
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

        // setup environment
        if (hasFlag("fs_read_skip")) {
            System.out.println("* read-skip feature on");
            TarInputStream.useReadSkip = true;
        }
    }

    protected void startApp() throws MIDletStateChangeException {
        Display display = Display.getDisplay(this);

        // setup environment
        TrackingMIDlet.numAlphaLevels = display.numAlphaLevels();

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
            Config.getSafeInstance().setMapPath("file:///E/cr-gpska.tar");
*/

/*
            // BENQ fix
            Config.getSafeInstance().setSimulatorPath("file:///0:/Misc/track.nmea");
*/

            // 3. create desktop
            desktop = new Desktop(display);

            // 4. read default map
            console.show("loading default map...");
            if ("".equals(Config.getSafeInstance().getMapPath())) {
                desktop.initDefaultMap();
                console.result(1, "skipped");
            } else {
                try {
                    desktop.initMap();
                    console.result(0, "ok");
                } catch (Exception e) {
                    e.printStackTrace();
                    console.result(-1, "failed");
                }
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

/*
    public static boolean isEmulator() {
        return emulator;
    }
*/

    public static boolean isLogEnabled() {
        return logEnabled;
    }

    public static boolean isJsr179() {
        return jsr179;
    }

    public static boolean isJsr82() {
        return jsr82;
    }

    public static String getFlags() {
        return flags;
    }

    public static boolean hasFlag(String flag) {
        return flags == null ? false : flags.indexOf(flag) > -1;
    }

    public static int numAlphaLevels() {
        return numAlphaLevels;
    }
}
