// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps;

import com.ice.tar.TarInputStream;
import com.ice.tar.TarEntry;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.lcdui.Image;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Vector;
import java.util.Enumeration;

import cz.kruch.j2se.io.BufferedInputStream;

import cz.kruch.track.ui.Position;
import cz.kruch.track.util.Logger;

import api.location.QualifiedCoordinates;

public class Map {

    public interface StateListener {
        public void mapOpened(Object result, Throwable throwable);
        public void slicesLoaded(Object result, Throwable throwable);
        public void loadingChanged(Object result, Throwable throwable);
    }

    private static final Logger log = new Logger("Map");

    private static final int EVENT_MAP_OPENED       = 0;
    private static final int EVENT_SLICES_LOADED    = 1;
    private static final int EVENT_LOADING_CHANGED  = 2;

    private static final int TEXT_FILE_BUFFER_SIZE = 512; // for map calibration files content
    private static final int SMALL_BUFFER_SIZE = 512 * 2; // for map calibration files
    private static final int LARGE_BUFFER_SIZE = 512 * 8; // for map image files

    private static final int TYPE_GMI        = 0;
    private static final int TYPE_GPSKA      = 1;
    private static final int TYPE_J2N        = 2;
    private static final int TYPE_BEST       = 3;

    private String path;
    private StateListener listener;

    private Slice[] slices;
    private Calibration calibration;
    private int type = -1;
    private Loader loader;

    private boolean ready = true;
    private Object lock = new Object();

    // map range and scale
    private QualifiedCoordinates[] range;
/*
    private double xscale, yscale;
*/

    public Map(String path, StateListener listener) {
        this.path = path;
        this.listener = listener;
        this.slices = new Slice[0];
    }

    public String getPath() {
        return path;
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
        return calibration.transform(qc);
    }

    /**
     * Closes map - releases map images, destroy loader.
     * Does gc at the end.
     */
    public void close() {
        if (log.isEnabled()) log.info("close map");

        // null images
        for (int N = slices.length, i = 0; i < N; i++) {
            if (log.isEnabled()) log.debug("null image for slice " + slices[i]);
            slices[i].setImage(null);
        }

        // destroy loader
        if (loader != null) {
            try {
                loader.destroy();
                if (log.isEnabled()) log.debug("loader destroyed");
                loader = null;
            } catch (IOException e) {
                if (log.isEnabled()) log.warn("failed to destroy loader");
            }
        }

        // gc
        System.gc();
    }

    public Slice getSlice(int x, int y) {
        Slice result = null;
        for (int N = slices.length, i = 0; i < N; i++) {
            Slice slice = slices[i];
            if (slice.isWithin(x, y)) {
                result = slice;
                break;
            }
        }

        return result;
    }

    public boolean isWithin(QualifiedCoordinates coordinates) {
        return (coordinates.getLat() <= range[0].getLat() && coordinates.getLat() >= range[3].getLat())
                && (coordinates.getLon() >= range[0].getLon() && coordinates.getLon() <= range[3].getLon());
    }

    /**
     * Opens and scans map.
     */
    public boolean prepareMap() {
        if (log.isEnabled()) log.debug("begin open map");

        // wait for previous task to finish
        synchronized (lock) {
            while (!ready) {
                if (log.isEnabled()) log.debug("wait for lock");
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                }
            }
            ready = false;
        }

        // open map in background
        (new Thread(new Runnable() {
            public void run() {
                if (log.isEnabled()) log.debug("map loading task started");

                // open and init map
                Throwable t = loadMap();

                // sync
                synchronized (lock) {
                    ready = true;
                    lock.notify();
                }

                // log
                if (log.isEnabled()) log.debug("map opened; " + t);

                // we are done
                notifyListener(EVENT_MAP_OPENED, null, t);
            }
        })).start();

        if (log.isEnabled()) log.debug("~begin open map");

        return true;
    }

    /**
     * Ensures slices have their images loaded.
     * @param slices
     */
    public boolean prepareSlices(Vector slices) {
        Vector collection = null;

        // create list of slices whose images are to be loaded
        for (Enumeration e = slices.elements(); e.hasMoreElements(); ) {
            Slice slice = (Slice) e.nextElement();
            if (slice.getImage() == null) {
                if (log.isEnabled()) log.debug("image missing for slice " + slice);

                if (collection == null) {
                    collection = new Vector();
                }
                collection.addElement(slice);
            }
        }

        // no images to be loaded
        if (collection == null) {
            return false;
        }

        // debug
        if (log.isEnabled()) log.debug("about to load new slices");

        // trick
        final Vector toload = collection;

        // wait for previous task to finish
        synchronized (lock) {
            while (!ready) {
                if (log.isEnabled()) log.debug("wait for lock");
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                }
            }
            ready = false;
        }

        // load images at background
        (new Thread(new Runnable() {
            public void run() {
                if (log.isEnabled()) log.debug("slice loading task started");

                // load images
                Throwable t = loadSlices(toload);

                // sync
                synchronized (lock) {
                    ready = true;
                    lock.notify();
                }

                // log
                if (log.isEnabled()) log.debug("all requested slices loaded");

                // we are done
                notifyListener(EVENT_SLICES_LOADED, null, t);
            }
        })).start();

        return true;
    }

    /* public only for init loading */
    public Throwable loadMap() {
        try {
            // load map
            loader = path.endsWith(".tar") ? (Loader) new TarLoader() : (Loader) new DirLoader();
            loader.init();
            loader.run();
            loader.checkException();

            if (log.isEnabled()) log.debug("map opened");

            /*
            * keep the connection open to avoid access confirmation
            */

            // check map for consistency
            if (calibration == null) {
                throw new InvalidMapException("Map calibration info missing");
            }
            if (slices.length == 0) {
                throw new InvalidMapException("Empty map - no slices");
            }

            // absolutize slices position
            for (int N = slices.length, i = 0; i < N; i++) {
                slices[i].absolutizePosition(calibration);
            }

            // type-specific ops
            switch (type) {
                case TYPE_BEST: {
                    if (log.isEnabled()) log.debug("fix slices dimension");
                    // fix slices dimension
                    for (int N = slices.length, i = 0; i < N; i++) {
                        Slice slice = slices[i];
                        ((Calibration.Best) slice.getCalibration()).fixDimension(calibration, slices);
                    }
                } break;
                default:
                    if (log.isEnabled()) log.debug("no map specific ops for map of type " + type);
            }

            // debug
            if (log.isEnabled()) {
                for (int N = slices.length, i = 0; i < N; i++) {
                    log.debug("ready slice " + slices[i]);
                }
            }

            // compute map range and scale
            precalculate();

            // log
            if (log.isEnabled()) log.info("map range is "  + range[0] + "(" + range[0].getLat() + "," + range[0].getLon() + ") - " + range[1] + "(" + range[1].getLat() + "," + range[1].getLon() + ")");

            return null;

        } catch (InvalidMapException e) {
            return e;
        } catch (IOException e) {
            return e;
        } catch (OutOfMemoryError e) {
            return e;
        }
    }

    /**
     * Precomputes map range.
     */
    private void precalculate() {
        calibration.computeGrid();
        range = new QualifiedCoordinates[4];
        range[0] = calibration.transform(new Position(0, 0));
        range[1] = calibration.transform(new Position(0, getWidth()));
        range[2] = calibration.transform(new Position(getHeight(), 0));
        range[3] = calibration.transform(new Position(getWidth(), getHeight()));
    }

    /**
     * Loads images for given slices.
     * @param toLoad
     */
    private Throwable loadSlices(Vector toLoad) {
        try {
            for (Enumeration e = toLoad.elements(); e.hasMoreElements(); ) {
                loadSlice((Slice) e.nextElement());
            }

            return null;

        } catch (IOException e) {
            return e;
        } catch (OutOfMemoryError e) {
            return e;
        }
    }

    /**
     * Releases slices images. Does gc.
     * @param slices
     */
    public void releaseSlices(Vector slices) {
        // clear slices image references
        for (Enumeration e = slices.elements(); e.hasMoreElements(); ) {
            Slice slice = (Slice) e.nextElement();
            slice.setImage(null);

            if (log.isEnabled()) log.debug("released image for slice " + slice);
        }

        // gc
        System.gc();
    }

    /**
     * Loads slice from map. Expects file connection opened.
     */
    private void loadSlice(Slice slice) throws IOException {
        if (log.isEnabled()) log.debug("Loading slice " + slice.getCalibration().getPath() + "...");

        // notify
        notifyListener(EVENT_LOADING_CHANGED, "Loading " + slice.getCalibration().getPath() + "...", null);

        IOException exception = null;
        OutOfMemoryError error = null;
        Throwable throwable = null;

        // load slice via loader
        try {
            // use loader
            loader.loadSlice(slice);

            // got image?
            if (slice.getImage() == null) {
                throw new InvalidMapException("No image " + slice.getCalibration().getPath());
            }

            // log
            if (log.isEnabled()) log.debug("image loaded for slice " + slice.getCalibration().getPath());

        } catch (IOException e) {
            // log
            if (log.isEnabled()) log.error("image loading failed for slice " + slice.getCalibration().getPath(), e);

            throwable = exception = e;

        } catch (OutOfMemoryError e) {
            // log
            if (log.isEnabled()) log.error("image loading failed for slice " + slice.getCalibration().getPath(), e);

            throwable = error = e;
        }

        // notify
        notifyListener(EVENT_LOADING_CHANGED, null, throwable);

        // upon no exception just return
        if (exception == null) {
            if (error == null) {
                return;
            } else {
                throw error;
            }
        }

        throw exception;
    }

    private void notifyListener(final int code, final Object result, final Throwable t) {
        (new Thread() {
            public void run() {
                switch (code) {
                    case EVENT_MAP_OPENED:
                        listener.mapOpened(result, t);
                        break;
                    case EVENT_SLICES_LOADED:
                        listener.slicesLoaded(result, t);
                        break;
                    case EVENT_LOADING_CHANGED:
                        listener.loadingChanged(result, t);
                        break;
                }
            }
        }).start();
    }

    private abstract class Loader extends Thread {
        protected IOException exception;

        public abstract void init() throws IOException;

        public abstract void destroy() throws IOException;

        public abstract void loadSlice(Slice slice) throws IOException;

        public void checkException() throws IOException {
            if (exception != null) {
                throw exception;
            }
        }

        protected String loadTextContent(InputStream stream) throws IOException {
            InputStreamReader reader = new InputStreamReader(stream);
            char[] buffer = new char[TEXT_FILE_BUFFER_SIZE];
            StringBuffer sb = new StringBuffer();
            int c = reader.read(buffer);
            while (c > -1) {
                sb.append(buffer, 0, c);
                c = reader.read(buffer);
            }

            return sb.toString();
        }
    }

    private class TarLoader extends Loader {
        private FileConnection fileConnection;

        public void init() throws IOException {
            fileConnection = (FileConnection) Connector.open(path, Connector.READ);
            if (log.isEnabled()) log.debug("map connection opened; " + path);
        }

        public void destroy() {
            // close file connection
            if (fileConnection != null) {
                try {
                    fileConnection.close();
                } catch (IOException e) {
                    if (log.isEnabled()) log.error("map closing failed", e);
                }
            }
        }

        public void run() {
            Vector collection = new Vector(0);
            TarInputStream tar = null;

            try {
                tar = new TarInputStream(new BufferedInputStream(fileConnection.openInputStream(), SMALL_BUFFER_SIZE));
                TarEntry entry = tar.getNextEntry();
                while (entry != null) {
                    if (!entry.isDirectory()) {
                        String entryName = entry.getName();
                        if (entryName.endsWith(".gmi")) {
                            Calibration calibration = new Calibration.GMI(loadTextContent(tar), entryName);
                            if (entryName.indexOf("/") == -1) { // map calibration
                                Map.this.calibration = calibration;
                                Map.this.type = TYPE_BEST;
                            } else {
                                if (log.isEnabled()) log.warn("calibration file " + entryName + " ignored");
                            }
                        } else if (entryName.endsWith(".map")) {
                            Calibration calibration = new Calibration.Ozi(loadTextContent(tar), entryName);
                            if (entryName.indexOf("/") == -1) { // map calibration
                                Map.this.calibration = calibration;
                                Map.this.type = TYPE_BEST;
                            } else {
                                if (log.isEnabled()) log.warn("calibration file " + entryName + " ignored");
                            }
                        } else if (entryName.endsWith(".xml")) {
                            Calibration calibration = new Calibration.XML(loadTextContent(tar), entryName);
                            if (entryName.startsWith("pictures/")) {
                                collection.addElement(new Slice(calibration));
                            } else {
                                Map.this.calibration = calibration;
                                Map.this.type = TYPE_GPSKA;
                            }
                        } else if (entryName.endsWith(".j2n")) {
                            Calibration calibration = new Calibration.J2N(loadTextContent(tar), entryName);
                            if (entryName.indexOf("/") == -1) { // map calibration
                                Map.this.calibration = calibration;
                                Map.this.type = TYPE_J2N;
                            } else {
                                if (log.isEnabled()) log.warn("calibration file " + entryName + " ignored");
                            }
                        } else if ((entryName.startsWith("set/") || (entryName.startsWith("pictures/"))) && entryName.endsWith(".png")) {
                            Calibration calibration = new Calibration.Best(entryName);
                            collection.addElement(new Slice(calibration));
                            Map.this.type = TYPE_BEST;
                        }
                    }
                    entry = tar.getNextEntry();
                }
            } catch (IOException e) {
                exception = e;
            } finally {
                if (tar != null) {
                    try {
                        tar.close();
                    } catch (IOException e) {
                    }
                }
            }

            slices = new Slice[collection.size()];
            collection.copyInto(slices);
        }

        public void loadSlice(Slice slice) throws IOException {
            TarInputStream tar = null;

            try {
                tar = new TarInputStream(new BufferedInputStream(fileConnection.openInputStream(), LARGE_BUFFER_SIZE));
                TarEntry entry = tar.getNextEntry();
                while (entry != null) {
                    if (entry.getName().endsWith(slice.getCalibration().getPath())) {
                        slice.setImage(Image.createImage(tar));
                        break;
                    }
                    entry = tar.getNextEntry();
                }
            } finally {
                if (tar != null) {
                    try {
                        tar.close();
                    } catch (IOException e) {
                        if (log.isEnabled()) log.error("failed to close input stream", e);
                    }
                }
            }

        }
    }

    private class DirLoader extends Loader {
        private String dir;

        public void init() throws IOException {
            int i = path.lastIndexOf('/');
            if (i == -1) {
                throw new InvalidMapException("Invalid map URL");
            }
            dir = path.substring(0, i);
            if (log.isEnabled()) log.debug("slices are in " + dir);
        }

        public void destroy() throws IOException {
        }

        public void run() {
            Vector collection = new Vector(0);
            FileConnection fc = null;
            InputStream in = null;

            try {
                // path points to calibration file
                fc = (FileConnection) Connector.open(path, Connector.READ);
                String url = fc.getURL();
                in = fc.openInputStream();
                if (url.endsWith(".gmi")) {
                    Map.this.calibration = new Calibration.GMI(loadTextContent(in), url);
                    Map.this.type = TYPE_BEST;
                } else if (url.endsWith(".map")) {
                    Map.this.calibration = new Calibration.Ozi(loadTextContent(in), url);
                    Map.this.type = TYPE_BEST;
                } else if (url.endsWith(".xml")) {
                    Map.this.calibration = new Calibration.XML(loadTextContent(in), url);
                    Map.this.type = TYPE_GPSKA;
                } else if (url.endsWith(".j2n")) {
                    Map.this.calibration = new Calibration.J2N(loadTextContent(in), url);
                    Map.this.type = TYPE_J2N;
                }
                in.close();
                in = null;
                fc.close();
                fc = null;

                // next, look for slices
                dir = dir + (Map.this.type == TYPE_J2N ? "/pictures" : "/set"); // compatibility with j2n
                fc = (FileConnection) Connector.open(dir, Connector.READ);
                if (fc.isDirectory()) {
                    for (Enumeration e = fc.list("*", false); e.hasMoreElements(); ) {
                        Calibration calibration = new Calibration.Best((String) e.nextElement());
                        collection.addElement(new Slice(calibration));
                        Map.this.type = TYPE_BEST;
                    }
                }

            } catch (IOException e) {
                exception = e;
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

            slices = new Slice[collection.size()];
            collection.copyInto(slices);
        }

        public void loadSlice(Slice slice) throws IOException {
            String spath = dir + "/" + slice.getCalibration().getPath();
            if (log.isEnabled()) log.debug("load slice image from " + spath);

            InputStream in = null;

            try {
                in = Connector.openInputStream(spath);
                slice.setImage(Image.createImage(in));
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

    private static final String defaultMapCalibration =
        "Point01,xy,0,0,in,deg,90,0,N,180,0,W,grid,,,,N\n" +
        "Point02,xy,639,0,in,deg,90,0,N,180,0,E,grid,,,,N\n" +
        "Point03,xy,0,319,in,deg,90,0,S,180,0,W, grid,,,,N\n" +
        "Point04,xy,639,319,in,deg,90,0,S,180,0,E, grid,,,,N\n" +
        "Point05,xy,320,160,in,deg,0,0,N,0,0,E, grid,,,,N\n" +
        "IWH,Map Image Width/Height,640,320";

    public static Map defaultMap(StateListener listener) throws IOException {
        Map map = new Map("", listener);
        map.calibration = new Calibration.Ozi(defaultMapCalibration, "resource:///resources/world_0_0.png");
/*
        map.range = new QualifiedCoordinates[2];
        map.range[0] = map.calibration.transform(new Position(0, 0));
        map.range[1] = map.calibration.transform(new Position(map.calibration.getWidth() - 1,
                                                              map.calibration.getHeight() - 1));
*/
        map.precalculate();
        Slice slice = new Slice(map.calibration);
        slice.absolutizePosition(map.calibration);
        slice.setImage(Image.createImage("/resources/world_0_0.png"));
        map.slices = new Slice[] { slice };

        if (log.isEnabled()) log.debug("default map slice ready " + slice);

        return map;
    }
}
