// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.maps.Slice;
import cz.kruch.track.maps.Map;
//#ifdef __LOG__
import cz.kruch.track.util.Logger;
//#endif
import cz.kruch.track.AssertionFailedException;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.Sprite;
import java.util.Vector;
import java.util.Enumeration;
import java.io.IOException;

final class MapViewer {
//#ifdef __LOG__
    private static final Logger log = new Logger("MapViewer");
//#endif

    private int gx, gy;
    private int width, height;
    private int x, y;
    private int chx, chy;
    private int chx0, chy0;
    private int crosshairWidth, crosshairHeight;
    private int arrowWidth, arrowHeight;
    private int mWidth, mHeight;

    private Image[] crosshairs;
    private Image[] courses;
    private Map map;
    private Vector slices = new Vector();

    private Image waypoint;
    private int wptSize2;
    private Position wptPosition;

    private boolean visible = true;
    private Float course;
    private int ci = 0;

    public MapViewer(int gx, int gy, int width, int height) throws IOException {
        this.gx = gx;
        this.gy = gy;
        this.crosshairs = new Image[] {
            Image.createImage("/resources/crosshair_tp_full.png"),
            Image.createImage("/resources/crosshair_tp_white.png"),
            Image.createImage("/resources/crosshair_tp_grey.png")
        };
        this.courses = new Image[] {
            Image.createImage("/resources/course_0.png"),
            Image.createImage("/resources/course_10.png"),
            Image.createImage("/resources/course_20.png"),
            Image.createImage("/resources/course_30.png"),
            Image.createImage("/resources/course_40.png"),
            Image.createImage("/resources/course_50.png"),
            Image.createImage("/resources/course_60.png"),
            Image.createImage("/resources/course_70.png"),
            Image.createImage("/resources/course_80.png")
        };
        this.waypoint = Image.createImage("/resources/wpt.png");
        this.wptSize2 = this.waypoint.getWidth() / 2;
        this.crosshairWidth = crosshairs[0].getWidth();
        this.crosshairHeight = crosshairs[0].getHeight();
        this.arrowWidth = courses[0].getWidth();
        this.arrowHeight = courses[0].getHeight();
        resize(width, height);
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        this.chx0 = this.chx = (width - crosshairWidth) / 2;
        this.chy0 = this.chy = (height - crosshairHeight) / 2;
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

    public void setMap(Map map) {
        this.map = map;
        this.mWidth = map.getWidth();
        this.mHeight = map.getHeight();
        this.slices = new Vector(0);
        resize(width, height);
    }

    /**
     * Returns crosshair position.
     */
    public Position getPosition() {
        Position p = new Position(x + chx + crosshairWidth / 2, y + chy + crosshairHeight / 2);

//#ifdef __LOG__
        if (log.isEnabled()) log.debug(p.toString());
//#endif

        return p;
    }

    // TODO better name - x,y is desired position of crosshair!
    public boolean move(int x, int y) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("move, current position " + this.x + "," + this.y);
//#endif

        boolean dirty = false;
        int direction = -1;

        int dx = x - (width / 2) - this.x ;
        if (dx > 0) {
            direction = Canvas.RIGHT;
        } else {
            direction = Canvas.LEFT;
        }

        int absDx = Math.abs(dx);
        for (int i = 0; i < absDx; i++) {
            dirty |= scroll(direction);
        }

        int dy = y - (height / 2) - this.y;
        if (dy > 0) {
            direction = Canvas.DOWN;
        } else {
            direction = Canvas.UP;
        }

        int absDy = Math.abs(dy);
        for (int i = 0; i < absDy; i++) {
            dirty |= scroll(direction);
        }

        chx = x - this.x - crosshairWidth / 2;
        chy = y - this.y - crosshairHeight / 2;

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
                } else if (chy < height - 1 - crosshairHeight / 2 - 1) {
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
                } else if (chy > 0 - crosshairHeight / 2) {
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
                } else if (chx > 0 - crosshairWidth / 2) {
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
                } else if (chx < width - 1 - crosshairWidth / 2 - 1) {
                    chx++;
                    dirty = true;
                }
                break;
            default:
                throw new IllegalArgumentException("Weird direction");
        }

        return dirty;
    }

    public void setCourse(Float course) {
        this.course = course;
    }

    public void setWaypoint(Position position) {
        this.wptPosition = position;
    }

    public Character boundsHit() {
        Character result = null;
        if (chx + crosshairWidth / 2 == 0) {
            result = new Character('W');
        } else if (chx + crosshairWidth / 2 >= width - 2) {
            result = new Character('E');
        } else if (chy + crosshairHeight / 2 == 0) {
            result = new Character('N');
        } else if (chy + crosshairHeight / 2 >= height - 2) {
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
        for (Enumeration e = slices.elements(); e.hasMoreElements(); ) {
            Slice slice = (Slice) e.nextElement();
            if (slice.getImage() == null) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("image for slice " + slice + " being loaded?");
//#endif
                continue;
            }
            drawSlice(graphics, /*clip, */slice);
        }
    }

    public void render2(Graphics graphics) {
        if (!visible) {
            return;
        }

        // paint crosshair
        graphics.drawImage(crosshairs[ci], chx, chy, 0/*Graphics.TOP | Graphics.LEFT*/);

        // paint waypoint
        if (wptPosition != null) {
            drawWaypoint(graphics);
        }

        // paint course
        if (course != null) {
            drawCourse(graphics);
        }
    }

    // TODO move calculation to setter
    private void drawCourse(Graphics graphics) {
        int ti;
        int courseInt = course.intValue();
        switch (courseInt / 90) {
            case 0:
                ti = Sprite.TRANS_NONE;
                break;
            case 1:
                ti = Sprite.TRANS_ROT90;
                break;
            case 2:
                ti = Sprite.TRANS_ROT180;
                break;
            case 3:
                ti = Sprite.TRANS_ROT270;
                break;
            case 4:
                ti = Sprite.TRANS_NONE;
                break;
            default:
                // should never happen
                throw new AssertionFailedException("Course over 360");
        }

        int cwo = courseInt % 90;
        int ci;
        if (cwo >= 0 && cwo < 6) {
            ci = 0;
        } else if (cwo >= 6 && cwo < 16) {
            ci = 1;
        } else if (cwo >= 16 && cwo < 26) {
            ci = 2;
        } else if (cwo >= 26 && cwo < 36) {
            ci = 3;
        } else if (cwo >= 36 && cwo < 46) {
            ci = 4;
        } else if (cwo >= 46 && cwo < 56) {
            ci = 5;
        } else if (cwo >= 56 && cwo < 66) {
            ci = 6;
        } else if (cwo >= 66 && cwo < 76) {
            ci = 7;
        } else if (cwo >= 76 && cwo < 86) {
            ci = 8;
        } else {
            ci = 0;
            if (ti == Sprite.TRANS_NONE) {
                ti = Sprite.TRANS_ROT90;
            } else if (ti == Sprite.TRANS_ROT90) {
                ti = Sprite.TRANS_ROT180;
            } else if (ti == Sprite.TRANS_ROT180) {
                ti = Sprite.TRANS_ROT270;
            } else if (ti == Sprite.TRANS_ROT270) {
                ti = Sprite.TRANS_NONE;
            }
        }

        graphics.drawRegion(courses[ci], 0, 0, arrowWidth, arrowHeight,
                            ti,
                            chx - (arrowWidth - crosshairWidth) / 2,
                            chy - (arrowHeight - crosshairHeight) / 2,
                            0/*Graphics.TOP | Graphics.LEFT*/);
    }

    private void drawWaypoint(Graphics graphics) {
        int x = wptPosition.getX();
        int y = wptPosition.getY();
        if (x > this.x && x < this.x + width && y > this.y && y < this.y + height) {
            graphics.drawImage(waypoint,
                               x - this.x - wptSize2, y - this.y - wptSize2,
                               0/*Graphics.TOP | Graphics.LEFT*/);
        }
    }

    private void drawSlice(Graphics graphics, /*, int[] clip, */Slice slice) {
        int m_x0 = slice.getX();
        int m_y0 = slice.getY();
        int slice_w = slice.getWidth();
        int slice_h = slice.getHeight();

        int x_src = -1;
        int w = -1;
        int x_dest = -1;
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

        int y_src = -1;
        int h = -1;
        int y_dest = -1;
        if (y > m_y0) {
            y_src = y - m_y0;
            h = slice_h - y_src;
            y_dest = gy;
        } else {
            y_src = 0;
            h = y + height - m_y0;
            y_dest = gy + m_y0 - y;
        }
        if (h > (height - y_dest)) h = height - y_dest;
        if (h > slice_h) h = slice_h;

//        if (log.isEnabled()) log.debug("draw " + w + "x" + h + " from " + slice.getURL() + ";" + x_src + "-" + y_src + " at " + x_dest + "-" + y_dest + " ");
        if (w > 0 && h > 0) {
            graphics.drawRegion(slice.getImage(),
                                x_src, y_src, w, h,
                                Sprite.TRANS_NONE,
                                x_dest, y_dest,
                                0/*Graphics.TOP | Graphics.LEFT*/);
        }
    }

    public void nextCrosshair() {
        ci++;
        if (ci == crosshairs.length)
            ci = 0;
    }

    public int[] getClip() {
        if (!visible)
            return null;

        return new int[] { chx, chy, crosshairWidth, crosshairHeight };
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
        Vector v = new Vector(4); // 4 is an pesimistic guess
        int _x = x;
        int _y = y;
        int xmax = x + width > mWidth ? mWidth : x + width;
        int ymax = y + height > mHeight ? mHeight : y + height;
        while (_y < ymax) {
            int _l = ymax; // bottom for current "line"
            while (_x < xmax) {
                Slice s = ensureSlice(_x, _y, v);
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

        // release slices images we will no longer use
        for (Enumeration e = slices.elements(); e.hasMoreElements(); ) {
            Slice slice = (Slice) e.nextElement();
            if (v.contains(slice)) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("reuse slice in current set; " + slice);
//#endif
                continue;
            }

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("release image in " + slice);
//#endif
            slice.setImage(null);
        }

        // set new slices
        slices = null; // gc hint
        slices = v;

        // prepare slices - returns true is there is at least one image to be loaded
        boolean loading = map.prepareSlices(v);
        v = null; // gc hint

        // gc hint (loading ahead)
        if (loading) System.gc();

        // return the 'loading' flags
        return loading;
    }

    private Slice ensureSlice(int x, int y, Vector newSlices) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("ensure slice for " + x + "-" + y);
//#endif

        Slice slice = null;

        // look for suitable slice in current set
        for (Enumeration e = slices.elements(); e.hasMoreElements(); ) {
            Slice s = (Slice) e.nextElement();
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
