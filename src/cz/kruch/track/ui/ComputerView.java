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

import cz.kruch.track.configuration.Config;
import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.util.SimpleCalendar;
import cz.kruch.j2se.io.BufferedInputStream;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.game.Sprite;
import javax.microedition.io.Connector;

import api.location.Location;
import api.location.QualifiedCoordinates;
import api.file.File;

import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.Vector;
import java.util.Hashtable;
import java.util.Calendar;
import java.util.TimeZone;

import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * CMS aka 'Computer' screen.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class ComputerView extends View {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("LocatorView");
//#endif

    // xml tags
    private static final String TAG_FONTS       = "fonts";
    private static final String TAG_FONT        = "font";
    private static final String TAG_SCREEN      = "screen";
    private static final String TAG_COLORS      = "colors";
    private static final String TAG_AREA        = "area";
    private static final String TAG_VALUE       = "value";
    private static final String ATTR_NAME       = "name";
    private static final String ATTR_FILE       = "file";
    private static final String ATTR_SYSTEM     = "system";
    private static final String ATTR_MODE       = "mode";
    private static final String ATTR_BGCOLOR    = "bgcolor";
    private static final String ATTR_FGCOLOR    = "fgcolor";
    private static final String ATTR_NXCOLOR    = "nxcolor";
    private static final String ATTR_PXCOLOR    = "pxcolor";
    private static final String ATTR_X          = "x";
    private static final String ATTR_Y          = "y";
    private static final String ATTR_W          = "w";
    private static final String ATTR_H          = "h";
    private static final String ATTR_ALIGN      = "align";

    private static final String[] NAME_CACHE = {
        TAG_FONT, TAG_AREA, TAG_VALUE, ATTR_X, ATTR_Y, ATTR_H, ATTR_W, ATTR_ALIGN
    };

    // special value
    private static final String TOKEN_COORDS     = "coords";
    private static final String TOKEN_TIME       = "time";
    private static final String TOKEN_TIME_TOTAL = "time-t";
    private static final String TOKEN_STATUS     = "status";
    private static final String TOKEN_WPT_AZI    = "wpt-azi";
    private static final String TOKEN_WPT_DIST   = "wpt-dist";
    private static final String TOKEN_WPT_VMG    = "wpt-vmg";
    private static final String TOKEN_WPT_ETA    = "wpt-eta";

    // float values
    private static final String[] TOKENS_float = {
        // base
        "alt",
        "course",
        "spd",
        "spd-max",
        "spd-avg",
        "dist-t",
        // deltas
        "alt-d",
        "course-d",
        "spd-d",
        "spd-dmax",
        "spd-davg"
    };

    // float values indexes
    private static final int VALUE_ALT      = 0;
    private static final int VALUE_COURSE   = 1;
    private static final int VALUE_SPD      = 2;
    private static final int VALUE_SPD_MAX  = 3;
    private static final int VALUE_SPD_AVG  = 4;
    private static final int VALUE_DIST_T   = 5;
    private static final int VALUE_ALT_D    = 6;
    private static final int VALUE_COURSE_D = 7;
    private static final int VALUE_SPD_D    = 8;
    private static final int VALUE_SPD_DMAX = 9;
    private static final int VALUE_SPD_DAVG = 10;
    private static final int VALUE_COORDS       = 1000;
    private static final int VALUE_TIME         = 1001;
    private static final int VALUE_TIME_TOTAL   = 1002;
    private static final int VALUE_STATUS       = 1003;
    private static final int VALUE_WPT_AZI      = 1004;
    private static final int VALUE_WPT_DIST     = 1005;
    private static final int VALUE_WPT_ETA      = 1006;
    private static final int VALUE_WPT_VMG      = 1007;
    private static final int VALUE_SIGN         = 2000;

    // charset
    private static final char[] CHARSET = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        ' ', '+', '-', '.', ':', '/', 0x1E, '"', '\'',
        'h', 'k', 'm', 'p', 's'
    };

    private static final char[] DELIMITERS  = { '{', '}' };

    private static final String SIGN_HEXA   = "0x1E";
    private static final String NO_TIME     = "??:??:??";

/*
    private static final Calendar CALENDAR  = Calendar.getInstance(TimeZone.getDefault());
    private static final Date DATE = new Date();
*/
    private static final SimpleCalendar TIME_CALENDAR = new SimpleCalendar(Calendar.getInstance(TimeZone.getDefault()));
    private static final SimpleCalendar ETA_CALENDAR = new SimpleCalendar(Calendar.getInstance(TimeZone.getDefault()));

    private static final int[] CRC_TABLE = new int[256];

    private static final class Area {
        public short x, y, w, h;
        public String fontName;
        public Object fontImpl;
        public char[] value;
        public boolean ralign;
        public float cw;
        public short ch;
        public short index = -1;

        /** to avoid generation of $1 class */
        public Area() {
        }
    }

    private final StringBuffer sb;
    private final char[] text;
    private final CharArrayTokenizer tokenizer;

    private String profile;
    private String status;
    private int[] colors;
    private final Vector areas;
    private final Hashtable fonts, fontsPng;

    private QualifiedCoordinates valueCoords, snrefCoords;
    private long timestamp, starttime;
    private final float[] valuesFloat;
    private int counter;

    public ComputerView(/*Navigator*/Desktop navigator) {
        super(navigator);
        this.sb = new StringBuffer(64);
        this.text = new char[64];
        this.tokenizer = new CharArrayTokenizer();
        this.colors = new int[8];
        this.areas = new Vector(4);
        this.fonts = new Hashtable(4);
        this.fontsPng = new Hashtable(2);
        this.valuesFloat = new float[TOKENS_float.length];

        // init CRC table
        int[] crc_table = CRC_TABLE;
        for (int n = 0; n < 256; n++) {
            int c = n;
            for (int k = 8; --k >= 0;) {
                if ((c & 1) != 0)
                    c = 0xedb88320 ^ (c >>> 1);
                else
                    c = c >>> 1;
            }
            crc_table[n] = c;
        }

        // reset values
        reset();

        // temporary solution
        try {
            profile = (String) load("cms.simple.xml");
        } catch (Throwable t) {
            status = t.toString();
        }

        // adjust mode
        changeDayNight(Config.dayNight);
    }

    public void reset() {
        TIME_CALENDAR.reset();
        ETA_CALENDAR.reset();

        valueCoords = null;
        snrefCoords = null;
        for (int i = valuesFloat.length; --i >= 0; ) {
            valuesFloat[i] = 0F;
        }
        timestamp = starttime = 0;
        counter = 0;
    }

    public int locationUpdated(Location l) {
        long t = l.getTimestamp();

        // calculate time diff
        long dt = timestamp == 0 ? 0 : (t - timestamp) / 1000;

        // update times
        timestamp = t;
        if (starttime == 0) {
            starttime = t;
        }

        // everything else needs fix
        if (l.getFix() > 0) {

            // calculate distance - emulate static navigation
            float ds = 0F;
            if (snrefCoords == null) {
                snrefCoords = l.getQualifiedCoordinates().clone();
            } else {
                final float accuracy = l.getQualifiedCoordinates().getAccuracy();
                ds = snrefCoords.distance(l.getQualifiedCoordinates());
                if (accuracy < 0F) {
                    if (ds < 50) {
                        ds = 0F;
                    }
                } else if (ds < (3 * accuracy + 5)) {
                    ds = 0F;
                } else {
                    QualifiedCoordinates.releaseInstance(snrefCoords);
                    snrefCoords = null;
                    snrefCoords = l.getQualifiedCoordinates().clone();
                }
            }

            // update coords
            QualifiedCoordinates.releaseInstance(valueCoords);
            valueCoords = null;
            valueCoords = l.getQualifiedCoordinates().clone();

            // local ref for faster access
            float[] valuesFloat = this.valuesFloat;

            // alt, alt-d
            float f = valueCoords.getAlt();
            if (f != Float.NaN) {
                if (dt > 0) {
                    valuesFloat[VALUE_ALT_D] = (f - valuesFloat[VALUE_ALT]) / dt;
                }
                valuesFloat[VALUE_ALT] = f;
            }

            // course, course-d
            f = l.getCourse();
            if (f > -1F) {
                valuesFloat[VALUE_COURSE_D] = f - valuesFloat[VALUE_COURSE];
                valuesFloat[VALUE_COURSE] = f;
            }

            // spd, spd-d
            f = l.getSpeed();
            if (f > -1F) {
                f *= 3.6F;
                if (dt > 0) {
                    valuesFloat[VALUE_SPD_D] = ((f - valuesFloat[VALUE_SPD]) / 3.6F) / dt;
                }
                valuesFloat[VALUE_SPD] = f;

                // spd-max, spd-dmax
//              valuesFloat[3 + DELTAS_OFFSET] = ?
                if (f > valuesFloat[VALUE_SPD_MAX]) {
                    valuesFloat[VALUE_SPD_MAX] = f;
                }

                // spd-avg, spd-davg
//              valuesFloat[4 + DELTAS_OFFSET] = ?
                valuesFloat[VALUE_SPD_AVG] = (valuesFloat[VALUE_SPD_AVG] * counter + f) / ++counter;
            }

            // dist-t
            valuesFloat[VALUE_DIST_T] += ds / 1000F;
        }

        return isVisible ? Desktop.MASK_SCREEN : Desktop.MASK_NONE;
    }

    public int changeDayNight(int dayNight) {
        // local refs for faster access
        Vector areas = this.areas;
        Hashtable fonts = this.fonts;
        Hashtable fontsPng = this.fontsPng;

        // release refs for Image fonts
        for (int N = areas.size(), i = 0; i < N; i++) {
            Area area = (Area) areas.elementAt(i);
            if (fontsPng.containsKey(area.fontName)) {
                area.fontImpl = null;
            }
        }

        // release images
        fontsPng.clear();
        System.gc(); // GC

        // create image fonts
        for (int N = areas.size(), i = 0; i < N; i++) {
            Area area = (Area) areas.elementAt(i);
            if (area.fontImpl == null) {
                try {
                    // get raw data and create a copy
                    byte[] data = (byte[]) fonts.get(area.fontName);
                    // colorify
                    colorifyPng(data, colors[dayNight * 4 + 1]);
                    // create image
                    ByteArrayInputStream bais = new ByteArrayInputStream(data);
                    Image image = NavigationScreens.createImage(bais);
                    bais.close();
                    // store image
                    fontsPng.put(area.fontName, image);
                    // use it is area
                    area.fontImpl = image;
                    area.cw = image.getWidth() / CHARSET.length;
                    area.ch = (short) image.getHeight();
                } catch (Throwable t) {
                    // fallback
                    area.fontImpl = Desktop.font;
//#ifdef __LOG__
                    if (log.isEnabled()) log.error("failure", t);
                    t.printStackTrace();
//#endif
                }
            }
        }

        return isVisible ? Desktop.MASK_SCREEN : Desktop.MASK_NONE;
    }

    public void render(Graphics graphics, Font font, int mask) {
        // local copies for faster access
        int w = Desktop.width;
        int h = Desktop.height;

        // default settings
        graphics.setFont(Desktop.font);

        // got profile?
        if (profile == null) {
            graphics.setColor(0x00FFFFFF);
            graphics.fillRect(0, 0, w, h);
            graphics.setColor(0x00000000);
            graphics.drawString("No CMS profile loaded", 2, 2 + Desktop.font.getHeight(), Graphics.TOP | Graphics.LEFT);
            if (status != null) {
                graphics.drawString(status, 2, 2 + 2 * Desktop.font.getHeight(), Graphics.TOP | Graphics.LEFT);
            }
        } else {
            final CharArrayTokenizer tokenizer = this.tokenizer;
            final StringBuffer sb = this.sb;
            final Vector areas = this.areas;
            final int mode = Config.dayNight;
            final float[] valuesFloat = this.valuesFloat;
            int state = 0;

            graphics.setColor(colors[mode * 4]);
            graphics.fillRect(0,0, w, h);
            graphics.setColor(colors[mode * 4 + 1]);

            for (int N = areas.size(), i = 0; i < N; i++) {
                Area area = (Area) areas.elementAt(i);

                sb.delete(0, sb.length());
                tokenizer.init(area.value, area.value.length, DELIMITERS, true);
                int narrowChars = 0;

                while (tokenizer.hasMoreTokens()) {
                    CharArrayTokenizer.Token token = tokenizer.next();
                    if (token.isDelimiter) {
                        state++;
                    } else {
                        graphics.setColor(colors[mode * 4 + 1]);
                        if (state % 2 == 1) {
                            int idx = area.index;
                            if (idx == -1) {
                                String[] keys = TOKENS_float;
                                for (int j = keys.length; --j >= 0; ) {
                                    if (token.equals(keys[j])) {
                                        idx = area.index = (short) j;
                                        break;
                                    }
                                }
                                if (idx == -1) {
                                    if (token.equals(TOKEN_COORDS)) {
                                        area.index = VALUE_COORDS;
                                    } else if (token.equals(TOKEN_TIME)) {
                                        area.index = VALUE_TIME;
                                    } else if (token.equals(TOKEN_TIME_TOTAL)) {
                                        area.index = VALUE_TIME_TOTAL;
                                    } else if (token.equals(TOKEN_STATUS)) {
                                        area.index = VALUE_STATUS;
                                    } else if (token.equals(SIGN_HEXA)) {
                                        area.index = VALUE_SIGN;
                                    } else if (token.equals(TOKEN_WPT_AZI)) {
                                        area.index = VALUE_WPT_AZI;
                                    } else if (token.equals(TOKEN_WPT_DIST)) {
                                        area.index = VALUE_WPT_DIST;
                                    } else if (token.equals(TOKEN_WPT_VMG)) {
                                        area.index = VALUE_WPT_VMG;
                                    } else if (token.equals(TOKEN_WPT_ETA)) {
                                        area.index = VALUE_WPT_ETA;
                                    } else {
                                        area.index = -666;
                                    }
                                    idx = area.index;
                                }
                            }
                            switch (idx) {
                                case VALUE_ALT:
                                case VALUE_COURSE:  {
                                    NavigationScreens.append(sb, (int) valuesFloat[idx]);
                                } break;
                                case VALUE_SPD:
                                case VALUE_SPD_MAX:
                                case VALUE_SPD_AVG: {
                                    float value = valuesFloat[idx];
                                    if (Config.unitsNautical) {
                                        NavigationScreens.append(sb, value / 1.852F, 1);
                                        narrowChars++;
                                    } else if (Config.unitsImperial) {
                                        NavigationScreens.append(sb, value / 1.609F, 1);
                                        narrowChars++;
                                    } else {
                                        NavigationScreens.append(sb, (int) value);
                                    }
                                } break;
                                case VALUE_DIST_T: {
                                    float value = valuesFloat[idx];
                                    if (Config.unitsNautical) {
                                        value /= 1.852F;
                                    } else if (Config.unitsImperial) {
                                        value /= 1.609F;
                                    }
                                    NavigationScreens.append(sb, value, 0);
                                    narrowChars++;
                                } break;
                                case VALUE_ALT_D:
                                case VALUE_COURSE_D:
                                case VALUE_SPD_D:
                                case VALUE_SPD_DMAX:
                                case VALUE_SPD_DAVG: {
                                    float value = valuesFloat[idx];
                                    if (Config.unitsNautical && idx > VALUE_SPD_D) { // spd-d is always in m/s
                                        value /= 1.852F;
                                    } else if (Config.unitsImperial && idx > VALUE_SPD_D) { // spd-d is always in m/s
                                        value /= 1.609F;
                                    }
                                    if (value >= 0F) {
                                        sb.append('+');
                                        graphics.setColor(colors[mode * 4 + 3]);
                                    } else {
                                        graphics.setColor(colors[mode * 4 + 2]);
                                    }
                                    NavigationScreens.append(sb, value, 1);
                                    narrowChars++;
                                } break;
                                case VALUE_COORDS: {
                                    if (valueCoords == null) {
                                        sb.append(MSG_NO_POSITION);
                                    } else {
                                        if (Config.useGeocachingFormat || Config.useUTM) {
                                            NavigationScreens.toStringBuffer(valueCoords, sb);
                                        } else {
                                            QualifiedCoordinates localQc = navigator.getMap().getDatum().toLocal(valueCoords);
                                            NavigationScreens.toStringBuffer(localQc, sb);
                                            QualifiedCoordinates.releaseInstance(localQc);
                                        }
                                    }
                                } break;
                                case VALUE_TIME: {
                                    if (timestamp == 0) {
                                        sb.append(NO_TIME);
                                    } else {
/*
                                        DATE.setTime(timestamp);
                                        CALENDAR.setTime(DATE);
                                        int hour = CALENDAR.get(Calendar.HOUR_OF_DAY);
                                        int min = CALENDAR.get(Calendar.MINUTE);
                                        int sec = CALENDAR.get(Calendar.SECOND);
*/
                                        TIME_CALENDAR.setTime(timestamp);
                                        printTime(sb, TIME_CALENDAR.hour,
                                                      TIME_CALENDAR.minute,
                                                      TIME_CALENDAR.second);
                                    }
                                    narrowChars += 2;
                                } break;
                                case VALUE_TIME_TOTAL: {
                                    if (timestamp == 0) {
                                        sb.append(NO_TIME);
                                    } else {
                                        final int dt = (int) (timestamp - starttime) / 1000;
                                        final int hours = dt / 3600;
                                        final int mins = (dt % 3600) / 60;
                                        final int secs = (dt % 3600) % 60;
                                        printTime(sb, hours, mins, secs);
                                    }
                                    narrowChars += 2;
                                } break;
                                case VALUE_STATUS: {
                                    NavigationScreens.drawProviderStatus(graphics, Desktop.osd.providerStatus,
                                                                         area.x, area.y, 0);
                                } break;
                                case VALUE_WPT_AZI: {
                                    final int azi = navigator.getWptAzimuth();
                                    if (azi < 0F) {
                                        sb.append('?');
                                    } else {
                                        NavigationScreens.append(sb, azi);
                                    }
                                } break;
                                case VALUE_WPT_DIST: {
                                    float dist = navigator.getWptDistance();
                                    if (dist < 0F) {
                                        sb.append('?');
                                    } else {
                                        if (Config.unitsNautical) {
                                            dist /= 1852F;
                                        } else if (Config.unitsImperial) {
                                            dist /= 1609F;
                                        } else {
                                            dist /= 1000F;
                                        }
                                        NavigationScreens.append(sb, dist, 0);
                                        narrowChars++;
                                    }
                                } break;
                                case VALUE_WPT_ETA: {
                                    int azi = navigator.getWptAzimuth();
                                    float dist = navigator.getWptDistance();
                                    if (azi < 0F || dist < 0F || timestamp == 0) {
                                        sb.append(NO_TIME);
                                    } else {
                                        if (dist > 50F) {
                                            double vmg = valuesFloat[VALUE_SPD] * (Math.cos(Math.toRadians(valuesFloat[VALUE_COURSE] - azi)));
                                            long dt = (long) (1000 * (dist / (vmg / 3.6F)));
                                            long eta = timestamp + (dt < 0F ? 2 * -dt : dt);
/*
                                            DATE.setTime(eta);
*/
                                            ETA_CALENDAR.setTime(eta);
                                        } else {
/*
                                            DATE.setTime(timestamp);
*/
                                            ETA_CALENDAR.setTime(timestamp);
                                        }
/*
                                        CALENDAR.setTime(DATE);
                                        int hour = CALENDAR.get(Calendar.HOUR_OF_DAY);
                                        int min = CALENDAR.get(Calendar.MINUTE);
                                        int sec = CALENDAR.get(Calendar.SECOND);
                                        printTime(sb, hour, min, sec);
*/
                                        printTime(sb, ETA_CALENDAR.hour,
                                                      ETA_CALENDAR.minute,
                                                      ETA_CALENDAR.second);
                                    }
                                    narrowChars += 2;
                                } break;
                                case VALUE_WPT_VMG: {
                                    int azi = navigator.getWptAzimuth();
                                    if (azi < 0F) {
                                        sb.append('?');
                                    } else {
                                        double vmg = valuesFloat[VALUE_SPD] * (Math.cos(Math.toRadians(valuesFloat[VALUE_COURSE] - azi)));
                                        if (Config.unitsNautical) {
                                            vmg /= 1.852F;
                                        } else if (Config.unitsImperial) {
                                            vmg /= 1.609F;
                                        }
                                        NavigationScreens.append(sb, vmg, 1);
                                        narrowChars++;
                                    }
                                } break;
                                case VALUE_SIGN: {
                                    sb.append(NavigationScreens.SIGN);
                                } break;
                                default: {
                                    sb.append('{');
                                    sb.append(token.array, token.begin, token.length);
                                    sb.append('}');
                                }
                            }
                        } else {
                            sb.append(token.array, token.begin, token.length);
                        }
                    }
                }

                int l = sb.length();
                if (l > 0) {
                    sb.getChars(0, sb.length(), text, 0);
                    graphics.setClip(area.x, area.y, area.w, area.h);
                    if (area.fontImpl instanceof Font) {
                        Font f = (Font) area.fontImpl;
                        int xoffset = area.ralign ? area.w - f.charsWidth(text, 0, l) : 0;
                        graphics.setFont(f);
                        graphics.drawChars(text, 0, l, area.x + xoffset, area.y, 0);
                    } else {
                        int xoffset = area.ralign ? area.w - (int)(area.cw * l) + (int)(narrowChars * (2D / 3D * area.cw)) : 0;
                        drawChars(graphics, text, l, area.x + xoffset, area.y, area);
                    }
                    graphics.setClip(0, 0, w, h);
                }
            }
        }

        // flush
        flushGraphics();
    }

    private StringBuffer printTime(StringBuffer sb, final int hour, final int min, final int sec) {
        if (hour < 10)
            sb.append('0');
        NavigationScreens.append(sb, hour).append(':');
        if (min < 10)
            sb.append('0');
        NavigationScreens.append(sb, min).append(':');
        if (sec < 10)
            sb.append('0');
        NavigationScreens.append(sb, sec);

        return sb;
    }

    private void drawChars(Graphics graphics, char[] value, final int length,
                           int x, int y, Area area) {
        Image image = (Image) area.fontImpl;
        final float cw = area.cw;
        final int scw = (int) (cw - cw / 5);
        final int icw = (int) cw;
        final int ch = area.ch;
        final char[] charset = CHARSET;
        final int N = charset.length;
        final boolean S60renderer = Config.S60renderer;

        for (int i = 0; i < length; i++) {
            char c = value[i];
            int j = 0;
            for ( ; j < N; j++) {
                if (c == charset[j]) {
                    break;
                }
            }
            if (j < N) {
                int z = c == '.' || c == ':' ? (int) (cw / 3) : 0;
                if (S60renderer) {
                    graphics.setClip(x + (int) (i * cw), y, icw - 2 * z, ch);
                    graphics.drawImage(image,
                                       x + (int) ((i - j) * cw) - z, y,
                                       Graphics.LEFT | Graphics.TOP);
                    graphics.setClip(area.x, area.y, area.w, area.h);
                } else {
                    graphics.drawRegion(image, (int) (j * cw) + z, 0, icw - 2 * z, ch,
                                        Sprite.TRANS_NONE, x + (int) (i * cw), y,
                                        Graphics.LEFT | Graphics.TOP);
                }
                if (c == ' ') {
                    x -= scw;
                } else {
                    x -= 2 * z;
                }
            } else {
                int color = graphics.getColor();
                graphics.setColor(0x00FF0000);
                graphics.drawChar(c, x + (int) (i * cw), y, 0);
                graphics.setColor(color);
            }
        }
    }

    private Object load(String filename) {
        Object result = null;
        File file = null;
        try {
            file = File.open(Connector.open(Config.getFolderProfiles() + filename, Connector.READ));
            if (file.exists()) {
                InputStream in = null;
                try {
                    in = new BufferedInputStream(file.openInputStream(), 512);
                    if (filename.endsWith(".xml")) {
                        result = loadProfile(filename, in);
                    } else if (filename.endsWith(".png")) {
                        result = loadFont(in, file.fileSize());
                    }
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
        } catch (Throwable t) {
            status = t.toString();
//#ifdef __LOG__
            if (log.isEnabled()) log.error("failed to load " + filename);
            t.printStackTrace();
//#endif
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        return result;
    }

    private String loadProfile(String filename, InputStream in) throws IOException, XmlPullParserException {
        // instantiate parser
        KXmlParser parser = new KXmlParser();
        parser.setNameCache(NAME_CACHE);

        try {
            // set input
            parser.setInput(in, null); // null is for encoding autodetection

            // var
            Area area = null;

            // sax
            for (int eventType = parser.next(); eventType != XmlPullParser.END_DOCUMENT; eventType = parser.next()) {
                switch (eventType) {
                    case XmlPullParser.START_TAG: {
                        String tag = parser.getName();
                        if (TAG_FONTS.equals(tag)) {
                            fonts.clear();
                            fontsPng.clear();
                        } else if (TAG_FONT.equals(tag)) {
                            String name = parser.getAttributeValue(null, ATTR_NAME);
                            String source = parser.getAttributeValue(null, ATTR_FILE);
                            if (source != null) {
                                byte[] image = (byte[]) load(source);
                                if (image != null) {
                                    fonts.put(name, image);
                                }
                            } else {
                                source = parser.getAttributeValue(null, ATTR_SYSTEM);
                                int code = Integer.parseInt(source, 16);
                                fonts.put(name, Font.getFont((code & 0xFF0000) >> 16,
                                                             (code & 0x00FF00) >> 8,
                                                             (code & 0x0000FF)));
                            }
                        } else if (TAG_SCREEN.equals(tag)){
                            areas.removeAllElements();
                        } else if (TAG_COLORS.equals(tag)) {
                            int offset = 0;
                            if ("night".equals(parser.getAttributeValue(null, ATTR_MODE))) {
                                offset = 4;
                            }
                            colors[offset] = Integer.parseInt(parser.getAttributeValue(null, ATTR_BGCOLOR), 16);
                            colors[offset + 1] = Integer.parseInt(parser.getAttributeValue(null, ATTR_FGCOLOR), 16);
                            colors[offset + 2] = Integer.parseInt(parser.getAttributeValue(null, ATTR_NXCOLOR), 16);
                            colors[offset + 3] = Integer.parseInt(parser.getAttributeValue(null, ATTR_PXCOLOR), 16);
                        } else if (TAG_AREA.equals(tag)) {
                            area = new Area();
                            area.x = Short.parseShort(parser.getAttributeValue(null, ATTR_X));
                            area.y = Short.parseShort(parser.getAttributeValue(null, ATTR_Y));
                            area.w = Short.parseShort(parser.getAttributeValue(null, ATTR_W));
                            area.h = Short.parseShort(parser.getAttributeValue(null, ATTR_H));
                            area.ralign = "right".equals(parser.getAttributeValue(null, ATTR_ALIGN));
                            String font = parser.getAttributeValue(null, TAG_FONT);
                            if (font != null) {
    /*
                                if (fo instanceof byte[]) {
                                    Image image = (Image) fo;
                                    area.cw = image.getWidth() / CHARSET.length;
                                    area.ch = (short) image.getHeight();
                                }
                                area.font = fo;
    */
                                area.fontName = font;
                                Object fo = fonts.get(font);
                                if (fo instanceof Font) {
                                    area.fontImpl = fo;
                                }
                            } else {
                                area.fontName = "Desktop";
                                area.fontImpl = Desktop.font;
                            }
                        } else if (TAG_VALUE.equals(tag)) {
                            area.value = parser.nextText().toCharArray();
                        }
                    } break;
                    case XmlPullParser.END_TAG: {
                        String tag = parser.getName();
                        if (TAG_AREA.equals(tag)){
                            areas.addElement(area);
                        }
                    } break;
                }
            }
        } finally {
            try {
                parser.close();
            } catch (IOException e) {
                // ignore
            }
        }

        return filename;
    }

    private static byte[] loadFont(InputStream in, long size) throws IOException {
        int length = (int) size;
        byte[] data = new byte[length];
        int offset = 0;

        int count = in.read(data, 0, length);
        while (count > -1 && offset < length) {
            offset += count;
            count = in.read(data, offset, length - offset);
        }

        if (offset < length) {
            throw new IOException("Incomplete read");
        }

        return data;
    }

    private static byte[] colorifyPng(byte[] data, int color) {
        int length = data.length;
        int chunkStart = 8;
        int chunkDataLength = 0;
        int plte = 0;
        int plteStart = 0;
        int plteDataOffset = 0;
        long plteCrc = 0;
        int tRnsStart = find_tRns(data, length);

        if (tRnsStart < 0) {
            return data;
        }

        for (int offset = 8; offset < length; offset++) {
            int i = data[offset];
            char c = (char) i;

            switch (plte) {
                case 0:
                    if (c == 'P') {
                        plte++;
                    }
                break;
                case 1:
                    if (c == 'L') {
                        plte++;
                    } else {
                        plte = 0;
                    }
                break;
                case 2:
                    if (c == 'T') {
                        plte++;
                    } else {
                        plte = 0;
                    }
                break;
                case 3:
                    if (c == 'E') {
                        plte++;
                    } else {
                        plte = 0;
                    }
                break;
            }

            if (plte == -1) {
                // PLTE already processed
                break;
            } else {
                if (offset == chunkStart) {
                    chunkDataLength = 0;
                }
                if (offset >= chunkStart) {
                    if (offset <= chunkStart + 3) {
                        chunkDataLength |= i << 8 * (3 - (offset - chunkStart));
//                        if (offset == chunkStart + 3) {
//                            System.out.println("chunk data length = " + chunkDataLength);
//                        }
                    } else if (plte == 4) {
                        if (plteStart == 0) {
                            plteStart = chunkStart + 4;
                            plteDataOffset = 0;
                        } else if (offset < chunkStart + 4 + 4 + chunkDataLength) {
//                            System.out.println("palette byte at " + offset + " is " + (byte) i);
                            data[offset] = (byte) i;
                            if (plteDataOffset % 3 == 0) {
//                                if (data[offset] != (byte) 0xff || data[offset + 1] != (byte) 0xff || data[offset + 2] != (byte) 0xff) {
                                if (data[tRnsStart + plteDataOffset / 3] != 0) {
                                    data[offset] = (byte) ((color >>> 16) & 0xff);
                                    data[offset + 1] = (byte) ((color >>> 8) & 0xff);
                                    data[offset + 2] = (byte) (color & 0xff);
                                }
                            }
                            plteDataOffset++;
                        } else {
                            if (offset == chunkStart + 4 + 4 + chunkDataLength) {
                                plteCrc = calcCrc(data, plteStart, 4 + chunkDataLength);
                            }
                            data[offset] = (byte) ((plteCrc >>> (8 * (3 - (offset - (chunkStart + 4 + 4 + chunkDataLength))))) & 0x000000FF);
                            if (offset == chunkStart + 4 + 4 + chunkDataLength + 4) {
                                plte = -1;
                            }
                        }
                    } else if (offset == chunkStart + 4 + 4 + chunkDataLength) {
                        chunkStart += 4 + 4 + chunkDataLength + 4;
//                        System.out.println("next chunk at = " + chunkStart);
                    }
                }
            }
        }

        return data;
    }

    private static long calcCrc(byte[] buf, int off, int len) {
        int[] crc_table = CRC_TABLE;
        int c = ~0;
        while (--len >= 0)
            c = crc_table[(c ^ buf[off++]) & 0xff] ^ (c >>> 8);
        return (long) ~c & 0xffffffffL;
    }

    private static int find_tRns(byte[] data, int length) {
        int trns = 0;
        for (int offset = 8; offset < length; offset++) {
            int i = data[offset];
            char c = (char) i;

            switch (trns) {
                case 0:
                    if (c == 't') {
                        trns++;
                    }
                break;
                case 1:
                    if (c == 'R') {
                        trns++;
                    } else {
                        trns = 0;
                    }
                break;
                case 2:
                    if (c == 'N') {
                        trns++;
                    } else {
                        trns = 0;
                    }
                break;
                case 3:
                    if (c == 'S') {
                        trns++;
                    } else {
                        trns = 0;
                    }
                break;
            }

            if (trns == 4) {
                return offset + 1;
            }
        }

        return -1;
    }
}
