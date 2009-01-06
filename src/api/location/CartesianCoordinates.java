// @LICENSE@

package api.location;

public final class CartesianCoordinates implements GeodeticPosition {
    public double easting, northing;
    public char[] zone;

    /*
     * POOL
     */

    private static final CartesianCoordinates[] pool = new CartesianCoordinates[8];
    private static int countFree;

    public static synchronized CartesianCoordinates newInstance(final char[] zone,
                                                                final double easting,
                                                                final double northing) {
        final CartesianCoordinates result;

        if (countFree == 0) {
            result = new CartesianCoordinates(zone, easting, northing);
        } else {
            result = pool[--countFree];
            pool[countFree] = null;
            result.zone = zone;
            result.easting = easting;
            result.northing = northing;
        }

        return result;
    }

    public static synchronized void releaseInstance(final CartesianCoordinates utm) {
        if (countFree < pool.length) {
            pool[countFree++] = utm;
        }
    }

    /*
     * ~POOL
     */

    private CartesianCoordinates(final char[] zone, final double easting, final double northing) {
        this.zone = zone;
        this.easting = easting;
        this.northing = northing;
    }

    public double getH() {
        return easting;
    }

    public double getV() {
        return northing;
    }

//#ifdef __LOG__
    public String toString() {
        return (new String(zone) + " " + easting + " " + northing);
    }
//#endif
}
