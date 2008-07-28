/*
 * Copyright 2006-2007 Ales Pour <kruhc@seznam.cz>.
 * All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package cz.kruch.track;

import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;
import javax.microedition.lcdui.Display;

/**
 * Main MIDlet.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public class TrackingMIDlet extends MIDlet implements Runnable {

    /** application title/name */
    public static final String APP_TITLE = "TrekBuddy";

    // system info

    private static String platform, flags;

    public static String version;
    public static boolean jsr82, jsr120, jsr135, jsr179, motorola179, comm;
    public static boolean sonyEricsson, nokia, siemens, lg, motorola;
    public static boolean wm, jbed, intent, palm, rim, symbian, uiq, brew;
    public static boolean sxg75, a780, s65;

    // diagnostics

    public static int pauses;

//#ifdef __LOG__
    private static boolean logEnabled;
//#endif

    private static final String JAD_GPS_CONNECTION_URL      = "GPS-Connection-URL";
    private static final String JAD_GPS_DEVICE_NAME         = "GPS-Device-Name";
    private static final String JAD_UI_FULL_SCREEN_HEIGHT   = "UI-FullScreen-Height";
    private static final String JAD_UI_HAS_REPEAT_EVENTS    = "UI-HasRepeatEvents";
    private static final String JAD_APP_FLAGS               = "App-Flags";

    public TrackingMIDlet() {
        // detect environment
        platform = System.getProperty("microedition.platform");
        flags = getAppProperty(JAD_APP_FLAGS);
        if (flags == null) {
            flags = System.getProperty("trekbuddy.app-flags");
        }
        version = getAppProperty("MIDlet-Version");
//#ifdef __LOG__
        logEnabled = hasFlag("log_enable");
//#endif

        // detect brand/device
//#ifdef __RIM__
        rim = platform.startsWith("RIM");
//#else
        nokia = platform.startsWith("Nokia");
        sonyEricsson = System.getProperty("com.sonyericsson.imei") != null;
        siemens = System.getProperty("com.siemens.IMEI") != null || System.getProperty("com.siemens.mp.imei") != null;
        motorola = System.getProperty("com.motorola.IMEI") != null;
        lg = platform.startsWith("LG");
        wm = platform.startsWith("Windows CE");
        jbed = platform.startsWith("Jbed");
        intent = platform.startsWith("intent");
        palm = platform.startsWith("Palm OS");
        sxg75 = "SXG75".equals(platform);
        brew = sxg75 || "BENQ-M7".equals(platform);
        a780 = "j2me".equals(platform);
        s65 = "S65".equals(platform);
//#endif

        // detect runtime capabilities
        try {
            Class.forName("javax.microedition.location.LocationProvider");
            jsr179 = true;
//#ifdef __LOG__
            System.out.println("* JSR-179");
//#endif
        } catch (Throwable t) {
        }
        try {
            Class.forName("javax.bluetooth.DiscoveryAgent");
            jsr82 = true;
//#ifdef __LOG__
            System.out.println("* JSR-82");
//#endif
        } catch (Throwable t) {
        }
        try {
            Class.forName("javax.wireless.messaging.TextMessage");
            jsr120 = true;
//#ifdef __LOG__
            System.out.println("* JSR-120");
//#endif
        } catch (Throwable t) {
        }
        try {
            Class.forName("javax.microedition.media.control.VideoControl");
            jsr135 = true;
//#ifdef __LOG__
            System.out.println("* JSR-135");
//#endif
        } catch (Throwable t) {
        }
        try {
            Class.forName("javax.microedition.io.CommConnection");
            comm = true;
//#ifdef __LOG__
            System.out.println("* CommConnection");
//#endif
        } catch (Throwable t) {
        }

//#ifdef __ALL__
        /* try Motorola-specific Location API */
        try {
            Class.forName("com.motorola.location.PositionSource");
            motorola179 = true;
//#ifdef __LOG__
            System.out.println("* Motorola-179");
//#endif
        } catch (Throwable throwable) {
        }
        /* detect Symbian */
        try {
            Class.forName("com.symbian.midp.io.protocol.http.Protocol");
            symbian = true;
//#ifdef __LOG__
            System.out.println("* Symbian");
//#endif
        } catch (Throwable t) {
        }
        if (sonyEricsson && symbian) {
            try {
                Class.forName("java.lang.ref.Reference");
                uiq = true;
            } catch (Throwable t) {
            }
        }
//#endif
    }

    private boolean running;
    private cz.kruch.track.ui.Desktop desktop;

    protected void startApp() throws MIDletStateChangeException {
        if (!running) {
            running = true;
            (new Thread(this)).start();
        }
    }

    protected void pauseApp() {
        // diagnostics
        pauses++;

        // anything else to do?
    }

    protected void destroyApp(boolean unconditional) throws MIDletStateChangeException {
        if (unconditional) {
            // same as answering "Yes" in "Do you want to quit?"
            desktop.response(cz.kruch.track.ui.YesNoDialog.YES, desktop);
        } else {
            // refuse
            throw new MIDletStateChangeException();
        }
    }

    public void run() {
        // fit static images into video memory
        int imgcached;
        try {
            System.gc();
            cz.kruch.track.ui.NavigationScreens.initialize();
            imgcached = 1;
        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
            imgcached = -1;
        }

        // load configuration
        int configured;
        try {
            cz.kruch.track.configuration.Config.initialize();
            configured = 1;
        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
            configured = -1;
        }

        // initialize file API
        api.file.File.initialize(sxg75 || hasFlag("fs_traverse_bug"));
//#ifdef __LOG__
        System.out.println("* FsType: " + api.file.File.fsType);
//#endif

        // customize UI
        int customized = 0;
        if (api.file.File.isFs()) {
            // gc - loading of images ahead...
            System.gc();
            try {
                customized = cz.kruch.track.ui.NavigationScreens.customize();
            } catch (Throwable t) {
//#ifdef __LOG__
                t.printStackTrace();
//#endif
                customized = -1;
            }
        }

        // L10n
        int localized;
        try {
            localized = Resources.initialize();
        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
            localized = -1;
        }

        // key map
        int keysmapped;
        try {
            keysmapped = Resources.keymap();
        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
            keysmapped = -1;
        }

        // create desktop canvas
        desktop = new cz.kruch.track.ui.Desktop(this);

        // init helpers and 'singletons'
        cz.kruch.track.configuration.Config.initDatums(this);
        cz.kruch.track.ui.nokia.DeviceControl.initialize();
        cz.kruch.track.ui.Waypoints.initialize(desktop);
        cz.kruch.track.util.Mercator.initialize();

        // cleanup after initialization?
        System.gc();

        // setup environment
        if (hasFlag("fs_skip_bug") || siemens /*|| symbian*/) {
//#ifdef __LOG__
            System.out.println("* fs skip-bug feature on");
//#endif
            cz.kruch.track.maps.Map.useSkip = false;
        }
        if (hasFlag("fs_no_reset") || sxg75 /*|| symbian*/) {
//#ifdef __LOG__
            System.out.println("* fs no-reset feature on");
//#endif
            cz.kruch.track.maps.Map.useReset = false;
        }
        if (!hasFlag("ui_no_partial_flush") && (nokia || sonyEricsson)) {
//#ifdef __LOG__
            System.out.println("* ui partial-flush feature on");
//#endif
            cz.kruch.track.ui.Desktop.partialFlush = true;
        }
        if (hasFlag("provider_o2_germany")) {
            cz.kruch.track.configuration.Config.o2provider = true;
        }
        if (sxg75) {
            cz.kruch.track.ui.NavigationScreens.useCondensed = 1;
        }

        // custom device handling
        if (getAppProperty(JAD_GPS_CONNECTION_URL) != null) {
            cz.kruch.track.configuration.Config.btServiceUrl = getAppProperty(JAD_GPS_CONNECTION_URL);
        }
        if (getAppProperty(JAD_GPS_DEVICE_NAME) != null) {
            cz.kruch.track.configuration.Config.btDeviceName = getAppProperty(JAD_GPS_DEVICE_NAME);
        }
        // broken device handling
        if (getAppProperty(JAD_UI_FULL_SCREEN_HEIGHT) != null) {
            if (cz.kruch.track.configuration.Config.fullscreen) {
                cz.kruch.track.ui.Desktop.fullScreenHeight = Integer.parseInt(getAppProperty(JAD_UI_FULL_SCREEN_HEIGHT));
            }
        }
        if (getAppProperty(JAD_UI_HAS_REPEAT_EVENTS) != null) {
            cz.kruch.track.ui.Desktop.hasRepeatEvents = "true".equals(getAppProperty(JAD_UI_HAS_REPEAT_EVENTS));
        }

        // setup & start desktop
        desktop.setFullScreenMode(cz.kruch.track.configuration.Config.fullscreen);
        desktop.setTitle(null);
        Display.getDisplay(this).setCurrent(desktop);
        desktop.boot(imgcached, configured, customized, localized, keysmapped);
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

    public static boolean hasFlag(final String flag) {
        return flags != null && flags.indexOf(flag) > -1;
    }

    public static boolean hasPorts() {
        return comm; // System.getProperty("microedition.commports") != null && System.getProperty("microedition.commports").length() > 0;
    }

    public static boolean supportsVideoCapture() {
        return jsr135 && "true".equals(System.getProperty("supports.video.capture"));
    }
}
