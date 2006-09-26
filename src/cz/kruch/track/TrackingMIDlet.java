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

    private static String APP_NAME = Desktop.APP_TITLE + " (C) 2006 KrUcH";

    private Desktop desktop;

    // system info
    private static String flags;
    private static boolean logEnabled;
    private static boolean jsr179;
    private static boolean jsr82;
    private static boolean jsr120;
    private static boolean jsr135;
    private static boolean videoCapture;
    private static boolean fs;
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
        TrackingMIDlet.logEnabled = "true".equals(getAppProperty("Log-Enable"));
        try {
            Class clazz = Class.forName("javax.microedition.location.LocationProvider");
            TrackingMIDlet.jsr179 = true;
        } catch (ClassNotFoundException e) {
        } catch (NoClassDefFoundError e) {
        }
        try {
            Class clazz = Class.forName("javax.bluetooth.DiscoveryAgent");
            TrackingMIDlet.jsr82 = true;
        } catch (ClassNotFoundException e) {
        } catch (NoClassDefFoundError e) {
        }
        try {
            Class clazz = Class.forName("javax.wireless.messaging.TextMessage");
            TrackingMIDlet.jsr120 = true;
        } catch (ClassNotFoundException e) {
        } catch (NoClassDefFoundError e) {
        }
        try {
            Class clazz = Class.forName("javax.microedition.media.Manager");
            TrackingMIDlet.jsr135 = "true".equals(System.getProperty("supports.video.capture"));
        } catch (ClassNotFoundException e) {
        } catch (NoClassDefFoundError e) {
        }

        // fs type
        System.out.println("* fs type: " + api.file.File.getFsType());
        TrackingMIDlet.fs = api.file.File.getFsType() > api.file.File.FS_NONE;

        // setup environment
        if (hasFlag("fs_read_skip")) {
            System.out.println("* fs read-skip feature on");
            com.ice.tar.TarInputStream.useReadSkip = true;
        }
        if (hasFlag("fs_no_available")) {
            System.out.println("* fs no-available feature on");
            cz.kruch.j2se.io.BufferedInputStream.useAvailable = false;
        }
        if (hasFlag("fs_no_reset")) {
            System.out.println("* fs no-reset feature on");
            cz.kruch.track.maps.Map.useReset = false;
        }
        if (hasFlag("ui_no_partial_flush")) {
            System.out.println("* ui no-partial-flush feature on");
            cz.kruch.track.ui.Desktop.partialFlush = false;
        }
    }

    protected void startApp() throws MIDletStateChangeException {
        Display display = Display.getDisplay(this);

        // setup environment
        TrackingMIDlet.numAlphaLevels = display.numAlphaLevels();
        System.out.println("* UI numAlphaLevels: " + TrackingMIDlet.numAlphaLevels);

        // create desktop if it does not exist
        if (desktop == null) {

            // 1. show boot screen
            Console console = new Console();
            display.setCurrent(console);
            console.show(APP_NAME);
            console.show("");
            console.show("initializing...");

            // 2. load configuration
            try {
                console.show("loading config...");
                Config.getInstance();
                console.result(0, "ok");
            } catch (Throwable t) {
                t.printStackTrace();
                console.result(-1, "failed");
            }

            // 3. create desktop
            desktop = new Desktop(this);

            // 4. read default map
            console.show("loading map...");
            try {
                if ("".equals(Config.getSafeInstance().getMapPath())) {
                    desktop.initDefaultMap();
                    console.result(1, "skipped");
                } else {
                    desktop.initMap();
                    console.result(0, "ok");
                }
            } catch (Throwable t) {
                t.printStackTrace();
                console.result(-1, "failed");
            }

/*
            // 5. preparing desktop
            console.show("preparing gui...");
            try {
                desktop.resetGui();
                console.result(0, "ok");
            } catch (Throwable t) {
                t.printStackTrace();
                console.result(-1, "failed");
            }
*/

            // 5. about to show desktop
            console.show("starting...");
            console.delay();
        }

        // show application desktop
        display.setCurrent(desktop);
    }

    protected void pauseApp() {
        // anything to do?
    }

    protected void destroyApp(boolean b) throws MIDletStateChangeException {
        // same as answering Yes in Do you want to quit?
        desktop.response(cz.kruch.track.ui.YesNoDialog.YES);
    }

    /*
     * Environment info.
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

    public static boolean isJsr120() {
        return jsr120;
    }

    public static boolean isJsr135() {
        return jsr135;
    }

    public static boolean isVideoCapture() {
        return videoCapture;
    }

    public static boolean isFs() {
        return fs;
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
