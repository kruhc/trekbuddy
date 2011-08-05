// @LICENSE@

package cz.kruch.track.maps;

import api.location.ProjectionSetup;
import api.location.Datum;
import api.location.QualifiedCoordinates;

import java.io.InputStream;
import java.io.IOException;
import java.util.Vector;

import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.util.Mercator;
import cz.kruch.track.io.LineReader;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.Resources;
import cz.kruch.track.ui.Position;

/**
 * OZIExplorer map calibration support.
 *
 * @author kruhc@seznam.cz
 */
final class OziCalibration extends Calibration {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("OziCalibration");
//#endif

    private static final char[] LINE_POINT              = { 'P', 'o', 'i', 'n', 't' };
    private static final char[] LINE_MAP_PROJECTION     = { 'M', 'a', 'p', ' ', 'P', 'r', 'o', 'j', 'e', 'c', 't', 'i', 'o', 'n' };
    private static final char[] LINE_PROJECTION_SETUP   = { 'P', 'r', 'o', 'j', 'e', 'c', 't', 'i', 'o', 'n', ' ', 'S', 'e', 't', 'u', 'p' };
    private static final char[] LINE_MMPXY              = { 'M', 'M', 'P', 'X', 'Y' };
    private static final char[] LINE_MMPLL              = { 'M', 'M', 'P', 'L', 'L' };
    private static final char[] LINE_IWH                = { 'I', 'W', 'H' };

    OziCalibration() {
        super();
    }

    void init(final InputStream in, final String path) throws IOException {
        super.init(path);

        // line counter
        int lines = 0;

        // optimization flags
        boolean parsePoints = true;
        boolean almostDone = false;

        // vars
        final Vector xy = new Vector(9);
        final Vector ll = new Vector(9);
        Datum datum = null;
        ProjectionSetup projectionSetup = null;
        String projectionType = null;

        // read content
        LineReader reader = new LineReader(in, 4096);
        CharArrayTokenizer tokenizer = new CharArrayTokenizer();
        CharArrayTokenizer.Token line = reader.readToken(false);
        while (line != null) {
            lines++;
            if (!almostDone) {
                if (line.startsWith(LINE_POINT)) {
                    if (parsePoints) {
                        tokenizer.init(line, true);
                        parsePoints = parsePoint(tokenizer, xy, ll);
                    }
                } else if (line.startsWith(LINE_MAP_PROJECTION)) {
                    tokenizer.init(line, false);
                    projectionType = parseProjectionType(tokenizer);
                    projectionSetup = prepareProjectionSetup(projectionType);
                } else if (line.startsWith(LINE_PROJECTION_SETUP)) {

                    // parsing phase optimization
                    almostDone = true;

                    /*
                    * non-crippled Ozi calibration - use MMPXY/LL instead
                    */
                    xy.removeAllElements();
                    ll.removeAllElements();

                    if (ProjectionSetup.PROJ_TRANSVERSE_MERCATOR.equals(projectionType) || ProjectionSetup.PROJ_LCC.equals(projectionType)) {
                        tokenizer.init(line, true);
                        projectionSetup = parseProjectionSetup(projectionType, tokenizer);
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("projection setup parsed");
//#endif
                    }
                } else if (line.startsWith(LINE_IWH)) { // for crippled .map files :-(
                    tokenizer.init(line, false);
                    parseIwh(tokenizer);
                } else {
                    if (lines == 5) {
                        tokenizer.init(line, false);
                        datum = (Datum) Config.datumMappings.get("map:" + parseDatum(tokenizer));
                    }
                }
            } else if (line.startsWith(LINE_MMPXY)) {
                tokenizer.init(line, false);
                parseXY(tokenizer, xy);
            } else if (line.startsWith(LINE_MMPLL)) {
                tokenizer.init(line, false);
                parseLL(tokenizer, ll);
            } else if (line.startsWith(LINE_IWH)) {
                tokenizer.init(line, false);
                parseIwh(tokenizer);
            }
            line = null; // gc hint
            line = reader.readToken(false);
        }

        // gc hint
        tokenizer = null;

        // close reader
        reader.close();
        reader = null; // gc hint

        // fix projection
        if (ProjectionSetup.PROJ_UTM.equals(projectionType)) {
            projectionSetup = Mercator.getUTMSetup((QualifiedCoordinates) ll.firstElement());
        } else if (ProjectionSetup.PROJ_MERCATOR.equals(projectionType)) {
            projectionSetup = Mercator.getMercatorSetup(ll);
        }

        // finalize map
        doFinal(datum, projectionSetup, xy, ll);
    }

    private static boolean parsePoint(final CharArrayTokenizer tokenizer,
                                      final Vector xy, final Vector ll/*, Vector utm*/) throws InvalidMapException {
        int index = 0;
        int x = -1;
        int y = -1;
        double lat = 0D;
        double lon = 0D;

        try {
            while (index <= 11 && tokenizer.hasMoreTokens()) {
                final CharArrayTokenizer.Token token = tokenizer.next2();
                if (token.isDelimiter) {
                    index++;
                } else {
                    switch (index) {
                        case 2: {
                            if (!token.isEmpty()) {
                                x = CharArrayTokenizer.parseInt(token);
                            } else {
                                index = Integer.MAX_VALUE;
                            }
                        } break;
                        case 3: {
                            if (!token.isEmpty()) {
                                y = CharArrayTokenizer.parseInt(token);
                            } else {
                                index = Integer.MAX_VALUE;
                            }
                        } break;
                        case 6: {
                            lat += CharArrayTokenizer.parseInt(token);
                        } break;
                        case 7: {
                            lat += CharArrayTokenizer.parseDouble(token) / 60D;
                        } break;
                        case 8: {
                            if (token.startsWith('S')) {
                                lat *= -1D;
                            }
                        } break;
                        case 9: {
                            lon += CharArrayTokenizer.parseInt(token);
                        } break;
                        case 10: {
                            lon += CharArrayTokenizer.parseDouble(token) / 60D;
                        } break;
                        case 11: {
                            if (token.startsWith('W')) {
                                lon *= -1D;
                            }
                        } break;
                    }
                }
            }

            // empty cal point check
            if (x < -1 || y < -1) {
                return false;
            }
            if (lat == 0D || lon == 0D) {
                return false;
            }

            // save position and coords
            xy.addElement(Position.newInstance(x, y));
            ll.addElement(QualifiedCoordinates.newInstance(lat, lon));

        } catch (Exception e) {
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_INVALID_POINT), e);
        }

        return true;
    }

    private static String parseProjectionType(final CharArrayTokenizer tokenizer) throws InvalidMapException {
        try {
            tokenizer.next2(); // skip "Map Projection"
            return tokenizer.next2().toString().trim();
        } catch (Exception e) {
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_PARSE_PROJ_FAILED), e);
        }
    }

    private static String parseDatum(final CharArrayTokenizer tokenizer) throws InvalidMapException {
        try {
            return tokenizer.next2().toString().trim();
        } catch (Exception e) {
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_PARSE_DATUM_FAILED), e);
        }
    }

    private static ProjectionSetup prepareProjectionSetup(final String projectionType) {
        /*
         * projection setup for known grids
         */

        ProjectionSetup projectionSetup = null;

        if (ProjectionSetup.PROJ_LATLON.equals(projectionType)) {
            projectionSetup = new ProjectionSetup(ProjectionSetup.PROJ_LATLON);
        } else if (ProjectionSetup.PROJ_BNG.equals(projectionType)) {
            projectionSetup = new ProjectionSetup(projectionType,
                                                  new char[]{'B', 'N', 'G'},
                                                  -2D, 49D,
                                                  0.9996012717D,
                                                  400000, -100000);
        } else if (ProjectionSetup.PROJ_SG.equals(projectionType)) {
            projectionSetup = new ProjectionSetup(projectionType,
                                                  new char[]{'S', 'G'},
                                                  15.808277777778D, 0D,
                                                  1D,
                                                  1500000, 0);
        } else if (ProjectionSetup.PROJ_IG.equals(projectionType)) {
            projectionSetup = new ProjectionSetup(projectionType,
                                                  new char[]{'I', 'G'},
                                                  -8D, 53.5D,
                                                  1.000035D,
                                                  200000, 250000);
        } else if (ProjectionSetup.PROJ_SUI.equals(projectionType)) {
            projectionSetup = new ProjectionSetup(projectionType,
                                                  new char[]{'S', 'U', 'I'},
                                                  7.4395833333334D, 46.9524055555556D,
                                                  1D,
                                                  200000, 600000);
        } else if (ProjectionSetup.PROJ_FRANCE_I.equals(projectionType)) {
            projectionSetup = new ProjectionSetup(projectionType,
                                                  new char[]{'F', '-', 'I'},
                                                  2.3372083333D, 49.5D,
                                                  Double.NaN,
                                                  600000, 1200000,
                                                  48.5985227778D, 50.3959116667D);
        } else if (ProjectionSetup.PROJ_FRANCE_II.equals(projectionType)) {
            projectionSetup = new ProjectionSetup(projectionType,
                                                  new char[]{'F', '-', 'I', 'I'},
                                                  2.3372083333D, 46.8D,
                                                  Double.NaN,
                                                  600000, 2200000,
                                                  45.8989188889D, 47.6960144444D);
        } else if (ProjectionSetup.PROJ_FRANCE_III.equals(projectionType)) {
            projectionSetup = new ProjectionSetup(projectionType,
                                                  new char[]{'F', '-', 'I', 'I', 'I'},
                                                  2.3372083333D, 44.1D,
                                                  Double.NaN,
                                                  600000, 3200000,
                                                  43.1992913889D, 44.9960938889D);
        } else if (ProjectionSetup.PROJ_FRANCE_IV.equals(projectionType)) {
            projectionSetup = new ProjectionSetup(projectionType,
                                                  new char[]{'F', '-', 'I', 'V'},
                                                  2.3372083333D, 42.165D,
                                                  Double.NaN,
                                                  234.358, 4185861.369,
                                                  41.5603877778D, 42.0000593542D);
        }
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("projection type: " + projectionType);
//#endif

        return projectionSetup;
    }

    private static ProjectionSetup parseProjectionSetup(final String projectionName,
                                                        final CharArrayTokenizer tokenizer)
            throws InvalidMapException {

        int index = 0;
        double latOrigin, lonOrigin;
        double k = Double.NaN;
        double falseEasting, falseNorthing;
        double parallel1, parallel2;

        latOrigin = lonOrigin = parallel1 = parallel2 = falseEasting = falseNorthing = 0D;

        try {
            while (index <= 7 && tokenizer.hasMoreTokens()) {
                final CharArrayTokenizer.Token token = tokenizer.next2();
                if (token.isDelimiter) {
                    index++;
                } else {
                    if (!token.isEmpty()) {
                        switch (index) {
                            case 1: {
                                latOrigin = CharArrayTokenizer.parseDouble(token);
                            } break;
                            case 2: {
                                lonOrigin = CharArrayTokenizer.parseDouble(token);
                            } break;
                            case 3: {
                                k = CharArrayTokenizer.parseDouble(token);
                            } break;
                            case 4: {
                                falseEasting = CharArrayTokenizer.parseDouble(token);
                            } break;
                            case 5: {
                                falseNorthing = CharArrayTokenizer.parseDouble(token);
                            } break;
                            case 6: {
                                parallel1 = CharArrayTokenizer.parseDouble(token);
                            } break;
                            case 7: {
                                parallel2 = CharArrayTokenizer.parseDouble(token);
                            } break;
                        }
                    }
                }
            }

            return new ProjectionSetup(projectionName,
                                       null,
                                       lonOrigin, latOrigin,
                                       k,
                                       falseEasting, falseNorthing,
                                       parallel1, parallel2);
        } catch (Exception e) {
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_INVALID_PROJECTION), e);
        }
    }

    private static boolean parseXY(final CharArrayTokenizer tokenizer, final Vector xy) throws InvalidMapException {
        try {
            tokenizer.next2(); // MMPXY
            tokenizer.next2(); // index [1-4]
            final int x = tokenizer.nextInt2();
            final int y = tokenizer.nextInt2();
            xy.addElement(Position.newInstance(x, y));
        } catch (Exception e) {
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_INVALID_MMPXY), e);
        }

        return true;
    }

    private static boolean parseLL(final CharArrayTokenizer tokenizer, final Vector ll) throws InvalidMapException {
        try {
            tokenizer.next2(); // MMPLL
            tokenizer.next2(); // index [1-4]
            final double lon = tokenizer.nextDouble2();
            final double lat = tokenizer.nextDouble2();
            ll.addElement(QualifiedCoordinates.newInstance(lat, lon));
        } catch (Exception e) {
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_INVALID_MMPLL), e);
        }

        return true;
    }

    private void parseIwh(final CharArrayTokenizer tokenizer) throws InvalidMapException {
        try {
            tokenizer.next2(); // IWH
            tokenizer.next2(); // Map Image Width/Height
            setWidth(getDimension(tokenizer.nextInt2()));
            setHeight(getDimension(tokenizer.nextInt2()));
        } catch (InvalidMapException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_INVALID_IWH));
        }
    }
}
