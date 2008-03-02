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
import cz.kruch.track.configuration.ConfigurationException;
import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.util.SimpleCalendar;
import cz.kruch.track.util.NmeaParser;
import cz.kruch.track.maps.io.LoaderIO;
import cz.kruch.track.Resources;
import cz.kruch.j2se.io.BufferedInputStream;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Canvas;
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
import java.util.Enumeration;

import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * CMS aka 'Cockpit' screen.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class ComputerView extends View implements Runnable, CommandListener {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("LocatorView");
//#endif

    // xml tags and attrs
    private static final String TAG_UNITS       = "units";
    private static final String TAG_FONT        = "font";
    private static final String TAG_COLORS      = "colors";
    private static final String TAG_SCREEN      = "screen";
    private static final String TAG_AREA        = "area";
    private static final String TAG_VALUE       = "value";
    private static final String ATTR_NAME       = "name";
    private static final String ATTR_FILE       = "file";
    private static final String ATTR_SYSTEM     = "system";
    private static final String ATTR_MODE       = "mode";
    private static final String ATTR_BACKGROUND = "background";
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
    private static final String TOKEN_COORDS        = "coords";
    private static final String TOKEN_TIME          = "time";
    private static final String TOKEN_TIME_T        = "time-t";
    private static final String TOKEN_TIME_T_AUTO   = "time-t-auto";
    private static final String TOKEN_STATUS        = "status";
    private static final String TOKEN_WPT_AZI       = "wpt-azi";
    private static final String TOKEN_WPT_DIST      = "wpt-dist";
    private static final String TOKEN_WPT_VMG       = "wpt-vmg";
    private static final String TOKEN_WPT_ETA       = "wpt-eta";
    private static final String TOKEN_WPT_ALT       = "wpt-alt";
    private static final String TOKEN_WPT_COORDS    = "wpt-coords";
    private static final String TOKEN_SNR           = "snr";

    // float values
    private static final String[] TOKENS_float = {
        "alt",
        "course",
        "spd",
        "spd-max",
        "spd-avg",
        "spd-avg-auto",
        "spd.i",
        "spd.i-max",
        "spd.i-avg",
        "spd.i-avg-auto",
        "spd.d",
        "spd.d-max",
        "spd.d-avg",
        "spd.d-avg-auto",
        "dist-t",
        "alt-d",
        "course-d",
        "spd-d",
        "spd-dmax",
        "spd-davg",
        "asc-t",
        "desc-t",
        "sat",
        "fix"
    };

    // float values indexes
    private static final int VALUE_ALT          = 0;
    private static final int VALUE_COURSE       = 1;
    private static final int VALUE_SPD          = 2;
    private static final int VALUE_SPD_MAX      = 3;
    private static final int VALUE_SPD_AVG      = 4;
    private static final int VALUE_SPD_AVG_AUTO = 5;
    private static final int VALUE_SPDi         = 6;
    private static final int VALUE_SPDi_MAX     = 7;
    private static final int VALUE_SPDi_AVG     = 8;
    private static final int VALUE_SPDi_AVG_AUTO = 9;
    private static final int VALUE_SPDd         = 10;
    private static final int VALUE_SPDd_MAX     = 11;
    private static final int VALUE_SPDd_AVG     = 12;
    private static final int VALUE_SPDd_AVG_AUTO = 13;
    private static final int VALUE_DIST_T       = 14;
    private static final int VALUE_ALT_D        = 15;
    private static final int VALUE_COURSE_D     = 16;
    private static final int VALUE_SPD_D        = 17;
    private static final int VALUE_SPD_DMAX     = 18;
    private static final int VALUE_SPD_DAVG     = 19;
    private static final int VALUE_ASC_T        = 20;
    private static final int VALUE_DESC_T       = 21;
    private static final int VALUE_SAT          = 22;
    private static final int VALUE_FIX          = 23;
    private static final int VALUE_COORDS       = 1000;
    private static final int VALUE_TIME         = 1001;
    private static final int VALUE_TIME_T       = 1002;
    private static final int VALUE_TIME_T_AUTO  = 1003;
    private static final int VALUE_STATUS       = 1004;
    private static final int VALUE_WPT_AZI      = 1005;
    private static final int VALUE_WPT_DIST     = 1006;
    private static final int VALUE_WPT_ETA      = 1007;
    private static final int VALUE_WPT_VMG      = 1008;
    private static final int VALUE_WPT_ALT      = 1009;
    private static final int VALUE_WPT_COORDS   = 1010;
    private static final int VALUE_SNR0         = 1100;
    private static final int VALUE_SIGN         = 2000;

    // charset
    private static final char[] CHARSET = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        ' ', '+', '-', '.', ':', '/', 0xb0, '"', '\'',
        'h', 'k', 'm', 'p', 's'
    };
    private static final char[] DELIMITERS  = { '{', '}' };

    private static final String CMS_SIMPLE_XML  = "cms.simple.xml";
    private static final String SIGN_HEXA       = "0x1E";
    private static final String NO_TIME         = "--:--:--";
    private static final String INF_TIME        = "99:99:99";

    private static final float AUTO_MIN         = 2.4F;

/*
    private static final Calendar CALENDAR  = Calendar.getInstance(TimeZone.getDefault());
    private static final Date DATE = new Date();
*/

    private static int[] CRC_TABLE;

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

    private SimpleCalendar TIME_CALENDAR, ETA_CALENDAR;
    private CharArrayTokenizer tokenizer;
    private char[] text;
    private StringBuffer sb;

    /* profile vars */
    private Integer units;
    private Vector areas;
    private int[] colors;
    private Image backgroundImage;

    /* shared among profiles */
    private Hashtable fonts, fontsPng, backgrounds;

    /* profiles and current profile */
    private String status;
    private Hashtable profiles;

    /* trip vars */
    private volatile QualifiedCoordinates valueCoords, snrefCoords;
    private volatile long timestamp, starttime, /*snreftime,*/ timetauto;
    /*private volatile float altLast, altDiff;*/
    private volatile int counter, sat, fix;
    private volatile boolean fix3d;
    private float[] valuesFloat;

    private float[] spdavgFloat;
    private volatile int spdavgIndex;
    private volatile float spdavgShort;

    public ComputerView(/*Navigator*/Desktop navigator) {
        super(navigator);
    }

    private void initialize() {
        // init calendars
        TIME_CALENDAR = new SimpleCalendar(Calendar.getInstance(TimeZone.getDefault()));
        ETA_CALENDAR = new SimpleCalendar(Calendar.getInstance(TimeZone.getDefault()));

        // init CRC table
        int[] crc_table = CRC_TABLE = new int[256];
        for (int n = 0; n < CRC_TABLE.length; n++) {
            int c = n;
            for (int k = 8; --k >= 0;) {
                if ((c & 1) != 0)
                    c = 0xedb88320 ^ (c >>> 1);
                else
                    c = c >>> 1;
            }
            crc_table[n] = c;
        }

        // init vars
        this.tokenizer = new CharArrayTokenizer();
        this.text = new char[128];
        this.sb = new StringBuffer(128);

        // init shared
        this.fonts = new Hashtable(4);
        this.fontsPng = new Hashtable(2);
        this.backgrounds = new Hashtable(2);

        // trip values
        this.valuesFloat = new float[TOKENS_float.length];
        this.spdavgFloat = new float[8];

        // reset trip values
        reset();
    }

    public void reset() {
        if (!isUsable()) {
            return;
        }
        
        TIME_CALENDAR.reset();
        ETA_CALENDAR.reset();

        valueCoords = snrefCoords = null;
        timestamp = starttime = timetauto = 0;
        counter = sat = fix = 0;
        fix3d = false;
/*
        altLast = Float.NaN;
        altDiff = 0F;
*/
        for (int i = valuesFloat.length; --i >= 0; ) {
            valuesFloat[i] = 0F;
        }
        for (int i = spdavgFloat.length; --i >= 0; ) {
            spdavgFloat[i] = -1F;
        }
        spdavgIndex = 0;
        spdavgShort = 0F;
    }

    public int locationUpdated(Location l) {
        if (!isUsable()) {
            return isVisible ? Desktop.MASK_SCREEN : Desktop.MASK_NONE;
        }

        final long t = l.getTimestamp();

        // calculate time diff
        final long dt = timestamp == 0 ? 0 : (t - timestamp);

        // update times
        timestamp = t;
        if (starttime == 0) { // first record
            starttime = t;
            ETA_CALENDAR.setTimeSafe(t);
        }

        // time since start
        final long tt = t - starttime;

        // everything else needs fix
        fix = l.getFix();
        if (fix > 0) {

            // fix3d
            fix3d = l.isFix3d();

            // sat
            sat = l.getSat();

            // accuracy
            final float hAccuracy = l.getQualifiedCoordinates().getHorizontalAccuracy();
/*
            final float vAccuracy = l.getQualifiedCoordinates().getVerticalAccuracy();
*/

            // calculate distance - emulate static navigation
            float ds = 0F;
            if (snrefCoords == null) {
                snrefCoords = l.getQualifiedCoordinates().clone();
                /*snreftime = timestamp;*/
            } else {
                ds = snrefCoords.distance(l.getQualifiedCoordinates());
                if (hAccuracy == 0F) {
                    if (ds < 50) {
                        ds = 0F;
                    }
                } else if (ds < (3 * hAccuracy + 5)) {
                    ds = 0F;
                } else {
                    QualifiedCoordinates.releaseInstance(snrefCoords);
                    snrefCoords = null;
                    snrefCoords = l.getQualifiedCoordinates().clone();
                    /*snreftime = timestamp;*/
                }
            }

            // update coords
            QualifiedCoordinates.releaseInstance(valueCoords);
            valueCoords = null;
            valueCoords = l.getQualifiedCoordinates().clone();

            // local ref for faster access
            final float[] valuesFloat = this.valuesFloat;

            // alt, alt-d
            final float alt = valueCoords.getAlt();
            if (!Float.isNaN(alt)) {

                // vertical speed
                final float da = alt - valuesFloat[VALUE_ALT];
                if (dt > 0) {
                    valuesFloat[VALUE_ALT_D] = da / (dt / 1000);
                }

                // alt
                valuesFloat[VALUE_ALT] = alt;
            }

            // course, course-d
            final float course = l.getCourse();
            if (course > -1F) {
                valuesFloat[VALUE_COURSE_D] = course - valuesFloat[VALUE_COURSE];
                valuesFloat[VALUE_COURSE] = course;
            }

            // dist-t
            valuesFloat[VALUE_DIST_T] += ds / 1000F;

            // spd, spd-d
            float f = l.getSpeed();
            if (f > -1F) {
                // to km/h
                f *= 3.6F;

                // time and spd-avg 'auto' - when speed over ~1.8 km/h
                if (f > AUTO_MIN) {
                    timetauto += dt;
                    if (valuesFloat[VALUE_DIST_T] > 0.5F || tt > 30000) {
                        valuesFloat[VALUE_SPD_AVG_AUTO] = valuesFloat[VALUE_DIST_T] / ((float) timetauto / (1000 * 3600));
                    }
                }

                // spd, spd-d
                if (dt > 0) {
                    valuesFloat[VALUE_SPD_D] = ((f - valuesFloat[VALUE_SPD]) / 3.6F) / (dt / 1000);
                }
                valuesFloat[VALUE_SPD] = f;

                // spd-avg
                valuesFloat[VALUE_SPD_AVG] = (valuesFloat[VALUE_SPD_AVG] * counter + f) / ++counter;

                // spd-max
                if (f > valuesFloat[VALUE_SPD_MAX]) {
                    valuesFloat[VALUE_SPD_MAX] = f;
                }

                // spd-avg short
                {
                    final float[] spdavgFloat = this.spdavgFloat;
                    spdavgFloat[spdavgIndex++] = f;
                    if (spdavgIndex >= spdavgFloat.length) {
                        spdavgIndex = 0;
                    }
                    int c = 0;
                    spdavgShort = 0F;
                    for (int i = spdavgFloat.length; i-- > 0; ) {
                        float v = spdavgFloat[i];
                        if (v > -1F) {
                            spdavgShort += v;
                            c++;
                        }
                    }
                    spdavgShort /= c;
                }
            }

/*
            // spd-avg as dist / time
            if (valuesFloat[VALUE_DIST_T] > 0.5F || tt > 30000) {
                valuesFloat[VALUE_SPD_AVG] = valuesFloat[VALUE_DIST_T] / ((float) tt / (1000 * 3600));
            }
*/

/*
            // asc/desc - decent accuracy, valid altitude
            if (fix3d && vAccuracy < 15F && !Float.isNaN(alt)) {
                if (!Float.isNaN(altLast)) {
                    altDiff += (alt - altLast);
                    if (altDiff > 25F) {
                        valuesFloat[VALUE_ASC_T] += altDiff;
                        altDiff = 0F;
                    } else if (altDiff < -25F) {
                        valuesFloat[VALUE_DESC_T] += altDiff;
                        altDiff = 0F;
                    }
                }
                altLast = alt;
            }
*/
        }

        return isVisible ? Desktop.MASK_SCREEN : Desktop.MASK_NONE;
    }

    public int handleAction(final int action, final boolean repeated) {
        switch (action) {
            case Canvas.DOWN: {
                if (profiles.size() > 1) {
                    final List list = new List(Resources.getString(Resources.DESKTOP_MSG_PROFILES), List.IMPLICIT);
                    for (final Enumeration e = profiles.keys(); e.hasMoreElements(); ) {
                        final String name = (String) e.nextElement();
                        if (!name.equals(Config.cmsProfile)) {
                            list.append(name, null);
                        }
                    }
                    list.addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Command.CANCEL, 0));
                    list.setCommandListener(this);
                    Desktop.display.setCurrent(list);
                }
            } break;
        }
        
        return super.handleAction(action, repeated);
    }

    /* synchronized to avoid race-cond when rendering */
    public synchronized void run() {

        // regular run?
        if (profiles != null) {

            // copy name and clear
            String name = _profileName;
            _profileName = null;

            // try to load new profile
            try {

                // load new profile
                loadViaCache(name);

                // colorify
                changeDayNight(Config.dayNight);

                // update desktop
                navigator.update(Desktop.MASK_SCREEN);

            } catch (Throwable t) {
                Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_LOAD_PROFILE_FAILED), t, navigator);
            }
            
        } else {

            // not initialized yet
            profiles = new Hashtable(4);

            // try to load profiles
            if (File.isFs()) {
                try {
                    // find profiles
                    fillProfiles(profiles);

                    // got something?
                    String s = null;
                    if (profiles.containsKey(Config.cmsProfile)) {
                        s = Config.cmsProfile;
                    } else if (profiles.containsKey(CMS_SIMPLE_XML)) {
                        s = CMS_SIMPLE_XML;
                    } else if (profiles.size() > 0) {
                        s = (String) profiles.keys().nextElement();
                    }
                    if (s != null) {
                        // finalize initialization
                        initialize();

                        // load default profile
                        loadViaCache(s);

                        // adjust mode
                        changeDayNight(Config.dayNight);
                    }
                } catch (Throwable t) {
                    status = t.toString();
//#ifdef __LOG__
                    t.printStackTrace();
//#endif
                }
            }
        }
    }

    private volatile String _profileName;

    public void commandAction(Command command, Displayable displayable) {
        // restore desktop
        Desktop.display.setCurrent(navigator);

        // load new profile if selected
        if (Command.CANCEL != command.getCommandType()) {

            // get selection
            List list = (List) displayable;
            _profileName = list.getString(list.getSelectedIndex());

            // enqueue load task
            LoaderIO.getInstance().enqueue(this);
        }
    }

    /* synchronized to avoid race-cond when switching profile or rendering */
    public synchronized int changeDayNight(final int dayNight) {
        if (!isUsable()) {
            return isVisible ? Desktop.MASK_SCREEN : Desktop.MASK_NONE;
        }

        if (backgrounds != null) {
            // colorify
            for (Enumeration e = backgrounds.elements(); e.hasMoreElements(); ) {
                // get raw data
                final byte[] data = (byte[]) e.nextElement();
                // colorify
                colorifyPng(data, colors[dayNight * 4 + 1]);
            }
            // release current background
            backgroundImage = null; // gc hint
            // prepare current screen background
            final byte[] data = (byte[]) backgrounds.get(Config.cmsProfile);
            if (data != null) {
                // create image
                try {
                    ByteArrayInputStream in = new ByteArrayInputStream(data);
                    backgroundImage = NavigationScreens.createImage(in);
                    in.close();
                } catch (Throwable t) {
                    // ignore
                }
            }
        }

        if (areas != null) {
            // local refs for faster access
            final Vector areas = this.areas;
            final Hashtable fonts = this.fonts;
            final Hashtable fontsPng = this.fontsPng;

            // release refs for Image fonts
            for (int N = areas.size(), i = 0; i < N; i++) {
                final Area area = (Area) areas.elementAt(i);
                if (area.fontImpl instanceof Image) {
                    area.fontImpl = null;
                }
            }

            // clear bitmap fonts images cache
            fontsPng.clear();
            System.gc(); // GC

            // create image fonts
            for (int N = areas.size(), i = 0; i < N; i++) {
                final Area area = (Area) areas.elementAt(i);
                if (area.fontImpl == null) {
                    try {
                        // get cached bitmap font
                        Image bitmap = (Image) fontsPng.get(area.fontName);
                        if (bitmap == null) { // create fresh new
                            // get raw data
                            final byte[] data = (byte[]) fonts.get(area.fontName);
                            // colorify
                            colorifyPng(data, colors[dayNight * 4 + 1]);
                            //  byte input
                            ByteArrayInputStream in = new ByteArrayInputStream(data);
                            try {
                                // create image
                                bitmap = NavigationScreens.createImage(in);
                                // cache image
                                fontsPng.put(area.fontName, bitmap);
                            } finally {
                                in.close();
                                in = null; // gc hint
                            }
                        }
                        // use it in the area
                        area.fontImpl = bitmap;
                        area.cw = bitmap.getWidth() / CHARSET.length;
                        area.ch = (short) bitmap.getHeight();
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
        }

        return isVisible ? Desktop.MASK_SCREEN : Desktop.MASK_NONE;
    }

    /* synchronized to avoid race-cond when switching profile */
    public synchronized void render(final Graphics graphics, final Font font,
                                    final int mask) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("render");
//#endif

        // local copies for faster access
        final int w = Desktop.width;
        final int h = Desktop.height;

        // default settings
        graphics.setFont(Desktop.font);

        // got profile?
        if (!isUsable() || status != null) {
            graphics.setColor(0x00FFFFFF);
            graphics.fillRect(0, 0, w, h);
            graphics.setColor(0x00000000);
            graphics.drawString(Resources.getString(Resources.DESKTOP_MSG_NO_CMS_PROFILES), 0, 0, Graphics.TOP | Graphics.LEFT);
            if (status != null) {
                graphics.drawString(status, 0, Desktop.font.getHeight(), Graphics.TOP | Graphics.LEFT);
            }
        } else {
            final int mode = Config.dayNight;
            final int[] colors = this.colors;
            final float[] valuesFloat = this.valuesFloat;
            final Vector areas = this.areas;
            final CharArrayTokenizer tokenizer = this.tokenizer;
            final StringBuffer sb = this.sb;
            final char[] text = this.text;

            int state = 0;

            graphics.setColor(colors[mode * 4]);
            graphics.fillRect(0,0, w, h);
            graphics.setColor(colors[mode * 4 + 1]);

            if (backgroundImage != null) {
                graphics.drawImage(backgroundImage, 0, 0, Graphics.TOP | Graphics.LEFT);
            }

            for (int N = areas.size(), i = 0; i < N; i++) {
                final Area area = (Area) areas.elementAt(i);

//#ifdef __LOG__
                if (log.isEnabled()) log.debug("area - font: " + area.fontName + "; value: " + new String(area.value));
//#endif
                sb.delete(0, sb.length());
                tokenizer.init(area.value, area.value.length, DELIMITERS, true);
                int narrowChars = 0;

                while (tokenizer.hasMoreTokens()) {
                    final CharArrayTokenizer.Token token = tokenizer.next();
                    if (token.isDelimiter) {
                        state++;
                    } else {
                        graphics.setColor(colors[mode * 4 + 1]);
                        if (state % 2 == 1) {
                            int idx = area.index;
                            if (idx == -1) {
                                final String[] keys = TOKENS_float;
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
                                    } else if (token.equals(TOKEN_TIME_T)) {
                                        area.index = VALUE_TIME_T;
                                    } else if (token.equals(TOKEN_TIME_T_AUTO)) {
                                        area.index = VALUE_TIME_T_AUTO;
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
                                    } else if (token.equals(TOKEN_WPT_ALT)) {
                                        area.index = VALUE_WPT_ALT;
                                    } else if (token.equals(TOKEN_WPT_COORDS)) {
                                        area.index = VALUE_WPT_COORDS;
                                    } else if (token.startsWith(TOKEN_SNR)) {
                                        area.index = (short) (VALUE_SNR0 + Integer.parseInt(token.toString().substring(3)));
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
                                case VALUE_SPD_AVG:
                                case VALUE_SPD_AVG_AUTO: {
                                    float value = valuesFloat[idx];
                                    switch (units.intValue()) {
                                        case Config.UNITS_IMPERIAL:
                                            value /= 1.609F;
                                        break;
                                        case Config.UNITS_NAUTICAL:
                                            value /= 1.852F;
                                        break;
                                    }
                                    NavigationScreens.append(sb, value, 1);
                                    narrowChars++;
                                } break;
                                case VALUE_SPDi:
                                case VALUE_SPDi_MAX:
                                case VALUE_SPDi_AVG:
                                case VALUE_SPDi_AVG_AUTO: {
                                    float value = valuesFloat[idx - 4];
                                    switch (units.intValue()) {
                                        case Config.UNITS_IMPERIAL:
                                            value /= 1.609F;
                                        break;
                                        case Config.UNITS_NAUTICAL:
                                            value /= 1.852F;
                                        break;
                                    }
                                    NavigationScreens.append(sb, (int) value);
                                } break;
                                case VALUE_SPDd:
                                case VALUE_SPDd_MAX:
                                case VALUE_SPDd_AVG:
                                case VALUE_SPDd_AVG_AUTO: {
                                    float value = valuesFloat[idx - 8];
                                    switch (units.intValue()) {
                                        case Config.UNITS_IMPERIAL:
                                            value /= 1.609F;
                                        break;
                                        case Config.UNITS_NAUTICAL:
                                            value /= 1.852F;
                                        break;
                                    }
                                    value -= (int) value;
                                    value *= 10;
                                    NavigationScreens.append(sb, (int) value);
                                } break;
                                case VALUE_DIST_T: {
                                    float value = valuesFloat[idx];
                                    switch (units.intValue()) {
                                        case Config.UNITS_IMPERIAL:
                                            value /= 1.609F;
                                        break;
                                        case Config.UNITS_NAUTICAL:
                                            value /= 1.852F;
                                        break;
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
                                    if (idx > VALUE_SPD_D) {
                                        switch (units.intValue()) {
                                            case Config.UNITS_IMPERIAL:
                                                value /= 1.609F;
                                            break;
                                            case Config.UNITS_NAUTICAL:
                                                value /= 1.852F;
                                            break;
                                        }
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
                                case VALUE_ASC_T: {
                                    NavigationScreens.append(sb, (long) valuesFloat[idx]);
                                } break;
                                case VALUE_DESC_T: {
                                    NavigationScreens.append(sb, (long) (-1 * valuesFloat[idx]));
                                } break;
                                case VALUE_SAT: {
                                    NavigationScreens.append(sb, sat);
                                } break;
                                case VALUE_FIX: {
                                    char c;
                                    switch (fix) {
                                        case 1:
                                            c = fix3d ? '3' : '2';
                                        break;
                                        case 2:
                                            c = 'D';
                                        break;
                                        default:
                                            c = (char) ('0' + fix);
                                    }
                                    sb.append(c);
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
                                        TIME_CALENDAR.setTimeSafe(timestamp);
                                        printTime(sb, TIME_CALENDAR.get(Calendar.HOUR_OF_DAY),
                                                      TIME_CALENDAR.get(Calendar.MINUTE),
                                                      TIME_CALENDAR.get(Calendar.SECOND));
                                    }
                                    narrowChars += 2;
                                } break;
                                case VALUE_TIME_T:
                                case VALUE_TIME_T_AUTO: {
                                    if (timestamp == 0) {
                                        sb.append(NO_TIME);
                                    } else {
                                        final int dt = (int) (idx == VALUE_TIME_T ? (timestamp - starttime) : timetauto) / 1000;
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
                                        switch (units.intValue()) {
                                            case Config.UNITS_METRIC:
                                                dist /= 1000F;
                                            break;
                                            case Config.UNITS_IMPERIAL:
                                                dist /= 1609F;
                                            break;
                                            case Config.UNITS_NAUTICAL:
                                                dist /= 1852F;
                                            break;
                                        }
                                        NavigationScreens.append(sb, dist, 0);
                                        narrowChars++;
                                    }
                                } break;
                                case VALUE_WPT_ETA: {
                                    final int azi = navigator.getWptAzimuth();
                                    final float dist = navigator.getWptDistance();
                                    if (azi < 0F || dist < 0F || timestamp == 0) {
                                        sb.append(NO_TIME);
                                    } else {
                                        if (dist > Config.wptProximity) {
                                            final double vmg = spdavgShort/*valuesFloat[VALUE_SPD]*/ * (Math.cos(Math.toRadians(valuesFloat[VALUE_COURSE] - azi)));
                                            if (vmg > 0F) {
                                                final long dt = (long) (1000 * (dist / (vmg / 3.6F)));
                                                long eta = timestamp + (dt < 0F ? 2 * -dt : dt);
                                                ETA_CALENDAR.setTime(eta);
                                                printTime(sb, ETA_CALENDAR.get(Calendar.HOUR_OF_DAY),
                                                              ETA_CALENDAR.get(Calendar.MINUTE),
                                                              ETA_CALENDAR.get(Calendar.SECOND));
                                            } else {
                                                sb.append(INF_TIME);
                                            }
                                        } else {
                                            ETA_CALENDAR.setTimeSafe(timestamp);
                                            printTime(sb, ETA_CALENDAR.get(Calendar.HOUR_OF_DAY),
                                                          ETA_CALENDAR.get(Calendar.MINUTE),
                                                          ETA_CALENDAR.get(Calendar.SECOND));
                                        }
                                    }
                                    narrowChars += 2;
                                } break;
                                case VALUE_WPT_VMG: {
                                    final int azi = navigator.getWptAzimuth();
                                    if (azi < 0F) {
                                        sb.append('?');
                                    } else {
                                        double vmg = spdavgShort/*valuesFloat[VALUE_SPD]*/ * (Math.cos(Math.toRadians(valuesFloat[VALUE_COURSE] - azi)));
                                        switch (units.intValue()) {
                                            case Config.UNITS_IMPERIAL:
                                                vmg /= 1.609F;
                                            break;
                                            case Config.UNITS_NAUTICAL:
                                                vmg /= 1.852F;
                                            break;
                                        }
                                        NavigationScreens.append(sb, vmg, 1);
                                        narrowChars++;
                                    }
                                } break;
                                case VALUE_WPT_ALT: {
                                    final float alt = navigator.getWptAlt();
                                    if (Float.isNaN(alt)) {
                                        sb.append('?');
                                    } else {
                                        NavigationScreens.append(sb, (int) alt);
                                    }
                                } break;
                                case VALUE_WPT_COORDS: {
                                    final QualifiedCoordinates qc = navigator.getWptCoords();
                                    if (qc == null) {
                                        sb.append(MSG_NO_POSITION);
                                    } else {
                                        NavigationScreens.toStringBuffer(qc, sb);
                                    }
                                } break;
                                case VALUE_SNR0:
                                case VALUE_SNR0 + 1:
                                case VALUE_SNR0 + 2:
                                case VALUE_SNR0 + 3:
                                case VALUE_SNR0 + 4:
                                case VALUE_SNR0 + 5:
                                case VALUE_SNR0 + 6:
                                case VALUE_SNR0 + 7:
                                case VALUE_SNR0 + 8:
                                case VALUE_SNR0 + 9:
                                case VALUE_SNR0 + 10:
                                case VALUE_SNR0 + 11: {
                                    NavigationScreens.append(sb, NmeaParser.snr[idx - VALUE_SNR0]);
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

                final int l = sb.length();
                if (l > 0) {
                    sb.getChars(0, sb.length(), text, 0);
                    graphics.setClip(area.x, area.y, area.w, area.h);
                    if (area.fontImpl instanceof Font) {
                        final Font f = (Font) area.fontImpl;
                        final int xoffset = area.ralign ? area.w - f.charsWidth(text, 0, l) : 0;
                        graphics.setFont(f);
                        graphics.drawChars(text, 0, l, area.x + xoffset, area.y, 0);
                    } else {
                        final int xoffset = area.ralign ? area.w - (int)(area.cw * l) + (int)(narrowChars * (2D / 3D * area.cw)) : 0;
                        drawChars(graphics, text, l, area.x + xoffset, area.y, area);
                    }
                    graphics.setClip(0, 0, w, h);
                }
            }
        }

        // flush
        flushGraphics();
    }

    private boolean isUsable() {
        return valuesFloat != null && profiles != null && profiles.size() != 0 && Config.cmsProfile != null && Config.cmsProfile.length() != 0;
    }

    private StringBuffer printTime(final StringBuffer sb,
                                   final int hour, final int min, final int sec) {
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

    private void drawChars(final Graphics graphics, final char[] value,
                           final int length, int x, int y,
                           final Area area) {
        final Image image = (Image) area.fontImpl;
        final float cw = area.cw;
        final int scw = (int) (cw - cw / 5);
        final int icw = (int) cw;
        final int ch = area.ch;
        final char[] charset = CHARSET;
        final int N = charset.length;
        final boolean S60renderer = Config.S60renderer;

        for (int i = 0; i < length; i++) {
            final char c = value[i];
            int j = 0;
            for ( ; j < N; j++) {
                if (c == charset[j]) {
                    break;
                }
            }
            if (j < N) {
                final int z = c == '.' || c == ':' ? (int) (cw / 3) : 0;
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
                final int color = graphics.getColor();
                graphics.setColor(0x00FF0000);
                graphics.drawChar(c, x + (int) (i * cw), y, 0);
                graphics.setColor(color);
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("unknown char: '" + c + "'");
//#endif
            }
        }
    }

    private String loadViaCache(final String filename) {
        Object o = profiles.get(filename);
        if (o == this) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("load profile: " + filename);
//#endif
            colors = new int[8];
            areas = new Vector(4, 4);
            load(filename);
            if (units == null) {
                units = new Integer(Config.units);
            }
            profiles.put(filename, new Object[]{ areas, colors, units });
        } else {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("found cached profile: " + filename);
//#endif
            Object[] cached = (Object[]) o;
            areas = (Vector) cached[0];
            colors = (int[]) cached[1];
            units = (Integer) cached[2];
        }

        Config.cmsProfile = filename;
        try {
            Config.update(Config.VARS_090);
        } catch (ConfigurationException e) {
            // ignore
        }

        return filename;
    }

    private Object load(final String filename) {
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

    private void fillProfiles(final Hashtable profiles) throws IOException {
        File dir = null;

        try {
            // open stores directory
            dir = File.open(Connector.open(Config.getFolderProfiles(), Connector.READ));

            // list file stores
            if (dir.exists()) {
                for (final Enumeration e = dir.list(); e.hasMoreElements(); ) {
                    final String name = ((String) e.nextElement()).toLowerCase();
                    if (name.startsWith("cms.") && name.endsWith(".xml")) {
                        profiles.put(name, this/* null not allowed */);
                    }
                }
            }
        } finally {
            // close dir
            if (dir != null) {
                try {
                    dir.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private String loadProfile(final String filename, final InputStream in) throws IOException, XmlPullParserException {
        // instantiate parser
        KXmlParser parser = new KXmlParser(NAME_CACHE);

        try {
            // set input
            parser.setInput(in, null); // null is for encoding autodetection

            // var
            Area area = null;

            // sax
            for (int eventType = parser.next(); eventType != XmlPullParser.END_DOCUMENT; eventType = parser.next()) {
                switch (eventType) {
                    case XmlPullParser.START_TAG: {
                        final String tag = parser.getName();
                        if (TAG_AREA.equals(tag)) {
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
                        } else if (TAG_UNITS.equals(tag)) {
                            final String system = parser.getAttributeValue(null, ATTR_SYSTEM);
                            if ("metric".equals(system)) {
                                units = new Integer(Config.UNITS_METRIC);
                            } else if ("imperial".equals(system)) {
                                units = new Integer(Config.UNITS_IMPERIAL);
                            } else if ("nautical".equals(system)) {
                                units = new Integer(Config.UNITS_NAUTICAL);
                            }
                        } else if (TAG_FONT.equals(tag)) {
                            final String name = parser.getAttributeValue(null, ATTR_NAME);
                            if (!fonts.containsKey(name)) {
                                String source = parser.getAttributeValue(null, ATTR_FILE);
                                if (source != null) {
                                    final byte[] image = (byte[]) load(source);
                                    if (image != null) {
                                        fonts.put(name, image);
                                    }
                                } else {
                                    source = parser.getAttributeValue(null, ATTR_SYSTEM);
                                    if (source != null) {
                                        final int code = Integer.parseInt(source, 16);
                                        fonts.put(name, Font.getFont((code >> 16) & 0x000000FF,
                                                                     (code >> 8) & 0x000000FF,
                                                                     (code) & 0x0000FF));
                                    }
                                }
                            }
                        } else if (TAG_COLORS.equals(tag)) {
                            int offset = 0;
                            if ("night".equals(parser.getAttributeValue(null, ATTR_MODE))) {
                                offset = 4;
                            }
                            colors[offset] = Integer.parseInt(parser.getAttributeValue(null, ATTR_BGCOLOR), 16);
                            colors[offset + 1] = Integer.parseInt(parser.getAttributeValue(null, ATTR_FGCOLOR), 16);
                            colors[offset + 2] = Integer.parseInt(parser.getAttributeValue(null, ATTR_NXCOLOR), 16);
                            colors[offset + 3] = Integer.parseInt(parser.getAttributeValue(null, ATTR_PXCOLOR), 16);
                        } else if (TAG_SCREEN.equals(tag)) {
                            String background = parser.getAttributeValue(null, ATTR_BACKGROUND);
                            if (background != null) {
                                final byte[] image = (byte[]) load(background);
                                if (image != null) {
                                    backgrounds.put(filename, image);
                                }
                            }
                        }
                    } break;
                    case XmlPullParser.END_TAG: {
                        final String tag = parser.getName();
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

    private static byte[] loadFont(final InputStream in, final long size) throws IOException {
        final int length = (int) size;
        final byte[] data = new byte[length];
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

    private static void colorifyPng(final byte[] data, final int color) {
        final int length = data.length;
        final int tRnsStart = find_tRns(data, length);

        if (tRnsStart > 0) {
            int chunkStart = 8;
            int chunkDataLength = 0;
            int plte = 0;
            int plteStart = 0;
            int plteDataOffset = 0;
            long plteCrc = 0;

            for (int offset = 8; offset < length; offset++) {
                final int i = data[offset];
                final char c = (char) i;

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
        }
    }

    private static long calcCrc(final byte[] buf, int off, int len) {
        int[] crc_table = CRC_TABLE;
        int c = ~0;
        while (--len >= 0)
            c = crc_table[(c ^ buf[off++]) & 0xff] ^ (c >>> 8);
        return (long) ~c & 0xffffffffL;
    }

    private static int find_tRns(final byte[] data, final int length) {
        int trns = 0;
        for (int offset = 8; offset < length; offset++) {
            final int i = data[offset];
            final char c = (char) i;

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
