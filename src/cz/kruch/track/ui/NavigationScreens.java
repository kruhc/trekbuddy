// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.util.Mercator;
import cz.kruch.track.util.ExtraMath;
import cz.kruch.track.util.SimpleCalendar;
import cz.kruch.track.configuration.Config;

import javax.microedition.lcdui.game.Sprite;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

import api.file.File;
import api.io.BufferedInputStream;
import api.location.QualifiedCoordinates;
import api.location.Location;
import api.location.CartesianCoordinates;
import api.location.ProjectionSetup;
import api.location.Datum;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.Vector;

/**
 * UI helper.
 *
 * @author kruhc@seznam.cz
 */
public final class NavigationScreens {

    /*
     * public constants
     */

    private static final char[] digits;

    public static final char[] DIST_STR_M      = { ' ', 'm', ' ' };
    public static final char[] DIST_STR_KM     = { ' ', 'k', 'm', ' ' };
    public static final char[] DIST_STR_MI     = { ' ', 'm', 'i', ' ' };
    public static final char[] DIST_STR_NMI    = { ' ', 'M', ' ' };
    public static final char[] DIST_STR_FT     = { ' ', 'f', 't', ' ' };

    public static final char SIGN         = 0xb0;
    public static final char PLUSMINUS    = 0xb1;
    public static final char DELTA_D      = 0x394;
    public static final char DELTA_d      = 0x3b4;

    public static final int ARROW_COURSE  = 0;
    public static final int ARROW_NAVI    = 1;

/*
    private static final Calendar CALENDAR = Calendar.getInstance(TimeZone.getDefault());
    private static final Date DATE = new Date();
*/
    private static final SimpleCalendar CALENDAR = new SimpleCalendar(Calendar.getInstance(TimeZone.getDefault()));

    private static final char[] STR_KN  = { ' ', 'k', 'n', ' ' };
    private static final char[] STR_MPH = { ' ', 'm', 'p', 'h', ' ' };
    private static final char[] STR_KMH = { ' ', 'k', 'm', '/', 'h', ' ' };
    private static final char[] STR_M   = { ' ', 'm' };
    private static final char[] STR_FT  = { ' ', 'f', 't' };

    private static final String RES_CROSSHAIRS = "/resources/crosshairs.png";
    private static final String RES_ARROWS = "/resources/arrows.png";
    private static final String RES_NAVIWS = "/resources/naviws.png";
    private static final String RES_POIS = "/resources/pois.png";
    private static final String RES_WPT = "/resources/wpt.png";
    private static final String RES_SELECTED = "/resources/selected.png";
    private static final String RES_BULLETS = "/resources/bullets.png";
    private static final String RES_NLOCK = "/resources/nlock.png";
    private static final String RES_SYMBOLS = "/resources/symbols.png";
    private static final String RES_ZOOMS = "/resources/zooms.png";
    private static final String RES_GUIDES = "/resources/guides.png";

    static {
	    digits = "0123456789".toCharArray();
    }

    /*
     * image cache
     */

    static Image crosshairs; // FIXME visibility
    static Image nlock; // FIXME visibility
    static Image waypoint; // FIXME visibility
    static Image selected; // FIXME visibility
    private static Image[] arrows;
    private static Image symbols;
    private static Image pois;
    private static Image providers;
    private static Image zooms;
    private static Image guides;

    // number formatter buffer
    private static final char[] print = new char[64];

    // private vars
    private static int[] arrowSize;
    private static int[] arrowSize2;
    private static boolean[] arrowsFull;
    private static int poiSize;
    private static int symbolSize;
    private static int zoomSize;

    // public (???) vars
    public static int useCondensed;
    static int bulletSize;
    static int nlockSize2;
    static int wptSize2;
    static int guideSize;
    static int gdx, gdOffset;
    static int selectedSize2;

    public static void initialize() throws IOException {
        // init image cache
        crosshairs = createImage(RES_CROSSHAIRS);
        arrows = new Image[] {
            createImage(RES_ARROWS),
            createImage(RES_NAVIWS)
        };
        providers = createImage(RES_BULLETS);
        pois = createImage(RES_POIS);
        symbols = createImage(RES_SYMBOLS);
        waypoint = createImage(RES_WPT);
        nlock = createImage(RES_NLOCK);
        selected = createImage(RES_SELECTED);

        // setup vars
        arrowSize = new int[2];
        arrowSize2 = new int[2];
        arrowsFull = new boolean[2];
        setupVars(ARROW_COURSE);
        setupVars(ARROW_NAVI);
        wptSize2 = waypoint.getHeight() >> 1;
        bulletSize = providers.getHeight();
        poiSize = pois.getHeight();
        nlockSize2 = nlock.getHeight() >> 1;
        symbolSize = symbols.getHeight();
        selectedSize2 = selected.getHeight() >> 1;
    }

    public static void initializeForTouch() throws IOException { // 2nd init for touchscreens
        // init image cache
//#if !__SYMBIAN__ && !__RIM__ && !__ANDROID__ && !__CN1__
        if (Config.zoomSpotsMode != 0)
//#endif
        {
            zooms = createImage(RES_ZOOMS);
            zoomSize = zooms.getHeight();
        }
//#if !__SYMBIAN__ && !__RIM__ && !__ANDROID__ && !__CN1__
        if (Config.guideSpotsMode != 0)
//#endif
        {
            guides = createImage(RES_GUIDES);
            guideSize = guides.getHeight();
        }
    }

    static int customize(final Vector resources) throws IOException {
        int i = 0;

        Image image = loadImage(resources, RES_CROSSHAIRS);
        if (image != null) {
            crosshairs = null;
            crosshairs = image;
            i++;
        }
        image = loadImage(resources, RES_ARROWS);
        if (image != null) {
            arrows[ARROW_COURSE] = null;
            arrows[ARROW_COURSE] = image;
            setupVars(ARROW_COURSE);
            i++;
        }
        image = loadImage(resources, RES_NAVIWS);
        if (image != null) {
            arrows[ARROW_NAVI] = null;
            arrows[ARROW_NAVI] = image;
            setupVars(ARROW_NAVI);
            i++;
        }
        image = loadImage(resources, RES_SYMBOLS);
        if (image != null) {
            symbols = null;
            symbols = image;
            symbolSize = image.getHeight();
            i++;
        }
        image = loadImage(resources, RES_WPT);
        if (image != null) {
            waypoint = null;
            waypoint = image;
            wptSize2 = image.getHeight() >> 1;
            i++;
        }
        image = loadImage(resources, RES_POIS);
        if (image != null) {
            pois = null;
            pois = image;
            poiSize = image.getHeight();
            i++;
        }
        image = loadImage(resources, RES_NLOCK);
        if (image != null) {
            nlock = null;
            nlock = image;
            nlockSize2 = image.getHeight() >> 1;
            i++;
        }
        image = loadImage(resources, RES_SELECTED);
        if (image != null) {
            selected = null;
            selected = image;
            selectedSize2 = selected.getHeight() >> 1;
            i++;
        }

        if (i > 0 && Config.forcedGc) {
            System.gc(); // conditional
        }

        return i;
    }

    static int customizeForTouch(final Vector resources) throws IOException {
        int i = 0;

//#if !__SYMBIAN__ && !__RIM__ && !__ANDROID__ && !__CN1__
        if (Config.zoomSpotsMode != 0)
//#endif
        {
            final Image image = loadImage(resources, RES_ZOOMS);
            if (image != null) {
                zooms = null;
                zooms = image;
                zoomSize = image.getHeight();
                i++;
            }
        }
//#if !__SYMBIAN__ && !__RIM__ && !__ANDROID__ && !__CN1__
        if (Config.guideSpotsMode != 0)
//#endif
        {
            final Image image = loadImage(resources, RES_GUIDES);
            if (image != null) {
                guides = null;
                guides = image;
                guideSize = image.getHeight();
                i++;
            }
        }

        if (i > 0 && Config.forcedGc) {
            System.gc(); // conditional
        }

        return i;
    }

    private static void setupVars(final int idx) {
        arrowSize[idx] = arrows[idx].getHeight();
        arrowsFull[idx] = arrows[idx].getWidth() == (arrows[idx].getHeight() / 4) * 9;
        if (arrowsFull[idx]) {
            arrowSize[idx] /= 4;
        }
        arrowSize2[idx] = arrowSize[idx] >> 1;
    }

    private static Image loadImage(final Vector resources, final String name) throws IOException {
        final String sub = name.substring(11);
        if (Config.resourceExist(resources, sub)) {
            return loadImage(Config.FOLDER_RESOURCES, sub);
        }
        return null;
    }

    static Image loadImage(final String folder, final String name) throws IOException {
        Image image = null;
        File file = null;

        try {
            file = File.open(Config.getFileURL(folder, name));
            if (file.exists()) {
                InputStream in = null;
                try {
                    image = Image.createImage(in = new BufferedInputStream(file.openInputStream(), 4096));
                } finally {
                    if (in != null) {
                        File.closeQuietly(in);
                    }
                }
            }
        } finally {
            File.closeQuietly(file);
        }

        return image;
    }

    static void drawArrow(final int type, final Graphics graphics, final float course,
                          final int x, final int y, final int anchor) {
        final Image image = arrows[type];
        final int size = arrowSize[type];
        final int size2 = arrowSize2[type];
        final boolean full = arrowsFull[type];
        final int courseInt = ((int) course) % 360;

        if (!full) {
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
                    throw new IllegalArgumentException("Course over 360?");
            }
            try { // Siemens hack - occasionally throws exception
                graphics.drawRegion(image,
                                    ci * size, 0, size, size,
                                    ti, x - size2, y - size2, anchor);
            } catch (IllegalArgumentException e) {
                // ignore
            }
        } else {
            int ci = courseInt / 10;
            int cr = courseInt % 10;
            if (cr > 5) {
                ci++;
                if (ci == 36) {
                    ci = 0;
                }
            }
            graphics.setClip(x - size2, y - size2, size, size);
            graphics.drawImage(image,
                               x - (ci % 9) * size - size2, y - (ci / 9) * size - size2,
                               anchor);
            graphics.setClip(0, 0, Desktop.width, Desktop.height);
        }
    }

    static void drawWaypoint(final Graphics graphics, final int x, final int y,
                             final int anchor) {
        graphics.drawImage(waypoint, x - wptSize2, y - wptSize2, anchor);
    }

    static void drawPOI(final Graphics graphics, final int status,
                        final int x, final int y, final int anchor) {
        final int poiSize = NavigationScreens.poiSize;
        final int poiSize2 = poiSize >> 1;

//#ifdef __ALT_RENDERER__
        if (Config.S60renderer) {
//#endif
            graphics.setClip(x - poiSize2, y - poiSize2, poiSize, poiSize);
            graphics.drawImage(pois,
                               x - status * poiSize - poiSize2, y - poiSize2,
                               anchor);
            graphics.setClip(0, 0, Desktop.width, Desktop.height);
//#ifdef __ALT_RENDERER__
        } else {
            graphics.drawRegion(pois,
                                status * poiSize, 0, poiSize, poiSize,
                                Sprite.TRANS_NONE, x - poiSize2, y - poiSize2, anchor);
        }
//#endif        
    }

    static void drawProviderStatus(final Graphics graphics, final int status,
                                   final int x, final int y, final int anchor) {
        final int bulletSize = NavigationScreens.bulletSize;
        final int ci = status & 0x0000000f;

//#ifdef __ALT_RENDERER__
        if (Config.S60renderer) {
//#endif
            graphics.setClip(x, y, bulletSize, bulletSize);
            graphics.drawImage(providers,
                               x - ci * bulletSize, y,
                               anchor);
            graphics.setClip(0, 0, Desktop.width, Desktop.height);
//#ifdef __ALT_RENDERER__
        } else {
            graphics.drawRegion(providers,
                                ci * bulletSize, 0, bulletSize, bulletSize,
                                Sprite.TRANS_NONE, x, y, anchor);
        }
//#endif        
    }

    static void drawBacklightStatus(final Graphics graphics) {
        if (guides == null || Config.guideSpotsMode == 0) {
            final int symbolSize = NavigationScreens.symbolSize;
            graphics.setClip(Desktop.width - symbolSize - 3, Desktop.height - symbolSize - 3, symbolSize, symbolSize);
            drawBar(graphics, Desktop.height - symbolSize - 3, Desktop.height - 3, 0);
            graphics.drawImage(symbols,
                               Desktop.width - symbolSize - 3, Desktop.height - symbolSize - 3,
                               Graphics.TOP | Graphics.LEFT);
            graphics.setClip(0, 0, Desktop.width, Desktop.height);
        }
    }

    static void drawKeylockStatus(final Graphics graphics) {
        if (guides == null || Config.guideSpotsMode == 0) {
            final int symbolSize = NavigationScreens.symbolSize;
            if (symbols.getWidth() >= (symbolSize << 1)) { // complete status icon
                graphics.setClip(Desktop.width - (symbolSize << 1) - 3, Desktop.height - symbolSize - 3,
                                 symbolSize, symbolSize);
                drawBar(graphics, Desktop.height - symbolSize - 3, Desktop.height - 3, 0);
                graphics.drawImage(symbols,
                                   Desktop.width - ((symbolSize << 1) + symbolSize) - 3, Desktop.height - symbolSize - 3,
                                   Graphics.TOP | Graphics.LEFT);
                graphics.setClip(0, 0, Desktop.width, Desktop.height);
            }
        }
    }

    static void drawZoomSpots(final Graphics graphics) {
        final boolean visible = Config.zoomSpotsMode == 1 || (Config.zoomSpotsMode == 2 && Desktop.screen.beenPressed);
        if (visible && zooms != null && !Desktop.screen.isKeylock()) {
            final int size = NavigationScreens.zoomSize;
            final int j = Desktop.width / 5;
            final int i = Desktop.height / 10;
            final int insx = (j - size) >> 1;
            final int insy = (i - size) >> 1;
            final int i8y = i * 7 + insy;
            final int j4x = j * 4 + insx;
            graphics.setClip(insx, i8y, size, size);
            graphics.drawImage(zooms, insx, i8y, Graphics.TOP | Graphics.LEFT);
            graphics.setClip(j4x, i8y, size, size);
            graphics.drawImage(zooms, j4x - size, i8y, Graphics.TOP | Graphics.LEFT);
            graphics.setClip(0, 0, Desktop.width, Desktop.height);
            // debug: render grid
/*
            graphics.setColor(0);
            for (int a = 0; a < 5; a++) {
                graphics.drawLine(a * j, 0, a * j, Desktop.height);
            }
            for (int a = 0; a < 10; a++) {
                graphics.drawLine(0, a * i, Desktop.width, a * i);
            }
*/
        }
    }

    static void drawGuideSpots(final Graphics graphics, final boolean all) {
        final boolean visible = Config.guideSpotsMode == 1 || (Config.guideSpotsMode == 2 && Desktop.screen.beenPressed);
        if (visible && guides != null) {
            final boolean locked = Desktop.screen.isKeylock();
            final int size = NavigationScreens.guideSize;
            final int w = Desktop.width;
            final int h = Desktop.height;
            final int y = h - size - 3 + gdOffset;
            // will be used for pointer-to-key mapping
            gdx = (w - 5 * size - 2 * 3) / 4;
            // 0. icons bar
            if (all) {
                drawBar(graphics, h - size - 2 * 3, Desktop.height, gdOffset);
            }
            // 1. home / lock
            int x = 3;
            if (all) {
                graphics.setClip(x, y, size, size);
                if (locked) {
                    graphics.drawImage(guides, x - size, y, Graphics.TOP | Graphics.LEFT);
                } else {
                    graphics.drawImage(guides, x, y, Graphics.TOP | Graphics.LEFT);
                }
            }
            // 2. waypoints
            x += size + gdx;
            if (all && !locked) {
                graphics.setClip(x, y, size, size);
                graphics.drawImage(guides, x - 4 * size, y, Graphics.TOP | Graphics.LEFT);
            }
            // 3. context
            x += size + gdx;
            if (!locked) {
                graphics.setClip(x, y, size, size);
                graphics.drawImage(guides, x - 2 * size, y, Graphics.TOP | Graphics.LEFT);
            }
            // 4. backlight
            x += size + gdx;
            if (all && !locked) {
                graphics.setClip(x, y, size, size);
                if (cz.kruch.track.ui.nokia.DeviceControl.getBacklightStatus() == 0) {
                    graphics.drawImage(guides, x - 5 * size, y, Graphics.TOP | Graphics.LEFT);
                } else {
                    graphics.drawImage(guides, x - 6 * size, y, Graphics.TOP | Graphics.LEFT);
                }
            }
            // 5. next screen
            x += size + gdx;
            if (all && !locked) {
                graphics.setClip(x, y, size, size);
                graphics.drawImage(guides, x - 3 * size, y, Graphics.TOP | Graphics.LEFT);
            }
            // restore clip
            graphics.setClip(0, 0, w, h);
        }
    }

    static void drawBar(final Graphics graphics, final int y1, final int y2, final int yOffset) {
//#ifndef __CN1__
        final int bh = Desktop.bar.getHeight();
        for (int y = y1; y < y2; ) {
            graphics.drawImage(Desktop.bar, 0, y + yOffset, Graphics.TOP | Graphics.LEFT);
            y += bh;
        }
//#endif        
    }

    static StringBuffer toStringBuffer(final Location l, final StringBuffer sb) {
/*
        DATE.setTime(timestamp);
        CALENDAR.setTime(DATE);
        final int hour = CALENDAR.get(Calendar.HOUR_OF_DAY);
        final int min = CALENDAR.get(Calendar.MINUTE);
*/
        final SimpleCalendar calendar = CALENDAR;
        calendar.setTimeSafe(l.getTimestamp());
        final int hour = calendar.get(Calendar.HOUR_OF_DAY);
        final int min = calendar.get(Calendar.MINUTE);

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
            switch (Config.units) {
                case Config.UNITS_METRIC: {
                    if (!Float.isNaN(speed)) {
                        append(sb, speed * 3.6F, 1).append(STR_KMH);
                    }
                    if (!Float.isNaN(alt)) {
                        append(sb, alt, 1).append(STR_M);
                    }
                } break;
                case Config.UNITS_IMPERIAL: {
                    if (!Float.isNaN(speed)) {
                        append(sb, speed * 3.6F / 1.609F, 1).append(STR_MPH);
                    }
                    if (!Float.isNaN(alt)) {
                        append(sb, alt / 0.3048F, 1).append(STR_FT);
                    }
                } break;
                case Config.UNITS_NAUTICAL: {
                    if (!Float.isNaN(speed)) {
                        append(sb, speed * 3.6F / 1.852F, 1).append(STR_KN);
                    }
                    if (!Float.isNaN(l.getCourse())) {
                        append(sb, (int) l.getCourse()).append(SIGN);
                    }
                } break;
            }
        }

        return sb;
    }

    static boolean isGrid() {
        return ProjectionSetup.contextProjection.isCartesian() && ProjectionSetup.contextProjection.code != api.location.ProjectionSetup.PROJECTION_MERCATOR;
    }

    static StringBuffer printTo(final QualifiedCoordinates qc, final StringBuffer sb) {
        switch (Config.cfmt) {
            case Config.COORDS_MAP_GRID: {
                if (isGrid()) {
                    final QualifiedCoordinates localQc = Datum.contextDatum.toLocal(qc);
                    toGrid(localQc, sb);
                    QualifiedCoordinates.releaseInstance(localQc);
                    break; // break here!
                }
            } // no break here - this is not(isGrid) path!
            case Config.COORDS_UTM: {
                toUTM(qc, sb);
                break;
            }
            case Config.COORDS_GC_LATLON:
            case Config.COORDS_GPX_LATLON: {
                toLL(qc, sb);
            } break;
            default: {
                final QualifiedCoordinates localQc = Datum.contextDatum.toLocal(qc);
                toLL(localQc, sb);
                QualifiedCoordinates.releaseInstance(localQc);
            }
        }

        return sb;
    }

    private static StringBuffer toLL(final QualifiedCoordinates qc, final StringBuffer sb) {
        if (useCondensed != 0 && Config.decimalPrecision) { // narrow or SGX75
            toCondensedLL(qc, sb);
        } else { // decent devices
            toFullLL(qc, sb);
        }

        return sb;
    }

    private static StringBuffer toGrid(final QualifiedCoordinates qc, final StringBuffer sb) {
        final CartesianCoordinates gridCoords = Mercator.LLtoGrid(qc);
        if (gridCoords.zone != null) {
            sb.append(gridCoords.zone).append(' ');
        }
        zeros(sb, gridCoords.easting, 10000);
        append(sb, ExtraMath.round(gridCoords.easting));
        sb.append('E').append(' ');
        zeros(sb, gridCoords.northing, 10000);
        append(sb, ExtraMath.round(gridCoords.northing));
        sb.append('N');
        CartesianCoordinates.releaseInstance(gridCoords);

        return sb;
    }

    private static StringBuffer toUTM(final QualifiedCoordinates qc,
                                      final StringBuffer sb) {
        final CartesianCoordinates utmCoords = Mercator.LLtoUTM(qc);
        sb.append(utmCoords.zone).append(' ');
        append(sb, ExtraMath.round(utmCoords.easting));
        sb.append('E').append(' ');
        append(sb, ExtraMath.round(utmCoords.northing));
        sb.append('N');
        CartesianCoordinates.releaseInstance(utmCoords);

        return sb;
    }

    private static StringBuffer toCondensedLL(final QualifiedCoordinates qc, final StringBuffer sb) {
        if (useCondensed == 1) {
            sb.append(qc.getLat() > 0D ? 'N' : 'S');
        }
        append(sb, QualifiedCoordinates.LAT, qc.getLat(), true);
        sb.deleteCharAt(sb.length() - 1);
        sb.append(' ');
        if (useCondensed == 1) {
            sb.append(qc.getLon() > 0D ? 'E' : 'W');
        }
        append(sb, QualifiedCoordinates.LON, qc.getLon(), true);
        sb.deleteCharAt(sb.length() - 1);

        return sb;
    }

    private static StringBuffer toFullLL(final QualifiedCoordinates qc, final StringBuffer sb) {
        sb.append(qc.getLat() > 0D ? 'N' : 'S').append(' ');
        append(sb, QualifiedCoordinates.LAT, qc.getLat(), Config.decimalPrecision);
        sb.append(' ');
        sb.append(qc.getLon() > 0D ? 'E' : 'W').append(' ');
        append(sb, QualifiedCoordinates.LON, qc.getLon(), Config.decimalPrecision);

        return sb;
    }

    private static StringBuffer append(final StringBuffer sb, final int type,
                                       final double value, final boolean hp) {
        if (Config.cfmt == Config.COORDS_GPX_LATLON) {
            appendAsDD(sb, type, value, hp);
        } else if (Config.cfmt == Config.COORDS_GC_LATLON) {
            appendAsDDMM(sb, type, value, hp);
        } else {
            appendAsDDMMSS(sb, type, value, hp);
        }

        return sb;
    }

    private static StringBuffer appendAsDDMMSS(final StringBuffer sb, final int type,
                                               final double value, final boolean hp) {
        double l = Math.abs(value);
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

        append(sb, h).append(SIGN);
        append(sb, m).append('\'');
        append(sb, s);
        if (hp) {
            sb.append('.');
            append(sb, ss);
        }
        sb.append('"');

        return sb;
    }

    private static StringBuffer appendAsDDMM(final StringBuffer sb, final int type,
                                             final double value, final boolean hp) {
        double l = Math.abs(value);
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
        append(sb, h).append(SIGN);
        append(sb, m).append('.');
        if (dec < 100) {
            sb.append('0');
        }
        if (dec < 10) {
            sb.append('0');
        }
        append(sb, dec);

        return sb;
    }

    private static StringBuffer appendAsDD(final StringBuffer sb, final int type,
                                           final double value, final boolean hp) {
        double l = Math.abs(value);
        int h = (int) Math.floor(l);
        l -= h;
        l *= 1000000D;
        int hh = (int) Math.floor(l);

        if (type == QualifiedCoordinates.LON && h < 100) {
            sb.append('0');
        }
        if (h < 10) {
            sb.append('0');
        }
        append(sb, h).append('.');
        append(sb, hh).append(SIGN);

        return sb;
    }

    static StringBuffer printTo(final StringBuffer sb, final QualifiedCoordinates qc,
                                final int mask, final boolean decimalPrecision) {
        // local coords
        final QualifiedCoordinates localQc = Datum.contextDatum.toLocal(qc);

        switch (Config.cfmt) {
            case Config.COORDS_MAP_GRID: {
                if (isGrid()) {
                    final CartesianCoordinates gridCoords = Mercator.LLtoGrid(localQc);
                    if ((mask & 1) != 0) {
                        append(sb, ExtraMath.round(gridCoords.easting)).append('E');
                    } else if ((mask & 2) != 0) {
                        append(sb, ExtraMath.round(gridCoords.northing)).append('N');
                    }
                    CartesianCoordinates.releaseInstance(gridCoords);
                    break;
                }
            } // no break here for not(isGrid) path!
            case Config.COORDS_UTM: {
                final CartesianCoordinates utmCoords = Mercator.LLtoUTM(qc);
                if ((mask & 1) != 0) {
                    append(sb, ExtraMath.round(utmCoords.easting)).append('E');
                } else if ((mask & 2) != 0) {
                    append(sb, ExtraMath.round(utmCoords.northing)).append('N');
                }
                CartesianCoordinates.releaseInstance(utmCoords);
            } break;
            default: {
                if ((mask & 1) != 0) {
                    final double lat;
                    if (Config.cfmt == Config.COORDS_GC_LATLON || Config.cfmt == Config.COORDS_GPX_LATLON) {
                        lat = qc.getLat();
                    } else {
                        lat = localQc.getLat();
                    }
                    sb.append(lat > 0D ? 'N' : 'S').append(' ');
                    append(sb, QualifiedCoordinates.LAT, lat, decimalPrecision);
                } else if ((mask & 2) != 0) {
                    final double lon;
                    if (Config.cfmt == Config.COORDS_GC_LATLON || Config.cfmt == Config.COORDS_GPX_LATLON) {
                        lon = qc.getLon();
                    } else {
                        lon = localQc.getLon();
                    }
                    sb.append(lon > 0D ? 'E' : 'W').append(' ');
                    append(sb, QualifiedCoordinates.LON, lon, decimalPrecision);
                }
            }
        }

        // release
        QualifiedCoordinates.releaseInstance(localQc);

        return sb;
    }

    static StringBuffer append(final StringBuffer sb, final int index, final int grade) {
        zeros(sb, index, grade);
        append(sb, (long) index);

        return sb;
    }

    private static StringBuffer zeros(final StringBuffer sb, final double d,
                                      final int c) {
        int i = ExtraMath.grade(d);
        while (i < c) {
            sb.append('0');
            i *= 10;
        }

        return sb;
    }

    public static StringBuffer append(final StringBuffer sb, double value, int precision) {
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

    public static StringBuffer append(final StringBuffer sb, long value) {
        if (value < 0) {
            sb.append('-');
        } else {
            value = -value;
        }

        final char[] digits = NavigationScreens.digits;

        if (value > -10) {
            sb.append(digits[(int)(-value)]);
        } else if (value > -100) {
            sb.append(digits[(int)(-(value / 10))]);
            sb.append(digits[(int)(-(value % 10))]);
        } else {
            synchronized (print) {
                final char[] print = NavigationScreens.print;
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

    static StringBuffer printDistance(final StringBuffer sb, final float distance) {
        if (!Float.isNaN(distance)) {
            switch (Config.units) {
                case Config.UNITS_METRIC: {
                    if (distance >= 10000F) { // dist > 10 km
                        append(sb, distance / 1000F, 1).append(DIST_STR_KM);
                    } else if (distance < 5F) {
                        append(sb, distance, 1).append(DIST_STR_M);
                    } else {
                        append(sb, (int) distance).append(DIST_STR_M);
                    }
                } break;
                case Config.UNITS_IMPERIAL: {
                    append(sb, distance / 1609F, 0).append(DIST_STR_MI);
                } break;
                case Config.UNITS_NAUTICAL: {
                    append(sb, distance / 1852F, 0).append(DIST_STR_NMI);
                } break;
            }
        } else {
            sb.append('?');
        }

        return sb;
    }

    static StringBuffer printAltitude(final StringBuffer sb, final float altitude) {
        if (!Float.isNaN(altitude)) {
            switch (Config.units) {
                case Config.UNITS_METRIC: {
                    append(sb, (int) altitude).append(DIST_STR_M);
                } break;
                case Config.UNITS_IMPERIAL:
                case Config.UNITS_NAUTICAL: {
                    append(sb, altitude / 0.3048F, 0).append(DIST_STR_FT);
                } break;
            }
        }

        return sb;
    }

    private static int adjustPrecision(final double value, int precision) {
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

    private static Image createImage(final String resource) throws IOException {
        return Image.createImage(resource);
    }
}
