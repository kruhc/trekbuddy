// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.maps.Slice;
import cz.kruch.track.maps.Map;
import cz.kruch.track.util.ExtraMath;
import cz.kruch.track.util.NakedVector;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.Sprite;

import api.location.Datum;
import api.location.Location;
import api.location.QualifiedCoordinates;
import api.location.ProjectionSetup;

//#define __CLIP__
//-#define __PF__

/**
 * Map viewer.
 *
 * @author kruhc@seznam.cz
 */
final class MapViewer {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("MapViewer");
//#endif

//#if __SYMBIAN__ || __RIM__ || __ANDROID__ || __CN1__
    private static final int MAX_TRAIL_LENGTH = 4096;
//#else
    private static final int MAX_TRAIL_LENGTH = 1024;
//#endif

    public static final byte WPT_STATUS_VOID    = 0;
    public static final byte WPT_STATUS_REACHED = 1;
    public static final byte WPT_STATUS_MISSED  = 2;
    public static final byte WPT_STATUS_CURRENT = 3;

    private int x, y;
    private int chx, chy;
    private int chx0, chy0;
    private int crosshairSize, crosshairSize2;

    private int gx, gy;
    private int sy;
    
    private final Position position;

    private int scaleDx/*, dy*/;
    private int scaleLength;
    private final char[] sInfo;
    private int sInfoLength;

    private Map map;
    private NakedVector slices, slices2; // slices2 for reuse during switch

    private float course, course2;

    private Position[] wptPositions;
    private byte[] wptStatuses;
    private NakedVector trkRanges;

    private int star;

    private final QualifiedCoordinates[] trailLL;
    private final Position[] trailXY;
//#if !__ANDROID__ && !__CN1__
//#ifdef __PF__
    private final short[] trailPF;
//#endif
//#endif
    private QualifiedCoordinates refQc;
    private float accDist, refCourse, courseDeviation;
    private int lllast, xylast, pflast, llcount, xycount;

    private int ci, li;

    MapViewer() {
        this.crosshairSize = NavigationScreens.crosshairs.getHeight();
        this.crosshairSize2 = this.crosshairSize >> 1;
        this.position = new Position(0, 0);
        this.slices = new NakedVector(4, 4);
        this.slices2 = new NakedVector(4, 4);
        this.sInfo = new char[32];
        this.course = this.course2 = Float.NaN;
        this.trailLL = new QualifiedCoordinates[MAX_TRAIL_LENGTH];
        this.trailXY = new Position[MAX_TRAIL_LENGTH];
//#if !__ANDROID__ && !__CN1__
//#ifdef __PF__
        this.trailPF = new short[MAX_TRAIL_LENGTH];
//#endif
//#endif
    }

    public void sizeChanged(int w, int h) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("size changed");
//#endif

        // reset vars
        final Position p = getPosition()._clone();
        this.chx0 = this.chx = (w - crosshairSize) >> 1;
        this.chy0 = this.chy = (h - crosshairSize) >> 1;
        this.x = this.y = 0;
        this.gx = this.gy = 0;
        if (Config.oneTileScroll) {
            this.chx = 0 - crosshairSize2;
            this.chy = 0 - crosshairSize2;
        }

        // restore position
        setPosition(p);

        // scale drawing x offset (same as in HPS)
        final int lineLength = Math.min(w - w / 10, h - h / 10);
        this.scaleDx = (w - lineLength) >> 1;

        // update scale
        calculateScale();
    }

    boolean hasMap() { // @threads ?:map
        return map != null;
    }

    Map getMap() { // @threads ?:map
        return map;
    }

    void setMap(final Map map) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("set map " + map);
//#endif

        /* synchronized to avoid race condition with render() */
        synchronized (this) { // @threads ?:map,slices

            // use new map
            this.map = null; // gc hint
            this.map = map;

        } // ~synchronized

        // new slices collection
        reslice();
        /* slices2 is always empty */
        
        // use new map (if any)
        if (map != null) {

            // update context
            Datum.contextDatum = map.getDatum();
            ProjectionSetup.contextProjection = map.getProjection();

            // use new map
            this.chx0 = this.chx = (Desktop.width - crosshairSize) >> 1;
            this.chy0 = this.chy = (Desktop.height - crosshairSize) >> 1;
            this.x = this.y = 0;
            this.gx = this.gy = 0;
            if (Config.oneTileScroll) {
                this.chx = 0 - crosshairSize2;
                this.chy = 0 - crosshairSize2;
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

        return position;
    }

    boolean setPosition(Position p) {
        if (map == null) {
            return false;
        }
        
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
        dirty = scroll(direction, Math.abs(dx)) || dirty;

        int dy = y - getPosition().getY();
        if (dy > 0) {
            direction = Canvas.DOWN;
        } else {
            direction = Canvas.UP;
        }
        dirty = scroll(direction, Math.abs(dy)) || dirty;

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("move made, dirty? " + dirty + ";current position " + this.x + "," + this.y + "; dirty = " + dirty + "; crosshair requested at " + x + "-" + y + " -> screen position at " + chx + "-" + chy);
//#endif

        return dirty;
    }

    boolean scroll(final int direction, int steps) {
        final int dWidth = Desktop.width;
        final int dHeight = Desktop.height;
        final int crosshairSize2 = this.crosshairSize2;
        final boolean ots = Config.oneTileScroll;

        int mWidth = map.getWidth();
        int mHeight = map.getHeight();
        boolean dirty = false;
        final int x0;
        final int y0;

        // scrolling-specific init
        if (!ots) {

            // absolute base
            x0 = 0;
            y0 = 0;

        } else {

            // locals
            final Position p = getPosition();
            int px = p.getX();
            int py = p.getY();

            // calculate target position
            switch (direction) {
                case Canvas.UP:
                    py -= steps;
                    if (py < 0) {
                        steps += py;
                        py = 0;
                    }
                break;
                case Canvas.LEFT:
                    px -= steps;
                    if (px < 0) {
                        steps += px;
                        px = 0;
                    }
                break;
                case Canvas.RIGHT:
                    px += steps;
                    if (mWidth > 0 && px >= mWidth) {
                        steps = mWidth - 1 - p.getX();
                        px = mWidth - 1;
                    }
                break;
                case Canvas.DOWN:
                    py += steps;
                    if (mHeight > 0 && py >= mHeight) {
                        steps = mHeight - 1 - p.getY();
                        py = mHeight - 1;
                    }
                break;
            }

            // get slice
            final Slice slice = getSliceFor(px, py);

            // adjust boundaries and reposition crosshair on tile change
            if (slice != null) {
                mWidth = scale(slice.getWidth());
                mHeight = scale(slice.getHeight());
                x0 = scale(slice.getX());
                y0 = scale(slice.getY());
                switch (direction) {
                    case Canvas.UP:
                        final int dy = mHeight - dHeight;
                        if (dy >= 0) {
                            if (y0 < y /* tile larger than screen: */ - dy) {
                                y = y0 /* tile larger than screen: */ + dy;
                                chy = dHeight - crosshairSize2 /*- 1*/;
                                steps = mHeight - (py - y0);
                                dirty = true;
                            }
                        } else {
                            if (y0 < y /* tile smaller than screen: */) {
                                y = y0 /* tile smaller than screen: */;
                                chy = dHeight - crosshairSize2 /*- 1*/;
                                steps = mHeight - (py - y0);
                                dirty = true;
                            }
                        }
                    break;
                    case Canvas.DOWN:
                        if (y0 > y) {
                            y = y0;
                            chy = 0 - crosshairSize2;
                            steps = py - y0;
                            dirty = true;
                        }
                    break;
                    case Canvas.LEFT:
                        final int dx = mWidth - dWidth;
                        if (dx >= 0) {
                            if (x0 < x /* tile larger than screen: */ - dx) {
                                x = x0 /* tile larger than screen: */ + dx;
                                chx = dWidth - crosshairSize2 /*- 1*/;
                                steps = mWidth - (px - x0);
                                dirty = true;
                            }
                        } else {
                            if (x0 < x /* tile smaller than screen: */) {
                                x = x0 /* tile smaller than screen: */;
                                chx = dWidth - crosshairSize2 /*- 1*/;
                                steps = mWidth - (px - x0);
                                dirty = true;
                            }
                        }
                    break;
                    case Canvas.RIGHT:
                        if (x0 > x) {
                            x = x0;
                            chx = 0 - crosshairSize2;
                            steps = px - x0;
                            dirty = true;
                        }
                    break;
                }
            } else {
                return false;
            }
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("move made? steps: " + steps);
//#endif

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
                } else if (chx < dWidth /*- 1*/ - crosshairSize2 - 1) {
                    chx++;
                    dirty = true;
                } else if (ots) {
                    if (x0 + mWidth < map.getWidth()) {
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
                } else if (chy < dHeight /*- 1*/ - crosshairSize2 - 1) {
                    chy++;
                    dirty = true;
                } else if (ots) {
                    if (y0 + mHeight < map.getWidth()) {
                        y = y0 + mHeight;
                        chy = 0 - crosshairSize2;
                        dirty = true;
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("Internal error - weird direction");
        }

//#if __SYMBIAN__ || __RIM__ || __ANDROID__ || __CN1__
        getPosition();
        if (Math.abs(sy - position.getY()) >= mHeight * 0.05) {
            sy = position.getY();
            calculateScale();
        }
//#endif        

        return dirty;
    }

    boolean move(final int x, final int y) {
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

    void initRoute(final Position[] positions, final NakedVector ranges,
                   final boolean reset, final int mode) {
        /* synchronized to avoid race condition with render() */
        synchronized (this) { // @threads ?:wpt*
            this.wptPositions = null;
            this.wptStatuses = null;
            this.trkRanges = null;
            if (positions != null) {
                this.wptPositions = positions;
                if (mode == 1) { // navigation
                    this.wptStatuses = new byte[positions.length];
                }
            }
            if (ranges != null) {
                this.trkRanges = ranges;
            }
        }
        if (reset) {
            this.star = this.li = 0;
        }
    }

    void setRoute(final Position[] positions) {
        /* synchronized to avoid race condition with render() */
        synchronized (this) { // @threads ?:wpt*
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

    void setPoiStatus(final int idx, final byte status) {
        /* synchronized to avoid race condition with render() */
        synchronized (this) { // @threads ?:wpt*
            wptStatuses[idx] = status;
        }
    }

    char boundsHit(final int direction) {
        char result = ' ';
        if (map != null) {
            final Position p = getPosition();
            switch (direction) {
                case Canvas.UP:
                    if (p.getY() == 0) result = 'N';
                break;
                case Canvas.LEFT:
                    if (p.getX() == 0) result = 'W';
                break;
                case Canvas.RIGHT:
                    if (p.getX() + 1 >= map.getWidth() - 1) result = 'E'; // TODO workaround, bug is elsewhere
                break;
                case Canvas.DOWN:
                    if (p.getY() + 1 >= map.getHeight() - 1) result = 'S'; // TODO workaround, bug is elsewhere
                break;
            }
        }

        return result;
    }

    void reset() {
        synchronized (this) { // @threads event|render:*
            lllast = xylast = pflast = llcount = xycount = 0;
            accDist = courseDeviation = 0F;
        }
    }

    void reslice() {
        synchronized (this) { // @threads lifecycle|render:slices
//#ifdef __ANDROID__
            final Object[] slicesArray = this.slices.getData();
            for (int i = this.slices.size(); --i >= 0; ) {
                ((Slice) slicesArray[i]).setImage(null);
            }
//#endif
            this.slices = null;
            this.slices = new NakedVector(4, 4);
        }
    }

    /*
     * TODO reuse with GpxTracklog
     */
    void locationUpdated(final Location location) {

        // got fix?
        if (location.getFix() > 0) { // TODO && dt >= 1000

            final QualifiedCoordinates coords = location.getQualifiedCoordinates();
            QualifiedCoordinates qc = null;

            synchronized (this) { // @threads event|render:*

                // in-trail?
                if (llcount > 0) {

                    // add distance increment
                    accDist += coords.distance(refQc);
                    final float r = accDist;

                    // course deviation-based decision when moving faster than 7.2 km/h
                    if (location.isSpeedValid() && location.getSpeed() > 2F) {

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

                    // use as reference
                    refQc.copyFrom(coords);

                } else {

//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("first position");
//#endif
                    // log first position
                    qc = coords;
                    refQc = coords._clone();
                }

                // got trail point?
                if (qc != null) {
                    accDist = courseDeviation = 0F;
                }

            } // ~synchronized

            // append to trail point if got any
            if (qc != null) {
                appendToTrail(qc.getLat(), qc.getLon());
            }
        }
    }

    void appendToTrail(final double lat, final double lon) {
        // create qc
        final QualifiedCoordinates qc = QualifiedCoordinates.newInstance(lat, lon);

        // @threads event|input:*
        synchronized (this) {

            // local refs for faster access
            final QualifiedCoordinates[] arrayLL = trailLL;
            final Position[] arrayXY = trailXY;
//#if !__ANDROID__ && !__CN1__
//#ifdef __PF__
            final short[] arrayPF = trailPF;
//#endif
//#endif
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
            arrayLL[lllast] = qc;
            if (++lllast == MAX_TRAIL_LENGTH) {
                lllast = 0;
            }
//        } // ~synchronized

        /* synchronized to avoid race condition with setMap() */
//        synchronized (this) { // @threads ?:*
            final Map map = this.map;
            if (map != null) {

                // how many XY is missing
                int N = lllast - xylast;
                if (N < 0) {
                    N = MAX_TRAIL_LENGTH + N;
                }

                // calculate missing XYs
                do {
                    arrayXY[xylast] = map.transform(arrayLL[xylast])._clone();
                    if (++xylast == MAX_TRAIL_LENGTH) {
                        xylast = 0;
                    }
                    if (++xycount > MAX_TRAIL_LENGTH) {
                        xycount = MAX_TRAIL_LENGTH;
                    }
                } while (--N > 0);

                // how many PF is missing
                if (xycount > 1) {
                    N = xylast - pflast - 1;
                    if (N < 0) {
                        N = MAX_TRAIL_LENGTH + N;
                    }

                    // calculate missing PFs
                    do {
                        int pfnext = pflast + 1;
                        if (pfnext == MAX_TRAIL_LENGTH) {
                            pfnext = 0;
                        }
//#if !__ANDROID__ && !__CN1__
//#ifdef __PF__
                        arrayPF[pflast] = updatePF(Config.trailThick,
                                                   arrayXY[pflast], arrayXY[pfnext]);
//#endif
//#endif
                        if (++pflast == MAX_TRAIL_LENGTH) {
                            pflast = 0;
                        }
                    } while (--N > 0);
                }
            }
//        } // ~synchronized

            // update members
            this.lllast = lllast;
            this.xylast = xylast;
            this.pflast = pflast;
            if (++llcount > MAX_TRAIL_LENGTH) {
                llcount = MAX_TRAIL_LENGTH;
            }
        } // ~synchronized

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("count = " + llcount);
//#endif
    }
    
    void render(final Graphics graphics) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("render");
//#endif

        // local ref
        final Map map = this.map;

        // clear bg
        if (Config.oneTileScroll || map.getWidth() < Desktop.width || map.getHeight() < Desktop.height || chx != chx0 || chy != chy0) {
            graphics.setColor(0x0);
            graphics.fillRect(0, 0, Desktop.width, Desktop.height);
        } else if (map.isVirtual()) {
            graphics.setColor(map.getBgColor());
            graphics.fillRect(0, 0, Desktop.width, Desktop.height);
/*
        } else {
            graphics.setColor(0x00a0a0a0);
            graphics.fillRect(0, 0, Desktop.width, Desktop.height);
*/
        }

        /* synchronized to avoid race condition with ensureSlices() */
        synchronized (this) { // @threads ?:slices
         
            // project slices to window
            if (!map.isVirtual()) {
                final Object[] slicesArray = this.slices.getData();
                for (int N = this.slices.size(), i = 0; i < N; i++) {
                    drawSlice(graphics, (Slice) slicesArray[i]);
                }
            }

        } // ~synchronized

        // paint route/pois/wpt
        synchronized (this) { // @threads ?:wpt*
            if (wptPositions != null) {
                drawNavigation(graphics);
            }
        }

        // draw trajectory
        synchronized (this) { // @threads ?:?
            if (Config.trailOn && xycount > 1) {
                drawTrail(graphics);
            }
        } // ~synchronized

        // local refs
        final int crosshairSize = this.crosshairSize;
        final int crosshairSize2 = this.crosshairSize2;

        // paint crosshair
        final int csx, csy;
        if (Config.fixedCrosshair) {
            csx = chx0;
            csy = chy0;
        } else {
            csx = chx;
            csy = chy;
        }
//#ifdef __ALT_RENDERER__
        if (Config.S60renderer) { // S60 renderer
//#endif
            graphics.setClip(csx, csy, crosshairSize, crosshairSize);
            graphics.drawImage(NavigationScreens.crosshairs,
                               csx - ci * crosshairSize, csy,
                               Graphics.TOP | Graphics.LEFT);
            graphics.setClip(0, 0, Desktop.width, Desktop.height);
//#ifdef __ALT_RENDERER__
        } else {
            graphics.drawRegion(NavigationScreens.crosshairs,
                                ci * crosshairSize, 0, crosshairSize, crosshairSize,
                                Sprite.TRANS_NONE,
                                csx, csy, Graphics.TOP | Graphics.LEFT);
        }
//#endif        

        // paint course
        final float course = this.course;
        if (!Float.isNaN(course)) {
            NavigationScreens.drawArrow(NavigationScreens.ARROW_COURSE,
                                        graphics, course,
                                        csx + crosshairSize2,
                                        csy + crosshairSize2,
                                        Graphics.TOP | Graphics.LEFT);
        }

        // paint navigation course
        final float course2 = this.course2;
        if (!Float.isNaN(course2)) {
            NavigationScreens.drawArrow(NavigationScreens.ARROW_NAVI,
                                        graphics, course2,
                                        csx + crosshairSize2,
                                        csy + crosshairSize2,
                                        Graphics.TOP | Graphics.LEFT);
        }

        // paint scale
        if (Config.osdScale && sInfoLength > 0) {
            drawScale(graphics);
        }
    }

    private void drawNavigation(final Graphics graphics) {
        // hack! setup graphics for waypoints
        final int color = graphics.getColor();
        graphics.setFont(Desktop.fontWpt);

        // local ref for faster access
        final Position[] positions = this.wptPositions;
        final byte[] statuses = this.wptStatuses;
        final int NR = this.trkRanges == null ? 0 : this.trkRanges.size();
        final int eow = NR > 0 ? ((int[])this.trkRanges.elementAt(0))[0] : positions.length;
        final int eop = Config.trackPoiMarks ? positions.length : eow;

        // draw polylines
        if (Desktop.routeDir != 0 || NR != 0) {

            // draw polys
            if (NR == 0) { // one single nav line

                // route line color and style
                setStroke(graphics, Config.routeLineStyle ? Graphics.DOTTED : Graphics.SOLID,
                          Config.routeColor, Config.routeThick);

                // draw route line
                drawPoly(graphics, positions, 0, positions.length, Config.routeThick);

            } else { // nav line and trk segs

                // track line color and style
                setStroke(graphics, Config.trackLineStyle ? Graphics.DOTTED : Graphics.SOLID,
                          Config.trackColor, Config.trackThick);

                // draw tracks
                final Object[] ranges = this.trkRanges.getData();
                for (int i = NR; --i >= 0; ) {
                    final int[] range = (int[]) ranges[i];
                    drawPoly(graphics, positions, range[0], range[1], Config.trackThick);
                }

                // has wpts?
                if (Desktop.routeDir != 0 && eow != 0) {

                    // route line color and style
                    setStroke(graphics, Config.routeLineStyle ? Graphics.DOTTED : Graphics.SOLID,
                              Config.routeColor, Config.routeThick);

                    // draw route line
                    drawPoly(graphics, positions, 0, eow, Config.routeThick);
                }
            }

            // restore line style
            graphics.setStrokeStyle(Graphics.SOLID);
//#if __ANDROID__ || __CN1__
            graphics.setStrokeWidth(1);
//#endif
        }

        // POI name/desc color
        graphics.setColor(0x00404040);

        // active wpt index
        final int wptIdx = Desktop.wptIdx;

        // draw POIs
        if (Desktop.routeDir != 0 || Desktop.showall) {
            for (int i = eop; --i >= 0; ) {
                if (positions[i] != null) {
                    byte status = statuses != null ? statuses[i] : WPT_STATUS_VOID; // statuses are null when showAll
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

    private static void setStroke(final Graphics g, final int style, final int color, final int width) {
        g.setStrokeStyle(style);
        g.setColor(Config.COLORS_16[color]);
//#if __ANDROID__ || __CN1__
        g.setStrokeWidth(width * 2 + 1);
//#endif
    }

    private void drawScale(final Graphics graphics) {
        // position on screen
        int h, x0/*, x1*/;
        if (Desktop.screen.iconBarVisible() && Config.guideSpotsMode == 1 && NavigationScreens.guideSize > 0) {
            h = Desktop.height - NavigationScreens.guideSize - 2 * 3;
/*
            x0 = (Desktop.width - scaleLength) >> 1;
*/
            x0 = scaleDx;
        } else {
            h = Desktop.height;
            x0 = scaleDx;
        }
/*
        x1 = x0 + scaleLength;
*/

        // scale
        final float a = DeviceScreen.density;
        final int cy = h - Desktop.osd.bh - (int)Math.ceil(4 * (a * 2));
        if (!Config.osdNoBackground) {
//--//#ifndef __CN1__
//#if !__ANDROID__ && !__CN1__
            graphics.drawImage(Desktop.barScale, x0 + 3 - 2, cy, Graphics.TOP | Graphics.LEFT);
//#else
            graphics.setAliasing(false);
            final int cc = graphics.getColor();
            graphics.setARGBColor(Desktop.barScale_c);
            graphics.fillRect(x0 + 3 - 2, cy, Desktop.barScale_w, Desktop.barScale_h);
            graphics.setColor(cc);
            graphics.setAlpha(0xff);
//#endif
        }
//#if __ANDROID__ || __CN1__
        graphics.setAliasing(false);
//#endif
        final int sh = (int)Math.floor(3 * a);
        final int sy = h - (int)Math.ceil(4 * (a * 2));
        graphics.setColor(0x00f0f0f0);
        graphics.fillRect(x0, sy, scaleLength, sh);
        graphics.setColor(0);
        graphics.drawRect(x0, sy, scaleLength, sh);
        final float ssl = (float)scaleLength / 5;
        final int ssli = ExtraMath.round(ssl);
        for (int i = 0; i < 5; ) {
            graphics.fillRect(x0 + ExtraMath.round(i * ssl), sy, ssli, sh);
            i += 2;
        }
//#if __ANDROID__ || __CN1__
        graphics.setAliasing(true);
//#endif
        graphics.drawChars(sInfo, 0, sInfoLength,
                           x0 + 3, cy,
                           Graphics.LEFT | Graphics.TOP);
    }

    private void drawPoi(final Graphics graphics, final Position position,
                         final byte status, final int idx) {
        int x = position.getX() - this.x;
        int y = position.getY() - this.y;
        if (Config.fixedCrosshair) {
            x -= chx - chx0;
            y -= chy - chy0;
        }

        // on screen?
        if (x > 0 && x < Desktop.width && y > 0 && y < Desktop.height) {

            final boolean current = status == WPT_STATUS_CURRENT;

            // draw point
            if (current) {
                NavigationScreens.drawWaypoint(graphics, x, y,
                                               Graphics.TOP | Graphics.LEFT);
            } else if (Config.routePoiMarks) {
                NavigationScreens.drawPOI(graphics, status, x, y,
                                          Graphics.TOP | Graphics.LEFT);
            }

            int showtext = 0;
            if ((Desktop.routeDir != 0) || (Desktop.wptIdx > -1 && Desktop.showall)) {
                switch (li % 5) {
                    case 0:
                        if (current) showtext = 1;
                    break;
                    case 1:
                        showtext = 1;
                    break;
                    case 2:
                        if (current) showtext = 2;
                    break;
                    case 3:
                        showtext = 2;
                    break;
                }
            } else if (current || Desktop.showall) {
                if (li % 3 == 0) {
                    showtext = 1;
                } else if (li % 3 == 1) {
                    showtext = 2;
                }
            }

            // draw POI text
            if (showtext != 0) {
                final Waypoint wpt = (Waypoint) Desktop.wpts.elementAt(idx);
                String text;
                if (showtext == 1) {
                    text = wpt.toString(); // either GPX or GC name
                } else { // showtext == 2
                    text = wpt.getComment();
/*
                    if (text == null || text.length() == 0) {
                        text = wpt.toString(); // either GPX or GC name 
                    }
*/
                }
                if (text != null) {
//--//#ifndef __CN1__
//#if !__ANDROID__ && !__CN1__
                    final int fh = Desktop.barWpt.getHeight();
                    final int bwMax = Desktop.barWpt.getWidth();
//#else
                    final int fh = Desktop.barWpt_h;
                    final int bwMax = Desktop.barWpt_w;
//#endif
                    int bw = Desktop.fontWpt.stringWidth(text) + 2;
//#if __ANDROID__ && __CN1__
                    // TODO more horizontal padding on hi-res screen
//#endif
                    if (bw > bwMax) {
                        bw = bwMax;
                    }
                    final int xpoit = x + 3;
                    final int ypoit = y - fh - 3;
//--//#ifndef __CN1__
//#if !__ANDROID__ && !__CN1__
//#ifdef __ALT_RENDERER__
                    if (Config.S60renderer) { // S60 renderer
//#endif
                        graphics.setClip(xpoit, ypoit, bw, fh);
                        graphics.drawImage(Desktop.barWpt, xpoit, ypoit,
                                           Graphics.TOP | Graphics.LEFT);
                        graphics.setClip(0, 0, Desktop.width, Desktop.height);
//#ifdef __ALT_RENDERER__
                    } else {
                        graphics.drawRegion(Desktop.barWpt, 0, 0, bw, fh,
                                            Sprite.TRANS_NONE, xpoit, ypoit,
                                            Graphics.TOP | Graphics.LEFT);
                    }
//#endif
//#else
                    final int cc = graphics.getColor();
                    graphics.setARGBColor(Desktop.barWpt_c);
                    graphics.fillRect(xpoit, ypoit, bw, fh);
                    graphics.setColor(cc);
                    graphics.setAlpha(0xff);
//#endif
                    graphics.drawString(text, xpoit + 1, y - fh,
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

        final int m_x0 = scale(slice.getX());
        final int m_y0 = scale(slice.getY());
        final int slice_w = scale(slice.getWidth());
        final int slice_h = scale(slice.getHeight());

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
                final int fchx, fchy;
                if (Config.fixedCrosshair) {
                    fchx = chx - chx0;
                    fchy = chy - chy0;
                } else {
                    fchx = fchy = 0;
                }
//#ifdef __ALT_RENDERER__
                if (Config.S60renderer) { // S60 renderer
//#endif
                    graphics.drawImage(img,
                                       - x_src + x_dest - fchx,
                                       - y_src + y_dest - fchy,
                                       Graphics.TOP | Graphics.LEFT);
//#ifdef __ALT_RENDERER__
                } else {
                    graphics.drawRegion(img,
                                        x_src, y_src, w, h,
                                        Sprite.TRANS_NONE,
                                        x_dest, y_dest,
                                        Graphics.TOP | Graphics.LEFT);
                }
//#endif                
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
//#if !__ANDROID__ && !__CN1__
//#ifdef __PF__
        final short[] arrayPF = trailPF;
//#else
        final int flags = ((Config.trailThick << 1) + 1) << 8;
//#endif
//#endif
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
//#if __ANDROID__ || __CN1__
        graphics.setStrokeWidth((Config.trailThick << 1) + 1);
//#endif

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
//#if !__ANDROID__ && !__CN1__
//#ifdef __PF__
                drawLineSegment(graphics, x0, y0, x1, y1, arrayPF[idx0], w, h);
//#else
                drawLineSegment(graphics, x0, y0, x1, y1, flags, w, h);
//#endif
//#else
                drawLineSegment(graphics, x0, y0, x1, y1, (1 << 8), w, h); // 1 means stroke width is 1
//#endif
            }

            idx0 = idx1;
        }

        // draw line from last trailpoint to current position
        if (Desktop.synced && !Desktop.browsing) {
            final Position p0 = arrayXY[idx0];
            final int x0 = p0.getX() - x;
            final int y0 = p0.getY() - y;
            final int x1 = chx + crosshairSize2;
            final int y1 = chy + crosshairSize2;
            final boolean xIsOff = (x0 < 0 && x1 < 0) || (x0 >= w && x1 >= w);
            final boolean yIsOff = (y0 < 0 && y1 < 0) || (y0 >= h && y1 >= h);
            
            // bounding box check first
            if (!xIsOff && !yIsOff) {

                // draw segment
//#if !__ANDROID__ && !__CN1__
//#ifdef __PF__
                drawLineSegment(graphics, x0, y0, x1, y1, updatePF(Config.trailThick, x1 - x0, y1 - y0), w, h);
//#else
                drawLineSegment(graphics, x0, y0, x1, y1, (((Config.trailThick << 1) + 1) << 8), w, h);
//#endif
//#else
                drawLineSegment(graphics, x0, y0, x1, y1, (1 << 8), w, h); // 1 means stroke width is 1
//#endif
            }
        }
        
        // restore color and style
        graphics.setColor(color);
//#if __ANDROID__ || __CN1__
        graphics.setStrokeWidth(1);
//#endif
    }

    private void drawPoly(final Graphics graphics, final Position[] points,
                          final int begin, final int end, final int thickness) {
        final int w = Desktop.width;
        final int h = Desktop.height;
        final int x = this.x;
        final int y = this.y;
        Position p0 = null;
        for (int i = end; --i >= begin; ) {
            final Position p1 = points[i];
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
//#if !__ANDROID__ && !__CN1__
//#ifdef __PF__
                        drawLineSegment(graphics, x0, y0, x1, y1, updatePF(thickness, p0, p1), w, h);
//#else
                        drawLineSegment(graphics, x0, y0, x1, y1, (((thickness << 1) + 1) << 8), w, h);
//#endif
//#else
                        drawLineSegment(graphics, x0, y0, x1, y1, (1 << 8), w, h); // 1 means stroke width is 1
//#endif
                    }
                }
                p0 = p1;
            }
        }
    }

    private void drawLineSegment(final Graphics graphics,
                                 int x0, int y0,
                                 int x1, int y1,
                                 final int flags,
                                 final int w, final int h) {
//#ifdef __CLIP__
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
//#endif
        if (Config.fixedCrosshair) {
            final int dx = chx - chx0;
            final int dy = chy - chy0;
            x0 -= dx;
            x1 -= dx;
            y0 -= dy;
            y1 -= dy;
        }
        clipLineSegment(graphics, x0, y0, x1, y1, flags);
    }

    private static void clipLineSegment(final Graphics graphics,
                                        final int x0, final int y0,
                                        final int x1, final int y1,
                                        final int flags) {

        final int thick = flags >>> 8;
        if (thick > 1) {
//#ifdef __PF__
            final int t0x0, t0y0, t0x1, t0y1, t0x2, t0y2;
            final int t1x0, t1y0, t1x1, t1y1, t1x2, t1y2;
            final int th2 = thick >> 1;
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
//#else
            final int th = thick >>> 1;
            final int dx = (x1 - x0);
            final int dy = (y1 - y0);
            final int p2 = dx * dx + dy * dy;
            final int t2 = th * th;
            final int k = (int)Math.sqrt(p2 / t2);
            final int r = k >>> 1;
            int tx = 0, ty = 0;
            if (k != 0) {
                tx = dy < 0 ? (dy - r) / k : (dy + r) / k;
                ty = dx < 0 ? (dx - r) / k : (dx + r) / k;
            }
// DEBUG
//            graphics.setColor(0xff,0,0);
//            graphics.drawLine(x0 + tx, y0 - ty, x1 + tx, y1 - ty);
//            graphics.drawLine(x1 + tx, y1 - ty, x1 + tx, y1 + ty);
//            graphics.setColor(0,0xff,0);
//            graphics.drawLine(x1 + tx, y1 + ty, x0 - tx, y0 + ty);
//            graphics.drawLine(x0 - tx, y0 + ty, x0 + tx, y0 - ty);
// ~DEBUG
            final int c1x = x1 + tx;
            final int c1y = y1 - ty;
            final int c0x = x0 - tx;
            final int c0y = y0 + ty;
            graphics.fillTriangle(x0 + tx, y0 - ty, c1x, c1y, c0x, c0y);
            graphics.fillTriangle(c1x, c1y, c0x, c0y, x1 - tx, y1 + ty);
//#endif
        } else {
            graphics.drawLine(x0, y0, x1, y1);
        }
    }

    int nextCrosshair() {
        if (star % 2 == 0) {
            if (wptPositions != null) {
                li++;
            }
        } else {
            ci++;
            if ((ci * crosshairSize) == NavigationScreens.crosshairs.getWidth()) {
                ci = 0;
            }
        }
        return Desktop.MASK_SCREEN;
    }

    void starTick() {
        star++;
    }

    boolean ensureSlices() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("ensure slices from map " + map);
//#endif

        // local ref
        final Map map = this.map;

        // gonna-release flag
        boolean releasing = false;

        // have-all-images flag
        boolean gotAll = true;

            // local refs to vectors
            final NakedVector oldSlices = slices;
            final NakedVector newSlices = slices2;

            // assertion
            if (!newSlices.isEmpty()) {
                newSlices.removeAllElements(); // avoid error spree
                throw new IllegalStateException("New tiles collection not empty");
            }

            // find needed slices ("row by row")
            final int mWidth = map.getWidth();
            final int mHeight = map.getHeight();
            int _x = x;
            int _y = y;
            final int xmax = _x + Desktop.width > mWidth ? mWidth : _x + Desktop.width;
            final int ymax = _y + Desktop.height > mHeight ? mHeight : _y + Desktop.height;
            while (_y < ymax) {
                int _l = ymax; // bottom for current "line"
                while (_x < xmax) {
                    final Slice s = ensureSlice(_x, _y, oldSlices, newSlices);
                    if (s != null) {
                        _x = scale(s.getRightEnd()); // s.getX() + s.getWidth();
                        _l = scale(s.getBottomEnd()); // s.getY() + s.getHeight();
                        if (s.getImage() == null) {
//#ifdef __LOG__
                            if (log.isEnabled()) log.debug("image missing for slice " + s);
//#endif
                            gotAll = false;
                        }
                    } else {
//#ifdef __LOG__
                        log.error("Out of map - no tile for " + _x + "-" + _y);
//#endif
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
            final Object[] oldSlicesArray = oldSlices.getData();
            for (int i = oldSlices.size(); --i >= 0; ) {
                final Slice slice = (Slice) oldSlicesArray[i];
                if (!newSlices.containsReference(slice)) {
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

        /* synchronized to avoid race condition with render() */
        synchronized (this) { // @threads ?:slices

            // gc cleanup
            oldSlices.removeAllElements();

            // exchange vectors
            slices = newSlices;
            slices2 = oldSlices;

        } // ~synchronized

        // loading flag
        boolean loading = false;

        // start loading images
        if (!gotAll && !map.isVirtual()) {
            loading = map.ensureImages(slices);
        }

        // return the 'loading' flags
        return loading;
    }

    private Slice ensureSlice(final int x, final int y,
                              final NakedVector oldSlices,
                              final NakedVector newSlices) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("ensure slice for " + x + "-" + y);
//#endif

        // result
        Slice slice = null;

        // descaled x,y for Slice.isWithin()
        final int xu = descale(x);
        final int yu = descale(y);

        // look for suitable slice in current set
        final Object[] oldSlicesArray = oldSlices.getData();
        for (int i = oldSlices.size(); --i >= 0; ) {
            final Slice s = (Slice) oldSlicesArray[i];
            if (s.isWithin(xu, yu)) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("slice found in current set; " + s);
//#endif
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
            if (!newSlices.containsReference(slice)) {
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

    private Slice getSliceFor(final int px, final int py) {

        // descaled x,y for Slice.isWithin()
        final int xu = descale(px);
        final int yu = descale(py);

        /* synchronized to avoid race condition with ensureSlices() */
        synchronized (this) { // @threads update->ensureSlices:slices
            // first look in current set
            final Object[] slicesArray = this.slices.getData();
            for (int i = slices.size(); --i >= 0; ) {
                final Slice s = (Slice) slicesArray[i];
                if (s.isWithin(xu, yu)) {
                    return s;
                }
            }
        }

        // next try map (we may have no map on start, so be careful)
        final Slice s;
        if (map != null) {
            s = map.getSlice(px, py);
            if (s == null) {
                throw new IllegalStateException("No slice for position " + new Position(px, py));
            }
        } else {
            s = null;
        }

        return s;
    }

    private int scale(final int i) {
        return map.scale(i);
    }

    private int descale(final int i) {
        return map.descale(i);
    }

    private void calculateScale() {
        // clear
        sInfoLength = 0;

        // have map?
        if (map != null) {
            // use 10% of map width at current Y (lat) for calculation
            final int cy = getPosition().getY();
            final float wd = ((float) map.getWidth()) / 10;
            final QualifiedCoordinates qc0 = map.transform(0, cy);
            final QualifiedCoordinates qc1 = map.transform(ExtraMath.round(wd), cy);
            double scale = qc0.distance(qc1) / wd;
            // not too frequent, reduce code size
//            QualifiedCoordinates.releaseInstance(qc0);
//            QualifiedCoordinates.releaseInstance(qc1);

            // valid scale?
            if (scale > 0F) { // this always true now, isn't it?
                char[] uc = null;
                switch (Config.units) {
                    case Config.UNITS_METRIC: {
                        uc = NavigationScreens.DIST_STR_M;
                    } break;
                    case Config.UNITS_IMPERIAL:
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
                if (half - guess > (grade >> 1)) {
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

//#if !__ANDROID__ && !__CN1__

//#ifdef __PF__

    private static short updatePF(final int thick0, final Position p0, final Position p1) {
        return updatePF(thick0, p1.getX() - p0.getX(), p1.getY() - p0.getY());
    }
    
    private static short updatePF(final int thick0, final double dx, final double dy) {
        final int thick = (thick0 << 1) + 1;
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

//#endif

//#endif // !__ANDROID__ && !__CN1__

}
