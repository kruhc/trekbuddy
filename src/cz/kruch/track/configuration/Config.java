// @LICENSE@

package cz.kruch.track.configuration;

import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.io.LineReader;
import cz.kruch.track.ui.YesNoDialog;
import cz.kruch.track.event.Callback;

import javax.microedition.rms.RecordStore;
import javax.microedition.io.Connector;
import java.util.Vector;
import java.util.Hashtable;
import java.util.Enumeration;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import api.location.Datum;
import api.location.Ellipsoid;
import api.file.File;

/**
 * Represents and handles configuration persisted in RMS.
 *
 * @author kruhc@seznam.cz
 */
public final class Config implements Runnable, YesNoDialog.AnswerListener {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Config");
//#endif

    /* known providers */
    public static final int LOCATION_PROVIDER_JSR82      = 0;
    public static final int LOCATION_PROVIDER_JSR179     = 1;
    public static final int LOCATION_PROVIDER_SERIAL     = 2;
    public static final int LOCATION_PROVIDER_SIMULATOR  = 3;
    public static final int LOCATION_PROVIDER_MOTOROLA   = 4;
    public static final int LOCATION_PROVIDER_O2GERMANY  = 5;
    public static final int LOCATION_PROVIDER_HGE100     = 6;

    /* tracklog options */
    public static final int TRACKLOG_NEVER          = 0;
    public static final int TRACKLOG_ASK            = 1;
    public static final int TRACKLOG_ALWAYS         = 2;

    /* tracklog format */
    public static final String TRACKLOG_FORMAT_NMEA = "NMEA 0183";
    public static final String TRACKLOG_FORMAT_GPX  = "GPX 1.1";

    /* coordinate format */
    public static final int COORDS_MAP_LATLON       = 0;
    public static final int COORDS_MAP_GRID         = 1;
    public static final int COORDS_GC_LATLON        = 2;
    public static final int COORDS_UTM              = 3;
    public static final int COORDS_GPX_LATLON       = 4;

    /* units */
    public static final int UNITS_METRIC            = 0;
    public static final int UNITS_IMPERIAL          = 1;
    public static final int UNITS_NAUTICAL          = 2;

    /* easyzoom */
    public static final int EASYZOOM_OFF            = 0;
    public static final int EASYZOOM_LAYERS         = 1;
    public static final int EASYZOOM_AUTO           = 2;

    /* listmode */
    public static final int LISTMODE_DEFAULT        = 0;
    public static final int LISTMODE_CUSTOM         = 1;

    /* datadir folders */
    public static final String FOLDER_MAPS         = "maps/";
    public static final String FOLDER_NMEA         = "tracks-nmea/";
    public static final String FOLDER_TRACKS       = "tracks-gpx/";
    public static final String FOLDER_WPTS         = "wpts/";
    public static final String FOLDER_PROFILES     = "ui-profiles/";
    public static final String FOLDER_RESOURCES    = "resources/";
    public static final String FOLDER_SOUNDS       = "sounds/";
    public static final String FOLDER_GC           = "gc/";
//#ifdef __HECL__
    public static final String FOLDER_PLUGINS      = "plugins/";
//#endif

    private static final String DATUMS_FILE        = "datums.txt";
    
    /* 16 basic colors */
    public static final int[] COLORS_16 = {
        0x000000, 0x808080, 0xc0c0c0, 0xffffff,
        0xffff00, 0xff0000, 0x800000, 0x800080,
        0xff00ff, 0x00ffff, 0x0000ff, 0x000080,
        0x008080, 0x008000, 0x00ff00, 0x808000,
    };

    /* rms stores */
    public static final String VARS_090             = "vars_090";
    public static final String CONFIG_090           = "config_090";
    public static final String PLUGIN_110           = "plugin_110_";

    public static final String EMPTY_STRING         = "";
    public static final String NO_MAP_RESOURCE      = "/resources/no-map.xml";

    public static final String FAKE_WORLD_BUILT_IN  = "world (built-in)";

    /*
     * Configuration params, initialized to default values.
     */

    // group [Map]
    public static String mapURL             = EMPTY_STRING;

    // group [Map datum]
    public static String geoDatum           = "WGS 84";

    // group [Startup screen]
    public static int startupScreen;

    // group [Provider]
    public static int locationProvider      = -1;

    // group [DataDir]
    private static String dataDir;
//#ifdef __CN1__
    public static String cardDir;
//#endif

    // group [common provider options]
    public static int tracklog              = TRACKLOG_NEVER;
    public static String tracklogFormat     = TRACKLOG_FORMAT_GPX;
    public static String captureLocator     = "capture://video";
    public static String snapshotFormat     = EMPTY_STRING;
    public static int snapshotFormatIdx;

    // group [Bluetooth provider options]
    public static int btKeepAlive;
    public static boolean btDoServiceSearch;
    public static boolean btAddressWorkaround;

    // group [Simulator provider options]
    public static int simulatorDelay            = 1000; // ms

    // group [Internal provider options]
    private static String locationTimings       = EMPTY_STRING;
    public static float altCorrection;
    public static int powerUsage                = 2;
    public static boolean assistedGps;
    public static boolean timeFix;

    // group [Serial provider options]
    public static String commUrl                = "comm:COM5;baudrate=9600";

    // group [Location sharing]
    public static boolean locationSharing;
    public static boolean autohideNotification  = true;

    // group [O2Germany provider options]
    public static int o2Depth                   = 1;

    // group [Blackberry]
    public static boolean negativeAltFix;

    // group [NMEA]
    public static boolean nmeaMsExact           = true;

    // group [Desktop]
    public static boolean fullscreen;
    public static boolean noSounds;
    public static boolean decimalPrecision      = true;
    public static boolean osdBasic              = true;
    public static boolean osdExtended           = true;
    public static boolean osdScale              = true;
    public static boolean osdNoBackground;
    public static boolean osdBoldFont;
    public static boolean osdBlackColor;
    public static boolean hpsWptTrueAzimuth     = true;
    public static boolean safeColors;
    public static boolean noQuestions;
    public static boolean uiNoCommands;
    public static boolean easyZoomVolumeKeys;
    public static boolean uiNoItemCommands;
    public static int desktopFontSize;
    public static int osdAlpha                  = 0xA0;
    public static int cmsCycle;
    public static int listFont                  = 0x200000;
    public static int zoomSpotsMode             = 1; // always
    public static int guideSpotsMode            = 2; // autohide
    public static int prescale                  = 100; // 100%
    public static boolean forceTextFieldFocus;
    public static boolean fixedCrosshair;
    public static boolean hpsMagneticNeedle     = true;

    // [Units]
    public static int units;

    // [Coordinates]
    public static int cfmt;

    // group [Listmode]
    public static int extListMode;

    // group [Easyzoom]
    public static int easyZoomMode              = EASYZOOM_AUTO;

    // group [Tweaks]
//#ifndef __CN1__
    public static boolean siemensIo;
//#else
    public static boolean wp8Io;
//#endif
//#ifdef __ALT_RENDERER__
    public static boolean S60renderer           = true;
//#endif
//#ifndef __ANDROID__
    public static boolean s40ticker;
//#endif
    public static boolean forcedGc;
    public static boolean oneTileScroll;
    public static boolean largeAtlases;
    public static boolean powerSave;
    public static boolean reliableInput;
    public static boolean hideBarCmd;
    public static boolean useNativeService;
    public static boolean lazyGpxParsing        = true;
    public static boolean lowmemIo;
    public static boolean numericInputHack;
    public static boolean externalConfigBackup;
    public static boolean tilesScaleFiltered;
    public static boolean verboseLoading;
//#ifdef __CN1__
    public static boolean wp8wvga = true;
//#endif
    public static int heclOpt                   = 1;
    public static int inputBufferSize           = 4096;
    public static int fpsControl;

    // group [GPX options]
    public static int gpxDt                     = 60; // 1 min
    public static int gpxDs                     = -1;
    public static boolean gpxOnlyValid          = true;
    public static boolean gpxGsmInfo;
    public static boolean gpxSecsDecimal        = true;

    // group [Trajectory]
    public static boolean trailOn;
    public static int trailColor                = 9;
    public static int trailThick;

    // group [Navigation]
    public static int wptProximity              = 50;
    public static int poiProximity              = 1000;
    public static boolean routePoiMarks         = true;
    public static boolean routeLineStyle;
    public static int routeColor                = 6;
    public static int routeThick;
    public static boolean trackPoiMarks;
    public static boolean trackLineStyle;
    public static int trackColor                = 7;
    public static int trackThick;
    public static boolean makeRevisions;
    public static boolean preferGsName          = true;
    public static int sort;
    public static boolean wptAlertSound         = true;
    public static boolean wptAlertVibr;
    public static boolean mobEnabled            = true;
    public static boolean gpxAllowExtensions    = true;

    // hidden
    public static String btDeviceName   = EMPTY_STRING;
    public static String btServiceUrl   = EMPTY_STRING;
    public static int x = -1;
    public static int y = -1;
    public static int dayNight;
    public static String cmsProfile     = EMPTY_STRING;
    public static boolean o2provider;
    public static int nokiaBacklightLast = 1; // first backlight level, see NokiaDeviceControl
    public static double lat, lon;
    public static double latAny, lonAny;
    public static boolean wptSessionMode;

    // runtime (not persisted)
    public static boolean dataDirAccess, dataDirExists;
    public static boolean filesizeAvail;
    public static String defaultWptSound;

//#ifdef __B2B__

    // vendor configuration support
    public static boolean vendorChecksumKnown;
    public static int vendorChecksum;
    public static String vendorNaviStore, vendorNaviCmd;

//#endif

//#ifdef __CRC__

    // builder ops
    public static boolean calcCrc = true;

//#endif

    private static final int ACTION_INITDATADIR     = 0;
    private static final int ACTION_PERSISTCFG      = 1;
    private static final int ACTION_UPDATEMAINCFG   = 2;
    private static final int ACTION_UPDATEVARSCFG   = 3;

    private int action;

    private Config(int action) {
        this.action = action;
    }

    public static int initialize() {

//#ifdef __RIM__

        /* default for Blackberry */
        dataDir = getDefaultDataDir("file:///SDCard/", "TrekBuddy/");
        commUrl = "btspp://000276fd79da:1";
        fullscreen = true;
        safeColors = true;
        externalConfigBackup = true;
        listFont = 0x200008;

//#elifdef __ANDROID__

        /* defaults for Android (MicroEmu) */
        if (android.os.Build.MODEL.indexOf("BlackBerry") > -1) {
            cz.kruch.track.TrackingMIDlet.playbook = true;
        }
        dataDir = getDefaultDataDir("file:///sdcard/", "TrekBuddy/");
        desktopFontSize = 1;
        listFont = 0x200010;
        tilesScaleFiltered = true;

//#elifdef __CN1__

        dataDir = "file:///TrekBuddy/";
        cardDir = "file:///Card/TrekBuddy/";
        desktopFontSize = 1;
        safeColors = true;
        fullscreen = true;
        wp8Io = true;
//#ifdef __ALT_RENDERER__
        S60renderer = false; // drawRegion supported well
//#endif

//#else

        if (cz.kruch.track.TrackingMIDlet.sxg75) {
            dataDir = getDefaultDataDir("file:///fs/", "tb/");
            fullscreen = true;
            altCorrection = -540F;
        } else if (cz.kruch.track.TrackingMIDlet.brew) {
            dataDir = getDefaultDataDir("file:///fs/", "tb/");
            altCorrection = -540F;
        } else if (cz.kruch.track.TrackingMIDlet.j9 || cz.kruch.track.TrackingMIDlet.wm) {
            dataDir = getDefaultDataDir("file:///Storage Card/", "TrekBuddy/");
            if (cz.kruch.track.TrackingMIDlet.wm) {
                commUrl = "socket://localhost:20175";
            }
        } else if (cz.kruch.track.TrackingMIDlet.siemens) {
            dataDir = getDefaultDataDir("file:///4:/", "TrekBuddy/");
            fullscreen = true;
        } else if (cz.kruch.track.TrackingMIDlet.lg) {
            dataDir = getDefaultDataDir("file:///Card/", "TrekBuddy/");
            fullscreen = true;
        } else if (cz.kruch.track.TrackingMIDlet.iden) {
            dataDir = getDefaultDataDir("file:///Storage%20Card/", "trekbuddy/");
            safeColors = true;
            filesizeAvail = true;
        } else if (cz.kruch.track.TrackingMIDlet.motorola || cz.kruch.track.TrackingMIDlet.a780) {
            dataDir = getDefaultDataDir("file:///b/", "trekbuddy/");
            forcedGc = true;
        } else if (cz.kruch.track.TrackingMIDlet.samsung) {
            dataDir = getDefaultDataDir("file:///Mmc/", "trekbuddy/");
            if (cz.kruch.track.TrackingMIDlet.b2710) {
                desktopFontSize = 2; 
                lowmemIo = true;
                largeAtlases = true;
            }
        } else if (cz.kruch.track.TrackingMIDlet.uiq) {
            dataDir = getDefaultDataDir("file:///Ms/", "Other/TrekBuddy/");
            fullscreen = true;
        } else if (cz.kruch.track.TrackingMIDlet.sonim) {
            dataDir = getDefaultDataDir("file:///Memory card/", "Trekbuddy/");
        } else { // Nokia, SonyEricsson, ...
            dataDir = getDefaultDataDir("file:///E:/", "TrekBuddy/"); // pstros: "file:///SDCard/TrekBuddy/"
            fullscreen = cz.kruch.track.TrackingMIDlet.nokia || cz.kruch.track.TrackingMIDlet.sonyEricsson || cz.kruch.track.TrackingMIDlet.symbian;
        }
        if (cz.kruch.track.TrackingMIDlet.symbian) {
            useNativeService = false; // !cz.kruch.track.TrackingMIDlet.s60rdfp2;
        } else if (cz.kruch.track.TrackingMIDlet.nokia) {
            safeColors = true;
            captureLocator = "capture://image";
            s40ticker = System.getProperty("com.nokia.mid.ui.layout") != null; // since S40 6th FP1
            fpsControl = 1;
        }

//#endif

//#if __SYMBIAN__ || __RIM__ || __ANDROID__ || __CN1__

        inputBufferSize = 8192;
        
//#endif

        int result;
        try {
            result = initialize(CONFIG_090);
        } catch (Throwable t) {
            result = -1;
        }
        try {
            initialize(VARS_090);
        } catch (Throwable t) {
            // ignore
        }

        // correct initial values
        if (locationProvider == -1) {
            if (cz.kruch.track.TrackingMIDlet.jsr179 || cz.kruch.track.TrackingMIDlet.android) {
                locationProvider = Config.LOCATION_PROVIDER_JSR179;
            } else if (cz.kruch.track.TrackingMIDlet.jsr82) {
                locationProvider = Config.LOCATION_PROVIDER_JSR82;
            } else if (cz.kruch.track.TrackingMIDlet.hasPorts()) {
                locationProvider = Config.LOCATION_PROVIDER_SERIAL;
            } else if (File.isFs()) {
                locationProvider = Config.LOCATION_PROVIDER_SIMULATOR;
            }
        }

//#ifdef __EMULATOR__
        locationProvider = Config.LOCATION_PROVIDER_SIMULATOR;
//#endif        

        return result;
    }

    private static String getDefaultDataDir(final String defaultRoot,
                                            final String appPath) {
        String cardRoot = System.getProperty("fileconn.dir.memorycard"); // usually includes protocol schema
        if (cardRoot == null) {
            cardRoot = defaultRoot;
        }

        final StringBuffer sb = new StringBuffer(32);
        if (!cardRoot.startsWith(File.FILE_PROTOCOL)) {
            sb.append(File.FILE_PROTOCOL);
            if (!cardRoot.startsWith("/")) {
                sb.append('/');
            }
        }
        sb.append(cardRoot);
        if (!cardRoot.endsWith("/")) {
            sb.append('/');
        }
        sb.append(appPath);

        return sb.toString();
    }

    private static void readMain(final DataInputStream din) throws IOException {
        mapURL = din.readUTF();
        /*String _locationProvider = */din.readUTF();
        /*timeZone = */din.readUTF();
        geoDatum = din.readUTF();
        /*tracklogsOn = */din.readBoolean(); // bc
        tracklogFormat = din.readUTF();
        setDataDir(din.readUTF());
        captureLocator = din.readUTF();
        snapshotFormat = din.readUTF();
        btDeviceName = din.readUTF();
        btServiceUrl = din.readUTF();
        simulatorDelay = din.readInt();
        /*locationInterval = */din.readInt();
        locationSharing = din.readBoolean();
        fullscreen = din.readBoolean();
        noSounds = din.readBoolean();
        /*useUTM = */din.readBoolean();
        osdExtended = din.readBoolean();
        osdNoBackground = din.readBoolean();
        /*osdMediumFont = */din.readBoolean();
        osdBoldFont = din.readBoolean();
        osdBlackColor = din.readBoolean();

        // 0.9.1 extension - obsolete since 0.9.65
        /*tracklog = */din.readUTF();

        // 0.9.2 extension
        /*useGeocachingFormat = */din.readBoolean();

        // pre 0.9.4 extension
        /*optimisticIo = */din.readBoolean();
        /*S60renderer = */din.readBoolean();
        /*cacheOffline = */din.readBoolean();

        // 0.9.5 extension
        decimalPrecision = din.readBoolean();
        /*useGridFormat = */din.readBoolean();
        hpsWptTrueAzimuth = din.readBoolean();
        osdBasic = din.readBoolean();

        // 0.9.5x extensions
        locationTimings = din.readUTF();
        trailOn = din.readBoolean();
        forcedGc = din.readBoolean();
        oneTileScroll = din.readBoolean();
        /*gpxRaw = */din.readBoolean();
        /*unitsNautical = */din.readBoolean();
        commUrl = din.readUTF();
        /*unitsImperial = */din.readBoolean();
        wptProximity = din.readInt();
        poiProximity = din.readInt();
        /*language = */din.readUTF();
        /*routeLineColor = */din.readInt();
        routeLineStyle = din.readBoolean();
        routePoiMarks = din.readBoolean();
        /*scrollingDelay = */din.readInt();
        gpxDt = din.readInt();
        gpxDs = din.readInt();

        // 0.9.63 extensions
        osdScale = din.readBoolean();

        // 0.9.65 extensions
        locationProvider = din.readInt();
        tracklog = din.readInt();
        gpxOnlyValid = din.readBoolean();

        // 0.9.66 extensions
        units = din.readInt();

        // 0.9.69 extensions
        o2Depth = din.readInt();
//#ifndef __CN1__
        siemensIo = din.readBoolean();
//#else
        wp8Io = din.readBoolean();
//#endif

        // 0.9.70 extensions
        largeAtlases = din.readBoolean();
        gpxGsmInfo = din.readBoolean();

        // 0.9.74 extensions
        osdAlpha = din.readInt();

        // 0.9.77 extensions
        btKeepAlive = din.readInt();

        // 0.9.78 extensions
        cmsCycle = din.readInt();

        // 0.9.79 extensions
        trailColor = din.readInt();
        trailThick = din.readInt();
        routeColor = din.readInt();
        routeThick = din.readInt();

        // 0.9.81 extensions
        makeRevisions = din.readBoolean();
        autohideNotification = din.readBoolean();
        preferGsName = din.readBoolean();
        safeColors = din.readBoolean();

        // 0.9.82 extensions
        powerSave = din.readBoolean();
        snapshotFormatIdx = din.readInt();
        cfmt = din.readInt();
        sort = din.readInt();

        // 0.9.85 extensions
        listFont = din.readInt();

        // 0.9.88 extensions
        altCorrection = din.readInt();
        gpxSecsDecimal = din.readBoolean();

        // 0.9.91 extensions
        negativeAltFix = din.readBoolean();

        // 0.9.92 extensions
        desktopFontSize = din.readInt();
        startupScreen = din.readInt();

        // 0.9.94 extensions
        noQuestions = din.readBoolean();
        reliableInput = din.readBoolean();

        // 0.9.95 extensions
        hideBarCmd = din.readBoolean();

        // 0.9.96 extensions
        useNativeService = din.readBoolean();
        lazyGpxParsing = din.readBoolean();
        assistedGps = din.readBoolean();

        // 0.9.97 extensions
        btDoServiceSearch = din.readBoolean();
        uiNoCommands = din.readBoolean();
        powerUsage = din.readInt();

        // 0.9.98 extensions
        btAddressWorkaround = din.readBoolean();
        lowmemIo = din.readBoolean();
        timeFix = din.readBoolean();

        // 1.0.2 change
        altCorrection = din.readFloat();

        // 1.0.4 change
        numericInputHack = din.readBoolean();

        // 1.0.5 change
        externalConfigBackup = din.readBoolean();

        try {
            // 1.0.10 change
            /*easyZoomMode = */din.readInt();

            // 1.0.11 change
            easyZoomVolumeKeys = din.readBoolean();
            /*showZoomSpots = new Boolean(*/din.readBoolean()/*)*/;

            // 1.0.14 change
            uiNoItemCommands = din.readBoolean();

            // 1.0.17b change
            /*showGuideSpots = new Boolean(*/din.readBoolean()/*)*/;

            // 1.0.17 change
            zoomSpotsMode = din.readInt();
            guideSpotsMode = din.readInt();

            // 1.0.24 change
            extListMode = din.readInt();
            heclOpt = din.readInt();
            prescale = din.readInt();
            tilesScaleFiltered = din.readBoolean();

            // 1.1.2 change
            /*verboseLoading = */din.readBoolean(); // reconfirm in 1.27

            // 1.1.4 change
//#ifndef __ANDROID__
            s40ticker = din.readBoolean();
//#endif

            // 1.2.0 change
            forceTextFieldFocus = din.readBoolean();
            wptAlertSound = din.readBoolean();
            wptAlertVibr = din.readBoolean();
            trackLineStyle = din.readBoolean();
            trackColor = din.readInt();
            trackThick = din.readInt();

            // 1.2.1 change
            mobEnabled = din.readBoolean();
            trackPoiMarks = din.readBoolean();

            // 1.2.2 change
            inputBufferSize = din.readInt();

            // 1.2.3 change
            fpsControl = din.readInt();

            // 1.26 changes
            fixedCrosshair = din.readBoolean();
            gpxAllowExtensions = din.readBoolean();

            // 1.27 changes
            verboseLoading = din.readBoolean(); // reconfirm

            // 1.28 changes
            easyZoomMode = din.readInt(); // reconfirm

            // 1.31 changes
//#ifdef __CN1__
            wp8wvga = din.readBoolean();
//#endif

            // 1.33 changes
            hpsMagneticNeedle = din.readBoolean();

        } catch (Exception e) {

            // 1.2.0 fallback
            wptAlertSound = !noSounds;
            wptAlertVibr = !powerSave;
            trackColor = routeColor;
            trackThick = routeThick;

        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("configuration read");
//#endif
    }

    private static void writeMain(final DataOutputStream dout) throws IOException {
        dout.writeUTF(mapURL);
        dout.writeUTF(EMPTY_STRING/*locationProvider*/);
        dout.writeUTF(EMPTY_STRING/*timeZone*/);
        dout.writeUTF(geoDatum);
        dout.writeBoolean(false/*tracklogsOn*/);
        dout.writeUTF(tracklogFormat);
        dout.writeUTF(dataDir);
        dout.writeUTF(captureLocator);
        dout.writeUTF(snapshotFormat);
        dout.writeUTF(btDeviceName);
        dout.writeUTF(btServiceUrl);
        dout.writeInt(simulatorDelay);
        dout.writeInt(0/*locationInterval*/);
        dout.writeBoolean(locationSharing);
        dout.writeBoolean(fullscreen);
        dout.writeBoolean(noSounds);
        dout.writeBoolean(false/*useUTM*/);
        dout.writeBoolean(osdExtended);
        dout.writeBoolean(osdNoBackground);
        dout.writeBoolean(false/*osdMediumFont*/);
        dout.writeBoolean(osdBoldFont);
        dout.writeBoolean(osdBlackColor);
        dout.writeUTF(EMPTY_STRING/*tracklog*/);
        dout.writeBoolean(false/*useGeocachingFormat*/);
        dout.writeBoolean(false/*optimisticIo*/);
        dout.writeBoolean(true/*S60renderer*/);
        dout.writeBoolean(false/*cacheOffline*/);
        dout.writeBoolean(decimalPrecision);
        dout.writeBoolean(false/*useGridFormat*/);
        dout.writeBoolean(hpsWptTrueAzimuth);
        dout.writeBoolean(osdBasic);
        dout.writeUTF(locationTimings);
        dout.writeBoolean(trailOn);
        dout.writeBoolean(forcedGc);
        dout.writeBoolean(oneTileScroll);
        dout.writeBoolean(false/*gpxRaw*/);
        dout.writeBoolean(false/*unitsNautical*/);
        dout.writeUTF(commUrl);
        dout.writeBoolean(false/*unitsImperial*/);
        dout.writeInt(wptProximity);
        dout.writeInt(poiProximity);
        dout.writeUTF(EMPTY_STRING/*language*/);
        dout.writeInt(0/*routeLineColor*/);
        dout.writeBoolean(routeLineStyle);
        dout.writeBoolean(routePoiMarks);
        dout.writeInt(0/*scrollingDelay*/);
        dout.writeInt(gpxDt);
        dout.writeInt(gpxDs);
        dout.writeBoolean(osdScale);
        dout.writeInt(locationProvider);
        dout.writeInt(tracklog);
        dout.writeBoolean(gpxOnlyValid);
        dout.writeInt(units);
        /* since 0.9.69 */
        dout.writeInt(o2Depth);
//#ifndef __CN1__
        dout.writeBoolean(siemensIo);
//#else
        dout.writeBoolean(wp8Io);
//#endif
        /* since 0.9.70 */
        dout.writeBoolean(largeAtlases);
        dout.writeBoolean(gpxGsmInfo);
        /* since 0.9.74 */
        dout.writeInt(osdAlpha);
        /* since 0.9.77 */
        dout.writeInt(btKeepAlive);
        /* since 0.9.78 */
        dout.writeInt(cmsCycle);
        /* since 0.9.79 */
        dout.writeInt(trailColor);
        dout.writeInt(trailThick);
        dout.writeInt(routeColor);
        dout.writeInt(routeThick);
        /* since 0.9.81 */
        dout.writeBoolean(makeRevisions);
        dout.writeBoolean(autohideNotification);
        dout.writeBoolean(preferGsName);
        dout.writeBoolean(safeColors);
        /* since 0.9.82 */
        dout.writeBoolean(powerSave);
        dout.writeInt(snapshotFormatIdx);
        dout.writeInt(cfmt);
        dout.writeInt(sort);
        /* since 0.9.86 */
        dout.writeInt(listFont);
        /* since 0.9.88 */
        dout.writeInt(0/*altCorrection*/);
        dout.writeBoolean(gpxSecsDecimal);
        /* since 0.9.91 */
        dout.writeBoolean(negativeAltFix);
        /* since 0.9.92 */
        dout.writeInt(desktopFontSize);
        dout.writeInt(startupScreen);
        /* since 0.9.94 */
        dout.writeBoolean(noQuestions);
        dout.writeBoolean(reliableInput);
        /* since 0.9.95 */
        dout.writeBoolean(hideBarCmd);
        /* since 0.9.96 */
        dout.writeBoolean(useNativeService);
        dout.writeBoolean(lazyGpxParsing);
        dout.writeBoolean(assistedGps);
        /* since 0.9.97 */
        dout.writeBoolean(btDoServiceSearch);
        dout.writeBoolean(uiNoCommands);
        dout.writeInt(powerUsage);
        /* since 0.9.98 */
        dout.writeBoolean(btAddressWorkaround);
        dout.writeBoolean(lowmemIo);
        dout.writeBoolean(timeFix);
        /* since 1.0.2 */
        dout.writeFloat(altCorrection);
        /* since 1.0.4 */
        dout.writeBoolean(numericInputHack);
        /* since 1.0.5 */
        dout.writeBoolean(externalConfigBackup);
        /* since 1.0.10 */
        dout.writeInt(0/*easyZoomMode*/);
        /* since 1.0.11 */
        dout.writeBoolean(easyZoomVolumeKeys);
        dout.writeBoolean(false/*showZoomSpots.booleanValue()*/);
        /* since 1.0.14 */
        dout.writeBoolean(uiNoItemCommands);
        /* since 1.0.17b */
        dout.writeBoolean(false/*showGuideSpots.booleanValue()*/);
        /* since 1.0.17 */
        dout.writeInt(zoomSpotsMode);
        dout.writeInt(guideSpotsMode);
        /* since 1.0.24 */
        dout.writeInt(extListMode);
        dout.writeInt(heclOpt);
        dout.writeInt(prescale);
        dout.writeBoolean(tilesScaleFiltered);
        /* since 1.1.2 */
        dout.writeBoolean(false/*verboseLoading*/); // reconfirm in 1.27
        /* since 1.1.4 */
//#ifndef __ANDROID__
        dout.writeBoolean(s40ticker);
//#endif
        /* since 1.2.0 */
        dout.writeBoolean(forceTextFieldFocus);
        dout.writeBoolean(wptAlertSound);
        dout.writeBoolean(wptAlertVibr);
        dout.writeBoolean(trackLineStyle);
        dout.writeInt(trackColor);
        dout.writeInt(trackThick);

        /* since 1.2.1 */
        dout.writeBoolean(mobEnabled);
        dout.writeBoolean(trackPoiMarks);

        /* since 1.2.2 */
        dout.writeInt(inputBufferSize);

        /* since 1.2.3 */
        dout.writeInt(fpsControl);

        /* since 1.26 */
        dout.writeBoolean(fixedCrosshair);
        dout.writeBoolean(gpxAllowExtensions);

        /* since 1.27 */
        dout.writeBoolean(verboseLoading);

        /* since 1.28 */
        dout.writeInt(easyZoomMode);

        /* since 1.31 */
//#ifdef __CN1__
        dout.writeBoolean(wp8wvga);
//#endif

        /* since 1.33 */
        dout.writeBoolean(hpsMagneticNeedle);

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("configuration updated");
//#endif
    }

    private static void dump() {
        File f = null;
        DataOutputStream os = null;
        try {
            f = File.open(getFileURL(FOLDER_RESOURCES, "settings.dat"), Connector.READ_WRITE);
            if (f.exists()) {
                f.delete();
            }
            f.create();
            os = new DataOutputStream(f.openOutputStream());
            writeMain(os);
        } catch (Exception e) {
//#ifdef __LOG__
            e.printStackTrace();
//#endif
        } finally {
            File.closeQuietly(os);
            File.closeQuietly(f);
        }
    }

    public static int initialize(final String rms) throws ConfigurationException {
        return initialize(rms, null);
    }

    public static int initialize(final String rms, final Callback callback) throws ConfigurationException {
        int result;

        RecordStore rs = null;
        DataInputStream din = null;

        try {
            // open the store
            rs = RecordStore.openRecordStore(rms, true,
                                             RecordStore.AUTHMODE_PRIVATE,
                                             false);

            // new store? existing store? corrupted store?
            result = rs.getNumRecords();
            if (result > 0) {
                din = new DataInputStream(new ByteArrayInputStream(rs.getRecord(1)));
                if (CONFIG_090.equals(rms)) {
                    readMain(din);
                } else if (VARS_090.equals(rms)) {
                    readVars(din);
                } else {
                    try {
                        callback.invoke(din, null, null);
                    } catch (api.lang.RuntimeException e) {
                        throw e.getCause();
                    }
                }
            }
        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
            throw new ConfigurationException(t);
        } finally {
            File.closeQuietly(din);
            closeQuietly(rs);
        }

        return result;
    }

    public static void fallback() {
        if (externalConfigBackup && dataDirExists) {
            File f = null;
            DataInputStream in = null;
            try {
                f = File.open(getFileURL(FOLDER_RESOURCES, "settings.dat"), Connector.READ);
                if (f.exists()) {
                    in = new DataInputStream(f.openInputStream());
                    readMain(in);
                }
            } catch (Exception e) {
//#ifdef __LOG__
                e.printStackTrace();
//#endif
            } finally {
                File.closeQuietly(in);
                File.closeQuietly(f);
            }
        }
    }

    public static void updateInBackground(final String rms) {
        int action = -1;
        if (CONFIG_090.equals(rms)) {
            action = ACTION_UPDATEMAINCFG;
        } else if (VARS_090.equals(rms)) {
            action = ACTION_UPDATEVARSCFG;
        }
        if (action > -1) {
            cz.kruch.track.ui.Desktop.getDiskWorker().enqueue(new Config(action));
        }
    }

    public static void update(final String rms) throws ConfigurationException {
        update(rms, null);
    }

    public static void update(final String rms, final Callback callback) throws ConfigurationException {
        RecordStore rs = null;
        DataOutputStream dout = null;

        try {
            final ByteArrayOutputStream data = new ByteArrayOutputStream();
            dout = new DataOutputStream(data);
            if (CONFIG_090.equals(rms)) {
                writeMain(dout);
            } else if (VARS_090.equals(rms)) {
                writeVars(dout);
            } else {
                try {
                    callback.invoke(dout, null, null);
                } catch (api.lang.RuntimeException e) {
                    throw e.getCause();
                }
            }
            dout.flush();
            final byte[] bytes = data.toByteArray();
            rs = RecordStore.openRecordStore(rms, true,
                                             RecordStore.AUTHMODE_PRIVATE,
                                             true);
            if (rs.getNumRecords() > 0) {
                rs.setRecord(1, bytes, 0, bytes.length);
            } else {
                rs.addRecord(bytes, 0, bytes.length);
            }
        } catch (Throwable t) {
            throw new ConfigurationException(t);
        } finally {
            File.closeQuietly(dout);
            closeQuietly(rs);
        }

        if (CONFIG_090.equals(rms) && externalConfigBackup && dataDirExists) {
//#ifdef __RIM__
            cz.kruch.track.ui.nokia.DeviceControl.saveAltDatadir();
//#endif
            cz.kruch.track.ui.Desktop.getDiskWorker().enqueue(new Config(ACTION_PERSISTCFG));
        }
    }

    public static void configChanged() {
        useDatum(geoDatum);
    }

    public static void checkDataDir(final int configured) {
//#ifdef __RIM__
        if (configured == 0) {
            cz.kruch.track.ui.nokia.DeviceControl.loadAltDatadir();
        }
//#endif
        File dir = null;
        try {
            dir = File.open(Config.dataDir, Connector.READ_WRITE);
            dataDirAccess = true;
            dataDirExists = dir.exists();
        } catch (Exception e) { // IOE or SE
            // ignore
        } finally {
            File.closeQuietly(dir);
        }
    }

    public static void initDataDir() {
        (new Config(ACTION_INITDATADIR)).run();
    }

    public void run() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("run; action = " + action);
//#endif

        switch (action) {
            case ACTION_INITDATADIR: {
//#ifndef __CN1__
                if (dataDirExists) {
                    response(YesNoDialog.NO, null);
                } else {
                    final StringBuffer sb = new StringBuffer(64);
                    sb.append("DataDir '").append(getDataDir().substring(7 /* "file://".length() */)).append("' does not exists. Create it?");
//#ifdef __ANDROID__
                    if (System.getProperty("fileconn.dir.memorycard") == null) {
                        sb.append("\n\nWarning: SD card not mounted!");
                    }
//#endif
                    (new YesNoDialog(Config.this, null, sb.toString(), null)).show();
                }
//#else
                response(YesNoDialog.YES, null);
//#endif
            } break;
            case ACTION_PERSISTCFG: {
                dump();
            } break;
            case ACTION_UPDATEMAINCFG: {
                try {
                    update(CONFIG_090);
                } catch (ConfigurationException e) {
//#ifdef __LOG__
                    log.error("failed to update ".concat(CONFIG_090));
//#endif
                }
            } break;
            case ACTION_UPDATEVARSCFG: {
                try {
                    update(VARS_090);
                } catch (ConfigurationException e) {
//#ifdef __LOG__
                    log.error("failed to update ".concat(VARS_090));
//#endif
                }
            } break;
        }
    }

    public void response(int answer, Object closure) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("response; answer: " + answer + "; closure " + closure);
//#endif
        if (answer == YesNoDialog.YES) {
            File datadir = null;
            try {
                datadir = File.open(getDataDir(), Connector.WRITE);
                if (!datadir.exists()) {
                    datadir.mkdir();
                }
                dataDirExists = true;
            } catch (Exception e) {
//#ifndef __CN1__
                cz.kruch.track.ui.Desktop.showError("Failed to create " + getDataDir().substring(8 /* "file:///".length() */),
                                                    e, null);
//#else
                throw new api.lang.RuntimeException("Failed to create " + getDataDir().substring(8 /* "file:///".length() */),
                                                    e);
//#endif
            } finally {
                File.closeQuietly(datadir);
            }
        }
        if (dataDirExists) {
            final String[] folders = {
                FOLDER_MAPS, FOLDER_NMEA, FOLDER_PROFILES, FOLDER_RESOURCES,
                FOLDER_SOUNDS, FOLDER_TRACKS, FOLDER_WPTS, FOLDER_GC
//#ifdef __HECL__
                , FOLDER_PLUGINS
//#endif
            };

            /* create folder structure */
            File dir = null;
            for (int i = folders.length; --i >= 0; ) {
                try {
                    dir = File.open(getFolderURL(folders[i]), Connector.READ_WRITE);
                    if (!dir.exists()) {
                        dir.mkdir();
                    }
                } catch (Throwable t) {
                    // ignore // TODO really?!?
                } finally {
                    File.closeQuietly(dir);
                }
            }

            /* create default files */
            File file = null;
            try {
                file = File.open(Config.getFileURL(Config.FOLDER_MAPS, "no-map.xml"), Connector.READ_WRITE);
                if (!file.exists()) {
                    file.create();
                    final InputStream in = getResourceAsStream(NO_MAP_RESOURCE);
                    final OutputStream out = file.openOutputStream();
                    final byte[] buffer = new byte[256];
                    int c = in.read(buffer);
                    while (c > -1) {
                        out.write(buffer, 0, c);
                        c = in.read(buffer);
                    }
                    File.closeQuietly(out);
                    File.closeQuietly(in);
                }
            } catch (Throwable t) { // IOE or SEC
//#ifdef __LOG__
                log.error("failed to create no-map.xml file", t);
//#endif
            } finally {
                File.closeQuietly(file);
            }
            try {
                file = File.open(Config.getFileURL(Config.FOLDER_MAPS, FAKE_WORLD_BUILT_IN), Connector.READ_WRITE);
                if (!file.exists()) {
                    file.create();
                }
            } catch (Throwable t) { // IOE or SEC
//#ifdef __LOG__
                log.error("failed to create world (built-in) file", t);
//#endif
            } finally {
                File.closeQuietly(file);
            }

            /* find default files */
            try {
                dir = File.open(Config.getFolderURL(Config.FOLDER_SOUNDS));
                for (final Enumeration seq = dir.list(); seq.hasMoreElements(); ) {
                    final String filename = (String) seq.nextElement();
                    final String candidate = filename.toLowerCase();
                    if (candidate.startsWith("wpt.") && (candidate.endsWith(".amr") || candidate.endsWith(".wav") || candidate.endsWith(".mp3") || candidate.endsWith(".aac")|| candidate.endsWith(".m4a")|| candidate.endsWith(".3gp"))) { // FIXME for iDEN
                        defaultWptSound = filename;
                        break;
                    }
                }
            } catch (Throwable t) {
                // ignore
            } finally {
                File.closeQuietly(dir);
            }
        }
    }

    private static void readVars(DataInputStream din) throws IOException {
        btDeviceName = din.readUTF();
        btServiceUrl = din.readUTF();
        /*defaultMapPath = */din.readUTF();
        x = din.readInt();
        y = din.readInt();
        dayNight = din.readInt();
        cmsProfile = din.readUTF();
        o2provider = din.readBoolean();
        nokiaBacklightLast = din.readInt();
        lat = din.readDouble();
        lon = din.readDouble();
        latAny = din.readDouble();
        lonAny = din.readDouble();
        try {
            /* since 1.2.5 */
            wptSessionMode = din.readBoolean();
        } catch (Exception e) {
            // ignore
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("vars read");
//#endif
    }

    private static void writeVars(DataOutputStream dout) throws IOException {
        dout.writeUTF(btDeviceName);
        dout.writeUTF(btServiceUrl);
        dout.writeUTF(EMPTY_STRING/*defaultMapPath*/);
        dout.writeInt(x);
        dout.writeInt(y);
        dout.writeInt(dayNight);
        dout.writeUTF(cmsProfile);
        dout.writeBoolean(o2provider);
        dout.writeInt(nokiaBacklightLast);
        dout.writeDouble(lat);
        dout.writeDouble(lon);
        dout.writeDouble(latAny);
        dout.writeDouble(lonAny);
        dout.writeBoolean(wptSessionMode);

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("vars updated");
//#endif
    }

    public static final Vector datums = new Vector(16);
    public static final Hashtable datumMappings = new Hashtable(16);

    public static Datum currentDatum;

    public static void initDefaultDatums() {
        // vars
        final char[] delims = { '{', '}', ',', '=' };
        final CharArrayTokenizer tokenizer = new CharArrayTokenizer();

        // first try built-in
        try {
            initDatums(getResourceAsStream("/resources/datums.txt"), tokenizer, delims);
        } catch (Throwable t) {
            // ignore
        }

        // inject the very basic datum
        Datum.WGS_84 = (Datum) datumMappings.get("map:WGS 84");
    }

    public static void initUserDatums(final Vector resources) {
        if (Config.resourceExist(resources, DATUMS_FILE)) {
            File file = null;
            try {
                file = File.open(Config.getFileURL(Config.FOLDER_RESOURCES, DATUMS_FILE));
                if (file.exists()) {
                    initDatums(file.openInputStream(), new CharArrayTokenizer(),
                               new char[]{ '{', '}', ',', '=' }); // streams gets closed there
                }
            } catch (Throwable t) {
                // ignore
            } finally {
                File.closeQuietly(file);
            }
        }
    }

    private static void initDatums(final InputStream in,
                                   final CharArrayTokenizer tokenizer,
                                   final char[] delims) {
        if (in == null) {
            return;
        }

        LineReader reader = null;
        try {
            reader = new cz.kruch.track.io.LineReader(in);
            CharArrayTokenizer.Token line = reader.readToken(false);
            while (line != null) {
                initDatum(tokenizer, line, delims);
                line = reader.readToken(false);
            }
        } catch (Throwable t) {
            // ignore
        } finally {
            // close reader - closes the stream as well
            LineReader.closeQuietly(reader);
        }
    }

    private static void initDatum(final CharArrayTokenizer tokenizer,
                                  final CharArrayTokenizer.Token token,
                                  final char[] delims) {
        try {
            tokenizer.init(token, delims, false);
            final String datumName = tokenizer.nextTrim();
            final String ellipsoidName = tokenizer.nextTrim();
            final Ellipsoid[] ellipsoids = Ellipsoid.ELLIPSOIDS;
            for (int i = ellipsoids.length; --i >= 0; ) {
                if (ellipsoidName.equals(ellipsoids[i].getName())) {
                    final Ellipsoid ellipsoid = ellipsoids[i];
                    final double dx = tokenizer.nextDouble();
                    final double dy = tokenizer.nextDouble();
                    final double dz = tokenizer.nextDouble();
                    final Datum datum = new Datum(datumName, ellipsoid, dx, dy, dz);
                    datums.addElement(datum);
                    while (tokenizer.hasMoreTokens()) {
                        final String nm = tokenizer.nextTrim();
                        datumMappings.put(nm, datum);
                    }
                    break;
                }
            }
        } catch (Throwable t) {
            // ignore
        }
    }

    private static void closeQuietly(final RecordStore rs) {
        if (rs != null) {
            try {
                rs.closeRecordStore();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
        }
    }

    private static String useDatum(final String id) {
        for (int i = datums.size(); --i >= 0; ) {
            final Datum datum = (Datum) datums.elementAt(i);
            if (id.equals(datum.name)) {
                currentDatum = datum;
                break;
            }
        }

        return id;
    }

    public static Datum getDatum(String id) {
        for (int i = datums.size(); --i >= 0; ) {
            final Datum datum = (Datum) datums.elementAt(i);
            if (id.equals(datum.name)) {
                return datum;
            }
        }

        return null;
    }

    public static String getDataDir() {
        return dataDir;
    }

    public static void setDataDir(String dir) {
        if (dir != null) {
//#ifdef __SYMBIAN__
//            if (cz.kruch.track.TrackingMIDlet.uiq) {
//                try {
//                    dir = new String(dir.getBytes("US-ASCII"));
//                } catch (java.io.UnsupportedEncodingException e) {
//                    // ignore
//                }
//            }
//#endif
            dir = dir.trim();
            if (!dir.endsWith(File.PATH_SEPARATOR)) {
                dir += File.PATH_SEPARATOR;
            }
        }
        dataDir = dir;
    }

    public static String getFolderURL(String folder) {
        return getDataDir().concat(folder);
    }

    public static String getFileURL(String folder, String file) {
        return getFolderURL(folder).concat(file);
    }

    public static String getLocationTimings(final int provider) {
        if (provider == locationProvider && locationTimings != null && locationTimings.length() != 0) {
            return locationTimings;
        }

//#if __ANDROID__ || __CN1__
        String timings = "0";
//#else
        String timings = "1,-1,-1";
//#endif

        switch (provider) {
            case LOCATION_PROVIDER_JSR179: {
                if (cz.kruch.track.TrackingMIDlet.a780) {
                    timings = "2,2,-1"; /* from http://www.kiu.weite-welt.com/de.schoar.blog/?p=186 */
/* Blackberries seem fine with 1,-1,-1
                } else if (cz.kruch.track.TrackingMIDlet.rim) {
                    timings = "2,-1,-1";
*/
                }
            } break;
//#ifdef __ALL__
            case LOCATION_PROVIDER_MOTOROLA: {
                timings = "9999,1,2000";
            } break;
//#endif
        }

        return timings;
    }

    public static void setLocationTimings(String timings) {
        locationTimings = timings;
    }

    public static long rss = -1;

    public static Vector listResources() {
        File dir = null;
        try {
            dir = File.open(Config.getFolderURL(Config.FOLDER_RESOURCES));
            if (dir.exists()) {
                rss = dir.directorySize(false);
            }
        } catch (Exception e) {
            // ignore
        } finally {
            File.closeQuietly(dir);
        }
        return null;
    }

    public static boolean resourceExist(final Vector resources, final String name) {
        return rss > 0;
    }

    private static InputStream getResourceAsStream(final String resource) {
//#ifndef __CN1__
        return Config.class.getResourceAsStream(resource);
//#else
        return com.codename1.ui.FriendlyAccess.getResourceAsStream(resource);
//#endif
    }

    /* The variant bellow is too dangerous for now */

/*
    private static final int RES_CACHE_LIMIT = 32;

    public static Vector listResources() {
        Vector result = null;
        File dir = null;
        try {
            dir = File.open(Config.getFolderURL(Config.FOLDER_RESOURCES));
            result = new Vector(0);
            if (dir.exists()) {
                if ((rss = dir.directorySize(false)) > 0) {
                    result.ensureCapacity(RES_CACHE_LIMIT);
                    final Enumeration seq = dir.list();
                    for ( ; seq.hasMoreElements(); ) {
                        result.addElement(seq.nextElement().toString().toLowerCase());
                        if (result.size() == RES_CACHE_LIMIT) {
                            break;
                        }
                    }
                    if (result.size() > 0 && seq.hasMoreElements()) {
                        result = null;
                    }
                }
            }
        } catch (Throwable t) {
            // ignore
        } finally {
            try {
                dir.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
        }

        return result;
    }

    public static boolean resourceExist(final Vector resources, final String name) {
        return resources == null || resources.contains(name.toLowerCase());
    }
*/
}
