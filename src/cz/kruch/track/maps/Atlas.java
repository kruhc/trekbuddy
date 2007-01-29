// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps;

import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.AssertionFailedException;
import cz.kruch.track.maps.io.LoaderIO;
import cz.kruch.j2se.io.BufferedInputStream;

import javax.microedition.io.Connector;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;

import api.location.QualifiedCoordinates;

import com.ice.tar.TarEntry;
import com.ice.tar.TarInputStream;

public final class Atlas implements Runnable {

    public interface StateListener {
        public void atlasOpened(Object result, Throwable throwable);
        public void loadingChanged(Object result, Throwable throwable);
    }

//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Atlas");
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
            String mapName = (String) e.nextElement();
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
    public boolean open() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("open atlas");
//#endif

        // open atlas in background
        LoaderIO.getInstance().enqueue(this);

        return true;
    }

    /**
     * Runnable's run implementation.
     */
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

    /* (non-javadoc) public only for init loading */
    public Throwable loadAtlas() {
        try {
            // load map
            loader = url.toLowerCase().endsWith(".tar") ? (Loader) new TarLoader() : (Loader) new DirLoader();
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

        // destroy all cached maps
        for (Enumeration e = maps.elements(); e.hasMoreElements(); ) {
            Map map = (Map) e.nextElement();
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("closing map " + map.getPath());
//#endif
            map.close();
        }

        // dispose
        maps.clear();
        layers.clear();
        maps = null;
        layers = null;

        // destroy loader
        try {
            loader.destroy();
        } catch (IOException e) {
        } finally {
            loader = null;
        }
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

    private abstract class Loader {
        protected String dir;
        protected Exception exception;
        protected BufferedInputStream shared;

        protected abstract void run();

        protected Loader() {
            this.shared = new BufferedInputStream(null, Map.TEXT_FILE_BUFFER_SIZE);
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
            shared.reuse(null);
            shared = null; // gc hint
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
            // vars
            api.file.File file = null;
            TarInputStream tar = null;

            try {
                // create stream
                file = new api.file.File(Connector.open(url, Connector.READ));
                tar = new TarInputStream(shared.reuse(file.openInputStream()));

                // shared vars
                CharArrayTokenizer tokenizer = new CharArrayTokenizer();
                StringBuffer sb = new StringBuffer(128);

                // iterate over archive
                TarEntry entry = tar.getNextEntry();
                while (entry != null) {
                    if (!entry.isDirectory()) {
                        String entryName = entry.getName();
                        int indexOf = entryName.lastIndexOf('.');
                        if (indexOf > -1) {
                            // get file extension
                            String ext = entryName.substring(indexOf + 1);

                            // get layer and map name
                            String lName = null;
                            String mName = null;
                            tokenizer.init(entryName, '/', false);
                            if (tokenizer.hasMoreTokens()) {
                                lName = tokenizer.next().toString();
                                if (tokenizer.hasMoreTokens()) {
                                    mName = tokenizer.next().toString();
                                }
                            }

                            // got layer and map name?
                            if (lName != null && mName != null) {
                                sb.delete(0, sb.length());
                                String url = sb.append(dir).append(lName).append('/').append(mName).append('/').append(mName).append(".tar").toString();
                                Calibration calibration = null;

                                // load map calibration file
                                if ("map".equals(ext)) {
                                    calibration = new Calibration.Ozi(tar, url);
                                } else if ("gmi".equals(ext)) {
                                    calibration = new Calibration.GMI(tar, url);
                                } else if ("j2n".equals(ext)) {
                                    calibration = new Calibration.J2N(tar, url);
                                } else if ("xml".equals(ext)) {
                                    calibration = new Calibration.XML(tar, url);
                                }

                                // found calibration file?
                                if (calibration != null) {
        //#ifdef __LOG__
                                    if (log.isEnabled()) log.debug("calibration loaded; " + calibration);
        //#endif
                                    // save calibration for given map - only one calibration per map allowed :-)
                                    if (!getLayerCollection(lName).contains(mName)) {
                                        getLayerCollection(lName).put(mName, calibration);
                                    }
                                }
                            }
                        }
                    }
                    entry.dispose();
                    entry = null; // gc hint
                    entry = tar.getNextEntry();
                }

                // dispose vars
                tokenizer.dispose();

            } catch (Exception e) {
                exception = e;
            } finally {

                // close stream
                if (tar != null) {
                    try {
                        tar.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }

                // close file
                if (file != null) {
                    try {
                        file.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }
    }

    private final class DirLoader extends Loader {

        public void run() {
            // file
            api.file.File file = null;

            try {
                // open atlas dir
                file = new api.file.File(Connector.open(dir, Connector.READ));

                // path holder
                StringBuffer sb = new StringBuffer(128);

                // iterate over layers
                for (Enumeration le = file.list(); le.hasMoreElements(); ) {
                    String layerEntry = (String) le.nextElement();
                    if ('/' == layerEntry.charAt(layerEntry.length() - 1)) {
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("new layer: " + layerEntry);
//#endif

                        // get map collection for current layer
                        Hashtable layerCollection = getLayerCollection(layerEntry.substring(0, layerEntry.length() - 1));

                        // set file connection
                        file.setFileConnection(layerEntry);

                        // iterate over layer
                        for (Enumeration me = file.list(); me.hasMoreElements(); ) {
                            String mapEntry = (String) me.nextElement();
                            if ('/' == mapEntry.charAt(mapEntry.length() - 1)) {
//#ifdef __LOG__
                                if (log.isEnabled()) log.debug("new map? " + mapEntry);
//#endif

                                // set file connection
                                file.setFileConnection(mapEntry);

                                // iterate over map dir
                                for (Enumeration ie = file.list(); ie.hasMoreElements(); ) {
                                    String fileEntry = (String) ie.nextElement();
                                    if ('/' == fileEntry.charAt(fileEntry.length() - 1))
                                        continue;
                                    int indexOf = fileEntry.lastIndexOf('.');
                                    if (indexOf == -1) {
                                        continue;
                                    }
                                    String ext = fileEntry.substring(indexOf + 1);

                                    // calibration
                                    sb.delete(0, sb.length());
                                    String path = sb.append(file.getURL()).append(fileEntry).toString();
                                    Calibration calibration = null;
                                    Map.FileInput fileInput = new Map.FileInput(path);

                                    // load map calibration file
                                    if ("map".equals(ext)) {
                                        calibration = new Calibration.Ozi(shared.reuse(fileInput._getInputStream()),
                                                                          path);
                                    } else if ("gmi".equals(ext)) {
                                        calibration = new Calibration.GMI(shared.reuse(fileInput._getInputStream()),
                                                                          path);
                                    } else if ("j2n".equals(ext)) {
                                        calibration = new Calibration.J2N(shared.reuse(fileInput._getInputStream()),
                                                                          path);
                                    } else if ("xml".equals(ext)) {
                                        calibration = new Calibration.XML(shared.reuse(fileInput._getInputStream()),
                                                                          path);
                                    }

                                    // close file input
                                    fileInput.close();

                                    // found calibration
                                    if (calibration != null) {
//#ifdef __LOG__
                                        if (log.isEnabled()) log.debug("calibration loaded; " + calibration);
//#endif
                                        // save calibration for given map
                                        layerCollection.put(mapEntry.substring(0, mapEntry.length() - 1), calibration);

                                        /* only one calibration per map allowed :-) */
                                        break;
                                    }
                                }

                                // back to layer dir
                                file.setFileConnection("..");
                            }
                        }

                        // go back to atlas root
                        file.setFileConnection("..");
                    }
                }
            } catch (Exception e) {
                exception = e;
            } finally {
                // close file
                if (file != null) {
                    try {
                        file.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }
    }
}
