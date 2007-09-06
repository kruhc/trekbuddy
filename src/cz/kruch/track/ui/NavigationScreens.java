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

import cz.kruch.track.AssertionFailedException;
import cz.kruch.track.configuration.Config;

import javax.microedition.lcdui.game.Sprite;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.io.Connector;

import api.location.QualifiedCoordinates;
import api.file.File;

import java.io.IOException;
import java.io.InputStream;

/**
 * UI helper.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class NavigationScreens {

    /*
     * public constants
     */

    public static final char[][] nStr = {
        { '3', '*'}, { '4', '*'}, { '5', '*'}, { '6', '*'},
        { '7', '*'}, { '8', '*'}, { '9', '*'},
        { '1', '0', '*'}, { '1', '1', '*'}, { '1', '2', '*'}
    };

    private static final char[] digits = {
	    '0' , '1' , '2' , '3' , '4' , '5' , '6' , '7' , '8' , '9'
    };
    
    public static final char SIGN         = 0xb0;
    public static final char PLUSMINUS    = 0xb1;
    public static final char DELTA_D      = 0x394;
    public static final char DELTA_d      = 0x3b4;

    /*
     * image cache
     */

    public static Image crosshairs;
    public static Image courses;
    public static Image waypoint, pois;
    public static Image providers;
    public static Image[] stores;

    // number formatter buffer
    private static final char[] print = new char[64];

    // private vars
    private static int arrowSize, arrowSize2;
    private static int wptSize2;
    private static int poiSize, poiSize2;

    // public (???) vars
    public static int bulletSize;

    public static void initialize() {
        // init image cache
        try {
            crosshairs = createImage("/resources/crosshairs.png");
            courses = createImage("/resources/arrows.png");
            waypoint = createImage("/resources/wpt.png");
            pois = createImage("/resources/pois.png");
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

        // setup vars
        arrowSize = courses.getHeight();
        arrowSize2 = arrowSize >> 1;
        wptSize2 = waypoint.getWidth() >> 1;
        bulletSize = providers.getHeight(); // = 10
        poiSize = pois.getHeight();
        poiSize2 = poiSize >> 1;
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
        image = loadImage("arrows.png");
        if (image != null) {
            courses = null;
            courses = image;
            System.gc();
            i++;
            arrowSize = courses.getHeight();
            arrowSize2 = arrowSize >> 1;
        }
        image = loadImage("wpt.png");
        if (image != null) {
            waypoint = null;
            waypoint = image;
            System.gc();
            i++;
            wptSize2 = waypoint.getHeight() >> 1;
        }
        image = loadImage("pois.png");
        if (image != null) {
            pois = null;
            pois = image;
            System.gc();
            i++;
            poiSize = pois.getHeight();
            poiSize2 = poiSize >> 1;
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

    public static void drawArrow(Graphics graphics, final float course,
                                 final int x, final int y, final int anchor) {
        int courseInt = ((int) course) % 360;
        int cr = courseInt / 90;
        int cwo = courseInt % 90;
        int ci = (cwo + 5) / 10;
        if (ci == 9) {
            ci = 0;
            cr++;
        }

        int ti;

        switch (cr) {
            case 0:
                ti = Sprite.TRANS_NONE;
                break;
            case 1:
//                ti = Sprite.TRANS_ROT90;
                ci = 9 /*- 1*/ - ci;
                ti = Sprite.TRANS_MIRROR_ROT180;
                break;
            case 2:
                ti = Sprite.TRANS_ROT180;
                break;
            case 3:
//                ti = Sprite.TRANS_ROT270;
                ci = 9 /*- 1*/ - ci;
                ti = Sprite.TRANS_MIRROR;
                break;
            case 4:
                ti = Sprite.TRANS_NONE;
                break;
            default:
                // should never happen
                throw new AssertionFailedException("Course over 360?");
        }

/* S60 renderer path is impossible - drawImage does not support rotation
        if (Config.S60renderer) {
        } else {
*/
            graphics.drawRegion(courses,
                                ci * arrowSize, 0, arrowSize, arrowSize,
                                ti, x - arrowSize2, y - arrowSize2, anchor);
/*
        }
*/
    }

    public static void drawWaypoint(Graphics graphics, final int x, final int y,
                                    final int anchor) {
        graphics.drawImage(waypoint, x - wptSize2, y - wptSize2, anchor);
    }

    public static void drawPOI(Graphics graphics, final int status,
                               final int x, final int y, final int anchor) {
        if (Config.S60renderer) {
            graphics.setClip(x - poiSize2, y - poiSize2, poiSize, poiSize);
            graphics.drawImage(pois,
                               x - status * poiSize - poiSize2, y - poiSize2,
                               anchor);
            graphics.setClip(0, 0, Desktop.width, Desktop.height);
        } else {
            graphics.drawRegion(pois,
                                status * poiSize, 0, poiSize, poiSize,
                                Sprite.TRANS_NONE, x - poiSize2, y - poiSize2, anchor);
        }
    }

    public static void drawProviderStatus(Graphics graphics, final int status,
                                          final int x, final int y, final int anchor) {
        final int ci = status & 0x0000000f;

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

    public static StringBuffer append(StringBuffer sb, double value, int precision) {
        if (value < 0D) {
            sb.append('-');
            value = -value;
        }

        precision = adjustPrecision(value, precision);

        final long m = (long) value;
        append(sb, m);

        sb.append('.');
        
        value -= m;
        while (precision-- > 0) {
            value *= 10;
            if (value < 1D) {
                sb.append('0');
            }
        }

        final long n = (long) value;
        if (n != 0 || sb.charAt(sb.length() - 1) != '0') { // avoids formatting like "51.00"
            append(sb, n);
        }

        return sb;
    }

    public static StringBuffer append(StringBuffer sb, long value) {
        if (value < 0) {
            sb.append('-');
        } else {
            value = -value;
        }

        if (value > -10) {
            sb.append(digits[(int)(-value)]);
        } else if (value > -100) {
            sb.append(digits[(int)(-(value / 10))]);
            sb.append(digits[(int)(-(value % 10))]);
        } else {
            synchronized (print) {
                final char[] print = NavigationScreens.print;
                final char[] digits = NavigationScreens.digits;
                int c = 0;
                long i = value;
                while (i <= -10) {
                    print[c++] = digits[(int)(-(i % 10))];
                    i = i / 10;
                }
                print[c++] = digits[(int)(-i)];
                while (--c >= 0) {
                    sb.append(print[c]);
                }
            }
        }

        return sb;
    }

    private static int adjustPrecision(double value, int precision) {
        if (precision == 0) {
            if (value < 10D) {
                precision = 3;
            } else if (value < 100D) {
                precision = 2;
            } else {
                precision = 1;
            }
        }

        return precision;
    }
}
