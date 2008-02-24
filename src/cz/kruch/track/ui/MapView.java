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
import java.util.Enumeration;

/**
 * Map screen.
 */
final class MapView extends View {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("MapView");
//#endif

    // current location
    private Location location;

    // map viewer
    /*private */final MapViewer mapViewer; // TODO fix visibility

    // navigation
    private Position[] route;

    public MapView(/*Navigator*/Desktop navigator) {
        super(navigator);
        this.mapViewer = new MapViewer(/*0, 0, */);
    }

    /**
     * @deprecated hack
     */
    public void ensureSlices() {
        if (!navigator._getInitializingMap() && mapViewer.hasMap()) {
            synchronized (navigator/*loadingSlicesLock*/) { // same lock as used in _get/_setLoadingSlices!!!
                if (!navigator._getLoadingSlices()) {
                    navigator._setLoadingSlices(mapViewer.ensureSlices());
                }
            }
        }
    }

    /**
     * @deprecated hack
     */
    public void setMap(Map map) {
        // setup map viewer
        mapViewer.setMap(map);

        // forget old route
        disposeRoute();

        // create navigation for new map
        if (map != null && Desktop.wpts != null) {

            // recalc route
            prepareRoute(Desktop.wpts);

            // set route for new map
            mapViewer.setRoute(route);
        }
    }

    /**
     * @deprecated hack
     */
    public Position getPosition() {
        return mapViewer.getPosition();
    }

    /**
     * @deprecated hack
     */
    public void setPosition(Position position) {
        mapViewer.setPosition(position);
    }

    public boolean isLocation() {
        return location != null;
    }

    public void close() {
        mapViewer.setMap(null); // saves crosshair position
    }

    void setVisible(boolean b) {
        super.setVisible(b);
        if (b) { /* trick */
            if (isLocation()) {
                updatedTrick();
            }
        }
    }

    public int routeChanged(Vector wpts) {
        // release old route
        disposeRoute();

        // routing starts
        if (wpts != null) {

            // prepare route
            prepareRoute(wpts);
        }

        // init route
        mapViewer.initRoute(route);

        return super.routeChanged(wpts);
    }

    public int navigationChanged(Vector wpts, int idx, boolean silent) {
        // navigation started or changed
        if (wpts != null) {

            // update UI
            updateNavigationInfo();

        } else { // no, navigation stopped

            // hide navigation arrow and info
            mapViewer.setNavigationCourse(-1F);
            Desktop.osd.resetNavigationInfo();

            // notify user
            if (isVisible) {
                Desktop.showConfirmation(Resources.getString(Resources.DESKTOP_MSG_NAV_STOPPED), navigator);
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

    /*private */void browsingOn() { // TODO fix visibility
        mapViewer.setCourse(-1F);
    }

    private void disposeRoute() {
        if (this.route != null) {
            final Position[] route = this.route;
            for (int i = route.length; --i >= 0;) {
                Position.releaseInstance(route[i]);
                route[i] = null;
            }
            this.route = null; // gc hint
        }
    }

    private void prepareRoute(final Vector wpts) {
        // local ref
        final Map map = navigator.getMap();

        // allocate new array
        route = new Position[wpts.size()];

        // create
        for (int N = wpts.size(), c = 0, i = 0; i < N; i++) {

            // get coordinates local to map
            final Waypoint wpt = (Waypoint) wpts.elementAt(i);
            final QualifiedCoordinates localQc = map.getDatum().toLocal(wpt.getQualifiedCoordinates());
            final Position position = map.transform(localQc);

            // add to route
            route[c++] = position.clone();

            // release
            QualifiedCoordinates.releaseInstance(localQc);
        }
    }

    public int handleAction(final int action, final boolean repeated) {
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
                browsingOn();

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
                boolean scrolled = false;
//                    for (int i = steps; i-- > 0; ) {
//                        scrolled = mapViewer.scroll(action) || scrolled;
//                    }
                scrolled = mapViewer.scroll(action, steps);

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
                        char neighbour = mapViewer.boundsHit();
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

                // fast check again
                if (repeated) {
                    navigator.eventing.callSerially(navigator);
                }
            }
        }

        return mask;
    }

    public int handleKey(final int keycode, final boolean repeated) {
        int mask = Desktop.MASK_NONE;

        switch (keycode) {
            
            case Canvas.KEY_NUM0: {
                if (mapViewer.hasMap()) {

                    // cycle crosshair
                    mask = mapViewer.nextCrosshair();
                }
            }
            break;

            case Canvas.KEY_NUM5: {
                if (mapViewer.hasMap()) {

                    // mode flags
                    Desktop.browsing = false;
                    Desktop.navigating = !Desktop.navigating;

                    // trick
                    if (navigator.isTracking() && isLocation()) {
                        mask |= updatedTrick();
                    }

                } else if (navigator.isTracking()) {
                    Desktop.showWarning(navigator._getLoadingResultText(), null, navigator);
                }
            }
            break;

            case Canvas.KEY_NUM7: { // layer switch
                navigator.changeLayer();
            } break;

            case Canvas.KEY_NUM9: { // map switch
                navigator.changeMap();
            } break;

            case Canvas.KEY_STAR: {
                mapViewer.starTick();
            }
            break;
        }

        return mask;
    }

    public void sizeChanged(int w, int h) {
        // map ready but not set yet - situation after start
        if (isMap() && !mapViewer.hasMap()) {

            // var
            Map map = navigator.getMap();

            // set map
            mapViewer.setMap(map);

            // update basic OSD
            QualifiedCoordinates localQc = map.transform(mapViewer.getPosition());
            QualifiedCoordinates qc = map.getDatum().toWgs84(localQc);
            setBasicOSD(qc, localQc,  true);
            QualifiedCoordinates.releaseInstance(qc);
            QualifiedCoordinates.releaseInstance(localQc);
        }

        // propagate further
        mapViewer.sizeChanged(w, h);
    }

    public int locationUpdated(Location l) {
        // make a copy of last known WGS-84 position
/* hazard
        Location.releaseInstance(location);
*/
        location = null;
        location = l.clone();

        // update
        return updatedTrick();
    }

    private int updatedTrick() {
        // local rel
        final Location l = this.location;

        // result update mask
        int mask = Desktop.MASK_NONE;

        // tracking?
        if (!Desktop.browsing && !navigator._getInitializingMap()) {

            // minimum UI update
            mask = Desktop.MASK_OSD;

            // move on map if we get fix
            if (l.getFix() > 0) {

                // more UI updates
                mask |= Desktop.MASK_CROSSHAIR;

                // get wgs84 and local coordinates
                Map map = navigator.getMap();
                QualifiedCoordinates qc = l.getQualifiedCoordinates();
                QualifiedCoordinates localQc = map.getDatum().toLocal(qc);

                // on map detection
                boolean onMap = map.isWithin(localQc);

                // OSD basic
                setBasicOSD(qc, localQc, onMap);
                Desktop.osd.setSat(l.getSat());

                // arrows
                if (l.getCourse() > -1F) {
                    mapViewer.setCourse(l.getCourse());
                }
                if (Desktop.wpts != null) {
                    mapViewer.setNavigationCourse(navigator.wptAzimuth);
                }

                // OSD extended and course arrow - navigating?
                if (Desktop.navigating && Desktop.wpts != null) {

                    // get navigation info
                    StringBuffer extInfo = Desktop.osd._getSb();
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

                // release local coordinates
                QualifiedCoordinates.releaseInstance(localQc);

            } else {

                // if not navigating, display extended tracking info (ie. time :-) )
                if (!Desktop.navigating || Desktop.wpts == null) {
                    Desktop.osd.setSat(0);
                    Desktop.osd.setExtendedInfo(NavigationScreens.toStringBuffer(l, Desktop.osd._getSb()));
                }

            }
        }

        return isVisible ? mask : Desktop.MASK_NONE;
    }

    public Location getLocation() {
        return location;
    }

    public QualifiedCoordinates getPointer() {
        return navigator.getMap().getDatum().toWgs84(navigator.getMap().transform(mapViewer.getPosition()));
    }

    public void render(final Graphics g, final Font f, final int mask) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("render");
//#endif

        // common screen params
        g.setFont(f);

        // is map(viewer) ready?
        if (!mapViewer.hasMap()) {

            // clear window
            g.setColor(0x0);
            g.fillRect(0, 0, Desktop.width, Desktop.height);
            g.setColor(0x00FFFFFF);

            // draw loaded target
            Object[] result = navigator._getLoadingResult();
            if (result[0] != null) {
                g.drawString(result[0].toString(), 0, 0, Graphics.TOP | Graphics.LEFT);
            }
            if (result[1] != null) {
                if (result[1] instanceof Throwable) {
                    Throwable t = (Throwable) result[1];
                    g.drawString(t.getClass().toString().substring(6) + ":", 0, Desktop.font.getHeight(), Graphics.TOP | Graphics.LEFT);
                    if (t.getMessage() != null) {
                        g.drawString(t.getMessage(), 0, 2 * Desktop.font.getHeight(), Graphics.TOP | Graphics.LEFT);
                    }
                } else {
                    g.drawString(result[1].toString(), 0, Desktop.font.getHeight(), Graphics.TOP | Graphics.LEFT);
                }
            }

        } else {

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
                g.setColor(Config.osdBlackColor ? 0x00000000 : 0x00FFFFFF);

                // render
                Desktop.osd.render(g);
            }
        }

        // draw status
        if ((mask & Desktop.MASK_STATUS) != 0) {

            // set text color
            g.setColor(Config.osdBlackColor ? 0x00000000 : 0x00FFFFFF);

            // render
            Desktop.status.render(g);
        }

        // flush
        if ((mask & Desktop.MASK_MAP) != 0 || (mask & Desktop.MASK_SCREEN) != 0 || !Desktop.partialFlush) {
            flushGraphics();
        } else {
            if ((mask & Desktop.MASK_OSD) != 0) {
                flushGraphics(Desktop.osd.getClip());
            }
            if ((mask & Desktop.MASK_STATUS) != 0) {
                flushGraphics(Desktop.status.getClip());
            }
            if ((mask & Desktop.MASK_CROSSHAIR) != 0) {
                flushGraphics(mapViewer.getClip());
            }
        }
    }

    private void getNavigationInfo(StringBuffer extInfo) {
        final float distance = navigator.wptDistance;
        final int azimuth = navigator.wptAzimuth;

        extInfo.append(NavigationScreens.DELTA_D).append('=');
        NavigationScreens.printDistance(extInfo, distance);
        NavigationScreens.append(extInfo, azimuth).append(NavigationScreens.SIGN);
        if (!Float.isNaN(navigator.wptHeightDiff)) {
            extInfo.append(' ').append(NavigationScreens.DELTA_d).append('=');
            NavigationScreens.append(extInfo, (int) navigator.wptHeightDiff);
        }
    }

    // TODO fix visibility
    static void setBasicOSD(QualifiedCoordinates qc, QualifiedCoordinates localQc, boolean onMap) {
        if (Config.useGeocachingFormat || Config.useUTM) {
            Desktop.osd.setInfo(qc, onMap);
        } else {
            Desktop.osd.setInfo(localQc, onMap);
        }
    }

    /*private */void syncOSD() { // TODO fix visibility
        // vars
        Map map = navigator.getMap();
        QualifiedCoordinates localQc = map.transform(mapViewer.getPosition());
        QualifiedCoordinates from = map.getDatum().toWgs84(localQc);

        // OSD basic
        setBasicOSD(from, localQc, true);

        // update navigation info
        navigator.updateNavigation(from);

        // release to pool
        QualifiedCoordinates.releaseInstance(from);
        QualifiedCoordinates.releaseInstance(localQc);

        // update extended OSD (and navigation, if any)
        if (!updateNavigationInfo()) {
            Desktop.osd.resetNavigationInfo();
        }
    }

    private boolean syncPosition() {
        boolean moved = false;

        if (location != null) {
            Map map = navigator.getMap();
            QualifiedCoordinates localQc = map.getDatum().toLocal(location.getQualifiedCoordinates());
            if (map.isWithin(localQc)) {
                moved = mapViewer.setPosition(map.transform(localQc));
            }
            QualifiedCoordinates.releaseInstance(localQc);
        }

        return moved;
    }

    private int getScrolls() {
        int steps = 1;
        if (Desktop.scrolls++ >= 15) {
            steps = 2;
            if (Desktop.scrolls >= 30) {
                steps = 3;
            }
            if (Desktop.scrolls >= 40) {
                steps = 4;
            }
        }

        return steps;
    }

    /*private */boolean updateNavigationInfo() { // TODO fix visibility
        // navigating?
        if (Desktop.wpts != null && Desktop.wptIdx > -1) {

            // get navigation info
            StringBuffer extInfo = Desktop.osd._getSb();
            getNavigationInfo(extInfo);

            // set course and delta
            mapViewer.setNavigationCourse(navigator.wptAzimuth);
            Desktop.osd.setNavigationInfo(extInfo);

            return true;
        }

        return false;
    }
}

