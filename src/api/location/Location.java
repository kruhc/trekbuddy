// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.location;

import java.util.Date;

public class Location {
    private QualifiedCoordinates coordinates;
    private long timestamp;
    private int fix;
    private int sat;
    private float speed = -1F;
    private float course = -1F;

    public Location(QualifiedCoordinates coordinates, long timestamp, int fix) {
        this(coordinates, timestamp, fix, -1);
    }

    public Location(QualifiedCoordinates coordinates, long timestamp, int fix, int sat) {
        this.coordinates = coordinates;
        this.timestamp = timestamp;
        this.fix = fix;
        this.sat = sat;
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

    public String toInfo() {
        return coordinates.toString();
    }

    public String toExtendedInfo() {
        StringBuffer sb = new StringBuffer(0);
        if (coordinates.getAlt() > -1F) {
            sb.append(coordinates.getAlt()).append(" m ");
        }
        if (speed > -1F) {
            sb.append((int) (new Float(speed * 1.852F)).intValue()).append(" km/h ");
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
