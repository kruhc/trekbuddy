// @LICENSE@

package cz.kruch.track.location;

import api.location.LocationProvider;
import api.location.Location;
import api.location.QualifiedCoordinates;
import api.location.LocationException;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import cz.kruch.track.util.NmeaParser;
import cz.kruch.track.configuration.Config;

/**
 * Base class for Serial/Bluetooth/Simulator providers.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
abstract class StreamReadingLocationProvider extends LocationProvider {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Stream");
//#endif

    // buffers
    private final byte[] btline;
    private final char[] line;
    private int btlineOffset, btlineCount;

    /* minimalistic NMEA stream hack */
    private int hack_rmc_count;
    private NmeaParser.Record gsa;

    // last I/O timestamp
    private long lastIO;

    protected StreamReadingLocationProvider(String name) {
        super(name);
        if (cz.kruch.track.configuration.Config.reliableInput) {
            this.btline = new byte[4096];
        } else {
            this.btline = new byte[512];
        }
        this.line = new char[NmeaParser.MAX_SENTENCE_LENGTH];
    }

    protected void reset() {
        btlineOffset = btlineCount = hack_rmc_count = 0;
        gsa = null;
    }

    synchronized long getLastIO() {
        return lastIO;
    }

    synchronized void setLastIO(long lastIO) {
        this.lastIO = lastIO;
    }

    protected final Location nextLocation(InputStream in, OutputStream observer) throws IOException, LocationException {
        // records
        NmeaParser.Record gga = null;
        NmeaParser.Record rmc = null;

        // xdr flag
        boolean xdr = false;

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

            // update last I/O timestamp
            setLastIO(System.currentTimeMillis());

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
                        if (hack_rmc_count++ >= 3) { // use GSA as GGA (alt missing, though)
                            if (gsa != null) {
                                gga = NmeaParser.Record.copyGsaIntoGga(gsa);
                                gga.timestamp = rmc.timestamp;
                            }
                        }

                    } break;
                    case NmeaParser.HEADER_XDR: {
//#ifdef __LOG__
                        if (log.isEnabled()) log.info("got XDR");
//#endif
                        xdr = true;
                    } break;
                    default:
                        continue;
                }
            } else {
//#ifdef __LOG__
                if (log.isEnabled()) log.warn("Invalid NMEA!");
//#endif
                if (NmeaParser.getType(line, l) != -1) {
                    checksums++;
                } else {
                    invalids++;
                }
                continue;
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

        // fix type
        final int fix;
        if (rmc.status != 'A') {
            fix = 0; // no fix (matches GGA::invalid fix type)
        } else if (gsa != null) {
            fix = gsa.fix; // 2 or 3, ie. 2D or 3D
        } else {
            fix = 1; // unspecified fix type (matches GGA::GPS fix type)
        }

        // corrections
        if (gsa != null) {
            if (gsa.fix != 3) { // not 3D fix - altitude is invalid
                gga.altitude = Float.NaN;
            }
        }

        // new location
        final Location location;
        
        // combine
        final long datetime = rmc.date + rmc.timestamp;
        final double lat = rmc.lat;
        final double lon = rmc.lon;
        final float alt = gga.altitude + Config.altCorrection;
        if (rmc.timestamp == gga.timestamp) { // good GPS unit
            final QualifiedCoordinates qc = QualifiedCoordinates.newInstance(lat, lon, alt,
                                                                             NmeaParser.hdop * 5, NmeaParser.vdop * 5);
            location = Location.newInstance(qc, datetime, fix, gga.sat);
            location.updateFixQuality(gga.fix);
        } else { // "unpaired sentences"
            location = Location.newInstance(QualifiedCoordinates.newInstance(lat, lon),
                                            datetime, fix);
            mismatches++;
        }
        location.setXdrBound(xdr);
        location.setCourse(rmc.course);
        location.setSpeed(rmc.speed);

        return location;
    }

    private int nextSentence(InputStream in, OutputStream observer) throws IOException {
        int pos = 0;
        int c = 0;

        boolean nl = false;
        boolean match = false;
        boolean retry = false;

        final char[] line = this.line;
        final byte[] btline = this.btline;

        while (c != -1) {

            // need new data?
            if (btlineOffset == btlineCount) {

                // read from stream
                final int n;
                if (cz.kruch.track.configuration.Config.reliableInput) {
                    final int available = in.available();
                    if (available > 0) {
                        if (available > maxavail) { // just for statistics
                            maxavail = available;
                        }
                        n = in.read(btline, 0, Math.min(available, btline.length));
                    } else {
                        final int i = in.read();
                        if (i == -1) {
                            n = -1;
                        } else {
                            n = 1;
                            btline[0] = (byte)i;
                        }
                    }
                } else {
                    n = in.read(btline, 0, btline.length);
                }

                // end of stream?
                if (n == -1) {
                    if (isGo()) { // not expected?
                        if (retry) { // already retried
                            c = -1;
                            break;
                        } else { // try once again (helps on older Nokias)
                            retry = true;
                            continue;
                        }
                    }
                    // death wished
                    c = -1;
                    break;
                }

                // use count
                btlineCount = n;

                // starting at the beginning
                btlineOffset = 0;

//#ifdef __ALL__
                // free CPU on Samsung
                if (cz.kruch.track.TrackingMIDlet.samsung) {
                    Thread.yield();
                }
//#endif

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

                    // record malformation
                    checksums++;

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
            setStatus("end of stream");
        }

        return -1;
    }
}
