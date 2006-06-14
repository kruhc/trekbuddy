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

    public Map(String path) throws IOException {
        this.path = path;
        this.slices = new Slice[0];
        open();
        compute();
    }

    public Calibration getCalibration() {
        return calibration;
    }

    public void close() {
        log.info("close map");

        // null images
        for (int N = slices.length, i = 0; i < N; i++) {
            log.debug("null image for slice " + slices[i]);
            slices[i].setImage(null);
        }

        // destroy loader
        try {
            loader.destroy();
            log.debug("loader destroyed");
            loader = null;
        } catch (IOException e) {
            log.warn("failed to destroy loader");
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
     * @throws IOException
     */
    private void open() throws IOException {

        // load map
        loader = path.endsWith(".tar") ? (Loader) new TarLoader() : (Loader) new DirLoader();
        loader.init();
/*
        l.start();
        try {
            l.join();
        } catch (InterruptedException e) {
        }
*/
        loader.run();
        loader.checkException();
        log.debug("map opened");

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
                log.debug("fix slices dimension");
                // fix slices dimension
                for (int N = slices.length, i = 0; i < N; i++) {
                    Slice slice = slices[i];
                    ((Calibration.Best) slice.getCalibration()).fixDimension(calibration, slices);
                }
            } break;
            default:
                log.debug("no map specific ops for map of type " + type);
        }

        // debug
        for (int N = slices.length, i = 0; i < N; i++) {
            log.debug("ready slice " + slices[i]);
        }
        // ~debug

        log.debug("map ready");
    }

    /**
     * Initial setup.
     */
    private void compute() {
        range = new QualifiedCoordinates[2];
        range[0] = calibration.transform(new Position(0, 0));
        range[1] = calibration.transform(new Position(calibration.getWidth() - 1,
                                                      calibration.getHeight() - 1));

        // log
        log.info("map range is "  + range[0] + "(" + range[0].getLat() + "," + range[0].getLon() + ") - " + range[1] + "(" + range[1].getLat() + "," + range[1].getLon() + ")");
    }

    /**
     * Ensures slices have their images loaded.
     * @param slices
     */
    public boolean prepareSlices(Vector slices) {
        Vector collection = null;

        for (Enumeration e = slices.elements(); e.hasMoreElements(); ) {
            Slice slice = (Slice) e.nextElement();
            if (slice.getImage() == null) {
                if (collection == null) {
                    collection = new Vector();
                }

                log.debug("image missing for slice " + slice);
                collection.addElement(slice);
            }
        }

        if (collection == null) {
            return false;
        }

        log.debug("about to load new slices");

        // wait for previous task to finish (it should be already finished!)
        if (task != null) {
            log.debug("waiting for previous task to finish");
            try {
                task.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // load images at background
        log.debug("starting loading task");
        final Vector toload = collection;
        task = new Thread(new Runnable() {
            public void run() {
                // load images
                Throwable t = loadSlices(toload);

                // log
                log.debug("all requested slices loaded");

                // we are done
                (new Desktop.Event(Desktop.Event.EVENT_SLICES_LOADED, null, t)).fire();
            }
        });
        task.setPriority(Thread.MAX_PRIORITY);
        task.start();

        return true;
    }

    /**
     * Loads images for given slices.
     * @param toLoad
     */
    private Throwable loadSlices(Vector toLoad) {
        try {
            for (Enumeration e = toLoad.elements(); e.hasMoreElements(); ) {
                Slice slice = (Slice) e.nextElement();
                log.debug("load image for slice " + slice);
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
     * Releases slices..
     * @param slices
     */
    public void releaseSlices(Vector slices) {
        //  clears their image references
        for (Enumeration e = slices.elements(); e.hasMoreElements(); ) {
            Slice slice = (Slice) e.nextElement();
            log.debug("release image for slice " + slice);
            slice.setImage(null);
        }

        // gc
        System.gc();
    }

    /**
     * Loads slice from map. Expects file connection opened.
     */
    private void loadSlice(Slice slice) throws IOException {
        log.debug("Loading slice " + slice.getCalibration().getPath() + "...");

        // fire event
        if (!TrackingMIDlet.isEmulator()) {
            (new Desktop.Event(Desktop.Event.EVENT_LOADING_STATUS_CHANGED, "Loading slice " + slice.getCalibration().getPath() + "...", null)).fire();
        }

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
            log.debug("image loaded for slice " + slice.getCalibration().getPath());

        } catch (IOException e) {
            // log
            log.error("image loading failed for slice " + slice.getCalibration().getPath(), e);

            throwable = exception = e;

        } catch (OutOfMemoryError e) {
            // log
            log.error("image loading failed for slice " + slice.getCalibration().getPath(), e);

            throwable = error = e;
        }

        // fire event
        if (!TrackingMIDlet.isEmulator()) {
            (new Desktop.Event(Desktop.Event.EVENT_LOADING_STATUS_CHANGED, null, throwable)).fire();
        }

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
            log.debug("map connection opened; " + path);
        }

        public void destroy() {
            // close file connection
            if (fileConnection != null) {
                try {
                    fileConnection.close();
                } catch (IOException e) {
                    log.error("map closing failed", e);
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
                                log.warn("calibration file " + entryName + " ignored");
                            }
                        } else if (entryName.endsWith(".map")) {
                            Calibration calibration = new Calibration.Ozi(loadTextContent(tar), entryName);
                            if (entryName.indexOf("/") == -1) { // map calibration
                                Map.this.calibration = calibration;
                                Map.this.type = TYPE_BEST;
                            } else {
                                log.warn("calibration file " + entryName + " ignored");
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
                                log.warn("calibration file " + entryName + " ignored");
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
                        log.error("failed to close input stream", e);
                    }
                }
            }

        }
    }

    private class DirLoader extends Loader {
        private String dir;

        public void init() throws IOException {
            dir = path.substring(0, path.lastIndexOf('/'));
            log.debug("slices are in " + dir);
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
            log.debug("load slice image from " + spath);

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
}
