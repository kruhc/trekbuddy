// @LICENSE@

package api.location;

import cz.kruch.track.util.ExtraMath;

public final class QualifiedCoordinates implements GeodeticPosition {

    public static final int UNKNOWN = 0;
    public static final int LAT = 1;
    public static final int LON = 2;

    public static final int DD_MM_SS  = 1;
    public static final int DD_MM     = 2;

    private double lat, lon;
    private float alt;
    private float hAccuracy, vAccuracy;

    /*
     * POOL
     */

    private static final QualifiedCoordinates[] pool = new QualifiedCoordinates[8];
    private static int countFree;

    public static QualifiedCoordinates newInstance(final double lat, final double lon) {
        return newInstance(lat, lon, Float.NaN);
    }

    public static synchronized QualifiedCoordinates newInstance(final double lat,
                                                                final double lon,
                                                                final float alt) {
        QualifiedCoordinates result;

        if (countFree == 0) {
            result = new QualifiedCoordinates(lat, lon, alt);
        } else {
            result = pool[--countFree];
            pool[countFree] = null;
            result.lat = lat;
            result.lon = lon;
            result.alt = alt;
            result.hAccuracy = result.vAccuracy = Float.NaN;
        }

        return result;
    }

    public static synchronized void releaseInstance(final QualifiedCoordinates qc) {
        if (qc != null) {
            if (countFree < pool.length) {
                pool[countFree++] = qc;
            }
        }
    }

    /*
     * ~POOL
     */

    public QualifiedCoordinates clone() {
        QualifiedCoordinates clone = newInstance(lat, lon, alt);
        clone.hAccuracy = this.hAccuracy;
        clone.vAccuracy = this.vAccuracy;
        return clone;
    }

    private QualifiedCoordinates(final double lat, final double lon,
                                 final float alt) {
        this.lat = lat;
        this.lon = lon;
        this.alt = alt;
        this.hAccuracy = this.vAccuracy = Float.NaN;
    }

    public double getH() {
        return lon;
    }

    public double getV() {
        return lat;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public float getAlt() {
        return alt;
    }

    public void setAlt(float alt) {
        this.alt = alt;
    }

    public float getHorizontalAccuracy() {
        return hAccuracy;
    }

    public void setHorizontalAccuracy(float accuracy) {
        this.hAccuracy = accuracy;
    }

    public float getVerticalAccuracy() {
        return vAccuracy;
    }

    public void setVerticalAccuracy(float accuracy) {
        this.vAccuracy = accuracy;
    }

    public float distance(QualifiedCoordinates neighbour) {
        return distance(neighbour.lat, neighbour.lon);
    }

    private float distance(final double neighbourLat, final double neighbourLon) {
        if (lat == neighbourLat && lon == neighbourLon) {
            return 0F;
        }

        /*
         * calculation for ellipsoid model
         */
        final double lat1 = Math.toRadians(lat);
        final double lon1 = - Math.toRadians(lon);
        final double lat2 = Math.toRadians(neighbourLat);
        final double lon2 = - Math.toRadians(neighbourLon);

        final double F = (lat1 + lat2) / 2.0D;
        final double G = (lat1 - lat2) / 2.0D;
        final double L = (lon1 - lon2) / 2.0D;

        final double sing = Math.sin(G);
        final double cosl = Math.cos(L);
        final double cosf = Math.cos(F);
        final double sinl = Math.sin(L);
        final double sinf = Math.sin(F);
        final double cosg = Math.cos(G);

        final double S = sing * sing * cosl * cosl + cosf * cosf * sinl * sinl;
        final double C = cosg * cosg * cosl * cosl + sinf * sinf * sinl * sinl;
        final double W = ExtraMath.atan2(Math.sqrt(S), Math.sqrt(C));
        final double R = Math.sqrt((S * C)) / W;
        final double H1 = (3D * R - 1.0D) / (2.0D * C);
        final double H2 = (3D * R + 1.0D) / (2.0D * S);
        final double D = 2 * W * 6378137;
        return (float) (D * (1 + (1.0D / 298.257223563D) * H1 * sinf * sinf * cosg * cosg -
            (1.0D / 298.257223563D) * H2 * cosf * cosf * sing * sing));

        /*
         * calculation for spherical model
         */
/*
        final double R = Datum.DATUM_WGS_84.getEllipsoid().getEquatorialRadius();

        final double lat1 = Math.toRadians(lat);
        final double lon1 = Math.toRadians(lon);
        final double lat2 = Math.toRadians(neighbourLat);
        final double lon2 = Math.toRadians(neighbourLon);

        final double temp_cosLat1 = Math.cos(lat1);
        final double x1 = R * (temp_cosLat1 * Math.cos(lon1));
        final double y1 = R * (Math.sin(lat1));
        final double z1 = R * (temp_cosLat1 * Math.sin(lon1));
        final double temp_cosLat2 = Math.cos(lat2);
        final double x2 = R * (temp_cosLat2 * Math.cos(lon2));
        final double y2 = R * (Math.sin(lat2));
        final double z2 = R * (temp_cosLat2 * Math.sin(lon2));

        final double dx = (x2 - x1);
        final double dy = (y2 - y1);
        final double dz = (z2 - z1);

        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
*/

        /*
         * good results only for distances up to 10-15 km
         */
/*
        final double dx = Math.abs(lon - neighbourLon) * (111319.490 * Math.cos(Math.toRadians((lat + neighbourLat) / 2)));
        final double dy = Math.abs(lat - neighbourLat) * (111319.490);

        return (float) Math.sqrt(dx * dx + dy * dy);
*/
    }

    /* non-JSR179 signature TODO fix */
    public float azimuthTo(final QualifiedCoordinates neighbour, final double distance) {
        double result = 0D;

        final int ilat1 = (int)(0.50D + lat * 360000D);
        final int ilat2 = (int)(0.50D + neighbour.lat * 360000D);
        final int ilon1 = (int)(0.50D + lon * 360000D);
        final int ilon2 = (int)(0.50D + neighbour.lon * 360000D);

        final double lat1 = Math.toRadians(lat);
        final double lon1 = Math.toRadians(lon);
        final double lat2 = Math.toRadians(neighbour.lat);
        final double lon2 = Math.toRadians(neighbour.lon);

        if (ilat1 == ilat2 && ilon1 == ilon2) {
            return (float) result;
        } else if (ilon1 == ilon2) {
            if (ilat1 > ilat2) {
                result = 180.0;
            }
        } else {
            final double c = ExtraMath.acos(Math.sin(lat2) * Math.sin(lat1) + Math.cos(lat2) * Math.cos(lat1) * Math.cos((lon2 - lon1)));
            final double A = ExtraMath.asin(Math.cos(lat2) * Math.sin((lon2 - lon1)) / Math.sin(c));
            result = Math.toDegrees(A);

            if ((ilat2 > ilat1) && (ilon2 > ilon1)) {
            } else if ((ilat2 < ilat1) && (ilon2 < ilon1)){
                result = 180.0 - result;
            } else if ((ilat2 < ilat1) && (ilon2 > ilon1)) {
                result = 180.0 - result;
            } else if ((ilat2 > ilat1) && (ilon2 < ilon1)) {
                result += 360.0;
            }
        }

        return (float) result;

        /*
         * Pythagoras would be happy :-))))
         */
/*
        final double dx = neighbour.lon - lon;
        final double dy = neighbour.lat - lat;
        double artificalLat, artificalLon;
        final int offset;

        if (dx > 0) {
            if (dy > 0) {
                artificalLat = lat;
                artificalLon = neighbour.lon;
                offset = 0;
            } else {
                artificalLat = neighbour.lat;
                artificalLon = lon;
                offset = 90;
            }
        } else {
            if (dy > 0) {
                artificalLat = neighbour.lat;
                artificalLon = lon;
                offset = 270;
            } else {
                artificalLat = lat;
                artificalLon = neighbour.lon;
                offset = 180;
            }
        }

        final double a = distance(artificalLat, artificalLon);
        double sina = a / distance;
        if (sina > 1.0D && sina < 1.001D) { // tolerate calculation inaccuracy 
            sina = 1.0D;
        }

        return (float) (offset + Math.toDegrees(ExtraMath.asin(sina)));
*/
    }
}