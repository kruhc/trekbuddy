// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.maps.Slice;
import cz.kruch.track.maps.Map;
import cz.kruch.track.util.Logger;
import cz.kruch.track.AssertionFailedException;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.Sprite;
import java.util.Vector;
import java.util.Enumeration;
import java.io.IOException;

final class MapViewer {
    private static final Logger log = new Logger("MapViewer");

    private int gx, gy;
    private int width, height;
    private int x, y;
    private int chx, chy;
    private int chx0, chy0;
    private int crosshairWidth, crosshairHeight;
    private int compasWidth, compasHeight;
    private int mWidth, mHeight;

    private Image crosshair, compas;
    private Image[] courses;
    private Map map;
    private Vector slices = new Vector();

    private boolean visible = true;
    private Float course;

    public MapViewer(int gx, int gy, int width, int height) throws IOException {
        this.gx = gx;
        this.gy = gy;
        this.width = width;
        this.height = height;
        this.x = this.y = 0;
        this.crosshair = Image.createImage("/resources/crosshair.png");
        this.compas = Image.createImage("/resources/compas.png");
        this.courses = new Image[] {
            Image.createImage("/resources/course_0_0.png"),
            Image.createImage("/resources/course_0_15.png"),
            Image.createImage("/resources/course_0_30.png"),
            Image.createImage("/resources/course_0_45.png"),
            Image.createImage("/resources/course_0_60.png"),
            Image.createImage("/resources/course_0_75.png")
        };
        this.crosshairWidth = crosshair.getWidth();
        this.crosshairHeight = crosshair.getHeight();
        this.compasWidth = compas.getWidth();
        this.compasHeight = compas.getHeight();
        this.chx0 = this.chx = (width - crosshairWidth) / 2;
        this.chy0 = this.chy = (height - crosshairHeight) / 2;
    }

    public void reset() {
        chx = chx0;
        chy = chy0;
        x = y = 0;
        visible = true;
    }

    public void hide() {
        visible = false;
    }

    public void show() {
        visible = true;
    }

    public void setMap(Map map) {
        this.map = map;
        this.mWidth = map.getWidth();
        this.mHeight = map.getHeight();
        this.slices = new Vector(0);
    }

    /**
     * Returns crosshair position.
     */
    public Position getPosition() {
        Position p = new Position(x + chx + crosshairWidth / 2, y + chy + crosshairHeight / 2);

        if (log.isEnabled()) log.debug(p.toString());

        return p;
    }

    // TODO better name - x,y is desired position of crosshair!
    public boolean move(int x, int y) {
        if (log.isEnabled()) log.debug("move, current position " + this.x + "," + this.y);

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

        if (log.isEnabled()) log.debug("move made, dirty? " + dirty + ";current position " + this.x + "," + this.y + "; dirty = " + dirty + "; crosshair requested at " + x + "-" + y + " -> screen position at " + chx + "-" + chy);

        return dirty;
    }

    public boolean scroll(int direction) {
        boolean dirty = false;

        switch (direction) {
            case Canvas.DOWN:
                if (chy < chy0) {
                    chy++;
                    dirty = true;
                } else if (y + height < mHeight) {
                    y++;
                    dirty = true;
                } else if (chy < height - 1 - crosshairHeight / 2) {
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
                } else if (x + width < mWidth) {
                    x++;
                    dirty = true;
                } else if (chx < width - 1 - crosshairWidth / 2) {
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

    public Character boundsHit() {
        Character result = null;
        if (chx + crosshairWidth / 2 == 0) {
            result = new Character('W');
        } else if (chx + crosshairWidth / 2 == width - 1) {
            result = new Character('E');
        } else if (chy + crosshairHeight / 2 == 0) {
            result = new Character('N');
        } else if (chy + crosshairHeight / 2 == height - 1) {
            result = new Character('S');
        }

        return result;
    }

    public void render(Graphics graphics) {
        if (!visible) {
            return;
        }

        // clear window
        graphics.setColor(0, 0, 0);
        graphics.fillRect(gx, gy, width, height);

        // project slices to window
        for (Enumeration e = slices.elements(); e.hasMoreElements(); ) {
            drawSlice(graphics, (Slice) e.nextElement());
        }

        // paint crosshair
        graphics.drawImage(crosshair, chx, chy, Graphics.TOP | Graphics.LEFT);
        graphics.drawImage(compas,
                           chx - (compasWidth - crosshairWidth) / 2,
                           chy - (compasHeight - crosshairHeight) / 2,
                           Graphics.TOP | Graphics.LEFT);

        // paint course
        if (course != null) {
            drawCourse(graphics);
        }
    }

    private void drawCourse(Graphics graphics) {
        int ti;
        switch (course.intValue() / 90) {
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
            default:
                // should never happen
                throw new AssertionFailedException("Course over 360");
        }

        int cwo = course.intValue() % 90;
        int ci;
        if (cwo >= 0 && cwo < 8) {
            ci = 0;
        } else if (cwo >= 8 && cwo < 23) {
            ci = 1;
        } else if (cwo >= 23 && cwo < 38) {
            ci = 2;
        } else if (cwo >= 38 && cwo < 53) {
            ci = 3;
        } else if (cwo >= 53 && cwo < 68) {
            ci = 4;
        } else if (cwo >= 68 && cwo < 83) {
            ci = 5;
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

        // TODO precompute offset
        graphics.drawRegion(courses[ci], 0, 0, courses[ci].getWidth(), courses[ci].getHeight(),
                            ti, chx - (courses[0].getHeight() - crosshairHeight) / 2, chy - (courses[0].getWidth() - crosshairWidth) / 2, Graphics.TOP | Graphics.LEFT);
    }

    private void drawSlice(Graphics graphics, Slice slice) {
        int m_x0 = slice.getAbsolutePosition().getX();
        int m_y0 = slice.getAbsolutePosition().getY();
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

/*
        // assertions
        if (x_src > slice.getWidth()) throw new IllegalStateException("x_src > slice.getWidth()");
        if (y_src > slice.getHeight()) throw new IllegalStateException("y_src > slice.getHeight()");
*/

        if (w > 0 && h > 0) {
            // assertion
            if (slice.getImage() == null) {
                throw new AssertionFailedException("Slice image is null");
            }
            graphics.drawRegion(slice.getImage(),
                                x_src, y_src, w, h,
                                Sprite.TRANS_NONE,
                                x_dest, y_dest,
                                0);
        }
    }

    // TODO optimize; eg. look in current slices
    public boolean ensureSlices() {
        if (log.isEnabled()) log.debug("ensure slices from map @" + Integer.toHexString(map.hashCode()));

        if (map == null) {
            throw new AssertionFailedException("No map");
        }

        // find needed slices "line by line"
        Vector collection = new Vector(4);
        int _x = x;
        int _y = y;
        while (_y <= (y + height)) {
            int _l = y + height; // bottom for current "line"
            while (_x <= (x + width)) {
                Slice s = ensureSlice(_x, _y, collection);
                if (s == null) {
                    _x = _y = Integer.MAX_VALUE;
                    break;
                } else {
                    Position p = s.getAbsolutePosition();
                    int _x0 = p.getX();
                    int _y0 = p.getY();
                    _x = _x0 + s.getWidth() + 1;
                    _l = _y0 + s.getHeight() + 1;
                }
            }
            _y = _l; // next "line" of slices
            _x = x;  // start on "line"
        }

        // release slices images we will no longer use
        for (Enumeration e = slices.elements(); e.hasMoreElements(); ) {
            Slice slice = (Slice) e.nextElement();
            if (collection.contains(slice)) {
                if (log.isEnabled()) log.debug("reuse slice in current set; " + slice);
                continue;
            }

            if (log.isEnabled()) log.debug("release map image in " + slice);
            slice.setImage(null);
        }

        // set new slices
        slices = collection;

        // prepare slices - returns true is there are images to be loaded
        boolean loading = map.prepareSlices(collection);

        // return the 'loading' flags
        return loading;
    }

    private Slice ensureSlice(int x, int y, Vector newSlices) {
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
            slice = map.getSlice(x, y);
        }

        // found it?
        if (slice != null) {
            if (newSlices.contains(slice)) {
                throw new AssertionFailedException("Slice already added");
            }
            newSlices.addElement(slice);
        }

        return slice;
    }
}
