// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.configuration;

import cz.kruch.track.util.CharArrayTokenizer;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.midlet.MIDlet;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Vector;
import java.util.Hashtable;

import api.location.Datum;

public final class Config {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Config");
//#endif

    private static final String[] CONFIGS = new String[]{
        "config_090",
        "vars_090"
    };

    public static final String LOCATION_PROVIDER_JSR82      = "Bluetooth";
    public static final String LOCATION_PROVIDER_JSR179     = "Internal";
    public static final String LOCATION_PROVIDER_SIMULATOR  = "Simulator";

    public static final String TRACKLOG_NEVER  = "never";
    public static final String TRACKLOG_ASK    = "ask";
    public static final String TRACKLOG_ALWAYS = "always";

    public static final String TRACKLOG_FORMAT_NMEA = "NMEA 0183";
    public static final String TRACKLOG_FORMAT_GPX  = "GPX 1.1";

    public static final String COORDS_MAP_LATLON    = "<Map Lat/Lon>";
    public static final String COORDS_MAP_GRID      = "<Map Grid>";
    public static final String COORDS_UTM           = "UTM";
    public static final String COORDS_GC_LATLON     = "Geocaching Lat/Lon";

    private static Config instance;

    private boolean initialized[] = new boolean[]{
        false,
        false
    };

    /*
     * Configuration params, initialized to default values.
     */

    // group [Map]
    protected String mapPath = ""; // no default map

    // group [Map datum]
    protected String geoDatum = Datum.DATUM_WGS_84.getName();

    // group [Provider]
    protected String locationProvider = "";

    // group [common provider options]
    protected String tracklogsOn = TRACKLOG_NEVER;
    protected String tracklogsFormat = TRACKLOG_FORMAT_GPX;
    protected String tracklogsDir = "file:///E:/tracklogs";
    protected String captureLocator = "capture://video";
    protected String captureFormat = "";

    // group [Simulator provider options]
    protected int simulatorDelay = 100;

    // group [Internal provider options]
    protected int locationInterval = 1;

    // group [Location sharing]
    protected boolean locationSharing;

    // group [Desktop]
    protected boolean fullscreen;
    protected boolean noSounds;
    protected boolean decimalPrecision;
    protected boolean osdBasic = true;
    protected boolean osdExtended = true;
    protected boolean osdNoBackground;
    protected boolean osdMediumFont;
    protected boolean osdBoldFont;
    protected boolean osdBlackColor;
    protected boolean hpsWptTrueAzimuth = true;

    // [Coordinates]
    protected boolean useGridFormat;
    protected boolean useUTM;
    protected boolean useGeocachingFormat;

    // group [Tweaks]
    protected boolean optimisticIo;
    protected boolean S60renderer;
    protected boolean cacheOffline;

    // hidden
    protected String btDeviceName = "";
    protected String btServiceUrl = "";
    protected String defaultMapPath = "";
    protected int x = -1;
    protected int y = -1;

    private Config(boolean failSafe) throws ConfigurationException {
        try {
            initialize(0);
        } catch (ConfigurationException e) {
            if (failSafe) {
                initialized[0] = true;
            } else {
                throw e;
            }
        }
        try {
            initialize(1);
        } catch (ConfigurationException e) {
            if (true /*failSafe*/ ) { // always fail-safe init
                initialized[1] = true;
            } else {
                throw e;
            }
        } finally {
            // trick to recognize map loaded upon start as default
            defaultMapPath = mapPath;
        }

        // correct initial values
        if (locationProvider == null || locationProvider.length() == 0) {
            if (cz.kruch.track.TrackingMIDlet.isJsr179()) {
                locationProvider = Config.LOCATION_PROVIDER_JSR179;
            } else if (cz.kruch.track.TrackingMIDlet.isJsr82()) {
                locationProvider = Config.LOCATION_PROVIDER_JSR82;
            } else if (cz.kruch.track.TrackingMIDlet.isFs()) {
                locationProvider = Config.LOCATION_PROVIDER_SIMULATOR;
            }
        }
    }

    public synchronized static Config getInstance() throws ConfigurationException {
        if (instance == null) {
            instance = new Config(false);
        }

        return instance;
    }

    public synchronized static Config getSafeInstance() {
        try {
            return getInstance();
        } catch (ConfigurationException e) {
            if (instance == null) {
                try {
                    instance = new Config(true);
                } catch (ConfigurationException exc) {
                    // should never happen
                }
            }

            return instance;
        }
    }

    protected void readMain(DataInputStream din) throws IOException {
        mapPath = din.readUTF();
        locationProvider = din.readUTF();
/*
            timeZone = din.readUTF();
*/
        din.readUTF(); // unused
        geoDatum = din.readUTF();
/*
            tracklogsOn = din.readUTF();
*/
        boolean oldTracklogsOn = din.readBoolean(); // unused
        tracklogsFormat = din.readUTF();
        tracklogsDir = din.readUTF();
        captureLocator = din.readUTF();
        captureFormat = din.readUTF();
        btDeviceName = din.readUTF();
        btServiceUrl = din.readUTF();
        simulatorDelay = din.readInt();
        locationInterval = din.readInt();
        locationSharing = din.readBoolean();
        fullscreen = din.readBoolean();
        noSounds = din.readBoolean();
        useUTM = din.readBoolean();
        osdExtended = din.readBoolean();
        osdNoBackground = din.readBoolean();
        osdMediumFont = din.readBoolean();
        osdBoldFont = din.readBoolean();
        osdBlackColor = din.readBoolean();

        // 0.9.1 extension
        try {
            tracklogsOn = din.readUTF();
        } catch (Exception e) {
            tracklogsOn = oldTracklogsOn ? Config.TRACKLOG_ASK : Config.TRACKLOG_NEVER;
        }

        // 0.9.2 extension
        try {
            useGeocachingFormat = din.readBoolean();
        } catch (Exception e) {
        }

        // pre 0.9.4 extension
        try {
            optimisticIo = din.readBoolean();
            S60renderer = din.readBoolean();
            cacheOffline = din.readBoolean();
        } catch (Exception e) {
        }

        // 0.9.5 extension
        try {
            decimalPrecision = din.readBoolean();
            useGridFormat = din.readBoolean();
            hpsWptTrueAzimuth = din.readBoolean();
            osdBasic = din.readBoolean();
        } catch (Exception e) {
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.info("configuration read");
//#endif
    }

    protected void writeMain(DataOutputStream dout) throws IOException {
        dout.writeUTF(mapPath);
        dout.writeUTF(locationProvider);
/* bc
            dout.writeUTF(timeZone);
*/
        dout.writeUTF("");
        dout.writeUTF(geoDatum);
/* bc
            dout.writeBoolean(tracklogsOn);
*/
        dout.writeBoolean(false);
        dout.writeUTF(tracklogsFormat);
        dout.writeUTF(tracklogsDir);
        dout.writeUTF(captureLocator);
        dout.writeUTF(captureFormat);
        dout.writeUTF(btDeviceName);
        dout.writeUTF(btServiceUrl);
        dout.writeInt(simulatorDelay);
        dout.writeInt(locationInterval);
        dout.writeBoolean(locationSharing);
        dout.writeBoolean(fullscreen);
        dout.writeBoolean(noSounds);
        dout.writeBoolean(useUTM);
        dout.writeBoolean(osdExtended);
        dout.writeBoolean(osdNoBackground);
        dout.writeBoolean(osdMediumFont);
        dout.writeBoolean(osdBoldFont);
        dout.writeBoolean(osdBlackColor);
        dout.writeUTF(tracklogsOn);
        dout.writeBoolean(useGeocachingFormat);
        dout.writeBoolean(optimisticIo);
        dout.writeBoolean(S60renderer);
        dout.writeBoolean(cacheOffline);
        dout.writeBoolean(decimalPrecision);
        dout.writeBoolean(useGridFormat);
        dout.writeBoolean(hpsWptTrueAzimuth);
        dout.writeBoolean(osdBasic);

//#ifdef __LOG__
        if (log.isEnabled()) log.info("configuration updated");
//#endif
    }

    private void initialize(int idx) throws ConfigurationException {
        if (!initialized[idx]) {
            initialized[idx] = true;

            RecordStore rs = null;
            DataInputStream din = null;

            try {
                // open the store
                rs = RecordStore.openRecordStore(CONFIGS[idx], true,
                                                 RecordStore.AUTHMODE_PRIVATE,
                                                 false);

                // new store? existing store? corrupted store?
                int numRecords = rs.getNumRecords();
                if (numRecords == 0) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.info("new configuration");
//#endif
                } else {
                    din = new DataInputStream(new ByteArrayInputStream(rs.getRecord(1)));
                    if (idx == 0) {
                        readMain(din);
                    } else {
                        readVars(din);
                    }
                }
            } catch (Exception e) {
                throw new ConfigurationException(e);
            } finally {
                if (din != null) {
                    try {
                        din.close();
                    } catch (IOException e) {
                    }
                }
                if (rs != null) {
                    try {
                        rs.closeRecordStore();
                    } catch (RecordStoreException e) {
                    }
                }
            }
        }
    }

    public void update(int idx) throws ConfigurationException {
        if (!initialized[idx]) {
            throw new ConfigurationException("Not initialized");
        }

        RecordStore rs = null;
        DataOutputStream dout = null;

        try {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            dout = new DataOutputStream(data);
            if (idx == 0) {
                writeMain(dout);
            } else {
                writeVars(dout);
            }
            dout.flush();
            byte[] bytes = data.toByteArray();
            rs = RecordStore.openRecordStore(CONFIGS[idx], true,
                                             RecordStore.AUTHMODE_PRIVATE,
                                             true);
            if (rs.getNumRecords() > 0) {
                rs.setRecord(1, bytes, 0, bytes.length);
            } else {
                rs.addRecord(bytes, 0, bytes.length);
            }
        } catch (Exception e) {
            throw new ConfigurationException(e);
        } finally {
            if (dout != null) {
                try {
                    dout.close();
                } catch (IOException e) {
                }
            }
            if (rs != null) {
                try {
                    rs.closeRecordStore();
                } catch (RecordStoreException e) {
                }
            }
        }
    }

    protected void readVars(DataInputStream din) throws IOException {
        btDeviceName = din.readUTF();
        btServiceUrl = din.readUTF();
        defaultMapPath = din.readUTF();
        x = din.readInt();
        y = din.readInt();

//#ifdef __LOG__
        if (log.isEnabled()) log.info("vars read");
//#endif
    }

    protected void writeVars(DataOutputStream dout) throws IOException {
        dout.writeUTF(btDeviceName);
        dout.writeUTF(btServiceUrl);
        dout.writeUTF(defaultMapPath);
        dout.writeInt(x);
        dout.writeInt(y);

//#ifdef __LOG__
        if (log.isEnabled()) log.info("vars updated");
//#endif
    }

    public static Datum[] DATUMS;
    public static Datum currentDatum = Datum.DATUM_WGS_84;
    public static Hashtable datumMappings = new Hashtable();

    public static void initDatums(MIDlet midlet) {
        int idx = 1;

        Vector datums = new Vector();
        datums.addElement(Datum.DATUM_WGS_84);
        datumMappings.put("map:WGS 84", Datum.DATUM_WGS_84);

        initDatum(datums, "AGD 66{Australian National,-133,-48,148}=map:Australian Geodetic 1966");
        initDatum(datums, "CH-1903{Bessel 1841,674,15,405}=map:CH-1903");
        initDatum(datums, "NAD27 (CONUS){Clarke 1866,-8,160,176}=map:NAD27 CONUS");
        initDatum(datums, "OSGB 36{Airy 1830,375,-111,431}=map:Ord Srvy Grt Britn");
        initDatum(datums, "RT 90{Bessel 1841,498,-36,568}=map:RT 90");
        initDatum(datums, "S-42 (Russia){Krassovsky 1940,28,-130,-95}=map:Pulkovo 1942 (1)");

        String s = midlet.getAppProperty("Datum-" + Integer.toString(idx++));
        while (s != null) {
            initDatum(datums, s);
            s = midlet.getAppProperty("Datum-" + Integer.toString(idx++));
        }

        DATUMS = new Datum[datums.size()];
        datums.copyInto(DATUMS);
    }

    private static void initDatum(Vector datums, String s) {
        int i0 = s.indexOf('{');
        int i1 = s.indexOf('}');
        int r = s.indexOf('=');
        if (i1 > i0 && r > i1) {
            CharArrayTokenizer tokenizer = new CharArrayTokenizer();
            String datumName = s.substring(0, i0);
            tokenizer.init(s.substring(i0 + 1, i1).toCharArray(), ',', false);
            String ellipsoidName = tokenizer.next().toString();
            Datum.Ellipsoid ellipsoid = null;
            for (int N = Datum.ELLIPSOIDS.length, i = 0; i < N; i++) {
                if (ellipsoidName.equals(Datum.ELLIPSOIDS[i].getName())) {
                    ellipsoid = Datum.ELLIPSOIDS[i];
                    break;
                }
            }
            if (ellipsoid != null) {
                double dx = CharArrayTokenizer.parseDouble(tokenizer.next());
                double dy = CharArrayTokenizer.parseDouble(tokenizer.next());
                double dz = CharArrayTokenizer.parseDouble(tokenizer.next());
                Datum datum = new Datum(datumName, ellipsoid, dx, dy, dz);
                datums.addElement(datum);

                tokenizer.init(s.substring(r + 1).toCharArray(), ',', false);
                while (tokenizer.hasMoreTokens()) {
                    String nm = tokenizer.next().toString();
                    datumMappings.put(nm, datum);
                }
            }
        }
    }

    public static String useDatum(String id) {
        for (int i = DATUMS.length; --i >= 0; ) {
            if (id.equals(DATUMS[i].getName())) {
                currentDatum = DATUMS[i];
                break;
            }
        }

        return id;
    }

    /*
     * properties getters/setters
     */

    public String[] getLocationProviders() {
        Vector list = new Vector();
        if (cz.kruch.track.TrackingMIDlet.isJsr82()) {
            list.addElement(LOCATION_PROVIDER_JSR82);
        }
        if (cz.kruch.track.TrackingMIDlet.isJsr179()) {
            list.addElement(LOCATION_PROVIDER_JSR179);
        }
        if (cz.kruch.track.TrackingMIDlet.isFs()) {
            list.addElement(LOCATION_PROVIDER_SIMULATOR);
        }
        String[] result = new String[list.size()];
        list.copyInto(result);

        return result;
    }

    public String getMapPath() {
        return mapPath;
    }

    public void setMapPath(String mapPath) {
        this.mapPath = mapPath;
    }

    public String getLocationProvider() {
        return locationProvider;
    }

    public void setLocationProvider(String locationProvider) {
        this.locationProvider = locationProvider;
    }

    public String getTracklogsOn() {
        return tracklogsOn;
    }

    public void setTracklogsOn(String tracklogsOn) {
        this.tracklogsOn = tracklogsOn;
    }

    public String getTracklogsFormat() {
        return tracklogsFormat;
    }

    public void setTracklogsFormat(String tracklogsFormat) {
        this.tracklogsFormat = tracklogsFormat;
    }

    public String getTracklogsDir() {
        return tracklogsDir;
    }

    public void setTracklogsDir(String tracklogsDir) {
        this.tracklogsDir = tracklogsDir;
    }

    public String getCaptureLocator() {
        return captureLocator;
    }

    public void setCaptureLocator(String captureLocator) {
        this.captureLocator = captureLocator;
    }

    public String getCaptureFormat() {
        return captureFormat;
    }

    public void setCaptureFormat(String captureFormat) {
        this.captureFormat = captureFormat;
    }

    public String getBtDeviceName() {
        return btDeviceName;
    }

    public void setBtDeviceName(String btDeviceName) {
        this.btDeviceName = btDeviceName;
    }

    public String getBtServiceUrl() {
        return btServiceUrl;
    }

    public void setBtServiceUrl(String btServiceUrl) {
        this.btServiceUrl = btServiceUrl;
    }

    public int getSimulatorDelay() {
        return simulatorDelay;
    }

    public void setSimulatorDelay(int simulatorDelay) {
        this.simulatorDelay = simulatorDelay;
    }

    public int getLocationInterval() {
        return locationInterval;
    }

    public void setLocationInterval(int locationInterval) {
        this.locationInterval = locationInterval;
    }

    public boolean isLocationSharing() {
        return locationSharing;
    }

    public void setLocationSharing(boolean locationSharing) {
        this.locationSharing = locationSharing;
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    public boolean isNoSounds() {
        return noSounds;
    }

    public void setNoSounds(boolean noSounds) {
        this.noSounds = noSounds;
    }

    public boolean isOsdBasic() {
        return osdBasic;
    }

    public void setOsdBasic(boolean osdBasic) {
        this.osdBasic = osdBasic;
    }

    public boolean isOsdExtended() {
        return osdExtended;
    }

    public void setOsdExtended(boolean osdExtended) {
        this.osdExtended = osdExtended;
    }

    public boolean isOsdNoBackground() {
        return osdNoBackground;
    }

    public void setOsdNoBackground(boolean osdNoBackground) {
        this.osdNoBackground = osdNoBackground;
    }

    public boolean isOsdMediumFont() {
        return osdMediumFont;
    }

    public void setOsdMediumFont(boolean osdMediumFont) {
        this.osdMediumFont = osdMediumFont;
    }

    public boolean isOsdBoldFont() {
        return osdBoldFont;
    }

    public void setOsdBoldFont(boolean osdBoldFont) {
        this.osdBoldFont = osdBoldFont;
    }

    public boolean isOsdBlackColor() {
        return osdBlackColor;
    }

    public void setOsdBlackColor(boolean osdBlackColor) {
        this.osdBlackColor = osdBlackColor;
    }

    public boolean isUseGridFormat() {
        return useGridFormat;
    }

    public void setUseGridFormat(boolean useGrid) {
        this.useGridFormat = useGrid;
    }

    public boolean isUseUTM() {
        return useUTM;
    }

    public void setUseUTM(boolean useUTM) {
        this.useUTM = useUTM;
    }

    public String getGeoDatum() {
        return geoDatum;
    }

    public void setGeoDatum(String geoDatum) {
        this.geoDatum = geoDatum;
    }

    public boolean isUseGeocachingFormat() {
        return useGeocachingFormat;
    }

    public void setUseGeocachingFormat(boolean useGeocachingFormat) {
        this.useGeocachingFormat = useGeocachingFormat;
    }

    public boolean isDecimalPrecision() {
        return decimalPrecision;
    }

    public void setDecimalPrecision(boolean decimalPrecision) {
        this.decimalPrecision = decimalPrecision;
    }

    public boolean isHpsWptTrueAzimuth() {
        return hpsWptTrueAzimuth;
    }

    public void setHpsWptTrueAzimuth(boolean hpsWptTrueAzimuth) {
        this.hpsWptTrueAzimuth = hpsWptTrueAzimuth;
    }

    public boolean isOptimisticIo() {
        return optimisticIo;
    }

    public void setOptimisticIo(boolean optimisticIo) {
        this.optimisticIo = optimisticIo;
    }

    public boolean isS60renderer() {
        return S60renderer;
    }

    public void setS60renderer(boolean s60renderer) {
        S60renderer = s60renderer;
    }

    public boolean isCacheOffline() {
        return cacheOffline;
    }

    public void setCacheOffline(boolean cacheOffline) {
        this.cacheOffline = cacheOffline;
    }

    public String getDefaultMapPath() {
        return defaultMapPath;
    }

    public void setDefaultMapPath(String defaultMapPath) {
        this.defaultMapPath = defaultMapPath;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }
}
