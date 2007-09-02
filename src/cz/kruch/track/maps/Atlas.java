/*
 * Copyright 2006-2007 Ales Pour <kruhc@seznam.cz>.
 * All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package cz.kruch.track.maps;

import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.AssertionFailedException;
import cz.kruch.track.ui.Desktop;
import cz.kruch.track.maps.io.LoaderIO;
import cz.kruch.track.maps.io.FileInput;

import javax.microedition.io.Connector;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Hashtable;

import api.location.QualifiedCoordinates;
import api.file.File;

import com.ice.tar.TarEntry;
import com.ice.tar.TarInputStream;

/**
 * Atlas representation and handling.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class Atlas implements Runnable {

/*
    public interface StateListener {
        public void atlasOpened(Object result, Throwable throwable);
        public void loadingChanged(Object result, Throwable throwable);
    }
*/

//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Atlas");
//#endif

/*
    private static final int EVENT_ATLAS_OPENED     = 0;
    private static final int EVENT_LOADING_CHANGED  = 1;
*/
    private static final String TBA_EXT = ".tba";

    // interaction with outside world
    private String url;
    private /*StateListener*/ Desktop listener;

    // atlas state
    private Hashtable layers;
    private String current;
    private Hashtable maps;

    // loaders factory
    private Class dlFactory, tlFactory;

    public Atlas(String url, /*StateListener*/Desktop listener) {
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

    // TODO qc are current map local!!!
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
        listener.atlasOpened(null, t);
    }

    /* (non-javadoc) public only for init loading */
    public Throwable loadAtlas() {
        try {
            // get basedir
            final int i = url.lastIndexOf(File.PATH_SEPCHAR);
            if (i == -1) {
                throw new InvalidMapException("Invalid atlas URL");
            }
            String dir = url.substring(0, i + 1);

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("layers are in " + dir);
//#endif

            // run loader
            if (url.toLowerCase().endsWith(TBA_EXT)) {
                loadDir(dir);
            } else {
                loadTar(dir);
            }
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

        // gc hints
        maps = null;
        layers = null;
    }

    private void loadTar(String dir) throws IOException {
        // vars
        api.file.File file = null;
        InputStream in = null;
        TarInputStream tar = null;
        final boolean isIdx = url.endsWith(".idx");

        try {
            // create stream
            file = File.open(Connector.open(url, Connector.READ));
            tar = new TarInputStream(in = file.openInputStream());

            // shared vars
            char[] delims = new char[]{File.PATH_SEPCHAR};
            CharArrayTokenizer tokenizer = new CharArrayTokenizer();
            StringBuffer sb = new StringBuffer(128);

            // iterate over archive
            TarEntry entry = tar.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    String entryName = entry.getName();
                    int indexOf = entryName.lastIndexOf('.');
                    if (indexOf > -1) {
                        // get layer and map name
                        String lName = null;
                        String mName = null;
                        tokenizer.init(entryName, delims, false);
                        if (tokenizer.hasMoreTokens()) {
                            lName = tokenizer.next().toString();
                            if (tokenizer.hasMoreTokens()) {
                                mName = tokenizer.next().toString();
                            }
                        }

                        // got layer and map name?
                        if (lName != null && mName != null) {
                            Calibration calibration = null;
                            String ext = entryName.substring(indexOf);
                            sb.delete(0, sb.length());
                            String url = isIdx ? sb.append(dir).append(entryName).toString() : sb.append(dir).append(lName).append(File.PATH_SEPCHAR).append(mName).append(File.PATH_SEPCHAR).append(mName).append(Map.TAR_EXT).toString();

                            // load map calibration file
                            if (Calibration.OZI_EXT.equals(ext)) {
                                calibration = new Calibration.Ozi(tar, url);
                            } else if (Calibration.GMI_EXT.equals(ext)) {
                                calibration = new Calibration.GMI(tar, url);
                            } else if (Calibration.J2N_EXT.equals(ext)) {
                                calibration = new Calibration.J2N(tar, url);
                            } else if (Calibration.XML_EXT.equals(ext)) {
                                calibration = new Calibration.XML(tar, url);
                            }

                            // found calibration file?
                            if (calibration != null) {
//#ifdef __LOG__
                                if (log.isEnabled())
                                    log.debug("calibration loaded: " + calibration + "; layer = " + lName + "; mName = " + mName);
//#endif
                                // save calibration for given map - only one calibration per map allowed :-)
                                if (!getLayerCollection(lName).contains(mName)) {
                                    getLayerCollection(lName).put(mName, calibration);
                                }
                            }
                        }
                    }
                }
                entry = null; // gc hint
                entry = tar.getNextEntry();
            }

            // dispose vars
            tokenizer.dispose();

        } finally {
            // dispose tar stream
            if (tar != null) {
                tar.reuse(null);
                tar.dispose();
                tar = null;
            }

            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
                in = null;
            }

            // close file
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    // ignore
                }
                file = null;
            }
        }
    }

    private void loadDir(String dir) throws IOException {
        // file
        api.file.File file = null;

        try {
            // open atlas dir
            file = File.open(Connector.open(dir, Connector.READ));

            // path holder
            StringBuffer sb = new StringBuffer(128);

            // iterate over layers
            for (Enumeration le = file.list(); le.hasMoreElements();) {
                String layerEntry = (String) le.nextElement();
                if (File.isDir(layerEntry)) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("new layer: " + layerEntry);
//#endif

                    // get map collection for current layer
                    Hashtable layerCollection = getLayerCollection(layerEntry.substring(0, layerEntry.length() - 1));

                    // set file connection
                    file.setFileConnection(layerEntry);

                    // iterate over layer
                    for (Enumeration me = file.list(); me.hasMoreElements();) {
                        String mapEntry = (String) me.nextElement();
                        if (File.isDir(mapEntry)) {
//#ifdef __LOG__
                            if (log.isEnabled()) log.debug("new map? " + mapEntry);
//#endif

                            // set file connection
                            file.setFileConnection(mapEntry);

                            // iterate over map dir
                            for (Enumeration ie = file.list(); ie.hasMoreElements();) {
                                String fileEntry = (String) ie.nextElement();

                                // is dir
                                if (File.isDir(fileEntry))
                                    continue;

                                // has ext
                                int indexOf = fileEntry.lastIndexOf('.');
                                if (indexOf == -1) {
                                    continue;
                                }

                                // get ext
                                String ext = fileEntry.substring(indexOf).toLowerCase();

                                // calibration
                                sb.delete(0, sb.length());
                                String path = sb.append(file.getURL()).append(fileEntry).toString();
                                Calibration calibration = null;
                                FileInput fileInput = new FileInput(path);

                                // load map calibration file
                                if (Calibration.OZI_EXT.equals(ext)) {
                                    calibration = new Calibration.Ozi(fileInput._getInputStream(), path);
                                } else if (Calibration.GMI_EXT.equals(ext)) {
                                    calibration = new Calibration.GMI(fileInput._getInputStream(), path);
                                } else if (Calibration.J2N_EXT.equals(ext)) {
                                    calibration = new Calibration.J2N(fileInput._getInputStream(), path);
                                } else if (Calibration.XML_EXT.equals(ext)) {
                                    calibration = new Calibration.XML(fileInput._getInputStream(), path);
                                }

                                // close file input
                                fileInput.close();

                                // found calibration
                                if (calibration != null) {
//#ifdef __LOG__
                                    if (log.isEnabled())
                                        log.debug("calibration loaded: " + calibration + "; layer = " + layerEntry + "; mName = " + mapEntry);
//#endif
                                    // save calibration for given map
                                    layerCollection.put(mapEntry.substring(0, mapEntry.length() - 1), calibration);

                                    /* only one calibration per map allowed :-) */
                                    break;
                                }
                            }

                            // back to layer dir
                            file.setFileConnection(File.PARENT_DIR);
                        }
                    }

                    // go back to atlas root
                    file.setFileConnection(File.PARENT_DIR);
                }
            }
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

    private Hashtable getLayerCollection(String cName) {
        Hashtable collection = (Hashtable) layers.get(cName);
        if (collection == null) {
            collection = new Hashtable();
            layers.put(cName, collection);
        }

        return collection;
    }
}
