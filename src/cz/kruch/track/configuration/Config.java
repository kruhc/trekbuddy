// @LICENSE@

package cz.kruch.track.configuration;

import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.util.Worker;
import cz.kruch.track.io.LineReader;
import cz.kruch.track.ui.YesNoDialog;

import javax.microedition.rms.RecordStore;
import javax.microedition.midlet.MIDlet;
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

    /* units */
    public static final int UNITS_METRIC            = 0;
    public static final int UNITS_IMPERIAL          = 1;
    public static final int UNITS_NAUTICAL          = 2;

    /* datadir folders */
    public static final String FOLDER_MAPS         = "maps/";
    public static final String FOLDER_NMEA         = "tracks-nmea/";
    public static final String FOLDER_TRACKS       = "tracks-gpx/";
    public static final String FOLDER_WPTS         = "wpts/";
    public static final String FOLDER_PROFILES     = "ui-profiles/";
    public static final String FOLDER_RESOURCES    = "resources/";
    public static final String FOLDER_SOUNDS       = "sounds/";
    public static final String FOLDER_GC           = "gc/";

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

    public static final String EMPTY_STRING        = "";

    /*
     * Configuration params, initialized to default values.
     */

    // group [Map]
    public static String mapPath            = EMPTY_STRING;

    // group [Map datum]
    public static String geoDatum           = "WGS 84";

    // group [Startup screen]
    public static int startupScreen;

    // group [Provider]
    public static int locationProvider      = -1;

    // group [DataDir]
    public static String dataDir;

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
    public static int altCorrection;
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
    public static int desktopFontSize;
    public static int osdAlpha                  = 0x80;
    public static int cmsCycle;
    public static int listFont                  = 0x200008;

    // [Units]
    public static int units;

    // [Coordinates]
    public static int cfmt;

    // group [Tweaks]
    public static boolean siemensIo;
    public static boolean S60renderer           = true;
    public static boolean forcedGc;
    public static boolean oneTileScroll;
    public static boolean largeAtlases;
    public static boolean powerSave;
    public static boolean reliableInput;
    public static boolean hideBarCmd;
    public static boolean useNativeService;
    public static boolean lazyGpxParsing;
    public static boolean lowmemIo;

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
    public static boolean routeLineStyle;
    public static boolean routePoiMarks         = true;
    public static int routeColor                = 6;
    public static int routeThick;
    public static boolean makeRevisions;
    public static boolean preferGsName          = true;
    public static int sort;

    // hidden
    public static String btDeviceName   = EMPTY_STRING;
    public static String btServiceUrl   = EMPTY_STRING;
    public static String defaultMapPath = EMPTY_STRING;
    public static int x = -1;
    public static int y = -1;
    public static int dayNight;
    public static String cmsProfile     = EMPTY_STRING;
    public static boolean o2provider;

    // runtime (not persisted)
    public static boolean dataDirAccess, dataDirExists;
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

    public static Worker worker;

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

//#elifdef __ANDROID__

        /* default for Android (MicroEmu) */
        dataDir = getDefaultDataDir("file:///sdcard/", "TrekBuddy/");
        listFont = 0x200010;

//#else

        if (cz.kruch.track.TrackingMIDlet.sxg75) {
            dataDir = getDefaultDataDir("file:///fs/", "tb/");
            fullscreen = true;
            altCorrection = -540;
        } else if (cz.kruch.track.TrackingMIDlet.brew) {
            dataDir = getDefaultDataDir("file:///fs/", "tb/");
            altCorrection = -540;
        } else if (cz.kruch.track.TrackingMIDlet.siemens) {
            dataDir = getDefaultDataDir("file:///4:/", "TrekBuddy/");
            fullscreen = true;
        } else if (cz.kruch.track.TrackingMIDlet.lg) {
            dataDir = getDefaultDataDir("file:///Card/", "TrekBuddy/");
            fullscreen = true;
        } else if (cz.kruch.track.TrackingMIDlet.motorola || cz.kruch.track.TrackingMIDlet.a780) {
            dataDir = getDefaultDataDir("file:///b/", "trekbuddy/");
            forcedGc = true;
        } else if (cz.kruch.track.TrackingMIDlet.samsung) {
            dataDir = getDefaultDataDir("file:///mmc/", "trekbuddy/");
        } else if (cz.kruch.track.TrackingMIDlet.j9 || cz.kruch.track.TrackingMIDlet.jbed || cz.kruch.track.TrackingMIDlet.intent /*|| cz.kruch.track.TrackingMIDlet.phoneme*/) {
            dataDir = getDefaultDataDir("file:///Storage Card/", "TrekBuddy/");
            if (cz.kruch.track.TrackingMIDlet.jbed || cz.kruch.track.TrackingMIDlet.intent) {
                commUrl = "socket://localhost:20175";
            }
        } else if (cz.kruch.track.TrackingMIDlet.uiq) {
            dataDir = getDefaultDataDir("file:///Ms/", "Other/TrekBuddy/");
            fullscreen = true;
        } else { // Nokia, SonyEricsson, ...
            dataDir = getDefaultDataDir("file:///E:/", "TrekBuddy/"); // pstros: "file:///SDCard/TrekBuddy/"
            fullscreen = cz.kruch.track.TrackingMIDlet.nokia || cz.kruch.track.TrackingMIDlet.sonyEricsson || cz.kruch.track.TrackingMIDlet.symbian;
        }
        if (cz.kruch.track.TrackingMIDlet.symbian) {
            useNativeService = true; // !cz.kruch.track.TrackingMIDlet.s60rdfp2;
        }

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

        // trick to recognize map loaded upon start as default
        defaultMapPath = mapPath;

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
        mapPath = din.readUTF();
        /*String _locationProvider = */din.readUTF();
        /*timeZone = */din.readUTF();
        geoDatum = din.readUTF();
        /*tracklogsOn = */din.readBoolean(); // bc
        tracklogFormat = din.readUTF();
        dataDir = din.readUTF();
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
        S60renderer = din.readBoolean();
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

        try {
            // 0.9.66 extensions
            units = din.readInt();

            // 0.9.69 extensions
            o2Depth = din.readInt();
            siemensIo = din.readBoolean();

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

        } catch (Exception e) {
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.info("configuration read");
//#endif
    }

    private static void writeMain(final DataOutputStream dout) throws IOException {
        dout.writeUTF(mapPath);
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
        dout.writeBoolean(S60renderer);
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
        dout.writeBoolean(siemensIo);
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
        dout.writeInt(altCorrection);
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

//#ifdef __LOG__
        if (log.isEnabled()) log.info("configuration updated");
//#endif
    }

    private static void dump() {
        File f = null;
        DataOutputStream os = null;
        try {
            f = File.open(getFolderURL(FOLDER_RESOURCES) + "settings.dat", Connector.READ_WRITE);
            if (f.exists()) {
                f.delete();
            }
            f.create();
            os = new DataOutputStream(f.openOutputStream());
            writeMain(os);
            os.flush();
        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
        } finally {
            try {
                os.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
            try {
                f.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
        }
    }

    private static int initialize(final String rms) throws ConfigurationException {
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
                } else {
                    readVars(din);
                }
            }
        } catch (Exception e) {
            throw new ConfigurationException(e);
        } finally {
            try {
                din.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
            try {
                rs.closeRecordStore();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
        }

        return result;
    }

    public static void fallback() {
        if (dataDirExists) {
            File f = null;
            DataInputStream in = null;
            try {
                f = File.open(getFolderURL(FOLDER_RESOURCES) + "settings.dat", Connector.READ);
                if (f.exists()) {
                    in = new DataInputStream(f.openInputStream());
                    readMain(in);
                }
            } catch (Throwable t) {
//#ifdef __LOG__
                t.printStackTrace();
//#endif
            } finally {
                try {
                    in.close();
                } catch (Exception e) { // NPE or IOE
                    // ignore
                }
                try {
                    f.close();
                } catch (Exception e) { // NPE or IOE
                    // ignore
                }
            }
        }
    }

    public static void update(final String rms) throws ConfigurationException {
        RecordStore rs = null;
        DataOutputStream dout = null;

        try {
            final ByteArrayOutputStream data = new ByteArrayOutputStream();
            dout = new DataOutputStream(data);
            if (CONFIG_090.equals(rms)) {
                writeMain(dout);
            } else {
                writeVars(dout);
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
            try {
                dout.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
            try {
                rs.closeRecordStore();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
        }

        if (CONFIG_090.equals(rms)) {
//#ifdef __RIM__
            cz.kruch.track.ui.nokia.DeviceControl.saveAltDatadir();
//#endif                
            if (dataDirExists) {
                worker.enqueue(new Config(ACTION_PERSISTCFG));
            }
        }
    }

    public static void checkDataDir(final int configured) {
//#ifdef __RIM__
        if (configured == 0) {
            cz.kruch.track.ui.nokia.DeviceControl.loadAltDatadir();
        }
//#endif
        File dir = null;
        try {
            dir = File.open(Config.dataDir);
            dataDirAccess = true;
            dataDirExists = dir.exists();
        } catch (Exception e) { // IOE or SE
            // ignore
        } finally {
            try {
                dir.close();
            } catch (Exception e) { // IOE or NPE
                // ignore
            }
        }
    }

    public static void initDataDir() {
        worker.enqueue(new Config(ACTION_INITDATADIR));
    }

    public void run() {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("run; action = " + action);
//#endif

        switch (action) {
            case ACTION_INITDATADIR: {
                if (dataDirExists) {
                    response(YesNoDialog.NO, null);
                } else {
                    (new YesNoDialog(this, null,
                                     "DataDir '" + getDataDir().substring(8 /* "file:///".length() */) + "' does not exists. Create it?",
                                     null)).show();
                }
            } break;
            case ACTION_PERSISTCFG: {
                dump();
            } break;
        }
    }

    public void response(int answer, Object closure) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("response; answer: " + answer + "; closure " + closure);
//#endif
        if (answer == YesNoDialog.YES) {
            File datadir = null;
            try {
                datadir = File.open(getDataDir(), Connector.WRITE);
                datadir.mkdir();
                dataDirExists = true;
            } catch (Exception e) {
                cz.kruch.track.ui.Desktop.showError("Failed to create " + getDataDir().substring(8 /* "file:///".length() */),
                                                    e, null);
            } finally {
                try {
                    datadir.close();
                } catch (Exception e) { // NPE or IOE
                    // ignore
                }
            }
        }
        if (dataDirExists) {
            final String[] folders = {
                FOLDER_MAPS, FOLDER_NMEA, FOLDER_PROFILES, FOLDER_RESOURCES,
                FOLDER_SOUNDS, FOLDER_TRACKS, FOLDER_WPTS, FOLDER_GC
            };
            /* create folder structure */
            for (int i = folders.length; --i >= 0; ) {
                File folder = null;
                try {
                    folder = File.open(getFolderURL(folders[i]), Connector.READ_WRITE);
                    if (!folder.exists()) {
//#ifdef __LOG__
                        if (log.isEnabled()) log.info("creating subfolder " + folders[i]);
//#endif
                        folder.mkdir();
                    }
                } catch (Exception e) {
                    // ignore // TODO really?!?
//#ifdef __LOG__
                    if (log.isEnabled()) log.error("failed to create subfolder " + folders[i], e);
//#endif
                } finally {
                    try {
                        folder.close();
                    } catch (Exception e) { // NPE or IOE
                        // ignore
                    }
                }
            }
            /* find default files */
            File dir = null;
            try {
                dir = File.open(Config.getFolderURL(Config.FOLDER_SOUNDS));
                for (final Enumeration seq = dir.list(); seq.hasMoreElements(); ) {
                    final String name = (String) seq.nextElement();
                    final String candidate = name.toLowerCase();
                    if (candidate.startsWith("wpt.") && (candidate.endsWith(".amr") || candidate.endsWith(".wav") || candidate.endsWith(".mp3") || candidate.endsWith(".aac")|| candidate.endsWith(".m4a")|| candidate.endsWith(".3gp"))) {
//#ifdef __LOG__
                        if (log.isEnabled()) log.info("found wpt sound file " + name);
//#endif
                        defaultWptSound = name;
                        break;
                    }
                }
            } catch (Exception e) {
                // ignore
//#ifdef __LOG__
                if (log.isEnabled()) log.info("could not list sounds: " + e);
//#endif
            } finally {
                try {
                    dir.close();
                } catch (Exception e) { // NPE or IOE
                    // ignore
                }
            }
        }
    }

    private static void readVars(DataInputStream din) throws IOException {
        btDeviceName = din.readUTF();
        btServiceUrl = din.readUTF();
        defaultMapPath = din.readUTF();
        x = din.readInt();
        y = din.readInt();
        dayNight = din.readInt();
        cmsProfile = din.readUTF();
        o2provider = din.readBoolean();

//#ifdef __LOG__
        if (log.isEnabled()) log.info("vars read");
//#endif
    }

    private static void writeVars(DataOutputStream dout) throws IOException {
        dout.writeUTF(btDeviceName);
        dout.writeUTF(btServiceUrl);
        dout.writeUTF(defaultMapPath);
        dout.writeInt(x);
        dout.writeInt(y);
        dout.writeInt(dayNight);
        dout.writeUTF(cmsProfile);
        dout.writeBoolean(o2provider);

//#ifdef __LOG__
        if (log.isEnabled()) log.info("vars updated");
//#endif
    }

    public static final Vector datums = new Vector(16);
    public static final Hashtable datumMappings = new Hashtable(16);

    public static Datum currentDatum;

    public static void initDefaultDatums(MIDlet midlet) {
        // vars
        final char[] delims = { '{', '}', ',', '=' };
        final CharArrayTokenizer tokenizer = new CharArrayTokenizer();

        // first try built-in
        try {
            initDatums(Config.class.getResourceAsStream("/resources/datums.txt"), tokenizer, delims);
        } catch (Throwable t) {
            // ignore
        }

        // lastly try JAD
        int idx = 1;
        CharArrayTokenizer.Token token = new CharArrayTokenizer.Token();
        String s = midlet.getAppProperty("Datum-" + Integer.toString(idx++));
        while (s != null) {
            token.init(s.toCharArray(), 0, s.length());
            initDatum(tokenizer, token, delims);
            s = midlet.getAppProperty("Datum-" + Integer.toString(idx++));
        }

        // inject the very basic datum
        Datum.WGS_84 = (Datum) datumMappings.get("map:WGS 84");

        // setup defaults
        useDatum(geoDatum);
    }

    public static void initUserDatums() {
        // vars
        final char[] delims = { '{', '}', ',', '=' };
        final CharArrayTokenizer tokenizer = new CharArrayTokenizer();

        // next try user's
        if (Config.dataDirExists) {
            File file = null;
            try {
                file = File.open(Config.getFolderURL(Config.FOLDER_RESOURCES) + "datums.txt");
                if (file.exists()) {
                    initDatums(file.openInputStream(), tokenizer, delims);
                }
            } catch (Throwable t) {
                // ignore
            } finally {
                try {
                    file.close();
                } catch (Exception e) { // IOE or NPE
                    // ignore
                }
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
                line = null; // gc hint
                line = reader.readToken(false);
            }
        } catch (Throwable t) {
            // ignore
        } finally {
            // close reader - closes the stream as well
            try {
                reader.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
        }
    }

    private static void initDatum(final CharArrayTokenizer tokenizer,
                                  final CharArrayTokenizer.Token token,
                                  final char[] delims) {
        try {
            tokenizer.init(token, delims, false);
            final String datumName = tokenizer.next().toString();
            final String ellipsoidName = tokenizer.next().toString();
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
                        final String nm = tokenizer.next().toString();
                        datumMappings.put(nm, datum);
                    }
                    break;
                }
            }
        } catch (Throwable t) {
            // ignore
        }
    }

    public static String useDatum(final String id) {
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
        if (!File.isDir(dataDir)) { // make sure it ends with '/'
            dataDir += File.PATH_SEPARATOR;
        }
        return dataDir;
    }

    public static void setDataDir(String dir) {
        dataDir = dir;
    }

    public static String getFolderURL(String folder) {
        return getDataDir() + folder;
    }

    public static String getLocationTimings(final int provider) {
        if (provider == locationProvider && locationTimings != null && locationTimings.length() != 0) {
            return locationTimings;
        }

        String timings = "1,-1,-1";

        switch (provider) {
            case LOCATION_PROVIDER_JSR179:
                if (cz.kruch.track.TrackingMIDlet.a780) {
                    timings = "2,2,-1"; /* from http://www.kiu.weite-welt.com/de.schoar.blog/?p=186 */
/* Blackberries seem fine with 1,-1,-1 
                } else if (cz.kruch.track.TrackingMIDlet.rim) {
                    timings = "2,-1,-1";
*/
                }
            break;
//#ifdef __ALL__
            case LOCATION_PROVIDER_MOTOROLA:
                timings = "9999,1,2000";
            break;
//#endif
        }

        return timings;
    }

    public static void setLocationTimings(String timings) {
        locationTimings = timings;
    }
}
