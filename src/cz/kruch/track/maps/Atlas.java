// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps;

import cz.kruch.track.util.Logger;
import cz.kruch.track.AssertionFailedException;
import cz.kruch.j2se.io.BufferedInputStream;
import cz.kruch.j2se.util.StringTokenizer;

import javax.microedition.io.file.FileConnection;
import javax.microedition.io.Connector;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Hashtable;

import api.location.QualifiedCoordinates;
import com.ice.tar.TarEntry;
import com.ice.tar.TarInputStream;

public final class Atlas {

    public interface StateListener {
        public void atlasOpened(Object result, Throwable throwable);
        public void loadingChanged(Object result, Throwable throwable);
    }

    private static final Logger log = new Logger("Map");

    private static final int EVENT_ATLAS_OPENED     = 0;
    private static final int EVENT_LOADING_CHANGED  = 1;

    // interaction with outside world
    private String url;
    private StateListener listener;

    // atlas state
    private Loader loader;
    private Hashtable layers;
    private String current;

    // I/O state
    private boolean ready = true;
    private Object lock = new Object();

    public Atlas(String url, StateListener listener) {
        this.url = url;
        this.listener = listener;
        this.layers = new Hashtable();
    }

    public String getURL() {
        return url;
    }

    public Enumeration getLayers() {
        return layers.keys();
    }

    public String getLayer() {
        return current;
    }

    public void setLayer(String layer) {
        current = layer;
    }

    public Enumeration getMapNames() {
        return ((Hashtable) layers.get(current)).keys();
    }

    public String getMapURL(String name) {
        return ((Calibration) (((Hashtable) layers.get(current)).get(name))).getPath();
    }

    public String getMapURL(String layerName, QualifiedCoordinates qc) {
        Hashtable layer = (Hashtable) layers.get(layerName);
        if (layerName == null) {
            throw new AssertionFailedException("Nonexistent layer");
        }

        for (Enumeration e = layer.elements(); e.hasMoreElements(); ) {
            Calibration calibration = (Calibration) e.nextElement();
            if (calibration.isWithin(qc)) {
                return calibration.getPath();
            }
        }

        return null;
    }

    public String getURL(String name) {
        return url + "?layer=" + current + "&map=" + name;
    }

    /**
     * Opens and scans atlas.
     */
    public boolean prepareAtlas() {
        if (log.isEnabled()) log.debug("begin open atlas");

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

                // open and init atlas
                Throwable t = loadAtlas();

                // sync
                synchronized (lock) {
                    ready = true;
                    lock.notify();
                }

                // log
                if (log.isEnabled()) log.debug("atlas opened; " + t);

                // we are done
                notifyListener(EVENT_ATLAS_OPENED, null, t);
            }
        })).start();

        if (log.isEnabled()) log.debug("~begin open atlas");

        return true;
    }

    /* public only for init loading */
    public Throwable loadAtlas() {
        try {
            // load map
            loader = url.endsWith(".tar") ? (Loader) new TarLoader() : (Loader) new DirLoader();
            loader.init();
            loader.run();
            loader.checkException();
            if (log.isEnabled()) log.debug("atlas opened");
        } catch (Throwable t) {
            return t;
        }

        return null;
    }

    /**
     * Closes atlas - destroys loader.
     * Does gc at the end.
     */
    public void close() {
        if (log.isEnabled()) log.info("close atlas");

        // destroy loader
        if (loader != null) {
            try {
                loader.destroy();
                if (log.isEnabled()) log.debug("loader destroyed");
            } catch (IOException e) {
                if (log.isEnabled()) log.warn("failed to destroy loader");
            } finally {
                loader = null;
            }
        }

        // gc
        System.gc();
    }

    // TODO is thread necessary?
    private void notifyListener(final int code, final Object result, final Throwable t) {
        (new Thread() {
            public void run() {
                switch (code) {
                    case EVENT_ATLAS_OPENED:
                        listener.atlasOpened(result, t);
                        break;
                    case EVENT_LOADING_CHANGED:
                        listener.loadingChanged(result, t);
                        break;
                }
            }
        }).start();
    }

    /*
    * Map loaders.
    */

    private abstract class Loader extends Thread {
        protected Exception exception;

        protected String dir;

        public String getDir() {
            return dir;
        }

        public void init() throws IOException {
            int i = url.lastIndexOf('/');
            if (i == -1) {
                throw new InvalidMapException("Invalid atlas URL");
            }
            dir = url.substring(0, i + 1);
            if (log.isEnabled()) log.debug("layers are in " + dir);
        }

        public void destroy() throws IOException {
        }

        public void checkException() throws Exception {
            if (exception != null) {
                throw exception;
            }
        }

        protected Hashtable getCollection(String cName) {
            Hashtable collection = (Hashtable) layers.get(cName);
            if (collection == null) {
                collection = new Hashtable();
                layers.put(cName, collection);
            }

            return collection;
        }
    }

    private class TarLoader extends Loader {

        public void run() {
            FileConnection fc = null;
            TarInputStream tar = null;

            try {
                fc = (FileConnection) Connector.open(url, Connector.READ);
                tar = new TarInputStream(new BufferedInputStream(fc.openInputStream(), Map.SMALL_BUFFER_SIZE));

                // iterate over archive
                TarEntry entry = tar.getNextEntry();
                while (entry != null) {
                    if (!entry.isDirectory()) {
                        String entryName = entry.getName();
                        int indexOf = entryName.lastIndexOf('.');
                        if (indexOf == -1)
                            continue;
                        String ext = entryName.substring(indexOf + 1);
                        if (Calibration.KNOWN_EXTENSIONS.contains(ext)) {
                            String lName = null;
                            String mName = null;
                            StringTokenizer st = new StringTokenizer(entryName, "/");
                            if (st.hasMoreTokens()) {
                                lName = st.nextToken();
                                if (st.hasMoreTokens()) {
                                    mName = st.nextToken();
                                }
                            }
                            if (lName == null || mName == null)
                                continue;

                            String url = dir + lName + "/" + mName + ".tar";
                            Calibration calibration = null;
                            if (entryName.endsWith(".gmi")) {
                                calibration = new Calibration.GMI(Map.loadTextContent(tar), url);
                            } else if (entryName.endsWith(".map")) {
                                calibration = new Calibration.Ozi(Map.loadTextContent(tar), url);
                            } else if (entryName.endsWith(".j2n")) {
                                calibration = new Calibration.J2N(Map.loadTextContent(tar), url);
                            } else if (entryName.endsWith(".xml")) {
                                calibration = new Calibration.XML(Map.loadTextContent(tar), url);
                            }
                            if (calibration != null) {
                                if (log.isEnabled()) log.debug("calibration loaded; " + calibration);
                                getCollection(lName).put(mName, calibration);
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
                if (fc != null) {
                    try {
                        fc.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }
    }

    private class DirLoader extends Loader {

        public void run() {
            FileConnection fc = null;
            InputStream in = null;

            try {
                // open atlas dir
                fc = (FileConnection) Connector.open(dir, Connector.READ);

                // iterate over layers
                for (Enumeration le = fc.list("*", false); le.hasMoreElements(); ) {
                    String lEntry = le.nextElement().toString();
                    if (lEntry.endsWith("/")) {
                        // debug
                        if (log.isEnabled()) log.debug("new layer: " + lEntry);

                        // get collection
                        Hashtable collection = getCollection(lEntry);

                        // set file connection
                        fc.setFileConnection(lEntry);

                        // iterate over layer
                        for (Enumeration me = fc.list("*", false); me.hasMoreElements(); ) {
                            String mEntry = me.nextElement().toString();
                            if (mEntry.endsWith("/")) {
                                // debug
                                if (log.isEnabled()) log.debug("new map: " + mEntry);

                                // set file connection
                                fc.setFileConnection(mEntry);

                                // iterate over map dir
                                for (Enumeration ie = fc.list("*", false); ie.hasMoreElements(); ) {
                                    String iEntry = ie.nextElement().toString();
                                    if (iEntry.endsWith("/"))
                                        continue;
                                    int indexOf = iEntry.lastIndexOf('.');
                                    if (indexOf == -1) {
                                        continue;
                                    }
                                    String ext = iEntry.substring(indexOf + 1);
                                    if (Calibration.KNOWN_EXTENSIONS.contains(ext)) {
                                        if (log.isEnabled()) log.debug("found map: " + iEntry);
                                        InputStream cin = null;
                                        Calibration calibration = null;
                                        try {
                                            String url = fc.getURL() + iEntry;
                                            cin = new BufferedInputStream(Connector.openInputStream(url), Map.SMALL_BUFFER_SIZE);
                                            if (iEntry.endsWith(".gmi")) {
                                                calibration = new Calibration.GMI(Map.loadTextContent(cin), url);
                                            } else if (iEntry.endsWith(".map")) {
                                                calibration = new Calibration.Ozi(Map.loadTextContent(cin), url);
                                            } else if (iEntry.endsWith(".j2n")) {
                                                calibration = new Calibration.J2N(Map.loadTextContent(cin), url);
                                            } else if (iEntry.endsWith(".xml")) {
                                                calibration = new Calibration.XML(Map.loadTextContent(cin), url);
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
                                            if (log.isEnabled()) log.debug("calibration loaded; " + calibration);
                                            collection.put(mEntry, calibration);
                                        }
                                    }
                                }

                                // back to layer root
                                fc.setFileConnection("..");
                            }
                        }

                        // go back to atlas root
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
        }
    }
}
