package cz.kruch.track.ui;

import cz.kruch.track.AssertionFailedException;
import cz.kruch.track.configuration.Config;

import javax.microedition.lcdui.game.Sprite;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.io.Connector;
import javax.microedition.midlet.MIDlet;

import api.location.LocationProvider;
import api.file.File;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.InputStream;

public final class NavigationScreens {

    /*
     * public constants
     */

    public static final String[] nStr = {
         "0*",  "1*",  "2*",  "3*",  "4*",  "5*",  "6*",  "7*",  "8*",  "9*",
        "10*", "11*", "12*", "13*", "14*", "15*", "16*", "17*", "18*", "19*",
        "20*", "21*", "22*", "23*", "24*"
    };

    public static String SIGN = "^";
    public static String PLUSMINUS = "+-";
    public static String DELTA = "d";

    /*
     * image cache
     */

    public static Image crosshairs;
    public static Image courses, courses2;
    public static Image waypoint;
    public static Image providers;
    public static Image[] stores;

    // private vars
    private static int arrowSize, arrowSize2;
    private static int wptSize2;

    // public (???) vars
    public static int bulletSize;

    public static void initialize() {
        // init image cache
        try {
            crosshairs = createImage("/resources/crosshairs.png");
            courses = createImage("/resources/courses.png");
            courses2 = createImage("/resources/courses2.png");
            waypoint = createImage("/resources/wpt.png");
            providers = createImage("/resources/bullets.png");
            stores = new Image[] {
                createImage("/resources/icon.store.xml.png"),
                createImage("/resources/icon.store.xmla.png"),
                createImage("/resources/icon.store.mem.png"),
                createImage("/resources/icon.store.mema.png")
            };
        } catch (IOException e) {
            throw new IllegalStateException("Image resources could not be loaded");
        }

        // init constants
        try {
            SIGN = new String(new byte[]{ (byte) 0xc2, (byte) 0xb0 }, "UTF-8");
            PLUSMINUS = new String(new byte[]{ (byte) 0xc2, (byte) 0xb1 }, "UTF-8");
            DELTA = new String(new byte[]{ (byte) 0xce, (byte) 0x94 }, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // ignore
        }

        // setup vars
        arrowSize = courses.getHeight();
        arrowSize2 = arrowSize >> 1;
        wptSize2 = waypoint.getWidth() >> 1;
        bulletSize = providers.getHeight();
    }

    public static int customize() throws IOException {
        int i = 0;

        Image image = loadImage("crosshairs.png");
        if (image != null) {
            crosshairs = null;
            crosshairs = image;
            System.gc();
            i++;
        }
        image = loadImage("courses.png");
        if (image != null) {
            courses = null;
            courses = image;
            System.gc();
            i++;
            arrowSize = courses.getHeight();
            arrowSize2 = arrowSize >> 1;
        }
        image = loadImage("courses2.png");
        if (image != null) {
            courses2 = null;
            courses2 = image;
            System.gc();
            i++;
        }
        image = loadImage("wpt.png");
        if (image != null) {
            waypoint = null;
            waypoint = image;
            System.gc();
            i++;
            wptSize2 = waypoint.getWidth() >> 1;
        }

        return i;
    }

    private static Image loadImage(String name) throws IOException {
        Image image = null;
        File file = null;
        try {
            file = File.open(Connector.open(Config.getFolderResources() + name, Connector.READ));
            if (file.exists()) {
                InputStream in = null;
                try {
                    in = file.openInputStream();
                    image = Image.createImage(in);
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        return image;
    }

    public static Image createImage(InputStream in) throws IOException {
        // assertion
        if (in == null) {
            throw new AssertionFailedException("Image stream is null");
        }

        Image image = Image.createImage(in);
        if (Config.forcedGc) {
            System.gc();
        }

        return image;
    }

    private static Image createImage(String res) throws IOException {
        Image image = Image.createImage(res);

        if (Config.forcedGc) {
            System.gc();
        }

        return image;
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
        int ci = (cwo + 5) / 10;
        if (ci == 9) {
            ci = 0;
            switch (ti) {
                case Sprite.TRANS_NONE:
                    ti = Sprite.TRANS_ROT90;
                break;
                case Sprite.TRANS_ROT90:
                    ti = Sprite.TRANS_ROT180;
                break;
                case Sprite.TRANS_ROT180:
                    ti = Sprite.TRANS_ROT270;
                break;
                case Sprite.TRANS_ROT270:
                    ti = Sprite.TRANS_NONE;
                break;
            }
        }

        Image courses;
        switch (ti) {
            case Sprite.TRANS_ROT90: {
                courses = NavigationScreens.courses2;
                ti = Sprite.TRANS_NONE;
            } break;
            case Sprite.TRANS_ROT270: {
                courses = NavigationScreens.courses2;
                ti = Sprite.TRANS_ROT180;
            } break;
            default:
                courses = NavigationScreens.courses;
        }

//        if (Desktop.S60renderer) {
//            graphics.setClip(x - arrowSize2, y - arrowSize2, arrowSize, arrowSize);
//            graphics.drawImage(courses,
//                               x - arrowSize2 - ci * arrowSize, y - arrowSize2,
//                               anchor);
//            graphics.setClip(0, 0, Desktop.width, Desktop.height);
//        } else {
            graphics.drawRegion(courses,
                                ci * arrowSize, 0, arrowSize, arrowSize,
                                ti, x - arrowSize2, y - arrowSize2, anchor);
//        }
    }

    public static void drawWaypoint(Graphics graphics, int x, int y, int anchor) {
        graphics.drawImage(waypoint, x - wptSize2, y - wptSize2, anchor);
    }

    public static void drawProviderStatus(Graphics graphics, int status,
                                          int x, int y, int anchor) {
        int ci = status < LocationProvider._CANCELLED ? status : LocationProvider.OUT_OF_SERVICE;

        if (Config.S60renderer) {
            graphics.setClip(x, y, bulletSize, bulletSize);
            graphics.drawImage(providers,
                               x - ci * bulletSize, y,
                               anchor);
            graphics.setClip(0, 0, Desktop.width, Desktop.height);
        } else {
            graphics.drawRegion(providers,
                                ci * bulletSize, 0, bulletSize, bulletSize,
                                Sprite.TRANS_NONE, x, y, anchor);
        }
    }

    public static StringBuffer append(StringBuffer sb, float value, int precision) {
        if (value < 0F) {
            sb.append('-');
            value = -value;
        }
        precision = adjustPrecision(value, precision);
        int i = (int) value;
        sb.append(i).append('.');
        for ( ; precision > 0; precision--) {
            value -= i;
            value *= 10;
            i = (int) value;
            sb.append(i);
        }

        return sb;
    }

    public static StringBuffer append(StringBuffer sb, double value, int precision) {
        if (value < 0D) {
            sb.append('-');
            value = -value;
        }
        precision = adjustPrecision(value, precision);
        long i = (long) value;
        sb.append(i).append('.');
        for ( ; precision > 0; precision--) {
            value -= i;
            value *= 10;
            i = (long) value;
            sb.append(i);
        }

        return sb;
    }

    private static int adjustPrecision(double value, int precision) {
        if (precision == 0) {
            if (value < 10D) {
                precision = 3;
            } else if (value < 100F) {
                precision = 2;
            } else {
                precision = 1;
            }
        }

        return precision;
    }
}
