// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.location;

public final class MinDec {
    private int type = QualifiedCoordinates.UNKNOWN;
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

    private MinDec(String value, String sign) {
        int degl;
        switch (sign.charAt(0)) {
            case 'N': {
                this.type = QualifiedCoordinates.LAT;
                this.sign = 1;
                degl = 2;
            } break;
            case 'S': {
                this.type = QualifiedCoordinates.LAT;
                this.sign = -1;
                degl = 2;
            } break;
            case 'E': {
                this.type = QualifiedCoordinates.LON;
                this.sign = 1;
                degl = 3;
            } break;
            case 'W': {
                this.type = QualifiedCoordinates.LON;
                this.sign = -1;
                degl = 3;
            } break;
            default:
                throw new IllegalArgumentException("Malformed coordinate: " + value);
        }
        int i = value.indexOf('.');
        if ((type == QualifiedCoordinates.LAT && (i != 4)) || (type == QualifiedCoordinates.LON && i != 5)) {
            throw new IllegalArgumentException("Malformed coordinate: " + value);
        }
        this.deg = Integer.parseInt(value.substring(0, degl));
        this.min = Double.parseDouble(value.substring(degl));
    }

    public double doubleValue() {
        return sign * (deg + min / 60D);
    }

    private int[] toArray() {
        int h = deg;
        int m = (int) Math.floor(min);
        double l = min - m;
        l *= 100000D;
        int dec = (int) Math.floor(l);
        if ((l - dec) > 0.5D) {
            dec++;
            if (dec == 100000) {
                dec = 0;
                m++;
                if (m == 60) {
                    m = 0;
                    h++;
                }
            }
        }

        return new int[]{ h, m, dec };
    }

    public String toString() {
        StringBuffer sb = new StringBuffer(32);

        int[] hms = toArray();
        int h = hms[0];
        int m = hms[1];
        int s = hms[2];
        hms = null; // gc hint

        sb.append(type == QualifiedCoordinates.LAT ? (sign == -1 ? "S" : "N") : (sign == -1 ? "W" : "E"));
        sb.append(' ');
        sb.append(h).append(cz.kruch.track.TrackingMIDlet.SIGN).append(m).append('.');
        if (s < 10000) {
            sb.append('0');
        }
        if (s < 1000) {
            sb.append('0');
        }
        if (s < 100) {
            sb.append('0');
        }
        if (s < 10) {
            sb.append('0');
        }
        sb.append(s);

        return sb.toString();
    }

    private String toSentence() {
        StringBuffer sb = new StringBuffer(32);

        int[] hms = toArray();
        int h = hms[0];
        int m = hms[1];
        int s = hms[2];
        hms = null; // gc hint

        if (type == QualifiedCoordinates.LON && h < 100) {
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
        if (s < 10000) {
            sb.append('0');
        }
        if (s < 1000) {
            sb.append('0');
        }
        if (s < 100) {
            sb.append('0');
        }
        if (s < 10) {
            sb.append('0');
        }
        sb.append(s);
        sb.append(',').append(type == QualifiedCoordinates.LAT ? (sign == -1 ? "S" : "N") : (sign == -1 ? "W" : "E"));

        return sb.toString();
    }

    public static String toSentence(int type, double value) {
        return (new MinDec(type, value)).toSentence();
    }

    public static MinDec fromSentence(String value, String sign) {
        return new MinDec(value, sign);
    }
}
