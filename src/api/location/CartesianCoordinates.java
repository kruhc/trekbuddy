// @LICENSE@

package api.location;

public final class CartesianCoordinates implements GeodeticPosition {
    public double easting, northing;
    public char[] zone;

    /*
     * POOL
     */

//#ifndef __NOBJPOOL__

    private static final CartesianCoordinates[] pool = new CartesianCoordinates[8];
    private static int countFree;

//#endif

    public static CartesianCoordinates newInstance(final char[] zone,
                                                   final double easting,
                                                   final double northing) {
        CartesianCoordinates result = null;
//#ifndef __NOBJPOOL__
        synchronized (pool) {
            if (countFree > 0) {
                result = pool[--countFree];
                pool[countFree] = null;
            }
        }
//#endif
        if (result == null) {
            result = new CartesianCoordinates();
        }
        result.zone = zone;
        result.easting = easting;
        result.northing = northing;

        return result;
    }

    public static void releaseInstance(final CartesianCoordinates utm) {
//#ifndef __NOBJPOOL__
        synchronized (pool) {
            if (countFree < pool.length) {
                pool[countFree++] = utm;
            }
        }
//#endif
    }

    /*
     * ~POOL
     */

    private CartesianCoordinates() {
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
