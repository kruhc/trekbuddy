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

import cz.kruch.track.configuration.Config;

import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;
import javax.microedition.lcdui.Display;
import javax.microedition.io.Connector;
import java.util.Hashtable;
import java.io.IOException;
import java.io.InputStream;

/**
 * Main MIDlet.
 *
 * @author Ales Pour <kruhc@seznam.cz> 
 */
public class TrackingMIDlet extends MIDlet implements Runnable {

    /** application title */
    public static final String APP_TITLE = "TrekBuddy";

    // system info

    private static String platform;
    private static String flags;

    public static boolean jsr82, jsr120, jsr135, jsr179, motorola179, comm;
    public static boolean sonyEricsson, nokia, siemens, wm, rim;
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
//#ifdef __LOG__
        logEnabled = hasFlag("log_enable");
//#endif

        // detect brand/device
        nokia = platform.startsWith("Nokia");
        sonyEricsson = System.getProperty("com.sonyericsson.imei") != null;
        siemens = System.getProperty("com.siemens.IMEI") != null;
        wm = platform.startsWith("Windows CE") || platform.startsWith("Palm OS");
        rim = platform.startsWith("RIM");
        sxg75 = "SXG75".equals(platform);
//#ifdef __A780__
        a780 = "j2me".equals(platform);
//#endif
//#ifdef __S65__
        s65 = "S65".equals(platform);
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
        try {
            Class.forName("javax.microedition.io.CommConnection");
            TrackingMIDlet.comm = true;
//#ifdef __LOG__
            System.out.println("* CommConnection");
//#endif
        } catch (Throwable t) {
        }

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
    private cz.kruch.track.ui.Desktop desktop;

    protected void startApp() throws MIDletStateChangeException {
        if (running) {
            return;
        }
        running = true;
        (new Thread(this)).start();
    }

    protected void pauseApp() {
        // diagnostics
        pauses++;

        // anything else to do?
    }

    protected void destroyApp(boolean b) throws MIDletStateChangeException {
        if (b) {
            // same as answering "Yes" in "Do you want to quit?"
            desktop.response(cz.kruch.track.ui.YesNoDialog.YES);
        } else {
            // refuse
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

        // initialize file API
        api.file.File.initialize(sxg75 || hasFlag("fs_traverse_bug"));

        // customize UI
        int customized = 0;
        if (isFs()) {
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
        int localized = 0;
        try {
            localized = localization();
        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
            localized = -1;
        }

        // create desktop canvas
        desktop = new cz.kruch.track.ui.Desktop(this);

        // init helpers and 'singletons'
        cz.kruch.track.configuration.Config.initDatums(this);
        cz.kruch.track.configuration.Config.useDatum(cz.kruch.track.configuration.Config.geoDatum);
        cz.kruch.track.ui.nokia.DeviceControl.initialize();
        cz.kruch.track.ui.Waypoints.initialize(desktop);

        // init environment from configuration
        cz.kruch.j2se.io.BufferedInputStream.useAvailableLie = cz.kruch.track.configuration.Config.optimisticIo;
//#ifdef __LOG__
        System.out.println("* use available lie? " + cz.kruch.j2se.io.BufferedInputStream.useAvailableLie);
//#endif

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
        desktop.setTitle(APP_TITLE);
        Display.getDisplay(this).setCurrent(desktop);
        desktop.boot(imagesLoaded, configLoaded, customized, localized);
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
        return comm; // System.getProperty("microedition.commports") != null && System.getProperty("microedition.commports").length() > 0;
    }

    public static boolean supportsVideoCapture() {
        return jsr135 && "true".equals(System.getProperty("supports.video.capture"));
    }

    /*
     * L10n.
     */

    public static final String MENU_START       = "menu.start";
    public static final String MENU_STOP        = "menu.stop";
    public static final String MENU_PAUSE       = "menu.pause";
    public static final String MENU_CONTINUE    = "menu.continue";
    public static final String MENU_LOADMAP     = "menu.loadmap";
    public static final String MENU_LOADATLAS   = "menu.loadatlas";
    public static final String MENU_SETTINGS    = "menu.settings";
    public static final String MENU_INFO        = "menu.info";
    public static final String MENU_EXIT        = "menu.exit";

    private static final Hashtable table = new Hashtable(16);

    private static int localization() throws IOException {
        int result = 0;

        InputStream in = null;
        try {
            in = Connector.openInputStream(Config.getFolderResources() + "language.txt");
            result++;
        } catch (Exception e) {
            // ignore
        }
        if (in == null) {
            in = TrackingMIDlet.class.getResourceAsStream("/resources/language.txt");
        }

        cz.kruch.track.io.LineReader reader = null;
        StringBuffer sb = null;

        try {
            reader = new cz.kruch.track.io.LineReader(in);
            String entry = reader.readLine(false);
            while (entry != null) {
                if (!entry.startsWith("#")) {
                    final int i = entry.indexOf('=');
                    if (i > -1) {
                        String key = entry.substring(0, i);
                        String value = entry.substring(i + 1);
                        if (value.indexOf('\\') > -1) {
                            if (sb == null) {
                                sb = new StringBuffer(24);
                            }
                            try {
                                value = convert(value, sb);
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                        table.put(key, value);
                    }
                }
                entry = reader.readLine(false);
            }
        } finally {
            // close reader (closes the file stream)
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        return result;
    }

    public static String resolve(String key) {
        String value = (String) table.get(key);
        if (value == null) {
            return key;
        }
        return value;
    }

    private static String convert(String value, StringBuffer sb) {
        sb.delete(0, sb.length());
        for (int N = value.length(), i = 0; i < N; ) {
            char c = value.charAt(i++);
            if (c == '\\') {
                c = value.charAt(i++);
                if (c == 'u') {
                    int unicode = 0;
        		    for (int j = 4; --j >= 0; ) {
		                c = value.charAt(i++);
                        switch (c) {
                            case '0':
                            case '1':
                            case '2':
                            case '3':
                            case '4':
                            case '5':
                            case '6':
                            case '7':
                            case '8':
                            case '9':
                                unicode = (unicode << 4) + c - '0';
                            break;
                            case 'a':
                            case 'b':
                            case 'c':
                            case 'd':
                            case 'e':
                            case 'f':
                                unicode = (unicode << 4) + 10 + c - 'a';
                            break;
                            case 'A':
                            case 'B':
                            case 'C':
                            case 'D':
                            case 'E':
                            case 'F':
                                unicode = (unicode << 4) + 10 + c - 'A';
                            break;
                            default:
                                throw new IllegalArgumentException("Malformed \\uxxxx encoding.");
                        }
                    }
                    sb.append((char) unicode);
                } else {
                    sb.append('\\').append(c);
                }
            } else
                sb.append(c);
        }
        return sb.toString();
    }
}
