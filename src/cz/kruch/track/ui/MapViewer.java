// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.maps.Slice;
import cz.kruch.track.maps.Map;
import cz.kruch.track.util.Mercator;
import cz.kruch.track.AssertionFailedException;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.Sprite;
import java.util.Vector;

import api.location.Location;

final class MapViewer {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("MapViewer");
//#endif

/*
    private static final int TRAJECTORY_LENGTH = 64;
*/

    private int x, y;
    private int chx, chy;
    private int chx0, chy0;
    private int crosshairSize, crosshairSize2;
    private int mWidth, mHeight;
    private final int[] clip;
    private final Position position;

    private Map map;
    private Vector slices;
    private Vector slices2; // for reuse during switch

    private float course = -1F;
    private Position wptPosition;

/*
    private QualifiedCoordinates[] trajectory;
    private short[] trajectoryX;
    private short[] trajectoryY;
    private int tlast, txylast;
*/

    private boolean visible = true;
    private int ci = 0;

    public MapViewer() {
        this.crosshairSize = NavigationScreens.crosshairs.getHeight();
        this.crosshairSize2 = this.crosshairSize >> 1;
        this.clip = new int[] { -1, -1, crosshairSize, crosshairSize };
        this.position = new Position(0, 0);
        this.slices = new Vector(4);
        this.slices2 = new Vector(4);
/*
        this.trajectory = new QualifiedCoordinates[TRAJECTORY_LENGTH];
        this.trajectoryX = new short[TRAJECTORY_LENGTH];
        this.trajectoryY = new short[TRAJECTORY_LENGTH];
*/
    }

    public void sizeChanged(int w, int h) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("size changed");
//#endif
        Position p = getPosition().clone();
        this.chx0 = this.chx = (w - crosshairSize) >> 1;
        this.chy0 = this.chy = (h - crosshairSize) >> 1;
        this.x = this.y = 0;
        // ??? ... start scrolling at left-top corner ... ???
        if (Config.oneTileScroll) {
            this.chx = 0 - crosshairSize2/* - 1*/;
            this.chy = 0 - crosshairSize2/* - 1*/;
        }
        // ~
        setPosition(p);
    }

    public boolean hasMap() {
        return map != null;
    }

    public void setMap(Map map) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("set map " + map);
//#endif

        // store position on map
        if (isDefaultMap(this.map)) {
            Position p = getPosition();
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
            this.slices.removeAllElements(); // slicesTemp is always empty

        } // ~synchronized

/*
        // forget calculated trajectory x-y
        this.tlast = this.txylast = -1;
*/

        // use new map (if any)
        if (map != null) {

            // update Mercator context // TODO ugly
            if (map.getProjection() instanceof Mercator.ProjectionSetup) {
                Mercator.contextDatum = map.getDatum();
                Mercator.contextProjection = (Mercator.ProjectionSetup) map.getProjection();
            } else {
                Mercator.contextDatum = null;
                Mercator.contextProjection = null;
            }

            // use new map
            this.mWidth = map.getWidth();
            this.mHeight = map.getHeight();

            // ???
//            sizeChanged(Desktop.width, Desktop.height);
            // ???
            this.chx0 = this.chx = (Desktop.width - crosshairSize) >> 1;
            this.chy0 = this.chy = (Desktop.height - crosshairSize) >> 1;
            this.x = this.y = 0;
            if (Config.oneTileScroll) {
                this.chx = 0 - crosshairSize2;
                this.chy = 0 - crosshairSize2;
            }
            // ~

            // restore position on map
            if (isDefaultMap(map)) {
                int x = Config.x;
                int y = Config.y;
                if (x > -1 && y > -1 && x < mWidth && y < mHeight) {
                    setPosition(new Position(x, y));
                }
            }
        }
    }

    public static boolean isDefaultMap(Map map) {
        if (map == null) {
            return false;
        }

        String mapPath = Config.mapPath;
        if (mapPath.equals(Config.defaultMapPath)) {
            String mapName = map.getName();
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

    public void setCourse(float course) {
        this.course = course;
    }

    /**
     * Returns crosshair position.
     */
    public Position getPosition() {
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

    public boolean setPosition(Position p) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("move to " + p + ", current position is " + getPosition());
//#endif
//        System.out.println("move to " + p + ", current position is " + getPosition());

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

        for (int i = Math.abs(dx); --i >= 0; ) {
            dirty |= scroll(direction);
        }

        int dy = y - getPosition().getY();
        if (dy > 0) {
            direction = Canvas.DOWN;
        } else {
            direction = Canvas.UP;
        }

        for (int i = Math.abs(dy); --i >= 0; ) {
            dirty |= scroll(direction);
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("move made, dirty? " + dirty + ";current position " + this.x + "," + this.y + "; dirty = " + dirty + "; crosshair requested at " + x + "-" + y + " -> screen position at " + chx + "-" + chy);
//#endif
//        System.out.println("move made, dirty? " + dirty + ";current position " + getPosition() + "; requested at " + p);

        return dirty;
    }

    public boolean scroll(int direction) {
        final int dWidth = Desktop.width;
        final int dHeight = Desktop.height;
        final int crosshairSize2 = this.crosshairSize2;

        int mHeight = this.mHeight;
        int mWidth = this.mWidth;
        int x0 = 0;
        int y0 = 0;

        final boolean ots = Config.oneTileScroll;

        // 1-tile scrolling?
        if (ots) {
            // locals
            Slice slice = null;
            Position p = getPosition();
            final int px = p.getX();
            final int py = p.getY();
            // first look in current set
            if (slice == null) {
                Vector slices = this.slices;
                for (int i = slices.size(); --i >= 0; ) {
                    Slice s = (Slice) slices.elementAt(i);
                    if (s.isWithin(px, py)) {
                        slice = s;
                        break;
                    }
                }
            }

            // next try map
            if (slice == null) {
                slice = this.map.getSlice(px, py);
            }

            // something is wrong
            if (slice == null) {
                throw new IllegalStateException("No slice for position " + p);
            }

            // adjust boundaries for 1-tile scrolling
            mWidth = slice.getWidth();
            mHeight = slice.getHeight();
            x0 = slice.getX();
            y0 = slice.getY();
        }

        boolean dirty = false;

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
                throw new IllegalArgumentException("Weird direction");
        }

//        System.out.println("scroll made, dirty? " + dirty + "; ots? " + ots + "; current position " + getPosition() + "; x=" + x + " y=" + y + " chx=" + chx + " chy=" + chy);

        return dirty;
    }

    public void setWaypoint(Position position) {
        this.wptPosition = position;
    }

    public char boundsHit() {
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

    public void locationUpdated(Location location) {

/*
        // showing trajectory?
        if (Config.getSafeInstance().isTrajectoryOn()) {


                // get coordinates
                QualifiedCoordinates[] array = trajectory; // local ref for faster access
                QualifiedCoordinates qc = location.getQualifiedCoordinates().clone();

                // if array is full, shift and append
                if (tlast == TRAJECTORY_LENGTH - 1) {
                    // shift left
                    QualifiedCoordinates.releaseInstance(array[0]);
                    array[0] = null;
                    System.arraycopy(array, 1, array, 0, TRAJECTORY_LENGTH - 1);

                    // append
                    array[tlast] = qc;

                    // shift left
                    System.arraycopy(trajectoryX, 1, trajectoryX, 0, TRAJECTORY_LENGTH - 1);
                    System.arraycopy(trajectoryY, 1, trajectoryY, 0, TRAJECTORY_LENGTH - 1);

                    // force calculation
                    txylast = tlast - 1;

                } else {

                    // append
                    array[++tlast] = qc;

                }
            }
        }
*/
    }

/*
    public void render(Graphics graphics) {
        if (!visible) {
            return;
        }

        render(graphics, null);
    }

    public void render(Graphics graphics, int[] clip) {
*/

    public void render(Graphics graphics) {
        if (!visible) {
            return;
        }

        /* synchronized to avoid race condition with ensureSlices() */
        synchronized (this) {

            // local ref for faster access
            Vector _slices = slices;

            // project slices to window
//            for (int i = _slices.size(); --i >= 0; ) {
            for (int N = _slices.size(), i = 0; i < N; i++) {
                drawSlice(graphics, /*clip, */(Slice) _slices.elementAt(i));
            }

        } // ~synchronized

/*
        // draw trajectory
        if (tlast > 0) {
            drawTrajectory(graphics);
        }
*/

        // paint waypoint
        if (wptPosition != null) {
            int x = wptPosition.getX();
            int y = wptPosition.getY();
            if (x > this.x && x < this.x + Desktop.width && y > this.y && y < this.y + Desktop.height) {
                NavigationScreens.drawWaypoint(graphics, x - this.x, y - this.y,
                                               Graphics.TOP | Graphics.LEFT);
            }
        }
/*
    }

    public void render2(Graphics graphics) {
        if (!visible) {
            return;
        }
*/

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
        if (course > -1F) {
            NavigationScreens.drawArrow(graphics, course,
                                        chx + crosshairSize2,
                                        chy + crosshairSize2,
                                        Graphics.TOP | Graphics.LEFT);
        }
    }

    private void drawSlice(Graphics graphics, /*, int[] clip, */Slice slice) {
        Image img = slice.getImage();
        if (img == null) {
            return;
        }

        int m_x0 = slice.getX();
        int m_y0 = slice.getY();
        int slice_w = slice.getWidth();
        int slice_h = slice.getHeight();

        int x_src;
        int w;
        int x_dest;
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

        int y_src;
        int h;
        int y_dest;
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
//        System.out.println("draw slice " + slice + "; xsrc=" + x_src + ", ysrc=" + y_src + ", xdest=" + x_dest + ", ydest=" + y_dest);
        if (w > 0 && h > 0) {
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
        }
    }

/*
    private void drawTrajectory(Graphics graphics) {
        QualifiedCoordinates[] array = trajectory;
        short[] arrayX = trajectoryX;
        short[] arrayY = trajectoryY;
        int x = this.x;
        int y = this.y;
        int tlast = this.tlast;

        // set color and style
        int color = graphics.getColor();
        graphics.setColor(0x00FF0000);

        // draw polyline
        for (int i = 0; i <= tlast; i++) {
            if (txylast < i) { // calculated missing x-y
                txylast++;
                QualifiedCoordinates localQc = map.getDatum().toLocal(array[txylast]);
                Position p = map.transform(localQc);
                QualifiedCoordinates.releaseInstance(localQc);
                arrayX[txylast] = (short) p.getX();
                arrayY[txylast] = (short) p.getY();
            }
            if (i > 0) {
                graphics.drawLine(arrayX[i - 1] - x, arrayY[i - 1] - y,
                                  arrayX[i] - x, arrayY[i] - y);
            }
        }
        // draw line from last "gpx" point to current position
        graphics.drawLine(arrayX[txylast] - x, arrayY[txylast] - y,
                          chx + crosshairSize2, chy + crosshairSize2);

        // restore color and style
        graphics.setColor(color);
    }
*/

    public void nextCrosshair() {
        ci++;
        if ((ci * crosshairSize) == NavigationScreens.crosshairs.getWidth()) {
            ci = 0;
        }
    }

    public int[] getClip() {
        if (!visible)
            return null;

        clip[0] = chx;
        clip[1] = chy;

        return clip;
    }

    public boolean ensureSlices() {
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
            Vector _slices = slices;
            Vector _slices2 = slices2;

            // assertion
            if (!_slices2.isEmpty()) {
                throw new AssertionFailedException("Temporary slices collection not empty");
            }

            // find needed slices ("row by row")
            int _x = x;
            int _y = y;
            int xmax = x + Desktop.width > mWidth ? mWidth : x + Desktop.width;
            int ymax = y + Desktop.height > mHeight ? mHeight : y + Desktop.height;
            while (_y < ymax) {
                int _l = ymax; // bottom for current "line"
                while (_x < xmax) {
                    Slice s = ensureSlice(_x, _y, _slices, _slices2);
                    if (s == null) {
                        throw new AssertionFailedException("Out of map - no slice for " + _x + "-" + _y);
                    } else {
                        _x = s.getX() + s.getWidth();
                        _l = s.getY() + s.getHeight();
                    }
                }
                _y = _l; // next "line" of slices
                _x = x;  // start on "line"
            }

            /*
             * detect missing images for slices for current view
             */

            // create list of slices whose images are to be loaded
            for (int i = _slices2.size(); gotAll && --i >= 0; ) {
                Slice slice = (Slice) _slices2.elementAt(i);
                if (slice.getImage() == null) {
    //#ifdef __LOG__
                    if (log.isEnabled()) log.debug("image missing for slice " + slice);
    //#endif
                    gotAll = false;
                }
            }

            /*
             * free images for slices no longer used in current view
             */

            // release slices images we will no longer use
            for (int i = _slices.size(); --i >= 0; ) {
                Slice slice = (Slice) _slices.elementAt(i);
                if (_slices2.contains(slice)) {
    //#ifdef __LOG__
                    if (log.isEnabled()) log.debug("reuse slice in current set; " + slice);
    //#endif
                    continue;
                }

    //#ifdef __LOG__
                if (log.isEnabled()) log.debug("release image in " + slice);
    //#endif
                slice.setImage(null);
                releasing = true;
            }

            // exchange vectors and do cleanup
            Vector v = slices;
            slices = slices2;
            slices2 = v;
            slices2.removeAllElements();

        } // ~synchronized

        // forced gc (loading ahead or after release)
        if (releasing && Config.forcedGc) {
            System.gc();
        }

        // loading flag
        boolean loading = false;

        // start loading images
        if (!gotAll) {
            loading = map.ensureImages(slices);
        }

        // return the 'loading' flags
        return loading;
    }

    private Slice ensureSlice(int x, int y, Vector oldSlices, Vector newSlices) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("ensure slice for " + x + "-" + y);
//#endif

        Slice slice = null;

        // look for suitable slice in current set
        for (int i = oldSlices.size(); --i >= 0; ) {
            Slice s = (Slice) oldSlices.elementAt(i);
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
            if (newSlices.contains(slice)) {
                throw new AssertionFailedException("Slice " + slice + " already added for " + x + "-" + y);
            }
            // add slice from map
            newSlices.addElement(slice);
        }

        return slice;
    }
}
