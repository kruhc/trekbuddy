// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track;

import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;
import javax.microedition.lcdui.Display;

import java.io.UnsupportedEncodingException;

public class TrackingMIDlet extends MIDlet implements Runnable {
    public static final String APP_TITLE = "TrekBuddy";
    public static final String APP_WWW = "www.trekbuddy.net";
    public static String APP_NAME = APP_TITLE + " (C) 2007 KrUcH";

    private cz.kruch.track.ui.Desktop desktop;

    // system info
//#ifdef __LOG__
    private static boolean logEnabled;
//#endif
    private static String platform;
    private static String flags;

    public static boolean jsr82;
    public static boolean jsr120;
    public static boolean jsr135;
    public static boolean jsr179;
    public static boolean motorola179;

    public static boolean sonyEricsson;
    public static boolean nokia;
    public static boolean wm;
    public static boolean sxg75;
//#ifdef __A780__
    public static boolean a780;
//#endif
//#ifdef __S65__
    public static boolean s65;
//#endif

    public TrackingMIDlet() {
        // init common vars
        try {
            APP_NAME = APP_TITLE + new String(new byte[]{ ' ', (byte) 0xc2, (byte) 0xa9, ' ', '2', '0', '0', '7', ' ', 'K', 'r', 'U', 'c', 'H' }, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // ignored
        }

        // detect environment
        TrackingMIDlet.platform = System.getProperty("microedition.platform");
        TrackingMIDlet.flags = getAppProperty("App-Flags");
//#ifdef __LOG__
        TrackingMIDlet.logEnabled = "true".equals(getAppProperty("Log-Enable"));
//#endif

        // detect runtime capabilities
        try {
            Class.forName("javax.microedition.location.LocationProvider");
            TrackingMIDlet.jsr179 = true;
//#ifdef __LOG__
            System.out.println("* JSR-179");
//#endif
        } catch (Throwable t) {
        }
//#ifdef __A1000__
        try {
            Class.forName("com.motorola.location.PositionSource");
            TrackingMIDlet.motorola179 = true;
//#ifdef __LOG__
            System.out.println("* Motorola-179");
//#endif
        } catch (Throwable t) {
        }
//#endif
        try {
            Class.forName("javax.bluetooth.DiscoveryAgent");
            TrackingMIDlet.jsr82 = true;
//#ifdef __LOG__
            System.out.println("* JSR-82");
//#endif
        } catch (Throwable t) {
        }
        try {
            Class.forName("javax.wireless.messaging.TextMessage");
            TrackingMIDlet.jsr120 = true;
//#ifdef __LOG__
            System.out.println("* JSR-120");
//#endif
        } catch (Throwable t) {
        }
        try {
            Class.forName("javax.microedition.media.control.VideoControl");
            TrackingMIDlet.jsr135 = true;
//#ifdef __LOG__
            System.out.println("* JSR-135");
//#endif
        } catch (Throwable t) {
        }

        // detect brand/device
        sxg75 = "SXG75".equals(platform);
        nokia = platform.startsWith("Nokia");
        sonyEricsson = System.getProperty("com.sonyericsson.imei") != null;
        wm = platform.startsWith("Windows CE");
        boolean siemens = System.getProperty("com.siemens.IMEI") != null;
//#ifdef __A780__
        a780 = "j2me".equals(platform);
//#endif
//#ifdef __S65__
        s65 = "S65".equals(platform);
//#endif

        // setup environment
        if (hasFlag("fs_skip_bug")) {
//#ifdef __LOG__
            System.out.println("* fs skip-bug feature on");
//#endif
            com.ice.tar.TarInputStream.useSkipBug = true;
        }
        if (hasFlag("fs_no_available_lie")) {
//#ifdef __LOG__
            System.out.println("* fs no-available-lie feature on");
//#endif
            cz.kruch.j2se.io.BufferedInputStream.useAvailableLie = false;
        }
        if (hasFlag("fs_no_reset") || sxg75) {
//#ifdef __LOG__
            System.out.println("* fs no-reset feature on");
//#endif
            cz.kruch.track.maps.Map.useReset = false;
        }
        if (hasFlag("ui_no_partial_flush") || sxg75 || a780 || siemens || wm) {
//#ifdef __LOG__
            System.out.println("* ui no-partial-flush feature on");
//#endif
            cz.kruch.track.ui.Desktop.partialFlush = false;
        }
//#ifdef __S65__
        if (s65) {
            cz.kruch.j2se.io.BufferedInputStream.useAvailableLie = false;
            com.ice.tar.TarInputStream.useSkipBug = true;
        }
//#endif
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
        if (b) {
            // same as answering "Yes" in "Do you want to quit?"
            desktop.response(cz.kruch.track.ui.YesNoDialog.YES);
        } else {
            throw new MIDletStateChangeException();
        }
    }

    public void run() {
        // fit static images into video memory
        boolean imagesLoaded;
        try {
            System.gc();
            cz.kruch.track.ui.NavigationScreens.initialize();
            imagesLoaded = true;
        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
            imagesLoaded = false;
        }

        // load configuration
        boolean configLoaded;
        try {
            cz.kruch.track.configuration.Config.initialize();
            configLoaded = true;
        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
            configLoaded = false;
        }

        // detect file API
        api.file.File.detect(sxg75 || hasFlag("fs_traverse_bug"));

        // customize UI
        int customized = 0;
        try {
            customized = cz.kruch.track.ui.NavigationScreens.customize();
        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
            customized = -1;
        }

        // create desktop canvas
        desktop = new cz.kruch.track.ui.Desktop(this);

        // init helpers
//        cz.kruch.track.util.ExtraMath.initialize();
        cz.kruch.track.configuration.Config.initDatums(this);
        cz.kruch.track.configuration.Config.useDatum(cz.kruch.track.configuration.Config.geoDatum);
        if (platform.indexOf("SunMicrosystems_wtk") == -1) {
            cz.kruch.track.ui.nokia.DeviceControl.initialize();
        }

        // init environment from configuration
        cz.kruch.j2se.io.BufferedInputStream.useAvailableLie = cz.kruch.track.configuration.Config.optimisticIo;
//#ifdef __LOG__
        System.out.println("* use available lie? " + cz.kruch.j2se.io.BufferedInputStream.useAvailableLie);
//#endif

        // custom device handling
        if (getAppProperty("GPS-Connection-URL") != null) {
            cz.kruch.track.configuration.Config.btServiceUrl = getAppProperty("GPS-Connection-URL");
        }
        if (getAppProperty("GPS-Device-Name") != null) {
            cz.kruch.track.configuration.Config.btDeviceName = getAppProperty("GPS-Device-Name");
        }
        // broken device handling
        if (getAppProperty("UI-FullScreen-Height") != null) {
            if (cz.kruch.track.configuration.Config.fullscreen) {
                cz.kruch.track.ui.Desktop.fullScreenHeight = Integer.parseInt(getAppProperty("UI-FullScreen-Height"));
            }
        }

        // setup & start desktop
        desktop.setFullScreenMode(cz.kruch.track.configuration.Config.fullscreen);
        desktop.setTitle(APP_TITLE);
        Display.getDisplay(this).setCurrent(desktop);
        desktop.boot(imagesLoaded, configLoaded, customized);
    }

    /*
     * Environment info.
     */

//#ifdef __LOG__
    public static boolean isLogEnabled() {
        return logEnabled;
    }
//#endif

    public static String getPlatform() {
        return platform;
    }

    public static String getFlags() {
        return flags;
    }

    public static boolean hasFlag(String flag) {
        return flags == null ? false : flags.indexOf(flag) > -1;
    }

    public static boolean isFs() {
        return api.file.File.fsType > api.file.File.FS_UNKNOWN;
    }

    public static boolean hasPorts() {
        return System.getProperty("microedition.commports") != null && System.getProperty("microedition.commports").length() > 0;
    }

    public static boolean supportsVideoCapture() {
        return jsr135 && "true".equals(System.getProperty("supports.video.capture"));
    }
}
