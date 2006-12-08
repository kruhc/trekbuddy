// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.configuration;

//#ifdef __LOG__
import cz.kruch.track.util.Logger;
//#endif
import cz.kruch.track.util.Datum;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Vector;

public abstract class Config {
//#ifdef __LOG__
    private static final Logger log = new Logger("Config");
//#endif

    public static final String LOCATION_PROVIDER_JSR82      = "Bluetooth";
    public static final String LOCATION_PROVIDER_JSR179     = "Internal";
    public static final String LOCATION_PROVIDER_SIMULATOR  = "Simulator";

    public static final String TRACKLOG_NEVER  = "never";
    public static final String TRACKLOG_ASK    = "ask";
    public static final String TRACKLOG_ALWAYS = "always";

    public static final String TRACKLOG_FORMAT_NMEA         = "NMEA 0183";
    public static final String TRACKLOG_FORMAT_GPX          = "GPX 1.1";

    private static Config instance = null;

    /*
     * Configuration params, initialized to default values.
     */

    // group [Map]
    protected String mapPath = ""; // no default map

    // group [Map datum]
    protected String geoDatum = Datum.DATUM_WGS_84.getName();

    // group [Provider]
    protected String locationProvider = LOCATION_PROVIDER_JSR82;

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
    protected boolean useUTM;
    protected boolean useGeocachingFormat;
    protected boolean osdExtended = true;
    protected boolean osdNoBackground;
    protected boolean osdMediumFont;
    protected boolean osdBoldFont;
    protected boolean osdBlackColor;

    // group [Tweaks]
    protected boolean optimisticIo;
    protected boolean S60renderer;
    protected boolean cacheOffline;

    // hidden
    protected String btDeviceName = "";
    protected String btServiceUrl = "";

    protected Config() {
    }

    public abstract void update() throws ConfigurationException;

    public synchronized static Config getInstance() throws ConfigurationException {
        if (instance == null) {
            instance = new RMSConfig(false);
        }

        return instance;
    }

    public synchronized static Config getSafeInstance() {
        try {
            return getInstance();
        } catch (ConfigurationException e) {
            if (instance == null) {
                try {
                    instance = new RMSConfig(true);
                } catch (ConfigurationException exc) {
                    // should never happen
                }
            }

            return instance;
        }
    }

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

    /**
     * RMS configuration.
     */
    private static final class RMSConfig extends Config {
        private static final String NAME = "config_090";

        private boolean initialized = false;

        public RMSConfig(boolean failSafe) throws ConfigurationException {
            try {
                ensureInitialized();
            } catch (ConfigurationException e) {
                if (failSafe) {
                    initialized = true;
                } else {
                    throw e;
                }
            }
        }

        private Config ensureInitialized() throws ConfigurationException {
            if (!initialized) {
                initialized = true;

                RecordStore rs = null;
                DataInputStream din = null;

                try {
                    // open the store
                    rs = RecordStore.openRecordStore(NAME, true, RecordStore.AUTHMODE_PRIVATE, false);

                    // new store? existing store? corrupted store?
                    int numRecords = rs.getNumRecords();
                    if (numRecords == 0) {
//#ifdef __LOG__
                        if (log.isEnabled()) log.info("new configuration");
//#endif
                    } else {
                        din = new DataInputStream(new ByteArrayInputStream(rs.getRecord(1)));
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
                        boolean oldTracklogsOn = din.readBoolean();
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

//#ifdef __LOG__
                if (log.isEnabled()) log.info("configuration read");
//#endif
            }

            return this;
        }

        public void update() throws ConfigurationException {
            if (!initialized) {
                throw new ConfigurationException("Not initialized");
            }

            RecordStore rs = null;
            DataOutputStream dout = null;

            try {
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                dout = new DataOutputStream(data);
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
                dout.flush();
                byte[] bytes = data.toByteArray();
                rs = RecordStore.openRecordStore(NAME, true, RecordStore.AUTHMODE_PRIVATE, true);
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

//#ifdef __LOG__
            if (log.isEnabled()) log.info("configuration updated");
//#endif            
        }
    }
}
