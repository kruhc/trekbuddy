// @LICENSE@

package cz.kruch.track;

import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

/**
 * The MIDlet.
 *
 * @author kruhc@seznam.cz
 */
public class TrackingMIDlet extends MIDlet implements Runnable {

    /** application title/name */
    public static final String APP_TITLE = "TrekBuddy";

    // system info

    private static String platform, flags;

    public static String version;
    public static boolean jsr82, jsr120, jsr135, jsr179, jsr234, motorola179, comm;
    public static boolean sonyEricsson, sonyEricssonEx, nokia, siemens, lg, motorola, samsung;
    public static boolean j9, jbed, intent, palm, rim, symbian, s60nd, s60rdfp2, uiq, brew, android;
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
    public static final String JAD_UI_RIGHT_KEY            = "UI-RightKey";
    public static final String JAD_APP_FLAGS               = "App-Flags";

    public TrackingMIDlet() {
        // detect environment
        platform = System.getProperty("microedition.platform");
        flags = getAppProperty(JAD_APP_FLAGS);
        if (flags == null) {
            flags = System.getProperty("trekbuddy.app-flags");
        }
//#ifdef __ANDROID__
        version = System.getProperty("MIDlet-Version");
//#else
        version = getAppProperty("MIDlet-Version");
//#endif		
//#ifdef __LOG__
        logEnabled = hasFlag("log_enable");
        System.out.println("* platform is " + platform);
//#endif

        // detect brand/device
//#ifdef __RIM__
        rim = true;
//#elifdef __ANDROID__
        android = true;
//#elifdef __SYMBIAN__
        s60nd = platform.startsWith("Nokia6630") || platform.startsWith("Nokia668") || platform.startsWith("NokiaN70") || platform.startsWith("NokiaN72");
        s60rdfp2 = platform.indexOf("sw_platform=S60") > -1;
        symbian = true;
//#else
        nokia = platform.startsWith("Nokia");
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
        // for IntelliJ IDEA; all should resolve to false
        rim = false;
        android = false;
        symbian = false;
        // ~
//#endif

//#ifndef __ANDROID__

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

//#endif /* !__ANDROID__ */
        
//#ifdef __ALL__

        /* try Motorola-specific Location API */
        try {
            Class.forName("com.motorola.location.PositionSource");
            motorola179 = true;
        } catch (Throwable throwable) {
        }

        /* detect UIQ */
        if (sonyEricssonEx) {
            if (symbian) {
                uiq = true;
            }
        } else { /* detect Jbed */
            try {
                Class.forName("com.jbed.io.CharConvUTF8");
                jbed = true;
            } catch (Throwable t) {
                // ignore
            }
        }

//#endif /* __ALL__ */
        
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
//#ifdef __LOG__
		System.out.println("* pauseApp *");
//#endif
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
        if (cz.kruch.track.configuration.Config.dataDirExists) {
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

        // init helpers and 'singletons'
        cz.kruch.track.configuration.Config.initDatums(this);

        // setup environment
        if (hasFlag("fs_skip_bug") || siemens /*|| symbian*/) {
            cz.kruch.track.maps.Map.useSkip = false;
        }
        if (hasFlag("fs_no_reset") || sxg75 /*|| symbian*/) {
            cz.kruch.track.maps.Map.useReset = false;
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
        if (getAppProperty(JAD_UI_RIGHT_KEY) != null) {
            cz.kruch.track.configuration.Config.hideBarCmd = "...".equals(getAppProperty(JAD_UI_RIGHT_KEY));
        }

//#ifdef __B2B__
        // b2b res init
        b2b_resInit();
//#endif        

        // cleanup after initialization?
/* ugly UI effect 
//#ifndef __RIM__
        System.gc(); // unconditional!!! 
//#endif
*/
        // create and boot desktop
        desktop = new cz.kruch.track.ui.Desktop(this);
        desktop.boot(imgcached, configured, customized, localized);
    }

//#ifdef __B2B__

    private void b2b_resInit() {
        final int idx = Integer.parseInt(Resources.getString(Resources.VENDOR_INITIAL_SCREEN));
        if (idx >= 0 && idx <= 2) {
            cz.kruch.track.configuration.Config.startupScreen = idx;
        }
        final String map = Resources.getString(Resources.VENDOR_INITIAL_MAP);
        if (!Integer.toString(Resources.VENDOR_INITIAL_MAP).equals(map)) {
            cz.kruch.track.configuration.Config.mapPath = map;
        }
        final String checksum = Resources.getString(Resources.VENDOR_INITIAL_CHECKSUM);
        if (!Integer.toString(Resources.VENDOR_INITIAL_CHECKSUM).equals(checksum)) {
            cz.kruch.track.configuration.Config.vendorChecksumKnown = true;
            cz.kruch.track.configuration.Config.vendorChecksum = (int) Long.parseLong(checksum, 16);
        }
        final String store = Resources.getString(Resources.VENDOR_INITIAL_NAVI_SOURCE);
        if (!Integer.toString(Resources.VENDOR_INITIAL_NAVI_SOURCE).equals(store)) {
            cz.kruch.track.configuration.Config.vendorNaviStore = store;
        }
        final String cmd = Resources.getString(Resources.VENDOR_INITIAL_NAVI_CMD);
        if (!Integer.toString(Resources.VENDOR_INITIAL_NAVI_CMD).equals(cmd)) {
            cz.kruch.track.configuration.Config.vendorNaviCmd = cmd;
        }
        final String datadir = Resources.getString(Resources.VENDOR_INITIAL_DATADIR);
        if (!Integer.toString(Resources.VENDOR_INITIAL_DATADIR).equals(datadir)) {
            cz.kruch.track.configuration.Config.dataDir = datadir;
        }
    }

//#endif

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
