// @LICENSE@

package cz.kruch.track;

import api.file.File;

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
    public static boolean jsr82, jsr120, jsr135, jsr179, jsr234, jsr256, motorola179, comm, nokiaui14;
    public static boolean sonyEricsson, sonyEricssonEx, nokia, siemens, lg, motorola, samsung, sonim;
    public static boolean j9, jbed, jblend, intent, wm, palm, rim, symbian, s40th6, s60nd, s60rdfp2, uiq, 
                          brew, android, playbook, iden, jp6plus;
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
        // initial state
        state = 0;
        // detect environment
//#ifndef __CN1__
        platform = System.getProperty("microedition.platform");
//#else
        platform = "cn1";
//#endif
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
        nokia = platform.startsWith("Nokia");
        s60nd = platform.startsWith("Nokia6630") || platform.startsWith("Nokia668") || platform.startsWith("NokiaN70") || platform.startsWith("NokiaN72");
        s60rdfp2 = platform.indexOf("sw_platform=S60") > -1;
        sonyEricsson = System.getProperty("com.sonyericsson.imei") != null;
        sonyEricssonEx = sonyEricsson || platform.startsWith("SonyEricsson");
        symbian = true;
//#else
        nokia = platform.startsWith("Nokia");
        sonyEricsson = System.getProperty("com.sonyericsson.imei") != null;
        sonyEricssonEx = sonyEricsson || platform.startsWith("SonyEricsson");
        samsung = platform.startsWith("SAMSUNG") || platform.startsWith("SGH");
        siemens = System.getProperty("com.siemens.IMEI") != null || System.getProperty("com.siemens.mp.imei") != null;
        motorola = System.getProperty("com.motorola.IMEI") != null;
        lg = platform.startsWith("LG") || System.getProperty("com.lge.lgjp.specification") != null;
        j9 = platform.startsWith("Windows CE");
        jbed = platform.startsWith("Jbed");
        intent = platform.startsWith("intent");
        palm = platform.startsWith("Palm OS");
        sxg75 = "SXG75".equals(platform);
        brew = sxg75 || "BENQ-M7".equals(platform);
        a780 = "j2me".equals(platform);
        s65 = "S65".equals(platform);
        sonim = platform.startsWith("Sonim");
        System.getProperty("com.nokia.targetdebug"); // for ODD
//#endif

//#ifndef __ANDROID__

        // detect runtime capabilities
        jsr82 = forName("bluetooth.api.version", "javax.bluetooth.DiscoveryAgent", "* JSR-82");
        jsr120 = forName("wireless.messaging.version", "javax.wireless.messaging.TextMessage", "* JSR-120");
        jsr135 = forName(null, "javax.microedition.media.control.VideoControl", "* JSR-135"); // TODO property
        jsr179 = forName("microedition.location.version", "javax.microedition.location.LocationProvider", "* JSR-179");
        try {
            Class.forName("javax.microedition.amms.control.camera.CameraControl");
            Class.forName("javax.microedition.amms.control.camera.SnapshotControl");
            jsr234 = true;
//#ifdef __LOG__
            System.out.println("* JSR-234");
//#endif
        } catch (Throwable t) {
            // ignore
        }
/*
        jsr256 = forName(null, "javax.microedition.sensor.SensorManager", "* JSR-256");
        comm = forName(null, "javax.microedition.io.CommConnection", "* CommConnection");
*/

//#else /* __ANDROID__ */

        // detect runtime capabilities
        jsr179 = true;
//#ifdef __BACKPORT__
        jsr82 = forName(null, "backport.android.bluetooth.BluetoothSocket", null);
//#else
        jsr82 = forName(null, "android.bluetooth.BluetoothSocket", null);
//#endif

//#endif /* __ANDROID__ */
        
//#ifdef __ALL__

        if (nokia) { /* detect Nokia UI API 1.4+ */
            s40th6 = jsr179 && System.getProperty("com.nokia.mid.ui.version") == null;
            try {
                nokiaui14 = Float.parseFloat(System.getProperty("com.nokia.mid.ui.version")) >= 1.4F
                            && "true".equals(System.getProperty("com.nokia.mid.ui.screensaverprevention"));
            } catch (Exception e) {
                // ignore parse error
            }
        } else if (sonyEricssonEx) { /* detect UIQ and/or family */
            if (symbian) { // UIQ?
                uiq = !s60rdfp2;
            } else { // detect family
                jp6plus = forName(null, "java.rmi.Remote", null);
            }
        } else {
            /* detect Jbed */
            jbed = forName(null, "com.jbed.io.CharConvUTF8", null);
            /* detect Jblend */
            jblend = forName(null, "com.jblend.util.SortedVector", null);
            /* detect Motorola-specific Location API */
            motorola179 = forName(null, "com.motorola.location.PositionSource", null);
            /* detect WM */
            wm = jbed || jblend || intent;
        }

//#endif /* __ALL__ */

//#if !__RIM__ && !__SYMBIAN__ && !__ANDROID__

        // has to be outside __ALL__
        iden = forName("* iDEN", "com.mot.iden.zip.ZipException", null);

//#endif
                
        // init device control (also helps to detect other platforms)
        cz.kruch.track.ui.nokia.DeviceControl.initialize();

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
    }

    private cz.kruch.track.ui.Desktop desktop;

    protected void startApp() throws MIDletStateChangeException {
//#ifdef __LOG__
        System.out.println("* startApp * " + state);
//#endif
        // initial launch?
        if (state == 0) {
            state = 1;
            (new Thread(this)).start();
        } else { // resumed from background
            state = 1;
//#ifdef __ANDROID__
            if (desktop != null) {
                desktop.onForeground();
            }
//#endif
        }
    }

    protected void pauseApp() {
//#ifdef __LOG__
        System.out.println("* pauseApp *");
//#endif
        // diagnostics
        pauses++;

        // update state
        state = 2;

        // minimize
//#ifdef __ANDROID__
        if (desktop != null) {
            desktop.onBackground();
        }
//#endif
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
        int imaged;
        try {
            cz.kruch.track.ui.NavigationScreens.initialize();
            imaged = 1;
        } catch (Throwable t) {
            imaged = -1;
        }

        // load configuration
        int configured;
        try {
            configured = cz.kruch.track.configuration.Config.initialize();
        } catch (Throwable t) {
            configured = -1;
        }

        // load default resources
        int resourced;
        try {
            resourced = Resources.initialize();
        } catch (Throwable t) {
            resourced = -1;
        }

        // initialize file API
        api.file.File.initialize(sxg75 || android || iden || hasFlag("fs_traverse_bug"));
//#ifdef __LOG__
        System.out.println("* FsType: " + api.file.File.fsType);
//#endif

//#ifndef __B2B__

        // check datadir access and existence
        if (File.isFs()) {
            cz.kruch.track.configuration.Config.checkDataDir(configured);
        }

//#else

        // just assume it exists
        cz.kruch.track.configuration.Config.dataDirAccess = true;
        cz.kruch.track.configuration.Config.dataDirExists = true;

//#endif

        // create and boot desktop
        cz.kruch.track.ui.Desktop d = new cz.kruch.track.ui.Desktop(this);
        d.show();
        try {
            d.boot(imaged, configured, resourced, true);
            desktop = d;
        } catch (Throwable t) {
            t.printStackTrace();
//#ifdef __ANDROID__            
            System.exit(0);
//#endif
        }
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
//-#ifndef __ANDROID__
//        return comm;
//-#else
        return true;
//-#endif
    }

    public static boolean supportsVideoCapture() {
//#ifndef __ANDROID__
        return ((jsr135 || jsr234) && "true".equals(System.getProperty("supports.video.capture")));
//#else
        return true;
//#endif
    }

//#ifdef __ANDROID__
    public static org.microemu.android.MicroEmulatorActivity getActivity() {
        return (org.microemu.android.MicroEmulatorActivity) ((org.microemu.android.device.AndroidDevice) org.microemu.device.DeviceFactory.getDevice()).getActivity();
    }
//#endif

    private static boolean forName(final String property, final String clazz, final String msg) {
        try {
            if (property == null || System.getProperty(property) == null) {
                Class.forName(clazz);
            }
//#ifdef __LOG__
            if (msg != null) { // message to printed when success
                System.out.println(msg);
            }
//#endif
            return true;
        } catch (Throwable t) {
            // ignore
        }
        return false;
    }
}
