// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.util;

import api.location.LocationException;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.Date;

public final class NmeaParser {
    private static final CharArrayTokenizer tokenizer = new CharArrayTokenizer();
    private static final Record gga = new Record();
    private static final Record rmc = new Record();

    private static int day, month, year;
    private static long date;

    public static Record parseGGA(char[] nmea, int length) throws LocationException {
        int index = 0;
        CharArrayTokenizer tokenizer = NmeaParser.tokenizer;
        Record record = gga;

        // init tokenizer and record
        tokenizer.init(nmea, length, false);
        record.invalidate();

        // process
        while (tokenizer.hasMoreTokens() && (index < 10)) {
            CharArrayTokenizer.Token token = tokenizer.next();
            if (token.isEmpty()) {
                if (index == 0) {
                    throw new LocationException("GPGGA expected");
                }
            } else {
                switch (index) {
                    case 0: {
                        // TODO optimize (should never happen - remove?)
/*
                        if (!"$GPGGA".equals(token.toString())) {
                            throw new LocationException("GPGGA expected");
                        }
*/
                    } break;
                    case 1: {
                        record.timestamp = parseTime(token);
                    } break;
                    case 2: {
//                        record.lat = parseDouble(token.array, token.begin /* + 0*/, 2) + parseDouble(token.array, token.begin + 2, token.length - 2) / 60D;
                    } break;
                    case 3: {
/*
                        if (token.array[token.begin] == 'S') {
                            record.lat *= -1;
                        }
*/
                    } break;
                    case 4: {
//                        record.lon = parseDouble(token.array, token.begin /* + 0*/, 3) + parseDouble(token.array, token.begin + 3, token.length - 3) / 60D;
                    } break;
                    case 5: {
/*
                        if (token.array[token.begin] == 'W') {
                            record.lon *= -1;
                        }
*/
                    } break;
                    case 6: {
                        record.fix = CharArrayTokenizer.parseInt(token);
                        if (record.fix == 0) { // no fix
                            index = 1000; // break cycle
                        }
                    } break;
                    case 7: {
                        record.sat = CharArrayTokenizer.parseInt(token);
                    } break;
                    case 8: {
                        record.hdop = CharArrayTokenizer.parseFloat(token);
                    } break;
                    case 9: {
                        record.altitude = CharArrayTokenizer.parseFloat(token);
                    } break;
                    case 10: {
                        // 'm'
                    } break;
                    case 11: {
/* unused
                        record.geoidh = parseFloat(token);
*/
                    } break;
                    case 12: {
                        // 'm'
                    } break;
                    case 13: {
/* unused
                        record.dgpst = parseInt(token);
*/
                    } break;
                    case 14: {
/* unused
                        record.dgpsid = token.toString();
*/
                    } break;
                    case 15: {
/*
                        record.checksum = token.toString();
*/
                    } break;
                }
            }
            index++;
            token = null; // gc hint
        }

        return record;
    }

    public static Record parseRMC(char[] nmea, int length) throws LocationException {
        int index = 0;
        CharArrayTokenizer tokenizer = NmeaParser.tokenizer;
        Record record = rmc;

        // init tokenizer and record
        tokenizer.init(nmea, length, false);
        record.invalidate();

        // process
        while (tokenizer.hasMoreTokens() && (index < 10)) {
            CharArrayTokenizer.Token token = tokenizer.next();
            if (token.isEmpty()) {
                if (index == 0) {
                    throw new LocationException("GPRMC expected");
                }
            } else {
                switch (index) {
                    case 0: {
                        // TODO optimize (should never happen - remove?)
/*
                        if (!"$GPRMC".equals(token.toString())) {
                            throw new LocationException("GPRMC expected");
                        }
*/
                    } break;
                    case 1: {
                        record.timestamp = parseTime(token);
                    } break;
                    case 2: {
                        record.status = token.array[token.begin];
                    } break;
                    case 3: {
                        record.lat = CharArrayTokenizer.parseDouble(token.array, token.begin /* + 0*/, 2) + CharArrayTokenizer.parseDouble(token.array, token.begin + 2, token.length - 2) / 60D;
                    } break;
                    case 4: {
                        if (token.array[token.begin] == 'S') {
                            record.lat *= -1;
                        }
                    } break;
                    case 5: {
                        record.lon = CharArrayTokenizer.parseDouble(token.array, token.begin /* + 0*/, 3) + CharArrayTokenizer.parseDouble(token.array, token.begin + 3, token.length - 3) / 60D;
                    } break;
                    case 6: {
                        if (token.array[token.begin] == 'W') {
                            record.lon *= -1;
                        }
                    } break;
                    case 7: {
                        if (record.status == 'A') {
                            record.speed = CharArrayTokenizer.parseFloat(token);
                            if (record.speed > 0F) {
                                record.speed *= 1.852F / 3.6F;
                            }
                        }
                    } break;
                    case 8: {
                        if (record.status == 'A') {
                            record.angle = CharArrayTokenizer.parseFloat(token);
                        }
                    } break;
                    case 9: {
                        record.date = parseDate(token);
                    } break;
                    case 10: {
                        // variation
                    } break;
                    case 11: {
                        // variation W?
                    } break;
                    case 12: {
/*
                        record.checksum = token.toString();
*/
                    } break;
                }
            }
            index++;
            token = null; // gc hint
        }

        return record;
    }

    private static int parseTime(CharArrayTokenizer.Token token) {
        int tl = CharArrayTokenizer.parseInt(token.array, token.begin, 6/*token.length*/);
        final int hours = /*(int)*/ tl / 10000;
        tl -= hours * 10000;
        final int mins = /*(int)*/ tl / 100;
        tl -= mins * 100;
        final int sec = /*(int)*/ tl;
//        int ms = parseInt(token.array, token.begin + 7, 3);

        return (3600 * hours + 60 * mins + sec) * 1000/* + ms*/; // in millis
    }

    private static long parseDate(CharArrayTokenizer.Token token) {
        char[] _tarray = token.array;
        final int _tbegin = token.begin;
        final int day = 10 * (_tarray[_tbegin/* + 0*/] - '0') + _tarray[_tbegin + 1] - '0';
        final int month = 10 * (_tarray[_tbegin + 2] - '0') + _tarray[_tbegin + 3] - '0';
        final int year = 2000 + 10 * (_tarray[_tbegin + 4] - '0') + _tarray[_tbegin + 5] - '0';
        if (day != NmeaParser.day || month != NmeaParser.month || year != NmeaParser.year) {
            NmeaParser.day = day;
            NmeaParser.month = month;
            NmeaParser.year = year;
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
            calendar.setTime(new Date(0));
            calendar.set(Calendar.DAY_OF_MONTH, day);
            calendar.set(Calendar.MONTH, month - 1); // zero-based
            calendar.set(Calendar.YEAR, year);
            date = calendar.getTime().getTime();
        }

        return date;
    }

    /**
     * Holder for both GGA and/or RMC.
     */
    public static final class Record {
        // NMEA COMMON
        public int timestamp;
        public double lat, lon;
/* unused
        public String checksum;
*/
        // GGA
        public int fix;
        public int sat;
        public float hdop;
        public float altitude;
/* unused
        public float geoidh;
        public int dgpst;
        public String dgpsid;
*/
        // RMC
        public char status;
        public float speed;
        public float angle;
        public long date;

        public Record() {
            this.invalidate();
        }

        public void invalidate() {
            this.timestamp = -1;
            this.fix = this.sat = -1;
            this.hdop = this.altitude = this.speed = this.angle = -1F;
        }
    }
}
