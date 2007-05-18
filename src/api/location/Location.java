// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.location;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.ui.NavigationScreens;

import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone;

public class Location {
    private static final String STR_KN = " kn ";
    private static final String STR_KMH = " km/h ";
    private static final String STR_M = " m";
    
    private static final Calendar CALENDAR = Calendar.getInstance(TimeZone.getDefault());
    private static final Date DATE = new Date();

    private QualifiedCoordinates coordinates;
    private long timestamp;
    private short fix;
    private short sat;
    private float accuracy;
    private float speed = -1F;
    private float course = -1F;

    /*
     * POOL
     */

    private static final Location[] pool = new Location[8];
    private static int countFree;

    public static Location newInstance(QualifiedCoordinates coordinates, long timestamp, int fix) {
        return newInstance(coordinates, timestamp, fix, -1, -1F);
    }

    public synchronized static Location newInstance(QualifiedCoordinates coordinates, long timestamp, int fix,
                                                    int sat, float accuracy) {
        Location result;

        if (countFree == 0) {
            result = new Location(coordinates, timestamp, fix, sat, accuracy);
        } else {
            result = pool[--countFree];
            result.coordinates = coordinates;
            result.timestamp = timestamp;
            result.fix = (short) fix;
            result.sat = (short) sat;
            result.accuracy = accuracy;
        }

        return result;
    }

    public synchronized static void releaseInstance(Location location) {
        if (countFree < pool.length && location != null) {
            pool[countFree++] = location;
        }
    }

    /*
     * ~POOL
     */

    public Location clone() {
        return newInstance(coordinates.clone(), timestamp, fix, sat, accuracy);
    }

    private Location(QualifiedCoordinates coordinates, long timestamp, int fix,
                     int sat, float accuracy) {
        this.coordinates = coordinates;
        this.timestamp = timestamp;
        this.fix = (short) fix;
        this.sat = (short) sat;
        this.accuracy = accuracy;
    }

    protected Location(QualifiedCoordinates coordinates, long timestamp, int fix) {
        this(coordinates, timestamp, fix, -1, -1F);
    }

    protected Location(Location location) {
        this.coordinates = location.coordinates;
        this.timestamp = location.timestamp;
        this.fix = location.fix;
        this.sat = location.sat;
        this.accuracy = location.accuracy;
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

    public float getAccuracy() {
        return accuracy;
    }

    public float getSpeed() {
        return speed;
    }

    public void setAccuracy(float accuracy) {
        this.accuracy = accuracy;
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
        DATE.setTime(timestamp);
        CALENDAR.setTime(DATE);
        int hour = CALENDAR.get(Calendar.HOUR_OF_DAY);
        int min = CALENDAR.get(Calendar.MINUTE);
        if (hour < 10)
            sb.append('0');
        sb.append(hour).append(':');
        if (min < 10)
            sb.append('0');
        sb.append(min).append(' ');

        if (Config.nauticalView) {
            if (speed > -1F) {
                NavigationScreens.append(sb, speed * 3.6F / 1.852F, 1).append(STR_KN);
            }
            if (course > -1F) {
                sb.append((int) course).append(NavigationScreens.SIGN);
            }
        } else {
            if (speed > -1F) {
                NavigationScreens.append(sb, speed * 3.6F, 1).append(STR_KMH);
            }
            if (coordinates.getAlt() > -1F) {
                sb.append(coordinates.getAlt()).append(STR_M);
            }
        }
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
