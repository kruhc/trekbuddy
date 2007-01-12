// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track;

import cz.kruch.track.ui.NavigationScreens;

import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Image;

import java.io.UnsupportedEncodingException;
import java.io.IOException;

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
    private static boolean fs;
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

    // image cache
    public static Image/*[]*/ courses, courses2;
    public static Image waypoint;
    public static Image/*[]*/ crosshairs;
    public static Image/*[]*/ providers;

    // common vars
    public static String SIGN = "^";
    public static final double SINS[] = new double[90 + 1];
    public static final int[] ranges = {
        500, 250, 100, 50, 25, 10, 5
    };
    public static final String[] rangesStr = {
        "500 m", "250 m", "100 m", "50 m", "25 m", "10 m", "5 m"
    };
    public static final String[] nStr = {
         "0*",  "1*",  "2*",  "3*",  "4*",  "5*",  "6*",  "7*",  "8*",  "9*",
        "10*", "11*", "12*", "13*", "14*", "15*", "16*", "17*", "18*", "19*",
        "20*", "21*", "22*", "23*", "24*"
    };

    public TrackingMIDlet() {
        this.desktop = null;

        final int FS_UNKNOWN = -1;
        final int FS_NONE    = 0;
        final int FS_JSR75   = 1;
        final int FS_SIEMENS = 2;
        final int FS_SXG75   = 3;
        final int FS_MOTOROLA = 4;

        // init common vars
        try {
            APP_NAME = cz.kruch.track.ui.Desktop.APP_TITLE + " " + new String(new byte[]{ (byte) 0xc2, (byte) 0xa9 }, "UTF-8") + " " + "2006 KrUcH";
            SIGN = new String(new byte[]{ (byte) 0xc2, (byte) 0xb0 }, "UTF-8");
        } catch (UnsupportedEncodingException e) {
        }
        for (int i = 0; i <= 90; i++) {
            SINS[i] = Math.sin(Math.toRadians(i));
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

        // fs type
        int fsType = FS_UNKNOWN;
        try {
            Class clazz = Class.forName("javax.microedition.io.file.FileConnection");
            fsType = FS_JSR75;
//#ifdef __LOG__
            System.out.println("* JSR-75/FC");
//#endif
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
        if (fsType == FS_UNKNOWN) {
            try {
                Class clazz = Class.forName("com.motorola.io.FileConnection");
                fsType = FS_MOTOROLA;
            } catch (ClassNotFoundException e) {
            } catch (NoClassDefFoundError e) {
            }
        }
        if (fsType == FS_UNKNOWN) {
            fsType = FS_NONE;
        }
        TrackingMIDlet.fs = fsType > FS_NONE;

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

    protected void startApp() throws MIDletStateChangeException {
        if (desktop == null) {
            (new Thread(this)).start();
        }
    }

    protected void pauseApp() {
        // anything to do?
    }

    protected void destroyApp(boolean b) throws MIDletStateChangeException {
        // same as answering Yes in Do you want to quit?
        desktop.response(cz.kruch.track.ui.YesNoDialog.YES);
    }

    public void run() {
        // init image cache
        try {
            courses = Image.createImage("/resources/courses.png");
            courses2 = Image.createImage("/resources/courses2.png");
            waypoint = Image.createImage("/resources/wpt.png");
            crosshairs = Image.createImage("/resources/crosshairs.png");
            providers = Image.createImage("/resources/bullets.png");
            NavigationScreens.initialize();
        } catch (IOException e) {
//                throw new MIDletStateChangeException(e.toString());
        }

        Display display = Display.getDisplay(this);

        // setup environment
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
        cz.kruch.track.configuration.Config.initDatums(this);
        cz.kruch.track.util.Mercator.initialize();

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
        return fs;
    }

    public static boolean isSxg75() {
        return sxg75;
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

    public static int asin(double sina) {
        if (sina < 0D) {
            throw new IllegalArgumentException("Invalid sin(a) value: " + sina);
        }
        if (sina > 1D) {
            return 90;
        }

        float step = 23;
        int direction = 0;
        int cycles = 0;
        double[] sins = SINS;
        int i = 45;

        for ( ; i >= 0 && i <= 90; ) {
            boolean b;
            if (sins[i] > sina) {
                b = direction != 0 && direction != -1;
                direction = -1;
                i -= step;
            } else if (sins[i] < sina) {
                b = direction != 0 && direction != 1;
                direction = 1;
                i += step;
            } else {
                return i;
            }

            if (step == 1 && b) {
                return i;
            }

            if (!b) {
                step /= 2;
            } else {
                step--;
            }
            if (step < 1) {
                step = 1;
            }

            if (cycles++ > 25) {
                throw new IllegalStateException("asin(a) failure - too many cycles");
            }
        }

        return i;
    }
}
