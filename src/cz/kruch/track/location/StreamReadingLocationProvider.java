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
 * Base class for Serial/Bluetooth and Simulator providers.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
abstract class StreamReadingLocationProvider extends LocationProvider {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Stream");
//#endif

    private static final int INPUT_BUFFER_SIZE      = 16;  // 512; // as recommended at Nokia forum

    private final char[] line;
    private final byte[] btline;
    private int btlineOffset, btlineCount;

    protected volatile long lastIO;

    /* for minimalistic NMEA stream */
    private int hack_rmc_count;
    private NmeaParser.Record gsa;

    /* for broken streams */
    private boolean hack_repeat;

    protected StreamReadingLocationProvider(String name) {
        super(name);
        this.line = new char[NmeaParser.MAX_SENTENCE_LENGTH];
        this.btline = new byte[INPUT_BUFFER_SIZE];
    }

    protected void reset() {
        btlineOffset = btlineCount = hack_rmc_count = 0;
        hack_repeat = false;
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
//#ifdef __LOG__
                if (log.isEnabled()) log.warn("end of stream");
//#endif
                return null;
            }
                           
            // checksum check
            if (NmeaParser.validate(line, l)) {

                // parse known sentences
                final NmeaParser.Record record = NmeaParser.parse(line, l);
                switch (record.type) {
                    case NmeaParser.HEADER_GGA: {
//#ifdef __LOG__
                        if (log.isEnabled()) log.info("got GGA");
//#endif
                        gga = record;
                        hack_rmc_count = 0;
                    } break;
                    case NmeaParser.HEADER_GSA: {
//#ifdef __LOG__
                        if (log.isEnabled()) log.info("got GSA");
//#endif
                        gsa = record;
                    } break;
                    case NmeaParser.HEADER_RMC: {
//#ifdef __LOG__
                        if (log.isEnabled()) log.info("got RMC");
//#endif
                        rmc = record;
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
                }
            }

            // sync
            if (rmc != null && gga != null) {
                final int i = rmc.timestamp - gga.timestamp;
                if (i > 0) {
                    gga = null;
                    syncs++;
//#ifdef __LOG__
                    if (log.isEnabled()) log.warn("sync error");
//#endif
                } else if (i < 0) {
                    rmc = null;
                    syncs++;
//#ifdef __LOG__
                    if (log.isEnabled()) log.warn("sync error");
//#endif
                }
            }
        }

        // alt correction
        if (gsa != null) {
            if (gsa.fix != 3) { // not 3D fix - altitude is invalid
                gga.altitude = Float.NaN;
            }
/* global
            gga.vdop = gsa.vdop;
*/
        }

        // new location
        final Location location;

        // combine
        final long datetime = rmc.date + rmc.timestamp;
        if (rmc.timestamp == gga.timestamp) {
            final QualifiedCoordinates qc = QualifiedCoordinates.newInstance(rmc.lat, rmc.lon, gga.altitude);
            qc.setHorizontalAccuracy(/*gga.hdop*/NmeaParser.hdop * 5);
            qc.setVerticalAccuracy(/*gga.vdop*/NmeaParser.vdop * 5);
            location = Location.newInstance(qc, datetime, rmc.status == 'A' ? gga.fix : 0, gga.sat);
        } else {
//#ifdef __LOG__
            if (log.isEnabled()) log.warn("unpaired sentences");
//#endif
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

        while (c != -1) {

            // need new data?
            if (btlineOffset == btlineCount) {

                // read from stream
                final int n = in.read(btline);

                // end of stream?
                if (n == -1) {
                    if (hack_repeat) { // already tried once
                        c = -1;
                        break;
                    } else { // try read again
                        hack_repeat = true;
                        continue;
                    }
                }

                // use count
                btlineCount = n;

                // starting at the beginning
                btlineOffset = 0;

                // update last I/O timestamp
                lastIO = System.currentTimeMillis();

                // NMEA log
                if (observer != null) {
                    try {
                        observer.write(btline, 0, n);
                    } catch (Throwable t) {
                        // TODO should notify
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
                nl = (ch == '\n' || ch == '\r') || (pos > 0 && ch == '$');

                if (nl) break;

                // add char to array
                line[pos++] = ch;

                // weird content check
                if (pos >= NmeaParser.MAX_SENTENCE_LENGTH) {

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
}
