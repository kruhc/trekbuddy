// @LICENSE@

package cz.kruch.track.maps;

import java.io.IOException;
import java.util.Vector;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.ui.Position;
import cz.kruch.track.ui.Desktop;
import cz.kruch.track.Resources;

import api.location.QualifiedCoordinates;
import api.location.Datum;
import api.location.ProjectionSetup;

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
    /*private*/ Calibration calibration; // not private for Loader access
    /*private*/ volatile boolean isInUse; // not private for Loader access

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
            if (loader != null) { // may be null when loading fails
                log.debug("  tile basename: " + loader.basename);
                log.debug("  tile dimensions: " + loader.tileWidth + "x" + loader.tileHeight);
            }
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

    /*
     * Accessors for zooming.
     */

    double getVerticalScale() {
        return calibration.getVerticalScale();
    }

    Calibration getCalibration() {
        return calibration;
    }

    /* (non-javadoc) public only for loading just to get calibration */
    Throwable loadCalibration() {
        try {
            // create loader
            if (loader == null) {
                final Class factory = Class.forName("cz.kruch.track.maps.TarLoader");
                loader = (Loader) factory.newInstance();
                loader.init(this, path);
            }

            // loads just calibration
            loader.loadCalibration();

            // check map for consistency
            if (calibration == null) {
                throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_NO_CALIBRATION), getName());
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
