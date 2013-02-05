// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.Resources;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.location.TripStatistics;
import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.util.SimpleCalendar;
import cz.kruch.track.util.NmeaParser;
import cz.kruch.track.util.NakedVector;
import cz.kruch.track.util.Mercator;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.game.Sprite;

import api.file.File;
import api.io.BufferedInputStream;
import api.location.Location;
import api.location.QualifiedCoordinates;
import api.location.CartesianCoordinates;
import api.lang.Int;

import java.io.InputStream;
import java.io.IOException;
import java.util.Vector;
import java.util.Hashtable;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.TimerTask;
import java.util.Enumeration;

import org.kxml2.io.HXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

//#ifdef __HECL__
import cz.kruch.track.hecl.ControlledInterp;

import org.hecl.HeclException;
import org.hecl.Thing;
import org.hecl.Interp;
import org.hecl.IntThing;
import org.hecl.DoubleThing;
import org.hecl.NumberThing;
import org.hecl.RealThing;
import org.hecl.LongThing;
import org.hecl.StringThing;
import org.hecl.CodeThing;
import org.hecl.FloatThing;
//#endif

/**
 * CMS aka computer screen.
 *
 * @author kruhc@seznam.cz
 */
final class ComputerView extends View
                         implements Runnable, CommandListener
//#ifdef __HECL__
                         , ControlledInterp.Lookup
//#endif
                                             {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("ComputerView");
//#endif

    // xml tags and attrs
    private static final String TAG_UNITS       = "units";
    private static final String TAG_FONT        = "font";
    private static final String TAG_COLORS      = "colors";
    private static final String TAG_SCREEN      = "screen";
    private static final String TAG_AREA        = "area";
    private static final String TAG_VALUE       = "value";
    private static final String TAG_SCRIPT      = "script";
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
    private static final String ATTR_IMAGE      = "image";

    // special values
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
    private static final String TOKEN_PRN           = "prn";
    private static final String TOKEN_TIMER         = "timer";
    private static final String TOKEN_LAT           = "lat";
    private static final String TOKEN_LON           = "lon";
    private static final String TOKEN_WPT_LAT       = "wpt-lat";
    private static final String TOKEN_WPT_LON       = "wpt-lon";
    private static final String TOKEN_WPT_NAME      = "wpt-name";
    private static final String TOKEN_WPT_CMT       = "wpt-cmt";
    private static final String TOKEN_WPT_SYM       = "wpt-sym";
    private static final String TOKEN_WPT_IMG       = "wpt-img";
    private static final String TOKEN_WPT_ALT_DIFF  = "wpt-alt-diff";
    private static final String TOKEN_XDR           = "xdr.";
    private static final String TOKEN_PACE          = "pace";
    private static final String TOKEN_COURSE_SLIDING    = "course.g-sliding";
    private static final String TOKEN_WPT_AZI_SLIDING   = "wpt-azi.g-sliding";

    // numeric values
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
        "fix",
        "pdop",
        "hdop",
        "vdop",
        "satv",
        "dgps"
    };

    // numeric values indexes
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
    private static final int VALUE_PDOP         = 24;
    private static final int VALUE_HDOP         = 25;
    private static final int VALUE_VDOP         = 26;
    private static final int VALUE_SATV         = 27;
    private static final int VALUE_DGPS         = 28;

    // special values indexes
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
    private static final int VALUE_TIMER        = 1011;
    private static final int VALUE_LAT          = 1012;
    private static final int VALUE_LON          = 1013;
    private static final int VALUE_WPT_LAT      = 1014;
    private static final int VALUE_WPT_LON      = 1015;
    private static final int VALUE_WPT_NAME     = 1016;
    private static final int VALUE_WPT_CMT      = 1017;
    private static final int VALUE_WPT_SYM      = 1018;
    private static final int VALUE_WPT_IMG      = 1019;
    private static final int VALUE_WPT_ALT_DIFF = 1020;
    private static final int VALUE_PACE         = 1021;
    private static final int VALUE_COURSE_SLIDING   = 1022;
    private static final int VALUE_WPT_AZI_SLIDING  = 1023;

    // even more special
    private static final int VALUE_SNR0         = 1100; // 12 slots
    private static final int VALUE_PRN0         = 1112; // 12 slots
    private static final int VALUE_XDR          = 1200;

    // extra special
    private static final int VALUE_SIGN         = 2000;
//#ifdef __HECL__
    private static final int VALUE_HECL         = 2001;
//#endif                                                 

    // charset
    private static final char[] CHARSET/* = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        ' ', '+', '-', '.', ':', '/', 0xb0, '"', '\'',
        'h', 'k', 'm', 'p', 's'
    }*/;
    static {
        CHARSET = "0123456789 +-.:/°\"\'hkmps".toCharArray();
    }
    private static final char[] DELIMITERS  = { '{', '}' };

    private static final String CMS_SIMPLE_XML  = "cms.simple.xml";
    private static final String SIGN_HEXA       = "0x1E";
    private static final String NO_TIME         = "--:--:--";
    private static final String INF_TIME        = "99:99:99";

    private static final float AUTO_MIN_SPD     = 2.4F;
    private static final long AUTO_MIN_T        = 30000;
    private static final int SHORT_AVG_DEPTH    = 30; // 30 sec (for 1 Hz NMEA)
    private static final int SHORT_AVG_DEPTH_MIN = 15; // SHORT_AVG_DEPTH / 2
    private static final int MAX_TEXT_LENGTH    = 128;


/*
    private static final Calendar CALENDAR  = Calendar.getInstance(TimeZone.getDefault());
    private static final Date DATE = new Date();
*/

//#ifdef __HECL__
    private static final String EVENT_ON_LOCATION_UPDATED   = "onLocationUpdated";
    private static final String EVENT_ON_TRACKING_START     = "onTrackingStart";
    private static final String EVENT_ON_TRACKING_STOP      = "onTrackingStop";
    private static final String EVENT_ON_STATUS_CHANGED     = "onStatusChanged";
    private static final String EVENT_ON_KEY_PRESS          = "onKeyPress";
/*
    private static final String EVENT_ON_TIMER              = "onTimer";
*/
 //#endif

    private static int[] CRC_TABLE;

    private static final class Area {
        public short x, y, w, h;
        public String fontName;
        public Object fontImpl;
        public char[] value;
//#ifdef __HECL__
        public CodeThing scriptlet;
        public String scriptvar;
//#endif
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
    private StringBuffer sb;
    private char[] text;

    /* profile vars */
    private Int iunits;
    private Integer units;
    private NakedVector areas;
    private Image backgroundImage;
    private int[] colors;

    /* graphics - shared among profiles */
    private Hashtable fonts, backgrounds;

//#ifdef __HECL__
    /* HECL - shared among profiles */
    private ControlledInterp interp;
    private NakedVector heclOnUpdated, heclOnStart, heclOnStop, heclOnKey/*, heclOnTimer*/;
    private Thing[] heclArgvOnUpdated, heclArgvOnStart, heclArgvOnStop, heclArgvVoid, heclArgvOnKey/*, heclArgvOnTimer*/;
    private Hashtable heclResults;
/*
    private TimerTask heclTimer;
*/
    private final api.lang.Int interpLock = new api.lang.Int(0);
//#endif

    /* profiles and current profile */
    private String status, initialProfile;
    private Hashtable profiles;
    private String[] profilesNames; // TODO get rid of
    private int profileIdx;

    /* current wpt image */
    private Image wptImg;
    private int wptImgId;

    /* trip vars */
    private volatile QualifiedCoordinates valueCoords, snrefCoords;
    private volatile long timestamp, starttime, /*snreftime,*/ timetauto;
    private volatile float altLast, altDiff;
    private volatile int sat, fix, dgps;
    private float[] valuesFloat;

    /* short term avg speed */
    private long[] spdavgLong;
    private float[] spdavgFloat;
    private volatile int spdavgIndex;
    private volatile float spdavgShort;

    /* very private members */
    private TimerTask rotator;
    private boolean rotate;
    private boolean activeIO;

    ComputerView(/*Navigator*/Desktop navigator) {
        super(navigator);
        this.iunits = new Int(Config.units);
        this.rotate = true;
        init();
    }

//#ifdef __B2B__

    void b2b_reset() {
        profiles = null;
        initialProfile = null;
    }

//#endif

    private void init() {
        // init calendars
        this.TIME_CALENDAR = new SimpleCalendar(Calendar.getInstance(TimeZone.getDefault()));
        this.ETA_CALENDAR = new SimpleCalendar(Calendar.getInstance(TimeZone.getDefault()));

        // trip values
        this.valuesFloat = new float[TOKENS_float.length];
        this.spdavgFloat = new float[SHORT_AVG_DEPTH];
        this.spdavgLong = new long[SHORT_AVG_DEPTH];
    }

    private void initializeForProfiles() {
        // init CRC table for PNG colorification
        int[] crc_table = CRC_TABLE = new int[256];
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

        // init vars for theme parsing
        this.tokenizer = new CharArrayTokenizer();
        this.text = new char[MAX_TEXT_LENGTH];
        this.sb = new StringBuffer(MAX_TEXT_LENGTH);

        // init themes shared var
        this.fonts = new Hashtable(8);
        this.backgrounds = new Hashtable(8);
    }

//#ifdef __HECL__
/*
    void setVisible(boolean b) {
        super.setVisible(b);
        if (heclOnTimer != null) {
            if (heclTimer != null) {
                heclTimer.cancel();
                heclTimer = null; // gc hint
            }
            if (b) {
                Desktop.timer.schedule(heclTimer = new Timer(this), 100, 100);
            }
        }
    }
*/
//#endif

    void trackingStarted() {
        // reset trip vars
        reset();

        // have profiles
        if (isUsable()) {
//#ifdef __HECL__
            // invoke handlers
            invokeHandlers(interp, heclOnStart, heclArgvOnStart);
//#endif
        }
    }

//#ifdef __HECL__

    void trackingStopped() {
        // invoke handlers
        invokeHandlers(interp, heclOnStop, heclArgvOnStop);
    }

//#endif

    public int locationUpdated(Location l) {
        // got any profile or plugin // HACK
        if (isUsable() || havePlugins()) {

            // timestamp
            final long t = l.getTimestamp();

            // calculate time diff
            final float dt = timestamp == 0 ? 0 : (t - timestamp);

            // update times
            if (timestamp == 0 || dt >= 1000) {
                timestamp = t;
            }
            if (starttime == 0) { // first record
                starttime = t;
                ETA_CALENDAR.setTimeSafe(t);
            }

            // everything else needs fix
            fix = l.getFix();
            if (fix > 0) {

                // sat, dgps
                sat = l.getSat();
                dgps = l.getFixQuality();

                // coords
                final QualifiedCoordinates qc = l.getQualifiedCoordinates();

                // accuracy
                final float hAccuracy = qc.getHorizontalAccuracy();
                final float vAccuracy = qc.getVerticalAccuracy();

                // calculate distance - emulate static navigation
                float ds = 0F, dsn = 0F;
                final Location avgLoc = TripStatistics.getLast(TripStatistics.TERM_LONG);
                if (avgLoc != null) {
                    final QualifiedCoordinates avgQc = avgLoc.getQualifiedCoordinates();
                    if (snrefCoords == null) {
                        snrefCoords = avgQc._clone();
                    } else {
                        ds = dsn = snrefCoords.distance(avgQc);
                        if (ds < 25) {
                            ds = 0F;
                        } else {
                            snrefCoords.copyFrom(avgQc);
                        }
                    }
                }

                // update coords
                if (valueCoords == null) {
                    valueCoords = qc._clone();
                } else {
                    valueCoords.copyFrom(qc);
                }

                // local ref for faster access
                final float[] valuesFloat = this.valuesFloat;

                // *dop
                if (!Float.isNaN(NmeaParser.pdop)) {
                    valuesFloat[VALUE_PDOP] = NmeaParser.pdop;
                } // no way to get it otherwise
                if (!Float.isNaN(NmeaParser.hdop)) {
                    valuesFloat[VALUE_HDOP] = NmeaParser.hdop;
                } else { // use stupid formula
                    valuesFloat[VALUE_HDOP] = hAccuracy / 5;
                }
                if (!Float.isNaN(NmeaParser.vdop)) {
                    valuesFloat[VALUE_VDOP] = NmeaParser.vdop;
                } else { // use stupid formula
                    valuesFloat[VALUE_VDOP] = vAccuracy / 5;
                }

                // alt, alt-d, altdiff
                final float alt = qc.getAlt();
                if (!Float.isNaN(alt) && dt >= 1000) {

                    // vertical speed (alt-d), alt
                    valuesFloat[VALUE_ALT_D] = (alt - valuesFloat[VALUE_ALT]) / (dt / 1000);
                    valuesFloat[VALUE_ALT] = alt;

                    // asc-t/desc-t
                    altDiff += (alt - altLast);
                    if (altDiff >= 50F) {
                        valuesFloat[VALUE_ASC_T] += altDiff;
                        altDiff = 0F;
                    } else if (altDiff <= -50F) {
                        valuesFloat[VALUE_DESC_T] += altDiff;
                        altDiff = 0F;
                    }
                    altLast = alt;
                }

                // course-d, course
                final float course = l.getCourse();
                if (!Float.isNaN(course) && dt >= 1000) {

                    // course-d, course
                    valuesFloat[VALUE_COURSE_D] = course - valuesFloat[VALUE_COURSE];
                    valuesFloat[VALUE_COURSE] = course;
                }

                // dist-t
                valuesFloat[VALUE_DIST_T] += ds / 1000F;

                // spd-avg
                final float dtt = t - starttime;
                if (dtt > 0) {
                    valuesFloat[VALUE_SPD_AVG] = (valuesFloat[VALUE_DIST_T] * 1000) / (dtt / 1000) * 3.6F;
                }

                // spd, spd-d
                float speed = l.getSpeed();
                if (!Float.isNaN(speed)) {
                    
                    // to km/h
                    speed *= 3.6F;

                    // 'auto' time and spd-avg - when speed over %AUTO_MIN% km/h
                    if (speed > AUTO_MIN_SPD && dt >= 1000) {
                        timetauto += dt;
                        if (valuesFloat[VALUE_DIST_T] > 0.5F || (t - starttime) > AUTO_MIN_T) {
                            valuesFloat[VALUE_SPD_AVG_AUTO] = valuesFloat[VALUE_DIST_T] / ((float) timetauto / (1000 * 3600));
                        }
                    }

                    // spd, spd-d
                    if (dt >= 1000) {
                        valuesFloat[VALUE_SPD_D] = ((speed - valuesFloat[VALUE_SPD]) / 3.6F) / (dt / 1000);
                        valuesFloat[VALUE_SPD] = speed;
                    }

                    // spd-avg // TODO check this looks really weird
//                    valuesFloat[VALUE_SPD_AVG] = (valuesFloat[VALUE_SPD_AVG] * counter + speed) / ++counter;

                    // spd-max
                    if (speed > valuesFloat[VALUE_SPD_MAX]) {
                        valuesFloat[VALUE_SPD_MAX] = speed;
                    }

                    // spd-avg short
                    if (dt >= 1000) {
                        calcSpdAvgShort(speed, dsn < 25 ? 0 : dsn, t);
                    }
                }

/*
                // spd-avg as dist / time
                if (valuesFloat[VALUE_DIST_T] > 0.5F || tt > 30000) {
                    valuesFloat[VALUE_SPD_AVG] = valuesFloat[VALUE_DIST_T] / ((float) tt / (1000 * 3600));
                }
*/
            } else {

                // invalidate
                sat = dgps = 0;

                // spg-avg short
                if (dt >= 1000) {
                    calcSpdAvgShort(0F, 0F, t);
                }

            }

//#ifdef __HECL__

            // have handler
            if (heclOnUpdated != null) {
                // dont hang
                synchronized (interpLock) {
                    // avoid waiting for lock
                    if (interpLock.intValue() == 0) {
                        // enter
                        interpLock.inc();
                        // invalidate vars
                        interp.cacheversion++;
                        // invoke users handlers
                        invokeHandlers(interp, heclOnUpdated, heclArgvOnUpdated);
                        // leave
                        interpLock.dec();
                    }
                }
            }
//#endif
        }

        return Desktop.MASK_SCREEN;
    }

    int configChanged() {

        // check CMS cycling
        if (rotator != null) {
            rotator.cancel();
            rotator = null;
        }
        if (Config.cmsCycle > 0) {
            Desktop.schedule(rotator = new Rotator(), Config.cmsCycle * 1000, Config.cmsCycle * 1000);
        }

        return super.configChanged();
    }

    int handleAction(final int action, final boolean repeated) {
        if (repeated) {
            return Desktop.MASK_NONE;
        }

        final int nprofiles = profiles.size();
        if (nprofiles > 1) {
            switch (action) {
//#ifdef __HECL__
                case Canvas.UP: {
                    ((IntThing) heclArgvOnKey[1].getVal()).set(action);
                    invokeHandlers(interp, heclOnKey, heclArgvOnKey);
                } break;
//#endif
                case Canvas.LEFT: {
                    synchronized (this) {
                        if (--profileIdx < 0) {
                            profileIdx = profilesNames.length - 1;
                        }
                        _profileName = profilesNames[profileIdx];
                    }
                    Desktop.getDiskWorker().enqueue(this);
                } break;
                case Canvas.RIGHT: {
                    synchronized (this) {
                        if (++profileIdx == profilesNames.length) {
                            profileIdx = 0;
                        }
                        _profileName = profilesNames[profileIdx];
                    }
                    Desktop.getDiskWorker().enqueue(this);
                } break;
                case Canvas.DOWN: {
                    final List list = new List(Resources.getString(Resources.DESKTOP_MSG_PROFILES), List.IMPLICIT);
                    final String[] names = this.profilesNames;
                    for (int N = names.length, i = 0; i < N; i++) {
                        list.setSelectedIndex(list.append(names[i], null), names[i].equals(Config.cmsProfile));
                    }
                    list.addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Desktop.CANCEL_CMD_TYPE, 0));
                    list.setCommandListener(this);
                    Desktop.display.setCurrent(list);
                } break;
                case Canvas.FIRE: {
                    rotate = !rotate;
                } break;
            }
        }
//#ifdef __HECL__
          else if (nprofiles > 0 && action == Canvas.UP) {
            ((IntThing) heclArgvOnKey[1].getVal()).set(action);
            invokeHandlers(interp, heclOnKey, heclArgvOnKey);
        }
//#endif
        
        return Desktop.MASK_NONE;
    }

    int handleKey(final int keycode, final boolean repeated) {
        switch (keycode) {
            case Canvas.KEY_NUM7: {
                if (!repeated) {
                    navigator.previousWpt();
                }
            } break;
            case Canvas.KEY_NUM9: {
                if (!repeated) {
                    navigator.nextWpt();
                }
            } break;
        }

        return Desktop.MASK_SCREEN;
    }

    /* synchronized to avoid race-cond when rendering */
    public synchronized void run() {

        // regular run?
        if (profiles != null) {

            // shit happens sometimes TODO but why?!?
            if (_profileName == null) {
//#ifdef __ANDROID__
                android.util.Log.e(cz.kruch.track.TrackingMIDlet.APP_TITLE, "profileName is null");
//#endif
                return;
            }

            // I/O active
            activeIO = true;

            // try to load new profile
            try {

                // load new profile
                loadViaCache(_profileName);

                // prepare profile
                prepare(Config.dayNight);

                // update desktop
                navigator.update(Desktop.MASK_SCREEN);

            } catch (Throwable t) {
//#ifdef __LOG__
                t.printStackTrace();
//#endif
                Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_LOAD_PROFILE_FAILED), t, Desktop.screen);

            } finally {

                // TODO why??
                _profileName = null;

                // I/O active
                activeIO = false;
            }
            
        } else {

            // not initialized yet
            Hashtable ps = new Hashtable(4);

            // try to load profiles
            if (Config.dataDirExists) {

                // I/O active
                activeIO = true;

                try {

                    // find profiles
                    findProfiles(ps);

                    // got something?
                    if (ps.size() > 0) {

                        // finalize initialization
                        initializeForProfiles();

//#ifdef __HECL__
                        // initialize interp
                        interp = new ControlledInterp(Config.FOLDER_PROFILES, false);
                        interp.addFallback(this, iunits);
                        interp.addCommand("print", new PrintCommand(this));

                        // debug help
                        heclResults = new Hashtable(16);
                        
                        // find handlers
                        try {
                            interp.loadUserScripts(Config.getFolderURL(Config.FOLDER_PROFILES));
                        } catch (Throwable t) {
//#ifdef __LOG__
                            t.printStackTrace();
                            if (log.isEnabled()) log.error("failed to load user scripts: " + t);
//#endif
                        }

                        // get handlers
                        heclOnUpdated = interp.getHandlers(EVENT_ON_LOCATION_UPDATED);
                        heclOnStart = interp.getHandlers(EVENT_ON_TRACKING_START);
                        heclOnStop = interp.getHandlers(EVENT_ON_TRACKING_STOP);
                        heclOnKey = interp.getHandlers(EVENT_ON_KEY_PRESS);
//                        heclOnTimer = interp.getHandlers(EVENT_ON_TIMER);
                        heclArgvOnUpdated = new Thing[]{ new Thing(EVENT_ON_LOCATION_UPDATED) };
                        heclArgvOnStart = new Thing[]{ new Thing(EVENT_ON_TRACKING_START) };
                        heclArgvOnStop = new Thing[]{ new Thing(EVENT_ON_TRACKING_STOP) };
                        heclArgvOnKey = new Thing[]{ new Thing(EVENT_ON_KEY_PRESS), IntThing.create(0) };
//                        heclArgvOnTimer = new Thing[]{ new Thing(EVENT_ON_TIMER), LongThing.create(0) };

                        // turn on optimizations
                        interp.optimize();

//#endif /* __HECL__ */

//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("use profiles");
//#endif

                        // use new set already
                        profiles = ps;

                        // look for default profile
                        final String s;
                        if (profiles.containsKey(Config.cmsProfile)) {
                            s = Config.cmsProfile;
                            for (int i = profilesNames.length; --i >= 0; ) {
                                if (s.equals(profilesNames[i])) {
                                    profileIdx = i; /* sync index */
                                    break;
                                }
                            }
                        } else if (profiles.containsKey(CMS_SIMPLE_XML)) {
                            s = CMS_SIMPLE_XML;
                        } else {
                            s = (String) profiles.keys().nextElement();
                        }

//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("start profile: " + s);
//#endif

                        // got some profile?
                        if (s != null) {

                            // load default profile
                            loadViaCache(s);

                            // prepare profile
                            prepare(Config.dayNight);

                            // this is initial profile
                            initialProfile = s;

                            // autoswitch
                            if (Config.cmsCycle > 0) {
                                Desktop.schedule(rotator = new Rotator(),
                                                 Config.cmsCycle * 1000, Config.cmsCycle * 1000);
                            }
                        }

                        // redraw
                        if (isVisible) {
                            navigator.update(Desktop.MASK_SCREEN);
                        }
                    }

                } catch (Throwable t) {
//#ifdef __LOG__
                    t.printStackTrace();
//#endif
                    // save this error
                    status = t.toString();

                } finally {

                    // I/O active
                    activeIO = false;

                }
            }

            // use new set
            profiles = ps;
        }
    }

    /* synchronized to avoid race-cond with rendering */
    public synchronized void prepare(final int dayNight) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("prepare: " + Config.cmsProfile);
//#endif

        // panel stuff
        if (backgrounds != null) {

            // get rid of existing panel
            backgroundImage = null;

            // prepare current screen background
            final byte[] data = (byte[]) backgrounds.get(Config.cmsProfile);
            if (data != null) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("prepare panel for " + Config.cmsProfile);
//#endif
                // create image
                try {

                    // colorify panel
                    colorifyPng(data, colors[dayNight * 4 + 1]);

                    // create image
                    backgroundImage = Image.createImage(data, 0, data.length);

                } catch (Throwable t) {
                    // ignore
//#ifdef __LOG__
                    if (log.isEnabled()) log.error("failure", t);
                    t.printStackTrace();
//#endif
                }

            }
        }

        // areas stuff
        final Vector areas = this.areas;
        if (areas != null) {

            // local cache
            final Hashtable cache = new Hashtable(4);

            // make sure image fonts are ready
            for (int i = areas.size(); --i >= 0; ) {
                final Area area = (Area) areas.elementAt(i);
                if (area.fontImpl == null || area.fontImpl instanceof Image) {

                    // gc hint;
                    area.fontImpl = null;

                    // set area's bitmap font
                    try {

                        // get cached bitmap font
                        Image bitmap = (Image) cache.get(area.fontName);
                        if (bitmap == null) { // create fresh new
//#ifdef __LOG__
                            if (log.isEnabled()) log.error("bitmap font image not colorified yet: " + area.fontName + "; colorify using " + Integer.toHexString(colors[dayNight * 4 + 1]) + " color");
//#endif

                            // get raw data
                            final byte[] data = (byte[]) fonts.get(area.fontName);

                            // create image
                            if (data != null) {

                                // colorify
                                colorifyPng(data, colors[dayNight * 4 + 1]);

                                // create image
                                bitmap = Image.createImage(data, 0, data.length);

                                // cache image
                                cache.put(area.fontName, bitmap);

                            }
                        }

                        // use it in the area
                        area.fontImpl = bitmap;
                        area.cw = bitmap.getWidth() / CHARSET.length;
                        area.ch = (short) bitmap.getHeight();

                    } catch (Throwable t) {

                        // fallback to ordinary font
                        area.fontImpl = Desktop.font;
//#ifdef __LOG__
                        if (log.isEnabled()) log.error("failure", t);
                        t.printStackTrace();
//#endif
                    }
                }
            }

            // gc hint
            cache.clear();
        }

        // GC
        if (Config.forcedGc) {
            System.gc(); // conditional
        }
    }

    private String _profileName;

    public void commandAction(Command command, Displayable displayable) {

        // load new profile if selected
        boolean load = false;
        if (Desktop.CANCEL_CMD_TYPE != command.getCommandType()) {

            // get selection
            synchronized (this) {
                final List list = (List) displayable;
                _profileName = list.getString(profileIdx = list.getSelectedIndex());
            }
            load = true;
        }

        // restore desktop
        Desktop.restore(displayable);

        // enqueue load task
        if (load) {
            Desktop.getDiskWorker().enqueue(this);
        }
    }

    /* synchronized to avoid race-cond with rendering */
    synchronized int changeDayNight(final int dayNight) {
        if (isUsable()) {
/*
            // get rid of bitmap fonts in all areas
            for (final Enumeration seq = profiles.elements(); seq.hasMoreElements(); ) {
                final Object o = seq.nextElement();
                if (o instanceof Object[]) {
                    final Vector v = (Vector) ((Object[]) o)[0];
                    for (int N = v.size(), j = 0; j < N; j++) {
                        final Area a = (Area) v.elementAt(j);
                        if (a.fontImpl instanceof Image) {
                            a.fontImpl = null;
                        }
                    }
                }
            }
*/

            // update current profile
            prepare(dayNight);
        }

        return Desktop.MASK_SCREEN;
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
        if (isUsable() && status == null) {
            final Object[] areas = this.areas.getData();
            final CharArrayTokenizer tokenizer = this.tokenizer;
            final StringBuffer sb = this.sb;
            final Desktop navigator = this.navigator;
            final QualifiedCoordinates valueCoords = this.valueCoords;
            final float[] valuesFloat = this.valuesFloat;
            final int[] colors = this.colors;
            final char[] text = this.text;
            final int units = this.units.intValue();
            final int mode = Config.dayNight;

            int state = 0;

            graphics.setColor(colors[mode * 4]);
            graphics.fillRect(0,0, w, h);
            graphics.setColor(colors[mode * 4 + 1]);

            if (backgroundImage != null) {
                graphics.drawImage(backgroundImage, 0, 0, Graphics.TOP | Graphics.LEFT);
            }

            for (int N = this.areas.size(), i = 0; i < N; i++) {
                final Area area = (Area) areas[i];
//#ifdef __LOG__
//                if (log.isEnabled()) log.debug("area - font: " + area.fontName + "; value: " + (area.value != null ? new String(area.value) : "") + "; script: " + area.script);
                if (log.isEnabled()) log.debug("area - font: " + area.fontName + "; value: " + area.value);
//#endif

                // local vars
                int narrowChars = 0;
                Image img = null;

                // prepare text buffer
                sb.delete(0, sb.length());

                // template or script?
                if (area.value != null) {

                    // init tokenizer
                    tokenizer.init(area.value, area.value.length, DELIMITERS, true);

                    while (tokenizer.hasMoreTokens()) {
                        final CharArrayTokenizer.Token token = tokenizer.next();
                        if (token.isDelimiter) {
                            state++;
                        } else {
                            graphics.setColor(colors[mode * 4 + 1]);
                            if (state % 2 == 1) { // variable
                                int idx = area.index;
                                if (idx == -1) {
                                    idx = resolveTokenIndex(area, token);
                                }
                                switch (idx) {
                                    case VALUE_ALT: {
                                        float alt = asAltitude(units, valuesFloat[idx]);
                                        NavigationScreens.append(sb, (int) alt);
                                    } break;
                                    case VALUE_COURSE:  {
                                        NavigationScreens.append(sb, (int) valuesFloat[idx]);
                                    } break;
                                    case VALUE_SPD:
                                    case VALUE_SPD_MAX:
                                    case VALUE_SPD_AVG:
                                    case VALUE_SPD_AVG_AUTO: {
                                        float value = fromKmh(units, valuesFloat[idx]);
                                        NavigationScreens.append(sb, value, 1);
                                        narrowChars++;
                                    } break;
                                    case VALUE_SPDi:
                                    case VALUE_SPDi_MAX:
                                    case VALUE_SPDi_AVG:
                                    case VALUE_SPDi_AVG_AUTO: {
                                        float value = fromKmh(units, valuesFloat[idx - 4]);
                                        NavigationScreens.append(sb, (int) value);
                                    } break;
                                    case VALUE_SPDd:
                                    case VALUE_SPDd_MAX:
                                    case VALUE_SPDd_AVG:
                                    case VALUE_SPDd_AVG_AUTO: {
                                        float value = fromKmh(units, valuesFloat[idx - 8]);
                                        value -= (int) value;
                                        value *= 10;
                                        NavigationScreens.append(sb, (int) value);
                                    } break;
                                    case VALUE_DIST_T: {
                                        float value = fromKmh(units, valuesFloat[idx]);
                                        NavigationScreens.append(sb, value, 0);
                                        narrowChars++;
                                    } break;
                                    case VALUE_ALT_D:
                                    case VALUE_COURSE_D:
                                    case VALUE_SPD_D:
                                    case VALUE_SPD_DMAX:
                                    case VALUE_SPD_DAVG: {
                                        float value = valuesFloat[idx];
                                        if (idx == VALUE_ALT_D) {
                                            value = asAltitude(units, value);
                                        } else if (idx > VALUE_COURSE_D) {
                                            value = fromKmh(units, value);
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
                                        sb.append((char) ('0' + fix));
                                    } break;
                                    case VALUE_PDOP:
                                    case VALUE_HDOP:
                                    case VALUE_VDOP: {
                                        NavigationScreens.append(sb, valuesFloat[idx], 1);
                                    } break;
                                    case VALUE_SATV: {
                                        NavigationScreens.append(sb, NmeaParser.satv);
                                    } break;
                                    case VALUE_DGPS: {
                                        sb.append((char) ('0' + dgps));
                                    } break;
                                    case VALUE_COORDS: {
                                        if (valueCoords == null) {
                                            sb.append(MSG_NO_POSITION);
                                        } else {
                                            NavigationScreens.printTo(valueCoords, sb);
                                        }
                                    } break;
                                    case VALUE_TIME: {
                                        if (timestamp == 0) {
                                            sb.append(NO_TIME);
                                        } else {
                                            TIME_CALENDAR.setTimeSafe(timestamp);
                                            printTime(sb, TIME_CALENDAR, units);
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
                                        if (azi < 0) {
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
                                            dist = asDistance(units, dist);
                                            NavigationScreens.append(sb, dist, 0);
                                            narrowChars++;
                                        }
                                    } break;
                                    case VALUE_WPT_ETA: {
                                        final int azi = navigator.getWptAzimuth();
                                        final float dist = navigator.getWptDistance();
                                        if (azi < 0 || dist < 0F || timestamp == 0) {
                                            sb.append(NO_TIME);
                                        } else {
                                            if (dist > Config.wptProximity) {
                                                final double vmg = spdavgShort/*valuesFloat[VALUE_SPD]*/ * (Math.cos(Math.toRadians(valuesFloat[VALUE_COURSE] - azi)));
                                                if (vmg > 0F) {
                                                    final long dt = (long) (1000 * (dist / (vmg / 3.6F)));
                                                    final long eta;
                                                    if (dt >= 0F) {
                                                        eta = timestamp + dt;
                                                    } else {
                                                        eta = timestamp + 2 * -dt;
                                                    }
                                                    ETA_CALENDAR.setTime(eta);
                                                    printTime(sb, ETA_CALENDAR, units);
                                                } else {
                                                    sb.append(INF_TIME);
                                                }
                                            } else {
                                                ETA_CALENDAR.setTimeSafe(timestamp);
                                                printTime(sb, ETA_CALENDAR, units);
                                            }
                                        }
                                        narrowChars += 2;
                                    } break;
                                    case VALUE_WPT_VMG: {
                                        final int azi = navigator.getWptAzimuth();
                                        if (azi < 0) {
                                            sb.append('?');
                                        } else {
                                            double vmg = fromKmh(units, spdavgShort/*valuesFloat[VALUE_SPD]*/ * (float)(Math.cos(Math.toRadians(valuesFloat[VALUE_COURSE] - azi))));
                                            NavigationScreens.append(sb, vmg, 1);
                                            narrowChars++;
                                        }
                                    } break;
                                    case VALUE_WPT_ALT: {
                                        float alt = Float.NaN;
                                        final Waypoint wpt = navigator.getWpt();
                                        if (wpt != null) {
                                            alt = wpt.getQualifiedCoordinates().getAlt();
                                        }
                                        if (Float.isNaN(alt)) {
                                            sb.append('?');
                                        } else {
                                            alt = asAltitude(units, alt);
                                            NavigationScreens.append(sb, (int) alt);
                                        }
                                    } break;
                                    case VALUE_WPT_COORDS: {
                                        QualifiedCoordinates qc = null;
                                        final Waypoint wpt = navigator.getWpt();
                                        if (wpt != null) {
                                            qc = wpt.getQualifiedCoordinates();
                                        }
                                        if (qc == null) {
                                            sb.append(MSG_NO_POSITION);
                                        } else {
                                            NavigationScreens.printTo(qc, sb);
                                        }
                                    } break;
                                    case VALUE_TIMER: {
                                        sb.append((timestamp / 1000) % 10);
                                    } break;
                                    case VALUE_LAT:
                                    case VALUE_LON: {
                                        if (valueCoords == null) {
                                            sb.append(MSG_NO_POSITION);
                                        } else {
                                            final int m = idx == VALUE_LAT ? 1 : 2;
                                            NavigationScreens.printTo(sb, valueCoords, m, Config.decimalPrecision);
                                        }
                                    } break;
                                    case VALUE_WPT_LAT:
                                    case VALUE_WPT_LON: {
                                        QualifiedCoordinates qc = null;
                                        final Waypoint wpt = navigator.getWpt();
                                        if (wpt != null) {
                                            qc = wpt.getQualifiedCoordinates();
                                        }
                                        if (qc == null) {
                                            sb.append(MSG_NO_POSITION);
                                        } else {
                                            final int m = idx == VALUE_WPT_LAT ? 1 : 2;
                                            NavigationScreens.printTo(sb, qc, m, Config.decimalPrecision);
                                        }
                                    } break;
                                    case VALUE_WPT_NAME: {
                                        final Waypoint wpt = navigator.getWpt();
                                        if (wpt != null) {
                                            final String s = wpt.getName();
                                            if (s != null){
                                                sb.append(s);
                                            }
                                        }
                                    } break;
                                    case VALUE_WPT_CMT: {
                                        final Waypoint wpt = navigator.getWpt();
                                        if (wpt != null) {
                                            final String s = wpt.getComment();
                                            if (s != null){
                                                sb.append(s);
                                            }
                                        }
                                    } break;
                                    case VALUE_WPT_SYM: {
                                        final Waypoint wpt = navigator.getWpt();
                                        if (wpt != null) {
                                            final String s = wpt.getSym();
                                            if (s != null){
                                                sb.append(s);
                                            }
                                        }
                                    } break;
                                    case VALUE_WPT_IMG: {
                                        final Waypoint wpt = navigator.getWpt();
                                        if (wpt != null) {
                                            final String s = wpt.getLink(Waypoint.LINK_GENERIC_IMAGE);
                                            if (s != null) {
                                                if (s.hashCode() != wptImgId) {
                                                    wptImgId = s.hashCode();
                                                    wptImg = null;
                                                    try {
                                                        wptImg = NavigationScreens.loadImage(Config.FOLDER_WPTS, s);
                                                    } catch (Exception e) {
                                                        sb.append('!');
                                                    }
                                                } else if (wptImg == null) {
                                                    sb.append(s);
                                                }
                                            } else {
                                                wptImgId = 0;
                                                wptImg = null; // gc hint
                                            }
                                        } else {
                                            wptImgId = 0;
                                            wptImg = null; // gc hint
                                        }
                                        img = wptImg;
                                    } break;
                                    case VALUE_WPT_ALT_DIFF: {
                                        float wptAlt = Float.NaN;
                                        final Waypoint wpt = navigator.getWpt();
                                        if (wpt != null) {
                                            wptAlt = wpt.getQualifiedCoordinates().getAlt();
                                        }
                                        if (Float.isNaN(wptAlt)) {
                                            sb.append('?');
                                        } else {
                                            float diff = asAltitude(units, wptAlt - valuesFloat[VALUE_ALT]);
                                            NavigationScreens.append(sb, (int) diff);
                                        }
                                    } break;
                                    case VALUE_PACE: {
                                        float value = fromKmh(units, spdavgShort/*valuesFloat[VALUE_SPD_AVG]*/);
                                        value = 60 / value;
                                        if (value < 100F) {
                                            final int mins = (int) value;
                                            final int secs = (int) (60 * (value - mins));
                                            NavigationScreens.append(sb, mins, 2);
                                            sb.append(':');
                                            NavigationScreens.append(sb, secs, 2);
                                        } else {
                                            sb.append("99:99");
                                        }
                                    } break;
                                    case VALUE_COURSE_SLIDING: {
                                        drawSlider(graphics, (int) valuesFloat[VALUE_COURSE], area);
                                        continue; // TODO UGLY
                                    } // break;
                                    case VALUE_WPT_AZI_SLIDING: {
                                        final int azi = navigator.getWptAzimuth();
                                        if (azi < 0) {
                                            // what to do?
                                        } else {
                                            drawSlider(graphics, azi, area);
                                        }
                                        continue; // TODO UGLY
                                    } // break;
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
                                        NavigationScreens.append(sb, NmeaParser.snrs[idx - VALUE_SNR0]);
                                    } break;
                                    case VALUE_PRN0:
                                    case VALUE_PRN0 + 1:
                                    case VALUE_PRN0 + 2:
                                    case VALUE_PRN0 + 3:
                                    case VALUE_PRN0 + 4:
                                    case VALUE_PRN0 + 5:
                                    case VALUE_PRN0 + 6:
                                    case VALUE_PRN0 + 7:
                                    case VALUE_PRN0 + 8:
                                    case VALUE_PRN0 + 9:
                                    case VALUE_PRN0 + 10:
                                    case VALUE_PRN0 + 11: {
                                        NavigationScreens.append(sb, NmeaParser.prns[idx - VALUE_PRN0]);
                                    } break;
//#ifdef __NMEA_XDR__
                                    case VALUE_XDR: {
                                        final String xdrId = token.substring(4/* TOKEN_XDR.length() */);
                                        final Float value = (Float) NmeaParser.xdr.get(xdrId);
                                        if (value != null && !value.isNaN()) {
                                            NavigationScreens.append(sb, value.floatValue(), 1);
                                        } else {
                                            sb.append('?');
                                        }
                                    } break;
//#endif
                                    case VALUE_SIGN: {
                                        sb.append(NavigationScreens.SIGN);
                                    } break;
//#ifdef __HECL__
                                    case VALUE_HECL: {
                                        try {
                                            if (area.scriptlet != null) {
                                                area.scriptlet.run(interp);
                                            } else if (interp.existsVar(area.scriptvar, 0)) {
                                                final Thing thing = interp.resolveVar(area.scriptvar);
                                                if (thing.getVal() instanceof NumberThing) {
                                                    final NumberThing number = (NumberThing) thing.getVal();
                                                    if (number.isIntegral()) {
                                                        NavigationScreens.append(sb, number.longValue());
                                                    } else {
                                                        NavigationScreens.append(sb, number.doubleValue(), 0);
                                                    }
                                                } else {
                                                    sb.append(thing);
                                                }
                                            } else {
                                                sb.append('?');
                                            }
                                        } catch (Throwable t) {
//#ifdef __LOG__
                                            t.printStackTrace();
                                            if (log.isEnabled()) log.debug("resolve var failed: " + area.scriptvar);
//#endif
                                            sb.append('!').append(t.toString());
                                        }
                                    } break;
//#endif
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

//#ifdef __HECL__

                } else if (area.scriptlet != null) {

                    // eval script
                    try {
                        area.scriptlet.run(interp);
                    } catch (Throwable t) {
//#ifdef __LOG__
                        t.printStackTrace();
                        if (log.isEnabled()) log.debug("interp script eval failed: " + area.scriptlet);
//#endif
                        sb.append('!').append(t.toString());
                    }

//#endif /* __HECL__ */

                }

                int l = sb.length();
                if (l > 0) {
                    if (l > MAX_TEXT_LENGTH) l = MAX_TEXT_LENGTH;
                    sb.getChars(0, l, text, 0);
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
                } else if (img != null) {
                    graphics.setClip(area.x, area.y, area.w, area.h);
                    graphics.drawImage(img, area.x, area.y, Graphics.LEFT | Graphics.TOP);
                    graphics.setClip(0, 0, w, h);
                }
            }
        } else {
            graphics.setColor(0x00000000);
            graphics.fillRect(0, 0, w, h);
            graphics.setColor(0x00FFFFFF);
            final short msgCode;
            if (profiles == null) {
                msgCode = Resources.NAV_MSG_TICKER_LOADING;
            } else if (profiles.size() == 0) {
                msgCode = Resources.DESKTOP_MSG_NO_CMS_PROFILES;
            } else {
                msgCode = Resources.DESKTOP_MSG_LOAD_PROFILE_FAILED;
            }
            graphics.drawString(Resources.getString(msgCode), 0, 0, Graphics.TOP | Graphics.LEFT);
            if (status != null) {
                graphics.drawString(status, 0, Desktop.font.getHeight(), Graphics.TOP | Graphics.LEFT);
            }
        }
    }

    private /*static*/ int resolveTokenIndex(final Area area,
                                         final CharArrayTokenizer.Token token) {
        int idx = -1;

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
            } else if (token.startsWith(TOKEN_PRN)) {
                area.index = (short) (VALUE_PRN0 + Integer.parseInt(token.toString().substring(3)));
            } else if (token.equals(TOKEN_TIMER)) {
                area.index = VALUE_TIMER;
            } else if (token.equals(TOKEN_LAT)) {
                area.index = VALUE_LAT;
            } else if (token.equals(TOKEN_LON)) {
                area.index = VALUE_LON;
            } else if (token.equals(TOKEN_WPT_LAT)) {
                area.index = VALUE_WPT_LAT;
            } else if (token.equals(TOKEN_WPT_LON)) {
                area.index = VALUE_WPT_LON;
            } else if (token.equals(TOKEN_WPT_NAME)) {
                area.index = VALUE_WPT_NAME;
            } else if (token.equals(TOKEN_WPT_CMT)) {
                area.index = VALUE_WPT_CMT;
            } else if (token.equals(TOKEN_WPT_SYM)) {
                area.index = VALUE_WPT_SYM;
            } else if (token.equals(TOKEN_WPT_IMG)) {
                area.index = VALUE_WPT_IMG;
            } else if (token.equals(TOKEN_WPT_ALT_DIFF)) {
                area.index = VALUE_WPT_ALT_DIFF;
            } else if (token.equals(TOKEN_PACE)) {
                area.index = VALUE_PACE;
            } else if (token.equals(TOKEN_COURSE_SLIDING)) {
                area.index = VALUE_COURSE_SLIDING;
            } else if (token.equals(TOKEN_WPT_AZI_SLIDING)) {
                area.index = VALUE_WPT_AZI_SLIDING;
            } else if (token.startsWith(TOKEN_XDR)) {
                area.index = VALUE_XDR;
//#ifdef __HECL__
            } else if (token.startsWith('$')) {
                try {
                    final String ts = token.toString().trim();
                    if (ts.indexOf(' ') > -1) { // precision explicitly set
                        area.scriptlet = CodeThing.get(interp, new Thing("print " + ts));
                    } else {
                        area.scriptvar = token.toString().substring(1);
                    }
                    area.index = VALUE_HECL;
                } catch (HeclException e) {
                    area.value = e.toString().toCharArray();
                }
//#endif
            } else {
                area.index = -1;
            }
            idx = area.index;
        }

        return idx;
    }

    private static StringBuffer printTime(final StringBuffer sb,
                                          final SimpleCalendar calendar, final int units) {
        final int H = units == Config.UNITS_IMPERIAL ? Calendar.HOUR : Calendar.HOUR_OF_DAY;
        return printTime(sb, calendar.get(H), calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND));
    }

    private static StringBuffer printTime(final StringBuffer sb,
                                          final int hour, final int min, final int sec) {
        if (hour < 10) {
            sb.append('0');
        }
        NavigationScreens.append(sb, hour).append(':');
        if (min < 10) {
            sb.append('0');
        }
        NavigationScreens.append(sb, min).append(':');
        if (sec < 10) {
            sb.append('0');
        }
        NavigationScreens.append(sb, sec);

        return sb;
    }

    private static void drawChars(final Graphics graphics, final char[] value,
                                  final int length, int x, int y,
                                  final Area area) {
        final Image image = (Image) area.fontImpl;
        final float cw = area.cw;
        final int scw = (int) (cw - cw / 5);
        final int icw = (int) cw;
        final int ch = area.ch;
        final char[] charset = CHARSET;
        final int N = charset.length;
//#ifdef __ALT_RENDERER__
        final boolean S60renderer = Config.S60renderer;
//#endif
        int xoff = 0;
        int invalids = 0;

        for (int i = 0; i < length; i++) {
            final char c = value[i];
            int j = 0;
            for ( ; j < N; j++) {
                if (c == charset[j]) {
                    break;
                }
            }
            if (j < N && invalids <= 1) {
                final int z = c == '.' || c == ':' ? (int) (cw / 3) : 0;
//#ifdef __ALT_RENDERER__
                if (S60renderer) {
//#endif
                    graphics.setClip(x + (int) (i * cw), y, icw - 2 * z, ch);
                    graphics.drawImage(image,
                                       x + (int) ((i - j) * cw) - z, y,
                                       Graphics.LEFT | Graphics.TOP);
                    graphics.setClip(area.x, area.y, area.w, area.h);
//#ifdef __ALT_RENDERER__
                } else {
                    graphics.drawRegion(image, (int) (j * cw) + z, 0, icw - 2 * z, ch,
                                        Sprite.TRANS_NONE, x + (int) (i * cw), y,
                                        Graphics.LEFT | Graphics.TOP);
                }
//#endif
                if (c == ' ') {
                    x -= scw;
                } else {
                    x -= 2 * z;
                }
                xoff = 0;
                invalids = 0;
            } else {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("unknown char: '" + c + "'");
//#endif
                if (xoff == 0) {
                    xoff = x + (int) (i * cw);
                }
/*
                final int color = graphics.getColor();
                graphics.setColor(0x00FF0000);
*/
                graphics.drawChar(c, xoff, y, 0);
/*
                graphics.setColor(color);
*/
                xoff += graphics.getFont().charWidth(c) + 1;
                invalids++;
            }
        }
    }

    private static void drawSlider(final Graphics graphics, final int value,
                                   final Area area) {
        final Image image = (Image) area.fontImpl;
        if (image != null) {
            final int iw = image.getWidth();
            final int x0 = value - (area.w >> 1);
            final int x1 = value + (area.w >> 1);
            graphics.setClip(area.x, area.y, area.w, area.h);
            if (x0 < 0) {
                graphics.drawImage(image, area.x - (iw + x0), area.y, Graphics.LEFT | Graphics.TOP);
            }
            graphics.drawImage(image, area.x + (area.w >> 1) - value, area.y, Graphics.LEFT | Graphics.TOP);
            if (x1 > iw) {
                graphics.drawImage(image, area.x + area.w + (iw - x1), area.y, Graphics.LEFT | Graphics.TOP);
            }
            graphics.setClip(0, 0, Desktop.width, Desktop.height);
        }
    }

    private boolean isUsable() {
        return profiles != null && profiles.size() != 0 && initialProfile != null;
    }

    private boolean havePlugins() {
//#ifdef __HECL__
        return cz.kruch.track.hecl.PluginManager.getInstance().size() > 0;
//#else
        return false;
//#endif
    }

    private void reset() {
        // reset calendars
        TIME_CALENDAR.reset();
        ETA_CALENDAR.reset();

        // reset vars
        valueCoords = snrefCoords = null;
        timestamp = starttime = timetauto = 0;
        altLast = altDiff = Float.NaN;
        sat = fix = dgps = 0;
        final float[] valuesFloat = this.valuesFloat;
        for (int i = valuesFloat.length; --i >= 0; ) {
            valuesFloat[i] = 0F;
        }
        final float[] spdavgFloat = this.spdavgFloat;
        final long[] spdavgLong = this.spdavgLong;
        for (int i = SHORT_AVG_DEPTH; --i >= 0; ) {
            spdavgFloat[i] = 0F;
            spdavgLong[i] = 0L;
        }
        spdavgIndex = 0;
        spdavgShort = 0F;

//#ifdef __HECL__
        // invalidate vars
        if (interp != null) {
            interp.cacheversion = 0;
        }
//#endif
    }

    private void calcSpdAvgShort(final float speed, final float ds, final long t) {
        final float[] spdavgFloat = this.spdavgFloat;
        spdavgFloat[spdavgIndex++ % SHORT_AVG_DEPTH] = speed;
        int N = 0;
        if (spdavgIndex > SHORT_AVG_DEPTH) {
            N = SHORT_AVG_DEPTH;
        } else if (spdavgIndex > SHORT_AVG_DEPTH_MIN) {
            N = spdavgIndex;
        }
        if (N > 0) {
            float smin = Float.MAX_VALUE, smax = 0F, sas = 0F;
            for (int i = N; i-- > 0; ) {
                final float v = spdavgFloat[i];
                sas += v;
                if (v < smin) smin = v;
                if (v > smax) smax = v;
            }
            sas -= smin;
            sas -= smax;
            spdavgShort = sas / (N - 2);
        }
/*
        final float[] spdavgFloat = this.spdavgFloat;
        final long[] spdavgLong = this.spdavgLong;
        final int idx = spdavgIndex % SHORT_AVG_DEPTH;
        final long t0 = spdavgLong[idx];
        spdavgLong[idx] = t;
        spdavgFloat[idx] = ds;
        spdavgIndex++;
        if (spdavgIndex > SHORT_AVG_DEPTH) {
            float sas = 0F;
            for (int i = SHORT_AVG_DEPTH; i-- > 0; ) {
                sas += spdavgFloat[i];
            }
            final float dtt = t - t0;
            spdavgShort = sas / (dtt / 1000) * 3.6F;
//            System.out.println("sas: " + sas + " / " + (dtt / 1000) + " idx: " + idx +  " t=" + t + " t0=" + t0);
        } else if (spdavgIndex > 1) {
            float sas = 0F;
            for (int i = spdavgIndex; i-- > 0; ) {
                sas += spdavgFloat[i];
            }
            final float dtt = t - spdavgLong[0];
            spdavgShort = sas / (dtt / 1000) * 3.6F;
//            System.out.println("sas: " + sas + " / " + (dtt / 1000) + " idx: " + idx + " t=" + t + " [0]=" + spdavgLong[0]);
        }
        System.out.println("spd-avg-short: " + spdavgShort);
*/
    }

    private String loadViaCache(final String filename) {

        // release current profile bitmap fonts
        final Vector as = this.areas;
        if (as != null) {
            for (int i = as.size(); --i >= 0; ) {
                final Area area = (Area) as.elementAt(i);
                if (area.fontImpl instanceof Image) {
                    area.fontImpl = null;
                }
            }
        }

        // gc hint
        areas = null;
        colors = null;
        units = null;

        // try cache
        final Object o = profiles.get(filename);
        if (o instanceof Object[]) {

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("found cached profile: " + filename);
//#endif
            // populate members
            final Object[] cached = (Object[]) o;
            areas = (NakedVector) cached[0];
            colors = (int[]) cached[1];
            units = (Integer) cached[2];

        } else { // not found

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("load profile: " + filename);
//#endif
            // new vars
            colors = new int[8];
            areas = new NakedVector(4, 4);

            // load from file
            load(filename);

            // fix missing profile vars
            if (units == null) {
                units = new Integer(Config.units);
            }

            // cache
            profiles.put(filename, new Object[]{ areas, colors, units });
        }

        // fallback hack
        iunits.setValue(units.intValue());

        // remember last selection
        Config.cmsProfile = filename;
/* back up during shutdown
        try {
            Config.update(Config.VARS_090);
        } catch (ConfigurationException e) {
            // ignore
        }
*/

        return filename;
    }

    private Object load(final String filename) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("load " + filename);
//#endif

        Object result = null;
        File file = null;
        try {
            file = File.open(Config.getFileURL(Config.FOLDER_PROFILES, filename));
            if (file.exists()) {
                InputStream in = null;
                try {
                    in = new BufferedInputStream(file.openInputStream(), 4096);
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
//#ifdef __LOG__
            } else {
                if (log.isEnabled()) log.warn("file " + filename + " does not exist");
//#endif
            }
        } catch (Throwable t) {
            status = t.toString();
//#ifdef __LOG__
            t.printStackTrace();
//#endif
        } finally {
            try {
                file.close();
            } catch (Exception e) { // IOE or NPE
                // ignore
            }
        }

        return result;
    }

    private void findProfiles(final Hashtable profiles) throws IOException {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("find profiles");
//#endif
        
        // var
        File dir = null;

        try {
            // open stores directory
            dir = File.open(Config.getFolderURL(Config.FOLDER_PROFILES));

            // list profiles
            if (dir.exists()) {
                final Vector v = new Vector(8);
                for (final Enumeration e = dir.list(); e.hasMoreElements(); ) {
                    final String filename = (String) e.nextElement();
                    final String candidate = filename.toLowerCase();
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("found file " + filename);
//#endif
                    if (candidate.startsWith("cms.") && (File.isOfType(candidate, ".xml"))) {
                        v.addElement(File.idenFix(filename));
                    }
                }
                profilesNames = FileBrowser.sort2array(v.elements(), null, null);
                for (int N = profilesNames.length, i = 0; i < N; i++) {
                    profiles.put(profilesNames[i], this/* hack: null not allowed */);
                }
            } else {
//#ifdef __LOG__
                if (log.isEnabled()) log.warn("folder ui-profiles does not exist");
//#endif
                status = "Folder ui-profiles does not exist.";
            }
        } finally {
            // close dir
            try {
                dir.close();
            } catch (Exception e) { // IOE or NPE
                // ignore
            }
        }
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("~find profiles; " + profiles.size());
//#endif
    }

     private String loadProfile(final String filename, final InputStream in) throws IOException, XmlPullParserException {
         // XML parser
         final HXmlParser parser = new HXmlParser();

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
                             final String font = parser.getAttributeValue(null, TAG_FONT);
                             if (font != null) {
                                 area.fontName = font;
                                 final Object fo = fonts.get(font);
                                 if (fo instanceof Font) {
                                     area.fontImpl = fo;
                                 }
                             } else {
                                 area.fontName = "Desktop";
                                 area.fontImpl = Desktop.font;
                             }
                             final String name = parser.getAttributeValue(null, ATTR_IMAGE);
                             if (name != null) {
                                 final byte[] image = (byte[]) load(name);
                                 if (image != null) {
                                     area.fontName = name;
                                     area.fontImpl = null;
                                     fonts.put(name, image);
                                 }
                             }
                         } else if (TAG_VALUE.equals(tag)) {
                             area.value = parser.nextText().toCharArray();
//#ifdef __HECL__
                         } else if (TAG_SCRIPT.equals(tag)) {
                             try {
                                 area.scriptlet = CodeThing.get(interp, new Thing(parser.nextText()));
                             } catch (HeclException e) {
                                 area.value = e.toString().toCharArray();
                             }
//#endif
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
                                         Font font;
                                         try {
                                             font = Font.getFont((code >> 16) & 0x000000ff,
                                                                 (code >> 8) & 0x000000ff,
                                                                 (code) & 0x0000ff);
                                         } catch (IllegalArgumentException e) {
                                             font = Font.getDefaultFont();
                                         }
                                         fonts.put(name, font);
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
                             final String background = parser.getAttributeValue(null, ATTR_BACKGROUND);
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
         final int tRnsStart = findChunk(data, 8, length, "tRNS".toCharArray());
         if (tRnsStart > 0) {
             final int trnsLength = getChunkLength(data, tRnsStart);
             final int plteStart = findChunk(data, 8, length, "PLTE".toCharArray());
             if (plteStart > 0) {
                 final int plteLength = getChunkLength(data, plteStart);
                 final int plteEntries = plteLength / 3;
                 int plteOffset = plteStart;
                 for (int i = 0; i < plteEntries; i++) {
                     if (i >= trnsLength || data[tRnsStart + i] != 0) {
                         data[plteOffset++] = (byte) ((color >>> 16) & 0xff);
                         data[plteOffset++] = (byte) ((color >>> 8) & 0xff);
                         data[plteOffset++] = (byte) (color & 0xff);
                     } else {
                         plteOffset += 3;
                     }
                 }
                 final long plteCrc = calcCrc(data, plteStart - 4, 4 + plteLength);
                 plteOffset = plteStart + plteLength;
                 data[plteOffset++] = (byte) ((plteCrc >>> 24) & 0xff);
                 data[plteOffset++] = (byte) ((plteCrc >>> 16) & 0xff);
                 data[plteOffset++] = (byte) ((plteCrc >>> 8) & 0xff);
                 data[plteOffset] = (byte) (plteCrc & 0xff);
             }
         }

/*
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
                         } else if (plte == 4) {
                             if (plteStart == 0) {
                                 plteStart = chunkStart + 4;
                                 plteDataOffset = 0;
                             } else if (offset < chunkStart + 4 + 4 + chunkDataLength) {
                                 data[offset] = (byte) i;
                                 if (plteDataOffset % 3 == 0) {
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
                         }
                     }
                 }
             }
         }
*/
     }

     private static long calcCrc(final byte[] buf, int off, int len) {
         final int[] crc_table = CRC_TABLE;
         int c = ~0;
         while (--len >= 0) {
             c = crc_table[(c ^ buf[off++]) & 0xff] ^ (c >>> 8);
         }
         return (long) ~c & 0xffffffffL;
     }

/*
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
*/

     private static int findChunk(final byte[] data, final int offset,
                                  final int length, final char[] sequence) {
         int chunk = 0;
         for (int rel = offset; rel < length; rel++) {
             final int c = data[rel];

             switch (chunk) {
                 case 0:
                     if (c == sequence[0]) {
                         chunk++;
                     }
                 break;
                 case 1:
                     if (c == sequence[1]) {
                         chunk++;
                     } else {
                         chunk = 0;
                     }
                 break;
                 case 2:
                     if (c == sequence[2]) {
                         chunk++;
                     } else {
                         chunk = 0;
                     }
                 break;
                 case 3:
                     if (c == sequence[3]) {
                         chunk++;
                     } else {
                         chunk = 0;
                     }
                 break;
             }

             if (chunk == 4) {
                 return rel + 1;
             }
         }

         return -1;
     }

     private static int getChunkLength(final byte[] data, final int offset) {
         int result = (data[offset - 8] & 0xff) << 24;
         result |= (data[offset - 7] & 0xff) << 16;
         result |= (data[offset - 6] & 0xff) << 8;
         result |= (data[offset - 5] & 0xff);
         return result;
     }

     private class Rotator extends TimerTask {

         /* prevents $1 generation */
         public Rotator() {
         }

         public void run() {
             if (isVisible && rotate) {
                 handleAction(Canvas.RIGHT, false);
             }
         }
     }

    private static float fromKmh(final int units, float value) {
        switch (units) {
            case Config.UNITS_IMPERIAL:
                value /= 1.609F;
            break;
            case Config.UNITS_NAUTICAL:
                value /= 1.852F;
            break;
        }

        return value;
    }

    private static float asDistance(final int units, float value) {
        switch (units) {
            case Config.UNITS_METRIC:
                value /= 1000F;
            break;
            case Config.UNITS_IMPERIAL:
                value /= 1609F;
            break;
            case Config.UNITS_NAUTICAL:
                value /= 1852F;
            break;
        }

        return value;
    }

    private static float asAltitude(final int units, float value) {
        switch (units) {
            case Config.UNITS_IMPERIAL:
            case Config.UNITS_NAUTICAL:
                value /= 0.3048F;
            break;
        }

        return value;
    }

//#ifdef __HECL__

    /* synchronized to avoid race-cond with hecl processing in render */
    private /*static*/ synchronized void invokeHandlers(final Interp interp,
                                                        final NakedVector handlers,
                                                        final Thing[] argv) {
        if (interp == null || handlers == null || argv == null) {
            return;
        }
        
        final Object[] items = handlers.getData();
        for (int i = handlers.size(); --i >= 0; ) {
            final String handlerName = items[i].toString();
            String result;
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("invoking handler " + items[i]);
//#endif
            try {
                ((org.hecl.Command) items[i]).cmdCode(interp, argv);
                result = "{SUCCESS}";
            } catch (Throwable t) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("handler failed: " + t);
//#endif
                result = t.toString();
            }
            heclResults.put(handlerName, result);
        }
    }

    // TODO move to TripStatitics

    private static final int HASH_ALT           = 0x179a9; // array     // 96681
    private static final int HASH_FIX           = 0x18c15; // specs     // 101397
    private static final int HASH_LAT	        = 0x1a19f; // specs     // 106911
    private static final int HASH_LON	        = 0x1a34b; // specs     // 107339
    private static final int HASH_SAT           = 0x1bbe6;      // 113638
    private static final int HASH_SPD           = 0x1bda7;      // 114087
    private static final int HASH_DGPS          = 0x2f05c6;     // 3081670
    private static final int HASH_HDOP          = 0x30cbdd;     // 3197917
    private static final int HASH_PACE	        = 0x346213;     // 3432979
    private static final int HASH_PDOP          = 0x346ed5;     // 3436245
    private static final int HASH_SATV          = 0x35c150;     // 3522896
    private static final int HASH_TIME	        = 0x3652cd;     // 3560141
    private static final int HASH_VDOP          = 0x37290f;     // 3614991
    private static final int HASH_ALT_D         = 0x589b940;    // 92911936
    private static final int HASH_ASC_T         = 0x58ca818;    // 93104152
    private static final int HASH_UTM_X	        = 0x6a71e27;    // 111615527
    private static final int HASH_UTM_Y	        = 0x6a71e28;    // 111615528
    private static final int HASH_WPT_DIST	    = 0x37011f78;   // 922820472
    private static final int HASH_WPT_ALT	    = 0x5c9ce597;   // 1553786263
    private static final int HASH_WPT_AZI	    = 0x5c9ce73e;   // 1553786686
    private static final int HASH_WPT_ETA	    = 0x5c9cf580;   // 1553790336
    private static final int HASH_WPT_LAT	    = 0x5c9d0d8d;   // 1553796493
    private static final int HASH_WPT_LON	    = 0x5c9d0f39;   // 1553796921
    private static final int HASH_WPT_SYM	    = 0x5c9d2ab5;   // 1553803957
    private static final int HASH_WPT_VMG	    = 0x5c9d347e;   // 1553806462
    private static final int HASH_SPD_AVG_AUTO  = 0x866fa930;   // -2039502544
    private static final int HASH_SPD_AVG       = 0x882281ac;   // -2011004500
    private static final int HASH_SPD_MAX       = 0x8822ac3e;   // -2010993602
    private static final int HASH_COURSE        = 0xaf42e01b;   // -1354571749
    private static final int HASH_DESC_T        = 0xb069a438;   // -1335253960
    private static final int HASH_DIST_T        = 0xb0a2420d;   // -1331543539
    private static final int HASH_TIME_T	    = 0xcbecd974;   // -873670284
    private static final int HASH_COURSE_D      = 0xea0b4b32;   // -368358606
    private static final int HASH_PROFILE       = 0xed8e89a9;   // -309425751
    private static final int HASH_WPT_ALT_DIFF	= 0xeeff2e7b;   // -285266309

//    private static final int HASH_SPD_D         = 0x688f5be;    // 109639102
//    private static final int HASH_SPD_DAVG      = 0x7c2ec454;   // 2083439700
//    private static final int HASH_SPD_DMAX      = 0x7c2eeee6;   // 2083450598
//    private static final int HASH_COORDS	    = 0xaf40241e; // -1354750946
//    private static final int HASH_STATUS	    = 0xcacdcff2; // -892481550
//    private static final int HASH_TIME_T_AUTO	= 0xdbccee68; // -607326616
//    private static final int HASH_PRN	        = 0x1b2ac; // 111276
//    private static final int HASH_SNR	        = 0x1bd77; // 114039
//    private static final int HASH_WPT_NAME	    = 0x37058c5d; // 923110493
//    private static final int HASH_WPT_CMT	    = 0x5c9ced38; // 1553788216
//    private static final int HASH_WPT_IMG	    = 0x5c9d03b1; // 1553793969
//    private static final int HASH_WPT_COORDS	= 0x79d50970; // 2044004720

    public Thing get(final String varname, final int units) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("interp get: " + varname);
//#endif
        Thing result = null;

        // try our standard vars
        if (varname.startsWith("cms::")) {
            final int hash = hash(varname, 5);
            boolean fromKmh = false, toFeets = false;
            int idx = -1;
            switch (hash) {
                case HASH_ALT: {
                    idx = VALUE_ALT;
                    toFeets = true;
                } break;
                case HASH_SPD: {
                    idx = VALUE_SPD;
                    fromKmh = true;
                } break;
                case HASH_HDOP: {
                    idx = VALUE_HDOP;
                } break;
                case HASH_VDOP: {
                    idx = VALUE_VDOP;
                } break;
                case HASH_ALT_D: {
                    idx = VALUE_ALT_D;
                } break;
                case HASH_ASC_T: {
                    idx = VALUE_ASC_T;
                } break;
                case HASH_SPD_AVG_AUTO: {
                    idx = VALUE_SPD_AVG_AUTO;
                    fromKmh = true;
                } break;
                case HASH_SPD_AVG: {
                    idx = VALUE_SPD_AVG;
                    fromKmh = true;
                } break;
                case HASH_SPD_MAX: {
                    idx = VALUE_SPD_MAX;
                    fromKmh = true;
                } break;
                case HASH_COURSE: {
//                    idx = VALUE_COURSE;
                    result = IntThing.create((int) valuesFloat[VALUE_COURSE]);
                } break;
                case HASH_DESC_T: {
                    idx = VALUE_DESC_T;
                } break;
                case HASH_DIST_T: {
                    idx = VALUE_DIST_T;
                    fromKmh = true;
                } break;
                case HASH_COURSE_D: {
                    idx = VALUE_COURSE_D;
                } break;
            }

            if (idx != -1) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("var " + varname + " resolved as float, idx = " + idx + ", value = " + valuesFloat[idx]);
//#endif
                // found float type var
                float v = valuesFloat[idx];
                if (fromKmh) {
                    v = fromKmh(units, v);
                } else if (toFeets) {
                    v = asAltitude(units, v);
                }
                result = FloatThing.create(v);

            } else if (result == null) {

                // try specials
                switch (hash) {
                    case HASH_FIX: {
                        result = IntThing.create(fix);
                    } break;
                    case HASH_LAT: {
                        result = DoubleThing.create(valueCoords != null ? valueCoords.getLat() : 0D);
                    } break;
                    case HASH_LON: {
                        result = DoubleThing.create(valueCoords != null ? valueCoords.getLon() : 0D);
                    } break;
                    case HASH_SAT: {
                        result = IntThing.create(sat);
                    } break;
                    case HASH_DGPS: {
                        result = IntThing.create(dgps);
                    } break;
                    case HASH_PACE: {
                        float value = 0F;
                        if (spdavgShort > 0F) {
                            value = 60F / fromKmh(units, spdavgShort/*valuesFloat[VALUE_SPD_AVG]*/);
                        }
                        result = IntThing.create((int)(60 * value));
                    } break;
                    case HASH_SATV: {
                        result = IntThing.create(NmeaParser.satv);
                    } break;
                    case HASH_TIME: {
                        result = LongThing.create(timestamp);
                    } break;
                    case HASH_UTM_X: {
                        final double val;
                        if (valueCoords != null) {
                            CartesianCoordinates cc = Mercator.LLtoUTM(valueCoords);
                            val = cc.getH();
                            CartesianCoordinates.releaseInstance(cc);
                        } else {
                            val = 0D;
                        }
                        result = DoubleThing.create(val);
                    } break;
                    case HASH_UTM_Y: {
                        final double val;
                        if (valueCoords != null) {
                            CartesianCoordinates cc = Mercator.LLtoUTM(valueCoords);
                            val = cc.getV();
                            CartesianCoordinates.releaseInstance(cc);
                        } else {
                            val = 0D;
                        }
                        result = DoubleThing.create(val);
                    } break;
                    case HASH_WPT_DIST: {
                        float dist = navigator.getWptDistance();
                        if (dist < 0F) {
                            dist = 0;
                        } else {
                            dist = asDistance(units, dist);
                        }
                        result = FloatThing.create(dist);
                    } break;
                    case HASH_WPT_ALT: {
                        float alt = 0F;
                        final Waypoint wpt = navigator.getWpt();
                        if (wpt != null) {
                            alt = wpt.getQualifiedCoordinates().getAlt();
                            if (!Float.isNaN(alt)) {
                                alt = asAltitude(units, alt);
                            }
                        }
                        result = FloatThing.create(alt);
                    } break;
                    case HASH_WPT_AZI: {
                        result = IntThing.create(navigator.getWptAzimuth());
                    } break;
                    case HASH_WPT_ETA: {
                        final int azi = navigator.getWptAzimuth();
                        final float dist = navigator.getWptDistance();
                        long eta;
                        if (azi < 0 || dist < 0F || timestamp == 0) {
                           eta = -1;
                        } else {
                            if (dist > Config.wptProximity) {
                                final double vmg = spdavgShort/*valuesFloat[VALUE_SPD]*/ * (Math.cos(Math.toRadians(valuesFloat[VALUE_COURSE] - azi)));
                                if (vmg > 0F) {
                                    final long dt = (long) (1000 * (dist / (vmg / 3.6F)));
                                    if (dt >= 0F) {
                                        eta = timestamp + dt;
                                    } else {
                                        eta = timestamp + 2 * -dt;
                                    }
                                } else {
                                    eta = -1;
                                }
                            } else {
                                eta = 0;
                            }
                        }
                        result = LongThing.create(eta);
                    } break;
                    case HASH_WPT_LAT: {
                        double lat = 0D;
                        final Waypoint wpt = navigator.getWpt();
                        if (wpt != null) {
                            lat = wpt.getQualifiedCoordinates().getLat();
                        }
                        result = DoubleThing.create(lat);
                    } break;
                    case HASH_WPT_LON: {
                        double lat = 0D;
                        final Waypoint wpt = navigator.getWpt();
                        if (wpt != null) {
                            lat = wpt.getQualifiedCoordinates().getLon();
                        }
                        result = DoubleThing.create(lat);
                    } break;
                    case HASH_WPT_SYM: {
                        final Waypoint wpt = navigator.getWpt();
                        if (wpt != null) {
                            final String s = wpt.getSym();
                            if (s != null){
                                result = StringThing.create(s);
                            }
                        }
                    } break;
                    case HASH_WPT_VMG: {
                        double vmg = 0D;
                        final int azi = navigator.getWptAzimuth();
                        if (azi >= 0) {
                            vmg = fromKmh(units, spdavgShort/*valuesFloat[VALUE_SPD]*/ * (float)(Math.cos(Math.toRadians(valuesFloat[VALUE_COURSE] - azi))));
                        }
                        result = DoubleThing.create(vmg);
                    } break;
                    case HASH_TIME_T: {
                        long dt;
                        if (timestamp > 0 && starttime > 0) {
                            dt = (timestamp - starttime) / 1000;
                        } else {
                            dt = 0;
                        }
                        result = LongThing.create(dt);
                    } break;
                    case HASH_PROFILE: {
                        result = StringThing.create(Config.cmsProfile);
                    } break;
                    case HASH_WPT_ALT_DIFF: { // TODO obsolete
						float diff = navigator.getWptAltDiff();
						if (!Float.isNaN(diff)) {
							diff = asAltitude(units, diff);
						}
						result = FloatThing.create(diff);
					} break;
                }
            }
        } else if (heclResults != null) { // can be also debug info
            final Object msg = heclResults.get(varname);
            if (msg != null) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("found handler " + varname + " status: " + msg);
//#endif
                result = StringThing.create((String) msg);
            }
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("result: " + result);
//#endif

        return result;
    }

    // TODO ~ move to TripStatitics
                                                 
    private static Thing updateCachedOrCreate(final Thing cached, final double value) {
        final Thing result;
        if (cached != null) {
            ((DoubleThing) cached.getVal()).set(value);
            result = cached;
        } else {
            result = DoubleThing.create(value);
        }
        return result;
    }

    private static Thing updateCachedOrCreate(final Thing cached, final float value) {
        final Thing result;
        if (cached != null) {
            ((FloatThing) cached.getVal()).set(value);
            result = cached;
        } else {
            result = FloatThing.create(value);
        }
        return result;
    }

    private static Thing updateCachedOrCreate(final Thing cached, final int value) {
        final Thing result;
        if (cached != null) {
            ((IntThing) cached.getVal()).set(value);
            result = cached;
        } else {
            result = IntThing.create(value);
        }
        return result;
    }

    private static Thing updateCachedOrCreate(final Thing cached, final long value) {
        final Thing result;
        if (cached != null) {
            ((LongThing) cached.getVal()).set(value);
            result = cached;
        } else {
            result = LongThing.create(value);
        }
        return result;
    }

    private static int hash(final String varname, int pos) {
        int h = 0;
        for (int i = varname.length() - pos; --i >= 0;) {
            h = 31 * h + varname.charAt(pos++);
        }
        return h;
    }

    private static final class PrintCommand implements org.hecl.Command {
//#ifdef __LOG__
        private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("PrintCommand");
//#endif

        private ComputerView parent;

        /* to avoid $1 */
        public PrintCommand(ComputerView parent) {
            this.parent = parent;
        }

        public Thing cmdCode(Interp interp, Thing[] argv) throws HeclException {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("interp print: " + argv[1] + (argv.length > 2 ? " " + argv[2].toString() : ""));
//#endif
            
            final StringBuffer sb = parent.sb;
            final RealThing thing = argv[1].getVal();
            if (thing instanceof NumberThing) {
                final NumberThing number = (NumberThing) thing;
                if (number.isIntegral()) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("interp print int " + argv[1]);
//#endif
                    NavigationScreens.append(sb, number.longValue());
                } else {
                    final int precision;
                    if (argv.length > 2) {
                        precision = ((IntThing) argv[2].getVal()).intValue();
                    } else {
                        precision = 0;
                    }
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("interp print double " + argv[1] + " with precision " + precision);
//#endif
                    NavigationScreens.append(sb, number.doubleValue(), precision);
                }
            } else {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("interp print object " + thing.thingclass() + " with toString value " + argv[1]);
//#endif
                sb.append(argv[1]);
            }

            return null;
        }
    }

//#endif /* __HECL__ */

}
