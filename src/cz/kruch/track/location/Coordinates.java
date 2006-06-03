// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.location;

import java.io.UnsupportedEncodingException;

public class Coordinates {
    private static String SIGN = "^";

    private double lon, lat;

    static {
        try {
            SIGN = new String(new byte[]{ (byte) 0xc2, (byte) 0xb0 }, "UTF-8");
        } catch (UnsupportedEncodingException e) {
        }
    }

    public Coordinates(double lon, double lat) {
        this.lon = lon;
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public double getLat() {
        return lat;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer(24);
        append(lon, sb).append(" E");
        sb.append("   ");
        append(lat, sb).append(" N");

        return sb.toString();
    }

    private StringBuffer append(double l, StringBuffer sb) {
        int h = (int) Math.floor(l);
        l -= h;
        l *= 60D;
        int m = (int) Math.floor(l);
        l -= m;
        l *= 100D;
        int s = (int) Math.floor(l);

        sb.append(h).append(SIGN);
        if (m < 10) sb.append('0');
        sb.append(m).append('"');
        if (s < 10) sb.append('0');
        sb.append(s).append('\'');

        return sb;
    }
}
