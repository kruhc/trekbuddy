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
import java.util.Hashtable;

import cz.kruch.j2se.io.BufferedInputStream;

import cz.kruch.track.ui.Position;
import cz.kruch.track.util.Logger;

import api.location.QualifiedCoordinates;

public final class Map {

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

    // map interaction with outside world
    private String path;
    private StateListener listener;

    // map state
    private Loader loader;
    private int type = -1;
    private Hashtable sets;
    private Hashtable calibrations;

    // layer state
    private LayerState layer;
    private LayerState defaultLayer;

    // I/O state
    private boolean ready = true;
    private Object lock = new Object();

    public Map(String path, StateListener listener) {
        this.path = path;
        this.listener = listener;
        this.sets = new Hashtable();
        this.calibrations = new Hashtable();
        this.layer = new LayerState();
    }

    public String getPath() {
        return path;
    }

    public int getWidth() {
        return layer.calibration.getWidth();
    }

    public int getHeight() {
        return layer.calibration.getHeight();
    }

    public QualifiedCoordinates transform(Position p) {
        return layer.calibration.transform(p);
    }

    public Position transform(QualifiedCoordinates qc) {
        return layer.calibration.transform(qc, proximitePosition(qc));
    }

    public Enumeration getLayers() {
        return calibrations.keys();
    }

    public boolean changeLayer(String name) {
        if (log.isEnabled()) log.debug("change layer from " + layer.name + " to " + name);

        // detect no-change
        if (name.equals(layer.name)) {
            return false;
        }

        // detect change to default layer
        if ("set/".equals(name)) {
            layer = defaultLayer;
        } else {
            // new layer
            LayerState newLayer = new LayerState();
            newLayer.name = name;
//            newLayer.calibration = defaultLayer.calibration.resize(0.50F);
            newLayer.calibration = (Calibration) calibrations.get(name);
            loader.doFinal(newLayer);
            newLayer.doFinal();

            // dispose current layer
            layer.dispose();

            // set new layer
            layer = newLayer;
        }

        return true;
    }

    /**
     * Closes map - releases map images, destroy loader.
     * Does gc at the end.
     */
    public void close() {
        if (log.isEnabled()) log.info("close map");

        // release layer resources
        layer.dispose();

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

    /**
     * Gets slice into which given point falls.
     * @param x
     * @param y
     * @return slice
     */
    public Slice getSlice(int x, int y) {
        Slice[] slices = layer.slices;
        for (int N = slices.length, i = 0; i < N; i++) {
            Slice slice = slices[i];
            if (slice.isWithin(x, y)) {
                return slice;
            }
        }

        return null;
    }

    // TODO optimize access to range
    public boolean isWithin(QualifiedCoordinates coordinates) {
        double lat = coordinates.getLat();
        double lon = coordinates.getLon();
        QualifiedCoordinates[] range = layer.range;
        return (lat <= range[0].getLat() && lat >= range[3].getLat())
                && (lon >= range[0].getLon() && lon <= range[3].getLon());
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

            // check map for consistency
            if (layer.calibration == null) {
                throw new InvalidMapException("Map calibration info missing");
            }
            if (layer.slices.length == 0) {
                throw new InvalidMapException("Empty map - no slices");
            }

            // prepare layer
            layer.doFinal();

            // log
            if (log.isEnabled()) log.info("map range is "  + layer.range[0] + "(" + layer.range[0].getLat() + "," + layer.range[0].getLon() + ") - " + layer.range[1] + "(" + layer.range[3].getLat() + "," + layer.range[3].getLon() + ")");

        } catch (Throwable t) {
            return t;
        }

        return null;
    }


    /**
     * Creates default map from embedded resources.
     */
    public static Map defaultMap(StateListener listener) throws IOException {
        Map map = new Map("", listener);
        InputStream in = Map.class.getResourceAsStream("/resources/world.map");
        map.type = TYPE_BEST;
        map.layer.calibration = new Calibration.Ozi(loadTextContent(in), "/resources/world.map");
        in.close();
        Slice slice = new Slice(new Calibration.Best("/resources/world_0_0.png"));
        slice.setImage(Image.createImage("/resources/world_0_0.png"));
        slice.absolutizePosition(map.layer.calibration);
        map.layer.slices = new Slice[] { slice };
        ((Calibration.Best) slice.getCalibration()).fixDimension(map.layer.calibration, map.layer.slices);
        slice.precalculate();
        map.layer.doFinal();

        if (log.isEnabled()) log.debug("default map slice ready " + slice);

        return map;
    }

    /**
     * Calculates proximite position for given coordinates.
     */
    private Calibration.ProximitePosition proximitePosition(QualifiedCoordinates coordinates) {
        QualifiedCoordinates leftTopQc = layer.range[0];

        double dlon = coordinates.getLon() - leftTopQc.getLon();
        double dlat = coordinates.getLat() - leftTopQc.getLat();

        Double dx = new Double(dlon / layer.xScale);
        Double dy = new Double(dlat / layer.yScale);

        int intDx = dx.intValue();
        int intDy = dy.intValue();

        int x = 0 + intDx;
        int y = 0 - intDy;

        return new Calibration.ProximitePosition(x, y);
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
     * Releases given slices images. Does gc.
     * @param slices
     */
    public void releaseSlices(Vector slices) {
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

    // TODO is thread necessary?
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

    private static String loadTextContent(InputStream stream) throws IOException {
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

    /*
     * Layer state.
     */

    private static class LayerState {
        public String name;
        public Slice[] slices;
        public Calibration calibration;
        public QualifiedCoordinates[] range;
        public double xScale, yScale;

        public LayerState() {
            this.slices = new Slice[0];
        }

        /**
         * Disposes layer resources - slices images.
         */
        public void dispose() {
            for (int N = slices.length, i = 0; i < N; i++) {
                if (log.isEnabled()) log.debug("null image for slice " + slices[i]);
                slices[i].setImage(null);
            }
        }

        /**
         * Finalizes layer initialization.
         */
        public void doFinal() {
            // absolutize slices position
            absolutizePositions();

            // finalize slices creation
            prepareSlices();

            // compure grid and range
            calibration.computeGrid();
            range = new QualifiedCoordinates[4];
            range[0] = calibration.transform(new Position(0, 0));
            range[1] = calibration.transform(new Position(0, calibration.getWidth()));
            range[2] = calibration.transform(new Position(calibration.getHeight(), 0));
            range[3] = calibration.transform(new Position(calibration.getWidth(), calibration.getHeight()));
            xScale = Math.abs((range[3].getLon() - range[0].getLon()) / calibration.getWidth());
            yScale = Math.abs((range[3].getLat() - range[0].getLat()) / calibration.getHeight());
        }

        private void absolutizePositions() {
            for (int N = slices.length, i = 0; i < N; i++) {
                slices[i].absolutizePosition(calibration);
            }
        }

        private void prepareSlices() {
            for (int N = slices.length, i = 0; i < N; i++) {
                Slice slice = slices[i];

                // fix slice dimension for filename-encoded positions
                if (slice.getCalibration() instanceof Calibration.Best) {
                    ((Calibration.Best) slice.getCalibration()).fixDimension(calibration, slices);
                }

                // precalculate slice range (pixel coordinates)
                slice.precalculate();

                // debug
                if (log.isEnabled()) log.debug("ready slice " + slices[i]);
            }
        }
    }

    /*
     * Map loaders.
     */

    private abstract class Loader extends Thread {
        protected Exception exception;

        public abstract void init() throws IOException;
        public abstract void destroy() throws IOException;
        public abstract void loadSlice(Slice slice) throws IOException;

        public void checkException() throws Exception {
            if (exception != null) {
                throw exception;
            }
        }

        protected Vector getCollection(String collectionName) {
            Vector collection = (Vector) sets.get(collectionName);
            if (collection == null) {
                collection = new Vector();
                sets.put(collectionName, collection);
            }

            return collection;
        }

        protected void doFinal(LayerState layer) {
            if (layer.name == null) { // default layer upon start
                defaultLayer = layer;
                switch (Map.this.type) {
                    case TYPE_BEST:
                        layer.name = "set/";
                        break;
                    default:
                        layer.name = "pictures/";
                }
            }
            Vector collection = getCollection(layer.name);
            layer.slices = new Slice[collection.size()];
            collection.copyInto(layer.slices);
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
                    // ignore
                }
            }
        }

        public void run() {
            TarInputStream tar = null;
            try {
                tar = new TarInputStream(new BufferedInputStream(fileConnection.openInputStream(), SMALL_BUFFER_SIZE));
                TarEntry entry = tar.getNextEntry();
                while (entry != null) {
                    if (!entry.isDirectory()) {
                        String entryName = entry.getName();
                        if (entryName.endsWith(".png")) { // slice
                            int indexOf = entryName.indexOf("/");
                            if (indexOf > -1) {
                                String collectionName = entryName.substring(0, indexOf + 1);
                                Calibration calibration = new Calibration.Best(entryName);
                                getCollection(collectionName).addElement(new Slice(calibration));
                            } else {
                                if (log.isEnabled()) log.warn("image file " + entryName + " ignored");
                            }
                        } else {
                            int indexOf = entryName.indexOf("/");
                            String collectionName = null;
                            if (indexOf > -1) {
                                collectionName = entryName.substring(0, indexOf + 1);
                            }
                            if (entryName.endsWith(".gmi")) {
                                Calibration calibration = new Calibration.GMI(loadTextContent(tar), entryName);
                                if (indexOf == -1) { // default layer calibration
                                    Map.this.layer.calibration = calibration;
                                    Map.this.type = TYPE_BEST;
                                } else {
                                    calibrations.put(collectionName, calibration);
                                }
                            } else if (entryName.endsWith(".map")) {
                                Calibration calibration = new Calibration.Ozi(loadTextContent(tar), entryName);
                                if (indexOf == -1) { // default layer calibration
                                    Map.this.layer.calibration = calibration;
                                    Map.this.type = TYPE_BEST;
                                } else {
                                    calibrations.put(collectionName, calibration);
                                }
                            } else if (entryName.endsWith(".xml")) {
                                Calibration calibration = new Calibration.XML(loadTextContent(tar), entryName);
                                if (indexOf == -1) { // default layer calibration
                                    Map.this.layer.calibration = calibration;
                                    Map.this.type = TYPE_GPSKA;
                                } else {
                                    calibrations.put(collectionName, calibration);
                                }
                            } else if (entryName.endsWith(".j2n")) {
                                Calibration calibration = new Calibration.J2N(loadTextContent(tar), entryName);
                                if (indexOf == -1) { // default layer calibration
                                    Map.this.layer.calibration = calibration;
                                    Map.this.type = TYPE_J2N;
                                } else {
                                    calibrations.put(collectionName, calibration);
                                }
                            }
                        }
                    }
                    entry = tar.getNextEntry();
                }
            } catch (Exception e) {
                exception = e;
            } finally {
                if (tar != null) {
                    try {
                        tar.close();
                    } catch (IOException e) {
                    }
                }
            }

            doFinal(layer);
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
            dir = path.substring(0, i + 1);
            if (log.isEnabled()) log.debug("slices are in " + dir);
        }

        public void destroy() throws IOException {
        }

        public void run() {
            FileConnection fc = null;
            InputStream in = null;

            try {
                // path points to calibration file
                in = new BufferedInputStream(Connector.openInputStream(path), SMALL_BUFFER_SIZE);
                String content = loadTextContent(in);
                if (path.endsWith(".gmi")) {
                    Map.this.layer.calibration = new Calibration.GMI(content, path);
                    Map.this.type = TYPE_BEST;
                } else if (path.endsWith(".map")) {
                    Map.this.layer.calibration = new Calibration.Ozi(content, path);
                    Map.this.type = TYPE_BEST;
                } else if (path.endsWith(".xml")) {
                    Map.this.layer.calibration = new Calibration.XML(content, path);
                    Map.this.type = TYPE_GPSKA;
                } else if (path.endsWith(".j2n")) {
                    Map.this.layer.calibration = new Calibration.J2N(content, path);
                    Map.this.type = TYPE_J2N;
                }
                in.close();
                in = null;

                // get all possible sets dirs
                fc = (FileConnection) Connector.open(dir, Connector.READ);
                Enumeration children = fc.list("*", false);

                // next, look for slices in sets
                while (children.hasMoreElements()) {
                    String child = (String) children.nextElement();
                    if (child.endsWith("/")) {
                        // debug
                        if (log.isEnabled()) log.debug("new set: " + child);

                        // get collection
                        Vector collection = getCollection(child);

                        // set file connection
                        fc.setFileConnection(child);

                        // iterate over set
                        for (Enumeration e = fc.list("*", false); e.hasMoreElements(); ) {
                            String entry = ((String) e.nextElement());
                            if (entry.endsWith(".png")) {
                                Calibration calibration = new Calibration.Best(child + entry);
                                collection.addElement(new Slice(calibration));
                            } else {
                                InputStream cin = null;
                                Calibration calibration = null;
                                try {
                                    cin = new BufferedInputStream(Connector.openInputStream(fc.getURL() + entry), SMALL_BUFFER_SIZE);
                                    content = loadTextContent(cin);
                                    if (entry.endsWith(".gmi")) {
                                        calibration = new Calibration.GMI(content,child + entry);
                                    } else if (entry.endsWith(".map")) {
                                        calibration = new Calibration.Ozi(content,child + entry);
                                    } else if (entry.endsWith(".j2n")) {
                                        calibration = new Calibration.J2N(content,child + entry);
                                    } else if (entry.endsWith(".xml")) {
                                        calibration = new Calibration.XML(content,child + entry);
                                    }
                                } finally {
                                    if (cin != null) {
                                        try {
                                            cin.close();
                                        } catch (IOException ex) {
                                            // ignore
                                        }
                                    }
                                }
                                if (calibration != null) {
                                    calibrations.put(child, calibration);
                                }
                            }
                        }

                        // go back to map root dir
                        fc.setFileConnection("..");
                    }
                }
            } catch (Exception e) {
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

            doFinal(layer);
        }

        public void loadSlice(Slice slice) throws IOException {
            String slicePath = dir + slice.getCalibration().getPath();
            if (log.isEnabled()) log.debug("load slice image from " + slicePath);

            InputStream in = null;
            try {
                in = new BufferedInputStream(Connector.openInputStream(slicePath), LARGE_BUFFER_SIZE);
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
}
