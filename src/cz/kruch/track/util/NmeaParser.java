// @LICENSE@

package cz.kruch.track.util;

import api.location.LocationException;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.Date;
import java.util.Hashtable;

/**
 * NMEA parser.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class NmeaParser {
    public static final int MAX_SENTENCE_LENGTH = 128; // 82(79) max according to NMEA 0183 v3.01

    public static final int HEADER_$GP = 0x00244750;
    public static final int HEADER_GGA = 0x00474741;
    public static final int HEADER_GSA = 0x00475341;
    public static final int HEADER_GSV = 0x00475356;
    public static final int HEADER_RMC = 0x00524d43;
    public static final int HEADER_XDR = 0x00584452;

    private static final Record gga = new Record();
    private static final Record gsa = new Record();
    private static final Record rmc = new Record();
    private static final Record unknown = new Record();
    private static final CharArrayTokenizer tokenizer = new CharArrayTokenizer();
    private static final char[] delimiters = { ',', '*' };
    private static final char[] NaN = { 'N', 'a', 'N' };

    private static long date;
    private static int day, month, year;
    private static int prnc;

    private static final int HEADER_LENGTH  = 6;   // sentence header length
    private static final int MAX_SATS       = 12;

    public static final byte[] snrs  = new byte[MAX_SATS];
    public static final byte[] prns = new byte[MAX_SATS];

    public static float pdop, hdop, vdop;
    public static float geoidh;
    public static int satv, sata;
    
    public static final Hashtable xdr = new Hashtable(4);

    private NmeaParser() {
    }

    public static void reset() {
        pdop = hdop = vdop = geoidh = Float.NaN;
    }

    public static boolean validate(final char[] raw, final int length) {
        int result = 0;
        for (int i = 1; i < length; i++) {
            final byte b = (byte) (raw[i] & 0x00ff);
            if (b == '*') {
                if (length - i >= 3) { // at least 2 CRC digits 
                    byte hi = (byte) (raw[i + 1] & 0x00ff);
                    byte lo = (byte) (raw[i + 2] & 0x00ff);
                    if (hi >= '0' && hi <= '9') {
                        hi -= '0';
                    } else if (hi >= 'A' && hi <= 'F') {
                        hi -= 'A' - 10;
                    }
                    if (lo >= '0' && lo <= '9') {
                        lo -= '0';
                    } else if (lo >= 'A' && lo <= 'F') {
                        lo -= 'A' - 10;
                    }
                    return result == (hi << 4) + lo;
                }
                break;
            } else {
                result ^= b;
            }
        }

        return false;
    }

    public static int getType(final char[] sentence, final int length) {
        if (length >= HEADER_LENGTH) {
            final int $gp = sentence[0] << 16 | sentence[1] << 8 | sentence[2];
            if ($gp == HEADER_$GP) {
                return sentence[3] << 16 | sentence[4] << 8 | sentence[5];
            }
        }

        return -1;
    }

    public static Record parse(final char[] nmea, final int length) throws LocationException {
        Record result = null;

        switch (getType(nmea, length)) {
            case NmeaParser.HEADER_GGA: {
                result = parseGGA(nmea, length);
            } break;
            case NmeaParser.HEADER_GSA: {
                result = parseGSA(nmea, length);
            } break;
            case NmeaParser.HEADER_GSV: {
                parseGSV(nmea, length);
            } break;
            case NmeaParser.HEADER_RMC: {
                result = parseRMC(nmea, length);
            } break;
/* commented out since 1.0.17
            case NmeaParser.HEADER_XDR: {
                result = parseXDR(nmea, length);
            } break;
*/
        }

        if (result == null) {
            result = unknown;
            result.invalidate(-1);
        }

        return result;
    }

    private static Record parseGGA(final char[] nmea, final int length) throws LocationException {
        // local refs for faster access
        final CharArrayTokenizer tokenizer = NmeaParser.tokenizer;
        final Record record = gga;

        // init tokenizer and record
        tokenizer.init(nmea, length, delimiters, false);
        record.invalidate(HEADER_GGA);

        // process
        int index = 0;
        while (index < 12 && tokenizer.hasMoreTokens()) {
            final CharArrayTokenizer.Token token = tokenizer.next();
            /* no token empty check here */
            switch (index) {
                case 0: // $GPGGA
                    break;
                case 1: {
                    if (!token.isEmpty()) {
                        record.timestamp = parseTime(token);
                    } else {
                        index = 1000; // break cycle
                    }
                } break;
                case 2: // lat - use RMC
                case 3: // lat sign - use RMC
                case 4: // lon - use RMC
                case 5: // lon sign - use RMC
                    break;
                case 6: {
                    if (!token.isEmpty()) {
                        record.fix = CharArrayTokenizer.parseInt(token); // should not be empty
                        if (record.fix == 0 || record.fix == 6) { // invalid fix or dead reckoning
                            record.fix = 0;
                            index = 1000; // break cycle
                        }
                    } else {
                        index = 1000; // break cycle
                    }
                } break;
                case 7: {
                    if (!token.isEmpty()) {
                        record.sat = CharArrayTokenizer.parseInt(token);
                    }
                } break;
                case 8: {
                    if (!token.isEmpty()) {
                        hdop = CharArrayTokenizer.parseFloat(token);
                    } else {
                        hdop = Float.NaN;
                    }
                } break;
                case 9: {
                    if (!token.isEmpty() && !token.endsWith(NaN)) {
                        record.altitude = CharArrayTokenizer.parseFloat(token);
                    }
                } break;
                case 10: // 'm'
                    break;
                case 11: {
                    if (!token.isEmpty()) {
                        geoidh = CharArrayTokenizer.parseFloat(token);
                    } else {
                        geoidh = Float.NaN;
                    }
                } break;
            }
            index++;
        }

        return record;
    }

    private static Record parseGSA(final char[] nmea, final int length) throws LocationException {
        // local refs for faster access
        final CharArrayTokenizer tokenizer = NmeaParser.tokenizer;
        final Record record = gsa;
        final byte[] snrs = NmeaParser.snrs;
        final byte[] prns = NmeaParser.prns;

        // init tokenizer and record
        tokenizer.init(nmea, length, delimiters, false);
        record.invalidate(HEADER_GSA);

        // prn indexes
        prnc = 0;

        // process
        int index = 0;
        while (index < 18 && tokenizer.hasMoreTokens()) {
            final CharArrayTokenizer.Token token = tokenizer.next();
            /* no token empty check here */
            switch (index) {
                case 0: // $GPGSA
                    break;
                case 1: // autoselection of 2d or 3d fix - ignored
                    break;
                case 2: {
                    if (!token.isEmpty()) {
                        record.fix = CharArrayTokenizer.parseInt(token); // should not be empty
                        if (record.fix == 1) { // no fix
                            index = 1000; // break cycle
                        }
                    } else {
                        index = 1000; // break cycle
                    }
                } break;
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                case 8:
                case 9:
                case 10:
                case 11:
                case 12:
                case 13:
                case 14: {
                    if (!token.isEmpty()) {
                        record.sat++;
                        prns[prnc++] = (byte) CharArrayTokenizer.parseInt(token);
                    } 
                } break;
                case 15:
                    if (!token.isEmpty()) {
                        pdop = CharArrayTokenizer.parseFloat(token);
                    } else {
                        pdop = Float.NaN;
                    }
                    break;
                case 16: {
                    if (!token.isEmpty()) {
                        hdop = CharArrayTokenizer.parseFloat(token);
                    } else {
                        hdop = Float.NaN;
                    }
                } break;
                case 17: {
                    if (!token.isEmpty()) {
                        vdop = CharArrayTokenizer.parseFloat(token);
                    } else {
                        vdop = Float.NaN;
                    }
                } break;
            }
            index++;
        }

        // clear remaining prns & snrs
        for (int i = prnc; i < MAX_SATS; i++) {
            prns[i] = 0;
            snrs[i] = 0;
        }

        return record;
    }

    private static void parseGSV(final char[] nmea, final int length) throws LocationException {
        // local refs for faster access
        final CharArrayTokenizer tokenizer = NmeaParser.tokenizer;
        final byte[] prns = NmeaParser.prns;
        final byte[] snrs = NmeaParser.snrs;

        // init tokenizer and record
        tokenizer.init(nmea, length, delimiters, false);

        // local vars
        int sentence = 0, maxi = 20;
        int tracked = -1, prn = -1;

        // process
        int index = 0;
        while (index < maxi && tokenizer.hasMoreTokens()) {
            final CharArrayTokenizer.Token token = tokenizer.next();
            if (!token.isEmpty()) {
                switch (index) {
                    case 0: // $GPGSV
                        break;
                    case 1: // number of sentences
                        break;
                    case 2: { // current sentence
                        sentence = CharArrayTokenizer.parseInt(token);
                        if (sentence == 1) {
                            sata = 0;
                        }
                    } break;
                    case 3: { // number of sats in view
                        satv = CharArrayTokenizer.parseInt(token);
                        maxi = 4 /* start offset */ + (satv > sentence * 4 ? 16 : (satv - (sentence - 1) * 4) * 4);
                    } break;
                    default: {
                        final int mod = index % 4;
                        switch (mod) {
                            case 0: {
                                tracked = -1;
                                prn = CharArrayTokenizer.parseInt(token); // should not be empty
                                for (int i = prnc; --i >= 0; ) {
                                    if (prn == prns[i]) {
                                        tracked = i;
                                        break;
                                    }
                                }
                            } break;
                            case 3: {
                                int snr = (CharArrayTokenizer.parseInt(token) - 15) / 3; // 'normalization'
                                if (snr < 1/*0*/) {
                                    snr = 1/*0*/;
                                } else if (snr > 9) {
                                    snr = 9;
                                }
                                if (tracked != -1) {
                                    snrs[tracked] = (byte) snr;
                                } else if (prnc == 0) { // no GSA
                                    if (sata < MAX_SATS) {
                                        prns[sata] = (byte) prn;
                                        snrs[sata] = (byte) snr;
                                        sata++;
                                    }
                                }
                            } break;
                        }
                    }
                }
            }
            index++;
        }
    }

    private static Record parseRMC(final char[] nmea, final int length) throws LocationException {
        // local refs for faster access
        final CharArrayTokenizer tokenizer = NmeaParser.tokenizer;
        final Record record = rmc;

        // init tokenizer and record
        tokenizer.init(nmea, length, delimiters, false);
        record.invalidate(HEADER_RMC);

        // process
        int index = 0;
        while (index < 10 && tokenizer.hasMoreTokens()) {
            final CharArrayTokenizer.Token token = tokenizer.next();
            if (!token.isEmpty()) {
                switch (index) {
                    case 0: // $GPRMC
                        break;
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
                        record.speed = CharArrayTokenizer.parseFloat(token) * (1.852F / 3.6F);
                    } break;
                    case 8: {
                        record.course = CharArrayTokenizer.parseFloat(token);
                    } break;
                    case 9: {
                        record.date = parseDate(token);
                    } break;
                    case 10: {
                        // variation - unused
                    } break;
                }
            }
            index++;
        }

        return record;
    }

    private static Record parseXDR(final char[] nmea, final int length) throws LocationException {
        // local refs for faster access
        final CharArrayTokenizer tokenizer = NmeaParser.tokenizer;

        // init tokenizer and record
        tokenizer.init(nmea, length, delimiters, false);
        unknown.invalidate(HEADER_XDR);

        // local vars
        float value = Float.NaN;

        // process
        int index = 0;
        while (index < 17 /* 4 measurements max */ && tokenizer.hasMoreTokens()) {
            final CharArrayTokenizer.Token token = tokenizer.next();
            /* no token empty check here */
            switch (index) {
                case 0: // $GPXDR
                    break;
                default: {
                    final int mod = (index - 1) % 4;
                    switch (mod) {
                        case 0:
                            // type - unused
                            break;
                        case 1: { // value
                            value = CharArrayTokenizer.parseFloat(token);
                        } break;
                        case 2:
                            // units - unused
                            break;
                        case 3: { // id
                            xdr.put(token.toString(), new Float(value));
                        } break;
                    }
                }
            }
            index++;
        }

        return unknown;
    }

    private static int parseTime(final CharArrayTokenizer.Token token) {
        int tl = CharArrayTokenizer.parseInt(token.array, token.begin, 6/*token.length*/);
        final int hours = tl / 10000;
        tl -= hours * 10000;
        final int mins = tl / 100;
        tl -= mins * 100;
        final int sec = tl;
        int ms = 0;
        if (token.length > 7) {
            ms = (token.array[token.begin + 7] - '0') * 100;
        }
        if (cz.kruch.track.configuration.Config.nmeaMsExact) {
            if (token.length > 8) {
                ms += (token.array[token.begin + 8] - '0') * 10;
            }
            if (token.length > 9) {
                ms += (token.array[token.begin + 9] - '0');
            }
        }

        return (3600 * hours + 60 * mins + sec) * 1000 + ms;
    }

    private static long parseDate(final CharArrayTokenizer.Token token) {
        final char[] _tarray = token.array;
        final int _tbegin = token.begin;
        final int day = 10 * (_tarray[_tbegin/* + 0*/] - '0') + _tarray[_tbegin + 1] - '0';
        final int month = 10 * (_tarray[_tbegin + 2] - '0') + _tarray[_tbegin + 3] - '0';
        final int year = 2000 + 10 * (_tarray[_tbegin + 4] - '0') + _tarray[_tbegin + 5] - '0';
        if (day != NmeaParser.day || month != NmeaParser.month || year != NmeaParser.year) {
            NmeaParser.day = day;
            NmeaParser.month = month;
            NmeaParser.year = year;
            final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
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
        // TYPE
        public int type;

        // NMEA COMMON
        public int timestamp;
        public double lat, lon;

        // GGA/GSA
        public int fix;
        public int sat;
        public float altitude;

        // RMC
        public char status;
        public float speed;
        public float course;
        public long date;

        public Record() {
            this.invalidate(-1);
        }

        private void invalidate(final int type) {
            this.type = type;
            this.timestamp = this.fix = this.sat = 0;
            this.lat = this.lon = Double.NaN;
            this.altitude = this.speed = this.course = Float.NaN;
            this.status = '?';
        }

        public static Record copyGsaIntoGga(final Record gsa) {
            gga.invalidate(HEADER_GGA);
            gga.sat = gsa.sat;

            return gga;
        }
    }
}
