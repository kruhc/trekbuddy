// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.maps.Slice;
import cz.kruch.track.maps.Map;
import cz.kruch.track.util.ExtraMath;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.Sprite;
import java.util.Vector;

import api.location.Location;
import api.location.QualifiedCoordinates;
import api.location.Datum;
import api.location.ProjectionSetup;

/**
 * Map viewer.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class MapViewer {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("MapViewer");
//#endif

    private static final int MAX_TRAJECTORY_LENGTH = 2048;

    public static final byte WPT_STATUS_VOID    = 0;
    public static final byte WPT_STATUS_REACHED = 1;
    public static final byte WPT_STATUS_MISSED  = 2;
    public static final byte WPT_STATUS_CURRENT = 3;

    private int x, y;
    private int chx, chy;
    private int chx0, chy0;
    private int crosshairSize, crosshairSize2;
    private int mWidth, mHeight;

    private int gx, gy;

/*
    private final int[] clip;
*/
    private final Position position;

    private int scaleDx/*, dy*/;
    private int scaleLength;
    private final char[] sInfo;
    private int sInfoLength;

    private Map map;
    private Vector slices;
    private Vector slices2; // for reuse during switch

    private float course, course2;

    private Position[] wptPositions;
    private byte[] wptStatuses;

    private int star;

    private QualifiedCoordinates[] trailLL;
    private Position[] trailXY;
    private QualifiedCoordinates refQc;
    private short[] trailPF;
    private int lllast, xylast, pflast, llcount, xycount;
    private float accDist, refCourse, courseDeviation;

    private int ci, li;

    MapViewer() {
        this.crosshairSize = NavigationScreens.crosshairs.getHeight();
        this.crosshairSize2 = this.crosshairSize >> 1;
/*
        this.clip = new int[] { -1, -1, crosshairSize, crosshairSize };
*/
        this.position = new Position(0, 0);
        this.slices = new Vector(4);
        this.slices2 = new Vector(4);
        this.sInfo = new char[32];
        this.course = this.course2 = Float.NaN;
        this.trailLL = new QualifiedCoordinates[MAX_TRAJECTORY_LENGTH];
        this.trailXY = new Position[MAX_TRAJECTORY_LENGTH];
        this.trailPF = new short[MAX_TRAJECTORY_LENGTH];
    }

    public void sizeChanged(int w, int h) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("size changed");
//#endif

        Position p = getPosition()._clone();
        this.chx0 = this.chx = (w - crosshairSize) >> 1;
        this.chy0 = this.chy = (h - crosshairSize) >> 1;
        this.x = this.y = 0;
        this.gx = this.gy = 0;
        // ??? ... start scrolling at left-top corner ... ???
        if (Config.oneTileScroll) {
            this.chx = 0 - crosshairSize2/* - 1*/;
            this.chy = 0 - crosshairSize2/* - 1*/;
        }
        // ~
        setPosition(p);

        // scale drawing x offset (same as in HPS)
        final int lineLength = Math.min(w - w / 10, h - h / 10);
        this.scaleDx = (w - lineLength) >> 1;

        // update scale
        calculateScale();
    }

    boolean hasMap() {
        return map != null;
    }

    Map getMap() {
        return map;
    }

    void setMap(Map map) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("set map " + map);
//#endif

        // store position on map
        if (isDefaultMap(this.map)) {
            final Position p = getPosition();
            Config.x = p.getX();
            Config.y = p.getY();
            try {
                Config.update(Config.VARS_090);
            } catch (ConfigurationException e) {
                // ignore
            }
        }

        /* synchronized to avoid race condition with render() */
        synchronized (this) {

            // use new map
            this.map = null; // gc hint
            this.map = map;

            // clear slices collection
            this.slices.removeAllElements();
            /* slicesTemp is always empty */

        } // ~synchronized

        // use new map (if any)
        if (map != null) {

            // update context
            Datum.contextDatum = map.getDatum();
            ProjectionSetup.contextProjection = map.getProjection();

            // use new map
            this.mWidth = map.getWidth();
            this.mHeight = map.getHeight();
            this.chx0 = this.chx = (Desktop.width - crosshairSize) >> 1;
            this.chy0 = this.chy = (Desktop.height - crosshairSize) >> 1;
            this.x = this.y = 0;
            this.gx = this.gy = 0;
            if (Config.oneTileScroll) {
                this.chx = 0 - crosshairSize2;
                this.chy = 0 - crosshairSize2;
            }
            // ~

            // restore position on map
            if (isDefaultMap(map)) {
                final int x = Config.x;
                final int y = Config.y;
                if (x > -1 && y > -1 && x < mWidth && y < mHeight) {
                    setPosition(new Position(x, y));
                }
            }

            // update scale
            calculateScale();

            // update trail
            calculateTrail();
        }
    }

    void setCourse(float course) {
        this.course = course;
    }

    void setNavigationCourse(float course) {
        this.course2 = course;
    }

    /**
     * Returns crosshair position.
     */
    Position getPosition() {
        position.setXy(x + chx + crosshairSize2, y + chy + crosshairSize2);
//        Position p = new Position(x + chx + crosshairSize2, y + chy + crosshairSize2);

//#ifdef __LOG__
//        if (log.isEnabled()) log.debug(p.toString());
//#endif

//        return p;
        return position;
    }

/*
    public boolean _setPosition(Position p) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("move to " + p + ", current position is " + getPosition());
//#endif

        boolean dirty = false;
        int x = p.getX();
        int y = p.getY();

        int direction;

        int dx = x - (Desktop.width >> 1) - this.x;
        if (dx > 0) {
            direction = Canvas.RIGHT;
        } else {
            direction = Canvas.LEFT;
        }

        int absDx = Math.abs(dx);
        for (int i = absDx; --i >= 0; ) {
            dirty |= scroll(direction);
        }

        int dy = y - (Desktop.height >> 1) - this.y;
        if (dy > 0) {
            direction = Canvas.DOWN;
        } else {
            direction = Canvas.UP;
        }

        int absDy = Math.abs(dy);
        for (int i = absDy; --i >= 0; ) {
            dirty |= scroll(direction);
        }

        chx = x - this.x - crosshairSize2;
        chy = y - this.y - crosshairSize2;

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("move made, dirty? " + dirty + ";current position " + this.x + "," + this.y + "; dirty = " + dirty + "; crosshair requested at " + x + "-" + y + " -> screen position at " + chx + "-" + chy);
//#endif

        return dirty;
    }
*/

    boolean setPosition(Position p) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("move to " + p + ", current position is " + getPosition());
//#endif

        final int x = p.getX();
        final int y = p.getY();

        boolean dirty = false;
        int direction;

        int dx = x - getPosition().getX();
        if (dx > 0) {
            direction = Canvas.RIGHT;
        } else {
            direction = Canvas.LEFT;
        }

//        for (int i = Math.abs(dx); --i >= 0; ) {
//            dirty |= scroll(direction);
//        }
        dirty = scroll(direction, Math.abs(dx)) || dirty;

        int dy = y - getPosition().getY();
        if (dy > 0) {
            direction = Canvas.DOWN;
        } else {
            direction = Canvas.UP;
        }

//        for (int i = Math.abs(dy); --i >= 0; ) {
//            dirty |= scroll(direction);
//        }
        dirty = scroll(direction, Math.abs(dy)) || dirty;

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("move made, dirty? " + dirty + ";current position " + this.x + "," + this.y + "; dirty = " + dirty + "; crosshair requested at " + x + "-" + y + " -> screen position at " + chx + "-" + chy);
//#endif

        return dirty;
    }

    boolean scroll(final int direction, final int steps) {
        int mHeight = this.mHeight;
        int mWidth = this.mWidth;
        int x0 = 0;
        int y0 = 0;

        final boolean ots = Config.oneTileScroll;

        // 1-tile scrolling?
        if (ots) {

            // locals
            Slice slice = null;
            final Position p = getPosition();
            final int px = p.getX();
            final int py = p.getY();
            
            // first look in current set
            if (slice == null) {
                final Vector slices = this.slices;
                for (int i = slices.size(); --i >= 0; ) {
                    final Slice s = (Slice) slices.elementAt(i);
                    if (s.isWithin(px, py)) {
                        slice = s;
                        break;
                    }
                }
            }

            // next try map (we may have no map on start, so be careful)
            if (slice == null && this.map != null) {

                // find slice
                slice = this.map.getSlice(px, py);

                // something is wrong
                if (slice == null) {
                    throw new IllegalStateException("No slice for position " + p);
                }
            }

            // adjust boundaries for 1-tile scrolling
            if (slice != null) {
                mWidth = slice.getWidth();
                mHeight = slice.getHeight();
                x0 = slice.getX();
                y0 = slice.getY();
            }
        }

        final int dWidth = Desktop.width;
        final int dHeight = Desktop.height;
        final int crosshairSize2 = this.crosshairSize2;

        boolean dirty = false;

        for (int i = steps; i-- > 0; ) 

        switch (direction) {
            case Canvas.UP:
                if (chy > chy0) {
                    chy--;
                    dirty = true;
                } else if (y > y0) {
                    y--;
                    dirty = true;
                } else if (chy > 0 - crosshairSize2) {
                    chy--;
                    dirty = true;
                } else if (ots) {
                    if (y0 > 0) {
                        y = y0 - dHeight - 1;
                        if (y < 0) y = 0;
                        chy = dHeight - crosshairSize2 - 1;
                        dirty = true;
                    }
                }
                break;
            case Canvas.LEFT:
                if (chx > chx0) {
                    chx--;
                    dirty = true;
                } else if (x > x0) {
                    x--;
                    dirty = true;
                } else if (chx > 0 - crosshairSize2) {
                    chx--;
                    dirty = true;
                } else if (ots) {
                    if (x0 > 0) {
                        x = x0 - dWidth - 1;
                        if (x < 0) x = 0;
                        chx = dWidth - crosshairSize2 - 1;
                        dirty = true;
                    }
                }
                break;
            case Canvas.RIGHT:
                if (chx < chx0) {
                    chx++;
                    dirty = true;
                } else if (x - x0 + dWidth < mWidth - 1) {
                    x++;
                    dirty = true;
                } else if (chx < dWidth - 1 - crosshairSize2 - 1) {
                    chx++;
                    dirty = true;
                } else if (ots) {
                    if (x0 + mWidth < this.mWidth) {
                        x = x0 + mWidth;
                        chx = 0 - crosshairSize2;
                        dirty = true;
                    }
                }
                break;
            case Canvas.DOWN:
                if (chy < chy0) {
                    chy++;
                    dirty = true;
                } else if (y - y0 + dHeight < mHeight - 1) {
                    y++;
                    dirty = true;
                } else if (chy < dHeight - 1 - crosshairSize2 - 1) {
                    chy++;
                    dirty = true;
                } else if (ots) {
                    if (y0 + mHeight < this.mHeight) {
                        y = y0 + mHeight;
                        chy = 0 - crosshairSize2;
                        dirty = true;
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("Internal error - weird direction");
        }

        return dirty;
    }

    boolean move(int x, int y) {
        boolean dirty = false;

        if (x == -1 && y == -1) {
            gx = gy = 0;
        } else {
            if (gx != 0 || gy != 0) {
                final int dx = x - gx;
                final int dy = y - gy;
                if (dx < 0) {
                    dirty |= scroll(Canvas.RIGHT, -dx);
                } else if (dx > 0) {
                    dirty |= scroll(Canvas.LEFT, dx);
                }
                if (dy < 0) {
                    dirty |= scroll(Canvas.DOWN, -dy);
                } else if (dy > 0) {
                    dirty |= scroll(Canvas.UP, dy);
                }
            }
            gx = x;
            gy = y;
        }

        return dirty;
    }

    void starTick() {
        if (wptPositions != null) {
            star++;
        }
    }

    void initRoute(Position[] positions) {
        /* synchronized to avoid race condition with render() */
        synchronized (this) {
            this.wptPositions = null;
            this.wptStatuses = null;
            if (positions != null) {
                this.wptPositions = positions;
                this.wptStatuses = new byte[positions.length];
            }
        }
        this.star = this.ci = this.li = 0;
    }

    void setRoute(Position[] positions) {
        /* synchronized to avoid race condition with render() */
        synchronized (this) {
            this.wptPositions = null;
            this.wptPositions = positions;
            if (positions != null) { // use new route
                if (this.wptStatuses != null && this.wptStatuses.length != positions.length) {
                    final byte[] newStatuses = new byte[positions.length];
                    System.arraycopy(this.wptStatuses, 0, newStatuses, 0, this.wptStatuses.length);
                    this.wptStatuses = null;
                    this.wptStatuses = newStatuses;
                }
            }
        }
    }

    void setPoiStatus(int idx, byte status) {
        wptStatuses[idx] = status;
    }

    char boundsHit() {
        final int crosshairSize2 = this.crosshairSize2;
        char result = ' ';
        if (chx + crosshairSize2 == 0) {
            result = 'W';
        } else if (chx + crosshairSize2 >= Desktop.width - 2) {
            result = 'E';
        } else if (chy + crosshairSize2 == 0) {
            result = 'N';
        } else if (chy + crosshairSize2 >= Desktop.height - 2) {
            result = 'S';
        }

        return result;
    }

    void reset() {
        lllast = xylast = pflast = llcount = xycount = 0;
        accDist = courseDeviation = 0F;
    }

    /*
     * TODO reuse with GpxTracklog
     */
    void locationUpdated(Location location) {

        // got fix?
        if (location.getFix() > 0) {

            final QualifiedCoordinates coords = location.getQualifiedCoordinates();
            QualifiedCoordinates qc = null;

            // in-trail?
            if (llcount > 0) {

                // add distance increment
                accDist += coords.distance(refQc);
                final float r = accDist;

                // course deviation-based decision when moving faster than 3.6 km/h
                if (location.isSpeedValid() && location.getSpeed() > 1F) {

                    // get course
                    final float course = location.getCourse();

                    // is course valid?
                    if (!Float.isNaN(course)) {

                        // calc deviation
                        float diff = course - refCourse;
                        if (diff > 180F) {
                            diff -= 360F;
                        } else if (diff < -180F) {
                            diff += 360F;
                        }
                        courseDeviation += diff;
                        refCourse = course;

                        // make decision
                        final float absCourseDev = Math.abs(courseDeviation);
                        final boolean doLog = (absCourseDev >= 60F && r > 25F) || (absCourseDev >= 45F && r >= 50) || (absCourseDev >= 15F && r >= 250) || (absCourseDev >= 5 && r >= 750);
                        if (doLog) {
//#ifdef __LOG__
                            if (log.isEnabled()) log.debug("moving; dist = " + r + "; course dev = " + courseDeviation);
//#endif
                            qc = refQc;
                        }
                    }

                } else { // dist-based decision
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("not moving; dist = " + r);
//#endif
                    qc = r >= 50 ? coords : null;
                }

            } else {

//#ifdef __LOG__
                if (log.isEnabled()) log.debug("first position");
//#endif
                // log first position
                qc = coords;

            }

            // got trail point?
            if (qc != null) {
                accDist = courseDeviation = 0F;
                appendToTrail(qc);
            }

            // ref (~ last)
            QualifiedCoordinates.releaseInstance(refQc);
            refQc = coords._clone();
        }
    }

    void appendToTrail(QualifiedCoordinates coords) {
        // local refs for faster access
        final QualifiedCoordinates[] arrayLL = trailLL;
        final Position[] arrayXY = trailXY;
        final short[] arrayPF = trailPF;
        int lllast = this.lllast;
        int xylast = this.xylast;
        int pflast = this.pflast;

        // properly append
        if (arrayLL[lllast] != null) {
            QualifiedCoordinates.releaseInstance(arrayLL[lllast]);
            arrayLL[lllast] = null; // gc hint
        }
        if (arrayXY[xylast] != null) {
            Position.releaseInstance(arrayXY[xylast]);
            arrayXY[xylast] = null; // gc hint
        }

        // append to LL array
        final QualifiedCoordinates cloned = coords._clone();
        arrayLL[lllast] = cloned;
        if (++lllast == MAX_TRAJECTORY_LENGTH) {
            lllast = 0;
        }

        /* synchronized to avoid race condition with setMap() */
        synchronized (this) {
            final Map map = this.map;
            if (map != null) {

                // how many XY is missing
                int N = lllast - xylast;
                if (N < 0) {
                    N = MAX_TRAJECTORY_LENGTH + N;
                }

                // calculate missing XYs
                do {
                    arrayXY[xylast] = map.transform(arrayLL[xylast])._clone();
                    if (++xylast == MAX_TRAJECTORY_LENGTH) {
                        xylast = 0;
                    }
                    if (++xycount > MAX_TRAJECTORY_LENGTH) {
                        xycount = MAX_TRAJECTORY_LENGTH;
                    }
                } while (--N > 0);

                // how many PF is missing
                if (xycount > 1) {
                    N = xylast - pflast - 1;
                    if (N < 0) {
                        N = MAX_TRAJECTORY_LENGTH + N;
                    }

                    // calculate missing PFs
                    do {
                        int pfnext = pflast + 1;
                        if (pfnext == MAX_TRAJECTORY_LENGTH) {
                            pfnext = 0;
                        }
                        arrayPF[pflast] = updatePF(Config.trailThick,
                                                   arrayXY[pflast], arrayXY[pfnext]);
                        if (++pflast == MAX_TRAJECTORY_LENGTH) {
                            pflast = 0;
                        }
                    } while (--N > 0);
                }
            }
        }

        // update members
        this.lllast = lllast;
        this.xylast = xylast;
        this.pflast = pflast;
        if (++llcount > MAX_TRAJECTORY_LENGTH) {
            llcount = MAX_TRAJECTORY_LENGTH;
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("count = " + llcount);
//#endif
    }
    
    void render(final Graphics graphics) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("render");
//#endif
        // local refs
        final int crosshairSize = this.crosshairSize;
        final int crosshairSize2 = this.crosshairSize2;

        /* synchronized to avoid race condition with ensureSlices() */
        synchronized (this) {
         
            // local ref for faster access
            final Vector slices = this.slices;

            // project slices to window
            for (int N = slices.size(), i = 0; i < N; i++) {
                drawSlice(graphics, /*clip, */(Slice) slices.elementAt(i));
            }

        } // ~synchronized

        // paint route/pois/wpt
        if (wptPositions != null) {
            drawNavigation(graphics);
        }

        // draw trajectory
        if (Config.trailOn && xycount > 1) {
            drawTrail(graphics);
        }

        // paint crosshair
        if (Config.S60renderer) { // S60 renderer
            graphics.setClip(chx, chy, crosshairSize, crosshairSize);
            graphics.drawImage(NavigationScreens.crosshairs,
                               chx - ci * crosshairSize, chy,
                               Graphics.TOP | Graphics.LEFT);
            graphics.setClip(0, 0, Desktop.width, Desktop.height);
        } else {
            graphics.drawRegion(NavigationScreens.crosshairs,
                                ci * crosshairSize, 0, crosshairSize, crosshairSize,
                                Sprite.TRANS_NONE,
                                chx, chy, Graphics.TOP | Graphics.LEFT);
        }

        // paint course
        if (!Float.isNaN(course)) {
            NavigationScreens.drawArrow(NavigationScreens.ARROW_COURSE,
                                        graphics, course,
                                        chx + crosshairSize2,
                                        chy + crosshairSize2,
                                        Graphics.TOP | Graphics.LEFT);
        }

        // paint navigation course
        if (!Float.isNaN(course2)) {
            NavigationScreens.drawArrow(NavigationScreens.ARROW_NAVI,
                                        graphics, course2,
                                        chx + crosshairSize2,
                                        chy + crosshairSize2,
                                        Graphics.TOP | Graphics.LEFT);
        }

        // paint scale
        if (Config.osdScale && sInfoLength > 0) {
            drawScale(graphics);
        }
    }

    private static boolean isDefaultMap(final Map map) {
        if (map == null) {
            return false;
        }

        final String mapPath = Config.mapPath;
        if (mapPath.equals(Config.defaultMapPath)) {
            final String mapName = map.getName();
            if (mapName == null) {
                return mapPath.equals(map.getPath());
            } else if (mapPath.length() == 0) {
                return "Default".equals(mapName);
            } else {
                return mapPath.endsWith(mapName);
            }
        }

        return false;
    }

    private void drawNavigation(final Graphics graphics) {
        // hack! setup graphics for waypoints
        final int color = graphics.getColor();
        graphics.setFont(Desktop.fontWpt);

        // local ref for faster access
        final Position[] positions = this.wptPositions;
        final byte[] statuses = this.wptStatuses;

        // draw polyline
        if (Desktop.routeDir != 0) {

            // line color and style
            if (Config.routeLineStyle) {
                graphics.setStrokeStyle(Graphics.DOTTED);
            }
            graphics.setColor(Config.COLORS_16[Config.routeColor]);

            // draw route as line
            final int thickness = Config.routeThick;
            final int w = Desktop.width;
            final int h = Desktop.height;
            final int x = this.x;
            final int y = this.y;
            Position p0 = null;
            for (int i = positions.length; --i >= 0; ) {
                final Position p1 = positions[i];
                if (p1 != null) {
                    if (p0 != null) {
                        final int x0 = p0.getX() - x;
                        final int y0 = p0.getY() - y;
                        final int x1 = p1.getX() - x;
                        final int y1 = p1.getY() - y;

                        // bounding box check first
                        final boolean xIsOff = (x0 < 0 && x1 < 0) || (x0 >= w && x1 >= w);
                        final boolean yIsOff = (y0 < 0 && y1 < 0) || (y0 >= h && y1 >= h);
                        if (!xIsOff && !yIsOff) {

                            // draw segment
                            drawLineSegment(graphics, x0, y0, x1, y1, updatePF(thickness, p0, p1), w, h);
                        }
                    }
                    p0 = p1;
                }
            }

            // restore line style
            if (Config.routeLineStyle) {
                graphics.setStrokeStyle(Graphics.SOLID);
            }
        }

        // POI name/desc color
        graphics.setColor(0x00404040);

        // active wpt index
        final int wptIdx = Desktop.wptIdx;

        // draw POIs
        if (Desktop.routeDir != 0 || Desktop.showall) {
            for (int i = positions.length; --i >= 0; ) {
                if (positions[i] != null) {
                    byte status = statuses[i];
                    if (status == WPT_STATUS_VOID) {
                        if (i < wptIdx) {
                            status = WPT_STATUS_MISSED;
                        } else if (i == wptIdx) {
                            continue; // skip for now
                        }
                    }
                    drawPoi(graphics, positions[i], status, i);
                }
            }
        }

        // draw current POI/WPT
        if (wptIdx > -1) {

            // setup color
            graphics.setColor(0);

            // draw current wpt last
            drawPoi(graphics, positions[wptIdx], WPT_STATUS_CURRENT, wptIdx);
        }

        // hack! restore graphics
        graphics.setColor(color);
        graphics.setFont(Desktop.font);
    }

    private void drawScale(final Graphics graphics) {
        // local references for faster access
        final int h = Desktop.height;
        final int x0 = scaleDx;
        final int x1 = scaleDx + scaleLength;

        // scale
        if (!Config.osdNoBackground) {
            graphics.drawImage(Desktop.barScale, x0 + 3 - 2, h - Desktop.osd.bh - 2, Graphics.TOP | Graphics.LEFT);
        }
        graphics.setColor(0);
        graphics.drawLine(x0, h - 4, x0, h - 2);
        graphics.drawLine(x0, h - 3, x1, h - 3);
        graphics.drawLine(x1, h - 4, x1, h - 2);
        graphics.drawChars(sInfo, 0, sInfoLength,
                           x0 + 3, h - Desktop.osd.bh - 2,
                           Graphics.LEFT | Graphics.TOP);
    }

    private void drawPoi(final Graphics graphics, final Position position,
                         final byte status, final int idx) {
        final int x = position.getX() - this.x;
        final int y = position.getY() - this.y;

        // on screen?
        if (x > 0 && x < Desktop.width && y > 0 && y < Desktop.height) {

            final boolean current = status == WPT_STATUS_CURRENT;
            final boolean showtext = current || (li % 2 > 0/* && Desktop.scrolls < 5*/);

            // draw point
            if (current) {
                NavigationScreens.drawWaypoint(graphics, x, y,
                                               Graphics.TOP | Graphics.LEFT);
            } else if (Config.routePoiMarks) {
                NavigationScreens.drawPOI(graphics, status, x, y,
                                          Graphics.TOP | Graphics.LEFT);
            }

            // draw text (either label or comment/description)
            if (showtext) {
                final Waypoint wpt = (Waypoint) Desktop.wpts.elementAt(idx);
                String text;
                if (li % 4 < 2) {
                    text = wpt.toString(); // either GPX or GC name
                } else {
                    text = wpt.getComment();
                    if (text == null) {
                        text = wpt.toString(); // either GPX or GC name 
                    }
                }
                if (text != null) {
                    final int fh = Desktop.barWpt.getHeight();
                    final int bwMax = Desktop.barWpt.getWidth();
                    int bw = Desktop.fontWpt.stringWidth(text) + 2;
                    if (bw > bwMax) {
                        bw = bwMax;
                    }
                    // TODO S60 renderer path
                    graphics.drawRegion(Desktop.barWpt, 0, 0, bw, fh,
                                        Sprite.TRANS_NONE, x + 3, y - fh - 3,
                                        Graphics.TOP | Graphics.LEFT);
                    graphics.drawString(text, x + 3 + 1, y - fh - 3 - 1,
                                        Graphics.TOP | Graphics.LEFT);
                }
            }
        }
    }

    private void drawSlice(final Graphics graphics, final Slice slice) {
        final Image img = slice.getImage();
        if (img == null) {
            return;
        }

        final int m_x0 = slice.getX();
        final int m_y0 = slice.getY();
        final int slice_w = slice.getWidth();
        final int slice_h = slice.getHeight();

        final int x_src;
        int w;
        final int x_dest;
        if (x > m_x0) {
            x_src = x - m_x0;
            w = slice_w - x_src;
            x_dest = 0/*gx*/;
        } else {
            x_src = 0;
            w = x + Desktop.width - m_x0;
            x_dest = /*gx + */m_x0 - x;
        }
        if (w > (Desktop.width - x_dest)) w = Desktop.width - x_dest;
        if (w > slice_w) w = slice_w;

        final int y_src;
        int h;
        final int y_dest;
        if (y > m_y0) {
            y_src = y - m_y0;
            h = slice_h - y_src;
            y_dest = 0/*gy*/;
        } else {
            y_src = 0;
            h = y + Desktop.height - m_y0;
            y_dest = /*gy + */m_y0 - y;
        }
        if (h > (Desktop.height - y_dest))
            h = Desktop.height - y_dest;
        if (h > slice_h)
            h = slice_h;

        if (w > 0 && h > 0) {
            if (img != Slice.NO_IMAGE) {
                if (Config.S60renderer) { // S60 renderer
                    graphics.drawImage(img,
                                       - x_src + x_dest,
                                       - y_src + y_dest,
                                       Graphics.TOP | Graphics.LEFT);
                } else {
                    graphics.drawRegion(img,
                                        x_src, y_src, w, h,
                                        Sprite.TRANS_NONE,
                                        x_dest, y_dest,
                                        Graphics.TOP | Graphics.LEFT);
                }
            } else {
                graphics.setColor(0x0);
                graphics.fillRect(x_dest, y_dest, w, h);
                graphics.setColor(0x00404040);
                graphics.drawRect(x_dest, y_dest, w, h);
                graphics.drawString(slice.toString(),
                                    - x_src + x_dest + 2, // padding
                                    - y_src + y_dest + 2, // padding 
                                    Graphics.TOP | Graphics.LEFT);
            }
        }
    }

    private void drawTrail(final Graphics graphics) {
        final Position[] arrayXY = trailXY;
        final short[] arrayPF = trailPF;
        final int count = this.xycount;
        final int x = this.x;
        final int y = this.y;
        final int w = Desktop.width;
        final int h = Desktop.height;
        int idx0 = this.xylast;
        if (idx0 >= count) {
            idx0 = 0;
        }

        // set color and style
        final int color = graphics.getColor();
        graphics.setColor(Config.COLORS_16[Config.trailColor]);

        // draw polyline
        for (int i = count - 1; --i >= 0; ) {
            int idx1 = idx0 + 1;
            if (idx1 >= count) {
                idx1 = 0;
            }
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("draw from " + idx0 + "(" + arrayXY[idx0] + ") to " + idx1 + " (" + arrayXY[idx1] + ")");
//#endif
            final Position p0 = arrayXY[idx0];
            final Position p1 = arrayXY[idx1];
            final int x0 = p0.getX() - x;
            final int y0 = p0.getY() - y;
            final int x1 = p1.getX() - x;
            final int y1 = p1.getY() - y;
            final boolean xIsOff = (x0 < 0 && x1 < 0) || (x0 >= w && x1 >= w);
            final boolean yIsOff = (y0 < 0 && y1 < 0) || (y0 >= h && y1 >= h);

            // bounding box check first
            if (!xIsOff && !yIsOff) {
                
                // draw segment
                drawLineSegment(graphics, x0, y0, x1, y1, arrayPF[idx0], w, h);
            }

            idx0 = idx1;
        }

        // draw line from last "gpx" point to current position
        if (!Desktop.browsing) {
            final Position p0 = arrayXY[idx0];
            final int x0 = p0.getX() - x;
            final int y0 = p0.getY() - y;
            final int x1 = chx + crosshairSize2;
            final int y1 = chy + crosshairSize2;
            drawLineSegment(graphics, x0, y0, x1, y1, updatePF(Config.trailThick, x1 - x0, y1 - y0), w, h);
        }
        
        // restore color and style
        graphics.setColor(color);
    }

    private static void drawLineSegment(final Graphics graphics,
                                        int x0, int y0,
                                        int x1, int y1,
                                        final short flags,
                                        final int w, final int h) {
        final boolean p0IsWithin = x0 >=0 && x0 < w && y0 >=0 && y0 < h;
        final boolean p1IsWithin = x1 >=0 && x1 < w && y1 >=0 && y1 < h;
        if (!p0IsWithin || !p1IsWithin) {
            int ps = -1, pe = -1;
            if (x0 == x1) {
                ps = x0 << 16;
                pe = x0 << 16 | (h - 1);
            } else if (y0 == y1) {
                ps = y0;
                pe = (w - 1) << 16 | y0;
            } else {
                final float k = (float)(y1 - y0) / (x1 - x0);
                final float px0y = (-k * x0 + y0); // q
                final float py0x = (-px0y / k);
                final float pxwy = (k * (w - 1) + px0y);
                final float pyhx = ((h - 1 - px0y) / k);
                if (px0y >= 0 && px0y <= (h - 1)) {
                    ps = (int)px0y;
                }
                if (pxwy >= 0 && pxwy <= (h - 1)) {
                    final int value = (w - 1) << 16 | (int)pxwy;
                    if (ps < 0) {
                        ps = value;
                    } else {
                        pe = value;
                    }
                }
                if (py0x >= 0 && py0x <= (w - 1)) {
                    final int value = (int)py0x << 16;
                    if (ps < 0) {
                        ps = value;
                    } else if (pe < 0) {
                        if (value != ps) {
                            pe = value;
                        }
                    }
                }
                if (pyhx >= 0 && pyhx <= (w - 1)) {
                    final int value = (int)pyhx << 16 | (h - 1);
                    if (ps < 0) {
                        ps = value;
                    } else if (pe < 0) {
                        pe = value;
                        if (value != ps) {
                            pe = value;
                        }
                    }
                }
            }
            if (pe < 0) pe = ps;
            if (!p0IsWithin && !p1IsWithin) {
                x0 = ps >>> 16;
                y0 = ps & 0x0000ffff;
                x1 = pe >>> 16;
                y1 = pe & 0x0000ffff;
            } else if (p0IsWithin) {
                final int xa = ps >>> 16;
                final int xb = pe >>> 16;
                final int ya = ps & 0x0000ffff;
                final int yb = pe & 0x0000ffff;
                if (x1 < x0) {
                    if (xa < xb) {
                        x1 = xa;
                        y1 = ya;
                    } else {
                        x1 = xb;
                        y1 = yb;
                    }
                } else if (x1 > x0) {
                    if (xa > xb) {
                        x1 = xa;
                        y1 = ya;
                    } else {
                        x1 = xb;
                        y1 = yb;
                    }
                } else {
                    if (y1 > y0) {
                        y1 = h;
                    } else {
                        y1 = 0;
                    }
                }
            } else if (p1IsWithin) {
                final int xa = ps >>> 16;
                final int xb = pe >>> 16;
                final int ya = ps & 0x0000ffff;
                final int yb = pe & 0x0000ffff;
                if (x1 < x0) {
                    if (xa < xb) {
                        x0 = xb;
                        y0 = yb;
                    } else {
                        x0 = xa;
                        y0 = ya;
                    }
                } else if (x1 > x0) {
                    if (xa < xb) {
                        x0 = xa;
                        y0 = ya;
                    } else {
                        x0 = xb;
                        y0 = yb;
                    }
                } else {
                    if (y1 > y0) {
                        y0 = 0;
                    } else {
                        y0 = h;
                    }
                }
            }
        }
        clipLineSegment(graphics, x0, y0, x1, y1, flags);
    }

    private static void clipLineSegment(final Graphics graphics,
                                        final int x0, final int y0,
                                        final int x1, final int y1,
                                        final short flags) {

        final int thick = flags >>> 8;
        if (thick > 1) {
            final int t0x0, t0y0, t0x1, t0y1, t0x2, t0y2;
            final int t1x0, t1y0, t1x1, t1y1, t1x2, t1y2;
            final int th2 = thick / 2;
            if ((flags & 0x01) != 0) {
                t0x0 = x0 - th2;
                t0y0 = y0;
                t0x1 = x0 + th2;
                t0y1 = y0;
                t0x2 = x1 - th2;
                t0y2 = y1;
                t1x0 = x0 + th2;
                t1y0 = y0;
                t1x1 = x1 - th2;
                t1y1 = y1;
                t1x2 = x1 + th2;
                t1y2 = y1;
            } else if ((flags & 0x02) != 0) {
                t0x0 = x0;
                t0y0 = y0 - th2;
                t0x1 = x0;
                t0y1 = y0 + th2;
                t0x2 = x1;
                t0y2 = y1 - th2;
                t1x0 = x0;
                t1y0 = y0 + th2;
                t1x1 = x1;
                t1y1 = y1 - th2;
                t1x2 = x1;
                t1y2 = y1 + th2;
            } else {
                t0x0 = t0y0 = t0x1 = t0y1 = t0x2 = t0y2 = 0;
                t1x0 = t1y0 = t1x1 = t1y1 = t1x2 = t1y2 = 0;
            }
            graphics.fillTriangle(t0x0, t0y0, t0x1, t0y1, t0x2, t0y2);
            graphics.fillTriangle(t1x0, t1y0, t1x1, t1y1, t1x2, t1y2);
        } else {
            graphics.drawLine(x0, y0, x1, y1);
        }
    }

    int nextCrosshair() {
        if (star % 2 == 0) {
            li++;
        } else {
            ci++;
            if ((ci * crosshairSize) == NavigationScreens.crosshairs.getWidth()) {
                ci = 0;
            }
        }
        return Desktop.MASK_SCREEN;
    }

/*
    public int[] getClip() {
        clip[0] = chx;
        clip[1] = chy;

        return clip;
    }
*/

    boolean ensureSlices() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("ensure slices from map " + map);
//#endif

        // gonna-release flag
        boolean releasing = false;

        // have-all-images flag
        boolean gotAll = true;

        /* synchronized to avoid race condition with render() */
        synchronized (this) {

            // local ref to temp collection
            final Vector oldSlices = slices;
            final Vector newSlices = slices2;

            // assertion
            if (!newSlices.isEmpty()) {
                throw new IllegalStateException("New tiles collection not empty");
            }

            // find needed slices ("row by row")
            int _x = x;
            int _y = y;
            final int xmax = x + Desktop.width > mWidth ? mWidth : x + Desktop.width;
            final int ymax = y + Desktop.height > mHeight ? mHeight : y + Desktop.height;
            while (_y < ymax) {
                int _l = ymax; // bottom for current "line"
                while (_x < xmax) {
                    final Slice s = ensureSlice(_x, _y, oldSlices, newSlices);
                    if (s != null) {
                        _x = s.getX() + s.getWidth();
                        _l = s.getY() + s.getHeight();
                        if (s.getImage() == null) {
//#ifdef __LOG__
                            if (log.isEnabled()) log.debug("image missing for slice " + s);
//#endif
                            gotAll = false;
                        }
                    } else {
                        throw new IllegalStateException("Out of map - no tile for " + _x + "-" + _y);
                    }
                }
                _y = _l; // next "line" of slices
                _x = x;  // start on "line"
            }

            /*
             * free images for slices no longer used in current view
             */

            // release slices images we will no longer use
            for (int i = oldSlices.size(); --i >= 0; ) {
                final Slice slice = (Slice) oldSlices.elementAt(i);
                if (!newSlices.contains(slice)) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("release image in " + slice);
//#endif
                    slice.setImage(null);
                    releasing = true;
                }
//#ifdef __LOG__
                  else {
                    if (log.isEnabled()) log.debug("reuse slice in current set; " + slice);
                }
//#endif
            }

            // gc cleanup
            oldSlices.removeAllElements();

            // exchange vectors and do cleanup
/*
            final Vector v = slices;
            slices = slices2;
            slices2 = v;
            slices2.removeAllElements();
*/
            slices = newSlices;
            slices2 = oldSlices;
        } // ~synchronized

        // loading flag
        boolean loading = false;

        // start loading images
        if (!gotAll) {
            loading = map.ensureImages(slices);
        }

        // return the 'loading' flags
        return loading;
    }

    private Slice ensureSlice(final int x, final int y,
                              final Vector oldSlices,
                              final Vector newSlices) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("ensure slice for " + x + "-" + y);
//#endif

        Slice slice = null;

        // look for suitable slice in current set
        for (int i = oldSlices.size(); --i >= 0; ) {
            final Slice s = (Slice) oldSlices.elementAt(i);
            if (s.isWithin(x, y)) {
                slice = s;
                break;
            }
        }

        // next try map slices
        if (slice == null) {
            slice = map.getSlice(x, y);
        }

        // found it?
        if (slice != null) {
            // assertion
            if (!newSlices.contains(slice)) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("add to new slices set: " + slice);
//#endif
                newSlices.addElement(slice);
            } else {
                throw new IllegalStateException("Tile " + slice + " already added for " + x + "-" + y);
            }
        }

        return slice;
    }

    private void calculateScale() {
        // clear
        sInfoLength = 0;

        // have map?
        if (map != null) {
/*
            // use full range - fails for globe maps :-)
            final QualifiedCoordinates[] range = map.getRange();
            double scale = range[0].distance(range[1]) / map.getWidth();
*/
            // use 10% of map width at current Y (lat) for calculation
            final int cy = getPosition().getY();
            QualifiedCoordinates qc0 = map.transform(0, cy);
            QualifiedCoordinates qc1 = map.transform(map.getWidth() / 10, cy);
            double scale = qc0.distance(qc1) / (map.getWidth() / 10);
            QualifiedCoordinates.releaseInstance(qc0);
            QualifiedCoordinates.releaseInstance(qc1);

            // valid scale?
            if (scale > 0F) { // this always true now, isn't it?
                char[] uc = null;
                switch (Config.units) {
                    case Config.UNITS_METRIC: {
                        uc = NavigationScreens.DIST_STR_M;
                    } break;
                    case Config.UNITS_IMPERIAL: {
                        uc = NavigationScreens.DIST_STR_FT;
                        scale /= 0.3048F;
                    } break;
                    case Config.UNITS_NAUTICAL: {
                        uc = NavigationScreens.DIST_STR_FT;
                        scale /= 0.3048F;
                    } break;
                }
                long half = (long) (scale * ((Desktop.width >> 1) - scaleDx));
                if (half >= 10000) {
                    switch (Config.units) {
                        case Config.UNITS_METRIC: {
                            uc = NavigationScreens.DIST_STR_KM;
                            final long m = half % 1000;
                            half /= 1000;
                            scale /= 1000F;
                            if (m > 500) {
                                half++;
                            }
                        } break;
                        case Config.UNITS_IMPERIAL: {
                            uc = NavigationScreens.DIST_STR_MI;
                            final long m = half % 5280;
                            half /= 5280;
                            scale /= 5280F;
                            if (m > 2640) {
                                half++;
                            }
                        } break;
                        case Config.UNITS_NAUTICAL: {
                            uc = NavigationScreens.DIST_STR_NMI;
                            final long m = half % 6076;
                            half /= 6076;
                            scale /= 6076F;
                            if (m > 3038) {
                                half++;
                            }
                        } break;
                    }
                }
                final int grade = ExtraMath.grade(half);
                long guess = (half / grade) * grade;
                if (half - guess > grade / 2) {
                    guess += grade;
                }
                scaleLength = (int) (guess / scale);
                final StringBuffer sb = new StringBuffer(32);
                sb.append(guess).append(uc);
                if (sb.length() > sInfo.length) {
                    throw new IllegalStateException("Scale length = " + sInfoLength);
                }
                sInfoLength = sb.length();
                sb.getChars(0, sInfoLength, sInfo, 0);
            }
        }
    }

    private void calculateTrail() {
        // trail on?
        if (Config.trailOn && llcount != 0) {

            // local ref for faster access
            final Map map = this.map;
            final QualifiedCoordinates[] arrayLL = trailLL;
            final Position[] arrayXY = trailXY;
            final int llcount = this.llcount;
            int tlast = this.lllast - 1; // really 'last'

            // recalc xy and fi
            for (int i = 0; i < llcount; i++) {
                if (++tlast == llcount) {
                    tlast = 0;
                }
                final Position p = map.transform(arrayLL[tlast]);
                if (arrayXY[tlast] != null) {
                    arrayXY[tlast].setXy(p.getX(), p.getY());
                } else {
                    arrayXY[tlast] = p._clone();
                }
            }
        }
    }

    private static short updatePF(final int thick0, final Position p0, final Position p1) {
        return updatePF(thick0, p1.getX() - p0.getX(), p1.getY() - p0.getY());
    }
    
    private static short updatePF(final int thick0, final double dx, final double dy) {
        final int thick = thick0 * 2 + 1;
        double aRad;
        short flags = 0;
        if (dx > 0) {
            aRad = ExtraMath.atan(dy / Math.abs(dx));
            if (Math.abs(aRad) > Math.PI / 4) {
                flags |= 1;
            } else {
                flags |= 2;
            }
            aRad += Math.PI / 2;
        } else if (dx < 0) {
            aRad = ExtraMath.atan(dy / Math.abs(dx));
            if (Math.abs(aRad) > Math.PI / 4) {
                flags |= 1;
            } else {
                flags |= 2;
            }
            aRad = Math.PI * 3 / 2 - aRad;
        } else {
            if (dy > 0) {
                aRad = Math.PI;
            } else if (dy < 0) {
                aRad = 0;
            } else {
                return 0;
            }
            flags |= 1;
        }
        
        final double w = thick + ((Math.sqrt(2 * thick * thick) - thick) * Math.abs(Math.sin(2 * aRad)));
        flags |= (short) (ExtraMath.round(w) << 8);

        return flags;
    }
}
