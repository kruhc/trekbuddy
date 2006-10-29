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
    private static final double MIN_DL_WALK = 0.00054D;     // cca 50 m
    private static final double MIN_DL_BIKE = 0.00270D;     // cca 250 m
    private static final double MIN_DL_CAR  = 0.00540D;     // cca 500 m
    private static final float  MIN_SPEED_WALK = 2F;        // 2 * 1.852 km/h
    private static final float  MIN_SPEED_BIKE = 10F;       // 10 * 1.852 km/h
    private static final float  MIN_SPEED_CAR  = 20F;       // 20 * 1.852 km/h
    private static final float  MIN_COURSE_DIVERSION      = 15F; // 15 degrees
    private static final float  MIN_COURSE_DIVERSION_FAST = 10F; // 10 degrees

    private static final double ONE_KNOT_DISTANCE_IN_DEGREES = 0.016637157;

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

    private volatile boolean go = true;
    private Object queue;

    private Location refLocation;
    private Location lastLocation;
    private boolean reconnected = false;

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
        go = false;
        synchronized (this) {
            notify();
        }
    }

    public void run() {
        api.file.File fc = null;
        OutputStream output = null;
        KXmlSerializer serializer = null;
        String path = Config.getSafeInstance().getTracklogsDir() + "/trekbuddy-" + date + "-" + type + ".gpx";

        try {
            fc = new api.file.File(Connector.open(path, Connector.WRITE));
            fc.create();
            output = new BufferedOutputStream(fc.openOutputStream(), 512);
        } catch (Throwable t) {
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException e) {
                }
            }

            // signal failure
            callback.invoke("Failed to create GPX file " + path, t);

            return; // no tracklog
        }

        // signal recording start
        callback.invoke(new Integer(CODE_RECORDING_START), null);

        try {
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

            for (;;) {
                Object item = null;
                synchronized (this) {
                    while (go && queue == null) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
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
                        Location l = (Location) item;
                        serializeTrkpt(serializer, l);
                        serializer.flush();
                    } else if (item instanceof Boolean) {
                        serializer.endTag(DEFAULT_NAMESPACE, "trkseg");
                        serializer.startTag(DEFAULT_NAMESPACE, "trkseg");
                    }
                }
            }
        } catch (Throwable t) {
            callback.invoke(null, t);
        } finally {
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
                }
            }
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                }
            }
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException e) {
                }
            }

            // signal recording stop
            callback.invoke(new Integer(CODE_RECORDING_STOP), null);
        }
    }

    private void serializePt(KXmlSerializer serializer, Location l) throws IOException {
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
                serializer.startTag(DEFAULT_NAMESPACE, "cmt");
                serializer.text(w.getComment());
                serializer.endTag(DEFAULT_NAMESPACE, "cmt");
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
        if (l.getHdop() > -1F) {
            serializer.startTag(DEFAULT_NAMESPACE, "hdop");
            serializer.text(Float.toString(l.getHdop()));
            serializer.endTag(DEFAULT_NAMESPACE, "hdop");
        }
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

    private void serializeTrkpt(KXmlSerializer serializer, Location l) throws IOException {
        serializer.startTag(DEFAULT_NAMESPACE, "trkpt");
        serializePt(serializer, l);
        serializer.endTag(DEFAULT_NAMESPACE, "trkpt");
    }

    private void serializeWpt(KXmlSerializer serializer, Waypoint w) throws IOException {
        serializer.startTag(DEFAULT_NAMESPACE, "wpt");
        serializePt(serializer, w);
        serializer.endTag(DEFAULT_NAMESPACE, "wpt");
        serializer.flush();
    }

    private String saveImage(byte[] raw) throws IOException {
        api.file.File fc = null;
        OutputStream output = null;
        String relPath = "Images-" + date;
        String fileName = "pic-" + imgNum++ + ".jpg";

        try {
            // check for 'Images' subdir existence
            String imgdir = Config.getSafeInstance().getTracklogsDir() + "/" + relPath + "/";
            fc = new api.file.File(Connector.open(imgdir, Connector.READ_WRITE));
            if (!fc.exists()) {
                fc.mkdir();
            }
            fc.close();
            fc = null;

            // save picture
            fc = new api.file.File(Connector.open(imgdir + fileName, Connector.WRITE));
            fc.create();
            output = new BufferedOutputStream(fc.openOutputStream(), 4096);
            output.write(raw);
            output.flush();

            // return relative path
            return relPath + "/" + fileName;

        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                }
            }
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException e) {
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
            queue = b;
            notify();
        }
    }

    public void setReconnected() {
        reconnected = true;
    }

    public void update(Location location) {
        QualifiedCoordinates qc = location.getQualifiedCoordinates();
        long timestamp = location.getTimestamp();

        /*
         * GPS dancing detection
         */
        boolean dance = false;
        if (location.getFix() > 0) {
            if (lastLocation == null) {
                lastLocation = location;
            } else {
                // check speed to detect GPS dancing
                float speed = location.getSpeed();
                if (speed > 0D) {
                    QualifiedCoordinates lastCoordinates = lastLocation.getQualifiedCoordinates();
                    long lastTimestamp = lastLocation.getTimestamp();
                    double latDiff = qc.getLat() - lastCoordinates.getLat();
                    double lonDiff = qc.getLon() - lastCoordinates.getLon();
                    double r = Math.sqrt(latDiff * latDiff + lonDiff * lonDiff);
                    double xspeed = (r / ONE_KNOT_DISTANCE_IN_DEGREES) / ((double) (timestamp - lastTimestamp) / 3600000);

                    double speedDiff = Math.abs(xspeed - speed) / speed;
                    if (speedDiff > 0.50D) {
                        dance = true;
                    }
                }
                // this one becomes last one
                lastLocation = location;
            }
        }

        if (refLocation == null) {
            refLocation = location;
        }

        boolean bLog = false;
        boolean bTimeDiff = (timestamp - refLocation.getTimestamp()) > MIN_DT;

        if (reconnected) {
            reconnected = false;
            bLog = true;
        }

        if (!bTimeDiff) {
            if (location.getFix() > 0) {
                if (refLocation.getFix() > 0) {
                    QualifiedCoordinates refCoordinates = refLocation.getQualifiedCoordinates();
                    // compute dist from reference location
                    double latDiff = qc.getLat() - refCoordinates.getLat();
                    double lonDiff = qc.getLon() - refCoordinates.getLon();
                    double r = Math.sqrt(latDiff * latDiff + lonDiff * lonDiff);
                    // get speed
                    float speed = location.getSpeed();

                    // depending on speed, find out whether log or not
                    if (speed < MIN_SPEED_WALK) { /* no move or hiking speed */
                        boolean bPosDiff = r > MIN_DL_WALK;
                        bLog = bPosDiff;
                    } else if (speed < MIN_SPEED_BIKE) { /* bike speed */
                        boolean bPosDiff = r > MIN_DL_BIKE;
                        boolean bCourseDiff = location.getCourse() > -1F && refLocation.getCourse() > -1F ? Math.abs(location.getCourse() - refLocation.getCourse()) > MIN_COURSE_DIVERSION : false;
                        bLog = bPosDiff || bCourseDiff;
                    } else if (speed < MIN_SPEED_CAR) {
                        boolean bPosDiff = r > MIN_DL_CAR;
                        boolean bCourseDiff = location.getCourse() > -1F && refLocation.getCourse() > -1F ? Math.abs(location.getCourse() - refLocation.getCourse()) > MIN_COURSE_DIVERSION_FAST : false;
                        bLog = bPosDiff || bCourseDiff;
                    } else {
                        boolean bPosDiff = r > 2 * MIN_DL_CAR;
                        boolean bCourseDiff = location.getCourse() > -1F && refLocation.getCourse() > -1F ? Math.abs(location.getCourse() - refLocation.getCourse()) > MIN_COURSE_DIVERSION_FAST : false;
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
                return;
            }

            // use location as new reference
            refLocation = location;

            // put location into 'queue'
            synchronized (this) {
                queue = location;
                notify();
            }
        }
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
        CALENDAR.setTimeZone(TimeZone.getDefault());
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
