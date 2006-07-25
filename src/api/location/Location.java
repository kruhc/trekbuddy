// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.location;

import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone;

public final class Location {
    private static final Calendar CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

    private QualifiedCoordinates coordinates;
    private long timestamp;
    private int fix;
    private int sat;
    private float hdop = -1F;
    private float speed = -1F;
    private float course = -1F;

    public Location(QualifiedCoordinates coordinates, long timestamp, int fix) {
        this(coordinates, timestamp, fix, -1, -1F);
    }

    public Location(QualifiedCoordinates coordinates, long timestamp, int fix, int sat, float hdop) {
        this.coordinates = coordinates;
        this.timestamp = timestamp;
        this.fix = fix;
        this.sat = sat;
        this.hdop = hdop;
    }

    public QualifiedCoordinates getQualifiedCoordinates() {
        return coordinates;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getFix() {
        return fix;
    }

    public int getSat() {
        return sat;
    }

    public float getHdop() {
        return hdop;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public float getCourse() {
        return course;
    }

    public void setCourse(float course) {
        this.course = course;
    }

    public String toInfo() {
        return coordinates.toString();
    }

    public String toExtendedInfo(int timezone) {
        StringBuffer sb = new StringBuffer(24);
        if (timestamp > 0) {
            CALENDAR.setTime(new Date(timestamp + timezone * 3600000));
            int hour = CALENDAR.get(Calendar.HOUR_OF_DAY);
            if (hour < 10) sb.append('0');
            sb.append(hour).append(':');
            int min = CALENDAR.get(Calendar.MINUTE);
            if (min < 10) sb.append('0');
            sb.append(min).append(' ');
        }
        if (coordinates.getAlt() > -1F) {
            sb.append(coordinates.getAlt()).append(" m ");
        }
        if (speed > -1F) {
            sb.append((new Float(speed * 1.852F)).intValue()).append(" km/h ");
        }
        if (course > -1F) {
            sb.append((new Float(course)).intValue()).append(QualifiedCoordinates.SIGN).append(' ');
        }
        if (sat > -1) {
            sb.append(sat).append('*');
        }

        if (sb.length() > 0) {
            return sb.toString();
        }

        return null;
    }

    // debug
    public String toString() {
        return (new Date(timestamp)).toString() + ": " + coordinates.toString() + ";" + fix + ";" + sat;
    }
    // ~debug
}
