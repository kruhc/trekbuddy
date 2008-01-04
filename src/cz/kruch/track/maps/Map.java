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

package cz.kruch.track.maps;

import java.io.IOException;
import java.util.Vector;

import cz.kruch.j2se.io.BufferedInputStream;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.maps.io.LoaderIO;
import cz.kruch.track.ui.Position;
import cz.kruch.track.ui.Desktop;
import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.Resources;

import api.location.QualifiedCoordinates;
import api.location.Datum;
import api.location.ProjectionSetup;

/**
 * Map representation and handling.
 *  
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class Map implements Runnable {
    public static final String JAR_EXT = ".jar";
    public static final String TAR_EXT = ".tar";

/*
    public interface StateListener {
        public void mapOpened(Object result, Throwable throwable);
        public void slicesLoading(Object result, Throwable throwable);
        public void slicesLoaded(Object result, Throwable throwable);
        public void loadingChanged(Object result, Throwable throwable);
    }
*/

//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Map");
//#endif

/*
    private static final int EVENT_MAP_OPENED       = 0;
    private static final int EVENT_SLICES_LOADING   = 1;
    private static final int EVENT_SLICES_LOADED    = 2;
    private static final int EVENT_LOADING_CHANGED  = 3;
*/

    // interaction with outside world
    private String path;
    private String name;
    private /*StateListener*/Desktop listener;

    // map state
    private Loader loader;
    private Calibration calibration;
    private Slice[] slices;
    private int numberOfSlices;

    public Map(String path, String name, /*StateListener*/Desktop listener) {
        if (path == null) {
            throw new IllegalArgumentException("Map without path: " + name);
        }
        this.path = path;
        this.name = name;
        this.listener = listener;
    }

    public void setCalibration(Calibration calibration) {
        this.calibration = calibration;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public int getWidth() {
        return calibration.getWidth();
    }

    public int getHeight() {
        return calibration.getHeight();
    }

    public ProjectionSetup getProjection() {
        return calibration.getProjection();
    }

    public Datum getDatum() {
        return calibration.getDatum();
    }

    public double getStep(char direction) {
        QualifiedCoordinates[] range = calibration.getRange();
        switch (direction) {
            case 'N':
                return (range[0].getLat() - range[3].getLat()) / (calibration.getHeight());
            case 'S':
                return (range[3].getLat() - range[0].getLat()) / (calibration.getHeight());
            case 'E':
                return (range[3].getLon() - range[0].getLon()) / (calibration.getWidth());
            case 'W':
                return (range[0].getLon() - range[3].getLon()) / (calibration.getWidth());
        }

        return 0;
    }

    public QualifiedCoordinates transform(Position p) {
        return calibration.transform(p);
    }

    public Position transform(QualifiedCoordinates qc) {
        return calibration.transform(qc);
    }

    public QualifiedCoordinates[] getRange() {
        return calibration.getRange();
    }

    public boolean isWithin(QualifiedCoordinates coordinates) {
        return calibration.isWithin(coordinates);
    }

    /**
     * Disposes map - releases map images and disposes loader.
     * Does gc at the end.
     */
    public void dispose() {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("dispose map " + getPath());
//#endif

        // dispose loader resources
        if (loader != null) {
            try {
                loader.dispose();
            } catch (IOException e) {
                // ignore
            }
        }

        // release slices images
        if (slices != null) {
            final Slice[] slices = this.slices; // local ref for faster access
            for (int i = numberOfSlices; --i >= 0; ) {
                slices[i].setImage(null);
            }
        }

        /*
         * slices and calibration are kept, unless instructed not to
         */
        if (Config.largeAtlases) {
            slices = null;
        }

        // GC
        System.gc();
    }

    /**
     * Closes map.
     */
    public void close() {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("close map " + getPath());
//#endif

        // dispose first
        dispose();

        // gc hints
        loader = null;
        slices = null;
        calibration = null;
    }

    /**
     * Gets slice into which given point falls.
     *
     * @param x pixel x
     * @param y pixel y
     * @return slice
     */
    public Slice getSlice(final int x, final int y) {
        final Slice[] slices = this.slices; // local ref for faster access
        for (int i = numberOfSlices; --i >= 0; ) {
            if (slices[i].isWithin(x, y)) {
                return slices[i];
            }
        }

        return null;
    }

    /**
     * Opens and scans map.
     *
     * @return always <code>true</code>
     */
    public boolean open() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("open map " + getPath());
//#endif

        // open map in background
        LoaderIO.getInstance().enqueue(this);

        return true;
    }

    /**
     * Runnable's run() implementation.
     */
    public void run() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("map loading task starting for " + getPath());
//#endif

        // open and init map
        Throwable throwable = loadMap();

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("map loading finished for " + getPath() + "; " + throwable);
//#endif

        // we are done
        listener.mapOpened(null, throwable);
    }

    /**
     * Ensures slices have their images loaded.
     *
     * @param list of slice to be loaded
     * @return always <code>true</code>
     */
    public boolean ensureImages(Vector list) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("about to load new slices");
//#endif

        // assertion
        if (loader.buffered == null) {
            throw new IllegalStateException("Loading images for disposed map: " + path);
        }

        // notify listener
        listener.slicesLoading(null, null);

        // load images at background
        LoaderIO.getInstance().enqueue(loader.use(list));

        return true;
    }

    /* (non-javadoc) public only for loading upon startup */
    public Throwable loadMap() {
        try {
            // create loader
            if (loader == null) {
                if (path.endsWith(JAR_EXT)) {
                    loader = (Loader) Class.forName("cz.kruch.track.maps.JarLoader").newInstance();
                } else if (path.toLowerCase().endsWith(TAR_EXT)) {
                    loader = (Loader) Class.forName("cz.kruch.track.maps.TarLoader").newInstance();
                } else {
                    loader = (Loader) Class.forName("cz.kruch.track.maps.DirLoader").newInstance();
                }
            }
            loader.init(this, path);
            final boolean finalize = loader.doFinal();

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("map opened");
//#endif

            // check map for consistency
            if (calibration == null) {
                throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_NO_CALIBRATION));
            }
            if (slices == null) {
                throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_NO_SLICES));
            }

            // finalize map preparation
            if (finalize) doFinal();

        } catch (Throwable t) {
            return t;
        }

        return null;
    }

    /**
     * Creates default map from embedded resources.
     *
     * @param listener application desktop
     * @return map
     * @throws Throwable if anything goes wrong
     */
    public static Map defaultMap(/*StateListener*/Desktop listener) throws Throwable {
        Map map = new Map("trekbuddy.jar", "Default", listener);
        Throwable t = map.loadMap();
        if (t != null) {
            throw t;
        }

        return map;
    }

    /**
     * Finalizes map initialization.
     *
     * @throws InvalidMapException if anything goes wrong
     */
    private void doFinal() throws InvalidMapException {
        // local ref for faster access
        final int mapWidth = calibration.width;
        final int mapHeight = calibration.height;
        final Slice[] slices = this.slices;
                                          
        // vars
        int xi = getWidth(), yi = getHeight();

        // finds nearest neighbour to 0-0 slice to figure map slice dimensions
        for (int i = numberOfSlices; --i >= 0; ) {
            final Slice slice = slices[i];
            final int x = slice.getX();
            if (x > 0 && x <= xi) {
                xi = x;
                final int y = slice.getY();
                if (y > 0 && y < yi) {
                    yi = y;
                }
            }
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("!0-!0 slice is " + xi + "-" + yi);
//#endif

        // finalize slices creation
        for (int i = numberOfSlices; --i >= 0; ) {
            slices[i].doFinal(mapWidth, mapHeight, xi, yi);
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("ready slice " + slices[i]);
//#endif
        }
    }

    /**
     * Map loader.
     */
    static abstract class Loader implements Runnable {
//#ifdef __LOG__
        protected static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Map.Loader");
//#endif
        protected static final String SET_DIR_PREFIX = "set/";

        protected String basename;
        protected String extension;
        protected BufferedInputStream buffered;

        protected boolean isGPSka, isTar;

        private Map map;
        private Vector _list;

        private int increment;

        abstract void loadSlice(Slice slice) throws IOException;

        protected Loader() {
        }

        protected void init(Map map, String url) throws IOException {
            if (this.map != null || this.buffered != null) {
                throw new IllegalStateException("Already initialized");
            }
            this.map = map;
            this.buffered = new BufferedInputStream(null, 4096);
        }

        void dispose() throws IOException {
            map = null;
            if (buffered != null) {
                buffered.close();
                buffered = null;
            }
        }

        final Loader use(Vector list) {
            synchronized (this) {
                if (_list != null) {
                    throw new IllegalStateException("Loading in progress");
                }
                _list = list;
            }

            return this;
        }

        public void run() {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("slice loading task started for " + map.getPath());
//#endif

            // load images
            Throwable throwable = loadImages(_list);

            // end of job
            synchronized (this) {
                _list = null;
            }

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("all requested slices loaded for " + map.getPath());
//#endif

            // we are done
            map.listener.slicesLoaded(null, throwable);
        }

        final boolean doFinal() {
            return map.numberOfSlices > 0;
        }

/*
        protected final Slice addSlice(String filename) throws InvalidMapException {
            if (basename == null) {
                if (!isGPSka) {
                    basename = Slice.getBasename(isTar ? filename.substring(4) : filename);
                } else {
                    basename = isTar ? filename.substring(4) : filename;
                }
            }

            if (list == null) {
                list = new Vector(64, 16);
            }

            Slice slice = newSlice(filename);
            list.addElement(slice);

            return slice;
        }

        protected Slice newSlice(String filename) throws InvalidMapException {
            return !isGPSka ? new Slice(filename) : new Slice();
        }
*/

        protected final Slice[] getMapSlices() {
            return map.slices;
        }

        protected final Calibration getMapCalibration() {
            return map.calibration;
        }

        protected final Slice addSlice(CharArrayTokenizer.Token token) throws InvalidMapException {
            if (map.slices != null) {
                // ensure array capacity
                if (map.numberOfSlices == map.slices.length) {
                    Slice[] newArray = new Slice[map.numberOfSlices + increment];
                    System.arraycopy(map.slices, 0, newArray, 0, map.numberOfSlices);
                    map.slices = null; // gc hint
                    map.slices = newArray;
                }
            } else { // first slice being added
                // alloc array
                map.slices = initArray();
                // detect slice basenema
                basename = getBasename(token);
                // detect extension
                extension = getExtension(token);
            }

            final Slice slice = newSlice(token);
            map.slices[map.numberOfSlices++] = slice;

            return slice;
        }

        protected Slice newSlice(CharArrayTokenizer.Token token) throws InvalidMapException {
            return !isGPSka ? new Slice(token) : new Slice();
        }

        private String getBasename(CharArrayTokenizer.Token token) throws InvalidMapException {
            String name = token.toString();
            if (isTar) {
                name = name.substring(4); // skips leading "set/..."
            }
            if (!isGPSka) {
                int p0 = -1, p1 = -1;
                int i = 0;
                for (int N = name.length() - 4/* extension length */; i < N; i++) {
                    if ('_' == name.charAt(i)) {
                        p0 = p1;
                        p1 = i;
                    }
                }
                if (p0 > -1 && p1 > -1) {
                    name = name.substring(0, p0);
                } else {
                    throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_INVALID_SLICE_NAME) + name);
                }
            }

            return name;
        }

        private String getExtension(CharArrayTokenizer.Token token) throws InvalidMapException {
            String name = token.toString();
            final int i = name.lastIndexOf('.');
            if (i > -1) {
                return name.substring(i);
            }
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_INVALID_SLICE_NAME) + name);
        }

        private Slice[] initArray() {
            int initialCapacity;
            if (map.calibration != null) {
                if (!isGPSka) {
                    initialCapacity = ((((map.calibration.getWidth() * map.calibration.getHeight()) / (300 * 400)) / 16) + 1) * 16;
                } else {
                    initialCapacity = 1;
                }
            } else {
                initialCapacity = 64;
            }
            increment = initialCapacity / 4;

            return new Slice[initialCapacity];
        }

        private Throwable loadImages(Vector slices) {
            // assertions
            if (slices == null) {
                throw new IllegalArgumentException("Slice list is null");
            }

            // load images for given slices
            try {
                for (int N = slices.size(), i = 0; i < N; i++) {
                    Slice slice = (Slice) slices.elementAt(i);
                    if (slice.getImage() == null) {

                        // notify
                        map.listener.loadingChanged("Loading " + slice.toString(), null);

                        try {
                            // load image
                            try {
                                loadSlice(slice);
                            } catch (Throwable t) {
                                throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_SLICE_LOAD_FAILED) + ": " + t.toString());
                            }

                            // got image?
                            if (slice.getImage() == null) {
                                throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_NO_SLICE_IMAGE) + " " + slice.toString());
                            }
//#ifdef __LOG__
                            if (log.isEnabled()) log.debug("image loaded for slice " + slice.toString());
//#endif
                        } finally {

                            // notify
                            map.listener.loadingChanged(null, null);

                        }
                    }
                }
            } catch (Throwable t) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("image loading for slice");
//#endif
                return t;
            }

            return null;
        }
    }

    /* stream characteristic */
    public static int fileInputStreamResetable;
    /* behaviour flags */
    public static boolean useReset = true;
    public static boolean useSkip = true;
}
