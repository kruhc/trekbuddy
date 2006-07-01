// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.location;

import java.io.UnsupportedEncodingException;

public class QualifiedCoordinates {
    public static int DD_MM_SS  = 1;
    public static int DD_MM     = 2;

    private static String SIGN = "^";

    private double lat, lon;
    private float alt;

    static {
        try {
            SIGN = new String(new byte[]{ (byte) 0xc2, (byte) 0xb0 }, "UTF-8");
        } catch (UnsupportedEncodingException e) {
        }
    }

    public QualifiedCoordinates(double lat, double lon) {
        this(lat, lon, -1F);
    }

    public QualifiedCoordinates(double lat, double lon, float alt) {
        this.lat = lat;
        this.lon = lon;
        this.alt = alt;
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

    public String toString() {
        StringBuffer sb = new StringBuffer(24);
        if (lat > 0D)
            append(lat, sb).append(" N");
        else
            append(-1D * lat, sb).append(" S");
        sb.append("   ");
        if (lon > 0D)
            append(lon, sb).append(" E");
        else
            append(-1D * lon, sb).append(" W");
        if (alt > -1F) {
            sb.append(' ').append(alt);
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

        sb.append(h).append(SIGN);
        if (m < 10) sb.append('0');
        sb.append(m).append('\'');
        if (s < 10) sb.append('0');
        sb.append(s).append('"');

        return sb;
    }
}
