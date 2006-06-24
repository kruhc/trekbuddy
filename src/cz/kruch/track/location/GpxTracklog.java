// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import api.location.QualifiedCoordinates;
import api.location.Location;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Date;
import java.util.Calendar;

import org.kxml2.io.KXmlSerializer;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.event.Callback;

public class GpxTracklog extends Thread {
    private static final String GPX_1_1_NAMESPACE = "http://www.topografix.com/GPX/1/1";
    private static final String DEFAULT_NAMESPACE = null;

    private static final int MIN_DT = 60000; // 1 min
    private static final double MIN_DL = 0.00015D; // cca 10 m

    private Callback callback;
    private Location queue;
    private boolean go = true;

    private QualifiedCoordinates refCoordinates = new QualifiedCoordinates(0D, 0D);
    private long refTimestamp = 0;

    public GpxTracklog(Callback callback) {
        this.callback = callback;
    }

    public void destroy() {
        go = false;
        interrupt();
    }

    public void run() {
        FileConnection fc = null;
        OutputStreamWriter writer = null;
        KXmlSerializer serializer = null;

        String path = Config.getSafeInstance().getTracklogsDir() + "/gpx-" + Long.toString(System.currentTimeMillis()) + ".xml";

        try {
            fc = (FileConnection) Connector.open(path, Connector.WRITE);
            fc.create();
            writer = new OutputStreamWriter(Connector.openOutputStream(path));
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
            serializer.setOutput(writer);
            serializer.startDocument("UTF-8", null);
            serializer.setPrefix(null, GPX_1_1_NAMESPACE);
            serializer.startTag(DEFAULT_NAMESPACE, "gpx");
            serializer.attribute(DEFAULT_NAMESPACE, "version", "1.1");

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
                serializer.startTag(DEFAULT_NAMESPACE, "wpt");
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
                if (l.getFix() == 0 || l.getFix() > 1) {
                    serializer.startTag(DEFAULT_NAMESPACE, "fix");
                    serializer.text(Integer.toString(l.getFix()));
                    serializer.endTag(DEFAULT_NAMESPACE, "fix");
                }
                if (l.getSat() > -1) {
                    serializer.startTag(DEFAULT_NAMESPACE, "sat");
                    serializer.text(Integer.toString(l.getSat()));
                    serializer.endTag(DEFAULT_NAMESPACE, "sat");
                }
                serializer.endTag(DEFAULT_NAMESPACE, "wpt");
            }
        } catch (Throwable t) {
            callback.invoke(null, t);
        } finally {
            if (serializer != null) {
                try {
                    serializer.endTag(null, "gpx");
                    serializer.endDocument();
                    serializer.flush();
                } catch (IOException e) {
                }
            }
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public void update(Location location) {
        QualifiedCoordinates qc = location.getQualifiedCoordinates();
        long timestamp = location.getTimestamp();

        boolean bTimeDiff = (timestamp - refTimestamp) > MIN_DT;
        boolean bPosDiff = location.getFix() > 0 ? ((Math.abs(qc.getLat() - refCoordinates.getLat()) > MIN_DL) || (Math.abs(qc.getLon() - refCoordinates.getLon()) > MIN_DL)) : false;

        if (bTimeDiff || bPosDiff) {

            // use location as new reference
            refCoordinates = qc;
            refTimestamp = timestamp;

            // put location into 'queue'
            synchronized (this) {
                queue = location;
                notify();
            }
        }
    }

    private static final String dateToXsdDate(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date(timestamp));
        StringBuffer sb = new StringBuffer();
        sb.append(cal.get(Calendar.YEAR)).append('-');
        sb.append(toTwoDigitStr(cal.get(Calendar.MONTH))).append('-');
        sb.append(toTwoDigitStr(cal.get(Calendar.DAY_OF_MONTH))).append('T');
        sb.append(toTwoDigitStr(cal.get(Calendar.HOUR_OF_DAY))).append(':');
        sb.append(toTwoDigitStr(cal.get(Calendar.MINUTE))).append(':');
        sb.append(toTwoDigitStr(cal.get(Calendar.SECOND)));
        sb.append('Z'/*cal.getTimeZone().getID()*/);

        return sb.toString();
    }

    private static final String toTwoDigitStr(int i) {
        if (i >= 10)
            return String.valueOf(i);
        else
            return "0" + String.valueOf(i);
    }
}
