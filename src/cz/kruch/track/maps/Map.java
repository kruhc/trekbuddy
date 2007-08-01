// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps;

import com.ice.tar.TarInputStream;
import com.ice.tar.TarEntry;

import javax.microedition.io.Connector;
import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;
import java.util.Enumeration;

import cz.kruch.j2se.io.BufferedInputStream;

import cz.kruch.track.ui.Position;
import cz.kruch.track.ui.Desktop;
import cz.kruch.track.ui.NavigationScreens;
import cz.kruch.track.maps.io.LoaderIO;
import cz.kruch.track.maps.io.FileInput;
import cz.kruch.track.io.LineReader;
import cz.kruch.track.AssertionFailedException;
import cz.kruch.track.util.CharArrayTokenizer;

import api.location.QualifiedCoordinates;
import api.location.Datum;
import api.location.ProjectionSetup;
import api.file.File;

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

    static final int TEXT_FILE_BUFFER_SIZE  = 512;  // for map calibration files content and tar header blocks
    static final int LARGE_BUFFER_SIZE      = 4096; // for map image files - 4 kB

    private static final String SET_DIR_PREFIX = "set/";

    // interaction with outside world
    private String path;
    private String name;
    private /*StateListener*/Desktop listener;

    // map state
    private Loader loader;
    private Slice[] slices;
    private Calibration calibration;

    public Map(String path, String name, /*StateListener*/Desktop listener) {
        if (path == null) {
            throw new AssertionFailedException("Map without path: " + name);
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

/*
    public boolean isWithin(Position position) {
        return calibration.isWithin(position);
    }
*/

    /**
     * Disposes map - releases map images and disposes loader.
     * Does gc at the end.
     */
    public void dispose() {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("dispose map " + getPath());
//#endif

        // dispose loader resources
        try {
            loader.dispose();
        } catch (IOException e) {
            // ignore
        }

        // release slices images
        if (slices != null) {
            Slice[] slices = this.slices; // local ref for faster access
            for (int i = slices.length; --i >= 0; ) {
                slices[i].setImage(null);
            }
        }

        /*
         * slices and calibration are kept
         */

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

        // already closed?
        if (loader == null) {
            return;
        }

        // dispose
        dispose();

        // gc hints
        loader = null;
        slices = null;
        calibration = null;
    }

    /**
     * Gets slice into which given point falls.
     * @param x
     * @param y
     * @return slice
     */
    public Slice getSlice(int x, int y) {
        Slice[] slices = this.slices; // local ref for faster access
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
     * @return always <code>true</code>
     */
    public boolean ensureImages(Vector list) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("about to load new slices");
//#endif

        // assertion
        if (loader.buffered == null) {
            throw new AssertionFailedException("Loading images for disposed map: " + path);
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
                    loader = new JarLoader();
                } else if (path.toLowerCase().endsWith(TAR_EXT)) {
                    loader = new TarLoader();
                } else {
                    loader = new DirLoader();
                }
            }
            loader.init(path);
            boolean finalize = loader.doFinal();
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
            if (finalize) doFinal();

        } catch (Throwable t) {
            return t;
        }

        return null;
    }

    /**
     * Creates default map from embedded resources.
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
     */
    private void doFinal() throws InvalidMapException {
        // local ref for faster access
        final int mapWidth = calibration.width;
        final int mapHeight = calibration.height;
        Slice[] slices = this.slices;

        // vars
        int xi = getWidth(), yi = getHeight();

        // absolutize slices position
        for (int i = slices.length; --i >= 0; ) {
            Slice slice = slices[i];

            // look for dimensions increments
            short x = slice.getX();
            if (x > 0 && x < xi)
                xi = x;
            short y = slice.getY();
            if (y > 0 && y < yi)
                yi = y;
        }

        // finalize slices creation
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

    private abstract class Loader implements Runnable {
        protected String basename;
        protected Exception exception;
        protected StringBuffer pathSb;
        protected BufferedInputStream buffered;

        private Vector list;

        public abstract void loadSlice(Slice slice) throws IOException;

        protected Loader() {
        }

        public void init(String url) throws IOException {
            this.buffered = new BufferedInputStream(null, LARGE_BUFFER_SIZE);
            this.pathSb = new StringBuffer(64);
        }

        public void checkException() throws Exception {
            if (exception != null) {
                throw exception;
            }
        }

        public void dispose() throws IOException {
            pathSb = null;
            if (buffered != null) {
                buffered.close();
                buffered.dispose();
                buffered = null;
            }
        }

        public Loader use(Vector list) {
            synchronized (this) {
                if (this.list != null) {
                    throw new AssertionFailedException("Loading in progress");
                }
                this.list = list;
            }

            return this;
        }

        public void run() {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("slice loading task started for " + getPath());
//#endif

            // load images
            Throwable throwable = loadImages(list);

            // end of job
            synchronized (this) {
                list = null;
            }

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("all requested slices loaded for " + getPath());
//#endif

            // we are done
            listener.slicesLoaded(null, throwable);
        }

        public boolean doFinal() {
            // got new slices info
            if (list != null) {

                // vector to array
                slices = new Slice[list.size()];
                list.copyInto(slices);

                // gc hints
                list.removeAllElements();
                list = null;

                return true;
            }

            return false;
        }

        protected Slice addSlice(String filename) throws InvalidMapException {
            if (basename == null) {
                if (calibration instanceof Calibration.XML) { // GPSka
                    basename = this instanceof TarLoader ? filename.substring(4) : filename;
                } else {
                    basename = Slice.getBasename(this instanceof TarLoader ? filename.substring(4) : filename);
                }
            }

            if (list == null) {
                list = new Vector(16);
            }

            Slice slice = new Slice(filename, calibration instanceof Calibration.XML);
            list.addElement(slice);

            return slice;
        }

        private Throwable loadImages(Vector slices) {

            // assertions
            if (slices == null) {
                throw new AssertionFailedException("Slice list is null");
            }

            try {
//                for (int i = slices.size(); --i >= 0; ) {
                for (int N = slices.size(), i = 0; i < N; i++) {
                    Slice slice = (Slice) slices.elementAt(i);
                    Throwable throwable = null;

                    if (slice.getImage() == null) {
                        try {
                            // notify
                            listener.loadingChanged("Loading " + slice.toString(), null);

                            try {
                                // load image
                                loadSlice(slice);
                            } catch (Throwable t) {
                                throw new InvalidMapException("Slice loading failed: " + t.toString());
                            }

                            // got image?
                            if (slice.getImage() == null) {
                                throw new InvalidMapException("No image for slice " + slice.toString());
                            }

//#ifdef __LOG__
                            if (log.isEnabled()) log.debug("image loaded for slice " + slice.getPath());
//#endif
                        } catch (Throwable t) {
//#ifdef __LOG__
                            t.printStackTrace();
//#endif
                            // record and rethrow
                            throwable = t;
                            throw t;

                        } finally {

                            // notify
                            listener.loadingChanged(null, throwable);

                        }
                    }
                }
            } catch (Throwable t) {
                return t;
            }

            return null;
        }
    }

    /* stream characteristic */
    public static int fileInputStreamResetable = 0;
    /* reset behaviour flag */
    public static boolean useReset = true;

    /*
     * Loader implementations.
     */

    private final class TarLoader extends Loader {
        private static final int MARK_SIZE = 64 * 1024 * 1024;

        private File file;
        private InputStream nativeIn;
        private TarInputStream tarIn;

        protected TarLoader() {
            super();
        }

        public void init(String url) throws IOException {
            super.init(url);

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("init map " + url);
//#endif

            // input stream
            InputStream in = null;

            try {
                file = File.open(Connector.open(path, Connector.READ));
                tarIn = new TarInputStream(in = file.openInputStream());

                /*
                 * test quality of File API
                 */

                if (useReset) {
                    if (in.markSupported()) {
                        try {
                            in.mark(MARK_SIZE); // max 64 MB map
                            nativeIn = in;
                            fileInputStreamResetable = 1;
//#ifdef __LOG__
                            if (log.isEnabled()) log.debug("input stream support marking, very good");
//#endif
                        } catch (Throwable t) {
                            /* OutOfMemoryError on S60 3rd */
                        }
                    } else {
                        try {
                            in.reset(); // try reset
                            nativeIn = in;
                            fileInputStreamResetable = 2;
//#ifdef __LOG__
                            if (log.isEnabled()) log.debug("input stream may be resetable");
//#endif
                        } catch (IOException e) {
                            fileInputStreamResetable = -1;
//#ifdef __LOG__
                            if (log.isEnabled()) log.debug("input stream definitely not resetable");
//#endif
                        }
                    }
                }

                /*
                 * ~
                 */

                // do not have calibration or slices
                if (Map.this.calibration == null || Map.this.slices == null) {

                    // iterate over tar
                    TarEntry entry = tarIn.getNextEntry();
                    while (entry != null) {

                        // get entry
                        String entryName = entry.getName();

                        // is it tile (in basedir only)
                        if (entryName.startsWith(SET_DIR_PREFIX) && entryName.endsWith(Slice.PNG_EXT)) { // slice

                            // no slices yest
                            if (Map.this.slices == null) {
                                addSlice(entryName).setClosure(new Long(entry.getPosition()));
                            }

                        } else { // no, maybe calibration file

                            // no calibration yet
                            if (Map.this.calibration == null && entryName.indexOf(File.PATH_SEPCHAR) == -1) {
//#ifdef __LOG__
                                if (log.isEnabled()) log.debug("do not have calibration yet");
//#endif
                                // load calibration
                                if (entryName.endsWith(Calibration.OZI_EXT)) {
                                    Map.this.calibration = new Calibration.Ozi(tarIn, entryName);
                                } else if (entryName.endsWith(Calibration.GMI_EXT)) {
                                    Map.this.calibration = new Calibration.GMI(tarIn, entryName);
                                } else if (entryName.endsWith(Calibration.XML_EXT)) {
                                    Map.this.calibration = new Calibration.XML(tarIn, entryName);
                                } else if (entryName.endsWith(Calibration.J2N_EXT)) {
                                    Map.this.calibration = new Calibration.J2N(tarIn, entryName);
                                }
                            }
                        }
                        entry = null; // gc hint
                        entry = tarIn.getNextEntry();
                    }
                }
            } catch (Exception e) {
                exception = e;
            } finally {

                // close native stream when not reusable
                if (nativeIn == null) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("input stream not reusable -> close it");
//#endif
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException e) {
                            // ignore
                        }
                    }

                } else { // otherwise prepare for reuse

                    // reset the stream
                    nativeIn.reset();

                    // and reuse it in buffered stream
                    buffered.reuse(nativeIn);
                }
            }
        }

        public void dispose() throws IOException {
            // dispose tar stream
            if (tarIn != null) {
                tarIn.reuse(null);
                tarIn.dispose();
                tarIn = null;
            }

            // close native stream
            if (nativeIn != null) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("closing native stream");
//#endif
                try {
                    nativeIn.close();
                } catch (IOException e) {
                    // ignore
                }
                nativeIn = null; // gc hint
            }

            // close file
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    // ignore
                }
                file = null; // gc hint
            }

            // parent
            super.dispose();
        }

        public void loadSlice(Slice slice) throws IOException {
            // input stream
            InputStream in = null;

            try {
                long streamOffset = ((Long) slice.getClosure()).longValue();
                boolean keepPosition = false;

                // prepare buffered stream
                if (nativeIn == null) {
                    buffered.reuse(in = file.openInputStream());
                } else {
                    if (streamOffset >= tarIn.getStreamOffset()) {
                        keepPosition = true;
                    } else {
                        nativeIn.reset();
                        buffered.reuse(nativeIn);
                    }
                }

                // prepare tar stream
                tarIn.reuse(buffered);
                if (!keepPosition) {
                    tarIn.setStreamOffset(0);
                }
                tarIn.rewind(streamOffset);

                // get corresponding entry
                Object unused = tarIn.getNextEntry();

                // read image
                slice.setImage(NavigationScreens.createImage(tarIn));

            } finally {

                // close native stream when not reusable
                if (nativeIn == null) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("input stream not reusable -> close it");
//#endif
                    if (in != null) {

                        // clean buffered
                        buffered.reuse(null);

                        // close native non-reusable stream
                        try {
                            in.close();
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }
        }
    }

    private final class JarLoader extends Loader {
        private static final String DEFAULT_OZI_MAP = "/resources/world.map";
        private static final String DEFAULT_GMI_MAP = "/resources/world.gmi";
        private static final String RESOURCES_SET_DIR   = "/resources/set/";
        private static final String RESOURCES_SET_FILE  = "/resources/world.set";

        protected JarLoader() {
            super();
        }

        public void init(String url) throws IOException {
            super.init(url);

            try {
                // look for Ozi calibration first
                InputStream in = cz.kruch.track.TrackingMIDlet.class.getResourceAsStream(DEFAULT_OZI_MAP);
                if (in == null) {
                    // look for GMI then
                    in = cz.kruch.track.TrackingMIDlet.class.getResourceAsStream(DEFAULT_GMI_MAP);
                    if (in == null) {
                        // neither MapCalibrator calibration
                        throw new InvalidMapException("No default map calibration");
                    } else { // got MapCalibrator calibration
                        try {
                            Map.this.calibration = new Calibration.GMI(in, DEFAULT_GMI_MAP);
                        } catch (InvalidMapException e) {
                            throw e;
                        } catch (IOException e) {
                            throw new InvalidMapException("Resource '/resources/world.gmi': " + e.toString());
                        }
                    }
                } else { // got Ozi calibration
                    try {
                        Map.this.calibration = new Calibration.Ozi(in, DEFAULT_OZI_MAP);
                    } catch (InvalidMapException e) {
                        throw e;
                    } catch (IOException e) {
                        throw new InvalidMapException("Resource '/resources/world.map': " + e.toString());
                    }
                }
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("in-jar calibration loaded");
//#endif

                // close stream
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
                in = null; // gc hint

                // each line is a slice filename
                LineReader reader = new LineReader(cz.kruch.track.TrackingMIDlet.class.getResourceAsStream(RESOURCES_SET_FILE));
                String entry = reader.readLine(false);
                while (entry != null) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("in-jar tile - " + entry);
//#endif
                    addSlice(entry);
                    entry = null; // gc hint
                    entry = reader.readLine(false);
                }

                // close reader (closes the stream)
                reader.close();

//#ifdef __LOG__
                if (log.isEnabled()) log.debug("in-jar .set processed");
//#endif
            } catch (Exception e) {
                exception = e;
            }
        }

        public void loadSlice(Slice slice) throws IOException {
            // reuse sb
            StringBuffer pathSb = this.pathSb;
            pathSb.delete(0, pathSb.length());

            // construct slice path
            pathSb.append(RESOURCES_SET_DIR).append(basename);
            if (!(calibration instanceof Calibration.XML)) {
                slice.appendPath(pathSb);
            }

            // get slice path
            String slicePath = pathSb.toString();

            // input stream
            InputStream in = null;

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("load slice image from " + slicePath);
//#endif

            // read image
            try {

                // read image
                slice.setImage(NavigationScreens.createImage(buffered.reuse(in = cz.kruch.track.TrackingMIDlet.class.getResourceAsStream(slicePath))));

            } finally {

                // close stream
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        // ignore
                    }

                    // clean buffered
                    buffered.reuse(null);
                }
            }
        }
    }

    private final class DirLoader extends Loader {
        private String dir;

        protected DirLoader() {
            super();
        }

        public void init(String url) throws IOException {
            super.init(url);

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("init map " + url);
//#endif

            int i = url.lastIndexOf(File.PATH_SEPCHAR);
            if (i == -1 || i + 1 == url.length()) {
                throw new InvalidMapException("Invalid map URL '" + url + "'");
            }
            dir = url.substring(0, i + 1);

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("slices are in " + dir);
//#endif

            try {
                // read calibration
                if (Map.this.calibration == null) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("do not have calibration yet");
//#endif

                    // helper loader
                    FileInput fileInput = new FileInput(path);

                    // parse known calibration
                    try {
                        i = path.lastIndexOf('.');
                        if (i > -1) {
                            // extension
                            String ext = path.substring(i).toLowerCase();

                            // path points to calibration file
                            if (ext.equals(Calibration.OZI_EXT)) {
                                Map.this.calibration = new Calibration.Ozi(buffered.reuse(fileInput._getInputStream()), path);
                            } else if (ext.equals(Calibration.GMI_EXT)) {
                                Map.this.calibration = new Calibration.GMI(buffered.reuse(fileInput._getInputStream()), path);
                            } else if (ext.equals(Calibration.XML_EXT)) {
                                Map.this.calibration = new Calibration.XML(buffered.reuse(fileInput._getInputStream()), path);
                            } else if (ext.equals(Calibration.J2N_EXT)) {
                                Map.this.calibration = new Calibration.J2N(buffered.reuse(fileInput._getInputStream()), path);
                            }

                            // clear buffered
                            buffered.reuse(null);
                        }

                        // check calibration
                        if (Map.this.calibration == null) {
                            throw new InvalidMapException("Unknown calibration file: " + path);
                        }
                    } catch (InvalidMapException e) {
                        throw e;
                    } catch (IOException e) {
                        throw new InvalidMapException("Failed to parse calibration file: " + path, e);
                    } finally {
                        // close helper loader
                        fileInput.close();
                    }
                }

                // prepare slices
                if (slices == null) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("do not have slices yet");
//#endif

                    // try listing file first
                    File file = null;
                    try {
                        String setFile = path.substring(0, path.lastIndexOf('.')) + ".set";
                        file = File.open(Connector.open(setFile, Connector.READ));
                        if (file.exists()) {
                            LineReader reader = null;
                            try {
                                // each line is a slice filename
                                reader = new LineReader(buffered.reuse(file.openInputStream()));
                                String entry = reader.readLine(false);
                                while (entry != null) {
                                    addSlice(entry);
                                    entry = reader.readLine(false);
                                }
                            } catch (InvalidMapException e) {
                                throw e;
                            } catch (IOException e) {
                                throw new InvalidMapException("Failed to parse listing file", e);
                            } finally {
                                // clear buffered
                                buffered.reuse(null);

                                // close reader (closes the file stream)
                                if (reader != null) {
                                    reader.close();
                                }
                            }
                        } else {
                            // close file
                            file.close();
                            file = null; // gc hint

                            // iterate over set directory
                            file = File.open(Connector.open(dir + SET_DIR_PREFIX, Connector.READ));
                            if (file.exists()) {
                                try {
                                    for (Enumeration e = file.list("*.png", false); e.hasMoreElements(); ) {
                                        addSlice((String) e.nextElement());
                                    }
                                } catch (IOException e) {
                                    throw new InvalidMapException("Could not list tiles in " + file.getURL(), e);
                                }
                            } else {
                                throw new InvalidMapException("Slices directory not found");
                            }
                        }
                    } finally {
                        // close file
                        if (file != null) {
                            file.close();
                            file = null; // gc hint
                        }
                    }
                }
            } catch (Exception e) {
                exception = e;
            }
        }

        public void loadSlice(Slice slice) throws IOException {
            // reuse sb
            StringBuffer pathSb = this.pathSb;
            pathSb.delete(0, pathSb.length());

            // construct slice path
            pathSb.append(dir).append(SET_DIR_PREFIX).append(basename);
            if (!(calibration instanceof Calibration.XML)) {
                slice.appendPath(pathSb);
            }

            // prepare path
            String slicePath = pathSb.toString();

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("load slice image from " + slicePath);
//#endif

            // file input
            FileInput input = new FileInput(slicePath);

            // read image
            try {

                // read image
                slice.setImage(NavigationScreens.createImage(buffered.reuse(input._getInputStream())));

            } finally {

                // gc hint
                buffered.reuse(null);

                // close input
                try {
                    input.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }
}
