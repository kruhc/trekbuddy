// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.maps.Slice;
import cz.kruch.track.maps.Map;
//#ifdef __LOG__
import cz.kruch.track.util.Logger;
//#endif
import cz.kruch.track.AssertionFailedException;
import cz.kruch.track.configuration.Config;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.Sprite;
import java.util.Vector;

final class MapViewer {
//#ifdef __LOG__
    private static final Logger log = new Logger("MapViewer");
//#endif

    private int gx, gy;
    private int width, height;
    private int x, y;
    private int chx, chy;
    private int chx0, chy0;
    private int crosshairSize, crosshairSize2;
    private int mWidth, mHeight;
    private Position position;

    private Map map;
    private Vector slices = new Vector();
    private Vector slicesTemp = new Vector(); // temp for reuse

    private float course = -1f;
    private Position wptPosition;

    private boolean visible = true;
    private int ci = 0;
    private int[] clip;

/*
    private Image[] crosshairs;
*/
    private boolean S60renderer;

    public MapViewer(int gx, int gy, int width, int height) {
        this.gx = gx;
        this.gy = gy;
        this.crosshairSize = cz.kruch.track.TrackingMIDlet.crosshairs.getHeight();
        this.crosshairSize2 = this.crosshairSize >> 1;
        this.clip = new int[] { -1, -1, crosshairSize, crosshairSize };
        this.position = new Position(0, 0);
        this.S60renderer = Config.getSafeInstance().isS60renderer();
        resize(width, height);
/*
        if (Config.getSafeInstance().isS60renderer()) {
            crosshairs = new Image[]{
                Image.createImage(cz.kruch.track.TrackingMIDlet.crosshairs,
                                  0, 0, crosshairSize, crosshairSize, 0),
                Image.createImage(cz.kruch.track.TrackingMIDlet.crosshairs,
                                  1 * crosshairSize, 0, crosshairSize, crosshairSize, 0),
                Image.createImage(cz.kruch.track.TrackingMIDlet.crosshairs,
                                  2 * crosshairSize, 0, crosshairSize, crosshairSize, 0)
            };
        }
*/
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        this.chx0 = this.chx = (width - crosshairSize) >> 1;
        this.chy0 = this.chy = (height - crosshairSize) >> 1;
        this.x = this.y = 0;
    }

    public void hide() {
        visible = false;
    }

    public void show() {
        visible = true;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean hasMap() {
        return map != null;
    }

    public void setMap(Map map) {
        this.map = null;
        this.slices.removeAllElements(); // slicesTemp is always empty
        if (map != null) {
            this.map = map;
            this.mWidth = map.getWidth();
            this.mHeight = map.getHeight();
            resize(width, height);
        }
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

    // TODO better name - x,y is desired position of crosshair!
    public boolean move(int x, int y) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("move, current position " + this.x + "," + this.y);
//#endif

        boolean dirty = false;
        int direction;

        int dx = x - (width >> 1) - this.x ;
        if (dx > 0) {
            direction = Canvas.RIGHT;
        } else {
            direction = Canvas.LEFT;
        }

        int absDx = Math.abs(dx);
        for (int i = absDx; --i >= 0; ) {
            dirty |= scroll(direction);
        }

        int dy = y - (height >> 1) - this.y;
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

    public boolean scroll(int direction) {
        boolean dirty = false;

        switch (direction) {
            case Canvas.DOWN:
                if (chy < chy0) {
                    chy++;
                    dirty = true;
                } else if (y + height < mHeight - 1) {
                    y++;
                    dirty = true;
                } else if (chy < height - 1 - crosshairSize2 - 1) {
                    chy++;
                    dirty = true;
                }
                break;
            case Canvas.UP:
                if (chy > chy0) {
                    chy--;
                    dirty = true;
                } else if (y > 0) {
                    y--;
                    dirty = true;
                } else if (chy > 0 - crosshairSize2) {
                    chy--;
                    dirty = true;
                }
                break;
            case Canvas.LEFT:
                if (chx > chx0) {
                    chx--;
                    dirty = true;
                } else if (x > 0) {
                    x--;
                    dirty = true;
                } else if (chx > 0 - crosshairSize2) {
                    chx--;
                    dirty = true;
                }
                break;
            case Canvas.RIGHT:
                if (chx < chx0) {
                    chx++;
                    dirty = true;
                } else if (x + width < mWidth - 1) {
                    x++;
                    dirty = true;
                } else if (chx < width - 1 - crosshairSize2 - 1) {
                    chx++;
                    dirty = true;
                }
                break;
            default:
                throw new IllegalArgumentException("Weird direction");
        }

        return dirty;
    }

    public void setWaypoint(Position position) {
        this.wptPosition = position;
    }

    public Character boundsHit() {
        Character result = null;
        if (chx + crosshairSize2 == 0) {
            result = new Character('W');
        } else if (chx + crosshairSize2 >= width - 2) {
            result = new Character('E');
        } else if (chy + crosshairSize2 == 0) {
            result = new Character('N');
        } else if (chy + crosshairSize2 >= height - 2) {
            result = new Character('S');
        }

        return result;
    }

    public void render(Graphics graphics) {
        if (!visible) {
            return;
        }

        render(graphics, null);
    }

    public void render(Graphics graphics, int[] clip) {
        if (!visible) {
            return;
        }

        // project slices to window
        for (int i = slices.size(); --i >= 0; ) {
            Slice slice = (Slice) slices.elementAt(i);
            if (slice.getImage() == null) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("image for slice " + slice + " being loaded?");
//#endif
                continue;
            }
            drawSlice(graphics, /*clip, */slice);
        }

        // paint waypoint
        if (wptPosition != null) {
            drawWaypoint(graphics);
        }
    }

    public void render2(Graphics graphics) {
        if (!visible) {
            return;
        }

        // paint crosshair
/*
        if (crosshairs != null) { // S60 renderer
            graphics.drawImage(crosshairs[ci], chx, chy, 0);
        } else {
*/
            graphics.drawRegion(cz.kruch.track.TrackingMIDlet.crosshairs,
                                ci * crosshairSize, 0, crosshairSize, crosshairSize,
                                Sprite.TRANS_NONE,
                                chx, chy, 0);
/*
        }
*/

        // paint course
        if (course > -1F) {
            drawCourse(graphics);
        }
    }

    // TODO move calculation to setter
    private void drawCourse(Graphics graphics) {
        NavigationScreens.drawArrow(graphics, course,
//                                    chx - (arrowSize - crosshairWidth) / 2,
//                                    chy - (arrowSize - crosshairHeight) / 2,
                                    chx + crosshairSize2,
                                    chy + crosshairSize2,
                                    0);
    }

    private void drawWaypoint(Graphics graphics) {
        int x = wptPosition.getX();
        int y = wptPosition.getY();
        if (x > this.x && x < this.x + width && y > this.y && y < this.y + height) {
            NavigationScreens.drawWaypoint(graphics, x - this.x, y - this.y, 0);
        }
    }

    private void drawSlice(Graphics graphics, /*, int[] clip, */Slice slice) {
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
            x_dest = gx;
        } else {
            x_src = 0;
            w = x + width - m_x0;
            x_dest = gx + m_x0 - x;
        }
        if (w > (width - x_dest)) w = width - x_dest;
        if (w > slice_w) w = slice_w;

        int y_src;
        int h;
        int y_dest;
        if (y > m_y0) {
            y_src = y - m_y0;
            h = slice_h - y_src;
            y_dest = gy;
        } else {
            y_src = 0;
            h = y + height - m_y0;
            y_dest = gy + m_y0 - y;
        }
        if (h > (height - y_dest))
            h = height - y_dest;
        if (h > slice_h)
            h = slice_h;

        if (w > 0 && h > 0) {
            if (S60renderer) { // S60 renderer
                graphics.drawImage(slice.getImage(),
                                   - x_src + x_dest,
                                   - y_src + y_dest, 0);
            } else {
                graphics.drawRegion(slice.getImage(),
                                    x_src, y_src, w, h,
                                    Sprite.TRANS_NONE,
                                    x_dest, y_dest,
                                    0/*Graphics.TOP | Graphics.LEFT*/);
            }
        }
    }

    public void nextCrosshair() {
        ci++;
        if ((ci * crosshairSize) == cz.kruch.track.TrackingMIDlet.crosshairs.getWidth())
            ci = 0;
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

        // assert map not null
        if (map == null) {
            throw new AssertionFailedException("No map");
        }

        // find needed slices ("row by row")
        Vector _slices = slices;
        Vector _slicesTemp = slicesTemp;
        int _x = x;
        int _y = y;
        int xmax = x + width > mWidth ? mWidth : x + width;
        int ymax = y + height > mHeight ? mHeight : y + height;
        while (_y < ymax) {
            int _l = ymax; // bottom for current "line"
            while (_x < xmax) {
                Slice s = ensureSlice(_x, _y, _slicesTemp);
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

        boolean releasing = false;

        // release slices images we will no longer use
        for (int i = _slices.size(); --i >= 0; ) {
            Slice slice = (Slice) _slices.elementAt(i);
            if (_slicesTemp.contains(slice)) {
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
        slices = slicesTemp;
        slicesTemp = v;
        slicesTemp.removeAllElements();

        // prepare slices - returns true is there is at least one image to be loaded
        boolean loading = map.prepareSlices(slices);

        // gc hint (loading ahead or after release)
        if (loading || releasing) {
            System.gc();
        }

        // return the 'loading' flags
        return loading;
    }

    private Slice ensureSlice(int x, int y, Vector newSlices) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("ensure slice for " + x + "-" + y);
//#endif

        Slice slice = null;

        // look for suitable slice in current set
        for (int i = slices.size(); --i >= 0; ) {
            Slice s = (Slice) slices.elementAt(i);
            if (s.isWithin(x, y)) {
                slice = s;
                break;
            }
        }

        // next try map slices
        if (slice == null) {

            // assert map not null
            if (map == null) {
                throw new AssertionFailedException("No map");
            }

            slice = map.getSlice(x, y);
        }

        // found it?
        if (slice != null) {
            if (newSlices.contains(slice)) {
                throw new AssertionFailedException("Slice " + slice + " already added for " + x + "-" + y);
            }
            newSlices.addElement(slice);
        }

        return slice;
    }
}
