// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track;

import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Image;

import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.util.Vector;

public class TrackingMIDlet extends MIDlet implements Runnable {
    private static String APP_NAME = cz.kruch.track.ui.Desktop.APP_TITLE + " (C) 2006 KrUcH";
    private static String APP_WWW = "www.trekbuddy.net";

    public static final int FS_UNKNOWN = -1;
    public static final int FS_NONE    = 0;
    public static final int FS_JSR75   = 1;
    public static final int FS_SIEMENS = 2;
    public static final int FS_SXG75   = 3;

    private static int fsType = FS_UNKNOWN;

    private cz.kruch.track.ui.Desktop desktop;

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
//#ifdef __S65__
    private static boolean s65;
//#endif
    private static boolean sonyEricsson;
    private static int numAlphaLevels = 2;

    // image cache
    public static Image/*[]*/ courses, courses2;
    public static Image waypoint;
    /*public static Image point, point2, pointAvg;*/
    public static Image/*[]*/ crosshairs;
    public static Image/*[]*/ providers;

    // common vars
    public static String SIGN = "^";
    public static final Vector KNOWN_EXTENSIONS = new Vector();
    public static final double SINS[] = new double[90 + 1];
    public static double[][] ranges;
    public static String[] rangesStr;

    public TrackingMIDlet() {
        this.desktop = null;

        // init common vars
        try {
            APP_NAME = cz.kruch.track.ui.Desktop.APP_TITLE + " " + new String(new byte[]{ (byte) 0xc2, (byte) 0xa9 }, "UTF-8") + " " + "2006 KrUcH";
            SIGN = new String(new byte[]{ (byte) 0xc2, (byte) 0xb0 }, "UTF-8");
        } catch (UnsupportedEncodingException e) {
        }
        KNOWN_EXTENSIONS.addElement("gmi");
        KNOWN_EXTENSIONS.addElement("map");
        KNOWN_EXTENSIONS.addElement("xml");
        KNOWN_EXTENSIONS.addElement("j2n");
        for (int N = SINS.length, i = 0; i < N; i++) {
            SINS[i] = Math.sin(Math.toRadians(i));
        }

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
        if (fsType == FS_UNKNOWN) {
            fsType = FS_NONE;
        }
        TrackingMIDlet.fs = fsType > FS_NONE;

        sxg75 = "SXG75".equals(platform);
//#ifdef __S65__
        s65 = "S65".equals(platform);
//#endif
        sonyEricsson = System.getProperty("com.sonyericsson.imei") != null;

        // setup environment
        if (hasFlag("fs_read_skip") || sonyEricsson) {
            System.out.println("* fs read-skip feature on");
            com.ice.tar.TarInputStream.useReadSkip = true;
        }
        if (hasFlag("fs_no_available_lie") || s65) {
            System.out.println("* fs no-available_lie feature on");
            cz.kruch.j2se.io.BufferedInputStream.useAvailableLie = false;
        }
        if (hasFlag("fs_no_reset")) {
            System.out.println("* fs no-reset feature on");
            cz.kruch.track.maps.Map.useReset = false;
        }
        if (hasFlag("ui_no_partial_flush") || sxg75) {
            System.out.println("* ui no-partial-flush feature on");
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
/*
            courses = new Image[]{
                Image.createImage("/resources/course_0.png"),
                Image.createImage("/resources/course_10.png"),
                Image.createImage("/resources/course_20.png"),
                Image.createImage("/resources/course_30.png"),
                Image.createImage("/resources/course_40.png"),
                Image.createImage("/resources/course_50.png"),
                Image.createImage("/resources/course_60.png"),
                Image.createImage("/resources/course_70.png"),
                Image.createImage("/resources/course_80.png")
            };
*/
            courses = Image.createImage("/resources/courses.png");
            courses2 = Image.createImage("/resources/courses2.png");
            waypoint = Image.createImage("/resources/wpt.png");
/*
            point = Image.createImage("/resources/lpt_green.png");
            point2 = Image.createImage("/resources/lpt_blue.png");
            pointAvg = Image.createImage("/resources/lpt_yellow.png");
            crosshairs = new Image[] {
                Image.createImage("/resources/crosshair_tp_full.png"),
                Image.createImage("/resources/crosshair_tp_white.png"),
                Image.createImage("/resources/crosshair_tp_grey.png")
            };
            providers = new Image[]{
                Image.createImage("/resources/s_blue.png"),
                Image.createImage("/resources/s_green.png"),
                Image.createImage("/resources/s_orange.png"),
                Image.createImage("/resources/s_red.png")
            };
*/
            crosshairs = Image.createImage("/resources/crosshairs.png");
            providers = Image.createImage("/resources/bullets.png");
        } catch (IOException e) {
//                throw new MIDletStateChangeException(e.toString());
        }

        // init constants
        ranges = new double[][]{
/*  0^ */   { 0.0045, 0.00225, 0.0009, 0.00045, 0.000225, 0.00009, 0.000045 },
/* 10^ */   { 0.0045694, 0.0022847, 0.0009139, 0.00045694, 0.00022847, 0.00009139, 0.000045694 },
/* 20^ */   { 0.0047889, 0.002394, 0.000958, 0.000478, 0.0002394, 0.0000958, 0.0000478 },
/* 30^ */   { 0.0051958, 0.0025981, 0.0010392, 0.00051958, 0.00025981, 0.00010392, 0.000051958 },
/* 40^ */   { 0.0058778, 0.0029444, 0.001175, 0.0005872, 0.0002936, 0.0001175, 0.0000589 },
/* 50^ */   { 0.007, 0.0035, 0.0014, 0.0007, 0.00035, 0.00014, 0.00007 },
/* 60^ */   { 0.009, 0.0045, 0.0018, 0.0009, 0.00045, 0.00018, 0.00009 },
/* 70^ */   { 0.0131583, 0.0065778, 0.0026333, 0.0013158, 0.0006577, 0.0002633, 0.00013158 },
/* 80^ */   { 0.02597, 0.012958, 0.005194, 0.002597, 0.0012958, 0.0005194, 0.0002597 },
/* 90^ */   { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 }
            };
        rangesStr = new String[]{
                "500 m", "250 m", "100 m", "50 m", "25 m", "10 m", "5 m"
        };

        Display display = Display.getDisplay(this);

        // setup environment
        TrackingMIDlet.numAlphaLevels = display.numAlphaLevels();

        // 1. show boot screen
        cz.kruch.track.ui.Console console = new cz.kruch.track.ui.Console();
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

    public static boolean isVideoCapture() {
        return videoCapture;
    }

    public static boolean isFs() {
        return fs;
    }

    public static boolean isSxg75() {
        return sxg75;
    }

//#ifdef __S65__
    public static boolean isS65() {
        return s65;
    }
//#endif    

    public static boolean isSonyEricsson() {
        return sonyEricsson;
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
