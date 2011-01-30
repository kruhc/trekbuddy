// @LICENSE@

package cz.kruch.track.ui;

import api.location.Location;
import api.location.QualifiedCoordinates;
import cz.kruch.track.maps.Map;
import cz.kruch.track.Resources;
import cz.kruch.track.configuration.Config;
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
    void setMap(Map map) {

        // setup map viewer
        mapViewer.setMap(map);

        // forget old route; also resets map viewer
        disposeRoute();

        // create navigation for new map
        if (map != null) {

            // update basic OSD
            final QualifiedCoordinates qc = map.transform(mapViewer.getPosition());
            setBasicOSD(qc, true);
            QualifiedCoordinates.releaseInstance(qc);

            // setup navigation
            if (Desktop.wpts != null) {

                // recalc route
                prepareRoute(Desktop.wpts);

                // set route for new map
                mapViewer.setRoute(route);
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
    void setVisible(boolean b) {
        super.setVisible(b);
        if (b) { /* trick */
            if (isLocation()) {
                updatedTrick();
            }
        }
    }

    public boolean isLocation() {
        return location != null;
    }

    public void close() {
        mapViewer.setMap(null); // saves crosshair position
    }

    public int routeChanged(Vector wpts) {
        // release old route; also resets map viewer
        disposeRoute();

        // routing starts
        if (wpts != null) {

            // prepare route
            prepareRoute(wpts);

            // init route
            mapViewer.initRoute(route);
        }

        return super.routeChanged(wpts);
    }

    public int routeExpanded(Vector wpts) {
        // release old route; also resets map viewer
        disposeRoute();

        // prepare route
        prepareRoute(wpts);

        // set route
        mapViewer.setRoute(route);

        return super.routeExpanded(wpts);
    }

    // TODO vector not used
	public int navigationChanged(final Vector wpts, final int idx, final boolean silent) {
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

    private boolean isAtlas() {
        return navigator.getAtlas() != null;
    }

    private boolean isMap() {
        return navigator.getMap() != null;
    }

    /*private */void browsingOn(boolean reason) { // TODO fix visibility
        mapViewer.setCourse(Float.NaN);
        if (reason) {
            synchronized (this) {
                if (location != null && location.getFix() > 0) {
                    mapViewer.appendToTrail(location.getQualifiedCoordinates());
                }
            }
        }
    }

    int moveTo(int x, int y) { // TODO direct access from Desktop
        int mask = Desktop.MASK_NONE;
        browsingOn(false);
        if (mapViewer.hasMap() && mapViewer.move(x, y)) {
            syncOSD();
            mask = Desktop.MASK_MAP | Desktop.MASK_OSD;
        }
        return mask;
    }

    private void disposeRoute() {
        synchronized (this) {
            final Position[] route = this.route;
            if (route != null) {
                for (int i = route.length; --i >= 0;) {
                    Position.releaseInstance(route[i]);
                    route[i] = null; // gc hint
                }
                this.route = null; // gc hint
                this.mapViewer.setRoute(null);
            }
        }
    }

    private void prepareRoute(final Vector wpts) {
        // local ref
        final Map map = this.mapViewer.getMap();

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
        this.route = route;
    }

    public int handleAction(final int action, final boolean repeated) {
        // local refs
        final Desktop navigator = this.navigator;
        final MapViewer mapViewer = this.mapViewer;

        int mask = Desktop.MASK_NONE;

        // only if map viewer is usable
        if (mapViewer.hasMap()) {

            // sync or navigate
            if (action == Canvas.FIRE) {

                // mode flags
                Desktop.browsing = false;
                Desktop.navigating = !Desktop.navigating;

                // trick
                if (navigator.isTracking() && isLocation()) {
                    mask |= updatedTrick();
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

                    // find sibling in atlas
                    if (isAtlas() && !navigator._getInitializingMap() && !navigator._getLoadingSlices()) {

                        // bounds hit?
                        final char neighbour = mapViewer.boundsHit();
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("bounds hit? sibling is " + neighbour);
//#endif
                        // got sibling?
                        if (neighbour != ' ') {
                            final Map map = navigator.getMap();
                            final QualifiedCoordinates qc = map.transform(mapViewer.getPosition());
                            final double lat = qc.getLat();
                            final double lon = qc.getLon();

                            // calculate coords that lies in the sibling map
                            QualifiedCoordinates newQc = null;
                            switch (neighbour) {
                                case'N':
                                    newQc = QualifiedCoordinates.newInstance(lat + 5 * map.getStep(neighbour), lon);
                                    break;
                                case'S':
                                    newQc = QualifiedCoordinates.newInstance(lat + 5 * map.getStep(neighbour), lon);
                                    break;
                                case'E':
                                    newQc = QualifiedCoordinates.newInstance(lat, lon + 5 * map.getStep(neighbour));
                                    break;
                                case'W':
                                    newQc = QualifiedCoordinates.newInstance(lat, lon + 5 * map.getStep(neighbour));
                                    break;
                            }

                            // switch alternate map
                            navigator.startAlternateMap(navigator.getAtlas().getLayer(),
                                                        newQc, null);
                        }
                    }
                }
            }
        }

        return mask;
    }

    public int handleKey(final int keycode, final boolean repeated) {
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
                    Desktop.display.vibrate(100);
                }
                toggle = repeated;
            }
            break;

            case Canvas.KEY_NUM5: {
                if (!repeated) {
                    if (mapViewer.hasMap()) {
                        Desktop.browsing = false;
                        Desktop.navigating = !Desktop.navigating;
                        if (navigator.isTracking() && isLocation()) {
                            mask |= updatedTrick();
                        }
                    } else if (navigator.isTracking()) {
                        Desktop.showWarning(navigator._getLoadingResultText(), null, Desktop.screen);
                    }
                }
            }
            break;

//#ifdef __ALL__
            case -36: // SE
                if (!Config.easyZoomVolumeKeys)
                    break;
//#endif
            case Canvas.KEY_NUM7: {
                scrolls = 0;
                switch (Config.easyZoomMode) {
                    case Config.EASYZOOM_OFF: {
                        if (!repeated) {
                            navigator.changeLayer();
                        }
                    } break;
                    case Config.EASYZOOM_LAYERS: {
                        if (!repeated) {
                            navigator.zoom(1);
                        } else {
                            navigator.changeLayer();
                        }
                    } break;
                    case Config.EASYZOOM_MAPS: {
                        // TODO
                    } break;
                }
            }
            break;

//#ifdef __ALL__
            case -37: // SE
                if (!Config.easyZoomVolumeKeys)
                    break;
//#endif
            case Canvas.KEY_NUM9: {
                scrolls = 0;
                switch (Config.easyZoomMode) {
                    case Config.EASYZOOM_OFF: {
                        if (!repeated) {
                            navigator.changeMap();
                        }
                    } break;
                    case Config.EASYZOOM_LAYERS: {
                        if (!repeated) {
                            navigator.zoom(-1);
                        } else {
                            navigator.changeMap();
                        }
                    } break;
                    case Config.EASYZOOM_MAPS: {
                        // TODO
                    } break;
                }
            }
            break;
        }

        return mask;
    }

    public void sizeChanged(int w, int h) {
        mapViewer.sizeChanged(w, h);
    }

    public void trackingStarted() {
        // pass event
        mapViewer.reset();
    }

    public void trackingStopped() {
        // set mode
        browsingOn(true);
        // local reset
        location = null;
    }

    public int locationUpdated(Location l) {
        // make a copy of last known WGS-84 position
        synchronized (this) {
            Location.releaseInstance(location);
            location = null; // gc hint
            location = l._clone();
        }

        // pass event
        mapViewer.locationUpdated(l);

        // update
        return updatedTrick();
    }

    private int updatedTrick() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("updated trick");
//#endif
        // local refs
        final Desktop navigator = this.navigator;

        // result update mask
        int mask = Desktop.MASK_NONE;

        // tracking?
        if (!Desktop.browsing && !navigator._getInitializingMap()) {

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("tracking...");
//#endif

            synchronized (this) {
                // local rel
                final Location l = this.location;

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
                    final Map map = navigator.getMap();
                    QualifiedCoordinates qc = l.getQualifiedCoordinates();

                    // on map detection
                    final boolean onMap = map.isWithin(qc);

                    // OSD basic
                    setBasicOSD(qc, onMap);
                    Desktop.osd.setSat(l.getSat());

                    // arrows
                    final float course = l.getCourse();
                    if (!Float.isNaN(course)) {
                        mapViewer.setCourse(course);
                    }
                    if (Desktop.wpts != null) {
                        mapViewer.setNavigationCourse(navigator.wptAzimuth);
                    }

                    // OSD extended and course arrow - navigating?
                    if (Desktop.navigating && Desktop.wpts != null) {

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
                        if (isAtlas() && !navigator._getInitializingMap() && !navigator._getLoadingSlices()) {

                            // switch alternate map
                            navigator.startAlternateMap(navigator.getAtlas().getLayer(), qc, null);
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
            } // ~synchronized
        }

        return mask;
    }

    public Location getLocation() {
        return location;
    }

    public QualifiedCoordinates getPointer() {
        return navigator.getMap().transform(mapViewer.getPosition());
    }

    public void render(final Graphics g, final Font f, final int mask) {
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
/* always redraw 'background'
                if ((mask & MASK_MAP) != 0) {
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
                        g.drawString(t.getMessage(), 0, 2 * Desktop.font.getHeight(), Graphics.TOP | Graphics.LEFT);
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

    // TODO fix visibility
    static void setBasicOSD(QualifiedCoordinates qc, boolean onMap) {
        Desktop.osd.setInfo(qc, onMap);
    }

    /*private */void syncOSD() { // TODO fix visibility
        // vars
        QualifiedCoordinates qc = navigator.getMap().transform(mapViewer.getPosition());

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

        synchronized (this) {
            if (location != null && location.getFix() > 0) {
                final QualifiedCoordinates qc = location.getQualifiedCoordinates();
                final Map map = navigator.getMap();
                if (map.isWithin(qc)) {
                    moved = mapViewer.setPosition(map.transform(qc));
                }
            }
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
}

