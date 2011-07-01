// @LICENSE@

package api.location;

/**
 * Represents basic location information.
 * 
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class Location {
    private static final int XDR_MASK   = 0x40000000;

    private QualifiedCoordinates coordinates;
    private long timestamp;
    private float speed;
    private float course;
    private int fixsat;

    /*
     * POOL
     */

    private static final Location[] pool = new Location[8];
    private static int countFree;

    public static Location newInstance(final QualifiedCoordinates coordinates,
                                       final long timestamp,
                                       final int fix) {
        return newInstance(coordinates, timestamp, fix, 0);
    }

    public static synchronized Location newInstance(final QualifiedCoordinates coordinates,
                                                    final long timestamp,
                                                    final int fix, final int sat) {
        final Location result;

        if (countFree == 0) {
            result = new Location();
        } else {
            result = pool[--countFree];
            pool[countFree] = null;
        }
        result.coordinates = coordinates;
        result.timestamp = timestamp;
        result.speed = result.course = Float.NaN;
        result.fixsat = ((fix << 8) & 0x0000ff00) | (sat & 0x000000ff);

        return result;
    }

    public static synchronized void releaseInstance(final Location location) {
        if (location != null) {
            QualifiedCoordinates.releaseInstance(location.coordinates);
            location.coordinates = null;
            if (countFree < pool.length) {
                pool[countFree++] = location;
            }
        }
    }

    /*
     * ~POOL
     */

    private Location() {
    }

    public Location _clone() {
        final Location l = newInstance(coordinates._clone(), timestamp, 0);
        l.speed = speed;
        l.course = course;
        l.fixsat = fixsat;
        return l;
    }

    public QualifiedCoordinates getQualifiedCoordinates() {
        return coordinates;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getFix() {
        return (byte) ((fixsat >> 8) & 0x000000ff);
    }

    public int getFixQuality() {
        return (byte) ((fixsat >> 16) & 0x000000ff);
    }

    public int getSat() {
        return (byte) (fixsat & 0x000000ff);
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

    public void updateFix(int fix) {
        if (fix == 2 || fix == 3) {
            this.fixsat = ((fix << 8) & 0x0000ff00) | (this.fixsat & 0xffff00ff);
        }
    }

    public void updateFixQuality(int quality) {
        if (quality == 1 || quality == 2) {
            this.fixsat = ((quality << 16) & 0x00ff0000) | (this.fixsat & 0xff00ffff);
        }
    }

    public boolean isXdrBound() {
        return (this.fixsat & XDR_MASK) != 0;
    }

    public void setXdrBound(boolean bound) {
        if (bound)
            this.fixsat |= XDR_MASK;
        else
            this.fixsat &= ~XDR_MASK;
    }

    public boolean isSpeedValid() {
        final float accuracy = this.coordinates.getHorizontalAccuracy();
        final float speed = this.speed;
        if (!Float.isNaN(accuracy) && !Float.isNaN(speed)) {
            if (speed > (accuracy / 10)) {
                return true;
            }
        }
        return false;
    }

    public void validateEx() {
        final QualifiedCoordinates qc = this.coordinates;
        if (qc.getLat() == 0D || qc.getLon() == 0D) {
            fixsat &= 0xff0000ff; // preserve MASKs and number of sats, clear fix type and quality
        }
    }
}
