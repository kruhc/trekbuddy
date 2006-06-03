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

public class Map {
    // component name for logging
    private static final String COMPONENT_NAME = "Book";

    private static final int TEXT_FILE_BUFFER_SIZE = 512; // for map calibration files content
    private static final int SMALL_BUFFER_SIZE = 512 * 2; // for map calibration files
    private static final int LARGE_BUFFER_SIZE = 512 * 8; // for map image files

    private String path;
    private FileConnection fileConnection;
    private Vector slices;
    private Calibration calibration;

    // loading task
    private Thread task;

    public Map(String path) throws IOException {
        this.path = path;
        this.slices = new Vector();
        open();
    }

    public Calibration getCalibration() {
        return calibration;
    }

    public void close() {
        System.out.println(COMPONENT_NAME + " [info] close map");
        if (fileConnection != null) {
            try {
                fileConnection.close();
            } catch (IOException e) {
                System.err.println(COMPONENT_NAME + " [error] map closing failed: " + e.toString());
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
            throw new IllegalStateException("Top-level calibration info missing");
        }
        if (slices.size() == 0) {
            throw new IllegalStateException("Empty map - no slices");
        }

        // absolutize slices position // TODO move to Calibration
        for (Enumeration e = slices.elements(); e.hasMoreElements(); ) {
            Slice slice = (Slice) e.nextElement();
            int x0 = calibration.getPositions()[0].getX() - slice.getCalibration().getPositions()[0].getX();
            int y0 = calibration.getPositions()[0].getY() - slice.getCalibration().getPositions()[0].getY();
            slice.setAbsolutePosition(x0, y0);
        }
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
                System.out.println("image missing for slice " + slice);
                collection.addElement(slice);
            }
        }

        if (collection == null) {
            return false;
        }

        System.out.println("ABOUT TO LOAD NEW SLICES");

        // wait for previous task to finish (it should be already finished!)
        if (task != null) {
            System.out.println(COMPONENT_NAME + " [debug] waiting for previous task to finish");
            try {
                task.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // load images at background
        System.out.println(COMPONENT_NAME + " [debug] starting loading task");
        final Vector toload = collection;
        task = new Thread(new Runnable() {
            public void run() {
                // load images
                Throwable t = loadSlices(toload);
                // we are done
                (new Desktop.Event(Desktop.Event.EVENT_BOOK_LOADED, null, t)).fire();
            }
        });
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
                System.out.println("load image for slice " + slice);
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
        fireEvent("Loading slice " + slice.getCalibration().getPath() + "...",
                  null);

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
            System.out.println(COMPONENT_NAME + " [debug] image loaded for " + slice.getCalibration().getPath());

        } catch (IOException e) {
            // log
            System.out.println(COMPONENT_NAME + " [error] image loading failed for " + slice.getCalibration().getPath() + ": " + e.toString());

            exception = e;
        }

        // close the stream
        if (tar != null) {
            try {
                tar.close();
            } catch (IOException e) {
                System.out.println(COMPONENT_NAME + " [error] failed to close input stream: " + e.toString());
            }
        }

        // fire event
        fireEvent(null, exception);

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

    private void fireEvent(String message, Throwable t) {
/*
        if (event != null) {
            event.setResult(message);
            event.fire();
        }
*/
        (new Desktop.Event(Desktop.Event.EVENT_BOOK_STATUS_CHANGED, message, t)).fire();
    }
}
