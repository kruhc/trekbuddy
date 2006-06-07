// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.util;

import api.location.QualifiedCoordinates;
import api.location.LocationException;
import cz.kruch.j2se.util.StringTokenizer;
import cz.kruch.track.location.SimplifiedLocation;

public class NmeaParser {
    // component name for logging
//    private static final String COMPONENT_NAME = "NmeaParser";

    public static SimplifiedLocation parse(String nmea) throws LocationException {
        Record record = new Record();
        int index = 0;
        StringTokenizer st = new StringTokenizer(nmea, ",*", true);
        while (st.hasMoreTokens() && (index < 8)) {
            switch (index) {
                case 0: {
//                    System.out.println(COMPONENT_NAME + " [debug] checking header");
                    String token = nextToken(st, false);
                    if (!"$GPGGA".equals(token)) {
                        throw new LocationException("Invalid NMEA record");
                    }
                    index++;
                } break;
                case 1: {
//                    System.out.println(COMPONENT_NAME + " [debug] parsing timestamp");
                    String token = nextToken(st, false);
                    record.timestamp = System.currentTimeMillis(); // TODO
                    index++;
                } break;
                case 2: {
//                    System.out.println(COMPONENT_NAME + " [debug] parsing latitude");
                    String token = nextToken(st, false);
                    record.lat = Double.parseDouble(token.substring(0, 2)) + Double.parseDouble(token.substring(2)) / 60D;
                    String ns = nextToken(st, false);
/*
                    if (!(ns.startsWith("N") || ns.startsWith("S"))) {
                        return null;
                    }
*/
                    index++;
                } break;
                case 3: {
//                    System.out.println(COMPONENT_NAME + " [debug] parsing longtitude");
                    String token = nextToken(st, false);
                    record.lon = Double.parseDouble(token.substring(0, 3)) + Double.parseDouble(token.substring(3)) / 60D;
                    String ew = nextToken(st, false);
/*
                    if (!(ew.startsWith("E") || ew.startsWith("W"))) {
                        return null;
                    }
*/
                    index++;
                } break;
                case 4: {
//                    System.out.println(COMPONENT_NAME + " [debug] parsing quality");
                    String token = nextToken(st, false);
                    record.quality = Integer.parseInt(token);
                    index++;
                } break;
                case 5: {
//                    System.out.println(COMPONENT_NAME + " [debug] parsing number of satellites");
                    String token = nextToken(st, false);
                    record.satellites = Integer.parseInt(token);
                    index++;
                } break;
                case 6: {
//                    System.out.println(COMPONENT_NAME + " [debug] parsing dilution");
                    String token = nextToken(st, false);
                    record.dilution = Float.parseFloat(token);
                    index++;
                } break;
                case 7: {
//                    System.out.println(COMPONENT_NAME + " [debug] parsing altitude");
                    String token = nextToken(st, false);
                    record.altitude = Float.parseFloat(token);
                    String m = nextToken(st, false);
                    index++;
                } break;
                case 8: {
//                    System.out.println(COMPONENT_NAME + " [debug] parsing geiod height");
                    String token = nextToken(st, false);
                    if (token != null) {
                        record.geoidh = Float.parseFloat(token);
                    }
                    String m = nextToken(st, false);
                    index++;
                } break;
                case 9: {
//                    System.out.println(COMPONENT_NAME + " [debug] parsing DGPS update time");
                    String token = nextToken(st, false);
                    if (token != null) {
                        record.dgpst = Integer.parseInt(token);
                    }
                    index++;
                } break;
                case 10: {
//                    System.out.println(COMPONENT_NAME + " [debug] parsing DGPS ID");
                    record.dgpsid = nextToken(st, false);
                    index++;
                } break;
                case 11: {
//                    System.out.println(COMPONENT_NAME + " [debug] parsing checksum");
                    record.checkum = nextToken(st, true);
                    index++;
                } break;
            }
        }

        QualifiedCoordinates qualifiedCoordinates = new QualifiedCoordinates(record.lat, record.lon, record.altitude);

        return new SimplifiedLocation(qualifiedCoordinates, record.timestamp);
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

    private static class Record {
        public long timestamp;
        public double lat;
        public double lon;
        public int quality;
        public int satellites;
        public float dilution;
        public float altitude;
        public float geoidh;
        public int dgpst;
        public String dgpsid;
        public String checkum;
    }
}
