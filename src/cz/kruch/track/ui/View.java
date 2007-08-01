// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.location.Navigator;
import cz.kruch.track.location.Waypoint;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.game.GameCanvas;

import api.location.Location;
import api.location.QualifiedCoordinates;

import java.util.Vector;

abstract class View {
    protected static final char[] MSG_NO_POSITION =
        { 'N', 'O', ' ', 'P', 'O', 'S', 'I', 'T', 'I', 'O', 'N' };

    protected Navigator navigator;
    protected boolean isVisible;

    protected View(Navigator navigator) {
        this.navigator = navigator;
    }

    void setVisible(boolean b) {
        isVisible = b;
    }

    public int handleAction(int action, boolean repeated) {
        return Desktop.MASK_NONE;
    }

    public int handleKey(int keycode, boolean repeated) {
        return Desktop.MASK_NONE;
    }

    public int changeDayNight(int dayNight) {
        return Desktop.MASK_NONE;
    }

    public int navigationChanged(Vector wpts, int idx, boolean silent) {
        return isVisible ? Desktop.MASK_SCREEN : Desktop.MASK_NONE;
    }

    public int routeChanged(Vector wpts) {
        return isVisible ? Desktop.MASK_SCREEN : Desktop.MASK_NONE;
    }

    public void close() {
    }

    public Location getLocation() {
        throw new IllegalStateException("Illegal view for this operation");
    }

    public QualifiedCoordinates getPointer() {
        throw new IllegalStateException("Illegal view for this operation");
    }

    public void reset() {
    }

    public void sizeChanged(int w, int h) {
    }

    public abstract void render(Graphics g, Font f, int mask);
    public abstract int locationUpdated(Location l);

    /*
     * Hack for rendering.
     */

    /** @deprecated temporary solution until renderer is improved */
    private GameCanvas gameCanvas;

    /** @deprecated temporary solution until renderer is improved */
    final void setCanvas(GameCanvas gameCanvas) {
        this.gameCanvas = gameCanvas;
    }

    /** @deprecated temporary solution until renderer is improved */
    protected final void flushGraphics() {
        gameCanvas.flushGraphics();
    }

    /** @deprecated temporary solution until renderer is improved */
    protected final void flushGraphics(int x, int y, int width, int height) {
        gameCanvas.flushGraphics(x, y, width, height);
    }

    /** @deprecated temporary solution until renderer is improved */
    protected final void flushGraphics(int[] clip) {
        gameCanvas.flushGraphics(clip[0], clip[1], clip[2], clip[3]);
    }
}
