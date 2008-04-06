/*
 * Copyright 2006-2007 Ales Pour <kruhc@seznam.cz>.
 * All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package cz.kruch.track.util;

import api.location.LocationException;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.Date;

/**
 * NMEA parser.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class NmeaParser {
    private static final Record gga = new Record();
    private static final Record gsa = new Record();
    private static final Record rmc = new Record();
    private static final CharArrayTokenizer tokenizer = new CharArrayTokenizer();
    private static final char[] delimiters = { ',', '*' };

    private static long date;
    private static int day, month, year;
    private static int prnc;

    private static final int MAX_SATS = 12;

    public static final byte[] snrs  = new byte[MAX_SATS];
    public static final byte[] prns = new byte[MAX_SATS];

    public static float pdop = -1F, hdop = -1F, vdop = -1F;
    public static int satv;

    public static Record parseGGA(final char[] nmea, final int length) throws LocationException {
        // local refs for faster access
        final CharArrayTokenizer tokenizer = NmeaParser.tokenizer;
        final Record record = gga;

        // init tokenizer and record
        tokenizer.init(nmea, length, false);
        record.invalidate();

        // process
        int index = 0;
        while ((index < 10) && tokenizer.hasMoreTokens()) {
            final CharArrayTokenizer.Token token = tokenizer.next();
            if (!token.isEmpty()) {
                switch (index) {
                    case 0: // $GPGGA
                        break;
                    case 1: {
                        record.timestamp = parseTime(token);
                    } break;
                    case 2: // lat - use RMC
                    case 3: // lat sign - use RMC
                    case 4: // lon - use RMC
                    case 5: // lon sign - use RMC
                        break;
                    case 6: {
                        record.fix = CharArrayTokenizer.parseInt(token);
                        switch (record.fix) {
                            case 0:
                            case 6: // dead reckoning -> ignore
                                record.fix = 0;
                                index = 666; // breaks 'while' cycle
                                break;
                        }
                    } break;
                    case 7: {
                        record.sat = CharArrayTokenizer.parseInt(token);
                    } break;
                    case 8: {
/* global
                        record.hdop = CharArrayTokenizer.parseFloat(token);
*/
                        hdop = CharArrayTokenizer.parseFloat(token);
                    } break;
                    case 9: {
                        record.altitude = CharArrayTokenizer.parseFloat(token);
                    } break;
                    case 10: // 'm'
                        break;
                    case 11: {
/* unused
                        record.geoidh = parseFloat(token);
*/
                    } break;
                    case 12: // 'm'
                        break;
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
                }
            }
            index++;
        }

        return record;
    }

    public static Record parseGSA(final char[] nmea, final int length) throws LocationException {
        // local refs for faster access
        final CharArrayTokenizer tokenizer = NmeaParser.tokenizer;
        final Record record = gsa;

        // init tokenizer and record
        tokenizer.init(nmea, length, delimiters, false);
        record.invalidate();

        // prn indexes
        prnc = 0;

        // process
        int index = 0;
        while ((index < 18) && tokenizer.hasMoreTokens()) {
            final CharArrayTokenizer.Token token = tokenizer.next();
            /* no token empty check here */
            switch (index) {
                case 0: // $GPGSA
                    break;
                case 1: // autoselection of 2d or 3d fix - ignored
                    break;
                case 2: {
                    record.fix = CharArrayTokenizer.parseInt(token); // should not be empty
                    if (record.fix == 1) { // no fix
                        index = 666; // break cycle
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
/*
                        record.pdop = CharArrayTokenizer.parseFloat(token);
*/
                        pdop = CharArrayTokenizer.parseFloat(token);
                    }
                    break;
                case 16: {
                    if (!token.isEmpty()) {
/*
                        record.hdop = CharArrayTokenizer.parseFloat(token);
*/
                        hdop = CharArrayTokenizer.parseFloat(token);
                    }
                } break;
                case 17: {
                    if (!token.isEmpty()) {
/*
                        record.vdop = CharArrayTokenizer.parseFloat(token);
*/
                        vdop = CharArrayTokenizer.parseFloat(token);
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

    public static void parseGSV(final char[] nmea, final int length) throws LocationException {
        // local refs for faster access
        final CharArrayTokenizer tokenizer = NmeaParser.tokenizer;
        final byte[] snrs = NmeaParser.snrs;

        // init tokenizer and record
        tokenizer.init(nmea, length, delimiters, false);

        // local vars
        int sentence = 0, maxi = 20;
        int tracked = -1;

        // process
        int index = 0;
        while ((index < maxi) && tokenizer.hasMoreTokens()) {
            final CharArrayTokenizer.Token token = tokenizer.next();
            /* no token empty check here */
            switch (index) {
                case 0: // $GPGSV
                    break;
                case 1: // number of sentences
                    break;
                case 2: // current sentence
                    sentence = CharArrayTokenizer.parseInt(token);
                    break;
                case 3: // number of sats in view
                    satv = CharArrayTokenizer.parseInt(token);
                    maxi = 4 /* start offset */ + (satv > sentence * 4 ? 16 : (satv - (sentence - 1) * 4) * 4);
                    break;
                default: {
                    final int mod = index % 4;
                    switch (mod) {
                        case 0: {
                            tracked = -1;
                            final int prn = CharArrayTokenizer.parseInt(token);
                            for (int i = prnc; --i >= 0; ) {
                                if (prn == prns[i]) {
                                    tracked = i;
                                    break;
                                }
                            }
                        } break;
                        case 3: {
                            if (tracked != -1) {
                                if (!token.isEmpty()) {
                                    int snr = (CharArrayTokenizer.parseInt(token) - 15) / 3;
                                    if (snr < 1/*0*/) {
                                        snr = 1/*0*/;
                                    } else if (snr > 9) {
                                        snr = 9;
                                    }
                                    snrs[tracked] = (byte) snr;
                                }
                            }
                        } break;
                    } break;
                }
            }
            index++;
        }
    }

    public static Record parseRMC(final char[] nmea, final int length) throws LocationException {
        // local refs for faster access
        final CharArrayTokenizer tokenizer = NmeaParser.tokenizer;
        final Record record = rmc;

        // init tokenizer and record
        tokenizer.init(nmea, length, false);
        record.invalidate();

        // process
        int index = 0;
        while ((index < 10) && tokenizer.hasMoreTokens()) {
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
                        record.speed = CharArrayTokenizer.parseFloat(token);
                        if (record.speed > 0F) {
                            record.speed *= 1.852F / 3.6F;
                        }
                    } break;
                    case 8: {
                        record.angle = CharArrayTokenizer.parseFloat(token);
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

    private static int parseTime(final CharArrayTokenizer.Token token) {
        int tl = CharArrayTokenizer.parseInt(token.array, token.begin, 6/*token.length*/);
        final int hours = /*(int)*/ tl / 10000;
        tl -= hours * 10000;
        final int mins = /*(int)*/ tl / 100;
        tl -= mins * 100;
        final int sec = /*(int)*/ tl;
//        int ms = parseInt(token.array, token.begin + 7, 3);

        return (3600 * hours + 60 * mins + sec) * 1000/* + ms*/; // in millis
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
        // NMEA COMMON
        public int timestamp;
        public double lat, lon;
/* unused
        public String checksum;
*/
        // GGA/GSA
        public int fix;
        public int sat;
/* global
        public float pdop, hdop, vdop;
*/
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

        private void invalidate() {
            this.timestamp = -1;
            this.lat = this.lon = Double.NaN;
            this.fix = this.sat = -1;
/*
            this.pdop = this.hdop = this.vdop = -1F;
*/
            this.altitude = this.speed = this.angle = Float.NaN;
            this.status = '?';
        }

        public static Record copyGsaIntoGga(final Record gsa) {
            gga.invalidate();
            gga.sat = gsa.sat;
            switch (gsa.fix) {
                case 1:
                    gga.fix = 0;
                break;
                case 2:
                case 3:
                    gga.fix = 1;
                break;
            }
/* global
            gga.pdop = gsa.pdop;
            gga.hdop = gsa.hdop;
            gga.vdop = gsa.vdop;
*/

            return gga;
        }
    }
}
