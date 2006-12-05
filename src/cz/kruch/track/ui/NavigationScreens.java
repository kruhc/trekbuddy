package cz.kruch.track.ui;

import cz.kruch.track.AssertionFailedException;

import javax.microedition.lcdui.game.Sprite;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

final class NavigationScreens {
    private static int arrowSize, arrowSize2;
    private static int wptSize2;

    public static void drawArrow(Graphics graphics, float course,
                                 int x, int y, int anchor) {
        int ti;
        int courseInt = (int) course;
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

        Image _courses = cz.kruch.track.TrackingMIDlet.courses;
        if (ti == Sprite.TRANS_ROT90) {
            _courses = cz.kruch.track.TrackingMIDlet.courses2;
            ti = Sprite.TRANS_NONE;
        } else if (ti == Sprite.TRANS_ROT270) {
            _courses = cz.kruch.track.TrackingMIDlet.courses2;
            ti = Sprite.TRANS_ROT180;
        }

        graphics.drawRegion(_courses,
                            ci * arrowSize, 0, arrowSize, arrowSize,
                            ti, x - arrowSize2, y - arrowSize2, anchor);
    }

    public static void drawWaypoint(Graphics graphics, int x, int y, int anchor) {
        graphics.drawImage(cz.kruch.track.TrackingMIDlet.waypoint,
                           x - wptSize2, y - wptSize2, anchor);
    }

    static void initialize() {
        arrowSize = cz.kruch.track.TrackingMIDlet.courses/*[0]*/.getHeight();
        arrowSize2 = arrowSize >> 1;
        wptSize2 = cz.kruch.track.TrackingMIDlet.waypoint.getWidth() >> 1;
    }
}
