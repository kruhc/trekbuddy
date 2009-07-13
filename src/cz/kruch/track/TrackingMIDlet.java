// @LICENSE@

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
    public static boolean jsr82, jsr120, jsr135, jsr179, jsr234, motorola179, comm;
    public static boolean sonyEricsson, sonyEricssonEx, nokia, siemens, lg, motorola, samsung;
    public static boolean j9, jbed, intent, palm, rim, symbian, s60nd, uiq, brew, android;
    public static boolean sxg75, a780, s65;

    // diagnostics

    public static int pauses;
    public static int state; 

//#ifdef __LOG__
    private static boolean logEnabled;
//#endif

    public static final String JAD_GPS_CONNECTION_URL      = "GPS-Connection-URL";
    public static final String JAD_GPS_DEVICE_NAME         = "GPS-Device-Name";
    public static final String JAD_UI_FULL_SCREEN_HEIGHT   = "UI-FullScreen-Height";
    public static final String JAD_UI_HAS_REPEAT_EVENTS    = "UI-HasRepeatEvents";
    public static final String JAD_APP_FLAGS               = "App-Flags";

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
        System.out.println("* platform is " + platform);
//#endif
//#ifdef __ANDROID__
        logEnabled = true;
//#endif

        // detect brand/device
//#ifdef __RIM__
        rim = true;
//#elifdef __ANDROID__
        android = true;
//#else
        nokia = platform.startsWith("Nokia");
        s60nd = platform.startsWith("Nokia66") || platform.startsWith("NokiaN70");
        sonyEricsson = System.getProperty("com.sonyericsson.imei") != null;
        sonyEricssonEx = sonyEricsson || platform.startsWith("SonyEricsson");
        samsung = platform.startsWith("SAMSUNG") || platform.startsWith("SGH");
        siemens = System.getProperty("com.siemens.IMEI") != null || System.getProperty("com.siemens.mp.imei") != null;
        motorola = System.getProperty("com.motorola.IMEI") != null;
        lg = platform.startsWith("LG");
        j9 = platform.startsWith("Windows CE");
        jbed = platform.startsWith("Jbed");
        intent = platform.startsWith("intent");
        palm = platform.startsWith("Palm OS");
        sxg75 = "SXG75".equals(platform);
        brew = sxg75 || "BENQ-M7".equals(platform);
        a780 = "j2me".equals(platform);
        s65 = "S65".equals(platform);
        // for IntelliJ
        rim = platform.startsWith("RIM");
        android = platform.indexOf("android") > -1;
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
            Class.forName("javax.microedition.amms.control.camera.CameraControl");
            Class.forName("javax.microedition.amms.control.camera.SnapshotControl");
            jsr234 = true;
//#ifdef __LOG__
            System.out.println("* JSR-234");
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
        /* detect UIQ */
        if (sonyEricssonEx) {
            if (symbian) {
                uiq = true;
//#ifdef __LOG__
                System.out.println("* UIQ");
//#endif
            }
        } else { /* detect Jbed */
            try {
                Class.forName("com.jbed.io.CharConvUTF8");
                jbed = true;
//#ifdef __LOG__
                System.out.println("* Jbed");
//#endif
            } catch (Throwable t) {
            }
        }
//#endif

        // init device control (also helps to detect other platforms)
        cz.kruch.track.ui.nokia.DeviceControl.initialize();
    }

    private cz.kruch.track.ui.Desktop desktop;

    protected void startApp() throws MIDletStateChangeException {
//#ifdef __LOG__
        System.out.println("* startApp *");
//#endif
        // initial launch?
        if (state == 0) {
            state = 1;
            (new Thread(this)).start();
        }

        // update state
        state = 1;
    }

    protected void pauseApp() {
        // diagnostics
        pauses++;

        // update state
        state = 2;
    }

    protected void destroyApp(boolean unconditional) throws MIDletStateChangeException {
        if (unconditional) {
            // update state
            state = 3;
            // code path same as after answering "Yes" in "Do you want to quit?"
            desktop.response(cz.kruch.track.ui.YesNoDialog.YES, desktop);
        } else {
            // refuse
            throw new MIDletStateChangeException();
        }
    }

    public void run() {
//#ifdef __LOG__
        System.out.println("* run *");
//#endif
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

        // initialize file API
        api.file.File.initialize(sxg75 || android || hasFlag("fs_traverse_bug"));
//#ifdef __LOG__
        System.out.println("* FsType: " + api.file.File.fsType);
//#endif

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

        // customize UI
        int customized = 0;
        if (cz.kruch.track.configuration.Config.dataDirExists/* && api.file.File.isFs()*/) {
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

        // localization
        int localized;
        try {
            localized = Resources.initialize();
        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
            localized = -1;
        }

        // keymap
        int keysmapped;
        try {
            keysmapped = Resources.keymap();
        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
            keysmapped = -1;
        }

        // init helpers and 'singletons'
        cz.kruch.track.configuration.Config.initDatums(this);
        cz.kruch.track.util.Mercator.initialize();

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
        if (hasFlag("provider_o2_germany")) {
//#ifdef __LOG__
            System.out.println("* O2 Germany provider");
//#endif
            cz.kruch.track.configuration.Config.o2provider = true;
        }
        if (sxg75) {
            cz.kruch.track.ui.NavigationScreens.useCondensed = 1;
        }

        // cleanup after initialization?
        System.gc();

        // create desktop
        desktop = new cz.kruch.track.ui.Desktop(this);

        // custom device handling
        if (getAppProperty(JAD_GPS_CONNECTION_URL) != null) {
            cz.kruch.track.configuration.Config.btServiceUrl = getAppProperty(JAD_GPS_CONNECTION_URL);
        }
        if (getAppProperty(JAD_GPS_DEVICE_NAME) != null) {
            cz.kruch.track.configuration.Config.btDeviceName = getAppProperty(JAD_GPS_DEVICE_NAME);
        }

        // boot desktop
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
        return ((jsr135 || jsr234) && "true".equals(System.getProperty("supports.video.capture")));
    }
}
