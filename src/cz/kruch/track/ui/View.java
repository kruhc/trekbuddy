// @LICENSE@

package cz.kruch.track.ui;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Font;

import api.location.Location;
import api.location.QualifiedCoordinates;

import java.util.Vector;

import cz.kruch.track.Resources;

/**
 * Base class for screens.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
abstract class View {
    protected static String MSG_NO_POSITION = Resources.getString(Resources.DESKTOP_MSG_NO_POSITION);
    protected static String MSG_NO_WAYPOINT = Resources.getString(Resources.DESKTOP_MSG_NO_WPT);

    protected final /*Navigator*/Desktop navigator;

    protected boolean isVisible;

    protected View(/*Navigator*/Desktop navigator) {
        this.navigator = navigator;
    }

    void setVisible(final boolean b) {
        isVisible = b;
    }

//#ifdef __ANDROID__

    void onBackground() {
    }

    void onForeground() {
    }

//#endif

    int handleAction(int action, boolean repeated) {
        return Desktop.MASK_NONE;
    }

    int handleKey(int keycode, boolean repeated) {
        return Desktop.MASK_NONE;
    }

    int changeDayNight(int dayNight) {
        return Desktop.MASK_NONE;
    }

    int navigationChanged(Vector wpts, int idx, boolean silent) {
        return isVisible ? Desktop.MASK_SCREEN : Desktop.MASK_NONE;
    }

    int routeChanged(Vector wpts, int mode) {
        return isVisible ? Desktop.MASK_SCREEN : Desktop.MASK_NONE;
    }

    int routeExpanded(Vector wpts) {
        return isVisible ? Desktop.MASK_SCREEN : Desktop.MASK_NONE;
    }

    int configChanged() {
        return isVisible ? Desktop.MASK_SCREEN : Desktop.MASK_NONE;
    }

    void close() {
    }

    Location getLocation() {
        throw new IllegalStateException("Illegal view for this operation");
    }

    QualifiedCoordinates getPointer() {
        throw new IllegalStateException("Illegal view for this operation");
    }

    void trackingStarted() {
    }

    void trackingStopped() {
    }

    void sizeChanged(int w, int h) {
    }

    int orientationChanged(int heading) {
        return isVisible ? Desktop.MASK_SCREEN : Desktop.MASK_NONE;
    }

    abstract void render(Graphics g, Font f, int mask);
    abstract int locationUpdated(Location l);

//#ifdef __B2B__

    static void b2b_init() {
        MSG_NO_WAYPOINT = Resources.getString(Resources.DESKTOP_MSG_NO_WPT);
        MSG_NO_POSITION = Resources.getString(Resources.DESKTOP_MSG_NO_POSITION);
    }

//#endif

}
