// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps;

import com.ice.tar.TarInputStream;
import com.ice.tar.TarEntry;

import javax.microedition.io.Connector;
import javax.microedition.lcdui.Image;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Vector;
import java.util.Enumeration;

import cz.kruch.j2se.io.BufferedInputStream;
import cz.kruch.j2se.io.BufferedReader;

import cz.kruch.track.ui.Position;
//#ifdef __LOG__
import cz.kruch.track.util.Logger;
//#endif
import cz.kruch.track.maps.io.LoaderIO;
import cz.kruch.track.TrackingMIDlet;

import api.location.QualifiedCoordinates;

public final class Map {

    public interface StateListener {
        public void mapOpened(Object result, Throwable throwable);
        public void slicesLoaded(Object result, Throwable throwable);
        public void loadingChanged(Object result, Throwable throwable);
    }

//#ifdef __LOG__
    private static final Logger log = new Logger("Map");
//#endif

    private static final int EVENT_MAP_OPENED       = 0;
    private static final int EVENT_SLICES_LOADED    = 1;
    private static final int EVENT_LOADING_CHANGED  = 2;

    static final int TEXT_FILE_BUFFER_SIZE = 512; // for map calibration files content
    static final int SMALL_BUFFER_SIZE = 512 * 2; // for map calibration files 1kB
    static final int LARGE_BUFFER_SIZE = 512 * 8; // for map image files 4 kB

    private static final int TYPE_GMI        = 0;
    private static final int TYPE_GPSKA      = 1;
    private static final int TYPE_J2N        = 2;
    private static final int TYPE_BEST       = 3;

    // interaction with outside world
    private String path;
    private String name;
    private StateListener listener;

    // map state
    private Loader loader;
    private int type = -1;
    private Slice[] slices;
    private Calibration calibration;

    public Map(String path, String name, StateListener listener) {
        this.path = path;
        this.name = name;
        this.listener = listener;
        this.slices = new Slice[0];
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

    public QualifiedCoordinates transform(Position p) {
        return calibration.transform(p);
    }

    public Position transform(QualifiedCoordinates qc) {
        if (isWithin(qc)) {
            return calibration.transform(qc);
        }

        return null;
    }

    public QualifiedCoordinates[] getRange() {
        return calibration.getRange();
    }

    public boolean isWithin(QualifiedCoordinates coordinates) {
        return calibration.isWithin(coordinates);
    }

    /**
     * Disposes map - releases map images.
     * Does gc at the end.
     */
    public void dispose() {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("dispose map @" + Integer.toHexString(hashCode()));
//#endif

        // release slices images
        for (int i = slices.length; --i >= 0; ) {
            slices[i].setImage(null);
        }

        // gc
        System.gc();
    }

    /**
     * Gets slice into which given point falls.
     * @param x
     * @param y
     * @return slice
     */
    public Slice getSlice(int x, int y) {
//        for (int N = slices.length, i = 0; i < N; i++) {
        for (int i = slices.length; --i >= 0; ) {
            Slice slice = slices[i];
            if (slice.isWithin(x, y)) {
                return slice;
            }
        }

        return null;
    }

    /**
     * Opens and scans map.
     * @return always <code>true</code>
     */
    public boolean open() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("open map @" + Integer.toHexString(hashCode()));
//#endif

        // open map in background
        LoaderIO.getInstance().enqueue(new Runnable() {
            public void run() {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("map loading task starting @" + Integer.toHexString(Map.this.hashCode()));
//#endif

                // open and init map
                Throwable throwable = loadMap();

//#ifdef __LOG__
                if (log.isEnabled()) log.debug("map opened @" + Integer.toHexString(Map.this.hashCode()) + "; " + throwable);
//#endif

                // we are done
                notifyListener(EVENT_MAP_OPENED, null, throwable);
            }
        });

        return true;
    }

    /**
     * Ensures slices have their images loaded.
     * @param slices
     */
    public boolean prepareSlices(Vector slices) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("prepare slices @" + Integer.toHexString(hashCode()));
//#endif

        final Vector collection = new Vector(0);

        // create list of slices whose images are to be loaded
        for (Enumeration e = slices.elements(); e.hasMoreElements(); ) {
            Slice slice = (Slice) e.nextElement();
            if (slice.getImage() == null) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("image missing for slice " + slice);
//#endif
                collection.addElement(slice);
            }
        }

        // no images to be loaded
        if (collection.size() == 0) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("got all slices with images");
//#endif
            return false;
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("about to load new slices");
//#endif

        // load images at background
        LoaderIO.getInstance().enqueue(new Runnable() {
            public void run() {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("slice loading task started @" + Integer.toHexString(Map.this.hashCode()));
//#endif

                // load images
                Throwable throwable = loadImages(collection);

//#ifdef __LOG__
                if (log.isEnabled()) log.debug("all requested slices loaded @" + Integer.toHexString(Map.this.hashCode()));
//#endif

                // we are done
                notifyListener(EVENT_SLICES_LOADED, null, throwable);
            }
        });

        return true;
    }

    /* (non-javadoc) public only for loading upon startup */
    public Throwable loadMap() {
        try {
            // load map
            loader = path.endsWith(".tar") || path.endsWith(".TAR") ? (Loader) new TarLoader() : (Loader) new DirLoader();
            loader.init();
            loader.run();
            loader.checkException();

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("map opened");
//#endif

            // check map for consistency
            if (calibration == null) {
                throw new InvalidMapException("Map calibration info missing");
            }
            if (slices.length == 0) {
                throw new InvalidMapException("Empty map - no slices");
            }

            // finalize map preparation
            doFinal();

        } catch (Throwable t) {
            return t;
        }

        return null;
    }

    /**
     * Creates default map from embedded resources.
     */
    public static Map defaultMap(StateListener listener) throws InvalidMapException {
        Map map = new Map("resource:///resources/world.map", "default", listener);
        map.type = TYPE_BEST;

        // load calibration
        InputStream in = TrackingMIDlet.class.getResourceAsStream("/resources/world.map");
        if (in == null) { // no Ozi calibration found
            in = TrackingMIDlet.class.getResourceAsStream("/resources/world.gmi");
            if (in == null) { // neither MapCalibrator calibration
                throw new InvalidMapException("No default map calibration");
            } else { // got MapCalibrator calibration
                try {
                    map.calibration = new Calibration.GMI(in, "/resources/world.gmi");
                } catch (IOException e) {
                    throw new InvalidMapException("Resource '/resources/world.gmi': " + e.toString());
                } finally {
                    try {
                        in.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        } else { // got Ozi calibration
            try {
                map.calibration = new Calibration.Ozi(in, "/resources/world.map");
            } catch (IOException e) {
                throw new InvalidMapException("Resource '/resources/world.map': " + e.toString());
            } finally {
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        // initialize slice
        Slice slice = new Slice(new Calibration.Best("/resources/world_0_0.png"));
        try {
            slice.setImage(Image.createImage("/resources/world_0_0.png"));
        } catch (IOException e) {
            throw new InvalidMapException("Resource '/resources/world_0_0.png': " + e.toString());
        }

        // finalize map
        map.slices = new Slice[]{ slice };
        map.doFinal();

        return map;
    }

    /**
     * Notififies listener.
     */
    private void notifyListener(int code, Object result, Throwable throwable) {
        switch (code) {
            case EVENT_MAP_OPENED:
                listener.mapOpened(result, throwable);
                break;
            case EVENT_SLICES_LOADED:
                listener.slicesLoaded(result, throwable);
                break;
            case EVENT_LOADING_CHANGED:
                listener.loadingChanged(result, throwable);
                break;
        }
    }

    /**
     * Loads images for given slices.
     */
    private Throwable loadImages(Vector collection) {
        try {
            for (Enumeration e = collection.elements(); e.hasMoreElements(); ) {
                Slice slice = (Slice) e.nextElement();
                Throwable throwable = null;

                try {
                    // notify
                    notifyListener(EVENT_LOADING_CHANGED, "Loading " + slice.getURL() + "...", null);

                    // use loader
                    loader.loadSlice(slice);

                    // got image?
                    if (slice.getImage() == null) {
                        throw new InvalidMapException("No image " + slice.getURL());
                    }

//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("image loaded for slice " + slice.getURL());
//#endif
                } catch (Throwable t) {
                    throwable = t;
                    throw t;
                } finally {
                    // notify
                    notifyListener(EVENT_LOADING_CHANGED, null, throwable);
                }
            }
        } catch (Throwable t) {
            return t;
        }

        return null;
    }

    /**
     * File input helper class.
     */
    static final class FileInput {
        private String url;
        private api.file.File fc;
        private InputStream in;

        FileInput(String url) {
            this.url = url;
        }

        InputStream getInputStream() throws IOException {
            fc = new api.file.File(Connector.open(url, Connector.READ));
            in = new BufferedInputStream(fc.openInputStream(), TEXT_FILE_BUFFER_SIZE);

            return in;
        }

        void close() throws IOException {
            if (in != null) {
                in.close();
            }
            if (fc != null) {
                fc.close();
            }
        }
    }

    /**
     * Finalizes map initialization.
     */
    public void doFinal() throws InvalidMapException {
        int xi = getWidth(), yi = getHeight();
        boolean friendly = calibration instanceof Calibration.Ozi || calibration instanceof Calibration.GMI || calibration instanceof Calibration.J2N;
        int mapWidth = calibration.width;
        int mapHeight = calibration.height;

        // absolutize slices position
//        for (int N = slices.length, i = 0; i < N; i++) {
        for (int i = slices.length; --i >= 0; ) {
            Slice slice = slices[i];
            slice.doFinal(friendly);

            // look for dimensions increments
            int x = slice.x;
            if (x > 0 && x < xi)
                xi = x;
            int y = slice.y;
            if (y > 0 && y < yi)
                yi = y;
        }

        // finalize slices creation
//        for (int N = slices.length, i = 0; i < N; i++) {
        for (int i = slices.length; --i >= 0; ) {
            Slice slice = slices[i];

            // figure slice dimension and precalculate range
            slice.doFinal(mapWidth, mapHeight, xi, yi);

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("ready slice " + slices[i]);
//#endif
        }
    }

    /*
     * Map loaders.
     */

    private abstract class Loader extends Thread {
        private Vector collection;
        protected Exception exception;

        public abstract void init() throws IOException;
        public abstract void destroy();
        public abstract void loadSlice(Slice slice) throws IOException;

        public void checkException() throws Exception {
            if (exception != null) {
                throw exception;
            }
        }

        protected void doFinal() {
            if (collection != null) {
                slices = new Slice[collection.size()];
                collection.copyInto(slices);
            }
        }

        protected void addSlice(Slice s) {
            if (collection == null) {
                collection = new Vector();
            }

            collection.addElement(s);
        }
    }

    public static boolean fileInputStreamResetable = false;
    public static boolean useReset = true;

    private final class TarLoader extends Loader {
        private api.file.File file;
        private InputStream in = null;

        public void init() throws IOException {
            file = new api.file.File(Connector.open(path, Connector.READ));
        }

        public void destroy() {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                }
            }
        }

        public void run() {
            TarInputStream tar = null;
            TarEntry entry;
            InputStream in = null;

            try {
                tar = new TarInputStream(new BufferedInputStream(in = file.openInputStream(), SMALL_BUFFER_SIZE));

                /*
                 * test quality of JSR-75/FileConnection
                 */

                if (useReset && in.markSupported()) {
                    in.mark(Integer.MAX_VALUE);
                    this.in = in;
                    Map.fileInputStreamResetable = true;
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("input stream resetable, very good");
//#endif
                }

                /*
                 * ~
                 */

                entry = tar.getNextEntry();
                while (entry != null) {
                    String entryName = entry.getName();
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("tar entry: " + entryName);
//#endif
                    int indexOf = entryName.lastIndexOf('.');
                    if (indexOf > -1) {
                        String ext = entryName.substring(indexOf + 1);
                        if ("png".equals(ext) && (entryName.startsWith("set/", 0) || entryName.startsWith("pictures/", 0))) { // slice
                            addSlice(new Slice(new Calibration.Best(entryName), entry));
                        } else {
                            if (Map.this.calibration == null && entryName.indexOf('/') == -1) {
                                if ("gmi".equals(ext)) {
                                    Map.this.calibration = new Calibration.GMI(tar, entryName);
                                    Map.this.type = TYPE_BEST;
                                } else if ("map".equals(ext)) {
                                    Map.this.calibration = new Calibration.Ozi(tar, entryName);
                                    Map.this.type = TYPE_BEST;
                                } else if ("xml".equals(ext)) {
                                    Map.this.calibration = new Calibration.XML(tar, entryName);
                                    Map.this.type = TYPE_GPSKA;
                                } else if ("j2n".equals(ext)) {
                                    Map.this.calibration = new Calibration.J2N(tar, entryName);
                                    Map.this.type = TYPE_J2N;
                                }
                            }
                        }
                    }
                    entry = tar.getNextEntry();
                }
            } catch (Exception e) {
                exception = e;
            } finally {
                if (tar != null && this.in == null) {
                    try {
                        tar.close();
                    } catch (IOException e) {
                    }
                }
            }

            this.doFinal();
        }

        public void loadSlice(Slice slice) throws IOException {
            TarEntry entry = (TarEntry) slice.getClosure();
            TarInputStream tar = null;
            try {
                if (in == null) {
                    tar = new TarInputStream(new BufferedInputStream(file.openInputStream(), LARGE_BUFFER_SIZE));
                } else {
                    in.reset();
                    tar = new TarInputStream(new BufferedInputStream(in, LARGE_BUFFER_SIZE));
                }
                tar.setNextEntry(entry);
                tar.getNextEntry();
                slice.setImage(Image.createImage(tar));
            } finally {
                if (tar != null && this.in == null) {
                    try {
                        tar.close();
                    } catch (IOException exc) {
                    }
                }
            }
        }
    }

    private final class DirLoader extends Loader {
        private String dir;

        public void init() throws IOException {
            int i = path.lastIndexOf('/');
            if (i == -1) {
                throw new InvalidMapException("Invalid map URL");
            }
            dir = path.substring(0, i + 1);

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("slices are in " + dir);
//#endif
        }

        public void destroy() {
        }

        public void run() {
            api.file.File fc = null;
            BufferedReader reader = null;

            try {
                String setDir = "set/";

                if (Map.this.calibration == null) {
                    // helper loader
                    FileInput fileInput = new FileInput(path);

                    // path points to calibration file
                    if (path.endsWith(".gmi")) {
                        Map.this.calibration = new Calibration.GMI(fileInput.getInputStream(), path);
                        Map.this.type = TYPE_BEST;
                    } else if (path.endsWith(".map")) {
                        Map.this.calibration = new Calibration.Ozi(fileInput.getInputStream(), path);
                        Map.this.type = TYPE_BEST;
                    } else if (path.endsWith(".xml")) {
                        Map.this.calibration = new Calibration.XML(fileInput.getInputStream(), path);
                        Map.this.type = TYPE_GPSKA;
                    } else if (path.endsWith(".j2n")) {
                        Map.this.calibration = new Calibration.J2N(fileInput.getInputStream(), path);
                        Map.this.type = TYPE_J2N;
                        setDir = "pictures/";
                    }

                    // close hellper loader
                    fileInput.close();

                } else {
                    if (Map.this.type == TYPE_J2N) {
                        setDir = "pictures/";
                    }
                }

                // do we have a list?
                fc = new api.file.File(Connector.open(path.substring(0, path.lastIndexOf('.')) + ".set", Connector.READ));
                if (fc.exists()) {
                    // each line is a slice filename
                    reader = new BufferedReader(new InputStreamReader(fc.openInputStream()), LARGE_BUFFER_SIZE);
                    String entry = reader.readLine(false);
                    while (entry != null) {
                        addSlice(new Slice(new Calibration.Best(setDir + entry.trim())));
                        entry = reader.readLine(false);
                    }
                } else {
                    // close connection for reuse
                    fc.close();

                    // iterate over set
                    fc = new api.file.File(Connector.open(dir + setDir, Connector.READ));
                    for (Enumeration e = fc.list("*.png", false); e.hasMoreElements(); ) {
                        String entry = e.nextElement().toString();
                        addSlice(new Slice(new Calibration.Best(setDir + entry)));
                    }
                }
            } catch (Exception e) {
                exception = e;
            } finally {
                if (reader != null) {
                    try {
                        fc.close();
                    } catch (IOException e) {
                    }
                }
                if (fc != null) {
                    try {
                        fc.close();
                    } catch (IOException e) {
                    }
                }
            }

            this.doFinal();
        }

        public void loadSlice(Slice slice) throws IOException {
            String slicePath = dir + slice.getURL();
            api.file.File fc = null;
            InputStream in = null;

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("load slice image from " + slicePath);
//#endif

            try {
                fc = new api.file.File(Connector.open(slicePath, Connector.READ));
                in = new BufferedInputStream(fc.openInputStream(), LARGE_BUFFER_SIZE);
                slice.setImage(Image.createImage(in));
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                    }
                }
                if (fc != null) {
                    try {
                        fc.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
    }
}
