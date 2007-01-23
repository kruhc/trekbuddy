// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track;

import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;
import javax.microedition.lcdui.Display;

import java.io.UnsupportedEncodingException;

public class TrackingMIDlet extends MIDlet implements Runnable {
    private static String APP_NAME = cz.kruch.track.ui.Desktop.APP_TITLE + " (C) 2006 KrUcH";
    private static String APP_WWW = "www.trekbuddy.net";

    private cz.kruch.track.ui.Desktop desktop;

    // system info
    private static String platform;
    private static String flags;
    private static boolean logEnabled;
    private static boolean jsr179;
    private static boolean jsr82;
    private static boolean jsr120;
    private static boolean jsr135;
    private static boolean sxg75;
//#ifdef __A780__
    private static boolean a780;
//#endif
//#ifdef __S65__
    private static boolean s65;
//#endif
    private static boolean sonyEricsson;
    private static boolean nokia;
    private static int numAlphaLevels = 2;

    public TrackingMIDlet() {
        this.desktop = null;

        // init common vars
        try {
            APP_NAME = cz.kruch.track.ui.Desktop.APP_TITLE + " " + new String(new byte[]{ (byte) 0xc2, (byte) 0xa9 }, "UTF-8") + " " + "2006 KrUcH";
        } catch (UnsupportedEncodingException e) {
        }

        // detect environment
        TrackingMIDlet.platform = System.getProperty("microedition.platform");
        TrackingMIDlet.flags = getAppProperty("App-Flags");
//#ifdef __LOG__
        TrackingMIDlet.logEnabled = "true".equals(getAppProperty("Log-Enable"));
//#endif
        try {
            Class clazz = Class.forName("javax.microedition.location.LocationProvider");
            TrackingMIDlet.jsr179 = true;
//#ifdef __LOG__
            System.out.println("* JSR-179");
//#endif
        } catch (ClassNotFoundException e) {
        } catch (NoClassDefFoundError e) {
        }
        try {
            Class clazz = Class.forName("javax.bluetooth.DiscoveryAgent");
            TrackingMIDlet.jsr82 = true;
//#ifdef __LOG__
            System.out.println("* JSR-82");
//#endif
        } catch (ClassNotFoundException e) {
        } catch (NoClassDefFoundError e) {
        }
        try {
            Class clazz = Class.forName("javax.wireless.messaging.TextMessage");
            TrackingMIDlet.jsr120 = true;
//#ifdef __LOG__
            System.out.println("* JSR-120");
//#endif
        } catch (ClassNotFoundException e) {
        } catch (NoClassDefFoundError e) {
        }
        try {
            Class clazz = Class.forName("javax.microedition.media.Manager");
            TrackingMIDlet.jsr135 = "true".equals(System.getProperty("supports.video.capture"));
//#ifdef __LOG__
            System.out.println("* JSR-135");
//#endif
        } catch (ClassNotFoundException e) {
        } catch (NoClassDefFoundError e) {
        }

        // detect brand/device
        sxg75 = "SXG75".equals(platform);
        nokia = platform.startsWith("Nokia");
        sonyEricsson = System.getProperty("com.sonyericsson.imei") != null;
//#ifdef __A780__
        a780 = "j2me".equals(platform);
//#endif
//#ifdef __S65__
        s65 = "S65".equals(platform);
//#endif

        // fs type
//#ifdef __JSR75__
        if (true) {
            try {
                Class clazz = Class.forName("javax.microedition.io.file.FileConnection");
                api.file.File.fsType = api.file.File.FS_JSR75;
            } catch (ClassNotFoundException e) {
            } catch (NoClassDefFoundError e) {
            }
        }
//#endif
//#ifdef __S65__
        if (isS65()) {
            if (api.file.File.fsType == api.file.File.FS_UNKNOWN) {
                try {
                    Class clazz = Class.forName("com.siemens.mp.io.file.FileConnection");
                    api.file.File.fsType = api.file.File.FS_SIEMENS;
                } catch (ClassNotFoundException e) {
                } catch (NoClassDefFoundError e) {
                }
            }
        }
//#endif
//#ifdef __A780__
        if (api.file.File.fsType == api.file.File.FS_UNKNOWN) {
            try {
                Class clazz = Class.forName("com.motorola.io.FileConnection");
                api.file.File.fsType = api.file.File.FS_MOTOROLA;
            } catch (ClassNotFoundException e) {
            } catch (NoClassDefFoundError e) {
            }
        }
//#endif
        if (api.file.File.fsType == api.file.File.FS_UNKNOWN) {
            api.file.File.fsType = api.file.File.FS_NONE;
        }

        // setup environment
        if (hasFlag("fs_read_skip") || sonyEricsson) {
//#ifdef __LOG__
            System.out.println("* fs read-skip feature on");
//#endif
            com.ice.tar.TarInputStream.useReadSkip = true;
        }
        if (hasFlag("fs_no_available_lie")) {
//#ifdef __LOG__
            System.out.println("* fs no-available_lie feature on");
//#endif
            cz.kruch.j2se.io.BufferedInputStream.useAvailableLie = false;
        }
        if (hasFlag("fs_no_reset") || sxg75) {
//#ifdef __LOG__
            System.out.println("* fs no-reset feature on");
//#endif
            cz.kruch.track.maps.Map.useReset = false;
        }
        if (hasFlag("ui_no_partial_flush") || sxg75) {
//#ifdef __LOG__
            System.out.println("* ui no-partial-flush feature on");
//#endif
            cz.kruch.track.ui.Desktop.partialFlush = false;
        }
    }

    private boolean running;

    protected void startApp() throws MIDletStateChangeException {
        if (!running) {
            running = true;
            (new Thread(this)).start();
        }
    }

    protected void pauseApp() {
        // anything to do?
    }

    protected void destroyApp(boolean b) throws MIDletStateChangeException {
        // same as answering "Yes" in "Do you want to quit?"
        desktop.response(cz.kruch.track.ui.YesNoDialog.YES);
    }

    public void run() {
        // init helpers
        cz.kruch.track.ui.NavigationScreens.initialize();
        cz.kruch.track.util.ExtraMath.initialize();
        cz.kruch.track.configuration.Config.initDatums(this);
/*
        cz.kruch.track.util.Mercator.initialize();
*/

        // setup environment
        Display display = Display.getDisplay(this);
        TrackingMIDlet.numAlphaLevels = display.numAlphaLevels();

        // 1. show boot screen
        cz.kruch.track.ui.Console console = new cz.kruch.track.ui.Console(display);
        display.setCurrent(console);
        console.show(APP_NAME);
        console.show(APP_WWW);
        console.show("");
        console.show("initializing...");

        // 2. load configuration
        try {
            console.show("loading config...");
            cz.kruch.track.configuration.Config.getInstance();
            console.result(0, "ok");
        } catch (Throwable t) {
            t.printStackTrace();
            console.result(-1, "failed");
        }

        // 2a. preinit
        cz.kruch.j2se.io.BufferedInputStream.useAvailableLie = cz.kruch.track.configuration.Config.getSafeInstance().isOptimisticIo();
//#ifdef __A780__
        if (a780) {
            cz.kruch.track.ui.Desktop.partialFlush = false;
        }
//#endif
//#ifdef __S65__
        if (s65) {
            cz.kruch.j2se.io.BufferedInputStream.useAvailableLie = false;
            com.ice.tar.TarInputStream.useReadSkip = true;
        }
//#endif
//#ifdef __LOG__
        System.out.println("* use available lie? " + cz.kruch.j2se.io.BufferedInputStream.useAvailableLie);
//#endif
        // broken device handling
        if (getAppProperty("GPS-Connection-URL") != null) {
            cz.kruch.track.configuration.Config.getSafeInstance().setBtServiceUrl(getAppProperty("GPS-Connection-URL"));
        }
        if (getAppProperty("GPS-Device-Name") != null) {
            cz.kruch.track.configuration.Config.getSafeInstance().setBtDeviceName(getAppProperty("GPS-Device-Name"));
        }

        // 3. create desktop
        desktop = new cz.kruch.track.ui.Desktop(this);

        // 4. read default map
        console.show("loading map...");
        try {
            if ("".equals(cz.kruch.track.configuration.Config.getSafeInstance().getMapPath())) {
                desktop.initDefaultMap();
                console.result(1, "skipped");
            } else {
                desktop.initMap();
                console.result(0, "ok");
            }
        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
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
        console = null;

        // show application desktop
        display.setCurrent(desktop);
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

    public static boolean isFs() {
        return api.file.File.fsType > api.file.File.FS_NONE;
    }

    public static boolean isSxg75() {
        return true; //sxg75;
    }

//#ifdef __A780__
    public static boolean isA780() {
        return a780;
    }
//#endif

//#ifdef __S65__
    public static boolean isS65() {
        return s65;
    }
//#endif    

    public static boolean isSonyEricsson() {
        return sonyEricsson;
    }

    public static boolean isNokia() {
        return nokia;
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
