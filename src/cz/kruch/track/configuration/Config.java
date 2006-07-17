// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.configuration;

import cz.kruch.track.util.Logger;
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
    private static final Logger log = new Logger("Config");

    public static final String LOCATION_PROVIDER_JSR82      = "Bluetooth";
    public static final String LOCATION_PROVIDER_JSR179     = "Internal";
    public static final String LOCATION_PROVIDER_SIMULATOR  = "Simulator";

    public static final String TRACKLOG_FORMAT_NMEA         = "NMEA 0183";
    public static final String TRACKLOG_FORMAT_GPX          = "GPX 1.1";

    public static final String[] TRACKLOGS_FORMAT = new String[] {
        TRACKLOG_FORMAT_NMEA,
        TRACKLOG_FORMAT_GPX
    };

    private static Config instance = null;
    private static Config safeInstance = new DefaultConfig();

    /*
     * Configuration params, initialized to default values.
     */

    // group
    protected String mapPath = ""; // no default map

    // group
    protected String locationProvider = LOCATION_PROVIDER_JSR82;

    // group
    protected boolean tracklogsOn = false;
    protected String tracklogsFormat = TRACKLOG_FORMAT_GPX;
    protected String tracklogsDir = "file:///E:/tracklogs";

    // group
    protected int simulatorDelay = 100;

    // group
    protected boolean fullscreen = false;
    protected boolean osdExtended = true;

    protected Config() {
    }

    public abstract void update() throws ConfigurationException;

    public static Config getInstance() throws ConfigurationException {
        if (instance == null) {
            instance = new RMSConfig();
        }

        return instance;
    }

    public static Config getSafeInstance() {
        try {
            return getInstance();
        } catch (ConfigurationException e) {
            return safeInstance;
        }
    }

    public String[] getLocationProviders() {
        Vector list = new Vector();
        list.addElement(LOCATION_PROVIDER_SIMULATOR);
        if (TrackingMIDlet.isJsr82()) list.addElement(LOCATION_PROVIDER_JSR82);
        if (TrackingMIDlet.isJsr179()) list.addElement(LOCATION_PROVIDER_JSR179);
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

    public int getSimulatorDelay() {
        return simulatorDelay;
    }

    public void setSimulatorDelay(int simulatorDelay) {
        this.simulatorDelay = simulatorDelay;
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    public boolean isOsdExtended() {
        return osdExtended;
    }

    public void setOsdExtended(boolean osdExtended) {
        this.osdExtended = osdExtended;
    }

    private static class DefaultConfig extends Config {
        public DefaultConfig() {
        }

        public Config ensureInitialized() throws ConfigurationException {
            return this;
        }

        public void update() throws ConfigurationException {
        }
    }

    private static class RMSConfig extends Config {
        private static final String NAME = "config_0711";

        private boolean initialized = false;

        public RMSConfig() throws ConfigurationException {
            ensureInitialized();
        }

        private Config ensureInitialized() throws ConfigurationException {
            if (!initialized) {
                try {
                    // open the store
                    RecordStore rs = RecordStore.openRecordStore(NAME, true, RecordStore.AUTHMODE_PRIVATE, false);

                    // new store? existing store? corrupted store?
                    switch (rs.getNumRecords()) {
                        case 0:
                            if (log.isEnabled()) log.info("new configuration");
                            break;
                        case 1: {
                                DataInputStream din = new DataInputStream(new ByteArrayInputStream(rs.getRecord(1)));
                                mapPath = din.readUTF();
                                locationProvider = din.readUTF();
                                tracklogsOn = din.readBoolean();
                                tracklogsFormat = din.readUTF();
                                tracklogsDir = din.readUTF();
                                simulatorDelay = din.readInt();
                                fullscreen = din.readBoolean();
                                osdExtended = din.readBoolean();
                                din.close();
                                if (log.isEnabled()) log.info("configuration read");
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

                } catch (RecordStoreException e) {
                    throw new ConfigurationException(e);
                } catch (IOException e) {
                    throw new ConfigurationException(e);
                }

                if (log.isEnabled()) log.info("configuration initialized");

                initialized = true;
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
                dout.writeBoolean(tracklogsOn);
                dout.writeUTF(tracklogsFormat);
                dout.writeUTF(tracklogsDir);
                dout.writeInt(simulatorDelay);
                dout.writeBoolean(fullscreen);
                dout.writeBoolean(osdExtended);
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

            if (log.isEnabled()) log.info("configuration updated");
        }
    }
}
