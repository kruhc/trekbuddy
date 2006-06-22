// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.util;

import api.location.QualifiedCoordinates;
import api.location.LocationException;
import api.location.Location;
import cz.kruch.j2se.util.StringTokenizer;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.Date;

public class NmeaParser {
    private static final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

    public static Location parse(String nmea) throws LocationException {
        Record record = new Record();
        int index = 0;
        StringTokenizer st = new StringTokenizer(nmea, ",*", true);
        while (st.hasMoreTokens() && (index < 8)) {
            switch (index) {
                case 0: {
                    String token = nextToken(st, false);
                    if (!"$GPGGA".equals(token)) {
                        throw new LocationException("Invalid NMEA record");
                    }
                    index++;
                } break;
                case 1: {
                    String token = nextToken(st, false);
                    record.timestamp = parseDate(token);
                    index++;
                } break;
                case 2: {
                    String token = nextToken(st, false);
                    record.lat = Double.parseDouble(token.substring(0, 2)) + Double.parseDouble(token.substring(2)) / 60D;
                    String ns = nextToken(st, false);
                    if (ns.startsWith("S")) {
                        record.lat *= -1;
                    }
                    index++;
                } break;
                case 3: {
                    String token = nextToken(st, false);
                    record.lon = Double.parseDouble(token.substring(0, 3)) + Double.parseDouble(token.substring(3)) / 60D;
                    String ew = nextToken(st, false);
                    if (ew.startsWith("W")) {
                        record.lon *= -1;
                    }
                    index++;
                } break;
                case 4: {
                    String token = nextToken(st, false);
                    record.fix = Integer.parseInt(token);
                    index++;
                } break;
                case 5: {
                    String token = nextToken(st, false);
                    record.sat = Integer.parseInt(token);
                    index++;
                } break;
                case 6: {
                    String token = nextToken(st, false);
                    record.dilution = Float.parseFloat(token);
                    index++;
                } break;
                case 7: {
                    String token = nextToken(st, false);
                    record.altitude = Float.parseFloat(token);
                    String m = nextToken(st, false); // unused
                    index++;
                } break;
                case 8: {
                    String token = nextToken(st, false);
                    if (token != null) {
                        record.geoidh = Float.parseFloat(token);
                    }
                    String m = nextToken(st, false);
                    index++;
                } break;
                case 9: {
                    String token = nextToken(st, false);
                    if (token != null) {
                        record.dgpst = Integer.parseInt(token);
                    }
                    index++;
                } break;
                case 10: {
                    record.dgpsid = nextToken(st, false);
                    index++;
                } break;
                case 11: {
                    record.checkum = nextToken(st, true);
                    index++;
                } break;
            }
        }

        QualifiedCoordinates coordinates = new QualifiedCoordinates(record.lat, record.lon, record.altitude);

        return new Location(coordinates, record.timestamp, record.fix, record.sat);
    }

    private static String nextToken(StringTokenizer st, boolean last) {
        String s = st.nextToken();
        if (",".equals(s)) {
            return null;
        }
        if (!last) {
            st.nextToken(); // ","
        }

        return s;
    }

    private static long parseDate(String ts) {
        int tl = Double.valueOf(ts).intValue();
        calendar.setTime(new Date(System.currentTimeMillis()));
        int hours = tl / 10000;
        tl -= hours * 10000;
        int mins = tl / 100;
        tl -= mins * 100;
        int sec = tl;
        calendar.set(Calendar.HOUR_OF_DAY, hours);
        calendar.set(Calendar.MINUTE, mins);
        calendar.set(Calendar.SECOND, sec);

        return calendar.getTime().getTime();
    }

    private static class Record {
        public long timestamp;
        public double lat;
        public double lon;
        public int fix = -1;
        public int sat = -1;
        public float dilution;
        public float altitude = -1;
        public float geoidh;
        public int dgpst;
        public String dgpsid;
        public String checkum;
    }
}
