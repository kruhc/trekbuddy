// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import api.location.QualifiedCoordinates;
import api.location.Location;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.OutputStream;
import java.util.Date;
import java.util.Calendar;

import org.kxml2.io.KXmlSerializer;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.event.Callback;
import cz.kruch.j2se.io.BufferedOutputStream;

public final class GpxTracklog extends Thread {
    private static final String GPX_1_1_NAMESPACE   = "http://www.topografix.com/GPX/1/1";
    private static final String DEFAULT_NAMESPACE   = null;

    private static final int    MIN_DT = 5 * 60000;         // 5 min
    private static final double MIN_DL_WALK = 0.00027D;     // cca 25 m
    private static final double MIN_DL_BIKE = 0.00110D;     // cca 100 m
    private static final double MIN_DL_CAR  = 0.00270D;     // cca 250 m
    private static final float  MIN_SPEED_SLOW = 3F;        // 3 * 1.852 km/h
    private static final float  MIN_SPEED_FAST = 15F;       // 15 * 1.852 km/h
    private static final float  MIN_COURSE_DIVERSION = 15F; // 15 degrees

    private static final double ONE_KNOT_DISTANCE_IN_DEGREES = MIN_DL_WALK * 1852D / 25D;

    private static final Calendar CALENDAR = Calendar.getInstance();

    private Callback callback;
    private String creator;

    private Location queue;
    private boolean go = true;

    private Location refLocation;
    private Location lastLocation;

    public GpxTracklog(Callback callback, String creator) {
        this.callback = callback;
        this.creator = creator;
    }

    public void destroy() {
        go = false;
        interrupt();
    }

    public void run() {
        FileConnection fc = null;
        OutputStream output = null;
        KXmlSerializer serializer = null;

        String path = Config.getSafeInstance().getTracklogsDir() + "/gpx-" + Long.toString(System.currentTimeMillis()) + ".xml";

        try {
            fc = (FileConnection) Connector.open(path, Connector.WRITE);
            fc.create();
            output = new BufferedOutputStream(fc.openOutputStream(), 512);
        } catch (Throwable t) {
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException e) {
                }
            }

            callback.invoke("Failed to create GPX file", t);

            return; // END
        }

        try {
            serializer = new KXmlSerializer();
            serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            serializer.setOutput(output, "UTF-8");
            serializer.startDocument("UTF-8", null);
            serializer.setPrefix(null, GPX_1_1_NAMESPACE);
            serializer.startTag(DEFAULT_NAMESPACE, "gpx");
            serializer.attribute(DEFAULT_NAMESPACE, "version", "1.1");
            serializer.attribute(DEFAULT_NAMESPACE, "creator", creator);
            serializer.attribute(DEFAULT_NAMESPACE, "time", dateToXsdDate(System.currentTimeMillis()));
            serializer.startTag(DEFAULT_NAMESPACE, "trk");
            serializer.startTag(DEFAULT_NAMESPACE, "trkseg");

            for (; go ;) {
                Location l = null;
                synchronized (this) {
                    while (go && queue == null) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                        }
                    }
                    l = queue;
                    queue = null;
                }

                if (!go) break;

                QualifiedCoordinates qc = l.getQualifiedCoordinates();
                serializer.startTag(DEFAULT_NAMESPACE, "trkpt");
                serializer.attribute(DEFAULT_NAMESPACE, "lat", Double.toString(qc.getLat()));
                serializer.attribute(DEFAULT_NAMESPACE, "lon", Double.toString(qc.getLon()));
                serializer.startTag(DEFAULT_NAMESPACE, "time");
                serializer.text(dateToXsdDate(l.getTimestamp()));
                serializer.endTag(DEFAULT_NAMESPACE, "time");
                if (qc.getAlt() > -1F) {
                    serializer.startTag(DEFAULT_NAMESPACE, "ele");
                    serializer.text(Float.toString(qc.getAlt()));
                    serializer.endTag(DEFAULT_NAMESPACE, "ele");
                }
                switch (l.getFix()) {
                    case 0: {
                        serializer.startTag(DEFAULT_NAMESPACE, "fix");
                        serializer.text("none");
                        serializer.endTag(DEFAULT_NAMESPACE, "fix");
                    } break;
                    case 1:
                        break;
                    case 2: {
                        serializer.startTag(DEFAULT_NAMESPACE, "fix");
                        serializer.text("dgps");
                        serializer.endTag(DEFAULT_NAMESPACE, "fix");
                    } break;
                    case 3: {
                        serializer.startTag(DEFAULT_NAMESPACE, "fix");
                        serializer.text("pps");
                        serializer.endTag(DEFAULT_NAMESPACE, "fix");
                    } break;
                    default: {
                        serializer.startTag(DEFAULT_NAMESPACE, "fix");
                        serializer.text(Integer.toString(l.getFix()));
                        serializer.endTag(DEFAULT_NAMESPACE, "fix");
                    }
                }
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
                if (l.getCourse() > -1F) {
                    serializer.startTag(DEFAULT_NAMESPACE, "course");
                    serializer.text(Float.toString(l.getCourse()));
                    serializer.endTag(DEFAULT_NAMESPACE, "course");
                }
                if (l.getSpeed() > -1F) {
                    serializer.startTag(DEFAULT_NAMESPACE, "speed");
                    serializer.text(Float.toString(l.getSpeed() * 1.852F / 3.6F));
                    serializer.endTag(DEFAULT_NAMESPACE, "speed");
                }
                serializer.endTag(DEFAULT_NAMESPACE, "trkpt");
                serializer.flush();
            }
        } catch (Throwable t) {
            callback.invoke(null, t);
        } finally {
            if (serializer != null) {
                try {
                    serializer.endTag(DEFAULT_NAMESPACE, "trkseg");
                    serializer.endTag(DEFAULT_NAMESPACE, "trk");
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
        }
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
                    if (speedDiff > 0.666D) {
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

        if (bTimeDiff == false) {
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
                    if (speed < MIN_SPEED_SLOW) { /* no move or hiking speed */
                        boolean bPosDiff = r > MIN_DL_WALK;
                        bLog = bPosDiff;
                    } else if (speed < MIN_SPEED_FAST) { /* bike speed */
                        boolean bPosDiff = r > MIN_DL_BIKE;
                        boolean bCourseDiff = location.getCourse() > -1F && refLocation.getCourse() > -1F ? Math.abs(location.getCourse() - refLocation.getCourse()) > MIN_COURSE_DIVERSION : false;
                        bLog = bPosDiff || bCourseDiff;
                    } else { /* car speed */
                        boolean bPosDiff = r > MIN_DL_CAR;
                        boolean bCourseDiff = location.getCourse() > -1F && refLocation.getCourse() > -1F ? Math.abs(location.getCourse() - refLocation.getCourse()) > MIN_COURSE_DIVERSION : false;
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

    private static final String dateToXsdDate(long timestamp) {
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

    private static final StringBuffer appendTwoDigitStr(StringBuffer sb, int i) {
        if (i < 10) {
            sb.append('0');
        }
        sb.append(i);

        return sb;
    }
}
