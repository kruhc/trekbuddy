package cz.kruch.track.maps;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.io.LineReader;
import cz.kruch.track.util.ImageUtils;
import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.util.NakedVector;
import cz.kruch.track.Resources;

import java.util.Vector;
import java.io.IOException;
import java.io.InputStream;

import api.file.File;

import javax.microedition.lcdui.Image;

/**
 * Atlas/Map loader.
 */
abstract class Loader implements Runnable {
//#ifdef __LOG__
    protected static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Map.Loader");
//#endif
    protected static final char[] SET_DIR_PREFIX = { 's', 'e', 't', '/' };
    protected static final char[] EXT_PNG = { '.', 'p', 'n', 'g' };
    protected static final char[] EXT_JPG = { '.', 'j', 'p', 'g' };
    protected static final char[] EXT_TBA = { '.', 't', 'b', 'a' };

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

    void loadCalibration() throws IOException {
        throw new java.lang.RuntimeException("override");
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

    final protected void loadDesc(final Atlas atlas, final InputStream in) throws IOException {
        final LineReader reader = new LineReader(in);
        String line = reader.readLine(false); // 1st line "Atlas x.y"
        if (line != null && line.startsWith("Atlas ")) {
            String value = line.substring(6/*"Atlas ".length()*/);
            atlas.setVersion(value);
            line = reader.readLine(false); // 2nd line "Zomm {Off, Layers, Auto}" // since version 1.1
            if (line != null && line.startsWith("Zoom ")) {
                value = line.substring(5/*"Zoom ".length()*/);
                int mode = -1;
                if ("Off".equals(value)) {
                    mode = Config.EASYZOOM_OFF;
                } else if ("Layers".equals(value)) {
                    mode = Config.EASYZOOM_LAYERS;
                } else if ("Auto".equals(value)) {
                    mode = Config.EASYZOOM_AUTO;
                }
                if (mode > -1) {
                    atlas.setPreferredZoomMode(mode);
                }
            }
        }
        // do not close reader, stream must remain open
    }

    final protected File getMetaFile(final String ext, final int mode) throws IOException {
        final String path = map.getPath();
//#ifndef __CN1__
        final String sibPath = path.substring(0, path.lastIndexOf('.')).concat(ext);
//#else
        String sibPath;
        if (cz.kruch.track.TrackingMIDlet.hasFlag("sd_writeable")) {
            sibPath = path.substring(0, path.lastIndexOf('.')).concat(ext);
        } else {
            sibPath = path.substring(0, path.lastIndexOf('.')).concat(ext);
            if (path.startsWith("file:///Card/")) {
                sibPath = sibPath.substring(13); // "file:///Card/".length()
                final int idx = sibPath.indexOf("TrekBuddy/maps/");
                if (idx > -1) {
                    sibPath = sibPath.substring(idx + 15); // "TrekBuddy/maps/".length()
                }
                sibPath = "file:///Local/".concat(sibPath.replace('/', '_'));
            }
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

        // load images for given slices
        try {
            for (int N = slices.size(), i = 0; i < N && mapIsInUse; i++) {
                final Slice slice = (Slice) array[i];
                if (slice.getImage() == null) {

                    // notify
                    if (verboseLoading) {
                        map.listener.loadingChanged(slice.appendInfo((new StringBuffer(64)).append("Loading ")).toString(), null);
                    }

                    try {
                        // load image
                        try {
                            loadSlice(slice);
                        } catch (IOException e) { // file not found or corrupted
//#ifdef __ANDROID__
                            android.util.Log.e(cz.kruch.track.TrackingMIDlet.APP_TITLE, "Tile loading failed", e);
//#endif
//#ifdef __LOG__
                            e.printStackTrace();
                            if (log.isEnabled()) log.debug("image loading failed: " + e);
//#endif
                            slice.setImage(Slice.NO_IMAGE);
                        } catch (Throwable t) { // typically out of memory
//#ifdef __ANDROID__
                            android.util.Log.e(cz.kruch.track.TrackingMIDlet.APP_TITLE, "Tile loading failed", t);
//#endif
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
