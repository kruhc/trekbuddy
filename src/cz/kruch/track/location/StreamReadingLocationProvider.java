/*
 * Copyright 2006-2007 Ales Pour <kruhc@seznam.cz>. All Rights Reserved.
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

package cz.kruch.track.location;

import api.location.LocationProvider;
import api.location.Location;
import api.location.QualifiedCoordinates;
import api.location.LocationException;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import cz.kruch.track.util.NmeaParser;

/**
 * Base class for Serial and Simulator location provider.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public abstract class StreamReadingLocationProvider extends LocationProvider {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("StreamReadingLocationProvider");
//#endif

/*
    private static final char[] HEADER_GGA = { '$', 'G', 'P', 'G', 'G', 'A' };
    private static final char[] HEADER_GSA = { '$', 'G', 'P', 'G', 'S', 'A' };
    private static final char[] HEADER_GSV = { '$', 'G', 'P', 'G', 'S', 'V' };
    private static final char[] HEADER_RMC = { '$', 'G', 'P', 'R', 'M', 'C' };
*/
    private static final int HEADER_GGA = 0x00474741;
    private static final int HEADER_GSA = 0x00475341;
    private static final int HEADER_GSV = 0x00475356;
    private static final int HEADER_RMC = 0x00524d43;

    private static final int HEADER_LENGTH = 6;
    private static final int BUFFER_SIZE = 512; // as recommended at Nokia forum

    public static int syncs, mismatches, checksums, restarts, stalls, errors;

    private final char[] line;
    private final byte[] btline;
    private int btlineOffset, btlineCount;

    /* for minimalistic NMEA stream */
    private int hack_rmc_count;
    private NmeaParser.Record gsa;

    protected StreamReadingLocationProvider(String name) {
        super(name);
        this.line = new char[BUFFER_SIZE];
        this.btline = new byte[BUFFER_SIZE];
        syncs = mismatches = checksums = restarts = stalls = errors = 0;
    }

    protected void reset() {
        btlineOffset = btlineCount = hack_rmc_count = 0;
        gsa = null;
    }

    protected final Location nextLocation(InputStream in, OutputStream observer) throws IOException, LocationException {
        // records
        NmeaParser.Record gga = null;
        NmeaParser.Record rmc = null;

        // get sentence pair
        while (gga == null || rmc == null) {

            // get sentence
            final int l = nextSentence(in, observer);
            if (l == -1) {
                return null;
            }
                           
            // checksum check
            if (validate(line, l)) {
                // parse known sentences
                switch (getType(line, l)) {
                    case HEADER_GGA: {
                        gga = NmeaParser.parseGGA(line, l);
                        hack_rmc_count = 0;
                    } break;
                    case HEADER_GSA: {
                        gsa = NmeaParser.parseGSA(line, l);
                    } break;
                    case HEADER_GSV: {
                        NmeaParser.parseGSV(line, l);
                    } break;
                    case HEADER_RMC: {
                        rmc = NmeaParser.parseRMC(line, l);
                        hack_rmc_count++;
                    } break;
                    default:
                        continue;
                }
            } else {
//#ifdef __LOG__
                if (log.isEnabled()) log.warn("Invalid NMEA!");
//#endif
                checksums++;
                continue;
            }

            // hack
            if (gsa != null) {
                if (hack_rmc_count >= 3) { // use GSA as GGA (alt missing, though)
                    gga = NmeaParser.Record.copyGsaIntoGga(gsa);
                    if (rmc != null) {
                        gga.timestamp = rmc.timestamp;
                    }
                } else if (gga != null) {
                    if (gsa.fix != 3) { // not 3D fix - altitude is invalid
                        gga.altitude = Float.NaN;
                    }
/* global
                    gga.vdop = gsa.vdop;
*/
                }
            }

            // sync
            if (rmc != null && gga != null) {
                int i = rmc.timestamp - gga.timestamp;
                if (i > 0) {
                    gga = null;
                    syncs++;
                } else if (i < 0) {
                    rmc = null;
                    syncs++;
                }
            }
        }

        // new location
        Location location;

        // combine
        final long datetime = rmc.date + rmc.timestamp;
        if (rmc.timestamp == gga.timestamp) {
            QualifiedCoordinates qc = QualifiedCoordinates.newInstance(rmc.lat, rmc.lon, gga.altitude);
            qc.setHorizontalAccuracy(/*gga.hdop*/NmeaParser.hdop * 5);
            qc.setVerticalAccuracy(/*gga.vdop*/NmeaParser.vdop * 5);
            location = Location.newInstance(qc, datetime, rmc.status == 'A' ? gga.fix : 0, gga.sat);
        } else {
            location = Location.newInstance(QualifiedCoordinates.newInstance(rmc.lat, rmc.lon),
                                            datetime, rmc.status == 'A' ? 1 : 0);
            mismatches++;
        }
        location.setFix3d(gsa != null && gsa.fix == 3);
        location.setCourse(rmc.angle);
        location.setSpeed(rmc.speed);

        return location;
    }

    private int nextSentence(InputStream in, OutputStream observer) throws IOException {
        int pos = 0;
        int c = 0;

        boolean nl = false;
        boolean match = false;

        final char[] line = this.line;
        final byte[] btline = this.btline;

        while (c > -1) {

            // need new data?
            if (btlineOffset == btlineCount) {

                // read from stream
                btlineCount = in.read(btline);
                if (btlineCount == -1) {
                    c = -1;
                    break;
                }
                btlineOffset = 0;

                // NMEA log
                if (observer != null) {
                    try {
                        observer.write(btline, 0, btlineCount);
                    } catch (Throwable t) {
                        // ignore
                    }
                }
            }

            // next char
            c = btline[btlineOffset++];

            // beginning of NMEA sentence?
            if (c == '$') {
                pos = 0;
                match = true; // lie, for now :-)
            }

            // header already matched or not yet enough data for header check
            if (match) {
                final char ch = (char) c;
                nl = (ch == '\n' || ch == '\r');

                if (nl) break;

                // add char to array
                line[pos++] = ch;

                // weird content check
                if (pos >= BUFFER_SIZE) {

                    // record state
                    setThrowable(new LocationException("NMEA sentence too long"));

                    // reset
                    pos = 0;
                    match = false;
                }
            }
        }

        if (nl) {
            return pos;
        }

        if (c == -1) {
            setStatus("End of stream");
        }

        return -1;
    }

    private static boolean validate(final char[] raw, final int length) {
        int result = 0;
        for (int i = 1; i < length; i++) {
            final byte b = (byte) (raw[i] & 0x00ff);
            if (b == '*') {
                if (length - i >= 2) {
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

    private static int getType(final char[] sentence, final int length) {
        if (length < 6) {
            return -1;
        }

        return sentence[3] << 16 | sentence[4] << 8 | sentence[5];
    }
}
