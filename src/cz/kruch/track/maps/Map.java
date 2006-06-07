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

    private String path;
    private FileConnection fileConnection;
    private Vector slices;
    private Calibration calibration;

    // map range
    protected QualifiedCoordinates[] range;

    // loading task
    private Thread task;

    public Map(String path) throws IOException {
        this.path = path;
        this.slices = new Vector();
        open();
        compute();
    }

    public Calibration getCalibration() {
        return calibration;
    }

    public void close() {
        log.info("close map");

        if (fileConnection != null) {
            try {
                fileConnection.close();
            } catch (IOException e) {
                log.error("map closing failed", e);
            }
        }
    }

    public Slice getSlice(int x, int y) {
        Slice result = null;
        for (Enumeration e = slices.elements(); e.hasMoreElements(); ) {
            Slice slice = (Slice) e.nextElement();
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

        // look for calibration files in the map
        TarInputStream tar = new TarInputStream(new BufferedInputStream(fileConnection.openInputStream(), SMALL_BUFFER_SIZE));
        TarEntry entry = tar.getNextEntry();
        while (entry != null) {
            if (!entry.isDirectory()) {
                if (entry.getName().endsWith(".gmi")) {
                    Calibration calibration = Calibration.GMI.create(loadTextContent(tar, false));
                    if (entry.getName().startsWith("slices/")) {
                        Slice slice = new Slice(calibration);
                        slices.addElement(slice);
                    } else {
                        this.calibration = calibration;
                    }
                }
            }
            entry = tar.getNextEntry();
        }
        tar.close();

        /*
        * keep the connection open to avoid access confirmation
        */

        // check map for consistency
        if (calibration == null) {
            throw new IllegalStateException("Map calibration info missing");
        }
        if (slices.size() == 0) {
            throw new IllegalStateException("Empty map - no slices");
        }

        // absolutize slices position
        for (Enumeration e = slices.elements(); e.hasMoreElements(); ) {
            Slice slice = (Slice) e.nextElement();
            slice.absolutizePosition(calibration);
        }
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
        // fire event
        if (!TrackingMIDlet.isEmulator()) {
            (new Desktop.Event(Desktop.Event.EVENT_LOADING_STATUS_CHANGED, "Loading slice " + slice.getCalibration().getPath() + "...", null)).fire();
        }

        IOException exception = null;

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

            // log
            log.debug("image loaded for slice " + slice.getCalibration().getPath());

        } catch (IOException e) {
            // log
            log.error("image loading failed for slice " + slice.getCalibration().getPath(), e);

            exception = e;
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
            (new Desktop.Event(Desktop.Event.EVENT_LOADING_STATUS_CHANGED, null, exception)).fire();
        }

        // upon no exception just return
        if (exception == null) {
            return;
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
}
