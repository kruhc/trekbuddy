// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track;

import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;
import javax.microedition.lcdui.Display;

import cz.kruch.track.ui.Desktop;
import cz.kruch.track.ui.Console;
import cz.kruch.track.configuration.Config;

import java.io.UnsupportedEncodingException;

public class TrackingMIDlet extends MIDlet {
    private static String APP_NAME = Desktop.APP_TITLE + " (C) 2006 KrUcH";

    public static final int FS_UNKNOWN = -1;
    public static final int FS_NONE    = 0;
    public static final int FS_JSR75   = 1;
    public static final int FS_SIEMENS = 2;
    public static final int FS_SXG75   = 3;

    private static int fsType = FS_UNKNOWN;

    private Desktop desktop;

    // system info
    private static String platform;
    private static String flags;
    private static boolean logEnabled;
    private static boolean jsr179;
    private static boolean jsr82;
    private static boolean jsr120;
    private static boolean jsr135;
    private static boolean videoCapture;
    private static boolean fs;
    private static boolean sxg75;
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
        TrackingMIDlet.platform = System.getProperty("microedition.platform");
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
        fsType = FS_UNKNOWN;
//#ifndef __NO_FS__
        try {
            Class clazz = Class.forName("javax.microedition.io.file.FileConnection");
            fsType = FS_JSR75;
        } catch (ClassNotFoundException e) {
        } catch (NoClassDefFoundError e) {
        }
        if (fsType == FS_UNKNOWN) {
            try {
                Class clazz = Class.forName("com.siemens.mp.io.file.FileConnection");
                fsType = FS_SIEMENS;
            } catch (ClassNotFoundException e) {
            } catch (NoClassDefFoundError e) {
            }
        }
//#endif
        if (fsType == FS_UNKNOWN) {
            fsType = FS_NONE;
        }
        System.out.println("* fs type: " + fsType);
        TrackingMIDlet.fs = fsType > FS_NONE;

        // setup environment
//#ifndef __NO_FS__
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
//#endif
        sxg75 = "SXG75".equals(platform);
        if (hasFlag("ui_no_partial_flush") || sxg75) {
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

    public static boolean isSxg75() {
        return sxg75;
    }

    public static String getPlatform() {
        return platform;
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
