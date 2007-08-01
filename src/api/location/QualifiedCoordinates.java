// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.location;

/* bad design - dependency */
import cz.kruch.track.configuration.Config;
import cz.kruch.track.util.Mercator;
import cz.kruch.track.ui.NavigationScreens;
import public_domain.Xedarius;

public final class QualifiedCoordinates implements GeodeticPosition {

    public static final int UNKNOWN = 0;
    public static final int LAT = 1;
    public static final int LON = 2;

    public static final int DD_MM_SS  = 1;
    public static final int DD_MM     = 2;

    private double lat, lon;
    private float alt;
    private float accuracy;

    /*
     * POOL
     */

    private static final QualifiedCoordinates[] pool = new QualifiedCoordinates[8];
    private static int countFree;

    public static QualifiedCoordinates newInstance(final double lat, final double lon) {
        return newInstance(lat, lon, Float.NaN, -1F);
    }

    public static QualifiedCoordinates newInstance(final double lat, final double lon,
                                                   final float alt) {
        return newInstance(lat, lon, alt, -1F);
    }

    public synchronized static QualifiedCoordinates newInstance(final double lat,
                                                                final double lon,
                                                                final float alt,
                                                                final float accuracy) {
        QualifiedCoordinates result;

        if (countFree == 0) {
            result = new QualifiedCoordinates(lat, lon, alt, -1F);
        } else {
            result = pool[--countFree];
            pool[countFree] = null;
            result.lat = lat;
            result.lon = lon;
            result.alt = alt;
            result.accuracy = accuracy;
        }

        return result;
    }

    public synchronized static void releaseInstance(QualifiedCoordinates qc) {
        if (qc != null && countFree < pool.length) {
            pool[countFree++] = qc;
        }
    }

    /*
     * ~POOL
     */

    public QualifiedCoordinates clone() {
        return newInstance(lat, lon, alt, accuracy);
    }

    private QualifiedCoordinates(final double lat, final double lon,
                                 final float alt, final float accuracy) {
        this.lat = lat;
        this.lon = lon;
        this.alt = alt;
        this.accuracy = accuracy;
    }

    protected QualifiedCoordinates(QualifiedCoordinates qc) {
        this.lat = qc.lat;
        this.lon = qc.lon;
        this.alt = qc.alt;
        this.accuracy = qc.accuracy;
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

    public float getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(float accuracy) {
        this.accuracy = accuracy;
    }

    public float distance(QualifiedCoordinates neighbour) {
        return distance(neighbour.lat, neighbour.lon);
    }

    private float distance(final double neighbourLat, final double neighbourLon) {
/*
        double h1 = 0, h2 = 0;
        double R = Datum.DATUM_WGS_84.getEllipsoid().getEquatorialRadius();

        double lat1 = Math.toRadians(lat);
        double lon1 = Math.toRadians(lon);
        double lat2 = Math.toRadians(neighbour.lat);
        double lon2 = Math.toRadians(neighbour.lon);

        double temp_cosLat1 = Math.cos(lat1);
        double x1 = (R + h1) * (temp_cosLat1 * Math.cos(lon1));
        double y1 = (R + h1) * (Math.sin(lat1));
        double z1 = (R + h1) * (temp_cosLat1 * Math.sin(lon1));
        double temp_cosLat2 = Math.cos(lat2);
        double x2 = (R + h2) * (temp_cosLat2 * Math.cos(lon2));
        double y2 = (R + h2) * (Math.sin(lat2));
        double z2 = (R + h2) * (temp_cosLat2 * Math.sin(lon2));

        double dx = (x2 - x1);
        double dy = (y2 - y1);
        double dz = (z2 - z1);

        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
*/

        final double dx = Math.abs(lon - neighbourLon) * (111319.490 * Math.cos(Math.toRadians((lat + neighbourLat) / 2)));
        final double dy = Math.abs(lat - neighbourLat) * (111319.490);

        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /* non-JSR179 signature TODO fix */
    public float azimuthTo(QualifiedCoordinates neighbour, final double distance) {
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
        double sina = a / distance /* c */;
        if (sina > 1.0D && sina < 1.001D) { // tolerate calculation inaccuracy 
            sina = 1.0D;
        }

        return (float) (offset + Math.toDegrees(Xedarius.asin(sina)));
    }

    public String toString() {
        return toStringBuffer(new StringBuffer(32)).toString();
    }

    public StringBuffer toStringBuffer(StringBuffer sb) {
        if (Config.useGridFormat && (Mercator.isGrid())) {
            Mercator.Coordinates gridCoords = Mercator.LLtoGrid(this);
            if (gridCoords.zone != null) {
                sb.append(gridCoords.zone).append(' ');
            }
            zeros(sb, gridCoords.easting, 10000);
            NavigationScreens.append(sb, round(gridCoords.easting));
            sb.append(' ');
            zeros(sb, gridCoords.northing, 10000);
            NavigationScreens.append(sb, round(gridCoords.northing));
            Mercator.Coordinates.releaseInstance(gridCoords);
        } else if (Config.useUTM) {
            Mercator.Coordinates utmCoords = Mercator.LLtoUTM(this);
            sb.append(utmCoords.zone).append(' ');
            sb.append('E').append(' ');
            NavigationScreens.append(sb, round(utmCoords.easting));
            sb.append(' ');
            sb.append('N').append(' ');
            NavigationScreens.append(sb, round(utmCoords.northing));
            Mercator.Coordinates.releaseInstance(utmCoords);
        } else {
            // condensed for SXG75
            if (cz.kruch.track.TrackingMIDlet.sxg75 && Config.decimalPrecision) {
                sb.append(lat > 0D ? 'N' : 'S');
                append(LAT, true, sb);
                sb.deleteCharAt(sb.length() - 1);
                sb.append(' ');
                sb.append(lon > 0D ? 'E' : 'W');
                append(LON, true, sb);
                sb.deleteCharAt(sb.length() - 1);
            } else { // decent devices
                sb.append(lat > 0D ? 'N' : 'S').append(' ');
                append(LAT, Config.decimalPrecision, sb);
                sb.append(' ');
                sb.append(lon > 0D ? 'E' : 'W').append(' ');
                append(LON, Config.decimalPrecision, sb);
            }
        }

        return sb;
    }

    private StringBuffer append(final int type, final boolean hp, StringBuffer sb) {
        double l = Math.abs(type == LAT ? lat : lon);
        if (Config.useGeocachingFormat) {
            int h = (int) Math.floor(l);
            l -= h;
            l *= 60D;
            int m = (int) Math.floor(l);
            l -= m;
            l *= 1000D;
            int dec = (int) Math.floor(l);
            if ((l - dec) > 0.5D) {
                dec++;
                if (dec == 1000) {
                    dec = 0;
                    m++;
                    if (m == 60) {
                        m = 0;
                        h++;
                    }
                }
            }

            if (type == LON && h < 100) {
                sb.append('0');
            }
            if (h < 10) {
                sb.append('0');
            }
            NavigationScreens.append(sb, h).append(NavigationScreens.SIGN);
            NavigationScreens.append(sb, m).append('.');
            if (dec < 100) {
                sb.append('0');
            }
            if (dec < 10) {
                sb.append('0');
            }
            NavigationScreens.append(sb, dec);
        } else {
            int h = (int) Math.floor(l);
            l -= h;
            l *= 60D;
            int m = (int) Math.floor(l);
            l -= m;
            l *= 60D;
            int s = (int) Math.floor(l);
            int ss = 0;

            if (hp) { // round decimals
                l -= s;
                l *= 10;
                ss = (int) Math.floor(l);
                if ((l - ss) > 0.5D) {
                    ss++;
                    if (ss == 10) {
                        ss = 0;
                        s++;
                        if (s == 60) {
                            s = 0;
                            m++;
                            if (m == 60) {
                                m = 0;
                                h++;
                            }
                        }
                    }
                }
            } else { // round secs
                if ((l - s) > 0.5D) {
                    s++;
                    if (s == 60) {
                        s = 0;
                        m++;
                        if (m == 60) {
                            m = 0;
                            h++;
                        }
                    }
                }
            }

            NavigationScreens.append(sb, h).append(NavigationScreens.SIGN);
            NavigationScreens.append(sb, m).append('\'');
            NavigationScreens.append(sb, s);
            if (hp) {
                sb.append('.');
                NavigationScreens.append(sb, ss);
            }
            sb.append('"');
        }

        return sb;
    }

    public static int round(final double d) {
        int i = (int) d;
        if ((d - i) > 0.5D) {
            i++;
        }

        return i;
    }

    private static StringBuffer zeros(StringBuffer sb, final double d, final int c) {
        int i = Mercator.grade(d);
        while (i < c) {
            sb.append('0');
            i *= 10;
        }

        return sb;
    }
}

/*
double ApproxDistance(double lat1, double lon1, double lat2,
                      double lon2)
{
   lat1 = GEO::DE2RA * lat1;
   lon1 = -GEO::DE2RA * lon1;
   lat2 = GEO::DE2RA * lat2;
   lon2 = -GEO::DE2RA * lon2;

   double F = (lat1 + lat2) / 2.0;
   double G = (lat1 - lat2) / 2.0;
   double L = (lon1 - lon2) / 2.0;

   double sing = sin(G);
   double cosl = cos(L);
   double cosf = cos(F);
   double sinl = sin(L);
   double sinf = sin(F);
   double cosg = cos(G);

   double S = sing*sing*cosl*cosl + cosf*cosf*sinl*sinl;
   double C = cosg*cosg*cosl*cosl + sinf*sinf*sinl*sinl;
   double W = atan2(sqrt(S),sqrt(C));
   double R = sqrt((S*C))/W;
   double H1 = (3 * R - 1.0) / (2.0 * C);
   double H2 = (3 * R + 1.0) / (2.0 * S);
   double D = 2 * W * GEO::ERAD;
   return (D * (1 + GEO::FLATTENING * H1 * sinf*sinf*cosg*cosg -
   GEO::FLATTENING*H2*cosf*cosf*sing*sing));
}
*/

