// @LICENSE@

package api.location;

import cz.kruch.track.util.ExtraMath;

/**
 * Qualified coordinates.
 *
 * @author kruhc@seznam.cz
 */
public final class QualifiedCoordinates implements GeodeticPosition {

    public static QualifiedCoordinates INVALID = newInstance(Double.NaN, Double.NaN);

    public static final int UNKNOWN = 0;
    public static final int LAT = 1;
    public static final int LON = 2;

    public static final int DD_MM_SS  = 1;
    public static final int DD_MM     = 2;

    private static final double FLATTENING_WGS84 = 1D / 298.257223563;

    private double lat, lon;
    private float alt;
    private float hAccuracy, vAccuracy;

    /*
     * POOL
     */

    private static final QualifiedCoordinates[] pool = new QualifiedCoordinates[8];
    private static int countFree;

    public static QualifiedCoordinates newInstance(final double lat, final double lon) {
        return newInstance(lat, lon, Float.NaN, Float.NaN, Float.NaN);
    }

    public static QualifiedCoordinates newInstance(final double lat, final double lon, final float alt) {
        return newInstance(lat, lon, alt, Float.NaN, Float.NaN);
    }

    public static synchronized QualifiedCoordinates newInstance(final double lat,
                                                                final double lon,
                                                                final float alt,
                                                                final float hAccuracy,
                                                                final float vAccuracy) {
        QualifiedCoordinates result;
        if (countFree == 0) {
            result = new QualifiedCoordinates();
        } else {
            result = pool[--countFree];
            pool[countFree] = null;
        }
        result.lat = lat;
        result.lon = lon;
        result.alt = alt;
        result.hAccuracy = hAccuracy;
        result.vAccuracy = vAccuracy;

        return result;
    }

    public static synchronized void releaseInstance(final QualifiedCoordinates qc) {
        if (qc != null && qc != INVALID) {
            if (countFree < pool.length) {
                pool[countFree++] = qc;
            }
        }
    }

    /*
     * ~POOL
     */

    private QualifiedCoordinates() {
    }

    public QualifiedCoordinates _clone() {
        return newInstance(lat, lon, alt, hAccuracy, vAccuracy);
    }

    public QualifiedCoordinates copyFrom(final QualifiedCoordinates qc) {
        this.lat = qc.lat;
        this.lon = qc.lon;
        this.alt = qc.alt;
        this.hAccuracy = qc.hAccuracy;
        this.vAccuracy = qc.vAccuracy;
        return this;
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

    public float azimuthTo(final QualifiedCoordinates to) {
        double result = 0D;

        final int ilat1 = (int)(0.50D + lat * 360000D);
        final int ilat2 = (int)(0.50D + to.lat * 360000D);
        final int ilon1 = (int)(0.50D + lon * 360000D);
        final int ilon2 = (int)(0.50D + to.lon * 360000D);

        final double lat1 = Math.toRadians(lat);
        final double lon1 = Math.toRadians(lon);
        final double lat2 = Math.toRadians(to.lat);
        final double lon2 = Math.toRadians(to.lon);

        if (ilat1 == ilat2 && ilon1 == ilon2) {
            return (float) result;
        } else if (ilon1 == ilon2) {
            if (ilat1 > ilat2) {
                result = 180D;
            }
        } else if (ilat1 == ilat2) {
            if (ilon1 > ilon2) {
                result = 270D;
            } else {
                result = 90D;
            }
        } else {
            final double c = ExtraMath.acos(Math.sin(lat2) * Math.sin(lat1) + Math.cos(lat2) * Math.cos(lat1) * Math.cos((lon2 - lon1)));
            final double A = ExtraMath.asin(Math.cos(lat2) * Math.sin((lon2 - lon1)) / Math.sin(c));
            result = Math.toDegrees(A);

            if ((ilat2 > ilat1) && (ilon2 > ilon1)) {
            } else if ((ilat2 < ilat1) && (ilon2 < ilon1)){
                result = 180D - result;
            } else if ((ilat2 < ilat1) && (ilon2 > ilon1)) {
                result = 180D - result;
            } else if ((ilat2 > ilat1) && (ilon2 < ilon1)) {
                result += 360D;
            }
        }

        return (float) result;
    }

    public float distance(final QualifiedCoordinates to) {
        return distance(lat, lon, to.lat, to.lon);
    }

    public static float distance(final double lat, final double lon,
                                 final double toLat, final double toLon) {
        if (lat == toLat && lon == toLon) {
            return 0F;
        }

        /*
         * calculation for ellipsoid model
         */
        final double lat1 = Math.toRadians(lat);
        final double lon1 = - Math.toRadians(lon);
        final double lat2 = Math.toRadians(toLat);
        final double lon2 = - Math.toRadians(toLon);

        final double F = (lat1 + lat2) / 2;
        final double G = (lat1 - lat2) / 2;
        final double L = (lon1 - lon2) / 2;

        final double sing = Math.sin(G);
        final double cosl = Math.cos(L);
        final double cosf = Math.cos(F);
        final double sinl = Math.sin(L);
        final double sinf = Math.sin(F);
        final double cosg = Math.cos(G);

        final double sinf_2 = sinf * sinf;
        final double sing_2 = sing * sing;
        final double sinl_2 = sinl * sinl;
        final double cosf_2 = cosf * cosf;
        final double cosg_2 = cosg * cosg;
        final double cosl_2 = cosl * cosl;

        final double S = sing_2 * cosl_2 + cosf_2 * sinl_2;
        final double C = cosg_2 * cosl_2 + sinf_2 * sinl_2;
        final double W = ExtraMath.atan2(Math.sqrt(S), Math.sqrt(C));
        final double R = Math.sqrt((S * C)) / W;
        final double H1 = (3 * R - 1D) / (2 * C);
        final double H2 = (3 * R + 1D) / (2 * S);
        final double D = 2 * W * 6378137;
        return (float) (D * (1 + FLATTENING_WGS84 * H1 * sinf_2 * cosg_2 -
            FLATTENING_WGS84 * H2 * cosf_2 * sing_2));
    }

    public static QualifiedCoordinates project(final QualifiedCoordinates from,
                                               final double bearing, final double distance) {

        /*
         * Free code from http://www.gavaghan.org/blog/free-source-code/geodesy-library-vincentys-formula-java/.
         */

        final double f = FLATTENING_WGS84;
        final double a = 6378137;
        final double b = (1D - f) * a;
        final double aSquared = a * a;
        final double bSquared = b * b;
        final double phi1 = Math.toRadians(from.getLat());
        final double alpha1 = Math.toRadians(bearing);
        final double cosAlpha1 = Math.cos(alpha1);
        final double sinAlpha1 = Math.sin(alpha1);
        final double tanU1 = (1D - f) * Math.tan(phi1);
        final double cosU1 = 1D / Math.sqrt(1D + tanU1 * tanU1);
        final double sinU1 = tanU1 * cosU1;

        // eq. 1
        final double sigma1 = ExtraMath.atan2(tanU1, cosAlpha1);

        // eq. 2
        final double sinAlpha = cosU1 * sinAlpha1;
        final double sin2Alpha = sinAlpha * sinAlpha;
        final double cos2Alpha = 1D - sin2Alpha;
        final double uSquared = cos2Alpha * (aSquared - bSquared) / bSquared;

        // eq. 3
        final double A = 1 + (uSquared / 16384) * (4096 + uSquared * (-768 + uSquared * (320 - 175 * uSquared)));

        // eq. 4
        final double B = (uSquared / 1024) * (256 + uSquared * (-128 + uSquared * (74 - 47 * uSquared)));

        // iterate until there is a negligible change in sigma
        double deltaSigma;
        final double sOverbA = distance / (b * A);
        double sigma = sOverbA;
        double sinSigma;
        double prevSigma = sOverbA;
        double sigmaM2;
        double cosSigmaM2;
        double cos2SigmaM2;

        for (; ;) {
            // eq. 5
            sigmaM2 = 2 * sigma1 + sigma;
            cosSigmaM2 = Math.cos(sigmaM2);
            cos2SigmaM2 = cosSigmaM2 * cosSigmaM2;
            sinSigma = Math.sin(sigma);
            final double cosSignma = Math.cos(sigma);

            // eq. 6
            deltaSigma = B
                    * sinSigma
                    * (cosSigmaM2 + (B / 4)
                    * (cosSignma * (-1 + 2 * cos2SigmaM2) - (B / 6) * cosSigmaM2 * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM2)));

            // eq. 7
            sigma = sOverbA + deltaSigma;

            // break after converging to tolerance
            if (Math.abs(sigma - prevSigma) < 0.0000000000001)
                break;

            prevSigma = sigma;
        }

        sigmaM2 = 2 * sigma1 + sigma;
        cosSigmaM2 = Math.cos(sigmaM2);
        cos2SigmaM2 = cosSigmaM2 * cosSigmaM2;

        final double cosSigma = Math.cos(sigma);
        sinSigma = Math.sin(sigma);

        // eq. 8
        final double phi2 = ExtraMath.atan2(sinU1 * cosSigma + cosU1 * sinSigma * cosAlpha1, (1D - f)
                * Math.sqrt(sin2Alpha + ExtraMath.powi(sinU1 * sinSigma - cosU1 * cosSigma * cosAlpha1, 2)));

        // eq. 9
        // This fixes the pole crossing defect spotted by Matt Feemster. When a
        // path passes a pole and essentially crosses a line of latitude twice -
        // once in each direction - the longitude calculation got messed up. Using
        // atan2 instead of atan fixes the defect. The change is in the next 3
        // lines.
        // double tanLambda = sinSigma * sinAlpha1 / (cosU1 * cosSigma - sinU1 * sinSigma * cosAlpha1);
        // double lambda = Math.atan(tanLambda);
        final double lambda = ExtraMath.atan2(sinSigma * sinAlpha1, (cosU1 * cosSigma - sinU1 * sinSigma * cosAlpha1));

        // eq. 10
        final double C = (f / 16) * cos2Alpha * (4 + f * (4 - 3 * cos2Alpha));

        // eq. 11
        final double L = lambda - (1D - C) * f * sinAlpha * (sigma + C * sinSigma * (cosSigmaM2 + C * cosSigma * (-1 + 2 * cos2SigmaM2)));

        // result
        final double latitude = Math.toDegrees(phi2);
        final double longitude = from.getLon() + Math.toDegrees(L);

        return newInstance(latitude, longitude);
    }
}