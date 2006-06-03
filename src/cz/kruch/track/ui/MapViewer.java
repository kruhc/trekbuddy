// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.maps.Slice;
import cz.kruch.track.maps.Map;
import cz.kruch.track.location.Position;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.game.Sprite;
import java.util.Vector;
import java.util.Enumeration;

final class MapViewer {
    private static final String COMPONENT_NAME = "MapViewer";

    private int gx, gy;
    private int width, height;
    private int x, y;

    private Map map;
    private Vector slices = new Vector();

    public MapViewer(int gx, int gy, int width, int height) {
        this.gx = gx;
        this.gy = gy;
        this.width = width;
        this.height = height;
    }

    public void setMap(Map map) {
        this.map = map;
    }

    public Position getPosition() {
        return new Position(x, y);
    }

    public boolean scroll(int direction) {
        boolean dirty = false;

        switch (direction) {
            case Canvas.DOWN:
                if (y + height < map.getCalibration().getHeight()) {
                    y++;
                    dirty = true;
//                    System.out.println("scroll DOWN");
                }
                break;
            case Canvas.UP:
                if (y > 0) {
                    y--;
                    dirty = true;
//                    System.out.println("scroll UP");
                }
                break;
            case Canvas.LEFT:
                if (x > 0) {
                    x--;
                    dirty = true;
//                    System.out.println("scroll LEFT");
                }
                break;
            case Canvas.RIGHT:
                if (x + width < map.getCalibration().getWidth()) {
                    x++;
                    dirty = true;
//                    System.out.println("scroll RIGHT");
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown direction");
        }

//        if (!dirty) System.out.println("no move");
//        System.out.println("x = " + x + ", y = " + y);

        return dirty;
    }

    public void render(Graphics graphics) {
        // clear window
        graphics.fillRect(gx, gy, width, height);

        // project slices to window
        for (Enumeration e = slices.elements(); e.hasMoreElements(); ) {
            drawSlice(graphics, (Slice) e.nextElement());
        }
    }

    private void drawSlice(Graphics graphics, Slice slice) {
        Position mapPosition = slice.getAbsolutePosition();

        int x_src = -1;
        int w = -1;
        int x_dest = -1;
        if (x > mapPosition.getX()) {
            x_src = x - mapPosition.getX();
            w = slice.getWidth() - x_src;
            x_dest = gx;
        } else {
            x_src = 0;
            w = x + width - mapPosition.getX();
            x_dest = gx + mapPosition.getX() - x;
        }
        if (w > width) w = width;

        int y_src = -1;
        int h = -1;
        int y_dest = -1;
        if (y > mapPosition.getY()) {
            y_src = y - mapPosition.getY();
            h = slice.getHeight() - y_src;
            y_dest = gy;
        } else {
            y_src = 0;
            h = y + height - mapPosition.getY();
            y_dest = gy + mapPosition.getY() - y;
        }
        if (h > height) h = height;

        if (w > 0 && h > 0) {
//            System.out.println("draw region src " + x_src + "x" + y_src + " dim " + w + "x" + h + " at " + x_dest + "x" + y_dest);
            graphics.drawRegion(slice.getImage(),
                                x_src, y_src, w, h,
                                Sprite.TRANS_NONE,
                                x_dest, y_dest,
                                0);
        }
    }

    public boolean ensureSlices() {
        if (map == null) {
            System.err.println(COMPONENT_NAME + " [error] no map");
            throw new IllegalStateException("No map");
        }

        Vector collection = new Vector();
        ensureSlice(x, y, collection);
        ensureSlice(x + width - 1, y, collection);
        ensureSlice(x, y + height - 1, collection);
        ensureSlice(x + width - 1, y + height - 1, collection);
        collection.trimToSize();

        // find slices we will no longer use
        Vector garbage = new Vector();
        for (Enumeration e = slices.elements(); e.hasMoreElements(); ) {
            Slice slice = (Slice) e.nextElement();
            if (!collection.contains(slice)) {
                // debug
                System.out.println(COMPONENT_NAME + " [debug] release map image in " + slice);

                // to be freed
                garbage.addElement(slice);
            }
        }

        // release old slices
        map.releaseSlices(garbage);

        // help gc
        if (garbage.size() > 0) System.gc(); // enforce gc

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
//            System.out.println("add map " + map);
            newSlices.addElement(slice);
        }
    }
}
