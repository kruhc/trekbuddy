// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.configuration;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;

public abstract class Config {
    public static final String[] LOCATION_PROVIDERS = new String[] {
        "<none>",
        "Bluetooth (JSR-82)",
        "Internal (JSR-179)",
        "Simulator"
    };

    public static final String DEFAULT_MAP_PATH    = "file:///E:/trekbuddy/map.tar";
    public static final String DEFAULT_PROVIDER     = LOCATION_PROVIDERS[0];
    public static final boolean DEFAULT_FULLSCREEN  = false;

    private static Config instance = null;
    private static Config safeInstance = new DefaultConfig();

    /*
     * Configuration params, initialized to default values.
     */

    protected String mapPath = DEFAULT_MAP_PATH;
    protected String locationProvider = DEFAULT_PROVIDER;
    protected boolean fullscreen = DEFAULT_FULLSCREEN;

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

    public boolean isFullscreen() {
        return fullscreen;
    }

    public void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
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
        private static final String NAME = "config10";

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
                            System.out.println("new cfg");
                            break;
                        case 1: {
                                DataInputStream din = new DataInputStream(new ByteArrayInputStream(rs.getRecord(1)));
                                mapPath = din.readUTF();
                                locationProvider = din.readUTF();
                                fullscreen = din.readBoolean();
                                System.out.println("configuration read");
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

                System.out.println("configuration initialized");

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
                dout.writeBoolean(fullscreen);
                dout.flush();
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

            System.out.println("configuration updated");
        }
    }
}
