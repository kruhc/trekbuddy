// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps;

//#ifdef __LOG__
import cz.kruch.track.util.Logger;
//#endif
import cz.kruch.track.AssertionFailedException;
import cz.kruch.track.maps.io.LoaderIO;
import cz.kruch.j2se.io.BufferedInputStream;
import cz.kruch.j2se.util.StringTokenizer;

import javax.microedition.io.Connector;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Hashtable;

import api.location.QualifiedCoordinates;
import api.file.File;

import com.ice.tar.TarEntry;
import com.ice.tar.TarInputStream;

public final class Atlas {

    public interface StateListener {
        public void atlasOpened(Object result, Throwable throwable);
        public void loadingChanged(Object result, Throwable throwable);
    }

//#ifdef __LOG__
    private static final Logger log = new Logger("Atlas");
//#endif

    private static final int EVENT_ATLAS_OPENED     = 0;
    private static final int EVENT_LOADING_CHANGED  = 1;

    // interaction with outside world
    private String url;
    private StateListener listener;

    // atlas state
    private Loader loader;
    private Hashtable layers;
    private String current;
    private Hashtable maps;

    public Atlas(String url, StateListener listener) {
        this.url = url;
        this.listener = listener;
        this.layers = new Hashtable();
        this.maps = new Hashtable();
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

    public Calibration getMapCalibration(String name) {
        return (Calibration) (((Hashtable) layers.get(current)).get(name));
    }

    public String getMapURL(String layerName, QualifiedCoordinates qc) {
        Hashtable layer = (Hashtable) layers.get(layerName);
        if (layer == null) {
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

    public String getMapName(String layerName, QualifiedCoordinates qc) {
        Hashtable layer = (Hashtable) layers.get(layerName);
        if (layer == null) {
            throw new AssertionFailedException("Nonexistent layer");
        }

        for (Enumeration e = layer.keys(); e.hasMoreElements(); ) {
            String mapName = e.nextElement().toString();
            Calibration calibration = (Calibration) layer.get(mapName);
            if (calibration.isWithin(qc)) {
                return mapName;
            }
        }

        return null;
    }

    public String getMapURL(QualifiedCoordinates qc) {
        Hashtable layer = (Hashtable) layers.get(current);
        if (layer == null) {
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

    public Hashtable getMaps() {
        return maps;
    }

    /**
     * Opens and scans atlas.
     */
    public boolean prepareAtlas() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("prepare atlas");
//#endif

        // open atlas in background
        LoaderIO.getInstance().enqueue(new Runnable() {
            public void run() {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("atlas loading task starting");
//#endif

                // open and init atlas
                Throwable t = loadAtlas();

//#ifdef __LOG__
                if (log.isEnabled()) log.debug("atlas opened; " + t);
//#endif

                // we are done
                notifyListener(EVENT_ATLAS_OPENED, null, t);
            }
        });

        return true;
    }

    /* (non-javadoc) public only for init loading */
    public Throwable loadAtlas() {
        try {
            // load map
            loader = url.endsWith(".tar") || url.endsWith(".TAR") ? (Loader) new TarLoader() : (Loader) new DirLoader();
            loader.init();
            loader.run();
            loader.checkException();
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("atlas opened");
//#endif
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
//#ifdef __LOG__
        if (log.isEnabled()) log.info("close atlas");
//#endif

        // destroy loader
        if (loader != null) {
            try {
                loader.destroy();
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("loader destroyed");
//#endif
            } catch (IOException e) {
//#ifdef __LOG__
                if (log.isEnabled()) log.warn("failed to destroy loader");
//#endif
            } finally {
                loader = null;
            }
        }

        // gc
        System.gc();
    }

    private void notifyListener(final int code, final Object result, final Throwable t) {
        switch (code) {
            case EVENT_ATLAS_OPENED:
                listener.atlasOpened(result, t);
                break;
            case EVENT_LOADING_CHANGED:
                listener.loadingChanged(result, t);
                break;
        }
    }

    /*
    * Atlas loaders.
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

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("layers are in " + dir);
//#endif
        }

        public void destroy() throws IOException {
        }

        public void checkException() throws Exception {
            if (exception != null) {
                throw exception;
            }
        }

        protected Hashtable getLayerCollection(String cName) {
            Hashtable collection = (Hashtable) layers.get(cName);
            if (collection == null) {
                collection = new Hashtable();
                layers.put(cName, collection);
            }

            return collection;
        }
    }

    private final class TarLoader extends Loader {

        public void run() {
            File fc = null;
            TarInputStream tar = null;

            try {
                fc = new File(Connector.open(url, Connector.READ));
                tar = new TarInputStream(new BufferedInputStream(fc.openInputStream(), Map.SMALL_BUFFER_SIZE));

                // iterate over archive
                TarEntry entry = tar.getNextEntry();
                while (entry != null) {
                    if (!entry.isDirectory()) {
                        String entryName = entry.getName();
                        int indexOf = entryName.lastIndexOf('.');
                        if (indexOf == -1) // not a file with extension
                            continue;

                        String ext = entryName.substring(indexOf + 1);
                        if (Calibration.KNOWN_EXTENSIONS.contains(ext)) {
                            // get layer and map name
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

                            String url = dir + lName + "/" + mName + "/" + mName + ".tar";
                            Calibration calibration = null;

                            // load map calibration file
                            if ("gmi".equals(ext)) {
                                calibration = new Calibration.GMI(tar, url);
                            } else if ("map".equals(ext)) {
                                calibration = new Calibration.Ozi(tar, url);
                            } else if ("j2n".equals(ext)) {
                                calibration = new Calibration.J2N(tar, url);
                            } else if ("xml".equals(ext)) {
                                calibration = new Calibration.XML(tar, url);
                            }
                            if (calibration != null) {
//#ifdef __LOG__
                                if (log.isEnabled()) log.debug("calibration loaded; " + calibration);
//#endif
                                getLayerCollection(lName).put(mName, calibration);
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
                    }
                }
            }
        }
    }

    private final class DirLoader extends Loader {

        public void run() {
            File fc = null;
            InputStream in = null;

            try {
                // open atlas dir
                fc = new File(Connector.open(dir, Connector.READ));

                // iterate over layers
                for (Enumeration le = fc.list(); le.hasMoreElements(); ) {
                    String lEntry = le.nextElement().toString();
                    if (lEntry.endsWith("/")) {
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("new layer: " + lEntry);
//#endif

                        // get collection
                        Hashtable collection = getLayerCollection(lEntry);

                        // set file connection
                        fc.setFileConnection(lEntry);

                        // iterate over layer
                        for (Enumeration me = fc.list(); me.hasMoreElements(); ) {
                            String mEntry = me.nextElement().toString();
                            if (mEntry.endsWith("/")) {
//#ifdef __LOG__
                                if (log.isEnabled()) log.debug("new map? " + mEntry);
//#endif

                                // set file connection
                                fc.setFileConnection(mEntry);

                                // iterate over map dir
                                for (Enumeration ie = fc.list(); ie.hasMoreElements(); ) {
                                    String iEntry = ie.nextElement().toString();
                                    if (iEntry.endsWith("/"))
                                        continue;

                                    int indexOf = iEntry.lastIndexOf('.');
                                    if (indexOf == -1) {
                                        continue;
                                    }
                                    String ext = iEntry.substring(indexOf + 1);

                                    if (Calibration.KNOWN_EXTENSIONS.contains(ext)) {
//#ifdef __LOG__
                                        if (log.isEnabled()) log.debug("found map calibration: " + iEntry);
//#endif

                                        Calibration calibration = null;
                                        String url = fc.getURL() + iEntry;
                                        Map.FileInput fileInput = new Map.FileInput(url);

                                        // load map calibration file
                                        if ("gmi".equals(ext)) {
                                            calibration = new Calibration.GMI(fileInput.getInputStream(), url);
                                        } else if ("map".equals(ext)) {
                                            calibration = new Calibration.Ozi(fileInput.getInputStream(), url);
                                        } else if ("j2n".equals(ext)) {
                                            calibration = new Calibration.J2N(fileInput.getInputStream(), url);
                                        } else if ("xml".equals(ext)) {
                                            calibration = new Calibration.XML(fileInput.getInputStream(), url);
                                        }
                                        // close file input
                                        fileInput.close();
                                        // we do not need but one calibration
                                        if (calibration != null) {
//#ifdef __LOG__
                                            if (log.isEnabled()) log.debug("calibration loaded; " + calibration);
//#endif
                                            collection.put(mEntry, calibration);

                                            /*
                                             * one calibration is enough - first one wins
                                             */

                                            break;
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
