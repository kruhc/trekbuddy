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

import cz.kruch.track.Resources;
import cz.kruch.track.ui.Desktop;
import cz.kruch.track.maps.io.LoaderIO;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;

import api.location.QualifiedCoordinates;
import api.file.File;

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
            throw new IllegalArgumentException("Nonexistent layer");
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
            throw new IllegalArgumentException("Nonexistent layer");
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
                throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_INVALID_ATLAS_URL));
            }
            String baseUrl = url.substring(0, i + 1);
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("layers are in " + baseUrl);
//#endif

            // run loader
            Loader loader;
            if (url.toLowerCase().endsWith(TBA_EXT)) {
                loader = (Loader) Class.forName("cz.kruch.track.maps.DirLoader").newInstance();
            } else {
                loader = (Loader) Class.forName("cz.kruch.track.maps.TarLoader").newInstance();
            }
            loader.loadIndex(this, url, baseUrl);

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

    /**
     * Atlas loader.
     */
    interface Loader {
        abstract void loadIndex(Atlas atlas, String url, String baseUrl) throws IOException;
    }

    Hashtable getLayerCollection(Atlas atlas, String cName) {
        Hashtable collection = (Hashtable) atlas.layers.get(cName);
        if (collection == null) {
            collection = new Hashtable();
            atlas.layers.put(cName, collection);
        }

        return collection;
    }
}
