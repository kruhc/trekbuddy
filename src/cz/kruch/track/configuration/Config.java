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

package cz.kruch.track.configuration;

import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.TrackingMIDlet;
import cz.kruch.track.io.LineReader;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
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

import api.location.Datum;
import api.file.File;

/**
 * Represents and handles configuration persisted in RMS.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class Config {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Config");
//#endif

    /* known providers */
    public static final String LOCATION_PROVIDER_JSR82      = "Bluetooth";
    public static final String LOCATION_PROVIDER_JSR179     = "Internal";
    public static final String LOCATION_PROVIDER_SERIAL     = "Serial";
    public static final String LOCATION_PROVIDER_SIMULATOR  = "Simulator";
    public static final String LOCATION_PROVIDER_MOTOROLA   = "Motorola";

    /* tracklog options */
    public static final String TRACKLOG_NEVER  = "never";
    public static final String TRACKLOG_ASK    = "ask";
    public static final String TRACKLOG_ALWAYS = "always";

    /* tracklog format */
    public static final String TRACKLOG_FORMAT_NMEA = "NMEA 0183";
    public static final String TRACKLOG_FORMAT_GPX  = "GPX 1.1";

    /* coordinate format */
    public static final String COORDS_MAP_LATLON    = "<Map Lat/Lon>";
    public static final String COORDS_MAP_GRID      = "<Map Grid>";
    public static final String COORDS_UTM           = "UTM";
    public static final String COORDS_GC_LATLON     = "Geocaching Lat/Lon";

    /* units */
    public static final String UNITS_METRIC        = "metric";
    public static final String UNITS_IMPERIAL      = "imperial";
    public static final String UNITS_NAUTICAL      = "nautical";

    /* datadir folders */
    public static final String FOLDER_MAPS      = "maps/";
    public static final String FOLDER_NMEA      = "tracks-nmea/";
    public static final String FOLDER_TRACKS    = "tracks-gpx/";
    public static final String FOLDER_WPTS      = "wpts/";
    public static final String FOLDER_PROFILES  = "ui-profiles/";
    public static final String FOLDER_RESOURCES = "resources/";
    public static final String FOLDER_SOUNDS    = "sounds/";

    /* rms stores */
    public static final String VARS_090         = "vars_090";
    public static final String CONFIG_090       = "config_090";

    private static final String EMPTY_STRING    = "";

    /*
     * Configuration params, initialized to default values.
     */

    // group [Map]
    public static String mapPath            = EMPTY_STRING; // file:///E:/trekbuddy/maps/GPSBasti/pasing.tar";

/*
    // group [Language]
    public static String language           = L10n.ENGLISH;
*/

    // group [Map datum]
    public static String geoDatum           = Datum.DATUM_WGS_84.getName();

    // group [Provider]
    public static String locationProvider   = EMPTY_STRING;

    // group [DataDir]
    private static String dataDir           = "file:///E:/TrekBuddy/";

    // group [common provider options]
    public static String tracklog           = TRACKLOG_NEVER;
    public static String tracklogFormat     = TRACKLOG_FORMAT_GPX;
    public static String captureLocator     = "capture://video";
    public static String captureFormat      = EMPTY_STRING;

    // group [Simulator provider options]
    public static int simulatorDelay        = 1000;

    // group [Internal provider options]
    private static String locationTimings   = EMPTY_STRING;

    // group [Serial provider options]
    public static String commUrl            = "comm:COM0;baudrate=9600";

    // group [Location sharing]
    public static boolean locationSharing;

    // group [Desktop]
    public static boolean fullscreen            = true;
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

    // [Units]
    public static boolean unitsNautical;
    public static boolean unitsImperial;

    // [Coordinates]
    public static boolean useGridFormat;
    public static boolean useUTM;
    public static boolean useGeocachingFormat;

    // group [Tweaks]
    public static boolean optimisticIo;
    public static boolean S60renderer;
/* obsolete
    public static boolean cacheOffline;
*/
    public static boolean forcedGc              = true;
    public static boolean oneTileScroll;

    // group [GPX options]
    public static int gpxDt = 60; // 1 min
    public static int gpxDs = -1;

    // group [Trajectory]
    public static boolean trajectoryOn;

    // group [Navigation]
    public static int wptProximity = 50;
    public static int poiProximity = 1000;
    public static int routeLineColor = 0x0;
    public static boolean routeLineStyle;
    public static boolean routePoiMarks = true;

/*
    // group [ScrollDelay]
    public static int scrollingDelay = 0;
*/

    // hidden
    public static String btDeviceName   = EMPTY_STRING;
    public static String btServiceUrl   = EMPTY_STRING;
    public static String defaultMapPath = EMPTY_STRING;
    public static int x = -1;
    public static int y = -1;
    public static int dayNight;

    public static Throwable initialize() {

//#ifdef __RIM__
        /* default for Blackberry */
        dataDir = "file:///SDCard/TrekBuddy/";
        S60renderer = true;
        forcedGc = false;
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

        // trick to recognize map loaded upon start as default
        defaultMapPath = mapPath;

        // gc
        System.gc();

        // correct initial values
        if (locationProvider == null || locationProvider.length() == 0) {
            if (cz.kruch.track.TrackingMIDlet.jsr179) {
                locationProvider = Config.LOCATION_PROVIDER_JSR179;
            } else if (cz.kruch.track.TrackingMIDlet.jsr82) {
                locationProvider = Config.LOCATION_PROVIDER_JSR82;
            } else if (cz.kruch.track.TrackingMIDlet.isFs()) {
                locationProvider = Config.LOCATION_PROVIDER_SIMULATOR;
            } else if (cz.kruch.track.TrackingMIDlet.hasPorts()) {
                locationProvider = Config.LOCATION_PROVIDER_SERIAL;
            }
        }

        return result;
    }

    private static void readMain(DataInputStream din) throws IOException {
        mapPath = din.readUTF();
        locationProvider = din.readUTF();
        /*timeZone = */din.readUTF();
        geoDatum = din.readUTF();
        /*tracklogsOn = */din.readBoolean(); // bc
        tracklogFormat = din.readUTF();
        dataDir = din.readUTF();
        captureLocator = din.readUTF();
        captureFormat = din.readUTF();
        btDeviceName = din.readUTF();
        btServiceUrl = din.readUTF();
        simulatorDelay = din.readInt();
        /*locationInterval = */din.readInt();
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
            tracklog = din.readUTF();
        } catch (Exception e) {
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
            /*cacheOffline = */din.readBoolean();
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

        // 0.9.5x extensions
        try {
            locationTimings = din.readUTF();
            trajectoryOn = din.readBoolean();
            forcedGc = din.readBoolean();
            oneTileScroll = din.readBoolean();
            /*gpxRaw = */din.readBoolean();
            unitsNautical = din.readBoolean();
            commUrl = din.readUTF();
            unitsImperial = din.readBoolean();
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

//#ifdef __LOG__
        if (log.isEnabled()) log.info("configuration read");
//#endif
    }

    private static void writeMain(DataOutputStream dout) throws IOException {
        dout.writeUTF(mapPath);
        dout.writeUTF(locationProvider);
        dout.writeUTF(""/*timeZone*/);
        dout.writeUTF(geoDatum);
        dout.writeBoolean(false/*tracklogsOn*/);
        dout.writeUTF(tracklogFormat);
        dout.writeUTF(dataDir);
        dout.writeUTF(captureLocator);
        dout.writeUTF(captureFormat);
        dout.writeUTF(btDeviceName);
        dout.writeUTF(btServiceUrl);
        dout.writeInt(simulatorDelay);
        dout.writeInt(-1/*locationInterval*/);
        dout.writeBoolean(locationSharing);
        dout.writeBoolean(fullscreen);
        dout.writeBoolean(noSounds);
        dout.writeBoolean(useUTM);
        dout.writeBoolean(osdExtended);
        dout.writeBoolean(osdNoBackground);
        dout.writeBoolean(osdMediumFont);
        dout.writeBoolean(osdBoldFont);
        dout.writeBoolean(osdBlackColor);
        dout.writeUTF(tracklog);
        dout.writeBoolean(useGeocachingFormat);
        dout.writeBoolean(optimisticIo);
        dout.writeBoolean(S60renderer);
        dout.writeBoolean(false/*cacheOffline*/);
        dout.writeBoolean(decimalPrecision);
        dout.writeBoolean(useGridFormat);
        dout.writeBoolean(hpsWptTrueAzimuth);
        dout.writeBoolean(osdBasic);
        dout.writeUTF(locationTimings);
        dout.writeBoolean(trajectoryOn);
        dout.writeBoolean(forcedGc);
        dout.writeBoolean(oneTileScroll);
        dout.writeBoolean(false/*gpxRaw*/);
        dout.writeBoolean(unitsNautical);
        dout.writeUTF(commUrl);
        dout.writeBoolean(unitsImperial);
        dout.writeInt(wptProximity);
        dout.writeInt(poiProximity);
        dout.writeUTF(""/*language*/); // bc
        dout.writeInt(routeLineColor);
        dout.writeBoolean(routeLineStyle);
        dout.writeBoolean(routePoiMarks);
        dout.writeInt(0/*scrollingDelay*/);
        dout.writeInt(gpxDt);
        dout.writeInt(gpxDs);
        dout.writeBoolean(osdScale);

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
            int numRecords = rs.getNumRecords();
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
            if (din != null) {
                try {
                    din.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            if (rs != null) {
                try {
                    rs.closeRecordStore();
                } catch (RecordStoreException e) {
                    // ignore
                }
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
            if (dout != null) {
                try {
                    dout.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            if (rs != null) {
                try {
                    rs.closeRecordStore();
                } catch (RecordStoreException e) {
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

//#ifdef __LOG__
        if (log.isEnabled()) log.info("vars updated");
//#endif
    }

    public static Datum[] DATUMS;
    public static Datum currentDatum = Datum.DATUM_WGS_84;
    public static final Hashtable datumMappings = new Hashtable();

    public static void initDatums(MIDlet midlet) {
        int idx = 1;
        char[] delims = { '{', '}', ',', '=' };
        Vector datums = new Vector();
        CharArrayTokenizer tokenizer = new CharArrayTokenizer();

        // WGS-84 is hardcoded
        datums.addElement(Datum.DATUM_WGS_84);
        datumMappings.put("map:WGS 84", Datum.DATUM_WGS_84);

        // first try built-in
        try {
            initDatums(tokenizer, delims, datums, TrackingMIDlet.class.getResourceAsStream("/resources/datums.txt"));
        } catch (Throwable t) {
            // ignore
        }

        // next try user's
        try {
            initDatums(tokenizer, delims, datums, Connector.openInputStream(Config.getFolderResources() + "datums.txt"));
        } catch (Throwable t) {
            // ignore
        }

        // lastly try JAD
        CharArrayTokenizer.Token t = new CharArrayTokenizer.Token();
        String s = midlet.getAppProperty("Datum-" + Integer.toString(idx++));
        while (s != null) {
            t.init(s.toCharArray(), 0, s.length());
            initDatum(tokenizer, delims, datums, t);
            s = midlet.getAppProperty("Datum-" + Integer.toString(idx++));
        }

        // release resources
        tokenizer.dispose();

        // finalize datum array
        DATUMS = new Datum[datums.size()];
        datums.copyInto(DATUMS);
    }

    private static void initDatums(CharArrayTokenizer tokenizer, char[] delims,
                                   Vector datums, InputStream in) {
        if (in == null) {
            return;
        }

        LineReader reader = null;
        try {
            reader = new cz.kruch.track.io.LineReader(in);
            CharArrayTokenizer.Token line = reader.readToken(false);
            while (line != null) {
                initDatum(tokenizer, delims, datums, line);
                line = reader.readToken(false);
            }
        } catch (Throwable t) {
            // ignore
        } finally {
            // close reader - closes the stream as well
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private static void initDatum(CharArrayTokenizer tokenizer, char[] delims,
                                  Vector datums, CharArrayTokenizer.Token token) {
        try {
            tokenizer.init(token, delims, false);
            String datumName = tokenizer.next().toString();
            String ellipsoidName = tokenizer.next().toString();
            Datum.Ellipsoid ellipsoid = null;
            Datum.Ellipsoid[] ellipsoids = Datum.ELLIPSOIDS;
            for (int i = ellipsoids.length; --i >= 0; ) {
                if (ellipsoidName.equals(ellipsoids[i].getName())) {
                    ellipsoid = ellipsoids[i];
                    break;
                }
            }
            if (ellipsoid != null) {
                double dx = tokenizer.nextDouble();
                double dy = tokenizer.nextDouble();
                double dz = tokenizer.nextDouble();
                Datum datum = new Datum(datumName, ellipsoid, dx, dy, dz);
                datums.addElement(datum);

                while (tokenizer.hasMoreTokens()) {
                    String nm = tokenizer.next().toString();
                    datumMappings.put(nm, datum);
                }
            }
        } catch (Throwable t) {
            // ignore
        }
    }

    public static String useDatum(String id) {
        Datum[] datums = DATUMS;
        for (int i = datums.length; --i >= 0; ) {
            if (id.equals(datums[i].getName())) {
                currentDatum = datums[i];
                break;
            }
        }

        return id;
    }

    /*
     * properties getters/setters
     */

    public static String[] getLocationProviders() {
        Vector list = new Vector();
        if (cz.kruch.track.TrackingMIDlet.jsr82) {
            list.addElement(LOCATION_PROVIDER_JSR82);
        }
        if (cz.kruch.track.TrackingMIDlet.jsr179) {
            list.addElement(LOCATION_PROVIDER_JSR179);
        }
        if (cz.kruch.track.TrackingMIDlet.hasPorts()) {
            list.addElement(LOCATION_PROVIDER_SERIAL);
        }
        if (cz.kruch.track.TrackingMIDlet.isFs()) {
            list.addElement(LOCATION_PROVIDER_SIMULATOR);
        }
//#ifdef __A1000__
        if (cz.kruch.track.TrackingMIDlet.motorola179) {
            list.addElement(LOCATION_PROVIDER_MOTOROLA);
        }
//#endif
        String[] result = new String[list.size()];
        list.copyInto(result);

        return result;
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

    public static String getFolderTracks() {
        return getDataDir() + FOLDER_TRACKS;
    }

    public static String getFolderNmea() {
        return getDataDir() + FOLDER_NMEA;
    }

    public static String getFolderWaypoints() {
        return getDataDir() + FOLDER_WPTS;
    }

    public static String getFolderProfiles() {
        return getDataDir() + FOLDER_PROFILES;
    }

    public static String getFolderResources() {
        return getDataDir() + FOLDER_RESOURCES;
    }

    public static String getFolderSounds() {
        return getDataDir() + FOLDER_SOUNDS;
    }

    public static String getLocationTimings() {
        if (locationTimings == null || locationTimings.length() == 0) {
//#ifdef __A780__
            if (cz.kruch.track.TrackingMIDlet.a780) {
                locationTimings = "2,2,-1"; /* from http://www.kiu.weite-welt.com/de.schoar.blog/?p=186 */
            } else
//#endif
//#ifdef __A1000__
            if (LOCATION_PROVIDER_MOTOROLA.equals(locationProvider)) {
                locationTimings = "9999,1,2000";
            } else
//#endif            
            {
                locationTimings = "1,-1,-1";
            }
        }

        return locationTimings;
    }

    public static void setLocationTimings(String timings) {
        locationTimings = timings;
    }
}
