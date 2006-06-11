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
    private FileConnection fileConnection;
    private Slice[] slices;
    private Calibration calibration;
    private int type = -1;

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

    public boolean isBuddy() {
        return calibration instanceof Calibration.GMI;
    }

    public boolean isGpska() {
        return calibration instanceof Calibration.XML;
    }

    public boolean isJ2n() {
        return calibration instanceof Calibration.J2N;
    }

    public void close() {
        log.info("close map");

        // null images
        for (int N = slices.length, i = 0; i < N; i++) {
            slices[i].setImage(null);
        }

        // close file connection
        if (fileConnection != null) {
            try {
                fileConnection.close();
            } catch (IOException e) {
                log.error("map closing failed", e);
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
     * @throws IOException
     */
    private void open() throws IOException {

        // open the map
        fileConnection = (FileConnection) Connector.open(path, Connector.READ);
        log.debug("map connection opened; " + path);

        // load map
        Loader l = new Loader();
/*
        l.start();
        try {
            l.join();
        } catch (InterruptedException e) {
        }
*/
        l.run();
        l.checkException();
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
                // fix slices dimension
                for (int N = slices.length, i = 0; i < N; i++) {
                    Slice slice = slices[i];
                    ((Calibration.Best) slice.getCalibration()).fixDimension(calibration, slices);
                }
            } break;
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
            slice.setImage(null);
        }
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

        // find map entry and set it
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

        // close the stream
        if (tar != null) {
            try {
                tar.close();
            } catch (IOException e) {
                log.error("failed to close input stream", e);
            }
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

    /**
     * Loads calibration file (text) content from opened stream.
     * @param stream
     * @param close
     * @return
     * @throws IOException
     */
    private String loadTextContent(InputStream stream, boolean close) throws IOException {
        InputStreamReader reader = new InputStreamReader(stream);
        char[] buffer = new char[TEXT_FILE_BUFFER_SIZE];
        StringBuffer sb = new StringBuffer();
        int c = reader.read(buffer);
        while (c > -1) {
            sb.append(buffer, 0, c);
            c = reader.read(buffer);
        }

        if (close) {
            stream.close();
        }

        return sb.toString();
    }

    private class Loader extends Thread {
        private IOException exception;

        public void checkException() throws IOException {
            if (exception != null) {
                throw exception;
            }
        }

        public void run() {
            Vector collection = new Vector(0);
            try {
                TarInputStream tar = new TarInputStream(new BufferedInputStream(fileConnection.openInputStream(), SMALL_BUFFER_SIZE));
                TarEntry entry = tar.getNextEntry();
                while (entry != null) {
                    if (!entry.isDirectory()) {
                        String entryName = entry.getName();
                        if (entryName.endsWith(".gmi")) {
                            Calibration calibration = new Calibration.GMI(loadTextContent(tar, false), entryName);
/* obsolete, do not support anymore
                            if (entryName.startsWith("slices/")) {
                                collection.addElement(new Slice(calibration));
                            } else {
                                Map.this.calibration = calibration;
                                Map.this.type = TYPE_GMI;
                            }
*/
                            if (entryName.indexOf("/") == -1) { // map calibration
                                Map.this.calibration = calibration;
                                Map.this.type = TYPE_BEST;
                            } else {
                                log.warn("calibration file " + entryName + " ignored");
                            }
                        } else if (entryName.endsWith(".map")) {
                            Calibration calibration = new Calibration.XML(loadTextContent(tar, false), entryName);
                            if (entryName.indexOf("/") == -1) { // map calibration
                                Map.this.calibration = calibration;
                                Map.this.type = TYPE_BEST;
                            } else {
                                log.warn("calibration file " + entryName + " ignored");
                            }
                        } else if (entryName.endsWith(".xml")) {
                            Calibration calibration = new Calibration.XML(loadTextContent(tar, false), entryName);
                            if (entryName.startsWith("pictures/")) {
                                collection.addElement(new Slice(calibration));
                            } else {
                                Map.this.calibration = calibration;
                                Map.this.type = TYPE_GPSKA;
                            }
                        } else if (entryName.endsWith(".j2n")) {
                            Calibration calibration = new Calibration.J2N(loadTextContent(tar, false), entryName);
                            if (entryName.indexOf("/") == -1) { // map calibration
                                Map.this.calibration = calibration;
                                Map.this.type = TYPE_J2N;
                            } else {
                                log.warn("calibration file " + entryName + " ignored");
                            }
                        } else if (entryName.startsWith("set/") && entryName.endsWith(".png")) {
                            Calibration calibration = new Calibration.Best(entryName);
                            collection.addElement(new Slice(calibration));
                            Map.this.type = TYPE_BEST;
                        }
                    }
                    entry = tar.getNextEntry();
                }
                tar.close();
            } catch (IOException e) {
                exception = e;
            }

            slices = new Slice[collection.size()];
            collection.copyInto(slices);
        }
    }
}
