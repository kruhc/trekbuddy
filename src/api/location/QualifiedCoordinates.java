// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.location;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.util.Mercator;
import cz.kruch.track.util.Datum;

import java.io.UnsupportedEncodingException;

public final class QualifiedCoordinates {

    public static final int UNKNOWN = 0;
    public static final int LAT = 1;
    public static final int LON = 2;

    public static class MinDec {
        private static final String[] NS = new String[]{ "N", "S" };
        private static final String[] EW = new String[]{ "E", "W" };

        private int type = UNKNOWN;
        private int sign = 0;
        private int deg = 0;
        private double min = 0D;

        public MinDec(int type, double value) {
            this.type = type;
            this.sign = value < 0D ? -1 : 1;
            value = Math.abs(value);
            this.deg = (int) Math.floor(value);
            value -= this.deg;
            value *= 60D;
            this.min = value;
        }

        private MinDec(String value) {
            value = value.trim();
            if (value.length() < 5) {
                throw new IllegalArgumentException("Malformed coordinate: " + value);
            }
            switch (value.charAt(0)) {
                case 'N': {
                    this.type = LAT;
                    this.sign = 1;
                } break;
                case 'S': {
                    this.type = LAT;
                    this.sign = -1;
                } break;
                case 'E': {
                    this.type = LON;
                    this.sign = 1;
                } break;
                case 'W': {
                    this.type = LON;
                    this.sign = -1;
                } break;
                default:
                    throw new IllegalArgumentException("Malformed coordinate: " + value);
            }
            int i = value.indexOf(SIGN);
            if (i < 3) {
                throw new IllegalArgumentException("Malformed coordinate: " + value);
            }
            this.deg = Integer.parseInt(value.substring(1, i).trim());
            this.min = Double.parseDouble(value.substring(i + SIGN.length()).trim());
        }

        private MinDec(String value, String sign) {
            int degl;
            switch (sign.charAt(0)) {
                case 'N': {
                    this.type = LAT;
                    this.sign = 1;
                    degl = 2;
                } break;
                case 'S': {
                    this.type = LAT;
                    this.sign = -1;
                    degl = 2;
                } break;
                case 'E': {
                    this.type = LON;
                    this.sign = 1;
                    degl = 3;
                } break;
                case 'W': {
                    this.type = LON;
                    this.sign = -1;
                    degl = 3;
                } break;
                default:
                    throw new IllegalArgumentException("Malformed coordinate: " + value);
            }
            int i = value.indexOf('.');
            if ((type == LAT && (i != 4)) || (type == LON && i != 5)) {
                throw new IllegalArgumentException("Malformed coordinate: " + value);
            }
            this.deg = Integer.parseInt(value.substring(0, degl));
            this.min = Double.parseDouble(value.substring(degl));
        }

        public String getLabel() {
            return type == LAT ? "Latitude" : "Longitude";
        }

        public String[] getLetters() {
            return type == LAT ? NS : EW;
        }

        public int getType() {
            return type;
        }

        public int getSign() {
            return sign;
        }

        public int getDeg() {
            return deg;
        }

        public double getMin() {
            return min;
        }

        public double doubleValue() {
            return sign * (deg + min / 60D);
        }

        private int[] toArray() {
            int h = deg;
            int m = (int) Math.floor(min);
            double l = min - m;
            l *= 1000D;
            int s = (int) Math.floor(l);
            if ((l - s) > 0.5D) {
                s++;
                if (s == 1000) {
                    s = 0;
                    m++;
                    if (m == 60) {
                        m = 0;
                        h++;
                    }
                }
            }

            return new int[]{ h, m, s };
        }

        public String toString() {
            StringBuffer sb = new StringBuffer(16);

            int[] hms = toArray();
            int h = hms[0];
            int m = hms[1];
            int s = hms[2];

            sb.append(type == LAT ? (sign == -1 ? "S" : "N") : (sign == -1 ? "W" : "E"));
            sb.append(' ');
            sb.append(h).append(SIGN).append(m).append('.');
            if (s < 100) {
                sb.append('0');
            }
            if (s < 10) {
                sb.append('0');
            }
            sb.append(s);

            return sb.toString();
        }

        public String toSentence() {
            StringBuffer sb = new StringBuffer(16);

            int[] hms = toArray();
            int h = hms[0];
            int m = hms[1];
            int s = hms[2];

            if (type == LON && h < 100) {
                sb.append('0');
            }
            if (h < 10) {
                sb.append('0');
            }
            sb.append(h);
            if (m < 10) {
                sb.append('0');
            }
            sb.append(m).append('.');
            if (s < 100) {
                sb.append('0');
            }
            if (s < 10) {
                sb.append('0');
            }
            sb.append(s);
            sb.append(',').append(type == LAT ? (sign == -1 ? "S" : "N") : (sign == -1 ? "W" : "E"));

            return sb.toString();
        }

        public static String toDecimalString(int type, double value) {
            return (new MinDec(type, value)).toString();
        }

        public static MinDec fromDecimalString(String value) {
            return new MinDec(value);
        }

        public static String toSentence(int type, double value) {
            return (new MinDec(type, value)).toSentence();
        }

        public static MinDec fromSentence(String value, String sign) {
            return new MinDec(value, sign);
        }
    }

    public static final int DD_MM_SS  = 1;
    public static final int DD_MM     = 2;

    public static String SIGN = "^";
    public static String DELTA = "d";

    private static double SINS[] = new double[90 + 1];

    private double lat, lon;
    private float alt;

    static {
        try {
            SIGN = new String(new byte[]{ (byte) 0xc2, (byte) 0xb0 }, "UTF-8");
            DELTA = new String(new byte[]{ (byte) 0xce, (byte) 0x94 }, "UTF-8");
        } catch (UnsupportedEncodingException e) {
        }
        for (int N = SINS.length, i = 0; i < N; i++) {
            SINS[i] = Math.sin(Math.toRadians(i));
        }
    }

    public QualifiedCoordinates(double lat, double lon) {
        this(lat, lon, -1F);
    }

    public QualifiedCoordinates(double lat, double lon, float alt) {
        this.lat = lat;
        this.lon = lon;
        this.alt = alt == Float.NaN ? -1F : alt;
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

    public float distance(QualifiedCoordinates neighbour) {
        double h1 = 0, h2 = 0;
        double R = Datum.current.getEllipsoid().getEquatorialRadius();

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

        return (new Double(Math.sqrt(dx * dx + dy * dy + dz * dz))).floatValue();
    }

    /* non-JSR179 signature */
    public int azimuthTo(QualifiedCoordinates neighbour, double distance) {
        double c = distance; // distance to neighbour

        double dx = neighbour.lon - lon;
        double dy = neighbour.lat - lat;
        QualifiedCoordinates artifical;
        int offset = 0;

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
        double sinAlpha = a / c;

        // gc hint
        artifical = null;

        // find best match
        double matchVal = Double.MAX_VALUE;
        int matchIdx = -1;
        for (int N = SINS.length, i = 0; i < N; i++) {
            double diff = Math.abs(sinAlpha - SINS[i]);
            if (diff < matchVal) {
                matchVal = diff;
                matchIdx = i;
            }
        }

        return offset + matchIdx;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer(16);
        if (Config.getSafeInstance().isUseUTM()) {
            Mercator.UTMCoordinates utmCoords = Mercator.LLtoUTM(lat, lon);
            sb.append(utmCoords.zone).append(' ');
            sb.append("E ").append((int) utmCoords.easting).append(' ');
            sb.append("N ").append((int) utmCoords.northing);
        } else {
            if (lat > 0D) {
                sb.append("N ");
                append(lat, sb);
            } else {
                sb.append("S ");
                append(-1 * lat, sb);
            }
            sb.append("  ");
            if (lon > 0D) {
                sb.append("E ");
                append(lon, sb);
            } else {
                sb.append("W ");
                append(-1 * lon, sb);
            }
        }

        return sb.toString();
    }

    private StringBuffer append(double l, StringBuffer sb) {
        int h = (int) Math.floor(l);
        l -= h;
        l *= 60D;
        int m = (int) Math.floor(l);
        l -= m;
        l *= 60D;
        int s = (int) Math.floor(l);
        int ss = 0;

        boolean b = Config.getSafeInstance().isDecimalPrecision();
        if (b) { // round decimals
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

        sb.append(h).append(SIGN);
/*
        if (m < 10) sb.append('0');
*/
        sb.append(m).append('\'');
/*
        if (s < 10) sb.append('0');
*/
        sb.append(s);
        if (b) {
            sb.append('.').append(ss);
        }
        sb.append('"');

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

