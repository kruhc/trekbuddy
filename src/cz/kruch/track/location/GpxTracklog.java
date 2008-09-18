// @LICENSE@

package cz.kruch.track.location;

import api.location.QualifiedCoordinates;
import api.location.Location;
import api.file.File;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.TimerTask;
import java.util.Enumeration;
import java.util.Hashtable;

import org.kxml2.io.KXmlSerializer;

import javax.microedition.io.Connector;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.event.Callback;
import cz.kruch.track.ui.NavigationScreens;
import cz.kruch.track.Resources;
import cz.kruch.track.util.NmeaParser;
import cz.kruch.j2se.io.BufferedOutputStream;

/**
 * GPX.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class GpxTracklog extends Thread {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Gpx");
//#endif

    private static final String DEFAULT_NAMESPACE   = null; // this is wrong but KXML handles it well :-)
    private static final String GPX_1_1_NAMESPACE   = "http://www.topografix.com/GPX/1/1";
    private static final String GS_1_0_NAMESPACE    = "http://www.groundspeak.com/cache/1/0";
    private static final String GS_1_0_PREFIX       = "groundspeak";
    private static final String EXT_NAMESPACE       = "urn:net:trekbuddy:1.0:nmea:rmc";
    private static final String EXT_PREFIX          = "rmc";
    private static final String XDR_NAMESPACE       = "urn:net:trekbuddy:1.0:nmea:xdr";
    private static final String XDR_PREFIX          = "xdr";
    private static final String GSM_NAMESPACE       = "urn:net:trekbuddy:1.0:gsm";
    private static final String GSM_PREFIX          = "gsm";

    private static final float  MIN_SPEED_WALK = 1F;        // 1 m/s ~ 3.6 km/h
    private static final float  MIN_SPEED_BIKE = 5F;        // 5 m/s ~ 18 km/h
    private static final float  MIN_SPEED_CAR  = 10F;       // 10 m/s ~ 36 km/h
    private static final float  MIN_COURSE_DIVERSION      = 15F; // 15 degrees
    private static final float  MIN_COURSE_DIVERSION_FAST = 10F; // 10 degrees

    public static final int LOG_WPT = 0;
    public static final int LOG_TRK = 1;

    public static final int CODE_RECORDING_STOP    = 0;
    public static final int CODE_RECORDING_START   = 1;
    public static final int CODE_WAYPOINT_INSERTED = 2;

    private static final String ELEMENT_GPX         = "gpx";
    private static final String ELEMENT_TRK         = "trk";
    private static final String ELEMENT_TRKSEG      = "trkseg";
    private static final String ELEMENT_TRKPT       = "trkpt";
    private static final String ELEMENT_TIME        = "time";
    private static final String ELEMENT_ELEVATION   = "ele";
    private static final String ELEMENT_FIX         = "fix";
    private static final String ELEMENT_SAT         = "sat";
    private static final String ELEMENT_WPT         = "wpt";
    private static final String ELEMENT_NAME        = "name";
    private static final String ELEMENT_CMT         = "cmt";
    private static final String ELEMENT_SYM         = "sym";
    private static final String ELEMENT_LINK        = "link";
    private static final String ELEMENT_EXTENSIONS  = "extensions";
    private static final String ELEMENT_COURSE      = "course";
    private static final String ELEMENT_SPEED       = "speed";
    private static final String ELEMENT_CELLID      = "cellid";
    private static final String ELEMENT_LAC         = "lac";
    private static final String ELEMENT_GS_CACHE        = "cache";
    private static final String ELEMENT_GS_TYPE         = "type";
    private static final String ELEMENT_GS_CONTAINER    = "container";
    private static final String ELEMENT_GS_DIFF         = "difficulty";
    private static final String ELEMENT_GS_TERRAIN      = "terrain";
    private static final String ELEMENT_GS_SHORTL       = "short_description";
    private static final String ELEMENT_GS_LONGL        = "long_description";
    private static final String ELEMENT_GS_COUNTRY      = "country";
    private static final String ELEMENT_GS_HINTS        = "encoded_hints";

    private static final String ATTRIBUTE_UTF_8     = "UTF-8";
    private static final String ATTRIBUTE_VERSION   = "version";
    private static final String ATTRIBUTE_CREATOR   = "creator";
    private static final String ATTRIBUTE_HREF      = "href";
    private static final String ATTRIBUTE_LAT       = "lat";
    private static final String ATTRIBUTE_LON       = "lon";
    private static final String ATTRIBUTE_GS_ID     = "id";

    private static final String FIX_NONE            = "none";
    private static final String FIX_3D              = "3d";
    private static final String FIX_2D              = "2d";
    private static final String FIX_DGPS            = "dgps";
    private static final String FIX_PPS             = "pps";

    private final Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
    private final Date date = new Date();
    private final String tzOffset;

    private final StringBuffer sb = new StringBuffer(32);
    private final char[] sbChars = new char[32];
    
    private Callback callback;
    private String creator;
    private String fileDate;
    private String fileName;
    private String path;
    private int type;
    private long time;

    private Object queue;
    private TimerTask flusher;
    private boolean go = true;

    private Location refLocation;
    private float refCourse, courseDeviation;
    private int imgNum = 1, ptCount;
    private boolean force;

    private File file;
    private OutputStream output;
    private KXmlSerializer serializer;

    public GpxTracklog(int type, Callback callback, String creator, long time) {
        this.type = type;
        this.callback = callback;
        this.creator = creator;
        this.time = time;
        this.fileDate = dateToFileDate(time);
        this.tzOffset = tzOffset(calendar);
    }

    public long getTime() {
        return time;
    }

    public String getCreator() {
        return creator;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFilePrefix(String filePrefix) {
        this.fileName = (filePrefix == null ? "" : filePrefix) + fileDate + ".gpx";
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void shutdown() {
        synchronized (this) {
            go = false;
            notify();
        }
    }

    public Throwable open() {
        
        // local vars
        Throwable throwable = null;

        // construct path
        path = Config.getFolderURL(type == LOG_TRK ? Config.FOLDER_TRACKS : Config.FOLDER_WPTS) + fileName;

        // try to open and create a file - isolated operation
        try {
            file = File.open(path, Connector.READ_WRITE);
            if (file.exists()) {
                file.delete();
            }
            file.create();
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("file created");
//#endif
        } catch (Throwable t) {
            throwable = t;
//#ifdef __LOG__
            if (log.isEnabled()) log.error("failed to open file: " + t);
//#endif
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    // ignore
                }
                file = null; // gc hint
            }
        }

        // file ready?
        if (throwable == null) {
            try {
                // output stream
                output = new BufferedOutputStream(file.openOutputStream(), 4096);

                // init serializer
                final KXmlSerializer serializer = this.serializer = new KXmlSerializer();
                serializer.setFeature(KXmlSerializer.FEATURE_INDENT_OUTPUT, true);
                serializer.setOutput(output, ATTRIBUTE_UTF_8);
                serializer.startDocument(ATTRIBUTE_UTF_8, null);
                serializer.setPrefix(null, GPX_1_1_NAMESPACE);
                if (type == LOG_WPT) {
                    serializer.setPrefix(GS_1_0_PREFIX, GS_1_0_NAMESPACE);
                }
                serializer.setPrefix(EXT_PREFIX, EXT_NAMESPACE);
                serializer.setPrefix(XDR_PREFIX, XDR_NAMESPACE);
                if (Config.gpxGsmInfo) {
                    serializer.setPrefix(GSM_PREFIX, GSM_NAMESPACE);
                }
                serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_GPX);
                serializer.attribute(DEFAULT_NAMESPACE, ATTRIBUTE_VERSION, "1.1");
                serializer.attribute(DEFAULT_NAMESPACE, ATTRIBUTE_CREATOR, creator);
                if (type == LOG_TRK) { // '==' is ok
                    serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_TRK);
                    serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_TRKSEG);
                }
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("output ready...");
//#endif
            } catch (Throwable t) {
                throwable = t;
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("open failed: " + t);
//#endif
                // cleanup
                close();
            }
        }

        return throwable;
    }

    public void close() {

        // close XML
        if (serializer != null) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("closing document");
//#endif
            try {
                if (type == LOG_TRK) { // '==' is ok
                    serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_TRKSEG);
                    serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_TRK);
                }
                serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_GPX);
                serializer.endDocument();
                serializer.flush();
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("~done");
//#endif
            } catch (IOException e) {
                // ignore
            }
            serializer = null; // gc hint
        }

        // close output
        if (output != null) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("closing output");
//#endif
            try {
                output.close();
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("~done");
//#endif
            } catch (IOException e) {
                // ignore
            }
            output = null; // gc hint
        }

        // close file
        if (file != null) {
            try {
                file.close();
            } catch (IOException e) {
                // ignore
            }
            file = null; // gc hint
        }
    }

    public void run() {

        // open
        final Throwable status = open();
        if (status == null) {

            // start periodic flusher (for tracklog only)
            if (type == LOG_TRK) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("starting flusher");
//#endif
                cz.kruch.track.ui.Desktop.timer.schedule(flusher = new TimerTask() {
                    public void run() {
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("trigger flush at " + new Date());
//#endif
                        insert(Boolean.FALSE);
                    }
                }, 60000L, 60000L);
            }

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("recording started");
//#endif
            // signal recording start
            callback.invoke(new Integer(CODE_RECORDING_START), null, this);

            // pop items until end
            while (go) {
                final Object item;
                synchronized (this) {
                    while (go && queue == null) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            // ignore
                        }
                    }
                    item = queue;
                    queue = null; // gc hint
                }

                if (!go) break;

                try {

                    if (item instanceof Location) {

                        // save <trktp>
                        final Location l = (Location) item;
                        serializeTrkpt(l);
                        Location.releaseInstance(l);

                        /*
                         * no flush here for performance reasons - flusher should do it
                         */

                    } else if (item instanceof Boolean) {

                        // break <trkseg>
                        if (Boolean.TRUE.equals(item) && ptCount > 0) {
                            serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_TRKSEG);
                            serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_TRKSEG);
                            ptCount = 0;
                        }

                        // flush
                        serializer.flush();

                    } else if (item instanceof Waypoint) {

                        // save <wpt>
                        final Waypoint w = (Waypoint) item;
                        final Object uo = w.getUserObject();
                        if (uo instanceof byte[]) {
                            w.setLinkPath(saveImage((byte[]) uo));
                            w.setUserObject(null); // gc hint
                        }
                        serializeWpt(w);

                        // flush
                        serializer.flush();

                        // notify TODO ugly ... why ugly???
                        callback.invoke(new Integer(CODE_WAYPOINT_INSERTED), null, this);

                    }

                } catch (Throwable t) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.error("loop error: " + t);
//#endif
                    // quit loop
                    go = false;
                    // signal fatal error
                    callback.invoke(null, t, this);
                }
            }

            // stop periodic flush
            if (flusher != null) {
                flusher.cancel();
                flusher = null; // gc hint
            }

            // close
            close();

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("signaling recording stopped");
//#endif
            // signal recording stop
            callback.invoke(new Integer(CODE_RECORDING_STOP), null, this);

        } else {

            // signal failure
            callback.invoke(Resources.getString(Resources.DESKTOP_MSG_START_TRACKLOG_FAILED)+ ": " + path, status, this);

        }
    }

    public void writeWpt(final Waypoint wpt) throws IOException {
        serializeWpt(wpt);
    }

    private void serializePt(final QualifiedCoordinates qc) throws IOException {
        final KXmlSerializer serializer = this.serializer;
        final char[] sbChars = this.sbChars;
        int i = doubleToChars(qc.getLat(), 9);
        serializer.attribute(DEFAULT_NAMESPACE, ATTRIBUTE_LAT, sbChars, i);
        i = doubleToChars(qc.getLon(), 9);
        serializer.attribute(DEFAULT_NAMESPACE, ATTRIBUTE_LON, sbChars, i);
        final float alt = qc.getAlt();
        if (!Float.isNaN(alt)) {
            serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_ELEVATION);
            i = doubleToChars(alt, 1);
            serializer.text(sbChars, 0, i);
            serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_ELEVATION);
        }
        ptCount++;
    }

    private void serializeTrkpt(final Location l) throws IOException {
        final KXmlSerializer serializer = this.serializer;
        serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_TRKPT);
        serializePt(l.getQualifiedCoordinates());
        serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_TIME);
        int i = dateToXsdDate(l.getTimestamp());
        serializer.text(sbChars, 0, i);
        serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_TIME);
        serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_FIX);
        switch (l.getFix()) {
            case 0: {
                serializer.text(FIX_NONE);
            } break;
            case 1:
                if (l.isFix3d()) {
                    serializer.text(FIX_3D);
                } else {
                    serializer.text(FIX_2D);
                }
                break;
            case 2: {
                serializer.text(FIX_DGPS);
            } break;
            case 3: {
                serializer.text(FIX_PPS);
            } break;
            default: {
                serializer.text(Integer.toString(l.getFix()));
            }
        }
        serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_FIX);
        final int sat = l.getSat();
        if (sat > 0) {
            serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_SAT);
            switch (sat) {
                case 3: {
                    serializer.text("3");
                } break;
                case 4: {
                    serializer.text("4");
                } break;
                case 5: {
                    serializer.text("5");
                } break;
                case 6: {
                    serializer.text("6");
                } break;
                case 7: {
                    serializer.text("7");
                } break;
                case 8: {
                    serializer.text("8");
                } break;
                case 9: {
                    serializer.text("9");
                } break;
                case 10: {
                    serializer.text("10");
                } break;
                case 11: {
                    serializer.text("11");
                } break;
                case 12: {
                    serializer.text("12");
                } break;
                default:
                    serializer.text(Integer.toString(sat));
            }
            serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_SAT);
        }
        /*
         * extensions - nmea:rmc, nmea:xdr and gsm
         */
        final float course = l.getCourse();
        final float speed = l.getSpeed();
        if (!Float.isNaN(course) || !Float.isNaN(speed) || l.isXdrBound() || Config.gpxGsmInfo) {
            serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_EXTENSIONS);
            if (!Float.isNaN(course)) {
                serializer.startTag(EXT_NAMESPACE, ELEMENT_COURSE);
                i = doubleToChars(course, 1);
                serializer.text(sbChars, 0, i);
                serializer.endTag(EXT_NAMESPACE, ELEMENT_COURSE);
            }
            if (!Float.isNaN(speed)) {
                serializer.startTag(EXT_NAMESPACE, ELEMENT_SPEED);
                i = doubleToChars(speed, 1);
                serializer.text(sbChars, 0, i);
                serializer.endTag(EXT_NAMESPACE, ELEMENT_SPEED);
            }
            if (l.isXdrBound()) {
                serializeXdr(serializer);
            }
            if (Config.gpxGsmInfo) {
                serializeElement(serializer, System.getProperty("com.sonyericsson.net.cellid"), GSM_NAMESPACE, ELEMENT_CELLID);
                serializeElement(serializer, System.getProperty("com.sonyericsson.net.lac"), GSM_NAMESPACE, ELEMENT_LAC);
            }
            serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_EXTENSIONS);
        }
        serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_TRKPT);
    }

    private void serializeGs(final GroundspeakBean bean) throws IOException {
        final KXmlSerializer serializer = this.serializer;
        serializer.startTag(GS_1_0_NAMESPACE, ELEMENT_GS_CACHE);
        serializer.attribute(DEFAULT_NAMESPACE, ATTRIBUTE_GS_ID, bean.id);
        serializer.startTag(GS_1_0_NAMESPACE, ELEMENT_NAME);
        serializer.text(bean.name);
        serializer.endTag(GS_1_0_NAMESPACE, ELEMENT_NAME);
        serializer.startTag(GS_1_0_NAMESPACE, ELEMENT_GS_TYPE);
        serializer.text(bean.type);
        serializer.endTag(GS_1_0_NAMESPACE, ELEMENT_GS_TYPE);
        serializer.startTag(GS_1_0_NAMESPACE, ELEMENT_GS_CONTAINER);
        serializer.text(bean.container);
        serializer.endTag(GS_1_0_NAMESPACE, ELEMENT_GS_CONTAINER);
        serializer.startTag(GS_1_0_NAMESPACE, ELEMENT_GS_DIFF);
        serializer.text(bean.difficulty);
        serializer.endTag(GS_1_0_NAMESPACE, ELEMENT_GS_DIFF);
        serializer.startTag(GS_1_0_NAMESPACE, ELEMENT_GS_TERRAIN);
        serializer.text(bean.terrain);
        serializer.endTag(GS_1_0_NAMESPACE, ELEMENT_GS_TERRAIN);
        serializer.startTag(GS_1_0_NAMESPACE, ELEMENT_GS_COUNTRY);
        serializer.text(bean.country);
        serializer.endTag(GS_1_0_NAMESPACE, ELEMENT_GS_COUNTRY);
        serializeElement(serializer, bean.shortListing, GS_1_0_NAMESPACE, ELEMENT_GS_SHORTL);
        serializeElement(serializer, bean.longListing, GS_1_0_NAMESPACE, ELEMENT_GS_LONGL);
        serializeElement(serializer, bean.encodedHints, GS_1_0_NAMESPACE, ELEMENT_GS_HINTS);
        serializer.endTag(GS_1_0_NAMESPACE, ELEMENT_GS_CACHE);
    }

    private void serializeWpt(final Waypoint wpt) throws IOException {
        final KXmlSerializer serializer = this.serializer;
        serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_WPT);
        serializePt(wpt.getQualifiedCoordinates());
        serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_TIME);
        final int i = dateToXsdDate(wpt.getTimestamp());
        serializer.text(sbChars, 0, i);
        serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_TIME);
        serializeElement(serializer, wpt.getName(), DEFAULT_NAMESPACE, ELEMENT_NAME);
        serializeElement(serializer, wpt.getComment(), DEFAULT_NAMESPACE, ELEMENT_CMT);
        serializeElement(serializer, wpt.getSym(), DEFAULT_NAMESPACE, ELEMENT_SYM);
        final String link = wpt.getLinkPath();
        if (link != null && link.length() > 0) {
            serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_LINK);
            serializer.attribute(DEFAULT_NAMESPACE, ATTRIBUTE_HREF, link);
            serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_LINK);
        }
        if (wpt.getUserObject() instanceof GroundspeakBean) {
            serializeGs((GroundspeakBean) wpt.getUserObject());
        }
        serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_WPT);
    }

    private static void serializeElement(final KXmlSerializer serializer, final String value,
                                         final String ns, final String tag) throws IOException {
        if (value != null && value.length() > 0) {
            serializer.startTag(ns, tag);
            serializer.text(value);
            serializer.endTag(ns, tag);
        }
    }

    private void serializeXdr(final KXmlSerializer serializer) throws IOException {
        final Hashtable xdr = NmeaParser.xdr;
        for (Enumeration keys = xdr.keys(); keys.hasMoreElements(); ) {
            final Object key = keys.nextElement();
            final Float value = (Float) xdr.get(key);
            final String tag = key.toString();
            serializer.startTag(XDR_NAMESPACE, tag);
            final int i = doubleToChars(value.floatValue(), 1);
            serializer.text(sbChars, 0, i);
            serializer.endTag(XDR_NAMESPACE, tag);
        }
    }

    private String saveImage(final byte[] raw) throws IOException {
        File file = null;
        OutputStream output = null;

        try {
            // images path
            final String relPath = "images-" + fileDate;

            // check for 'Images' subdir existence
            final String imgdir = Config.getFolderURL(Config.FOLDER_WPTS) + relPath + "/";
            file = File.open(imgdir, Connector.READ_WRITE);
            if (!file.exists()) {
                file.mkdir();
            }
            file.close();
            file = null;

            // image filename
            final String fileName = "pic-" + imgNum++ + ".jpg";

            // save picture
            file = File.open(imgdir + fileName, Connector.READ_WRITE);
            if (!file.exists()) {
                file.create();
            }
            output = file.openOutputStream();
            output.write(raw);

            // return relative path
            return relPath + "/" + fileName;

        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    public void insert(final Waypoint waypoint) {
        synchronized (this) {
            freeLocationInQueue();
            queue = waypoint;
            notify();
        }
    }

    public void insert(final Location location) {
        synchronized (this) {
            freeLocationInQueue();
            force = true;
            queue = location.clone();
            notify();
        }
    }

    public void insert(final Boolean b) {
        synchronized (this) {
            freeLocationInQueue();
            force = b.booleanValue();
            queue = b;
            notify();
        }
    }

    public void locationUpdated(final Location location) {
        synchronized (this) {
            final Location l = check(location);
            if (l != null) {
                freeLocationInQueue();
                queue = location.clone();
                notify();
            }
        }
    }

    /** must be called from synchronized method */
    private void freeLocationInQueue() {
        if (queue instanceof Location) { // processing thread did not make it - forget it
            Location.releaseInstance((Location) queue);
            queue = null;
        }
    }

    public Location check(final Location location) {

        if (Config.gpxDt == 0) { // 'raw'
            return location;
        }

        final int fix = location.getFix();

        if (fix <= 0 && Config.gpxOnlyValid) {
            return null;
        }

        boolean bLog = false;

        if (refLocation == null) {
            refLocation = location.clone();
            bLog = true;
        } else if (force) {
            force = false;
            bLog = true;
        }

        final boolean bTimeDiff = (location.getTimestamp() - refLocation.getTimestamp()) > (Config.gpxDt * 1000);

        if (bTimeDiff) {
            bLog = true;
        }

        if (!bLog) { // no condition met yet, try ds, dt, deviation
            
            if (fix > 0) {

                // check logging criteria
                if (refLocation.getFix() > 0) {

                    // compute dist from reference location
                    final float r = location.getQualifiedCoordinates().distance(refLocation.getQualifiedCoordinates());

                    // calculate course deviation
                    final float speed = location.isSpeedValid() ? location.getSpeed() : 0F;
                    final float course = location.getCourse();
                    if (!Float.isNaN(course) && speed > MIN_SPEED_WALK) {
                        float diff = course - refCourse;
                        if (diff > 180F) {
                            diff -= 360F;
                        } else if (diff < -180F) {
                            diff += 360F;
                        }
                        courseDeviation += diff;
                        refCourse = course;
                    }

                    // depending on speed, find out whether log or not
                    if (speed < MIN_SPEED_WALK) { /* no move or hiking speed */
                        bLog = r > 50;
                    } else if (speed < MIN_SPEED_BIKE) { /* bike speed */
                        bLog = r > 250 || Math.abs(courseDeviation) > MIN_COURSE_DIVERSION;
                    } else if (speed < MIN_SPEED_CAR) { /* car speed */
                        bLog = r > 500 || Math.abs(courseDeviation) > MIN_COURSE_DIVERSION_FAST;
                    } else { /* ghost rider */
                        bLog = r > 1000 || Math.abs(courseDeviation) > MIN_COURSE_DIVERSION_FAST;
                    }

                    // check user's distance criteria if not going to log
                    if (!bLog && Config.gpxDs > 0) {
                        bLog = r > Config.gpxDs;
                    }

                } else {
                    bLog = true;
                }
            }
        }

        if (bLog) {

            // use location as new reference
            Location.releaseInstance(refLocation);
            refLocation = null;
            refLocation = location.clone();

            // new heading 
            courseDeviation = 0F;

            return location;
        }

        return null;
    }

    private int dateToXsdDate(final long timestamp) {
        date.setTime(timestamp);
        calendar.setTime(date);

        final StringBuffer sb = this.sb.delete(0, this.sb.length());
        final Calendar calendar = this.calendar;

        NavigationScreens.append(sb, calendar.get(Calendar.YEAR)).append('-');
        appendTwoDigitStr(sb, calendar.get(Calendar.MONTH) + 1).append('-');
        appendTwoDigitStr(sb, calendar.get(Calendar.DAY_OF_MONTH)).append('T');
        appendTwoDigitStr(sb, calendar.get(Calendar.HOUR_OF_DAY)).append(':');
        appendTwoDigitStr(sb, calendar.get(Calendar.MINUTE)).append(':');
        appendTwoDigitStr(sb, calendar.get(Calendar.SECOND));
//        sb.append('Z'/*CALENDAR.getTimeZone().getID()*/);
        sb.append(tzOffset);

        final int result = sb.length();
        sb.getChars(0, result, sbChars, 0);

        return result;
    }

    private int doubleToChars(final double value, final int precision) {
        final StringBuffer sb = this.sb.delete(0, this.sb.length());
        NavigationScreens.append(sb, value, precision);

        final int result = sb.length();
        sb.getChars(0, result, sbChars, 0);

        return result;
    }

    public static String dateToFileDate(final long time) {
        final Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTime(new Date(time));
        final StringBuffer sb = new StringBuffer(32);
        NavigationScreens.append(sb, calendar.get(Calendar.YEAR));
        appendTwoDigitStr(sb, calendar.get(Calendar.MONTH) + 1);
        appendTwoDigitStr(sb, calendar.get(Calendar.DAY_OF_MONTH)).append('-');
        appendTwoDigitStr(sb, calendar.get(Calendar.HOUR_OF_DAY));
        appendTwoDigitStr(sb, calendar.get(Calendar.MINUTE));
        appendTwoDigitStr(sb, calendar.get(Calendar.SECOND));

        return sb.toString();
    }

    private static String tzOffset(final Calendar calendar) {
        final int tOffset = calendar.getTimeZone().getRawOffset();
        if (tOffset == 0) {
            return "Z";
        }
        final StringBuffer sb = new StringBuffer(32);
        sb.append(tOffset < 0 ? '-' : '+');
        appendTwoDigitStr(sb, tOffset / 3600000);
        sb.append(':');
        appendTwoDigitStr(sb, (tOffset % 3600000) / 60000);

        return sb.toString();
    }

    private static StringBuffer appendTwoDigitStr(final StringBuffer sb, final int i) {
        if (i < 10) {
            sb.append('0');
        }
        NavigationScreens.append(sb, i);

        return sb;
    }
}
