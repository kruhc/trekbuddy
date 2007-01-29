package cz.kruch.track.ui;

import cz.kruch.track.AssertionFailedException;

import javax.microedition.lcdui.game.Sprite;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

import api.location.LocationProvider;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public final class NavigationScreens {
    /*
     * public constants
     */
    public static final int[] ranges = {
        500, 250, 100, 50, 25, 10, 5
    };
    public static final String[] rangesStr = {
        "500 m", "250 m", "100 m", "50 m", "25 m", "10 m", "5 m"
    };
    public static final String[] nStr = {
         "0*",  "1*",  "2*",  "3*",  "4*",  "5*",  "6*",  "7*",  "8*",  "9*",
        "10*", "11*", "12*", "13*", "14*", "15*", "16*", "17*", "18*", "19*",
        "20*", "21*", "22*", "23*", "24*"
    };
    public static String SIGN = "^";
    public static String PLUSMINUS = "+-";

    /*
     * image cache
     */
    public static Image courses, courses2;
    public static Image waypoint;
    public static Image crosshairs;
    public static Image providers;

    // private vars
    private static int arrowSize, arrowSize2;
    private static int wptSize2;

    // public (???) vars
    public static int bulletSize;

    public static void initialize() {
        // init image cache
        try {
            courses = Image.createImage("/resources/courses.png");
            courses2 = Image.createImage("/resources/courses2.png");
            waypoint = Image.createImage("/resources/wpt.png");
            crosshairs = Image.createImage("/resources/crosshairs.png");
            providers = Image.createImage("/resources/bullets.png");
        } catch (IOException e) {
            // TODO what???
        }

        // init constants
        try {
            SIGN = new String(new byte[]{ (byte) 0xc2, (byte) 0xb0 }, "UTF-8");
            PLUSMINUS = new String(new byte[]{ (byte) 0xc2, (byte) 0xb1 }, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // ignore
        }

        // setup vars
        arrowSize = courses.getHeight();
        arrowSize2 = arrowSize >> 1;
        wptSize2 = waypoint.getWidth() >> 1;
        bulletSize = providers.getHeight();
    }

    public static void drawArrow(Graphics graphics, float course,
                                 int x, int y, int anchor) {
        int ti;
        int courseInt = ((int) course) % 360;
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

        Image _courses = courses;
        if (ti == Sprite.TRANS_ROT90) {
            _courses = courses2;
            ti = Sprite.TRANS_NONE;
        } else if (ti == Sprite.TRANS_ROT270) {
            _courses = courses2;
            ti = Sprite.TRANS_ROT180;
        }

        graphics.drawRegion(_courses,
                            ci * arrowSize, 0, arrowSize, arrowSize,
                            ti, x - arrowSize2, y - arrowSize2, anchor);
    }

    public static void drawWaypoint(Graphics graphics, int x, int y, int anchor) {
        graphics.drawImage(waypoint,
                           x - wptSize2, y - wptSize2, anchor);
    }

    public static void drawProviderStatus(Graphics graphics, int status,
                                          int x, int y, int anchor) {
        int ci = status < LocationProvider._CANCELLED ? status : LocationProvider.OUT_OF_SERVICE;

        graphics.drawRegion(providers,
                            ci * bulletSize, 0, bulletSize, bulletSize,
                            Sprite.TRANS_NONE, x, y, anchor);
    }
}
