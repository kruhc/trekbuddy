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

import org.kxml2.io.KXmlSerializer;

import javax.microedition.io.Connector;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.event.Callback;
import cz.kruch.track.ui.NavigationScreens;
import cz.kruch.track.Resources;

/**
 * GPX tracklog.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class GpxTracklog extends Thread {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Gpx");
//#endif

    private static final String GPX_1_1_NAMESPACE   = "http://www.topografix.com/GPX/1/1";
    private static final String DEFAULT_NAMESPACE   = null;
    private static final String EXT_NAMESPACE       = "urn:net:trekbuddy:1.0:nmea:rmc";
    private static final String EXT_PREFIX          = "rmc";
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
    private static final String ELEMENT_DESC        = "desc";
    private static final String ELEMENT_LINK        = "link";
    private static final String ELEMENT_EXTENSIONS  = "extensions";
    private static final String ELEMENT_COURSE      = "course";
    private static final String ELEMENT_SPEED       = "speed";
    private static final String ELEMENT_CELLID      = "cellid";
    private static final String ELEMENT_LAC         = "lac";

    private static final String ATTRIBUTE_UTF_8     = "UTF-8";
    private static final String ATTRIBUTE_VERSION   = "version";
    private static final String ATTRIBUTE_CREATOR   = "creator";
    private static final String ATTRIBUTE_HREF      = "href";
    private static final String ATTRIBUTE_LAT       = "lat";
    private static final String ATTRIBUTE_LON       = "lon";

    private static final String FIX_NONE            = "none";
    private static final String FIX_3D              = "3d";
    private static final String FIX_2D              = "2d";
    private static final String FIX_DGPS            = "dgps";
    private static final String FIX_PPS             = "pps";

    private final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
    private final Date date = new Date();

    private final char[] sbChars = new char[32];
    
    private Callback callback;
    private String creator;
    private String fileDate;
    private String fileName;
    private int type;
    private long time;

    private boolean go = true;
    private Object queue;
    private TimerTask flusher;

    private Location refLocation;
    private float refCourse, courseDeviation;
    private int imgNum = 1;
    private boolean force;

    public GpxTracklog(int type, Callback callback, String creator, long time) {
        this.type = type;
        this.callback = callback;
        this.creator = creator;
        this.time = time;
        this.fileDate = dateToFileDate(time);
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

    public void shutdown() {
        synchronized (this) {
            go = false;
            notify();
        }
    }

    public void run() {

        // local vars
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

            KXmlSerializer serializer = null;

            try {
                /* buffering is in serializer by default in OutputStreamWriter */
                output = file.openOutputStream();

                // init serializer
                serializer = new KXmlSerializer();
                serializer.setFeature(KXmlSerializer.FEATURE_INDENT_OUTPUT, true);
                serializer.setOutput(output, ATTRIBUTE_UTF_8);
                serializer.startDocument(ATTRIBUTE_UTF_8, null);
                serializer.setPrefix(null, GPX_1_1_NAMESPACE);
                serializer.setPrefix(EXT_PREFIX, EXT_NAMESPACE);
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

                // signal recording start
                callback.invoke(new Integer(CODE_RECORDING_START), null, this);

                // start periodic flush
                cz.kruch.track.ui.Desktop.timer.schedule(flusher = new TimerTask() {
                    public void run() {
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("trigger flush at " + new Date());
//#endif
                        insert(Boolean.FALSE);
                    }
                }, 60000L, 60000L);

                // pop items until end
                for (; go ;) {
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

                    if (item instanceof Location) {
                        final Location l = (Location) item;
                        if (check(l) != null) {
                            serializeTrkpt(serializer, l);
                        }
                        Location.releaseInstance(l);
                    } else if (item instanceof Boolean) {
                        if (Boolean.TRUE.equals(item)) {
                            serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_TRKSEG);
                            serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_TRKSEG);
                        }
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("flush at " + new Date());
//#endif
                        serializer.flush();
                    } else if (item instanceof Waypoint) {
                        final Waypoint w = (Waypoint) item;
                        if (w.getUserObject() != null) {
                            w.setLinkPath(saveImage((byte[]) w.getUserObject()));
                            w.setUserObject(null); // gc hint
                        }
                        serializeWpt(serializer, w);
                        serializer.flush();
                        // TODO ugly
                        callback.invoke(new Integer(CODE_WAYPOINT_INSERTED), null, this);
                    }
                }

                // signal recording stop
                callback.invoke(new Integer(CODE_RECORDING_STOP), null, this);

            } catch (Throwable t) {

                // signal fatal error
                callback.invoke(null, t, this);

            } finally {

                // stop periodic flush
                if (flusher != null) {
                    flusher.cancel();
                    flusher = null; // gc hint
                }

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
                        serializer.endTag(null, ELEMENT_GPX);
                        serializer.endDocument();
                        serializer.flush();
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("~done");
//#endif
                    } catch (IOException e) {
                        // ignore
                    }
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
                }
            }
        } else {
            // signal failure
            callback.invoke(Resources.getString(Resources.DESKTOP_MSG_START_TRACKLOG_FAILED) + ": " + path, throwable, this);
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

    private void serializePt(final KXmlSerializer serializer,
                             final QualifiedCoordinates qc,
                             final Object pt) throws IOException {
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
        if (pt instanceof Waypoint) {
            final Waypoint wpt = (Waypoint) pt;
            serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_TIME);
            i = dateToXsdDate(wpt.getTimestamp());
            serializer.text(sbChars, 0, i);
            serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_TIME);
            if (wpt.getName() != null && wpt.getName().length() > 0) {
                serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_NAME);
                serializer.text(wpt.getName());
                serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_NAME);
            }
            if (wpt.getComment() != null && wpt.getComment().length() > 0) {
                serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_DESC);
                serializer.text(wpt.getComment());
                serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_DESC);
            }
            if (wpt.getLinkPath() != null && wpt.getLinkPath().length() > 0) {
                serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_LINK);
                serializer.attribute(DEFAULT_NAMESPACE, ATTRIBUTE_HREF, wpt.getLinkPath());
                serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_LINK);
            }
        } else if (pt instanceof Location) {
            final Location l = (Location) pt;
            serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_TIME);
            i = dateToXsdDate(l.getTimestamp());
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
            if (l.getHdop() > -1F) {
                serializer.startTag(DEFAULT_NAMESPACE, "hdop");
                serializer.text(Float.toString(l.getHdop()));
                serializer.endTag(DEFAULT_NAMESPACE, "hdop");
            }
*/
            /*
             * extensions - nmea:rmc and gsm
             */
            final float course = l.getCourse();
            final float speed = l.getSpeed();
            String cellid = null;
            String lac = null;
            if (Config.gpxGsmInfo) {
                cellid = System.getProperty("com.sonyericsson.net.cellid");
                lac = System.getProperty("com.sonyericsson.net.lac");
            }
            if (!Float.isNaN(course) || !Float.isNaN(speed) || cellid != null || lac != null) {
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
                if (cellid != null) {
                    serializer.startTag(GSM_NAMESPACE, ELEMENT_CELLID);
                    serializer.text(cellid);
                    serializer.endTag(GSM_NAMESPACE, ELEMENT_CELLID);
                }
                if (lac != null) {
                    serializer.startTag(GSM_NAMESPACE, ELEMENT_LAC);
                    serializer.text(lac);
                    serializer.endTag(GSM_NAMESPACE, ELEMENT_LAC);
                }
                serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_EXTENSIONS);
            }
        }
    }

    private void serializeTrkpt(final KXmlSerializer serializer,
                                final Location l) throws IOException {
        serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_TRKPT);
        serializePt(serializer, l.getQualifiedCoordinates(), l);
        serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_TRKPT);
    }

    private void serializeWpt(final KXmlSerializer serializer,
                              final Waypoint wpt) throws IOException {
        serializer.startTag(DEFAULT_NAMESPACE, ELEMENT_WPT);
        serializePt(serializer, wpt.getQualifiedCoordinates(), wpt);
        serializer.endTag(DEFAULT_NAMESPACE, ELEMENT_WPT);
    }

    private String saveImage(final byte[] raw) throws IOException {
        File file = null;
        OutputStream output = null;

        try {
            // images path
            String relPath = "images-" + fileDate;

            // check for 'Images' subdir existence
            String imgdir = Config.getFolderWaypoints() + relPath + "/";
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

    public void insert(Waypoint waypoint) {
        synchronized (this) {
            freeLocationInQueue();
            queue = waypoint;
            notify();
        }
    }

    public void insert(Location location) {
        synchronized (this) {
            freeLocationInQueue();
            force = true;
            queue = location.clone();
            notify();
        }
    }

    public void insert(Boolean b) {
        synchronized (this) {
            freeLocationInQueue();
            force = b.booleanValue();
            queue = b;
            notify();
        }
    }

    public void locationUpdated(Location location) {
        synchronized (this) {
            freeLocationInQueue();
            queue = location.clone();
            notify();
        }
    }

    /** must be called from synchronized method */
    private void freeLocationInQueue() {
        if (queue instanceof Location) { // processing thread did not make it - forget it
            Location.releaseInstance((Location) queue);
            queue = null;
        }
    }

    public Location check(Location location) {

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
                    final float speed = location.getSpeed();
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

        StringBuffer sb = new StringBuffer(32);
        final Calendar calendar = this.calendar;

        NavigationScreens.append(sb, calendar.get(Calendar.YEAR)).append('-');
        appendTwoDigitStr(sb, calendar.get(Calendar.MONTH) + 1).append('-');
        appendTwoDigitStr(sb, calendar.get(Calendar.DAY_OF_MONTH)).append('T');
        appendTwoDigitStr(sb, calendar.get(Calendar.HOUR_OF_DAY)).append(':');
        appendTwoDigitStr(sb, calendar.get(Calendar.MINUTE)).append(':');
        appendTwoDigitStr(sb, calendar.get(Calendar.SECOND));
        sb.append('Z'/*CALENDAR.getTimeZone().getID()*/);

        final int result = sb.length();
        sb.getChars(0, result, sbChars, 0);

        return result;
    }

    private int doubleToChars(final double value, final int precision) {
        StringBuffer sb = new StringBuffer(32);
        NavigationScreens.append(sb, value, precision);

        final int result = sb.length();
        sb.getChars(0, result, sbChars, 0);

        return result;
    }

    public static String dateToFileDate(final long time) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        calendar.setTime(new Date(time));
        StringBuffer sb = new StringBuffer(32);
        NavigationScreens.append(sb, calendar.get(Calendar.YEAR)).append('-');
        appendTwoDigitStr(sb, calendar.get(Calendar.MONTH) + 1).append('-');
        appendTwoDigitStr(sb, calendar.get(Calendar.DAY_OF_MONTH)).append('-');
        appendTwoDigitStr(sb, calendar.get(Calendar.HOUR_OF_DAY)).append('-');
        appendTwoDigitStr(sb, calendar.get(Calendar.MINUTE)).append('-');
        appendTwoDigitStr(sb, calendar.get(Calendar.SECOND));

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
