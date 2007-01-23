// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.location;

/* bad design - dependency */
import cz.kruch.track.configuration.Config;
import cz.kruch.track.util.Mercator;
import cz.kruch.track.util.ExtraMath;
import cz.kruch.track.ui.NavigationScreens;

public final class QualifiedCoordinates implements GeodeticPosition {

    public static final int UNKNOWN = 0;
    public static final int LAT = 1;
    public static final int LON = 2;

    public static final int DD_MM_SS  = 1;
    public static final int DD_MM     = 2;

    private double lat, lon;
    private float alt;
    private boolean hp;

    private Datum datum;

    public QualifiedCoordinates(double lat, double lon) {
        this(lat, lon, -1F);
    }

    public QualifiedCoordinates(double lat, double lon, float alt) {
        this.lat = lat;
        this.lon = lon;
        this.alt = alt;
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

    public void setHp(boolean hp) {
        this.hp = hp;
    }

    public void setDatum(Datum datum) {
        this.datum = datum;
    }

    public QualifiedCoordinates toWgs84() {
        if (datum == null || datum == Datum.DATUM_WGS_84) {
            return this;
        }

        return datum.toWgs84(this);
    }

    public float distance(QualifiedCoordinates neighbour) {
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

        double dx = Math.abs(lon - neighbour.lon) * (111319.490 * Math.cos(Math.toRadians((lat + neighbour.lat) / 2)));
        double dy = Math.abs(lat - neighbour.lat) * (111319.490);

        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /* non-JSR179 signature */
    public int azimuthTo(QualifiedCoordinates neighbour, double distance) {
        double c = distance; // distance to neighbour

        double dx = neighbour.lon - lon;
        double dy = neighbour.lat - lat;
        QualifiedCoordinates artifical;
        int offset;

        if (dx > 0) {
            if (dy > 0) {
                artifical = new QualifiedCoordinates(neighbour.lat, lon);
                offset = 0;
            } else {
                artifical = new QualifiedCoordinates(lat, neighbour.lon);
                offset = 90;
            }
        } else {
            if (dy > 0) {
                artifical = new QualifiedCoordinates(lat, neighbour.lon);
                offset = 270;
            } else {
                artifical = new QualifiedCoordinates(neighbour.lat, lon);
                offset = 180;
            }
        }

        double a = artifical.distance(neighbour);
        double sina = a / c;

        // gc hint
        artifical = null;

        return offset + ExtraMath.asin(sina);
    }

    public String toString() {
        return toStringBuffer(new StringBuffer(32)).toString();
    }

    public StringBuffer toStringBuffer(StringBuffer sb) {
        if (Config.getSafeInstance().isUseGridFormat() && (Mercator.isGrid())) {
            Mercator.UTMCoordinates gridCoords = Mercator.LLtoGrid(this);
            if (gridCoords.zone != null) {
                sb.append(gridCoords.zone).append(' ');
            }
            zeros(sb,gridCoords.easting, 10000).append(round(gridCoords.easting));
            sb.append(' ');
            zeros(sb, gridCoords.northing, 10000).append(round(gridCoords.northing));
        } else if (Config.getSafeInstance().isUseUTM()) {
            Mercator.UTMCoordinates utmCoords = Mercator.LLtoUTM(this);
            sb.append(utmCoords.zone).append(' ');
            sb.append("E ").append(round(utmCoords.easting));
            sb.append(' ');
            sb.append("N ").append(round(utmCoords.northing));
        } else {
            sb.append(lat > 0D ? "N " : "S ");
            append(LAT, sb);
            sb.append(' ');
            sb.append(lon > 0D ? "E " : "W ");
            append(LON, sb);
        }

        return sb;
    }

    private StringBuffer append(int type, StringBuffer sb) {
        double l = Math.abs(type == LAT ? lat : lon);
        if (Config.getSafeInstance().isUseGeocachingFormat()) {
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
            sb.append(h).append(NavigationScreens.SIGN);
            sb.append(m).append('.');
            if (dec < 100) {
                sb.append('0');
            }
            if (dec < 10) {
                sb.append('0');
            }
            sb.append(dec);
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

            sb.append(h).append(NavigationScreens.SIGN);
            sb.append(m).append('\'');
            sb.append(s);
            if (hp) {
                sb.append('.').append(ss);
            }
            sb.append('"');
        }

        return sb;
    }

    public static int round(double d) {
        int i = (int) d;
        if ((d - i) > 0.5D) {
            i++;
        }

        return i;
    }

    private static StringBuffer zeros(StringBuffer sb, double d, int c) {
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

