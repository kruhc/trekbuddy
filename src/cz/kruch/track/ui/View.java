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

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.game.GameCanvas;

import api.location.Location;
import api.location.QualifiedCoordinates;

import java.util.Vector;

import cz.kruch.track.Resources;

/**
 * Base class for screens - {@link Desktop.MapView}, {@link ComputerView},
 * {@link LocatorView}.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
abstract class View {
    protected static final String MSG_NO_POSITION;

    static {
        MSG_NO_POSITION = Resources.getString(Resources.DESKTOP_MSG_NO_POSITION);
    }

    protected /*Navigator*/Desktop navigator;
    protected boolean isVisible;

    protected View(/*Navigator*/Desktop navigator) {
        this.navigator = navigator;
    }

    void setVisible(final boolean b) {
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
    protected final void flushGraphics(final int x, final int y, final int width, final int height) {
        gameCanvas.flushGraphics(x, y, width, height);
    }

    /** @deprecated temporary solution until renderer is improved */
    protected final void flushGraphics(final int[] clip) {
        gameCanvas.flushGraphics(clip[0], clip[1], clip[2], clip[3]);
    }
}
