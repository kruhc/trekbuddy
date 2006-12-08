// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps;

import com.ice.tar.TarInputStream;
import com.ice.tar.TarEntry;

import javax.microedition.io.Connector;
import javax.microedition.lcdui.Image;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStoreFullException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.util.Vector;
import java.util.Enumeration;
import java.util.Hashtable;

import cz.kruch.j2se.io.BufferedInputStream;

import cz.kruch.track.ui.Position;
//#ifdef __LOG__
import cz.kruch.track.util.Logger;
//#endif
import cz.kruch.track.maps.io.LoaderIO;
import cz.kruch.track.io.LineReader;
import cz.kruch.track.AssertionFailedException;

import api.location.QualifiedCoordinates;

public final class Map implements Runnable {

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

    static final int TEXT_FILE_BUFFER_SIZE = 512; // for map calibration files content and tar header blocks
    static final int LARGE_BUFFER_SIZE = 512 * 8; // for map image files - 4 kB

    private static final String SET_DIR_PREFIX = "set/";

    // interaction with outside world
    private String path;
    private String name;
    private StateListener listener;

    // map state
    private Loader loader;
    private Slice[] slices;
    private Calibration calibration;

    public Map(String path, String name, StateListener listener) {
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

        // GC
        System.gc();
    }

    /**
     * Closes map.
     */
    public void close() {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("close map");
//#endif

        // already closed?
        if (slices == null) {
            return;
        }

        // dispose
        dispose();
        slices = null;
        calibration = null;

        // release loader
        try {
            loader.destroy();
        } catch (IOException e) {
        } finally {
            loader = null;
        }
    }

    /**
     * Gets slice into which given point falls.
     * @param x
     * @param y
     * @return slice
     */
    public Slice getSlice(int x, int y) {
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
        LoaderIO.getInstance().enqueue(this);

        return true;
    }

    /**
     * Runnable's run() implementation.
     */
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

    /**
     * Ensures slices have their images loaded.
     */
    public boolean prepareSlices(Vector list) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("prepare slices @" + Integer.toHexString(hashCode()));
//#endif

        // have-all-images flag
        boolean gotAll = true;

        // create list of slices whose images are to be loaded
        for (int i = list.size(); gotAll && --i >= 0; ) {
            Slice slice = (Slice) list.elementAt(i);
            if (slice.getImage() == null) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("image missing for slice " + slice);
//#endif
                gotAll = false;
            }
        }

        // no images to be loaded
        if (gotAll) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("got all slices with images");
//#endif
            return false;
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("about to load new slices");
//#endif

        // load images at background
        LoaderIO.getInstance().enqueue(loader.use(list));

        return true;
    }

    /* (non-javadoc) public only for loading upon startup */
    public Throwable loadMap() {
        try {
            // load map
            if (path.endsWith(".jar")) {
                loader = new JarLoader();
            } else if (path.toLowerCase().endsWith(".tar")) {
                loader = new TarLoader();
            } else {
                loader = new DirLoader();
            }
            loader.init(path);
            loader.doFinal();
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
    public static Map defaultMap(StateListener listener) throws Throwable {
        Map map = new Map("trekbuddy.jar", "Default", listener);
        Throwable t = map.loadMap();
        if (t != null) {
            throw t;
        }

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
    private Throwable loadImages(Vector slices) {
        try {
            for (int i = slices.size(); --i >= 0; ) {
                Slice slice = (Slice) slices.elementAt(i);
                Throwable throwable = null;

                if (slice.getImage() == null) {
                    try {
                        // notify
                        notifyListener(EVENT_LOADING_CHANGED, "Loading " + slice.toString(), null);

                        // use loader
                        loader.load(slice);

                        // got image?
                        if (slice.getImage() == null) {
                            throw new InvalidMapException("No image for slice " + slice.toString());
                        }

//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("image loaded for slice " + slice.getPath());
//#endif
                    } catch (Throwable t) {
                        throwable = t;
                        throw t;
                    } finally {
                        // notify
                        notifyListener(EVENT_LOADING_CHANGED, null, throwable);
                    }
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
        private InputStream in;

        FileInput(String url) {
            this.url = url;
        }

        InputStream getInputStream() throws IOException {
            in = new BufferedInputStream(Connector.openInputStream(url), TEXT_FILE_BUFFER_SIZE);

            return in;
        }

        public String getUrl() {
            return url;
        }

        void close() throws IOException {
            if (in != null) {
                in.close();
                in = null;
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
        for (int i = slices.length; --i >= 0; ) {
            Slice slice = slices[i];
            if (!friendly) {
                slice.gpskaFix();
            }

            // look for dimensions increments
            int x = slice.getX();
            if (x > 0 && x < xi)
                xi = x;
            int y = slice.getY();
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
        private Vector collection;
        protected Exception exception;
        protected String basename;

        public abstract void init(String url) throws IOException;
        public abstract void destroy() throws IOException;
        public abstract void loadSlice(Slice slice) throws IOException;

        protected BufferedInputStream bufferedIn;
        protected StringBuffer pathSb;

        private Vector loadList;

        protected Loader() {
            this.pathSb = new StringBuffer(32);
            this.collection = new Vector();
        }

        public void checkException() throws Exception {
            if (exception != null) {
                throw exception;
            }
        }

        public Loader use(Vector list) {
            synchronized (this) {
                if (loadList != null) {
                    throw new AssertionFailedException("Loading in progress");
                }
                loadList = list;
            }

            return this;
        }

        public void run() {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("slice loading task started @" + Integer.toHexString(Map.this.hashCode()));
//#endif

            // load images
            Throwable throwable = loadImages(loadList);

            // end of job
            synchronized (this) {
                loadList = null;
            }

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("all requested slices loaded @" + Integer.toHexString(Map.this.hashCode()));
//#endif

            // we are done
            notifyListener(EVENT_SLICES_LOADED, null, throwable);
        }

        public void load(Slice slice) throws IOException {
            loadSlice(slice);
        }

        protected void doFinal() {
            slices = new Slice[collection.size()];
            collection.copyInto(slices);
            // gc hints
            collection.removeAllElements();
            collection = null;
        }

        protected Slice addSlice(String filename) throws InvalidMapException {
            if (basename == null) {
                basename = Calibration.Best.getBasename(filename);
            }

            Slice slice = new Slice(new Calibration.Best(filename));
            collection.addElement(slice);

            return slice;
        }
    }

    private abstract class CachingLoader extends Loader {
        private RecordStore rsTiles, rsMeta;
        private Hashtable meta;
        protected ByteArrayOutputStream observer;

        protected CachingLoader() throws IOException, RecordStoreException {
            if (cz.kruch.track.configuration.Config.getSafeInstance().isCacheOffline()) {
                String cacheName = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
                this.rsTiles = RecordStore.openRecordStore("cache_" + cacheName + ".bin",
                                                           true,
                                                           RecordStore.AUTHMODE_ANY,
                                                           true);
                this.rsMeta = RecordStore.openRecordStore("cache_" + cacheName + ".set",
                                                           true,
                                                           RecordStore.AUTHMODE_ANY,
                                                           true);
                this.meta = new Hashtable(this.rsMeta.getNumRecords());
                for (RecordEnumeration e = this.rsMeta.enumerateRecords(null, null, false); e.hasNextElement(); ) {
                    DataInputStream din = new DataInputStream(new ByteArrayInputStream(e.nextRecord()));
                    this.meta.put(din.readUTF(), new Integer(din.readInt()));
                    din.close();
                }
            }
        }

        public void destroy() throws IOException {
            if (rsTiles != null) {
                try {
                    rsTiles.closeRecordStore();
                } catch (RecordStoreException e) {
                    // TODO ignore???
                } finally {
                    rsTiles = null;
                }
            }
            if (rsMeta != null) {
                try {
                    rsMeta.closeRecordStore();
                } catch (RecordStoreException e) {
                    // TODO ignore ???
                } finally {
                    rsMeta = null;
                }
            }
            if (observer != null) {
                observer.close();
                observer = null; // gc hint
            }
            if (meta != null) {
                meta.clear();
                meta = null;
            }
        }

        public void load(Slice slice) throws IOException {
            if (meta != null) {

                // look for slice in cacheOffline
                Integer idx = (Integer) meta.get(slice.getPath());
                if (idx != null) {

                    // init image from cacheOffline
                    try {
                        slice.setImage(Image.createImage(rsTiles.getRecord(idx.intValue()), 0, rsTiles.getRecordSize(idx.intValue())));
                    } catch (RecordStoreException e) {
                        cz.kruch.track.ui.Desktop.showError("Failed to load image from cacheOffline", e, null);
                    }
                }

                // still no image?
                if (slice.getImage() == null) {

                    // load slice with observer
                    if (observer == null) {
                        observer = new ByteArrayOutputStream(LARGE_BUFFER_SIZE);
                    } else {
                        observer.reset();
                    }
                    loadSlice(slice); // same as super.load(slice);

                    // put to cacheOffline
                    try {
                        // store tile image
                        int i = rsTiles.addRecord(observer.toByteArray(), 0, observer.size());

                        // construct meta record
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        DataOutputStream dout = new DataOutputStream(out);
                        dout.writeUTF(slice.getPath());
                        dout.writeInt(i);
                        dout.flush();

                        // store meta record
                        try {
                            rsMeta.addRecord(out.toByteArray(), 0, out.size());
                            meta.put(slice.getPath(), new Integer(i));
                        } catch (RecordStoreException e) {
                            // rollback
                            try {
                                rsTiles.deleteRecord(i);
                            } catch (RecordStoreException e1) {
                                // ignore
                            }
                            throw e; // rethrow
                        } finally {
                            dout.close();
                        }
                    } catch (RecordStoreFullException e) {
                        cz.kruch.track.ui.Desktop.showWarning("Failed to cacheOffline image - not enough memory?", null, null);
                    } catch (RecordStoreException e) {
                        cz.kruch.track.ui.Desktop.showError("Failed to cacheOffline image", e, null);
                    }
                }
            } else {
                // non-cached loading
                loadSlice(slice); // same as super.load(slice);
            }
        }
    }

    /* stream characteristic */
    public static int fileInputStreamResetable = 0;
    /* reset behaviour flag */
    public static boolean useReset = true;

    /*
     * Loader implementations.
     */

    private final class TarLoader extends CachingLoader {
        private static final int MARK_SIZE = 64 * 1024 * 1024;

        private InputStream fsIn;
        private TarInputStream tarIn;

        public TarLoader() throws IOException, RecordStoreException {
            super();
        }

        public void init(String url) throws IOException {
            InputStream in = null;

            try {
                bufferedIn = new BufferedInputStream(in = Connector.openInputStream(path), TarInputStream.useReadSkip ? LARGE_BUFFER_SIZE : TEXT_FILE_BUFFER_SIZE);
                tarIn = new TarInputStream(bufferedIn);

                /*
                 * test quality of JSR-75/FileConnection
                 */

                if (useReset) {
                    if (in.markSupported()) {
                        in.mark(MARK_SIZE); // max 64 MB map
                        fsIn = in;
                        fileInputStreamResetable = 1;
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("input stream support marking, very good");
//#endif
                    } else {
                        try {
                            in.reset(); // try reset
                            fsIn = in;
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

                TarEntry entry = tarIn.getNextEntry();
                while (entry != null) {
                    String entryName = entry.getName();
                    if (entryName.startsWith(SET_DIR_PREFIX) && entryName.endsWith(".png")) { // slice
                        addSlice(entryName.substring(SET_DIR_PREFIX.length())).setClosure(new Long(entry.getPosition()));
                    } else {
                        if (Map.this.calibration == null && entryName.indexOf('/') == -1) {
                            if (entryName.endsWith(".gmi")) {
                                Map.this.calibration = new Calibration.GMI(tarIn, entryName);
                            } else if (entryName.endsWith(".map")) {
                                Map.this.calibration = new Calibration.Ozi(tarIn, entryName);
                            } else if (entryName.endsWith(".xml")) {
                                Map.this.calibration = new Calibration.XML(tarIn, entryName);
                            } else if (entryName.endsWith(".j2n")) {
                                Map.this.calibration = new Calibration.J2N(tarIn, entryName);
                            }
                        }
                    }
                    entry.dispose();
                    entry = null; // gc hint
                    entry = tarIn.getNextEntry();
                }
            } catch (Exception e) {
                exception = e;
            } finally {
                if ((fsIn == null) /* no reuse */ && (in != null)) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("input stream not reusable -> close it");
//#endif
                    try {
                        in.close();
                    } catch (IOException e) {
                    }
                }
            }

            // gc hints
            in = null;
        }

        public void destroy() throws IOException {
            if (fsIn != null) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("closing native stream");
//#endif
                try {
                    fsIn.close();
                } finally {
                    fsIn = null;
                }
            }
            tarIn = null; // no need to close, just forget

            // delegate call to parent
            super.destroy();
        }

        public void loadSlice(Slice slice) throws IOException {
            InputStream in = null;

            try {
                long offset = ((Long) slice.getClosure()).longValue();
                boolean keepPosition = false;
                if (fsIn == null) {
                    bufferedIn.reuse(in = Connector.openInputStream(path));
                } else {
                    try {
                        if (offset < tarIn.getPosition()) {
                            fsIn.reset();
                            bufferedIn.reuse(fsIn);
                        } else {
                            keepPosition = true;
                        }
                    } catch (IOException e) {
                        fsIn = null;
                    }
                }
                tarIn.reuse(bufferedIn, keepPosition);
                tarIn.setPosition(offset);
                TarEntry entry = tarIn.getNextEntry();
                bufferedIn.setObserver(observer);
                slice.setImage(Image.createImage(tarIn));
                bufferedIn.setObserver(null);
                entry.dispose();
            } finally {
                if ((fsIn == null) /* no reuse */ && (in != null)) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("input stream not reusable -> close it");
//#endif
                    try {
                        in.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
    }

    private final class JarLoader extends Loader {
        private static final String RESOURCES_SET = "/resources/set/";

        public void init(String url) throws IOException {
            LineReader reader = null;

            try {
                // load calibration
                InputStream in = cz.kruch.track.TrackingMIDlet.class.getResourceAsStream("/resources/world.map");
                if (in == null) { // no Ozi calibration found
                    in = cz.kruch.track.TrackingMIDlet.class.getResourceAsStream("/resources/world.gmi");
                    if (in == null) { // neither MapCalibrator calibration
                        throw new InvalidMapException("No default map calibration");
                    } else { // got MapCalibrator calibration
                        try {
                            Map.this.calibration = new Calibration.GMI(in, "/resources/world.gmi");
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
                        Map.this.calibration = new Calibration.Ozi(in, "/resources/world.map");
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

                // each line is a slice filename
                reader = new LineReader(new BufferedInputStream(cz.kruch.track.TrackingMIDlet.class.getResourceAsStream("/resources/world.set"), TEXT_FILE_BUFFER_SIZE));
                String entry = reader.readLine(false);
                while (entry != null) {
                    addSlice(entry);
                    entry = null; // gc hint
                    entry = reader.readLine(false);
                }
            } catch (Exception e) {
                exception = e;
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                    }
                }
            }

             // gc hints
            reader = null;
        }

        public void destroy() throws IOException {
        }

        public void loadSlice(Slice slice) throws IOException {
            // reuse sb
            pathSb.setLength(0);

            // get slice path
            String slicePath = pathSb.append(RESOURCES_SET).append(basename).append(slice.getPath()).toString();
            InputStream in = null;

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("load slice image from " + slicePath);
//#endif

            try {
                if (bufferedIn == null) {
                    bufferedIn = new BufferedInputStream(in = cz.kruch.track.TrackingMIDlet.class.getResourceAsStream(slicePath), LARGE_BUFFER_SIZE);
                } else {
                    bufferedIn.reuse(in = cz.kruch.track.TrackingMIDlet.class.getResourceAsStream(slicePath));
                }
                slice.setImage(Image.createImage(bufferedIn));
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
    }

    private final class DirLoader extends CachingLoader {
        private String dir;

        public DirLoader() throws IOException, RecordStoreException {
            super();
        }

        public void init(String url) throws IOException {
            int i = url.lastIndexOf('/');
            if (i == -1 || i + 1 == url.length()) {
                throw new InvalidMapException("Invalid map URL '" + url + "'");
            }
            dir = url.substring(0, i + 1);

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("slices are in " + dir);
//#endif

            api.file.File fc = null;

            try {
                if (Map.this.calibration == null) {
                    // helper loader
                    FileInput fileInput = new FileInput(path);

                    // path points to calibration file
                    if (path.endsWith(".gmi")) {
                        Map.this.calibration = new Calibration.GMI(fileInput.getInputStream(), path);
                    } else if (path.endsWith(".map")) {
                        Map.this.calibration = new Calibration.Ozi(fileInput.getInputStream(), path);
                    } else if (path.endsWith(".xml")) {
                        Map.this.calibration = new Calibration.XML(fileInput.getInputStream(), path);
                    } else if (path.endsWith(".j2n")) {
                        Map.this.calibration = new Calibration.J2N(fileInput.getInputStream(), path);
                    } else {
                        throw new InvalidMapException("Unknown calibration file");
                    }

                    // close helper loader
                    fileInput.close();

                }

                // do we have a list?
                fc = new api.file.File(Connector.open(path.substring(0, path.lastIndexOf('.')) + ".set", Connector.READ));
                if (fc.exists()) {
                    LineReader reader = null;
                    try {
                        // each line is a slice filename
                        reader = new LineReader(new BufferedInputStream(fc.openInputStream(), TEXT_FILE_BUFFER_SIZE));
                        String entry = reader.readLine(false);
                        while (entry != null) {
                            addSlice(entry);
                            entry = null; // gc hint
                            entry = reader.readLine(false);
                        }
                    } catch (IOException e) {
                        throw new InvalidMapException("Failed to parse listing file", e);
                    } finally {
                        if (reader != null) {
                            reader.close();
                        }
                    }
                } else {
                    // close connection for reuse
                    fc.close();
                    fc = null; // gc hint

                    // iterate over set
                    fc = new api.file.File(Connector.open(dir + SET_DIR_PREFIX, Connector.READ));
                    if (fc.exists()) {
                        for (Enumeration e = fc.list("*.png", false); e.hasMoreElements(); ) {
                            addSlice((String) e.nextElement());
                        }
                    } else {
                        throw new InvalidMapException("Slices directory not found");
                    }
                }
            } catch (Exception e) {
                exception = e;
            } finally {
                if (fc != null) {
                    try {
                        fc.close();
                    } catch (IOException e) {
                    }
                }
            }
        }

        public void destroy() throws IOException {
            // delegate to parent
            super.destroy();
        }

        public void loadSlice(Slice slice) throws IOException {
            // reuse sb
            pathSb.setLength(0);

            // get slice path
            String slicePath = pathSb.append(dir).append(SET_DIR_PREFIX).append(basename).append(slice.getPath()).toString();
            InputStream in = null;

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("load slice image from " + slicePath);
//#endif

            try {
                if (bufferedIn == null) {
                    bufferedIn = new BufferedInputStream(in = Connector.openInputStream(slicePath), LARGE_BUFFER_SIZE);
                } else {
                    bufferedIn.reuse(in = Connector.openInputStream(slicePath));
                }
                bufferedIn.setObserver(observer);
                slice.setImage(Image.createImage(bufferedIn));
                bufferedIn.setObserver(null);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
    }
}
