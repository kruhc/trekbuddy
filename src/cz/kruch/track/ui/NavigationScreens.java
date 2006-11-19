package cz.kruch.track.ui;

import cz.kruch.track.AssertionFailedException;

import javax.microedition.lcdui.game.Sprite;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Graphics;
import java.io.IOException;

final class NavigationScreens {
    private static boolean initialized = false;

    private static Image[] courses;
    private static int arrowSize;

    private static Image waypoint;
    private static int wptSize2;

    public static void ensureInitialized() throws IOException {
        if (!initialized) {
            initialized = true;
            courses = new Image[]{
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
            arrowSize = courses[0].getWidth();
            waypoint = Image.createImage("/resources/wpt.png");
            wptSize2 = waypoint.getWidth() / 2;
        }
    }

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

        graphics.drawRegion(courses[ci], 0, 0, arrowSize, arrowSize,
                            ti, x - arrowSize / 2, y - arrowSize / 2, anchor);
    }

    public static void drawWaypoint(Graphics graphics, int x, int y, int anchor) {
        graphics.drawImage(waypoint, x - wptSize2, y - wptSize2, anchor);
    }
}
