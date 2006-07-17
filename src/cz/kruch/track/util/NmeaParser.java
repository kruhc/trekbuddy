// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.util;

import api.location.LocationException;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.NoSuchElementException;
import java.util.Date;

public final class NmeaParser {
    private static final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

    public static Record parseGGA(char[] nmea) throws LocationException {
        Record record = new Record();
        int index = 0;
        CharArrayTokenizer tokenizer = new CharArrayTokenizer(nmea, ',', false);
        while (tokenizer.hasMoreTokens() && (index < 10)) {
            Token token = tokenizer.next();
            if (token.isEmpty()) {
                index++;
                continue;
            }
            switch (index) {
                case 0: {
                    // TODO optimize (should never happen - remove?)
                    if (!"$GPGGA".equals(token.toString())) {
                        throw new LocationException("Invalid NMEA record - GPGGA expected");
                    }
                    index++;
                } break;
                case 1: {
                    record.timestamp = parseDate(token);
                    index++;
                } break;
                case 2: {
                    record.lat = parseFloat(token.array, token.begin /* + 0*/, 2) + parseFloat(token.array, token.begin + 2, token.length - 2) / 60D;
                    index++;
                } break;
                case 3: {
                    if (token.array[token.begin] == 'S') {
                        record.lat *= -1;
                    }
                    index++;
                } break;
                case 4: {
                    record.lon = parseFloat(token.array, token.begin /* + 0*/, 3) + parseFloat(token.array, token.begin + 3, token.length - 3) / 60D;
                    index++;
                } break;
                case 5: {
                    if (token.array[token.begin] == 'W') {
                        record.lon *= -1;
                    }
                    index++;
                } break;
                case 6: {
                    record.fix = parseInt(token);
                    if (record.fix == 0) { // no fix
                        index = 1000; // break cycle
                    }
                    index++;
                } break;
                case 7: {
                    record.sat = parseInt(token);
                    index++;
                } break;
                case 8: {
                    record.hdop = parseFloat(token);
                    index++;
                } break;
                case 9: {
                    record.altitude = parseFloat(token);
                    token = tokenizer.next(); // unused 'm'
                    index++;
                } break;
                case 10: {
                    record.geoidh = parseFloat(token);
                    token = tokenizer.next(); // unused 'm'
                    index++;
                } break;
                case 11: {
                    record.dgpst = parseInt(token);
                    index++;
                } break;
                case 12: {
                    record.dgpsid = token.toString();
                    index++;
                } break;
                case 13: {
                    record.checksum = token.toString();
                    index++;
                } break;
            }
        }

        return record;
    }

    public static Record parseRMC(char[] nmea) throws LocationException {
        Record record = new Record();
        int index = 0;
        CharArrayTokenizer tokenizer = new CharArrayTokenizer(nmea, ',', false);
        while (tokenizer.hasMoreTokens() && (index < 9)) {
            Token token = tokenizer.next();
            if (token.isEmpty()) {
                index++;
                continue;
            }
            switch (index) {
                case 0: {
                    // TODO optimize (should never happen - remove?)
                    if (!"$GPRMC".equals(token.toString())) {
                        throw new LocationException("Invalid NMEA record - GPRMC expected");
                    }
                    index++;
                } break;
                case 1: {
                    record.timestamp = parseDate(token);
                    index++;
                } break;
                case 2: {
                    if (token.array[token.begin] != 'A') {  // not Active, bail out
                        index = 1000;
                        record.timestamp = 0; // prevent timestamp match
                    }
                    index++;
                } break;
                case 3: {
/* we already have it from GPGGA
                    record.lat = Double.parseDouble(token.substring(0, 2)) + Double.parseDouble(token.substring(2)) / 60D;
*/
                    index++;
                } break;
                case 4: {
/* skip N/S
                    if (token.startsWith("S")) {
                        record.lat *= -1;
                    }
*/
                    index++;
                } break;
                case 5: {
/* we already have it from GPGGA
                    record.lon = Double.parseDouble(token.substring(0, 3)) + Double.parseDouble(token.substring(3)) / 60D;
*/
                    index++;
                } break;
                case 6: {
/* skip E/W
                    if (ew.startsWith("W")) {
                        record.lon *= -1;
                    }
*/
                    index++;
                } break;
                case 7: {
                    record.speed = parseFloat(token);
                    index++;
                } break;
                case 8: {
                    record.angle = parseFloat(token);
                    index++;
                } break;
                case 9: {
                    // date
                } break;
                case 10: {
                    // variation
                } break;
                case 11: {
                    record.checksum = token.toString();
                    index++;
                } break;
            }
        }

        return record;
    }

    private static long parseDate(Token token) {
        double tl = parseFloat(token.array, token.begin, token.length);
        calendar.setTime(new Date());
        int hours = (int) tl / 10000;
        tl -= hours * 10000;
        int mins = (int) tl / 100;
        tl -= mins * 100;
        int sec = (int) tl;
        int ms = (int) ((tl - sec) * 1000);
        calendar.set(Calendar.HOUR_OF_DAY, hours);
        calendar.set(Calendar.MINUTE, mins);
        calendar.set(Calendar.SECOND, sec);
        calendar.set(Calendar.MILLISECOND, ms);

        return calendar.getTime().getTime();
    }

    private static int parseInt(Token token) {
        return parseInt(token.array, token.begin, token.length);
    }

    private static int parseInt(char[] value, int offset, int length) {
        if (length == 0) {
            throw new NumberFormatException("No input");
        }

        int end = offset + length;
        int result = 0; // TODO is this correct initial value

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
                int idigit = -1;
                if (ch >= '0' && ch <= '9') {
                    idigit = ch - '0';
                }
                if (idigit > -1) {
                    float fdigit = idigit;
                    if (decSeen > 0) {
                        result += fdigit / decSeen;
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

    private final static class CharArrayTokenizer {
        private char[] array;
        private char delimiter;
        private boolean returnDelim;

        private int pos = 0;

        public CharArrayTokenizer(char[] array, char delimiter, boolean returnDelim) {
            this.array = array;
            this.delimiter = delimiter;
            this.returnDelim = returnDelim;
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
                        return new Token(array, begin, pos - begin);
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

                return new Token(array, begin, pos - begin);
            }

            throw new NoSuchElementException();
        }
    }

    private static final class Token {
        public char[] array;
        public int begin;
        public int length;

        public Token(char[] array, int begin, int length) {
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
        // GGA
        public long timestamp;
        public double lat;
        public double lon;
        public int fix = -1;
        public int sat = -1;
        public float hdop = -1F;
        public float altitude = -1F;
        public float geoidh;
        public int dgpst;
        public String dgpsid;
        // RMC
        public float speed = -1F;
        public float angle = -1F;
        // NMEA COMMON
        public String checksum;
    }
}
