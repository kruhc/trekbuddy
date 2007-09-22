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
import cz.kruch.track.util.Mercator;
import cz.kruch.track.util.ExtraMath;
import cz.kruch.track.util.SimpleCalendar;
import cz.kruch.track.configuration.Config;

import javax.microedition.lcdui.game.Sprite;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.io.Connector;

import api.location.QualifiedCoordinates;
import api.location.Location;
import api.file.File;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * UI helper.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class NavigationScreens {

    /*
     * public constants
     */

    public static final String[] nStr = {
        "3*", "4*", "5*", "6*", "7*", "8*", "9*", "10*", "11*", "12*"
    };

    private static final char[] digits = {
	    '0' , '1' , '2' , '3' , '4' , '5' , '6' , '7' , '8' , '9'
    };
    
    public static final char SIGN         = 0xb0;
    public static final char PLUSMINUS    = 0xb1;
    public static final char DELTA_D      = 0x394;
    public static final char DELTA_d      = 0x3b4;

/*
    private static final Calendar CALENDAR = Calendar.getInstance(TimeZone.getDefault());
    private static final Date DATE = new Date();
*/
    private static final SimpleCalendar CALENDAR = new SimpleCalendar(Calendar.getInstance(TimeZone.getDefault()));

    private static final String STR_KN  = " kn ";
    private static final String STR_MPH = " mph ";
    private static final String STR_KMH = " km/h ";
    private static final String STR_M   = " m";
    
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
    private static boolean arrowsFull;

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
        arrowsFull = courses.getWidth() == courses.getHeight() * 36;
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
            arrowsFull = courses.getWidth() == courses.getHeight() * 36;
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
        final int courseInt = ((int) course) % 360;

        if (arrowsFull) {
            int ci = courseInt / 10;
            int cr = courseInt % 10;
            if (cr > 5) {
                ci++;
                if (ci == 36) {
                    ci = 0;
                }
            }
            graphics.setClip(x - arrowSize2, y - arrowSize2, arrowSize, arrowSize);
            graphics.drawImage(courses,
                               x - ci * arrowSize - arrowSize2, y - arrowSize2,
                               anchor);
            graphics.setClip(0, 0, Desktop.width, Desktop.height);
        } else {
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
            graphics.drawRegion(courses,
                                ci * arrowSize, 0, arrowSize, arrowSize,
                                ti, x - arrowSize2, y - arrowSize2, anchor);
        }
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

    // TODO move to NavigationScreens
    public static StringBuffer toStringBuffer(Location l, StringBuffer sb) {
/*
        DATE.setTime(timestamp);
        CALENDAR.setTime(DATE);
        final int hour = CALENDAR.get(Calendar.HOUR_OF_DAY);
        final int min = CALENDAR.get(Calendar.MINUTE);
*/
        CALENDAR.setTimeSafe(l.getTimestamp());

        final int hour = CALENDAR.hour;
        final int min = CALENDAR.minute;

        if (hour < 10) {
            sb.append('0');
        }
        append(sb, hour).append(':');
        if (min < 10) {
            sb.append('0');
        }
        append(sb, min).append(' ');

        if (l.getFix() > 0) {
            final float speed = l.getSpeed();
            final float alt = l.getQualifiedCoordinates().getAlt();
            if (Config.unitsNautical) {
                if (speed > -1F) {
                    append(sb, speed * 3.6F / 1.852F, 1).append(STR_KN);
                }
                if (l.getCourse() > -1F) {
                    append(sb, (int) l.getCourse()).append(SIGN);
                }
            } else if (Config.unitsImperial) {
                if (speed > -1F) {
                    append(sb, speed * 3.6F / 1.609F, 1).append(STR_MPH);
                }
                if (alt != Float.NaN) {
                    append(sb, alt, 1).append(STR_M);
                }
            } else {
                if (speed > -1F) {
                    NavigationScreens.append(sb, speed * 3.6F, 1).append(STR_KMH);
                }
                if (alt != Float.NaN) {
                    append(sb, alt, 1).append(STR_M);
                }
            }
/* rendered by OSD directly
            if (sat > -1) {
                sb.append(sat).append('*');
            }
*/
        }

        return sb;
    }

    public static StringBuffer toStringBuffer(QualifiedCoordinates qc, StringBuffer sb) {
        if (Config.useGridFormat && (Mercator.isGrid())) {
            toGrid(qc, sb);
        } else if (Config.useUTM) {
            toUTM(qc, sb);
        } else {
            // condensed for SXG75
            if (cz.kruch.track.TrackingMIDlet.sxg75 && Config.decimalPrecision) {
                toCondensedLL(qc, sb);
            } else { // decent devices
                toLL(qc, sb);
            }
        }

        return sb;
    }

    private static StringBuffer toGrid(QualifiedCoordinates qc, StringBuffer sb) {
        Mercator.Coordinates gridCoords = Mercator.LLtoGrid(qc);
        if (gridCoords.zone != null) {
            sb.append(gridCoords.zone).append(' ');
        }
        zeros(sb, gridCoords.easting, 10000);
        NavigationScreens.append(sb, ExtraMath.round(gridCoords.easting));
        sb.append(' ');
        zeros(sb, gridCoords.northing, 10000);
        NavigationScreens.append(sb, ExtraMath.round(gridCoords.northing));
        Mercator.Coordinates.releaseInstance(gridCoords);

        return sb;
    }

    private static StringBuffer toUTM(QualifiedCoordinates qc, StringBuffer sb) {
        Mercator.Coordinates utmCoords = Mercator.LLtoUTM(qc);
        sb.append(utmCoords.zone).append(' ');
        sb.append('E').append(' ');
        NavigationScreens.append(sb, ExtraMath.round(utmCoords.easting));
        sb.append(' ');
        sb.append('N').append(' ');
        NavigationScreens.append(sb, ExtraMath.round(utmCoords.northing));
        Mercator.Coordinates.releaseInstance(utmCoords);

        return sb;
    }

    private static StringBuffer toCondensedLL(QualifiedCoordinates qc, StringBuffer sb) {
        sb.append(qc.getLat() > 0D ? 'N' : 'S');
        append(QualifiedCoordinates.LAT, qc.getLat(), true, sb);
        sb.deleteCharAt(sb.length() - 1);
        sb.append(' ');
        sb.append(qc.getLon() > 0D ? 'E' : 'W');
        append(QualifiedCoordinates.LON, qc.getLon(), true, sb);
        sb.deleteCharAt(sb.length() - 1);

        return sb;
    }

    private static StringBuffer toLL(QualifiedCoordinates qc, StringBuffer sb) {
        sb.append(qc.getLat() > 0D ? 'N' : 'S').append(' ');
        append(QualifiedCoordinates.LAT, qc.getLat(), Config.decimalPrecision, sb);
        sb.append(' ');
        sb.append(qc.getLon() > 0D ? 'E' : 'W').append(' ');
        append(QualifiedCoordinates.LON, qc.getLon(), Config.decimalPrecision, sb);

        return sb;
    }

    public static StringBuffer append(final int index, final int grade, StringBuffer sb) {
        zeros(sb, index, grade);
        append(sb, (long) index);

        return sb;
    }

    public static StringBuffer append(final int type, final double value, final boolean hp, StringBuffer sb) {
        double l = Math.abs(value);
        if (Config.useGeocachingFormat) {
            int h = (int) Math.floor(l);
            l -= h;
            l *= 60D;
            int m = (int) Math.floor(l);
            l -= m;
            l *= 1000D;
            int dec = (int) Math.floor(l);
            if ((l - dec) > 0.5D) {
                dec++;
                if (dec == 1000) {
                    dec = 0;
                    m++;
                    if (m == 60) {
                        m = 0;
                        h++;
                    }
                }
            }

            if (type == QualifiedCoordinates.LON && h < 100) {
                sb.append('0');
            }
            if (h < 10) {
                sb.append('0');
            }
            NavigationScreens.append(sb, h).append(NavigationScreens.SIGN);
            NavigationScreens.append(sb, m).append('.');
            if (dec < 100) {
                sb.append('0');
            }
            if (dec < 10) {
                sb.append('0');
            }
            NavigationScreens.append(sb, dec);
        } else {
            int h = (int) Math.floor(l);
            l -= h;
            l *= 60D;
            int m = (int) Math.floor(l);
            l -= m;
            l *= 60D;
            int s = (int) Math.floor(l);
            int ss = 0;

            if (hp) { // round decimals
                l -= s;
                l *= 10;
                ss = (int) Math.floor(l);
                if ((l - ss) > 0.5D) {
                    ss++;
                    if (ss == 10) {
                        ss = 0;
                        s++;
                        if (s == 60) {
                            s = 0;
                            m++;
                            if (m == 60) {
                                m = 0;
                                h++;
                            }
                        }
                    }
                }
            } else { // round secs
                if ((l - s) > 0.5D) {
                    s++;
                    if (s == 60) {
                        s = 0;
                        m++;
                        if (m == 60) {
                            m = 0;
                            h++;
                        }
                    }
                }
            }

            NavigationScreens.append(sb, h).append(NavigationScreens.SIGN);
            NavigationScreens.append(sb, m).append('\'');
            NavigationScreens.append(sb, s);
            if (hp) {
                sb.append('.');
                NavigationScreens.append(sb, ss);
            }
            sb.append('"');
        }

        return sb;
    }

    private static StringBuffer zeros(StringBuffer sb, final double d, final int c) {
        int i = ExtraMath.grade(d);
        while (i < c) {
            sb.append('0');
            i *= 10;
        }

        return sb;
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
