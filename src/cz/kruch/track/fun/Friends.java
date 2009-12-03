// @LICENSE@

package cz.kruch.track.fun;

import cz.kruch.track.Resources;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.location.StampedWaypoint;
import cz.kruch.track.ui.Desktop;
import cz.kruch.track.ui.NavigationScreens;
import cz.kruch.track.util.CharArrayTokenizer;

import javax.microedition.lcdui.Displayable;

import java.io.IOException;
import java.util.Date;

import api.location.QualifiedCoordinates;

public abstract class Friends {
    public static final String TYPE_IAH         = "IAH";
    public static final String TYPE_MYT         = "MYT";
    public static final String SERVER_URL       = "sms://:16007";

    static final String SMS_PROTOCOL    = "sms://";
    static final String PORT            = ":16007";
    static final String TBSMS_HEADER    = "$TB";
    static final String TBSMS_IAH       = "$TBIAH";
    static final String TBSMS_MYT       = "$TBMYT";

    static final char SEPARATOR_CHAR = ',';

    public static Friends createInstance() throws Exception {
        if (cz.kruch.track.TrackingMIDlet.android) {
            throw new RuntimeException("Not supported");
        } else {
            return (Friends) Class.forName("cz.kruch.track.fun.Jsr120Friends").newInstance();
        }
    }

    public abstract void start() throws IOException;
    public abstract void destroy();
    public abstract void reconfigure(Displayable next);
    public abstract void send(String phone, String type, String message,
                              QualifiedCoordinates coordinates, long time);

    static double parseSentence(final String value, final String letter) {
        int degl, type, sign;
        switch (letter.charAt(0)) {
            case 'N': {
                type = QualifiedCoordinates.LAT;
                sign = 1;
                degl = 2;
            } break;
            case 'S': {
                type = QualifiedCoordinates.LAT;
                sign = -1;
                degl = 2;
            } break;
            case 'E': {
                type = QualifiedCoordinates.LON;
                sign = 1;
                degl = 3;
            } break;
            case 'W': {
                type = QualifiedCoordinates.LON;
                sign = -1;
                degl = 3;
            } break;
            default:
                throw new IllegalArgumentException("Malformed coordinate: " + value);
        }

        final int i = value.indexOf('.');
        if ((type == QualifiedCoordinates.LAT && (i != 4)) || (type == QualifiedCoordinates.LON && i != 5)) {
            throw new IllegalArgumentException("Malformed coordinate: " + value);
        }

        final int deg = Integer.parseInt(value.substring(0, degl));
        final double min = Double.parseDouble(value.substring(degl));

        return sign * (deg + min / 60D);
    }

    static String toSentence(final int type, double value) {
        final int sign = value < 0D ? -1 : 1;
        value = Math.abs(value);
        int d = (int) Math.floor(value);
        value -= d;
        value *= 60D;
        double min = value;
        int m = (int) Math.floor(min);
        double l = min - m;
        l *= 100000D;
        int s = (int) Math.floor(l);
        if ((l - s) > 0.5D) {
            s++;
            if (s == 100000) {
                s = 0;
                m++;
                if (m == 60) {
                    m = 0;
                    d++;
                }
            }
        }

        StringBuffer sb = new StringBuffer(32);
        if (type == QualifiedCoordinates.LON && d < 100) {
            sb.append('0');
        }
        if (d < 10) {
            sb.append('0');
        }
        NavigationScreens.append(sb, d);
        if (m < 10) {
            sb.append('0');
        }
        NavigationScreens.append(sb, m).append('.');
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
        NavigationScreens.append(sb, s);
        sb.append(',').append(type == QualifiedCoordinates.LAT ? (sign == -1 ? "S" : "N") : (sign == -1 ? "W" : "E"));

        return sb.toString();
    }

    static String headerify(String s) {
        if (s.startsWith("SMS")) {
            s = s.substring(3).trim();
        }
        return "(" + s + ") ";
    }

//#ifdef __LOG__
    static void debug(String text) {
        // decode
        CharArrayTokenizer tokenizer = new CharArrayTokenizer();
        tokenizer.init(text, new char[]{ ',', '*' }, false);
        String header = tokenizer.next().toString();
        String type = null;
        String chat = null;
        if (TBSMS_IAH.equals(header)) {
            type = TYPE_IAH;
            chat = headerify(Resources.getString(Resources.NAV_ITEM_SMS_IAH));
        } else if (TBSMS_MYT.equals(header)) {
            type = TYPE_MYT;
            chat = headerify(Resources.getString(Resources.NAV_ITEM_SMS_MYT));
        }
        if (type != null) {
            // get tokens
            String times = tokenizer.next().toString();
            String latv = tokenizer.next().toString();
            String lats = tokenizer.next().toString();
            String lonv = tokenizer.next().toString();
            String lons = tokenizer.next().toString();
            String unknown = tokenizer.next().toString();
            if (tokenizer.hasMoreTokens()) {
                chat += unknown;
            } else {
                // unknown is checksum with leading '*'
            }

            long time = Long.parseLong(times) * 1000;
            double lat = parseSentence(latv, lats);
            double lon = parseSentence(lonv, lons);
            String xxx = (new Date(time)).toString();
            Waypoint wpt = new StampedWaypoint(QualifiedCoordinates.newInstance(lat, lon),
                                               null, chat, time);

        } else {
            Desktop.showWarning(Resources.getString(Resources.DESKTOP_MSG_UNKNOWN_SMS) + " '" + text + "'", null, null);
        }
    }
//#endif
}
