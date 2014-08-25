// @LICENSE@

package cz.kruch.track.maps;

import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.ui.Position;
import cz.kruch.track.ui.Desktop;
import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.util.ImageUtils;
import cz.kruch.track.util.NakedVector;
import cz.kruch.track.Resources;

import api.location.QualifiedCoordinates;
import api.location.Datum;
import api.location.ProjectionSetup;
import api.file.File;

import javax.microedition.lcdui.Image;

/**
 * Map representation and handling.
 *  
 * @author kruhc@seznam.cz
 */
public final class Map implements Runnable {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Map");
//#endif

    // map properties
    private String path;
    private String name;

    // interaction with outside world
    /*StateListener*/Desktop listener;

    // map loader
    private Loader loader;

    // map state
    private Calibration calibration;
    private volatile boolean isInUse;

    // special map properties // HACK
    boolean virtual;
    int bgColor;

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

    public int getTileWidth() {
        return loader.tileWidth;
    }

    public int getTileHeight() {
        return loader.tileHeight;
    }

    public ProjectionSetup getProjection() {
        return calibration.getProjection();
    }

    public Datum getDatum() {
        return calibration.getDatum();
    }

    public double getVerticalScale() {
        return calibration.getVerticalScale();
    }

    public int getBgColor() {
        return bgColor;
    }

    public boolean isVirtual() {
        return virtual;
    }

    public double getStep(final char direction) {
        // local ref
        final Calibration calibration = this.calibration;
        final int w = calibration.getWidth();
        final int h = calibration.getHeight();

        // get top-left and right-bottom map coordinates
        final QualifiedCoordinates qc0 = calibration.transform(0, 0);
        final QualifiedCoordinates qc3 = calibration.transform(w, h);

        double step = 0D;

        switch (direction) {
            case 'N':
                step = (qc0.getLat() - qc3.getLat()) / h;
                break;
            case 'S':
                step = (qc3.getLat() - qc0.getLat()) / h;
                break;
            case 'E':
                step = (qc3.getLon() - qc0.getLon()) / w;
                break;
            case 'W':
                step = (qc0.getLon() - qc3.getLon()) / w;
                break;
        }

        QualifiedCoordinates.releaseInstance(qc0);
        QualifiedCoordinates.releaseInstance(qc3);

        return step;
    }

    public QualifiedCoordinates transform(final Position p) {
        return calibration.transform(p.getX(), p.getY());
    }

    public QualifiedCoordinates transform(final int px, final int py) {
        return calibration.transform(px, py);
    }

    public Position transform(final QualifiedCoordinates qc) {
        return calibration.transform(qc);
    }

    public double getRange(final int i) {
        double range = 0D;

        switch (i) {
            case 0:
            case 1: {
                final QualifiedCoordinates qc0 = calibration.transform(0, 0);
                if (i == 0) {
                    range = qc0.getLat();
                } else {
                    range = qc0.getLon();
                }
                QualifiedCoordinates.releaseInstance(qc0);
            } break;
            case 2:
            case 3: {
                final QualifiedCoordinates qc3 = calibration.transform(calibration.getWidth(),
                                                                       calibration.getHeight());
                if (i == 2) {
                    range = qc3.getLat();
                } else {
                    range = qc3.getLon();
                }
                QualifiedCoordinates.releaseInstance(qc3);
            } break;
        }

        return range;
    }

    public boolean isWithin(final QualifiedCoordinates coordinates) {
        return calibration.isWithin(coordinates);
    }

    public void isTmx(final StringBuffer sb) {
        if (loader != null) {
            sb.append(loader.isTmi).append('/').append(loader.isTmc);
        }
    }

    /**
     * Disposes map - releases map images and disposes loader.
     */
    public void dispose() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("dispose map " + getPath());
//#endif

        // no longer in use
        isInUse = false;

        // dispose loader resources
        if (loader != null) {
            try {
                loader.dispose(Config.largeAtlases);
            } catch (IOException e) {
                // ignore
            }
        }

        // release as much as possible
        if (Config.largeAtlases) {
            loader = null;
        }

        // GC
        if (Config.forcedGc) {
            System.gc(); // conditional
        }
    }

    /**
     * Closes map.
     */
    public void close() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("close map " + getPath());
//#endif

        // dispose first
        dispose();

        // gc hints
        loader = null;
        calibration = null;
    }

    /**
     * Gets slice into which given point falls.
     *
     * @param x pixel x (scaled & magnified)
     * @param y pixel y (scaled & magnified)
     * @return slice
     */
    public Slice getSlice(final int x, final int y) {
        return loader.getSlice(descale(x), descale(y));
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
        Desktop.getDiskWorker().enqueue(this);

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
        final Throwable throwable = loadMap();

//#ifdef __LOG__
        if (log.isEnabled()) {
            log.debug("map loading finished for " + getPath() + "; " + throwable);
            log.debug("  tile basename: " + loader.basename);
            log.debug("  tile dimensions: " + loader.tileWidth + "x" + loader.tileHeight);
        }
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
    public boolean ensureImages(final Vector list) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("about to load new slices");
//#endif

        // load images at background
        Desktop.getDiskWorker().enqueue(loader.use(list));

        return true;
    }

    /* (non-javadoc) public only for loading upon startup */
    public Throwable loadMap() {
        try {
            // create loader
            if (loader == null) {
                final String pathLc = path.toLowerCase();
                final Class factory;
                if (pathLc.endsWith(".tar")) {
                    factory = Class.forName("cz.kruch.track.maps.TarLoader");
                } else if (pathLc.endsWith(".jar")) {
                    factory = Class.forName("cz.kruch.track.maps.JarLoader");
                } else if (pathLc.endsWith(".xml")) {
                    factory = Class.forName("cz.kruch.track.maps.NoMapLoader");
                } else {
                    factory = Class.forName("cz.kruch.track.maps.DirLoader");
                }
                loader = (Loader) factory.newInstance();
                loader.init(this, path);
            }

            // prepare loader
            loader.prepare();
            isInUse = true;

            // loads whatever is needed
            loader.loadMeta();

            // check map for consistency
            if (calibration == null) {
                throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_NO_CALIBRATION), getName());
            }
            if (!loader.hasSlices()) {
                throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_NO_SLICES), getName());
            }

            // fix tile info and scale
            loader.fix();

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("map opened");
//#endif

            // GC
            if (Config.forcedGc) {
                System.gc(); // conditional
            }

        } catch (Throwable t) {

            // cleanup
            if (loader != null) {
                try {
                    loader.dispose(true);
                } catch (Exception e) {
                    // ignore
                }
                loader = null; // gc hint
            }

            // propagate map name
            if (t instanceof InvalidMapException) {
                ((InvalidMapException) t).setName(getName());
            }

            return t;
        }

        return null;
    }

    public void setMagnifier(final int x2) {
        calibration.magnify(x2);
    }

    public int scale(final int i) {
        final Calibration calibration = this.calibration;
        return calibration.prescale(i) << calibration.x2;
    }

    public int descale(final int i) {
        final Calibration calibration = this.calibration;
        return calibration.descale(i >> calibration.x2);
    }

    /**
     * Map loader.
     */
    abstract static class Loader implements Runnable {
//#ifdef __LOG__
        protected static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Map.Loader");
//#endif
        protected static final char[] SET_DIR_PREFIX = { 's', 'e', 't', '/' };
        protected static final char[] EXT_PNG = { '.', 'p', 'n', 'g' };
        protected static final char[] EXT_JPG = { '.', 'j', 'p', 'g' };

//#ifndef __CN1__
        private api.io.BufferedInputStream bufferedIn;
//#else
        private api.io.FilterInputStream bufferedIn;
//#endif

        protected Map map;
        protected String basename;
        protected char[] extension;
//#ifdef __SUPPORT_GPSKA__
        protected boolean isGPSka;
//#endif        
        protected boolean isTar, isTmi, isTmc;

        protected int tileWidth, tileHeight;

        private Vector _list;

        abstract void loadMeta() throws IOException;
        abstract void loadIndex(Atlas atlas, String url, String baseUrl) throws IOException;
        abstract void loadSlice(Slice slice) throws IOException;

        Loader() {
            this.tileWidth = this.tileHeight = Integer.MAX_VALUE;
//            ((api.io.BufferedInputStream) bufferef()).setAutofill(true, -1);
        }

        void init(final Map map, final String url) throws IOException {
            this.map = map;
//#ifdef __SUPPORT_GPSKA__
            this.isGPSka = url.toLowerCase().endsWith(Calibration.XML_EXT);
//#endif            
        }

        final void prepare() throws IOException {
            if (bufferedIn == null) {
//#ifdef __SYMBIAN__
                final int size;
                if (isTar && Config.useNativeService && Map.networkInputStreamAvailable) {
                    size = 26280 - 8; // 26280 = 18 * 1460 (MSS) is good for network
                } else {
                    size = Config.inputBufferSize;
                }
                bufferedIn = new api.io.BufferedInputStream(null, size);
//#elifdef __CN1__
                bufferedIn = new api.io.FilterInputStream(null);
//#else
                bufferedIn = new api.io.BufferedInputStream(null, Config.inputBufferSize);
//#endif
            }
        }

        protected void onLoad() {
        }

        final protected File getMetaFile(final String ext, final int mode) throws IOException {
            final String path = map.getPath();
//#ifndef __CN1__
            final String sibPath = path.substring(0, path.lastIndexOf('.')).concat(ext);
//#else
            String sibPath = path.substring(0, path.lastIndexOf('.')).concat(ext);
            if (path.startsWith("file:///Card/")) {
                sibPath = sibPath.substring(13); // "file:///Card/".length()
                final int idx = sibPath.indexOf("TrekBuddy/maps/");
                if (idx > -1) {
                    sibPath = sibPath.substring(idx + 15); // "TrekBuddy/maps/".length()
                }
                sibPath = "file:///Local/".concat(sibPath.replace('/', '_'));
            }
//#endif
            return File.open(sibPath, mode);
        }

        void dispose(final boolean deep) throws IOException {
            if (bufferedIn != null) {
                bufferedIn.setInputStream(null);
                bufferedIn.close();
            }
            bufferedIn = null;
        }

        final void fix() throws InvalidMapException {
            if (tileWidth == Integer.MAX_VALUE || tileHeight == Integer.MAX_VALUE) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("find tile dimensions from root tile");
//#endif
                final Slice slice = getSlice(0, 0);
                if (slice != null) { // TODO always true
                    try {
                        loadSlice(slice);
                        tileWidth = slice.getImage().getWidth();
                        tileHeight = slice.getImage().getHeight();
                        slice.setImage(null);
                    } catch (Exception e) {
                        throw new InvalidMapException("Root tile 0-0 missing");
                    }
                } else {
                    throw new InvalidMapException("Root tile 0-0 missing");
                }
            }
            onLoad();
        }

        final Image scaleImage(final InputStream stream) throws IOException {
            final Calibration mc = getMapCalibration();
            if (mc.x2 == 0 && mc.iprescale == 100) {
                return Image.createImage(stream);
            }
            return ImageUtils.resizeImage(stream, mc.fprescale, mc.x2);
        }

        final long addSlice(final CharArrayTokenizer.Token token) throws InvalidMapException {
            // detect slice basename
            if (basename == null) {
                basename = getBasename(token);
            }

            // detect extension
            if (extension == null) {
                extension = getExtension(token);
            }

            // detect tile dimensions
            final long lxly = Slice.parseXyLong(token);
            final int x = (int) ((lxly >> 20) & 0xfffff);
            final int y = (int) (lxly & 0xfffff);
            if (x > 0 && x < tileWidth) {
                tileWidth = x;
            }
            if (y > 0 && y < tileHeight) {
                tileHeight = y;
            }

            return lxly;
        }

        Slice newSlice() {
            return new Slice();
        }

        /*
         * x,y are demagnified & descaled
         */
        Slice getSlice(final int x, final int y) {
            final Calibration calibration = getMapCalibration();
            final int mw = calibration.getWidthUnscaled();
            final int mh = calibration.getHeightUnscaled();
            final int tw = tileWidth;
            final int th = tileHeight;
            final int sx = (x / tw) * tw;
            final int sy = (y / th) * th;
            final int sw = sx + tw <= mw ? tw : mw - sx;
            final int sh = sy + th <= mh ? th : mh - sy;
            final Slice slice = newSlice();
            slice.setRect(sx, sy, sw, sh);
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("slice for " + x + "-" + y + " is " + slice);
//#endif
            return slice;
        }

        boolean hasSlices() {
            return basename != null;
        }

        void sortSlices(Vector list) {
        }

        public void run() {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("slice loading task started for " + map.getPath());
//#endif
            // notify listener
            map.listener.slicesLoading(null, null);

            // gc
            if (Config.forcedGc) {
                System.gc(); // conditional
            }

            // sort slices by loading order; not needed on platforms with random file access
            sortSlices(_list);

            // load images
            final Throwable throwable = loadImages(_list);

            // end of job
            synchronized (this) {
                _list = null;
            }

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("task finished");
//#endif

            // we are done
            map.listener.slicesLoaded(null, map.isInUse ? throwable : null);
        }

        final Loader use(final Vector list) {
            synchronized (this) {
                if (_list != null) {
                    throw new IllegalStateException("Loading in progress");
                }
                _list = list;
            }

            return this;
        }

        final Calibration getMapCalibration() {
            return map.calibration;
        }

        final String getSliceBasename() {
            return basename;
        }

        private String getBasename(final CharArrayTokenizer.Token token) throws InvalidMapException {
            String name = token.toString();

            if (isTar) {
                name = name.substring(4); // skips leading "set/..."
            }

//#ifdef __SUPPORT_GPSKA__
            if (!isGPSka) {
//#endif
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
//#ifdef __SUPPORT_GPSKA__                
            }
//#endif
/*
            // URL encode
            final StringBuffer sb = new StringBuffer(name.length() + 16);
            for (int N = name.length(), i = 0; i < N; i++) {
                final char c = name.charAt(i);
                if ((c >= 0x30 && c <= 0x39) || (c >= 0x41 && c <= 0x5A) || (c >= 0x61 && c <= 0x7A)) {
                    sb.append(c);
                } else {
                    sb.append('%');
                    if (c <= 0xf) {
                        sb.append('0');
                    }
                    sb.append(Integer.toHexString(c).toUpperCase());
                }
            }
            name = sb.toString();
*/

            return name;
        }

        private static char[] getExtension(final CharArrayTokenizer.Token token) throws InvalidMapException {
            final String name = token.toString();
            final int i = name.lastIndexOf('.');
            if (i > -1) {
                return name.substring(i).toCharArray();
            }
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_INVALID_SLICE_NAME) + name);
        }

        private Throwable loadImages(final Vector slices) {
            // assertions
            if (slices == null) {
                throw new IllegalArgumentException("Slice list is null");
            }

            // locals
            final Object[] array = ((NakedVector) slices).getData();
            final boolean mapIsInUse = map.isInUse;
            final boolean verboseLoading = Config.verboseLoading;
            final StringBuffer sb;
            if (verboseLoading) {
                sb = new StringBuffer(64);
            } else {
                sb = null;
            }

            // load images for given slices
            try {
                for (int N = slices.size(), i = 0; i < N && mapIsInUse; i++) {
                    final Slice slice = (Slice) array[i];
                    if (slice.getImage() == null) {

                        // notify
                        if (verboseLoading) {
                            map.listener.loadingChanged(slice.appendInfo(sb.delete(0, sb.length()).append("Loading ")).toString(), null);
                        }

                        try {
                            // load image
                            try {
                                loadSlice(slice);
                            } catch (IOException e) { // file not found or corrupted
//#ifdef __LOG__
                                e.printStackTrace();
                                if (log.isEnabled()) log.debug("image loading failed: " + e);
//#endif
                                slice.setImage(Slice.NO_IMAGE);
                            } catch (Throwable t) { // typically out of memory
//#ifdef __LOG__
                                t.printStackTrace();
                                if (log.isEnabled()) log.debug("image loading failed: " + t);
//#endif
                                slice.setImage(Slice.NO_IMAGE);
                                throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_SLICE_LOAD_FAILED), t);
                            }

                            // assertion and/or user warning
                            if (slice.getImage() == null/* || slice.getImage() == Slice.NO_IMAGE*/) {
                                throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_NO_SLICE_IMAGE) + " " + slice.toString());
                            }
//#ifdef __LOG__
                            if (log.isEnabled()) log.debug("image loaded for slice " + slice.toString());
//#endif
                        } finally {

                            // notify
                            if (verboseLoading) {
                                map.listener.loadingChanged(null, null);
                            }
                        }
                    }
                }
            } catch (Throwable t) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("image loading for slice failed", t);
//#endif
                return t;

            }

            return null;
        }

        final InputStream bufferef() {
            return bufferedIn;
        }

        final InputStream buffered(final InputStream in) {
            return bufferedIn.setInputStream(in);
        }

        final void bufferel() throws IOException {
            bufferedIn.close();
        }

        static String escape(final String url) {
            int idx = url.indexOf(' ');
            if (idx == -1) {
                return url;
            }
//#ifdef __ANDROID__
            return url;
//#else
            final StringBuffer sb = new StringBuffer(64);
            int s0 = 0;
            while (idx > -1) {
                sb.append(url.substring(s0, idx));
                sb.append("%20");
                s0 = idx + 1;
                idx = url.indexOf(' ', s0);
            }
            if (s0 < url.length()) {
                sb.append(url.substring(s0));
            }
            return sb.toString();
//#endif
        }
    }

    /* stream characteristic */
    public static String fileInputStreamClass;
    public static int fileInputStreamResetable;
//#ifdef __SYMBIAN__
    public static boolean networkInputStreamAvailable = true;
    public static String networkInputStreamError;
    public static String networkInputStreamInfo;
//#endif
    /* behaviour flags */
    public static boolean useReset = true;
    public static boolean useSkip = true;
}
