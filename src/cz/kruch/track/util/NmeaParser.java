// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.util;

import api.location.LocationException;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.NoSuchElementException;
import java.util.Date;

public final class NmeaParser {
    private static final Calendar CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
    private static final Date DATE_0 = new Date(0);
    private static final CharArrayTokenizer tokenizer = new CharArrayTokenizer();

    public static Record parseGGA(char[] nmea) throws LocationException {
        Record record = new Record();
        int index = 0;

        // init tokenizer with current data
        tokenizer.init(nmea, ',', false);

        // process
        while (tokenizer.hasMoreTokens() && (index < 10)) {
            Token token = tokenizer.next();
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
                        record.fix = parseInt(token);
                        if (record.fix == 0) { // no fix
                            index = 1000; // break cycle
                        }
                    } break;
                    case 7: {
                        record.sat = parseInt(token);
                    } break;
                    case 8: {
                        record.hdop = parseFloat(token);
                    } break;
                    case 9: {
                        record.altitude = parseFloat(token);
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
                        record.checksum = token.toString();
                    } break;
                }
            }
            index++;
            token = null; // gc hint
        }

        return record;
    }

    public static Record parseRMC(char[] nmea) throws LocationException {
        Record record = new Record();
        int index = 0;

        // init tokenizer with current data
        tokenizer.init(nmea, ',', false);

        // process
        while (tokenizer.hasMoreTokens() && (index < 10)) {
            Token token = tokenizer.next();
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
                        record.lat = parseDouble(token.array, token.begin /* + 0*/, 2) + parseDouble(token.array, token.begin + 2, token.length - 2) / 60D;
                    } break;
                    case 4: {
                        if (token.array[token.begin] == 'S') {
                            record.lat *= -1;
                        }
                    } break;
                    case 5: {
                        record.lon = parseDouble(token.array, token.begin /* + 0*/, 3) + parseDouble(token.array, token.begin + 3, token.length - 3) / 60D;
                    } break;
                    case 6: {
                        if (token.array[token.begin] == 'W') {
                            record.lon *= -1;
                        }
                    } break;
                    case 7: {
                        if (record.status == 'A') {
                            record.speed = parseFloat(token);
                        }
                    } break;
                    case 8: {
                        if (record.status == 'A') {
                            record.angle = parseFloat(token);
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
                        record.checksum = token.toString();
                    } break;
                }
            }
            index++;
            token = null; // gc hint
        }

        return record;
    }

    private static int parseTime(Token token) {
        int tl = parseInt(token.array, token.begin, 6/*token.length*/);
        int hours = /*(int)*/ tl / 10000;
        tl -= hours * 10000;
        int mins = /*(int)*/ tl / 100;
        tl -= mins * 100;
        int sec = /*(int)*/ tl;
//        int ms = parseInt(token.array, token.begin + 7, 3);

        return (3600 * hours + 60 * mins + sec) * 1000/* + ms*/; // in millis
    }

    private static long parseDate(Token token) {
        char[] _tarray = token.array;
        int _tbegin = token.begin;
        int day = 10 * (_tarray[_tbegin + 0] - '0') + _tarray[_tbegin + 1] - '0';
        int month = 10 * (_tarray[_tbegin + 2] - '0') + _tarray[_tbegin + 3] - '0';
        int year = 2000 + 10 * (_tarray[_tbegin + 4] - '0') + _tarray[_tbegin + 5] - '0';
        CALENDAR.setTime(DATE_0);
        CALENDAR.set(Calendar.DAY_OF_MONTH, day);
        CALENDAR.set(Calendar.MONTH, month - 1); // zero-based
        CALENDAR.set(Calendar.YEAR, year);

        return CALENDAR.getTime().getTime();
    }

    private static int parseInt(Token token) {
        return parseInt(token.array, token.begin, token.length);
    }

    public static int parseInt(char[] value, int offset, int length) {
        if (length == 0) {
            throw new NumberFormatException("No input");
        }

        int end = offset + length;
        int result = 0; // TODO is this correct initial value???

        while (offset < end) {
            char ch = value[offset++];
/* too slow
            int digit = Character.digit(ch, 10);
*/
            int digit = -1;
            if (ch >= '0' && ch <= '9') {
                digit = ch - '0';
            }
            if (digit > -1) {
                result *= 10;
                result += digit;
            } else {
                throw new NumberFormatException("Not a digit: " + ch);
            }
        }

        return result;
    }

    private static float parseFloat(Token token) {
        return parseFloat(token.array, token.begin, token.length);
    }

    private static float parseFloat(char[] value, int offset, int length) {
        if (length == 0) {
            throw new NumberFormatException("No input");
        }

        int decSeen = 0;
        int end = offset + length;
        float result = 0F; // TODO is this correct initial value

        while (offset < end) {
            char ch = value[offset++];
            if (ch == '.') {
                decSeen = 10;
            } else {
/* too slow
                int idigit = Character.digit(ch, 10);
*/
                if (ch >= '0' && ch <= '9') {
                    float fdigit = ch - '0';
                    if (decSeen > 0) {
                        result += (fdigit / decSeen);
                        decSeen *= 10;
                    } else {
                        result *= 10F;
                        result += fdigit;
                    }
                } else {
                    throw new NumberFormatException("Not a digit: " + ch);
                }
            }
        }

        return result;
    }

    public static double parseDouble(char[] value, int offset, int length) {
        if (length == 0) {
            throw new NumberFormatException("No input");
        }

        int decSeen = 0;
        int end = offset + length;
        double result = 0D; // TODO is this correct initial value?

        while (offset < end) {
            char ch = value[offset++];
            if (ch == '.') {
                decSeen = 10;
            } else {
/* too slow
                int idigit = Character.digit(ch, 10);
*/
                if (ch >= '0' && ch <= '9') {
                    double fdigit = ch - '0';
                    if (decSeen > 0) {
                        result += (fdigit / decSeen);
                        decSeen *= 10;
                    } else {
                        result *= 10D;
                        result += fdigit;
                    }
                } else {
                    throw new NumberFormatException("Not a digit: " + ch);
                }
            }
        }

        return result;
    }

    private final static class CharArrayTokenizer {
        private char[] array;
        private char delimiter;
        private boolean returnDelim;

        private int pos = 0;

        private Token token;

        public CharArrayTokenizer() {
            this.token = new Token();
        }

        public void init(char[] array, char delimiter, boolean returnDelim) {
            this.array = array;
            this.delimiter = delimiter;
            this.returnDelim = returnDelim;
            this.pos = 0;
        }

        public boolean hasMoreTokens() {
            return pos < array.length;
        }

        public Token next() {
            if (hasMoreTokens()) {
                int begin = pos;
                if (array[pos] == delimiter) {
                    pos++;
                    if (returnDelim) {
                        // init the token with valid data
                        token.init(array, begin, pos - begin);

                        // return
                        return token;
                    } else {
                        begin++;
                    }
                }
                int i = pos;
                while (i < array.length) {
                    char ch = array[i];
                    if (ch == delimiter) {
                        break;
                    } else {
                        i++;
                    }
                }
                pos = i;

                // init the token with valid data
                token.init(array, begin, pos - begin);

                return token;
            }

            throw new NoSuchElementException();
        }
    }

    private static final class Token {
        public char[] array;
        public int begin;
        public int length;

        public void init(char[] array, int begin, int length) {
            this.array = array;
            this.begin = begin;
            this.length = length;
        }

        public boolean isEmpty() {
            return length == 0;
        }

        public String toString() {
            return new String(array, begin, length);
        }
    }

    /**
     * Holder for both GGA and/or RMC.
     */
    public static final class Record {
        // NMEA COMMON
        public int timestamp;
        public double lat, lon;
        public String checksum;
        // GGA
        public int fix = -1;
        public int sat = -1;
        public float hdop = -1F;
        public float altitude = -1F;
/* unused
        public float geoidh;
        public int dgpst;
        public String dgpsid;
*/
        // RMC
        public char status;
        public float speed = -1F;
        public float angle = -1F;
        public long date;
    }
}
