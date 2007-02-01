// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import api.location.QualifiedCoordinates;
import api.location.Location;

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

public final class GpxTracklog extends Thread {
    private static final String GPX_1_1_NAMESPACE   = "http://www.topografix.com/GPX/1/1";
    private static final String DEFAULT_NAMESPACE   = null;
    private static final String EXT_NAMESPACE       = "urn:net:trekbuddy:1.0:nmea:rmc";
    private static final String EXT_PREFIX          = "rmc";

    private static final int    MIN_DT = 5 * 60000;         // 5 min
//    private static final double MIN_DL_WALK = 0.00054D;     // cca 50 m
//    private static final double MIN_DL_BIKE = 0.00270D;     // cca 250 m
//    private static final double MIN_DL_CAR  = 0.00540D;     // cca 500 m
    private static final float  MIN_SPEED_WALK = 2F;        // 2 * 1.852 km/h
    private static final float  MIN_SPEED_BIKE = 10F;       // 10 * 1.852 km/h
    private static final float  MIN_SPEED_CAR  = 20F;       // 20 * 1.852 km/h
    private static final float  MIN_COURSE_DIVERSION      = 15F; // 15 degrees
    private static final float  MIN_COURSE_DIVERSION_FAST = 10F; // 10 degrees

//    private static final double ONE_KNOT_DISTANCE_IN_DEGREES = 0.016637157;

    private static final Calendar CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

    public static final String LOG_TRK = "track";
    public static final String LOG_WPT = "waypoints";

    public static final int CODE_RECORDING_STOP    = 0;
    public static final int CODE_RECORDING_START   = 1;
    public static final int CODE_WAYPOINT_INSERTED = 2;

    private String type;
    private Callback callback;
    private String creator;
    private long time;
    private String date;

    private boolean go = true;
    private Object queue;

    private Location refLocation;
    private Location lastLocation;
    private boolean reconnected;
    private int count;

    private int imgNum = 1;

    public GpxTracklog(String type, Callback callback, String creator, long time) {
        this.type = type;
        this.callback = callback;
        this.creator = creator;
        this.time = time;
        this.date = dateToFileDate(time);
    }

    public long getTime() {
        return time;
    }

    public void destroy() {
        synchronized (this) {
            go = false;
            notify();
        }
    }

    public void run() {
        api.file.File file = null;
        OutputStream output = null;
        String path = Config.getSafeInstance().getTracklogsDir() + "/trekbuddy-" + date + "-" + type + ".gpx";
        Throwable throwable = null;

        // try to create file - isolated operation
        try {
            file = new api.file.File(Connector.open(path, Connector.READ_WRITE));
            if (!file.exists()) {
                file.create();
            }
        } catch (Throwable t) {
            throwable = t;
        }

        // file created?
        if (throwable == null) {

            // signal recording start
            callback.invoke(new Integer(CODE_RECORDING_START), null);

            KXmlSerializer serializer = null;

            try {
                output = new BufferedOutputStream(file.openOutputStream(), 512);
                serializer = new KXmlSerializer();
                serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
                serializer.setOutput(output, "UTF-8");
                serializer.startDocument("UTF-8", null);
                serializer.setPrefix(null, GPX_1_1_NAMESPACE);
                serializer.setPrefix(EXT_PREFIX, EXT_NAMESPACE);
                serializer.startTag(DEFAULT_NAMESPACE, "gpx");
                serializer.attribute(DEFAULT_NAMESPACE, "version", "1.1");
                serializer.attribute(DEFAULT_NAMESPACE, "creator", creator);
                if (type == LOG_TRK) {
                    serializer.startTag(DEFAULT_NAMESPACE, "trk");
                    serializer.startTag(DEFAULT_NAMESPACE, "trkseg");
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

                    if (type == LOG_WPT) {
                        if (item instanceof Waypoint) {
                            Waypoint w = (Waypoint) item;
                            if (w.getUserObject() != null) {
                                w.setLinkPath(saveImage((byte[]) w.getUserObject()));
                            }
                            serializeWpt(serializer, w);
                            callback.invoke(new Integer(CODE_WAYPOINT_INSERTED), null);
                        }
                    } else { // type == LOG_TRK
                        if (item instanceof Location) {
                            Location l = check((Location) item);
                            if (l != null) {
                                serializeTrkpt(serializer, l);
                                serializer.flush();
                            }
                        } else if (item instanceof Boolean) {
                            serializer.endTag(DEFAULT_NAMESPACE, "trkseg");
                            serializer.startTag(DEFAULT_NAMESPACE, "trkseg");
                        }
                    }
                }

                // signal recording stop
                callback.invoke(new Integer(CODE_RECORDING_STOP), null);

            } catch (Throwable t) {

                // signal error
                callback.invoke(null, t);

            } finally {

                // close XML
                if (serializer != null) {
                    try {
                        if (type == LOG_TRK) {
                            serializer.endTag(DEFAULT_NAMESPACE, "trkseg");
                            serializer.endTag(DEFAULT_NAMESPACE, "trk");
                        }
                        serializer.endTag(null, "gpx");
                        serializer.endDocument();
                        serializer.flush();
                    } catch (IOException e) {
                        // TODO ignore???
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
            callback.invoke("Failed to create GPX file " + path, throwable);
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
        serializer.attribute(DEFAULT_NAMESPACE, "lat", Double.toString(qc.getLat()));
        serializer.attribute(DEFAULT_NAMESPACE, "lon", Double.toString(qc.getLon()));
        if (qc.getAlt() > -1F) {
            serializer.startTag(DEFAULT_NAMESPACE, "ele");
            serializer.text(Float.toString(qc.getAlt()));
            serializer.endTag(DEFAULT_NAMESPACE, "ele");
        }
        serializer.startTag(DEFAULT_NAMESPACE, "time");
        serializer.text(dateToXsdDate(l.getTimestamp()));
        serializer.endTag(DEFAULT_NAMESPACE, "time");
        if (l instanceof Waypoint) {
            Waypoint w = (Waypoint) l;
            if (w.getName() != null && w.getName().length() > 0) {
                serializer.startTag(DEFAULT_NAMESPACE, "name");
                serializer.text(w.getName());
                serializer.endTag(DEFAULT_NAMESPACE, "name");
            }
            if (w.getComment() != null && w.getComment().length() > 0) {
                serializer.startTag(DEFAULT_NAMESPACE, "desc");
                serializer.text(w.getComment());
                serializer.endTag(DEFAULT_NAMESPACE, "desc");
            }
            if (w.getLinkPath() != null && w.getLinkPath().length() > 0) {
                serializer.startTag(DEFAULT_NAMESPACE, "link");
                serializer.attribute(DEFAULT_NAMESPACE, "href", w.getLinkPath());
                serializer.endTag(DEFAULT_NAMESPACE, "link");
            }
        }
        serializer.startTag(DEFAULT_NAMESPACE, "fix");
        switch (l.getFix()) {
            case 0: {
                serializer.text("none");
            } break;
            case 1:
                if (qc.getAlt() > -1F) {
                    serializer.text("3d");
                } else {
                    serializer.text("2d");
                }
                break;
            case 2: {
                serializer.text("dgps");
            } break;
            case 3: {
                serializer.text("pps");
            } break;
            default: {
                serializer.text(Integer.toString(l.getFix()));
            }
        }
        serializer.endTag(DEFAULT_NAMESPACE, "fix");
        if (l.getSat() > -1) {
            serializer.startTag(DEFAULT_NAMESPACE, "sat");
            serializer.text(Integer.toString(l.getSat()));
            serializer.endTag(DEFAULT_NAMESPACE, "sat");
        }
/*
        if (l.getHdop() > -1F) {
            serializer.startTag(DEFAULT_NAMESPACE, "hdop");
            serializer.text(Float.toString(l.getHdop()));
            serializer.endTag(DEFAULT_NAMESPACE, "hdop");
        }
*/
        if (l.getCourse() > -1F || l.getSpeed() > -1F) {
            serializer.startTag(DEFAULT_NAMESPACE, "extensions");
            if (l.getCourse() > -1F) {
                serializer.startTag(EXT_NAMESPACE, "course");
                serializer.text(Float.toString(l.getCourse()));
                serializer.endTag(EXT_NAMESPACE, "course");
            }
            if (l.getSpeed() > -1F) {
                serializer.startTag(EXT_NAMESPACE, "speed");
                serializer.text(Float.toString(l.getSpeed() * 1.852F / 3.6F));
                serializer.endTag(EXT_NAMESPACE, "speed");
            }
            serializer.endTag(DEFAULT_NAMESPACE, "extensions");
        }
    }

    private static void serializeTrkpt(KXmlSerializer serializer, Location l) throws IOException {
        serializer.startTag(DEFAULT_NAMESPACE, "trkpt");
        serializePt(serializer, l);
        serializer.endTag(DEFAULT_NAMESPACE, "trkpt");
    }

    private static void serializeWpt(KXmlSerializer serializer, Waypoint w) throws IOException {
        serializer.startTag(DEFAULT_NAMESPACE, "wpt");
        serializePt(serializer, w);
        serializer.endTag(DEFAULT_NAMESPACE, "wpt");
        serializer.flush();
    }

    private String saveImage(byte[] raw) throws IOException {
        api.file.File file = null;
        OutputStream output = null;

        try {
            // images path
            String relPath = "Images-" + date;

            // check for 'Images' subdir existence
            String imgdir = Config.getSafeInstance().getTracklogsDir() + "/" + relPath + "/";
            file = new api.file.File(Connector.open(imgdir, Connector.READ_WRITE));
            if (!file.exists()) {
                file.mkdir();
            }
            file.close();
            file = null;

            // image filename
            String fileName = "pic-" + imgNum++ + ".jpg";

            // save picture
            file = new api.file.File(Connector.open(imgdir + fileName, Connector.READ_WRITE));
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

    public void insert(Location location) {
        synchronized (this) {
            queue = location;
            notify();
        }
    }

    public void insert(Waypoint waypoint) {
        synchronized (this) {
            queue = waypoint;
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

    public void update(Location location) {
        synchronized (this) {
            queue = location;
            notify();
        }
    }

    public Location check(Location location) {

        /*
         * GPS dancing detection.
         * Rule #1: no course change over 90 degrees within 1 sec
         */

        boolean dance = false;

        if (location.getFix() > 0) {
            if (lastLocation == null) {
                lastLocation = location;
            } else {
                if ((location.getTimestamp() - lastLocation.getTimestamp()) < 1250) {
                    int courseDiff = Math.abs((int) location.getCourse() - (int) lastLocation.getCourse());
                    if (courseDiff >= 180) {
                        courseDiff = 360 - courseDiff;
                    }
                    if (courseDiff >= 90) {
                        dance = true;
                    }
                }

                // this one becomes last one
                lastLocation = null;
                lastLocation = location;
            }
        }

        if (refLocation == null) {
            refLocation = location;
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
            refLocation = null;
            refLocation = location;

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
