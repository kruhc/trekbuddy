// @LICENSE@

package cz.kruch.track.configuration;

import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.io.LineReader;
import cz.kruch.track.ui.YesNoDialog;

import javax.microedition.rms.RecordStore;
import javax.microedition.midlet.MIDlet;
import javax.microedition.io.Connector;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;
import java.util.Hashtable;
import java.util.Enumeration;

import api.location.Datum;
import api.location.Ellipsoid;
import api.file.File;

/**
 * Represents and handles configuration persisted in RMS.
 *
 * @author Ales Pour <kruhc@seznam.cz>
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
    public static final int COORDS_UTM              = 2;
    public static final int COORDS_GC_LATLON        = 3;

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

    // group [Provider]
    public static int locationProvider      = -1;

    // group [DataDir]
    private static String dataDir;

    // group [common provider options]
    public static int tracklog              = TRACKLOG_NEVER;
    public static String tracklogFormat     = TRACKLOG_FORMAT_GPX;
    public static String captureLocator     = "capture://video";
    public static String snapshotFormat     = EMPTY_STRING;
    public static int snapshotFormatIdx;

    // group [Bluetooth provider options]
    public static int btKeepAlive;

    // group [Simulator provider options]
    public static int simulatorDelay            = 1000; // ms

    // group [Internal provider options]
    private static String locationTimings       = EMPTY_STRING;

    // group [Serial provider options]
    public static String commUrl                = "comm:COM5;baudrate=9600";

    // group [Location sharing]
    public static boolean locationSharing;
    public static boolean autohideNotification  = true;

    // group [O2Germany provider options]
    public static int o2Depth                   = 1;

    // group [Desktop]
    public static boolean fullscreen;
    public static boolean noSounds;
    public static boolean decimalPrecision;
    public static boolean osdBasic              = true;
    public static boolean osdExtended           = true;
    public static boolean osdScale              = true;
    public static boolean osdNoBackground;
    public static boolean osdMediumFont;
    public static boolean osdBoldFont;
    public static boolean osdBlackColor;
    public static boolean hpsWptTrueAzimuth     = true;
    public static boolean safeColors;
    public static int osdAlpha                  = 0x80;
    public static int cmsCycle;

    // [Units]
    public static int units;

    // [Coordinates]
    public static int cfmt;
//    public static boolean useGridFormat;
//    public static boolean useUTM;
//    public static boolean useGeocachingFormat;

    // group [Tweaks]
    public static boolean siemensIo;
    public static boolean S60renderer           = true;
    public static boolean forcedGc;
    public static boolean oneTileScroll;
    public static boolean largeAtlases;
    public static boolean powerSave;

    // group [GPX options]
    public static int gpxDt                     = 60; // 1 min
    public static int gpxDs                     = -1;
    public static boolean gpxOnlyValid          = true;
    public static boolean gpxGsmInfo;

    // group [Trajectory]
    public static boolean trailOn;
    public static int trailColor                = 9;
    public static int trailThick;

    // group [Navigation]
    public static int wptProximity              = 50;
    public static int poiProximity              = 1000;
    public static int routeLineColor;
    public static boolean routeLineStyle;
    public static boolean routePoiMarks         = true;
    public static int routeColor;
    public static int routeThick;
    public static boolean makeRevisions         = true;
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

    private Config() {
    }

    public static Throwable initialize() {

//#ifdef __RIM__
        /* default for Blackberry */
        dataDir = getDefaultDataDir("SDCard/", "TrekBuddy/");
        commUrl = "btspp://000276fd79da:1";
        fullscreen = true;
        safeColors = true;
//#else
        if (cz.kruch.track.TrackingMIDlet.sxg75) {
            dataDir = "file:///fs/tb/";
            fullscreen = true;
        } else if (cz.kruch.track.TrackingMIDlet.siemens) {
            dataDir = "file:///4:/TrekBuddy/";
            fullscreen = true;
        } else if (cz.kruch.track.TrackingMIDlet.lg) {
            dataDir = getDefaultDataDir("Card/", "TrekBuddy/");
            fullscreen = true;
        } else if (cz.kruch.track.TrackingMIDlet.motorola || cz.kruch.track.TrackingMIDlet.a780) {
            dataDir = "file:///b/trekbuddy/";
            forcedGc = true;
        } else if (cz.kruch.track.TrackingMIDlet.samsung) {
            dataDir = "file:///tflash/trekbuddy/";
        } else if (cz.kruch.track.TrackingMIDlet.j9 || cz.kruch.track.TrackingMIDlet.jbed || cz.kruch.track.TrackingMIDlet.intent /*|| cz.kruch.track.TrackingMIDlet.phoneme*/) {
            dataDir = getDefaultDataDir("/Storage Card/", "TrekBuddy/");
            if (cz.kruch.track.TrackingMIDlet.jbed || cz.kruch.track.TrackingMIDlet.intent) {
                commUrl = "socket://127.0.0.1:20175";
            }
        } else if (cz.kruch.track.TrackingMIDlet.uiq) {
            dataDir = getDefaultDataDir("Ms/", "Other/TrekBuddy/");
            fullscreen = true;
        } else { // Nokia, SonyEricsson, ...
            dataDir = getDefaultDataDir("E:/", "TrekBuddy/"); // pstros: "file:///SDCard/TrekBuddy/"
            if (cz.kruch.track.TrackingMIDlet.nokia || cz.kruch.track.TrackingMIDlet.sonyEricsson) {
                fullscreen = true;
            }
        }
//#endif

        Throwable result = null;
        try {
            initialize(CONFIG_090);
        } catch (Throwable t) {
            result = t;
        }
        try {
            initialize(VARS_090);
        } catch (Throwable t) {
            // ignore
        }

        // check DataDir access
        if (File.isFs()) {
            checkDataDirAccess();
        }

        // trick to recognize map loaded upon start as default
        defaultMapPath = mapPath;

        // correct initial values
        if (locationProvider == -1) {
            if (cz.kruch.track.TrackingMIDlet.jsr179) {
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
        String cardRoot = null;
        if (File.isFs()) {
            try {
                for (final Enumeration roots = File.listRoots(); roots.hasMoreElements(); ) {
                    final String root = (String) roots.nextElement();
                    if (root.equals(defaultRoot)) {
                        cardRoot = defaultRoot; break;
                    }
                    if (root.indexOf("Card") > -1 || root.indexOf("Stick") > -1) {
                        cardRoot = root;
                    }
                }
            } catch (Exception e) { // IOE or SE
                // ignore
            }
        }
        if (cardRoot == null) {
            cardRoot = System.getProperty("fileconn.dir.memorycard"); // usually includes protocol schema
            if (cardRoot == null) {
                cardRoot = defaultRoot;
            }
        }

        return (new StringBuffer(cardRoot.startsWith("file://") ? "" : "file:///").append(cardRoot).append(appPath)).toString();
    }

    private static void checkDataDirAccess() {
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

    private static void readMain(DataInputStream din) throws IOException {
        mapPath = din.readUTF();
        String _locationProvider = din.readUTF();
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
        osdMediumFont = din.readBoolean();
        osdBoldFont = din.readBoolean();
        osdBlackColor = din.readBoolean();

        // 0.9.1 extension - obsolete since 0.9.65
        String _tracklog = "never";
        try {
            _tracklog = din.readUTF();
        } catch (Exception e) {
        }

        // 0.9.2 extension
        try {
            /*useGeocachingFormat = */din.readBoolean();
        } catch (Exception e) {
        }

        // pre 0.9.4 extension
        try {
            /*optimisticIo = */din.readBoolean();
            S60renderer = din.readBoolean();
            /*cacheOffline = */din.readBoolean();
        } catch (Exception e) {
        }

        // 0.9.5 extension
        try {
            decimalPrecision = din.readBoolean();
            /*useGridFormat = */din.readBoolean();
            hpsWptTrueAzimuth = din.readBoolean();
            osdBasic = din.readBoolean();
        } catch (Exception e) {
        }

        // 0.9.5x extensions
        boolean _unitsNautical = false, _unitsImperial = false;
        try {
            locationTimings = din.readUTF();
            trailOn = din.readBoolean();
            forcedGc = din.readBoolean();
            oneTileScroll = din.readBoolean();
            /*gpxRaw = */din.readBoolean();
            _unitsNautical = din.readBoolean();
            commUrl = din.readUTF();
            _unitsImperial = din.readBoolean();
            wptProximity = din.readInt();
            poiProximity = din.readInt();
            /*language = */din.readUTF();
            routeLineColor = din.readInt();
            routeLineStyle = din.readBoolean();
            routePoiMarks = din.readBoolean();
            /*scrollingDelay = */din.readInt();
            gpxDt = din.readInt();
            gpxDs = din.readInt();
        } catch (Exception e) {
        }

        // 0.9.63 extensions
        try {
            osdScale = din.readBoolean();
        } catch (Exception e) {
        }

        // 0.9.65 extensions
        try {
            locationProvider = din.readInt();
            tracklog = din.readInt();
            gpxOnlyValid = din.readBoolean();
        } catch (Exception e) {
            if ("Bluetooth".equals(_locationProvider)) {
                locationProvider = Config.LOCATION_PROVIDER_JSR82;
            } else if ("Internal".equals(_locationProvider)) {
                locationProvider = Config.LOCATION_PROVIDER_JSR179;
            } else if ("Serial".equals(_locationProvider)) {
                locationProvider = Config.LOCATION_PROVIDER_SERIAL;
            } else if ("Simulator".equals(_locationProvider)) {
                locationProvider = Config.LOCATION_PROVIDER_SIMULATOR;
            }
            if ("never".equals(_tracklog)) {
                tracklog = TRACKLOG_NEVER;
            } else if ("ask".equals(_tracklog)) {
                tracklog = TRACKLOG_ASK;
            } else if ("always".equals(_tracklog)) {
                tracklog = TRACKLOG_ALWAYS;
            }
        }

        // 0.9.66 extensions
        try {
            units = din.readInt();
        } catch (Exception e) {
            if (_unitsImperial) {
                units = Config.UNITS_IMPERIAL;
            } else if (_unitsNautical) {
                units = Config.UNITS_NAUTICAL;
            }
        }

        // 0.9.69 extensions
        try {
            o2Depth = din.readInt();
            siemensIo = din.readBoolean();
        } catch (Exception e) {
            siemensIo = cz.kruch.track.TrackingMIDlet.siemens && !cz.kruch.track.TrackingMIDlet.sxg75;
        }

        // 0.9.70 extensions
        try {
            largeAtlases = din.readBoolean();
            gpxGsmInfo = din.readBoolean();
        } catch (Exception e) {
        }

        // 0.9.74 extensions
        try {
            osdAlpha = din.readInt();
        } catch (Exception e) {
        }

        // 0.9.77 extensions
        try {
            btKeepAlive = din.readInt();
        } catch (Exception e) {
        }

        // 0.9.78 extensions
        try {
            cmsCycle = din.readInt();
        } catch (Exception e) {
        }

        // 0.9.79 extensions
        try {
            trailColor = din.readInt();
            trailThick = din.readInt();
            routeColor = din.readInt();
            routeThick = din.readInt();
        } catch (Exception e) {
        }

        // 0.9.81 extensions
        try {
            makeRevisions = din.readBoolean();
            autohideNotification = din.readBoolean();
            preferGsName = din.readBoolean();
            safeColors = din.readBoolean();
        } catch (Exception e) {
        }

        // 0.9.82 extensions
        try {
            powerSave = din.readBoolean();
            snapshotFormatIdx = din.readInt();
            cfmt = din.readInt();
            sort = din.readInt();
        } catch (Exception e) {
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.info("configuration read");
//#endif
    }

    private static void writeMain(DataOutputStream dout) throws IOException {
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
        dout.writeInt(-1/*locationInterval*/);
        dout.writeBoolean(locationSharing);
        dout.writeBoolean(fullscreen);
        dout.writeBoolean(noSounds);
        dout.writeBoolean(false/*useUTM*/);
        dout.writeBoolean(osdExtended);
        dout.writeBoolean(osdNoBackground);
        dout.writeBoolean(osdMediumFont);
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
        dout.writeInt(routeLineColor);
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

//#ifdef __LOG__
        if (log.isEnabled()) log.info("configuration updated");
//#endif
    }

    private static void initialize(String rms) throws ConfigurationException {
        RecordStore rs = null;
        DataInputStream din = null;

        try {
            // open the store
            rs = RecordStore.openRecordStore(rms, true,
                                             RecordStore.AUTHMODE_PRIVATE,
                                             false);

            // new store? existing store? corrupted store?
            final int numRecords = rs.getNumRecords();
            if (numRecords == 0) {
//#ifdef __LOG__
                if (log.isEnabled()) log.info("new configuration (" + rms + ")");
//#endif
            } else {
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
    }

    public static void update(String rms) throws ConfigurationException {
        RecordStore rs = null;
        DataOutputStream dout = null;

        try {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            dout = new DataOutputStream(data);
            if (CONFIG_090.equals(rms)) {
                writeMain(dout);
            } else {
                writeVars(dout);
            }
            dout.flush();
            byte[] bytes = data.toByteArray();
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
    }

    public static void initDataDir() {
        cz.kruch.track.maps.io.LoaderIO.getInstance().enqueue(new Config());
    }

    public void run() {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("init datadir");
//#endif

        if (dataDirExists) {
            response(YesNoDialog.NO, null);
        } else {
            (new YesNoDialog(null, new Config(), null,
                             "DataDir '" + getDataDir().substring(8 /* "file:///".length() */) + "' does not exists. Create it?",
                             null)).show();
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
                FOLDER_SOUNDS, FOLDER_TRACKS, FOLDER_WPTS
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
                for (final Enumeration seq = dir.list("wpt.*", false); seq.hasMoreElements(); ) {
                    final String name = (String) seq.nextElement();
                    if (name.endsWith(".amr") || name.endsWith(".wav") || name.endsWith(".mp3")) {
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

    public static void initDatums(MIDlet midlet) {
        // vars
        final char[] delims = { '{', '}', ',', '=' };
        final CharArrayTokenizer tokenizer = new CharArrayTokenizer();

        // first try built-in
        try {
            initDatums(Config.class.getResourceAsStream("/resources/datums.txt"), tokenizer, delims);
        } catch (Throwable t) {
            // ignore
        }

        // next try user's
        if (Config.dataDirExists/* && File.isFs()*/) {
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
        String timings = "1,-1,-1";

//#ifdef __ALL__
        switch (provider) {
            case LOCATION_PROVIDER_JSR179:
                if (cz.kruch.track.TrackingMIDlet.a780) {
                    timings = "2,2,-1"; /* from http://www.kiu.weite-welt.com/de.schoar.blog/?p=186 */
                }
            break;
            case LOCATION_PROVIDER_MOTOROLA:
                timings = "9999,1,2000";
            break;
        }
//#endif

        if (provider == locationProvider && locationTimings != null && locationTimings.length() != 0) {
            return locationTimings;
        }

        return timings;
    }

    public static void setLocationTimings(String timings) {
        locationTimings = timings;
    }
}
