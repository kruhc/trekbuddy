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
import cz.kruch.track.maps.io.LoaderIO;

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

    public Map(String path, StateListener listener) {
        this(path, null, listener);
    }

    public Map(String path, String name, StateListener listener) {
        this.path = path;
        this.name = name;
        this.listener = listener;
        this.slices = new Slice[0];
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
     * Closes map - releases map images, destroy loader.
     * Does gc at the end.
     */
    public void close() {
        if (log.isEnabled()) log.info("close map @" + Integer.toHexString(hashCode()));

        // release slices images
        for (int N = slices.length, i = 0; i < N; i++) {
            slices[i].setImage(null);
        }

        // destroy loader
        if (loader != null) {
            loader.destroy();
            loader = null;
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
        for (int N = slices.length, i = 0; i < N; i++) {
            Slice slice = slices[i];
            if (slice.isWithin(x, y)) {
                return slice;
            }
        }

        return null;
    }

    /**
     * Opens and scans map.
     */
    public boolean open() {
        if (log.isEnabled()) log.debug("open map @" + Integer.toHexString(hashCode()));

        // open map in background
        LoaderIO.getInstance().enqueue(new Runnable() {
            public void run() {
                if (log.isEnabled()) log.debug("map loading task starting @" + Integer.toHexString(Map.this.hashCode()));

                // open and init map
                Throwable throwable = loadMap();

                // log
                if (log.isEnabled()) log.debug("map opened @" + Integer.toHexString(Map.this.hashCode()) + "; " + throwable);

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
        if (log.isEnabled()) log.debug("prepare slices @" + Integer.toHexString(hashCode()));

        final Vector collection = new Vector(0);

        // create list of slices whose images are to be loaded
        for (Enumeration e = slices.elements(); e.hasMoreElements(); ) {
            Slice slice = (Slice) e.nextElement();
            if (slice.getImage() == null) {
                if (log.isEnabled()) log.debug("image missing for slice " + slice);
                collection.addElement(slice);
            }
        }

        // no images to be loaded
        if (collection.size() == 0) {
            return false;
        }

        // debug
        if (log.isEnabled()) log.debug("about to load new slices");

        // load images at background
        LoaderIO.getInstance().enqueue(new Runnable() {
            public void run() {
                if (log.isEnabled()) log.debug("slice loading task started @" + Integer.toHexString(Map.this.hashCode()));

                // load images
                Throwable throwable = loadImages(collection);

                // log
                if (log.isEnabled()) log.debug("all requested slices loaded @" + Integer.toHexString(Map.this.hashCode()));

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
            loader = path.endsWith(".tar") ? (Loader) new TarLoader() : (Loader) new DirLoader();
            loader.init();
            loader.run();
            loader.checkException();
            if (log.isEnabled()) log.debug("map opened");

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
    public static Map defaultMap(StateListener listener) throws IOException {
        Map map = new Map("", listener);
        map.type = TYPE_BEST;
        InputStream in = Map.class.getResourceAsStream("/resources/world.map");
        if (in == null) {
            in = Map.class.getResourceAsStream("/resources/world.gmi");
            if (in == null) {
                throw new InvalidMapException("No default map calibration");
            } else {
                map.calibration = new Calibration.GMI(loadTextContent(in), "/resources/world.map");
            }
        } else {
            map.calibration = new Calibration.Ozi(loadTextContent(in), "/resources/world.map");
        }
        in.close();
        Slice slice = new Slice(new Calibration.Best("/resources/world_0_0.png"));
        in = Map.class.getResourceAsStream("/resources/world_0_0.png");
        slice.setImage(Image.createImage(in));
        in.close();
        map.slices = new Slice[]{ slice };
        slice.doFinal(map.calibration);
        slice.doFinal(map.calibration, map.slices);
        slice.precalculate();
        map.doFinal();

        if (log.isEnabled()) log.debug("default map slice ready " + slice);

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
                loadImage((Slice) e.nextElement());
            }
            return null;
        } catch (IOException e) {
            return e;
        } catch (OutOfMemoryError e) {
            return e;
        }
    }

    /**
     * Loads slice from map. Expects file connection opened.
     */
    private void loadImage(Slice slice) throws IOException {
        if (log.isEnabled()) log.debug("Loading slice " + slice.getURL() + "...");

        // notify
        notifyListener(EVENT_LOADING_CHANGED, "Loading " + slice.getURL() + "...", null);

        IOException exception = null;
        OutOfMemoryError error = null;
        Throwable throwable = null;

        // load slice via loader
        try {
            // use loader
            loader.loadSlice(slice);

            // got image?
            if (slice.getImage() == null) {
                throw new InvalidMapException("No image " + slice.getURL());
            }

            // log
            if (log.isEnabled()) log.debug("image loaded for slice " + slice.getURL());

        } catch (IOException e) {
            // log
            if (log.isEnabled()) log.error("image loading failed for slice " + slice.getURL(), e);

            throwable = exception = e;

        } catch (OutOfMemoryError e) {
            // log
            if (log.isEnabled()) log.error("image loading failed for slice " + slice.getURL(), e);

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

    static String loadTextContent(InputStream stream) throws IOException {
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

    /**
     * Finalizes map initialization.
     */
    public void doFinal() throws InvalidMapException {
        // absolutize slices position
        for (int N = slices.length, i = 0; i < N; i++) {
            slices[i].doFinal(calibration);
        }

        // finalize slices creation
        for (int N = slices.length, i = 0; i < N; i++) {
            Slice slice = slices[i];

            // fix slice dimension for filename-encoded positions
            slice.doFinal(calibration, slices);

            // precalculate slice range in pixel coordinates
            slice.precalculate();

            // debug
            if (log.isEnabled()) log.debug("ready slice " + slices[i]);
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

        protected Vector getCollection() {
            if (collection == null) {
                collection = new Vector();
            }

            return collection;
        }

        protected void doFinal() {
            if (collection != null) {
                slices = new Slice[collection.size()];
                collection.copyInto(slices);
            }
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
                        if (entryName.endsWith(".png") && (entryName.startsWith("set/") || entryName.startsWith("pictures/"))) { // slice
                            getCollection().addElement(new Slice(new Calibration.Best(entryName)));
                        } else {
                            if (entryName.indexOf("/") == -1) {
                                if (entryName.endsWith(".gmi")) {
                                    Map.this.calibration = new Calibration.GMI(loadTextContent(tar), entryName);
                                    Map.this.type = TYPE_BEST;
                                } else if (entryName.endsWith(".map")) {
                                    Map.this.calibration = new Calibration.Ozi(loadTextContent(tar), entryName);
                                    Map.this.type = TYPE_BEST;
                                } else if (entryName.endsWith(".xml")) {
                                    Map.this.calibration = new Calibration.XML(loadTextContent(tar), entryName);
                                    Map.this.type = TYPE_GPSKA;
                                } else if (entryName.endsWith(".j2n")) {
                                    Map.this.calibration = new Calibration.J2N(loadTextContent(tar), entryName);
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
                if (tar != null) {
                    try {
                        tar.close();
                    } catch (IOException e) {
                    }
                }
            }

            this.doFinal();
        }

        public void loadSlice(Slice slice) throws IOException {
            TarInputStream tar = null;
            try {
                tar = new TarInputStream(new BufferedInputStream(fileConnection.openInputStream(), LARGE_BUFFER_SIZE));
                TarEntry entry = tar.getNextEntry();
                while (entry != null) {
                    if (entry.getName().endsWith(slice.getURL())) {
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

        public void destroy() {
        }

        public void run() {
            FileConnection fc = null;
            InputStream in = null;

            try {
                String setDir = "set/";

                // path points to calibration file
                in = new BufferedInputStream(Connector.openInputStream(path), SMALL_BUFFER_SIZE);
                String content = loadTextContent(in);
                if (path.endsWith(".gmi")) {
                    Map.this.calibration = new Calibration.GMI(content, path);
                    Map.this.type = TYPE_BEST;
                } else if (path.endsWith(".map")) {
                    Map.this.calibration = new Calibration.Ozi(content, path);
                    Map.this.type = TYPE_BEST;
                } else if (path.endsWith(".xml")) {
                    Map.this.calibration = new Calibration.XML(content, path);
                    Map.this.type = TYPE_GPSKA;
                } else if (path.endsWith(".j2n")) {
                    Map.this.calibration = new Calibration.J2N(content, path);
                    Map.this.type = TYPE_J2N;
                    setDir = "pictures/";
                }
                in.close();
                in = null;

                // get all possible sets dirs
                fc = (FileConnection) Connector.open(dir, Connector.READ);
                Enumeration children = fc.list("*", false);

                // next, look for slices in the set
                while (children.hasMoreElements()) {
                    String child = children.nextElement().toString();
                    if (child.startsWith(setDir)) {
                        // debug
                        if (log.isEnabled()) log.debug("new set: " + child);

                        // set file connection
                        fc.setFileConnection(child);

                        // iterate over set
                        for (Enumeration e = fc.list("*.png", false); e.hasMoreElements(); ) {
                            String entry = e.nextElement().toString();
                            getCollection().addElement(new Slice(new Calibration.Best(child + entry)));
                        }

                        // go back to map root dir
                        fc.setFileConnection("..");

                        break; // only one set per map
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

            this.doFinal();
        }

        public void loadSlice(Slice slice) throws IOException {
            String slicePath = dir + slice.getURL();
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
