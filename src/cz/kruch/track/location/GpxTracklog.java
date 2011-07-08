// @LICENSE@

package cz.kruch.track.location;

import api.location.QualifiedCoordinates;
import api.location.Location;
import api.file.File;
import api.io.BufferedOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.TimerTask;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import org.kxml2.io.HXmlSerializer;

import javax.microedition.io.Connector;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.event.Callback;
import cz.kruch.track.ui.NavigationScreens;
import cz.kruch.track.Resources;
import cz.kruch.track.util.NmeaParser;

/**
 * GPX.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class GpxTracklog implements Runnable {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Gpx");
//#endif

    private static final String DEFAULT_NAMESPACE   = null; // this is wrong but KXML handles it well :-)
    private static final String GPX_1_1_NAMESPACE   = "http://www.topografix.com/GPX/1/1";

    public static final String GS_1_0_NAMESPACE     = "http://www.groundspeak.com/cache/1/0";
    public static final String GS_1_0_PREFIX        = "groundspeak";
    public static final String AU_1_0_NAMESPACE     = "http://geocaching.com.au/geocache/1";
    public static final String AU_1_0_PREFIX        = "au";

    private static final String NMEA_NAMESPACE      = "http://trekbuddy.net/2009/01/gpx/nmea";
    private static final String NMEA_PREFIX         = "nmea";
    private static final String GSM_NAMESPACE       = "http://trekbuddy.net/2009/01/gpx/gsm";
    private static final String GSM_PREFIX          = "gsm";

    private static final float  MIN_SPEED_WALK              = 1F;  // 1 m/s ~ 3.6 km/h
    private static final float  MIN_SPEED_BIKE              = 5F;  // 5 m/s ~ 18 km/h
    private static final float  MIN_SPEED_CAR               = 10F; // 10 m/s ~ 36 km/h
    private static final float  MIN_COURSE_DIVERSION        = 15F; // 15 degrees
    private static final float  MIN_COURSE_DIVERSION_FAST   = 10F; // 10 degrees

    public static final int LOG_WPT = 0;
    public static final int LOG_TRK = 1;

    public static final int CODE_RECORDING_STOP    = 0;
    public static final int CODE_RECORDING_START   = 1;

    private static final String ELEMENT_GPX             = "gpx";
    private static final String ELEMENT_TRK             = "trk";
    private static final String ELEMENT_TRKSEG          = "trkseg";
    private static final String ELEMENT_TRKPT           = "trkpt";
    private static final String ELEMENT_TIME            = "time";
    private static final String ELEMENT_ELEVATION       = "ele";
    private static final String ELEMENT_FIX             = "fix";
    private static final String ELEMENT_SAT             = "sat";
    private static final String ELEMENT_WPT             = "wpt";
    private static final String ELEMENT_GEOIDH          = "geoidheight";
    private static final String ELEMENT_NAME            = "name";
    private static final String ELEMENT_CMT             = "cmt";
    private static final String ELEMENT_SYM             = "sym";
    private static final String ELEMENT_LINK            = "link";
    private static final String ELEMENT_EXTENSIONS      = "extensions";
    private static final String ELEMENT_COURSE          = "course";
    private static final String ELEMENT_SPEED           = "speed";
    private static final String ELEMENT_CELLID          = "cellid";
    private static final String ELEMENT_LAC             = "lac";
    private static final String ELEMENT_SENSORS         = "sensors";
    private static final String ELEMENT_SENSOR          = "sensor";
    private static final String ELEMENT_GS_CACHE        = "cache";
    private static final String ELEMENT_GS_TYPE         = "type";
    private static final String ELEMENT_GS_CONTAINER    = "container";
    private static final String ELEMENT_GS_DIFF         = "difficulty";
    private static final String ELEMENT_GS_TERRAIN      = "terrain";
    private static final String ELEMENT_GS_SHORTL       = "short_description";
    private static final String ELEMENT_GS_LONGL        = "long_description";
    private static final String ELEMENT_GS_COUNTRY      = "country";
    private static final String ELEMENT_GS_HINTS        = "encoded_hints";
    private static final String ELEMENT_AU_CACHE        = "geocache";
    private static final String ELEMENT_AU_SUMMARY      = "summary";
    private static final String ELEMENT_AU_DESC         = "description";
    private static final String ELEMENT_AU_HINTS        = "hints";

    private static final String ELEMENT_LOGS            = "logs";
    private static final String ELEMENT_LOG             = "log";
    private static final String ELEMENT_DATE            = "date";
    private static final String ELEMENT_TYPE            = "type";
    private static final String ELEMENT_TEXT            = "text";
    private static final String ELEMENT_GS_FINDER       = "finder";
    private static final String ELEMENT_AU_FINDER       = "geocacher";

    private static final String ATTRIBUTE_UTF_8         = "UTF-8";
    private static final String ATTRIBUTE_ISO_8859_1    = "ISO-8859-1";
    private static final String ATTRIBUTE_VERSION       = "version";
    private static final String ATTRIBUTE_CREATOR       = "creator";
    private static final String ATTRIBUTE_HREF          = "href";
    private static final String ATTRIBUTE_LAT           = "lat";
    private static final String ATTRIBUTE_LON           = "lon";
    private static final String ATTRIBUTE_ID            = "id";
    private static final String ATTRIBUTE_STATUS        = "status";
    private static final String ATTRIBUTE_AVAILABLE     = "available";

    private static final String FIX_NONE    = "none";
    private static final String FIX_2D      = "2d";
    private static final String FIX_3D      = "3d";
    private static final String FIX_DGPS    = "dgps";

    private final Calendar calendar;
    private final Date date;
/*
    private final String tzOffset;
*/

    private final StringBuffer sb;
    private final char[] sbChars;
    
    private Callback callback;
    private String creator;
    private String url, fileDate, fileName, filePrefix;
    private long time;
    private int type;

    private Object queue;
    private TimerTask flusher;
    private boolean go;

    private Location refLocation;
    private float refCourse, courseDeviation;
    private int /*imgNum, */ptCount;
    private boolean force;

    private File file;
    private HXmlSerializer serializer;

    private Thread thread;

    public GpxTracklog(int type, Callback callback, String creator, long time) {
        this.type = type;
        this.callback = callback;
        this.creator = creator;
        this.time = time;
        this.fileDate = dateToFileDate(time);
/*
        this.tzOffset = tzOffset(calendar);
*/
        this.calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        this.date = new Date();
        this.sb = new StringBuffer(32);
        this.sbChars = new char[32];
    }

    public long getTime() {
        return time;
    }

    public String getCreator() {
        return creator;
    }

    public String getDefaultFileName() {
        return (filePrefix == null ? "" : filePrefix) + fileDate + ".gpx";
    }

    public String getFileName() {
        return fileName;
    }

    public String getURL() {
        return url;
    }

    public void setFilePrefix(String filePrefix) {
        this.filePrefix = filePrefix;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public boolean isAlive() {
        if (thread != null) {
            return thread.isAlive();
        }
        return false;
    }

    public void start() {
        go = true;
        thread = new Thread(this, "GPX tracklog");
        thread.start();
    }

    public void join() throws InterruptedException {
        if (thread != null) {
            thread.join();
        }
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
        if (fileName == null) {
            fileName = getDefaultFileName();
        }
        url = Config.getFolderURL(type == LOG_TRK ? Config.FOLDER_TRACKS : Config.FOLDER_WPTS) + fileName;

        // try to open and create a file - isolated operation
        try {
            file = File.open(url, Connector.READ_WRITE);
            if (file.exists()) {
                file.delete();
            }
            file.create();
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("file created");
//#endif
        } catch (Exception e) {
            throwable = e;
//#ifdef __LOG__
            if (log.isEnabled()) log.error("failed to open file: " + e);
//#endif
            // cleanup - safe operation
            close();
        }

        // file ready?
        if (throwable == null) {
            try {
                // output stream
                final OutputStream output = new BufferedOutputStream(file.openOutputStream(), 4096, true);

                // init serializer
                final HXmlSerializer serializer = this.serializer = new HXmlSerializer();
                serializer.setFeature(HXmlSerializer.FEATURE_INDENT_OUTPUT, true);
                if (type == LOG_WPT) {
                    serializer.setOutput(output, ATTRIBUTE_UTF_8);
                    serializer.startDocument(ATTRIBUTE_UTF_8, null);
                } else {
                    serializer.setOutput(output, ATTRIBUTE_ISO_8859_1);
                    serializer.startDocument(ATTRIBUTE_ISO_8859_1, null);
                }
                serializer.setPrefix(null, GPX_1_1_NAMESPACE);
                if (type == LOG_WPT) {
                    serializer.setPrefix(GS_1_0_PREFIX, GS_1_0_NAMESPACE);
                    serializer.setPrefix(AU_1_0_PREFIX, AU_1_0_NAMESPACE);
                }
                serializer.setPrefix(NMEA_PREFIX, NMEA_NAMESPACE);
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
            } catch (Exception e) {
                throwable = e;
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("write prolog failed: " + e);
//#endif
                // cleanup - safe operation
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
            final HXmlSerializer s = this.serializer;
            try {
                if (type == LOG_TRK) { // '==' is ok
                    s.endTag(DEFAULT_NAMESPACE, ELEMENT_TRKSEG);
                    s.endTag(DEFAULT_NAMESPACE, ELEMENT_TRK);
                }
                s.endTag(DEFAULT_NAMESPACE, ELEMENT_GPX);
                s.endDocument(); // includes writer flush
                s.close();
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("~done");
//#endif
            } catch (Exception e) {
                // ignore
            }
            serializer = null; // gc hint
        }

        // close file
        if (file != null) {
            try {
                file.close();
            } catch (Exception e) {
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
                cz.kruch.track.ui.Desktop.schedule(flusher = new Flusher(this), 60000L, 60000L);
            }

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("recording started");
//#endif
            // signal recording start
            callback.invoke(new Integer(CODE_RECORDING_START), null, this);

            // pop items until end
            while (true) {

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
                    if (!go)
                        break;
                }

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
            callback.invoke(Resources.getString(Resources.DESKTOP_MSG_START_TRACKLOG_FAILED)+ ": " + url, status, this);

        }
    }

    public void writeWpt(final Waypoint wpt) throws IOException {
        serializeWpt(wpt);
    }

    private void serializePt(final QualifiedCoordinates qc) throws IOException {
        final HXmlSerializer serializer = this.serializer;
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
        final HXmlSerializer serializer = this.serializer;
        final char[] sbChars = this.sbChars;
        serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_TRKPT);
        serializePt(l.getQualifiedCoordinates());
        serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_TIME);
        int i = dateToXsdDate(l.getTimestamp());
        serializer.text(sbChars, 0, i);
        serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_TIME);
        if (!Float.isNaN(NmeaParser.geoidh) && NmeaParser.geoidh != 0D) {
            serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_GEOIDH);
            i = doubleToChars(NmeaParser.geoidh, 1);
            serializer.text(sbChars, 0, i);
            serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_GEOIDH);
        }
        switch (l.getFix()) {
            case 0: {
                serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_FIX);
                serializer.text(FIX_NONE);
                serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_FIX);
            } break;
            case 2: {
                serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_FIX);
                serializer.text(FIX_2D);
                serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_FIX);
            } break;
            case 3: {
                serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_FIX);
                serializer.text(FIX_3D);
                serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_FIX);
            } break;
        }
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
                serializer.startTag(NMEA_NAMESPACE, ELEMENT_COURSE);
                i = doubleToChars(course, 1);
                serializer.text(sbChars, 0, i);
                serializer.endTag(NMEA_NAMESPACE, ELEMENT_COURSE);
            }
            if (!Float.isNaN(speed)) {
                serializer.startTag(NMEA_NAMESPACE, ELEMENT_SPEED);
                i = doubleToChars(speed, 1);
                serializer.text(sbChars, 0, i);
                serializer.endTag(NMEA_NAMESPACE, ELEMENT_SPEED);
            }
            if (l.isXdrBound()) {
                serializeXdr(serializer);
            }
            if (Config.gpxGsmInfo) {
                serializeElement(serializer, cz.kruch.track.ui.nokia.DeviceControl.getGsmCellId(), GSM_NAMESPACE, ELEMENT_CELLID);
                serializeElement(serializer, cz.kruch.track.ui.nokia.DeviceControl.getGsmLac(), GSM_NAMESPACE, ELEMENT_LAC);
            }
            serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_EXTENSIONS);
        }
        serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_TRKPT);
    }

    private void serializeGs(final GroundspeakBean bean) throws IOException {
        final HXmlSerializer serializer = this.serializer;
        final String prefix = bean.getNs();
        final String ns;
        final String eleCache, eleShortL, eleLongL, eleHints, eleDate, eleFinder;
        if (prefix == GS_1_0_PREFIX) { // '==' is OK
            ns = GS_1_0_NAMESPACE;
            eleShortL = ELEMENT_GS_SHORTL;
            eleLongL = ELEMENT_GS_LONGL;
            eleHints = ELEMENT_GS_HINTS;
            eleDate = ELEMENT_DATE;
            eleFinder = ELEMENT_GS_FINDER;
            serializer.startTag(GS_1_0_NAMESPACE, eleCache = ELEMENT_GS_CACHE);
            if (bean.getId() != null) {
                serializer.attribute(DEFAULT_NAMESPACE, ATTRIBUTE_ID, bean.getId());
            }
            serializer.attribute(DEFAULT_NAMESPACE, ATTRIBUTE_AVAILABLE, "True");
        } else if (prefix == AU_1_0_PREFIX) {
            ns = AU_1_0_NAMESPACE;
            eleShortL = ELEMENT_AU_SUMMARY;
            eleLongL = ELEMENT_AU_DESC;
            eleHints = ELEMENT_AU_HINTS;
            eleDate = ELEMENT_TIME;
            eleFinder = ELEMENT_AU_FINDER;
            serializer.startTag(AU_1_0_NAMESPACE, eleCache = ELEMENT_AU_CACHE);
            serializer.attribute(DEFAULT_NAMESPACE, ATTRIBUTE_STATUS, "Available");
        } else {
            return;
        }
        serializeElement(serializer, bean.getName(), ns, ELEMENT_NAME);
        serializeElement(serializer, bean.getType(), ns, ELEMENT_GS_TYPE);
        serializeElement(serializer, bean.getContainer(), ns, ELEMENT_GS_CONTAINER);
        serializeElement(serializer, bean.getDifficulty(), ns, ELEMENT_GS_DIFF);
        serializeElement(serializer, bean.getTerrain(), ns, ELEMENT_GS_TERRAIN);
        serializeElement(serializer, bean.getCountry(), ns, ELEMENT_GS_COUNTRY);
        serializeElement(serializer, bean.getShortListing(), ns, eleShortL);
        serializeElement(serializer, bean.getLongListing(), ns, eleLongL);
        serializeElement(serializer, bean.getEncodedHints(), ns, eleHints);
        final Vector logs = bean.getLogs();
        if (logs != null && logs.size() > 0) {
            serializer.startTag(ns, ELEMENT_LOGS);
            for (int i = 0; i < logs.size(); i++) {
                final GroundspeakBean.Log entry = (GroundspeakBean.Log) logs.elementAt(i);
                serializer.startTag(ns, ELEMENT_LOG);
                serializer.attribute(DEFAULT_NAMESPACE, ATTRIBUTE_ID, entry.getId());
                serializeElement(serializer, entry.getDate(), ns, eleDate);
                serializeElement(serializer, entry.getType(), ns, ELEMENT_TYPE);
                serializeElement(serializer, entry.getFinder(), ns, eleFinder);
                serializeElement(serializer, entry.getText(), ns, ELEMENT_TEXT);
                serializer.endTag(ns, ELEMENT_LOG);
            }
            serializer.endTag(ns, ELEMENT_LOGS);
        }
        serializer.endTag(ns, eleCache);
    }

    private void serializeWpt(final Waypoint wpt) throws IOException {
        final HXmlSerializer serializer = this.serializer;
        final char[] sbChars = this.sbChars;
        serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_WPT);
        serializePt(wpt.getQualifiedCoordinates());
        if (wpt.getTimestamp() != 0) {
            serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_TIME);
            final int i = dateToXsdDate(wpt.getTimestamp());
            serializer.text(sbChars, 0, i);
            serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_TIME);
        }
        if (!Float.isNaN(NmeaParser.geoidh) && NmeaParser.geoidh != 0D) {
            serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_GEOIDH);
            final int i = doubleToChars(NmeaParser.geoidh, 1);
            serializer.text(sbChars, 0, i);
            serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_GEOIDH);
        }
        serializeElement(serializer, wpt.getName(), DEFAULT_NAMESPACE, ELEMENT_NAME);
        serializeElement(serializer, wpt.getComment(), DEFAULT_NAMESPACE, ELEMENT_CMT);
        serializeElement(serializer, wpt.getSym(), DEFAULT_NAMESPACE, ELEMENT_SYM);
        final Vector links = wpt.getLinks();
        if (links != null) {
            for (int N = links.size(), j = 0; j < N; j++) {
                serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_LINK);
                serializer.attribute(DEFAULT_NAMESPACE, ATTRIBUTE_HREF, (String) links.elementAt(j));
                serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_LINK);
            }
        }
        if (wpt.getUserObject() instanceof GroundspeakBean) {
            if (((GroundspeakBean) wpt.getUserObject()).isParsed()) {
                serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_EXTENSIONS);
                serializeGs((GroundspeakBean) wpt.getUserObject());
                serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_EXTENSIONS);
            }
        }
        serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_WPT);
    }

    private static void serializeElement(final HXmlSerializer serializer, final String value,
                                         final String ns, final String tag) throws IOException {
        if (value != null && value.length() > 0) {
            serializer.startTag(ns, tag);
            serializer.text(value);
            serializer.endTag(ns, tag);
        }
    }

    private void serializeXdr(final HXmlSerializer serializer) throws IOException {
        final Hashtable xdr = NmeaParser.xdr;
        if (xdr.size() > 0) {
            serializer.startTag(NMEA_NAMESPACE, ELEMENT_SENSORS);
            for (final Enumeration keys = xdr.keys(); keys.hasMoreElements(); ) {
                final Object key = keys.nextElement();
                final Float value = (Float) xdr.get(key);
                final String id = key.toString();
                serializer.startTag(NMEA_NAMESPACE, ELEMENT_SENSOR);
                serializer.attribute(DEFAULT_NAMESPACE, ATTRIBUTE_ID, id);
                final int i = doubleToChars(value.floatValue(), 1);
                serializer.text(sbChars, 0, i);
                serializer.endTag(NMEA_NAMESPACE, ELEMENT_SENSOR);
            }
            serializer.endTag(NMEA_NAMESPACE, ELEMENT_SENSORS);
        }
    }

/*
    public void insert(final Waypoint waypoint) {
        synchronized (this) {
            freeLocationInQueue();
            queue = waypoint;
            notify();
        }
    }

*/
    public void insert(final Location location) {
        synchronized (this) {
            freeLocationInQueue();
            force = true;
            queue = location._clone();
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
                queue = location._clone();
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

    private Location check(final Location location) {

        final int fix = location.getFix();

        if (fix <= 0 && Config.gpxOnlyValid) {
            return null;
        }

        if (Config.gpxDt == 0) { // 'raw'
            return location;
        }

        boolean bLog = false;

        if (refLocation == null) {
            refLocation = location._clone();
            bLog = true;
        } else if (force) {
            force = false;
            bLog = true;
        }

        final boolean bTimeDiff = (location.getTimestamp() - refLocation.getTimestamp()) >= (Config.gpxDt * 1000);

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
            refLocation = location._clone();

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
        if (Config.gpxSecsDecimal) {
            final long ms = timestamp % 1000;
            if (ms > 0) {
                sb.append('.');
                appendFractional(sb, (int) ms);
            }
        }
        sb.append('Z');

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

/*
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
*/

    private static StringBuffer appendTwoDigitStr(final StringBuffer sb, final int i) {
        if (i < 10) {
            sb.append('0');
        }
        NavigationScreens.append(sb, i);

        return sb;
    }

    private static StringBuffer appendFractional(final StringBuffer sb, int i) {
        int f = i / 100;
        sb.append(f);
        i = i % 100;
        if (i != 0) {
            f = i / 10;
            sb.append(f);
            f = i % 10;
            if (f != 0) {
                sb.append(f);
            }
        }

        return sb;
    }

    private static final class Flusher extends TimerTask {

        private GpxTracklog tracklog;

        /* to avoid $1 */
        public Flusher(GpxTracklog tracklog) {
            this.tracklog = tracklog;
        }

        public void run() {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("trigger flush at " + new Date());
//#endif
            tracklog.insert(Boolean.FALSE);
        }
    }
}
