// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.location;

import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone;

public class Location {
    private static final Calendar CALENDAR = Calendar.getInstance(TimeZone.getDefault());
    private static final Date DATE = new Date();

    private QualifiedCoordinates coordinates;
    private long timestamp;
    private short fix;
    private short sat;
    private float hdop;
    private float speed = -1F;
    private float course = -1F;

    public Location(QualifiedCoordinates coordinates, long timestamp, int fix) {
        this(coordinates, timestamp, fix, -1, -1F);
    }

    public Location(QualifiedCoordinates coordinates, long timestamp, int fix,
                    int sat, float hdop) {
        this.coordinates = coordinates;
        this.timestamp = timestamp;
        this.fix = (short) fix;
        this.sat = (short) sat;
        this.hdop = hdop;
    }

    public Location(Location location) {
        this.coordinates = location.coordinates;
        this.timestamp = location.timestamp;
        this.fix = location.fix;
        this.sat = location.sat;
        this.hdop = location.hdop;
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

    public void setHdop(float hdop) {
        this.hdop = hdop;
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

    public StringBuffer toStringBuffer(StringBuffer sb) {
/*
        if (timestamp > 0) {
*/
            DATE.setTime(timestamp);
            CALENDAR.setTime(DATE);
            int hour = CALENDAR.get(Calendar.HOUR_OF_DAY);
            if (hour < 10) sb.append('0');
            sb.append(hour).append(':');
            int min = CALENDAR.get(Calendar.MINUTE);
            if (min < 10) sb.append('0');
            sb.append(min).append(' ');
/*
        }
*/
        if (coordinates.getAlt() > -1F) {
            sb.append(coordinates.getAlt()).append(" m ");
        }
        if (speed > -1F) {
            sb.append((int) (speed * 1.852F)).append(" km/h ");
        }
/* course arrow is good enough
        if (course > -1F) {
            sb.append((new Float(course)).intValue()).append(QualifiedCoordinates.SIGN).append(' ');
        }
*/
/* rendered by OSD directly
        if (sat > -1) {
            sb.append(sat).append('*');
        }
*/

        return sb;
    }

    // debug
    public String toString() {
        return (new Date(timestamp)).toString() + ": " + coordinates.toString() + ";" + fix + ";" + sat;
    }
    // ~debug
}
