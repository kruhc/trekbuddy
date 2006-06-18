// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps;

import com.ice.tar.TarInputStream;
import com.ice.tar.TarEntry;

import javax.microedition.io.file.FileConnection;
import javax.microedition.io.Connector;
import javax.microedition.lcdui.Image;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Vector;
import java.util.Enumeration;

import cz.kruch.j2se.io.BufferedInputStream;
import cz.kruch.track.ui.Desktop;
import cz.kruch.track.ui.Position;
import cz.kruch.track.TrackingMIDlet;
import cz.kruch.track.util.Logger;
import api.location.QualifiedCoordinates;

public class Map {
    private static final Logger log = new Logger("Map");

    private static final int TEXT_FILE_BUFFER_SIZE = 512; // for map calibration files content
    private static final int SMALL_BUFFER_SIZE = 512 * 2; // for map calibration files
    private static final int LARGE_BUFFER_SIZE = 512 * 8; // for map image files

    private static final int TYPE_GMI        = 0;
    private static final int TYPE_GPSKA      = 1;
    private static final int TYPE_J2N        = 2;
    private static final int TYPE_BEST       = 3;

    private String path;
    private Slice[] slices;
    private Calibration calibration;
    private int type = -1;
    private Loader loader;

    // map range
    protected QualifiedCoordinates[] range;

    // loading task
    private Thread task;

    public Map(String path) {
        this.path = path;
        this.slices = new Slice[0];
    }

    public String getPath() {
        return path;
    }

    public Calibration getCalibration() {
        return calibration;
    }

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
        return (coordinates.getLat() <= range[0].getLat() && coordinates.getLat() >= range[1].getLat())
                && (coordinates.getLon() >= range[0].getLon() && coordinates.getLon() <= range[1].getLon());
    }

    /**
     * Opens and scans map.
     */
    public boolean prepare() {
        if (log.isEnabled()) log.debug("about to open and prepare map");

        // wait for previous task to finish (it should be already finished!)
        if (task != null) {
            if (log.isEnabled()) log.debug("waiting for previous task to finish");
            try {
                task.join();
            } catch (InterruptedException e) {
            }
            task = null;
        }

        // load images at background
        if (log.isEnabled()) log.debug("starting init task");
        task = new Thread(new Runnable() {
            public void run() {
                // open and init map
                Throwable t = loadMap();

                // log
                if (log.isEnabled()) log.debug("map opened and initialized");

                // we are done
                (new Desktop.Event(Desktop.Event.EVENT_MAP_OPENED, null, t)).fire();
            }
        });
        task.setPriority(Thread.MAX_PRIORITY);
        task.start();

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

        // wait for previous task to finish (it should be already finished!)
        if (task != null) {
            if (log.isEnabled()) log.debug("waiting for previous task to finish");
            try {
                task.join();
            } catch (InterruptedException e) {
            }
            task = null;
        }

        // load images at background
        if (log.isEnabled()) log.debug("starting loading task");
        task = new Thread(new Runnable() {
            public void run() {
                // load images
                Throwable t = loadSlices(toload);

                // log
                if (log.isEnabled()) log.debug("all requested slices loaded");

                // we are done
                (new Desktop.Event(Desktop.Event.EVENT_SLICES_LOADED, null, t)).fire();
            }
        });
        task.setPriority(Thread.MAX_PRIORITY);
        task.start();

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

            // compute map range
            range = new QualifiedCoordinates[2];
            range[0] = calibration.transform(new Position(0, 0));
            range[1] = calibration.transform(new Position(calibration.getWidth() - 1,
                                                          calibration.getHeight() - 1));

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
     * Loads images for given slices.
     * @param toLoad
     */
    private Throwable loadSlices(Vector toLoad) {
        try {
            for (Enumeration e = toLoad.elements(); e.hasMoreElements(); ) {
                Slice slice = (Slice) e.nextElement();
                if (log.isEnabled()) log.debug("load image for slice " + slice);
                loadSlice(slice);
            }

            return null;

        } catch (IOException e) {
            return e;
        } catch (OutOfMemoryError e) {
            return e;
        }
    }

    /**
     * Releases slices images.
     * @param slices
     */
    public void releaseSlices(Vector slices) {
        //  clear slices image references
        for (Enumeration e = slices.elements(); e.hasMoreElements(); ) {
            Slice slice = (Slice) e.nextElement();
            if (log.isEnabled()) log.debug("release image for slice " + slice);
            slice.setImage(null);
        }

        // gc
        System.gc();
    }

    /**
     * Loads slice from map. Expects file connection opened.
     */
    private void loadSlice(Slice slice) throws IOException {
        if (log.isEnabled()) log.debug("Loading slice " + slice.getCalibration().getPath() + "...");

        // fire event
//        if (!TrackingMIDlet.isEmulator()) {
            (new Desktop.Event(Desktop.Event.EVENT_LOADING_STATUS_CHANGED, "Loading slice " + slice.getCalibration().getPath() + "...", null)).fire();
//        }

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

        // fire event
//        if (!TrackingMIDlet.isEmulator()) {
            (new Desktop.Event(Desktop.Event.EVENT_LOADING_STATUS_CHANGED, null, throwable)).fire();
//        }

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
            dir = path.substring(0, path.lastIndexOf('/'));
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

            FileConnection fc = null;
            InputStream in = null;

            try {
                fc = (FileConnection) Connector.open(spath, Connector.READ);
                in = fc.openInputStream();
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

    private static final String defaultMapCalibration =
        "Point01,xy,0,0,in,deg,90,0,N,180,0,W,grid,,,,N\n" +
        "Point02,xy,639,0,in,deg,90,0,N,180,0,E,grid,,,,N\n" +
        "Point03,xy,0,319,in,deg,90,0,S,180,0,W, grid,,,,N\n" +
        "Point04,xy,639,319,in,deg,90,0,S,180,0,E, grid,,,,N\n" +
        "Point05,xy,320,160,in,deg,0,0,N,0,0,E, grid,,,,N\n" +
        "IWH,Map Image Width/Height,640,320";

    public static Map defaultMap() throws IOException {
        Map map = new Map("");
        map.calibration = new Calibration.Ozi(defaultMapCalibration, "resource:///resources/world_0_0.png");
        map.range = new QualifiedCoordinates[2];
        map.range[0] = map.calibration.transform(new Position(0, 0));
        map.range[1] = map.calibration.transform(new Position(map.calibration.getWidth() - 1,
                                                              map.calibration.getHeight() - 1));
        Slice slice = new Slice(map.calibration);
        slice.absolutizePosition(map.calibration);
        slice.setImage(Image.createImage("/resources/world_0_0.png"));
        map.slices = new Slice[] { slice };

        if (log.isEnabled()) log.debug("default map slice ready " + slice);

        return map;
    }
}
