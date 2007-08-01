// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.location;

/* bad design - dependency */
import cz.kruch.track.configuration.Config;
import cz.kruch.track.ui.NavigationScreens;
import cz.kruch.track.util.SimpleCalendar;

import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone;

public final class Location {
    private static final char[] STR_KN  = { ' ', 'k', 'n', ' ' };
    private static final char[] STR_MPH = { ' ', 'm', 'p', 'h', ' '};
    private static final char[] STR_KMH = { ' ', 'k', 'm', '/', 'h', ' '};
    private static final char[] STR_M   = { ' ', 'm' };
    
/*
    private static final Calendar CALENDAR = Calendar.getInstance(TimeZone.getDefault());
    private static final Date DATE = new Date();
*/
    private static final SimpleCalendar CALENDAR = new SimpleCalendar(Calendar.getInstance(TimeZone.getDefault()));

    private QualifiedCoordinates coordinates;
    private long timestamp;
    private short fix;
    private short sat;
    private float speed;
    private float course;

    /*
     * POOL
     */

    private static final Location[] pool = new Location[8];
    private static int countFree;

    public static Location newInstance(QualifiedCoordinates coordinates,
                                       final long timestamp, final int fix) {
        return newInstance(coordinates, timestamp, fix, -1);
    }

    public synchronized static Location newInstance(QualifiedCoordinates coordinates,
                                                    final long timestamp, final int fix,
                                                    final int sat) {
        Location result;

        if (countFree == 0) {
            result = new Location(coordinates, timestamp, fix, sat);
        } else {
            result = pool[--countFree];
            pool[countFree] = null;
            result.coordinates = coordinates;
            result.timestamp = timestamp;
            result.fix = (short) fix;
            result.sat = (short) sat;
        }

        return result;
    }

    public synchronized static void releaseInstance(Location location) {
        if (location != null) {
            if (countFree < pool.length) {
                pool[countFree++] = location;
            }
            QualifiedCoordinates.releaseInstance(location.getQualifiedCoordinates());
            location.coordinates = null;
        }
    }

    /*
     * ~POOL
     */

    public Location clone() {
        return newInstance(coordinates.clone(), timestamp, fix, sat);
    }

    private Location(QualifiedCoordinates coordinates,
                     final long timestamp, final int fix, final int sat) {
        this.coordinates = coordinates;
        this.timestamp = timestamp;
        this.fix = (short) fix;
        this.sat = (short) sat;
        this.speed = this.course = -1F;
    }

    protected Location(QualifiedCoordinates coordinates,
                       final long timestamp, final int fix) {
        this(coordinates, timestamp, fix, -1);
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

    public StringBuffer toStringBuffer(StringBuffer sb) {
/*
        DATE.setTime(timestamp);
        CALENDAR.setTime(DATE);
        final int hour = CALENDAR.get(Calendar.HOUR_OF_DAY);
        final int min = CALENDAR.get(Calendar.MINUTE);
*/
        CALENDAR.setTime(timestamp);
        
        final int hour = CALENDAR.hour;
        final int min = CALENDAR.minute;

        if (hour < 10) {
            sb.append('0');
        }
        NavigationScreens.append(sb, hour).append(':');
        if (min < 10) {
            sb.append('0');
        }
        NavigationScreens.append(sb, min).append(' ');

        if (Config.unitsNautical) {
            if (speed > -1F) {
                NavigationScreens.append(sb, speed * 3.6F / 1.852F, 1).append(STR_KN);
            }
            if (course > -1F) {
                NavigationScreens.append(sb, (int) course).append(NavigationScreens.SIGN);
            }
        } else if (Config.unitsImperial) {
            if (speed > -1F) {
                NavigationScreens.append(sb, speed * 3.6F / 1.609F, 1).append(STR_MPH);
            }
            if (coordinates.getAlt() != Float.NaN) {
                NavigationScreens.append(sb, coordinates.getAlt(), 1).append(STR_M);
            }
        } else {
            if (speed > -1F) {
                NavigationScreens.append(sb, speed * 3.6F, 1).append(STR_KMH);
            }
            if (coordinates.getAlt() != Float.NaN) {
                NavigationScreens.append(sb, coordinates.getAlt(), 1).append(STR_M);
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
