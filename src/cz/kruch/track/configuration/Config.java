// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.configuration;

//#ifdef __LOG__
import cz.kruch.track.util.Logger;
//#endif
import cz.kruch.track.util.Datum;
import cz.kruch.track.TrackingMIDlet;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.util.Vector;

public abstract class Config {
//#ifdef __LOG__
    private static final Logger log = new Logger("Config");
//#endif

    public static final String LOCATION_PROVIDER_JSR82      = "Bluetooth";
    public static final String LOCATION_PROVIDER_JSR179     = "Internal";
    public static final String LOCATION_PROVIDER_SIMULATOR  = "Simulator";

    public static final String TRACKLOG_FORMAT_NMEA         = "NMEA 0183";
    public static final String TRACKLOG_FORMAT_GPX          = "GPX 1.1";

    public static final String[] TRACKLOGS_FORMAT = new String[] {
        TRACKLOG_FORMAT_GPX,
        TRACKLOG_FORMAT_NMEA
    };

    private static Config instance = null;

    /*
     * Configuration params, initialized to default values.
     */

    // group [Map]
    protected String mapPath = ""; // no default map

    // group [Map datum]
    protected String geoDatum = Datum.DATUM_WGS_84.getName();
/*
    protected int dX = 0;
    protected int dY = 0;
    protected int dZ = 0;
*/

    // group [Timezone]
    protected String timeZone = "GMT";

    // group [Provider]
    protected String locationProvider = LOCATION_PROVIDER_JSR82;

    // group [common provider options]
    protected boolean tracklogsOn = false;
    protected String tracklogsFormat = TRACKLOG_FORMAT_GPX;
    protected String tracklogsDir = "file:///E:/tracklogs";
    protected String captureLocator = "capture://video";
    protected String captureFormat = "";

    // group [Simulator provider options]
    protected int simulatorDelay = 100;

    // group [Internal provider options]
    protected int locationInterval = 1;

    // group [Location sharing]
    protected boolean locationSharing = false;

    // group [Desktop]
    protected boolean fullscreen = false;
    protected boolean noSounds = false;
    protected boolean useUTM = false;
    protected boolean osdExtended = true;
    protected boolean osdNoBackground = false;
    protected boolean osdMediumFont = false;
    protected boolean osdBoldFont = false;
    protected boolean osdBlackColor = false;

    // hidden
    protected String btDeviceName = "";
    protected String btServiceUrl = "";

    // precalcs
    private Integer tzOffset = null;

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
        if (TrackingMIDlet.isJsr82()) {
            list.addElement(LOCATION_PROVIDER_JSR82);
        }
        if (TrackingMIDlet.isJsr179()) {
            list.addElement(LOCATION_PROVIDER_JSR179);
        }
        if (TrackingMIDlet.isFs()) {
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

    public boolean isTracklogsOn() {
        return tracklogsOn;
    }

    public void setTracklogsOn(boolean tracklogsOn) {
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

    public String getTimeZone() {
        return timeZone;
    }

    public int getTimeZoneOffset() {
        if (tzOffset == null) {
            String tzString = getTimeZone();
            int i = tzString.indexOf(':');
            if (i == -1) {
                tzOffset = new Integer(0);
            } else {
                int hours = 0;
                if (tzString.charAt(3) == '+') {
                    hours = Integer.parseInt(tzString.substring(4, 6));
                } else {
                    hours = Integer.parseInt(tzString.substring(3, 6));
                }
                int mins = Integer.parseInt(tzString.substring(7));
                tzOffset = new Integer((hours * 60 + mins) * 60);
            }
        }

        return tzOffset.intValue();
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
        this.tzOffset = null;
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

/*
    public int getdX() {
        return dX;
    }

    public void setdX(int dX) {
        this.dX = dX;
    }

    public int getdY() {
        return dY;
    }

    public void setdY(int dY) {
        this.dY = dY;
    }

    public int getdZ() {
        return dZ;
    }

    public void setdZ(int dZ) {
        this.dZ = dZ;
    }
*/

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

                try {
                    // open the store
                    RecordStore rs = RecordStore.openRecordStore(NAME, true, RecordStore.AUTHMODE_PRIVATE, false);

                    // new store? existing store? corrupted store?
                    switch (rs.getNumRecords()) {
                        case 0:
//#ifdef __LOG__
                            if (log.isEnabled()) log.info("new configuration");
//#endif
                            break;
                        case 1: {
                            DataInputStream din = new DataInputStream(new ByteArrayInputStream(rs.getRecord(1)));
                            mapPath = din.readUTF();
                            locationProvider = din.readUTF();
                            timeZone = din.readUTF();
                            geoDatum = din.readUTF();
                            tracklogsOn = din.readBoolean();
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
/*
                            dX = din.readInt();
                            dY = din.readInt();
                            dZ = din.readInt();
*/
                            din.close();
//#ifdef __LOG__
                            if (log.isEnabled()) log.info("configuration read");
//#endif
                            } break;
                        default: {

                            // close the store
                            rs.closeRecordStore();

                            // delete the store
                            RecordStore.deleteRecordStore(NAME);

                            throw new ConfigurationException("Corrupted store");
                        }
                    }

                    // close the store
                    rs.closeRecordStore();

//#ifdef __LOG__
                    if (log.isEnabled()) log.info("configuration initialized");
//#endif

                } catch (RecordStoreException e) {
                    throw new ConfigurationException(e);
                } catch (IOException e) {
                    throw new ConfigurationException(e);
                } finally {
                    initialized = true;
                }
            }

            return this;
        }

        public void update() throws ConfigurationException {
            if (!initialized) {
                throw new ConfigurationException("Not initialized");
            }

            try {
                RecordStore rs = RecordStore.openRecordStore(NAME, true, RecordStore.AUTHMODE_PRIVATE, true);
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                DataOutputStream dout = new DataOutputStream(data);
                dout.writeUTF(mapPath);
                dout.writeUTF(locationProvider);
                dout.writeUTF(timeZone);
                dout.writeUTF(geoDatum);
                dout.writeBoolean(tracklogsOn);
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
/*
                dout.writeInt(dX);
                dout.writeInt(dY);
                dout.writeInt(dZ);
*/
                dout.close();
                byte[] bytes = data.toByteArray();
                if (rs.getNumRecords() > 0) {
                    rs.setRecord(1, bytes, 0, bytes.length);
                } else {
                    rs.addRecord(bytes, 0, bytes.length);
                }
                rs.closeRecordStore();
            } catch (RecordStoreException e) {
                throw new ConfigurationException(e);
            } catch (IOException e) {
                throw new ConfigurationException(e);
            }

//#ifdef __LOG__
            if (log.isEnabled()) log.info("configuration updated");
//#endif            
        }
    }
}
