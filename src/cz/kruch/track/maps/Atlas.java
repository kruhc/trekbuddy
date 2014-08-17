// @LICENSE@

package cz.kruch.track.maps;

import cz.kruch.track.Resources;
import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.ui.Desktop;

import java.util.Enumeration;
import java.util.Hashtable;

import api.file.File;
import api.location.QualifiedCoordinates;
import api.lang.Int;

/**
 * Atlas representation and handling.
 *
 * @author kruhc@seznam.cz
 */
public final class Atlas implements Runnable {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Atlas");
//#endif

    // interaction with outside world
    private String url;
    private String name;
    private /*StateListener*/ Desktop listener;

    // atlas state
    private Hashtable layers;
    private String current;
    private Hashtable maps;

    // special properties
    boolean virtual;

    public Atlas(String url, String name, /*StateListener*/Desktop listener) {
        this.url = url;
        this.name = name;
        this.listener = listener;
        this.layers = new Hashtable();
        this.maps = new Hashtable();
    }

    public String getURL() {
        return url;
    }

    public String getName() {
        return name;
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

    public Hashtable getMaps() {
        return maps;
    }
    
    public Enumeration getMapNames() {
        return getMapNames(current);
    }

    public Enumeration getMapNames(final String layer) {
        return ((Hashtable) layers.get(layer)).keys();
    }

    public Calibration getMapCalibration(final String mapName) {
        return (Calibration) (((Hashtable) layers.get(current)).get(mapName));
    }

    public String getMapURL(final String mapPath, final String mapName) {
        final StringBuffer sb = new StringBuffer(64);
        sb.append(url);
        if (mapPath.indexOf("%20") > -1) { // platform is encoding spaces
            sb.append("?layer=").append(File.encode(current)).append("&map=").append(File.encode(mapName));
        } else { // not encoding or nor spaces at all
            sb.append("?layer=").append(current).append("&map=").append(mapName);
        }
        return sb.toString();
    }

    public String getFileURL(final String mapName) {
        return ((Calibration) (((Hashtable) layers.get(current)).get(mapName))).getPath();
    }

    // TODO qc are NOT map local!!!
    public String getFileURL(final String layerName, final QualifiedCoordinates qc) {
        final Hashtable layer = (Hashtable) layers.get(layerName);
        if (layer == null) {
            throw new IllegalArgumentException("Nonexistent layer");
        }

        for (final Enumeration e = layer.elements(); e.hasMoreElements(); ) {
            final Calibration calibration = (Calibration) e.nextElement();
            if (calibration.isWithin(qc)) {
                return calibration.getPath();
            }
        }

        return null;
    }

    public String getMapName(final String layerName, final QualifiedCoordinates qc) {
        final Hashtable layer = (Hashtable) layers.get(layerName);
        if (layer == null) {
            throw new IllegalArgumentException("Nonexistent layer");
        }

        for (final Enumeration e = layer.keys(); e.hasMoreElements(); ) {
            final String mapName = (String) e.nextElement();
            final Calibration calibration = (Calibration) layer.get(mapName);
            if (calibration.isWithin(qc)) {
                return mapName;
            }
        }

        return null;
    }

    public String getNextLayer(final Map currentMap, final int direction,
                               final QualifiedCoordinates coords, final Int eventType) {
        
        // try current layer first
        String mapName = getNextLayer(current, currentMap, direction, coords);
        String layerName = null;

        // try other layers - first one wins
        if (mapName == null) {
            final Enumeration e = layers.keys();
            while (e.hasMoreElements()) {
                final String name = (String) e.nextElement();
                if (name.equals(current)) {
                    continue;
                }
                mapName = getNextLayer(name, currentMap, direction, coords);
                if (mapName != null) {
                    layerName = name;
                    eventType.setValue(4); // 4 = EVENT_LAYER_SELECTION_FINISHED
                    break;
                }
            }
        } else {
            layerName = mapName;
            eventType.setValue(5); // 5 = EVENT_MAP_SELECTION_FINISHED
        }

        return layerName;
    }

    public String getNextLayer(final String layerName, final Map currentMap,
                               final int direction, final QualifiedCoordinates coords) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("getNextLayer; " + layerName);
//#endif

        // current map scale
        final double currentScale = currentMap.getVerticalScale();
        double nextScale = direction == 1 ? 0D : Double.MAX_VALUE;
        String nextMap = null;

        // get current layer collection
        final Hashtable layer = (Hashtable) layers.get(layerName);
        final Enumeration e = layer.keys();
        while (e.hasMoreElements()) {
            final Object map = e.nextElement();
            final Calibration calibration = (Calibration) layer.get(map);
            final double scale = calibration.getVerticalScale();
//#ifdef __LOG__
            if (log.isEnabled()) {
                log.debug("\tmap " + map + "; scale " + scale);
                log.debug("\t\tratio: " + (scale / currentScale));
            }
//#endif
            if (direction == 1) {
                if (scale / currentScale < 0.75D) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("\t\t" + (scale / currentScale) + " < 0.75");
//#endif
                    if (scale > nextScale) {
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("\t\tnextScale = " + nextScale + " - use");
//#endif
                        nextScale = scale;
                        nextMap = (String) map;
                    }
                }
            } else if (direction == -1) {
                if (scale / currentScale > 1.25D) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("\t\t" + (scale / currentScale) + " > 1.25");
//#endif
                    if (scale < nextScale) {
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("\t\tnextScale = " + nextScale + " - use");
//#endif
                        nextScale = scale;
                        nextMap = (String) map;
                    }
                }
            }
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("nextMap: "  + nextMap);
//#endif

        return nextMap;
    }

    public static String atlasURLtoFileURL(final String url) {
        final String[] parts = parseURL(url);
        final StringBuffer sb = new StringBuffer(64);
        sb.append(parts[0].substring(0, parts[0].lastIndexOf(File.PATH_SEPCHAR) + 1));
        sb.append(parts[1]).append(api.file.File.PATH_SEPCHAR);
        sb.append(parts[2]).append(api.file.File.PATH_SEPCHAR);
        return sb.toString();
    }

    public static String[] parseURL(final String url) {
        final CharArrayTokenizer tokenizer = new CharArrayTokenizer();
        tokenizer.init(url, new char[]{ '?', '&','=' }, false);
        final String atlasURL = tokenizer.next().toString();
        tokenizer.next(); // layer
        final String layerName = tokenizer.next().toString();
        tokenizer.next(); // map
        final String mapName = tokenizer.next().toString();
        return new String[]{ atlasURL, layerName, mapName };
    }

    /**
     * Opens and scans atlas.
     */
    public boolean open() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("open atlas");
//#endif

        // open atlas in background
        Desktop.getDiskWorker().enqueue(this);

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
        final Throwable t = loadAtlas();

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
            final String baseUrl = url.substring(0, i + 1);
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("layers are in " + baseUrl);
//#endif

            // run loader
            final String urlLc = url.toLowerCase();
            final Class factory;
            if (urlLc.endsWith(".tba")) {
                factory = Class.forName("cz.kruch.track.maps.DirLoader");
            } else if (urlLc.endsWith(".tar") || urlLc.endsWith(".idx")) {
                factory = Class.forName("cz.kruch.track.maps.TarLoader");
            } else if (urlLc.endsWith(".xml")) {
                factory = Class.forName("cz.kruch.track.maps.NoMapLoader");
            } else {
                throw new InvalidMapException("Unsupported format");
            }
            final Map.Loader loader = (Map.Loader) factory.newInstance();
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
        if (log.isEnabled()) log.debug("close atlas");
//#endif

        // destroy all cached maps
        for (final Enumeration seq = maps.elements(); seq.hasMoreElements(); ) {
            final Map map = (Map) seq.nextElement();
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

    static Hashtable getLayerCollection(final Atlas atlas, final String cName) {
        Hashtable collection = (Hashtable) atlas.layers.get(cName);
        if (collection == null) {
            collection = new Hashtable();
            atlas.layers.put(cName, collection);
        }

        return collection;
    }
}
