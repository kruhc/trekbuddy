// @LICENSE@

package cz.kruch.track.ui;

import api.location.Location;
import api.location.QualifiedCoordinates;
import cz.kruch.track.maps.Map;
import cz.kruch.track.maps.Atlas;
import cz.kruch.track.Resources;
import cz.kruch.track.util.NakedVector;
import cz.kruch.track.util.GpxVector;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;
import cz.kruch.track.location.Waypoint;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Font;
import java.util.Vector;

/**
 * Map screen.
 */
final class MapView extends View {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("MapView");
//#endif

    // for faster movement
    private volatile int scrolls;

    // toggle flag
    private volatile boolean toggle;

    // local copy of current location
    private Location location;

    // map viewer
    /*private */final MapViewer mapViewer; // TODO fix visibility

    // navigation
    private Position[] route;
    private int lastRouteId;

    // magnifier
    private int x2;

    MapView(/*Navigator*/Desktop navigator) {
        super(navigator);
        this.mapViewer = new MapViewer(/*0, 0, */);
    }

    /**
     * @deprecated hack
     */
    boolean prerender() {
        return mapViewer.hasMap() && mapViewer.ensureSlices();
    }

    /**
     * @deprecated hack
     */
    void setMap(final Map map) {

        // setup magnifier
        if (map != null) {
            map.setMagnifier(x2);
        }

        // setup map viewer
        injectMap(map);

        // forget old route; also resets map viewer
        disposeRoute();

        // create navigation for new map
        if (map != null) {

            // update basic OSD
            final QualifiedCoordinates qc = map.transform(mapViewer.getPosition());
            setBasicOSD(qc, true);
            // not too frequent, reduce code size
//            QualifiedCoordinates.releaseInstance(qc);

            // setup navigation
            if (Desktop.wpts != null) {

                // recalc route
                prepareRoute(Desktop.wpts);

                // set route for new map
                synchronized (this) { // @threads ?:route
                    mapViewer.setRoute(route);
                }
            }
        }
    }

    /**
     * @deprecated hack
     */
    Position getPosition() {
        return mapViewer.getPosition();
    }

    /**
     * @deprecated hack
     */
    void setPosition(Position position) {
        mapViewer.setPosition(position);
    }

    /* @Override */
    void setVisible(final boolean b) {
        super.setVisible(b);
        if (b) { /* trick */
            if (isLocation()) {
                updatedTrick();
            }
        }
    }

    boolean isLocation() {
        return location != null;
    }

//#if __ANDROID__ || __CN1__

    void onBackground() {
        // release images
        mapViewer.reslice();
    }

//#endif

    void close() {
        injectMap(null); // may save position in default map/atlas
    }

    int routeChanged(final Vector wpts, final int mode) {

        // release old route
        disposeRoute();

        // mapviewer reset
        boolean reset = false;

        // ranges
        NakedVector ranges = null;

        // routing starts
        if (wpts != null) {

            // prepare route
            prepareRoute(wpts);

            // get ranges
            if (((GpxVector) wpts).hasTracks()) {
                ranges = ((GpxVector) wpts).getRanges();
            }

            // detect significant change
            reset = lastRouteId != wpts.hashCode();
            lastRouteId = wpts.hashCode();

        } else {

            // stop
            lastRouteId = 0;
        }

        // init or clear route
        synchronized (this) { // @threads ?:route
            mapViewer.initRoute(route, ranges, reset, mode); // route is null when navigation stopped
        }

        return super.routeChanged(wpts, mode);
    }

    int routeExpanded(final Vector wpts) {

        // release old route
        disposeRoute();

        // prepare route
        prepareRoute(wpts);

        // set route
        synchronized (this) { // @threads ?:route
            mapViewer.setRoute(route);
        }

        return super.routeExpanded(wpts);
    }

    // TODO vector not used
	int navigationChanged(final Vector wpts, final int idx, final boolean silent) {

        // navigation started or changed
        if (wpts != null) {

            // update UI
            updateNavigationInfo();

        } else { // no, navigation stopped

            // hide navigation arrow and info
            mapViewer.setNavigationCourse(Float.NaN);
            Desktop.osd.resetNavigationInfo();

            // notify user
            if (isVisible) {
                Desktop.showConfirmation(Resources.getString(Resources.DESKTOP_MSG_NAV_STOPPED), Desktop.screen);
            }
        }

        return super.navigationChanged(wpts, idx, silent);
    }

    private void injectMap(final Map map) {
        // store position
        final Map currentMap = mapViewer.getMap();
        if (currentMap != null) {
            QualifiedCoordinates qc = currentMap.transform(mapViewer.getPosition());
            Config.latAny = qc.getLat();
            Config.lonAny = qc.getLon();
            if (isDefault(currentMap)) {
                Config.lat = qc.getLat();
                Config.lon = qc.getLon();
            }
            // not too frequent, reduce code size
//            QualifiedCoordinates.releaseInstance(qc);
            Config.updateInBackground(Config.VARS_090);
        }

        // set the map
        mapViewer.setMap(map);

        // restore position on map
        if (map != null) {
            QualifiedCoordinates qc = QualifiedCoordinates.newInstance(Config.latAny, Config.lonAny);
            if (map.isWithin(qc)) {
                setPosition(map.transform(qc));
            } else {
                // not too frequent, reduce code size
//                QualifiedCoordinates.releaseInstance(qc);
                qc = QualifiedCoordinates.newInstance(Config.lat, Config.lon);
                if (map.isWithin(qc)) {
                    setPosition(map.transform(qc));
                }
                // not too frequent, reduce code size
//                QualifiedCoordinates.releaseInstance(qc);
            }
        }
    }

    /*private */void browsingOn(final boolean trackingStopped) { // TODO fix visibility
        // per Pavel request, hide after stop
        if (!trackingStopped) {
            mapViewer.setCourse(Float.NaN);
        }

        // tracking stopped?
        if (trackingStopped) {

            // append last position to trail
            boolean hasLocation;
            double lat = 0D, lon = 0D;
            synchronized (this) { // @threads ?:location
                final Location l = location;
                hasLocation = l != null && l.getFix() > 0;
                if (hasLocation) {
                    final QualifiedCoordinates qc = l.getQualifiedCoordinates();
                    lat = qc.getLat();
                    lon = qc.getLon();
                }
            }
            if (hasLocation) {
                mapViewer.appendToTrail(lat, lon);
            }
        }
    }

    int moveTo(final int x, final int y) { // TODO direct access from Desktop
        int mask = Desktop.MASK_NONE;

        // set browsing mode
        browsingOn(false);

        // update position and update OSD
        if (mapViewer.hasMap()) {
            if (mapViewer.move(x, y)) {
                syncOSD();
                mask = Desktop.MASK_MAP | Desktop.MASK_OSD;
            } else {
                final Position p = mapViewer.getPosition();
                final int px = p.getX();
                final int py = p.getY();
                int action;
                if (px == 0) {
                    action = Canvas.LEFT;
                } else if (py == 0) {
                    action = Canvas.UP;
                } else {
                    final Map map = mapViewer.getMap();
                    final int dx = Math.abs(map.getWidth() - px);
                    final int dy = Math.abs(map.getHeight() - py);
                    if (dx > dy) {
                        action = Canvas.DOWN;
                    } else {
                        action = Canvas.RIGHT;
                    }
                }
                loadSiblingMap(action);
            }
        }

        return mask;
    }

    private void disposeRoute() {
        synchronized (this) { // @threads ?:route
            this.route = null;
        }
    }

    private void prepareRoute(final Vector wpts) {
        // local ref
        final Map map = mapViewer.getMap();

        // NPE reported on AM // TODO how can this happen??
        if (map != null) {

            // allocate new array
            final Position[] route = new Position[wpts.size()];

            // create
            for (int N = wpts.size(), c = 0, i = 0; i < N; i++) {

                // get position on map
                final Waypoint wpt = (Waypoint) wpts.elementAt(i);
                final QualifiedCoordinates qc = wpt.getQualifiedCoordinates();
                final Position position = map.transform(qc);

                // add to route
                route[c++] = position._clone();
            }

            // set
            synchronized (this) { // @threads ?:route
                this.route = route;
            }
        }
    }

    int handleAction(final int action, final boolean repeated) {
        // local refs
        final MapViewer mapViewer = this.mapViewer;

        int mask = Desktop.MASK_NONE;

        // only if map viewer is usable
        if (mapViewer.hasMap()) {

            // local ref
            final Desktop navigator = this.navigator;

            // sync or navigate
            if (action == Canvas.FIRE) {

                // sync
                if (!repeated) {

                    // mode flags
                    Desktop.synced = false;
                    Desktop.browsing = false;
                    Desktop.navigating = !Desktop.navigating;

                    // trick
                    if (navigator.isTracking() && isLocation()) {
                        mask |= updatedTrick();
                    }

                } else {

                    // trigger bitmap zoom
                    toggleMagnifier();
                    mask = Desktop.MASK_ALL;
                }

            } else { // move left-right-up-down

                // cursor movement breaks real-time tracking
                Desktop.browsing = true;
                browsingOn(false);

                // calculate number of scrolls
                int steps = 1;
                if (navigator._getLoadingSlices() || navigator._getInitializingMap()) {
                    steps = 0;
                } else if (repeated) {
                    steps = getScrolls();
                }

//#ifdef __LOG__
                if (log.isEnabled()) log.debug("handleAction - steps to scroll = " + steps);
//#endif

                // scroll the maps
                boolean scrolled = mapViewer.scroll(action, steps);

                // has map been scrolled?
                if (scrolled) {

                    // sync OSD
                    syncOSD();

                    // update mask
                    mask = Desktop.MASK_MAP | Desktop.MASK_OSD;

                } else if (steps > 0) { // no scroll? out of current map? find sibling map

                    // try sibling map
                    loadSiblingMap(action);
                }
            }
        }

        return mask;
    }

    int handleKey(final int keycode, final boolean repeated) {
        // local refs
        final Desktop navigator = this.navigator;
        final MapViewer mapViewer = this.mapViewer;

        // result repaint mask
        int mask = Desktop.MASK_NONE;

        // handle key
        switch (keycode) {
            
            case Canvas.KEY_NUM0: {
                if (!repeated) {
                    if (!toggle) {
                        mask = mapViewer.nextCrosshair();
                    }
                } else {
                    mapViewer.starTick();
                    Desktop.display.vibrate(100); // bypass power-save check
                }
                toggle = repeated;
            }
            break;

            case Canvas.KEY_NUM5: { // same as FIRE
                if (!repeated) {
                    if (mapViewer.hasMap()) {
                        Desktop.synced = false;
                        Desktop.browsing = false;
                        Desktop.navigating = !Desktop.navigating;
                        if (navigator.isTracking() && isLocation()) {
                            mask |= updatedTrick();
                        }
                    } else if (navigator.isTracking()) {
                        Desktop.showWarning(navigator._getLoadingResultText(), null, Desktop.screen);
                    }
                } else { // trigger bitmap zoom
                    toggleMagnifier();
                    mask = Desktop.MASK_ALL;
                }
            }
            break;

//#ifdef __ANDROID__
            case -25:
//#elifdef __RIM__
            case -151:
//#else
            case -37: // SE
//#endif
                if (!Config.easyZoomVolumeKeys)
                    break;
            case Canvas.KEY_NUM7: {
                scrolls = 0;
/* obsolete since 1.27
                switch (Config.easyZoomMode) {
                    case Config.EASYZOOM_OFF: {
                        if (!repeated) {
                            navigator.changeLayer();
                        }
                    } break;
                    case Config.EASYZOOM_LAYERS: {
*/
                        if (!repeated) {
                            navigator.zoom(-1);
                        } else {
                            navigator.changeLayer();
                        }
/*
                    } break;
                    case Config.EASYZOOM_MAPS: {
                        // TODO
                    } break;
                }
*/
            } break;

//#ifdef __ANDROID__
            case -24:
//#elifdef __RIM__
            case -150:
//#else
            case -36: // SE
//#endif
                if (!Config.easyZoomVolumeKeys)
                    break;
            case Canvas.KEY_NUM9: {
                scrolls = 0;
/* obsolete since 1.27
                switch (Config.easyZoomMode) {
                    case Config.EASYZOOM_OFF: {
                        if (!repeated) {
                            navigator.changeMap();
                        }
                    } break;
                    case Config.EASYZOOM_LAYERS: {
*/
                        if (!repeated) {
                            navigator.zoom(1);
                        } else {
                            navigator.changeMap();
                        }
/*
                    } break;
                    case Config.EASYZOOM_MAPS: {
                        // TODO
                    } break;
                }
*/
            } break;
        }

        return mask;
    }

    void sizeChanged(int w, int h) {
        mapViewer.sizeChanged(w, h);
    }

    void trackingStarted() {
        // pass event
        mapViewer.reset();
    }

    void trackingStopped() {
        // set mode
        browsingOn(true);
        // local reset
        synchronized (this) { // @threads ?:location
            location = null;
        }
    }

    int locationUpdated(Location l) {
        // only valid location
        if (l.getFix() > 0) {
            
            // make a copy of last known WGS-84 position
            synchronized (this) { // @threads ?:location
                if (location != null) {
                    location.copyFrom(l);
                } else {
                    location = l._clone();
                }
            }

            // pass event
            mapViewer.locationUpdated(l);

            // update
            return updatedTrick();
        }

        return Desktop.MASK_SCREEN;
    }

    Location getLocation() {
        return location;
    }

    QualifiedCoordinates getPointer() {
        return navigator.getMap().transform(mapViewer.getPosition()); // mapViewer may have no map when in bg
    }

    void render(final Graphics g, final Font f, final int mask) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("render");
//#endif

        // local refs
        final MapViewer mapViewer = this.mapViewer;

        // common screen params
        g.setFont(f);

        // is map(viewer) ready?
        if (mapViewer.hasMap()) {

            // draw map
/* always redraw 'background' // TODO review!!!
                if ((mask & Desktop.MASK_MAP) != 0) {
*/
            // whole map redraw requested
            mapViewer.render(g);
/*
                }
*/

            // draw OSD
            if ((mask & Desktop.MASK_OSD) != 0) {

                // set text color
                g.setColor(Config.osdBlackColor ? 0x0 : 0x00FFFFFF);

                // render
                Desktop.osd.render(g);
            }

            // draw status
            if ((mask & Desktop.MASK_STATUS) != 0) {

                // set text color
                g.setColor(Config.osdBlackColor ? 0x00000000 : 0x00FFFFFF);

                // render
                Desktop.status.render(g);
            }

            // draw backlight status
            if (cz.kruch.track.ui.nokia.DeviceControl.getBacklightStatus() != 0) {
                NavigationScreens.drawBacklightStatus(g);
            }

            // draw keylock status
            if (Desktop.screen.isKeylock()) {
                NavigationScreens.drawKeylockStatus(g);
            }

/*
            drawGrid(g);
*/

            // draw zoom spots
            if (navigator.isAtlas()) {
                NavigationScreens.drawZoomSpots(g);
            }

            // draw visual guides
            NavigationScreens.drawGuideSpots(g, true);

        } else { // no map

            // clear window
            g.setColor(0x0);
            g.fillRect(0, 0, Desktop.width, Desktop.height);
            g.setColor(0x00FFFFFF);

            // draw loaded target
            final Object[] result = navigator._getLoadingResult();
            if (result[0] != null) {
                g.drawString(result[0].toString(), 0, 0, Graphics.TOP | Graphics.LEFT);
            }
            if (result[1] != null) {
                if (result[1] instanceof Throwable) {
                    final Throwable t = (Throwable) result[1];
                    g.drawString(t.getClass().toString().substring(6) + ":", 0, Desktop.font.getHeight(), Graphics.TOP | Graphics.LEFT);
                    if (t.getMessage() != null) {
                        g.drawString(t.getMessage(), 0, Desktop.font.getHeight() << 1, Graphics.TOP | Graphics.LEFT);
                    }
                    if (result[2] == null) {
                        result[2] = result[1];
                        Desktop.showError("Init map", t, Desktop.screen);
                    }
                } else {
                    g.drawString(result[1].toString(), 0, Desktop.font.getHeight(), Graphics.TOP | Graphics.LEFT);
                }
            }

        }
/*
        // flush
        if ((mask & Desktop.MASK_MAP) != 0 || (mask & Desktop.MASK_SCREEN) != 0 || !Desktop.partialFlush) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("full flush");
//#endif
            flushGraphics();
        } else {
            if ((mask & Desktop.MASK_OSD) != 0) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("flush OSD clip " + Desktop.osd.getClip());
//#endif
                flushGraphics(Desktop.osd.getClip());
            }
            if ((mask & Desktop.MASK_STATUS) != 0) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("flush Status clip " + Desktop.status.getClip());
//#endif
                flushGraphics(Desktop.status.getClip());
            }
            if ((mask & Desktop.MASK_CROSSHAIR) != 0) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("flush crosshair clip " + mapViewer.getClip());
//#endif
                flushGraphics(mapViewer.getClip());
            }
        }
*/
    }

    private void loadSiblingMap(final int action) {

        // find sibling in atlas
        if (navigator.isAtlas() && !navigator._getInitializingMap() && !navigator._getLoadingSlices()) {

            // bounds hit?
            final char neighbour = mapViewer.boundsHit(action);
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("bounds hit? sibling is " + neighbour);
//#endif
            // got sibling?
            if (neighbour != ' ') {
                final Map map = mapViewer.getMap();
                final QualifiedCoordinates qc = map.transform(mapViewer.getPosition());
                final double lat = qc.getLat();
                final double lon = qc.getLon();

                // calculate coords that lies in the sibling map
                QualifiedCoordinates newQc = null;
                switch (neighbour) {
                    case 'E':
                        newQc = QualifiedCoordinates.newInstance(lat, lon + 5 * map.getStep(neighbour));
                        break;
                    case 'N':
                        newQc = QualifiedCoordinates.newInstance(lat + 5 * map.getStep(neighbour), lon);
                        break;
                    case 'S':
                        newQc = QualifiedCoordinates.newInstance(lat + 5 * map.getStep(neighbour), lon);
                        break;
                    case 'W':
                        newQc = QualifiedCoordinates.newInstance(lat, lon + 5 * map.getStep(neighbour));
                        break;
                }

                // switch alternate map
                navigator.startAlternateMap(navigator.getAtlas().getLayer(), newQc);
            }
        }
    }

    private int updatedTrick() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("updated trick");
//#endif
        // local refs
        final Desktop navigator = this.navigator;

        // result update mask
        int mask = Desktop.MASK_NONE;

        // clear synced ('onMap') flag
        Desktop.synced = false;

        // tracking?
        if (!Desktop.browsing && !navigator._getInitializingMap()) {

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("tracking...");
//#endif

            final Location l;
            synchronized (this) { // @threads ?:location
                // local rel
                l = this.location;
            }

                // minimum UI update
                mask = Desktop.MASK_OSD;

                // move on map if we get fix
                if (l.getFix() > 0) {

//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("have fix");
//#endif

                    // more UI updates
                    mask |= Desktop.MASK_CROSSHAIR;

                    // get wgs84 and local coordinates
                    final Map map = navigator.getMap(); // mapViewer may have no map when in bg // !!! not true since 1.1.2
                    QualifiedCoordinates qc = l.getQualifiedCoordinates();

                    // on map detection
                    final boolean onMap = Desktop.synced = map.isWithin(qc);

                    // OSD basic
                    setBasicOSD(qc, onMap);
                    Desktop.osd.setSat(l.getSat());

                    // course arrow
                    final float course = l.getCourse();
                    if (!Float.isNaN(course)) {
                        mapViewer.setCourse(course);
                    }

                    // navi arrow
                    final boolean navigating = Desktop.navigating && Desktop.wpts != null && Desktop.wptIdx != -1;
                    if (navigating) {
                        mapViewer.setNavigationCourse(navigator.wptAzimuth);
                    }

                    // OSD extended with navi or tracking info
                    if (navigating) {

                        // get navigation info
                        final StringBuffer extInfo = Desktop.osd._getSb();
                        getNavigationInfo(extInfo);

                        // set navigation info
                        Desktop.osd.setNavigationInfo(extInfo);

                    } else { // no, tracking info

                        // in extended info
                        Desktop.osd.setExtendedInfo(NavigationScreens.toStringBuffer(l, Desktop.osd._getSb()));
                    }

                    // are we on map?
                    if (onMap) {

                        // sync position
                        if (syncPosition()) {
                            mask |= Desktop.MASK_MAP;
                        }

                    } else { // off current map

                        // load sibling map, if exists
                        if (navigator.isAtlas() && !navigator._getInitializingMap() && !navigator._getLoadingSlices()) {

                            // switch alternate map
                            navigator.startAlternateMap(navigator.getAtlas().getLayer(), qc);
                        }
                    }

                } else {

//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("no fix");
//#endif

                    // if not navigating, display extended tracking info (ie. time :-) )
                    if (!Desktop.navigating || Desktop.wpts == null) {
                        Desktop.osd.setSat(0);
                        Desktop.osd.setExtendedInfo(NavigationScreens.toStringBuffer(l, Desktop.osd._getSb()));
                    }

                }
//            } // ~synchronized
        }

        return mask;
    }

    private int toggleMagnifier() {
        // map needed
        if (!mapViewer.hasMap()) {
            return Desktop.MASK_NONE;
        }

        // give feedback
        Desktop.vibrate(50);

        // invert
        x2 = ++x2 % 2;

        // setup map
        final Map map = mapViewer.getMap();
        setMap(null);
        map.setMagnifier(x2);
        setMap(map);

        return Desktop.MASK_ALL;
    }

    private void getNavigationInfo(final StringBuffer extInfo) {
        final Desktop navigator = this.navigator;
        extInfo.append(NavigationScreens.DELTA_D).append('=');
        NavigationScreens.printDistance(extInfo, navigator.wptDistance);
        NavigationScreens.append(extInfo, navigator.wptAzimuth).append(NavigationScreens.SIGN);
        if (!Float.isNaN(navigator.wptHeightDiff)) {
            extInfo.append(' ').append(NavigationScreens.DELTA_d).append('=');
            NavigationScreens.printAltitude(extInfo, navigator.wptHeightDiff);
        }
    }

    int magnify(final int direction) {
        // map needed
        if (!mapViewer.hasMap()) {
            return Desktop.MASK_NONE;
        }

        // convert to toggle
        if ((x2 == 0 && direction == 1) || (x2 == 1 && direction == -1)) {
            return toggleMagnifier();
        }
        return Desktop.MASK_NONE;
    }

    // TODO fix visibility
    static void setBasicOSD(QualifiedCoordinates qc, boolean onMap) {
        Desktop.osd.setInfo(qc, onMap);
    }

    /*private */void syncOSD() { // TODO fix visibility
        // vars
        QualifiedCoordinates qc = getPointer();

        // OSD basic
        setBasicOSD(qc, true);

        // update navigation info
        navigator.updateNavigation(qc);

        // release to pool
        QualifiedCoordinates.releaseInstance(qc);

        // update extended OSD (and navigation, if any)
        if (!updateNavigationInfo()) {
            Desktop.osd.resetNavigationInfo();
        }
    }

    private boolean syncPosition() {
        boolean moved = false;

        // update current position on map
        boolean hasLocation;
        double lat = 0D, lon = 0D;
        synchronized (this) { // @threads ?:location
            final Location l = location;
            hasLocation = l != null && l.getFix() > 0;
            if (hasLocation) {
                final QualifiedCoordinates qc = l.getQualifiedCoordinates();
                lat = qc.getLat();
                lon = qc.getLon();
            }
        }
        if (hasLocation) {
            final Map map = navigator.getMap();
            final QualifiedCoordinates qc = QualifiedCoordinates.newInstance(lat, lon);
            if (map.isWithin(qc)) {
                moved = mapViewer.setPosition(map.transform(qc));
            }
            QualifiedCoordinates.releaseInstance(qc);
        }

        return moved;
    }

    private int getScrolls() {
        int steps = 1;
        if (scrolls++ >= 15) {
            steps = 2;
            if (scrolls >= 30) {
                steps = 3;
            }
            if (scrolls >= 45) {
                steps = 6;
            }
        }

        return steps;
    }

    /*private */boolean updateNavigationInfo() { // TODO fix visibility
        // navigating?
        if (Desktop.wpts != null && Desktop.wptIdx > -1) {

            // get navigation info
            final StringBuffer extInfo = Desktop.osd._getSb();
            getNavigationInfo(extInfo);

            // set course and delta
            mapViewer.setNavigationCourse(navigator.wptAzimuth);
            Desktop.osd.setNavigationInfo(extInfo);

            return true;
        }

        return false;
    }

    private static boolean isDefault(final Map map) {
        // no map
        if (map == null) {
            return false;
        }

        // default map?
        final String startupURL = Config.mapURL;
        if (startupURL == null || startupURL.length() == 0) {
//#ifndef __CN1__
            return Desktop.DEFAULT_MAP_NAME == map.getName(); // '==' is OK
//#else
            return Desktop.DEFAULT_MAP_NAME.equals(map.getName());
//#endif
        }

        // atlas
        final String mapPath = map.getPath();
        if (startupURL.indexOf('?') > -1) {
            final String s = Atlas.atlasURLtoFileURL(startupURL);
            return mapPath.startsWith(s);
        } else { // single map
            return mapPath.equals(startupURL);
        }
    }

/*
    void drawGrid(Graphics g) {
        {
            final float dj = (float)Desktop.screen.getWidth() / 5f;
            final float di = (float)Desktop.screen.getHeight() / 10f;

            for (int i = 0; i <= 10; i++)
            for (int j = 0; j <= 4; j++)
            switch (i) {
                case 0: {
                    switch (j) {
                        case 0:
                            drawGridBox(g, "1", j * dj, i * di, dj, di);
                            break;
                        case 1:
                        case 2:
                        case 3:
                            drawGridBox(g, "UP", j * dj, i * di, dj, di);
                            break;
                        case 4:
                            drawGridBox(g, "3", j * dj, i * di, dj, di);
                            break;
                    }
                } break;
                case 1: {
//                    switch (j) {
//                        case 1:
//                        case 2:
//                        case 3:
//                            drawGridBox(g, "UP", j * dj, i * di, dj, di);
//                            break;
//                    }
                } break;
                case 2:
                case 3:
                case 4:
                case 5: {
                    switch (j) {
                        case 0:
                            drawGridBox(g, "LEFT", j * dj, i * di, dj, di);
                            break;
                        case 1:
                        case 2:
                        case 3:
                            drawGridBox(g, "5", j * dj, i * di, dj, di);
                            break;
                        case 4:
                            drawGridBox(g, "RIGHT", j * dj, i * di, dj, di);
                            break;
                    }
                } break;
                case 6: {
//                    switch (j) {
//                        case 1:
//                        case 2:
//                        case 3:
//                            drawGridBox(g, "DOWN", j * dj, i * di, dj, di);
//                            break;
//                    }
                } break;
                case 7: {
                    switch (j) {
                        case 0:
                            drawGridBox(g, "7", j * dj, i * di, dj, di);
                            break;
                        case 1:
                        case 2:
                        case 3:
                            drawGridBox(g, "DOWN", j * dj, i * di, dj, di);
                            break;
                        case 4:
                            drawGridBox(g, "9", j * dj, i * di, dj, di);
                            break;
                    }
                } break;
                case 8:
                    // space!!!
                    break;
                case 9:
                case 10: {
                    switch (j) {
                        case 0:
                            drawGridBox(g, "*", j * dj, i * di, dj, di);
                            break;
                        case 1:
                            drawGridBox(g, "", j * dj, i * di, dj, di);
                            break;
                        case 2:
                            drawGridBox(g, "", j * dj, i * di, dj, di);
                            break;
                        case 3:
                            drawGridBox(g, "", j * dj, i * di, dj, di);
                            break;
                        case 4:
                            drawGridBox(g, "#", j * dj, i * di, dj, di);
                            break;
                    }
                } break;
            }
        }
    }

    void drawGridBox(Graphics g, String label, float x, float y, float w, float h) {
        g.setColor(0x40ff0000);
        g.fillRect((int)x, (int)y, (int)w, (int)h);
        g.setColor(0x4000ff00);
        g.fillRect((int)x + 2, (int)y + 2, (int)w - 4, (int)h - 4);
        g.setColor(0x000000ff);
        g.drawString(label, (int)x + 2, (int)y + 2, 0);
    }
*/
}

