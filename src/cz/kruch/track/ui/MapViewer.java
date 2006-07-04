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
    private static final Logger log = new Logger("Map");

    private int gx, gy;
    private int width, height;
    private int x, y;
    private int chx, chy;
    private int chx0, chy0;

    private Image crosshair;
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
        this.courses = new Image[] {
            Image.createImage("/resources/course_0_0.png"),
            Image.createImage("/resources/course_0_15.png"),
            Image.createImage("/resources/course_0_30.png"),
            Image.createImage("/resources/course_0_45.png"),
            Image.createImage("/resources/course_0_60.png"),
            Image.createImage("/resources/course_0_75.png")
        };
        this.chx0 = this.chx = (width - crosshair.getWidth()) / 2;
        this.chy0 = this.chy = (height - crosshair.getHeight()) / 2;
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
    }

    public Position getPosition() {
        // crosshair-oriented position
        Position p = new Position(x + chx + crosshair.getWidth() / 2,
                                  y + chy + crosshair.getHeight() / 2);
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

        for (int i = 0; i < Math.abs(dx); i++) {
            dirty |= scroll(direction);
        }

        int dy = y - (height / 2) - this.y;
        if (dy > 0) {
            direction = Canvas.DOWN;
        } else {
            direction = Canvas.UP;
        }

        for (int i = 0; i < Math.abs(dy); i++) {
            dirty |= scroll(direction);
        }

        chx = x - this.x - crosshair.getWidth() / 2;
        chy = y - this.y - crosshair.getHeight() / 2;

//        if (dirty) System.out.println("move made, current position " + this.x + "," + this.y + "; dirty = " + dirty + "; crosshair requested at " + x + "-" + y + " -> screen position at " + chx + "-" + chy);

        return dirty;
    }

    public boolean scroll(int direction) {
        boolean dirty = false;

        switch (direction) {
            case Canvas.DOWN:
                if (chy < chy0) {
                    chy++;
                    dirty = true;
                } else if (y + height < map.getHeight()) {
                    y++;
                    dirty = true;
                } else if (chy < height - 1 - crosshair.getHeight() / 2) {
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
                } else if (chy > 0 - crosshair.getHeight() / 2) {
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
                } else if (chx > 0 - crosshair.getWidth() / 2) {
                    chx--;
                    dirty = true;
                }
                break;
            case Canvas.RIGHT:
                if (chx < chx0) {
                    chx++;
                    dirty = true;
                } else if (x + width < map.getWidth()) {
                    x++;
                    dirty = true;
                } else if (chx < width - 1 - crosshair.getWidth() / 2) {
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
                            ti, chx - (courses[0].getHeight() - crosshair.getHeight()) / 2, chy - (courses[0].getWidth() - crosshair.getWidth()) / 2, Graphics.TOP | Graphics.LEFT);
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
//            log.debug("draw region " + x_src + "x" + y_src + " dim " + w + "x" + h + " of " + slice + " at " + x_dest + "x" + y_dest);
            graphics.drawRegion(slice.getImage(),
                                x_src, y_src, w, h,
                                Sprite.TRANS_NONE,
                                x_dest, y_dest,
                                0);
        }
    }

    public boolean ensureSlices() {
        if (map == null) {
            if (log.isEnabled()) log.error("no map");
            throw new IllegalStateException("No map");
        }

        Vector collection = new Vector();
        ensureSlice(x, y, collection);
        ensureSlice(x + width - 1, y, collection);
        ensureSlice(x, y + height - 1, collection);
        ensureSlice(x + width - 1, y + height - 1, collection);

        // find slices we will no longer use
        Vector garbage = null;
        for (Enumeration e = slices.elements(); e.hasMoreElements(); ) {
            Slice slice = (Slice) e.nextElement();
            if (!collection.contains(slice)) {
                // debug
                if (log.isEnabled()) log.debug("release map image in " + slice);

                // to be freed
                if (garbage == null) {
                    garbage = new Vector();
                }
                garbage.addElement(slice);
            }
        }

        // release old slices
        if (garbage != null) {
            map.releaseSlices(garbage);
        }

        // set new slices
        slices = collection;

        // prepare slices - returns true is there are images to be loaded
        boolean loading = map.prepareSlices(collection);

        // return the 'loading' flags
        return loading;
    }

    private void ensureSlice(int x, int y, Vector newSlices) {
        Slice slice = map.getSlice(x, y);
        if (slice != null && !newSlices.contains(slice)) {
            newSlices.addElement(slice);
        }
    }
}
