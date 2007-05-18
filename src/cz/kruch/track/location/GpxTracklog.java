// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import api.location.QualifiedCoordinates;
import api.location.Location;
import api.file.File;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone;

import org.kxml2.io.KXmlSerializer;

import javax.microedition.io.Connector;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.event.Callback;
import cz.kruch.j2se.io.BufferedOutputStream;

/**
 * GPX tracklog.
 */
public final class GpxTracklog extends Thread {
    private static final String GPX_1_1_NAMESPACE   = "http://www.topografix.com/GPX/1/1";
    private static final String DEFAULT_NAMESPACE   = null;
    private static final String EXT_NAMESPACE       = "urn:net:trekbuddy:1.0:nmea:rmc";
    private static final String EXT_PREFIX          = "rmc";

    private static final int    MIN_DT = 5 * 60000;         // 5 min
    private static final float  MIN_SPEED_WALK = 1F;        // 1 m/s ~ 3.6 km/h
    private static final float  MIN_SPEED_BIKE = 5F;        // 5 m/s ~ 18 km/h
    private static final float  MIN_SPEED_CAR  = 10F;       // 10 m/s ~ 36 km/h
    private static final float  MIN_COURSE_DIVERSION      = 15F; // 15 degrees
    private static final float  MIN_COURSE_DIVERSION_FAST = 10F; // 10 degrees

    private static final Calendar CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

    public static final int LOG_TRK = 1000;
    public static final int LOG_WPT = 2000;

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
    private static final String ELEMENT_DESC        = "desc";
    private static final String ELEMENT_LINK        = "link";
    private static final String ELEMENT_EXTENSIONS  = "extensions";
    private static final String ELEMENT_COURSE      = "course";
    private static final String ELEMENT_SPEED       = "speed";

    private static final String ATTRIBUTE_UTF_8     = "UTF-8";
    private static final String ATTRIBUTE_VERSION   = "version";
    private static final String ATTRIBUTE_CREATOR   = "creator";
    private static final String ATTRIBUTE_HREF      = "href";
    private static final String ATTRIBUTE_LAT       = "lat";
    private static final String ATTRIBUTE_LON       = "lon";

    private static final String FIX_NONE    = "none";
    private static final String FIX_3D      = "3d";
    private static final String FIX_2D      = "2d";
    private static final String FIX_DGPS    = "dgps";
    private static final String FIX_PPS     = "pps";

    private int type;
    private Callback callback;
    private String creator;
    private long time;
    private String date;
    private String fileName;

    private boolean go = true;
    private Object queue;

    private Location refLocation;
    private Location lastLocation;
    private boolean reconnected;
    private int count;
    private int imgNum = 1;

    public GpxTracklog(int type, Callback callback, String creator, long time) {
        this.type = type;
        this.callback = callback;
        this.creator = creator;
        this.time = time;
        this.date = dateToFileDate(time);
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
        this.fileName = (filePrefix == null ? "" : filePrefix) + date + ".gpx";
    }

    public void destroy() {
        synchronized (this) {
            go = false;
            notify();
        }
    }

    public void run() {
        File file = null;
        OutputStream output = null;
        Throwable throwable = null;

        // construct path
        String path = (type == LOG_TRK ? Config.getFolderTracks() : Config.getFolderWaypoints()) + fileName;

        // try to create file - isolated operation
        try {
            file = File.open(Connector.open(path, Connector.READ_WRITE));
            if (!file.exists()) {
                file.create();
            }
        } catch (Throwable t) {
            throwable = t;
        }

        // file created?
        if (throwable == null) {

            // signal recording start
            callback.invoke(new Integer(CODE_RECORDING_START), null, this);

            KXmlSerializer serializer = null;

            try {
                output = new BufferedOutputStream(file.openOutputStream(), 512);
                serializer = new KXmlSerializer();
                serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
                serializer.setOutput(output, ATTRIBUTE_UTF_8);
                serializer.startDocument(ATTRIBUTE_UTF_8, null);
                serializer.setPrefix(null, GPX_1_1_NAMESPACE);
                serializer.setPrefix(EXT_PREFIX, EXT_NAMESPACE);
                serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_GPX);
                serializer.attribute(DEFAULT_NAMESPACE, ATTRIBUTE_VERSION, "1.1");
                serializer.attribute(DEFAULT_NAMESPACE, ATTRIBUTE_CREATOR, creator);
                if (type == LOG_TRK) { // '==' is ok
                    serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_TRK);
                    serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_TRKSEG);
                }

                for (; go ;) {
                    // pop item
                    Object item;
                    synchronized (this) {
                        while (go && queue == null) {
                            try {
                                wait();
                            } catch (InterruptedException e) {
                                // ignore
                            }
                        }
                        item = queue;
                        queue = null;
                    }

                    if (!go) break;

                    if (type == LOG_WPT) { // '==' is ok
                        if (item instanceof Waypoint) {
                            Waypoint w = (Waypoint) item;
                            if (w.getUserObject() != null) {
                                w.setLinkPath(saveImage((byte[]) w.getUserObject()));
                            }
                            serializeWpt(serializer, w);
                            serializer.flush();
                            callback.invoke(new Integer(CODE_WAYPOINT_INSERTED), null, this);
                        }
                    } else { // type == LOG_TRK
                        if (item instanceof Location) {
                            Location l = check((Location) item);
                            if (l != null) {
                                serializeTrkpt(serializer, l);
                                /* performance hit */
                                // serializer.flush();
                            }
                            Location.releaseInstance(l);
                        } else if (item instanceof Boolean) {
                            serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_TRKSEG);
                            serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_TRKSEG);
                            serializer.flush();
                        }
                    }
                }

                // signal recording stop
                callback.invoke(new Integer(CODE_RECORDING_STOP), null, this);

            } catch (Throwable t) {

                // signal error
                callback.invoke(null, t, this);

            } finally {

                // close XML
                if (serializer != null) {
                    try {
                        if (type == LOG_TRK) { // '==' is ok
                            serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_TRKSEG);
                            serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_TRK);
                        }
                        serializer.endTag(null, ELEMENT_GPX);
                        serializer.endDocument();
                        serializer.flush();
                    } catch (IOException e) {
                        // ignore
                    }
                }

                // close output
                if (output != null) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        } else {
            // signal failure
            callback.invoke("Failed to create GPX file " + path, throwable, this);
        }

        // close file
        if (file != null) {
            try {
                file.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private static void serializePt(KXmlSerializer serializer, Location l) throws IOException {
        QualifiedCoordinates qc = l.getQualifiedCoordinates();
        serializer.attribute(DEFAULT_NAMESPACE, ATTRIBUTE_LAT, Double.toString(qc.getLat()));
        serializer.attribute(DEFAULT_NAMESPACE, ATTRIBUTE_LON, Double.toString(qc.getLon()));
        final float alt = qc.getAlt();
        if (alt > -1F) {
            serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_ELEVATION);
            serializer.text(Float.toString(alt));
            serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_ELEVATION);
        }
        serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_TIME);
        serializer.text(dateToXsdDate(l.getTimestamp()));
        serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_TIME);
        if (l instanceof Waypoint) {
            Waypoint w = (Waypoint) l;
            if (w.getName() != null && w.getName().length() > 0) {
                serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_NAME);
                serializer.text(w.getName());
                serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_NAME);
            }
            if (w.getComment() != null && w.getComment().length() > 0) {
                serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_DESC);
                serializer.text(w.getComment());
                serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_DESC);
            }
            if (w.getLinkPath() != null && w.getLinkPath().length() > 0) {
                serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_LINK);
                serializer.attribute(DEFAULT_NAMESPACE, ATTRIBUTE_HREF, w.getLinkPath());
                serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_LINK);
            }
        }
        serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_FIX);
        switch (l.getFix()) {
            case 0: {
                serializer.text(FIX_NONE);
            } break;
            case 1:
                if (alt > -1F) {
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
        if (sat > -1) {
            serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_SAT);
            serializer.text(Integer.toString(sat));
            serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_SAT);
        }
/*
        if (l.getHdop() > -1F) {
            serializer.startTag(DEFAULT_NAMESPACE, "hdop");
            serializer.text(Float.toString(l.getHdop()));
            serializer.endTag(DEFAULT_NAMESPACE, "hdop");
        }
*/
        final float course = l.getCourse();
        final float speed = l.getSpeed();
        if (course > -1F || speed > -1F) {
            serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_EXTENSIONS);
            if (course > -1F) {
                serializer.startTag(EXT_NAMESPACE, ELEMENT_COURSE);
                serializer.text(Float.toString(course));
                serializer.endTag(EXT_NAMESPACE, ELEMENT_COURSE);
            }
            if (speed > -1F) {
                serializer.startTag(EXT_NAMESPACE, ELEMENT_SPEED);
                serializer.text(Float.toString(speed));
                serializer.endTag(EXT_NAMESPACE, ELEMENT_SPEED);
            }
            serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_EXTENSIONS);
        }
    }

    private static void serializeTrkpt(KXmlSerializer serializer, Location l) throws IOException {
        serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_TRKPT);
        serializePt(serializer, l);
        serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_TRKPT);
    }

    private static void serializeWpt(KXmlSerializer serializer, Waypoint w) throws IOException {
        serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_WPT);
        serializePt(serializer, w);
        serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_WPT);
    }

    private String saveImage(byte[] raw) throws IOException {
        api.file.File file = null;
        OutputStream output = null;

        try {
            // images path
            String relPath = "images-" + date;

            // check for 'Images' subdir existence
            String imgdir = Config.getFolderTracks() + "/" + relPath + "/";
            file = File.open(Connector.open(imgdir, Connector.READ_WRITE));
            if (!file.exists()) {
                file.mkdir();
            }
            file.close();
            file = null;

            // image filename
            String fileName = "pic-" + imgNum++ + ".jpg";

            // save picture
            file = File.open(Connector.open(imgdir + fileName, Connector.READ_WRITE));
            if (!file.exists()) {
                file.create();
            }
            output = new BufferedOutputStream(file.openOutputStream(), 4096);
            output.write(raw);
            output.flush();

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

    public void insert(Waypoint waypoint) {
        synchronized (this) {
            queue = waypoint;
            notify();
        }
    }

    public void insert(Location l) {
        synchronized (this) {
            reconnected = true;
            queue = l;
            notify();
        }
    }

    public void insert(Boolean b) {
        synchronized (this) {
            reconnected = true;
            queue = b;
            notify();
        }
    }

    public void locationUpdated(Location location) {
        synchronized (this) {
            queue = location.clone();
            notify();
        }
    }

    public Location check(Location location) {

        if (Config.gpxRaw) {
            return location;
        }

        /*
         * GPS dancing detection.
         * Rule #1: no course change over 90 degrees within 1 sec
         */

        boolean dance = false;

        if (location.getFix() > 0) {
            if (lastLocation != null) {
                if ((location.getTimestamp() - lastLocation.getTimestamp()) < 1250) {
                    int courseDiff = Math.abs((int) (location.getCourse() - lastLocation.getCourse()));
                    if (courseDiff >= 180) {
                        courseDiff = 360 - courseDiff;
                    }
                    if (courseDiff >= 90) {
                        dance = true;
                    }
                }
            }

            // this one becomes last one
            Location.releaseInstance(lastLocation);
            lastLocation = location.clone();
        }

        if (refLocation == null) {
            refLocation = location.clone();
        }

        boolean bLog = false;
        boolean bTimeDiff = (location.getTimestamp() - refLocation.getTimestamp()) > MIN_DT;

        if (reconnected) {
            reconnected = false;
            bLog = true;
        }

        if (location.getFix() > 0) {
            if (count++ == 1) {
                bLog = true; // log start point (2nd valid position;
            }
        }

        if (!bTimeDiff) {
            if (location.getFix() > 0) {
                if (refLocation.getFix() > 0) {
                    // calculate distance
                    QualifiedCoordinates refCoordinates = refLocation.getQualifiedCoordinates();
/*
                    // compute dist from reference location
                    double latDiff = qc.getLat() - refCoordinates.getLat();
                    double lonDiff = qc.getLon() - refCoordinates.getLon();
                    double r = Math.sqrt(latDiff * latDiff + lonDiff * lonDiff);
*/
                    float r = location.getQualifiedCoordinates().distance(refCoordinates);

                    // get speed and course
                    float speed = location.getSpeed();
                    float course = location.getCourse();
                    float refCourse = refLocation.getCourse();

                    // depending on speed, find out whether log or not
                    if (speed < MIN_SPEED_WALK) { /* no move or hiking speed */
//                        boolean bPosDiff = r > MIN_DL_WALK;
                        boolean bPosDiff = r > 50;
                        bLog = bPosDiff;
                    } else if (speed < MIN_SPEED_BIKE) { /* bike speed */
//                        boolean bPosDiff = r > MIN_DL_BIKE;
                        boolean bPosDiff = r > 250;
                        boolean bCourseDiff = course > -1F && refCourse > -1F ? Math.abs(course - refCourse) > MIN_COURSE_DIVERSION : false;
                        bLog = bPosDiff || bCourseDiff;
                    } else if (speed < MIN_SPEED_CAR) {
//                        boolean bPosDiff = r > MIN_DL_CAR;
                        boolean bPosDiff = r > 500;
                        boolean bCourseDiff = course > -1F && refCourse > -1F ? Math.abs(course - refCourse) > MIN_COURSE_DIVERSION_FAST : false;
                        bLog = bPosDiff || bCourseDiff;
                    } else {
//                        boolean bPosDiff = r > 2 * MIN_DL_CAR;
                        boolean bPosDiff = r > 1000;
                        boolean bCourseDiff = course > -1F && refCourse > -1F ? Math.abs(course - refCourse) > MIN_COURSE_DIVERSION_FAST : false;
                        bLog = bPosDiff || bCourseDiff;
                    }
                } else {
                    bLog = true;
                }
            }
        } else { // min 1x per period
            bLog = true;
        }

        if (bLog) {

            /*
             * last chance - if this is not time periodic log, skip
             * dancing positions
             */
            if (!bTimeDiff && dance) {
                return null;
            }

            // use location as new reference
            Location.releaseInstance(refLocation);
            refLocation = location.clone();

            return location;
        }

        return null;
    }

    public static String dateToXsdDate(long timestamp) {
        CALENDAR.setTime(new Date(timestamp));
        StringBuffer sb = new StringBuffer();
        sb.append(CALENDAR.get(Calendar.YEAR)).append('-');
        appendTwoDigitStr(sb, CALENDAR.get(Calendar.MONTH) + 1).append('-');
        appendTwoDigitStr(sb, CALENDAR.get(Calendar.DAY_OF_MONTH)).append('T');
        appendTwoDigitStr(sb, CALENDAR.get(Calendar.HOUR_OF_DAY)).append(':');
        appendTwoDigitStr(sb, CALENDAR.get(Calendar.MINUTE)).append(':');
        appendTwoDigitStr(sb, CALENDAR.get(Calendar.SECOND));
        sb.append('Z'/*CALENDAR.getTimeZone().getID()*/);

        return sb.toString();
    }

    public static String dateToFileDate(long time) {
        CALENDAR.setTime(new Date(time/* + Config.getSafeInstance().getTimeZoneOffset() * 1000*/));
        StringBuffer sb = new StringBuffer();
        sb.append(CALENDAR.get(Calendar.YEAR)).append('-');
        appendTwoDigitStr(sb, CALENDAR.get(Calendar.MONTH) + 1).append('-');
        appendTwoDigitStr(sb, CALENDAR.get(Calendar.DAY_OF_MONTH)).append('-');
        appendTwoDigitStr(sb, CALENDAR.get(Calendar.HOUR_OF_DAY)).append('-');
        appendTwoDigitStr(sb, CALENDAR.get(Calendar.MINUTE)).append('-');
        appendTwoDigitStr(sb, CALENDAR.get(Calendar.SECOND));

        return sb.toString();
    }

    private static StringBuffer appendTwoDigitStr(StringBuffer sb, int i) {
        if (i < 10) {
            sb.append('0');
        }
        sb.append(i);

        return sb;
    }
}
