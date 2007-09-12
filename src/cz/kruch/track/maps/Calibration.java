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

package cz.kruch.track.maps;

import api.location.QualifiedCoordinates;
import api.location.Datum;
import api.location.ProjectionSetup;
import api.location.GeodeticPosition;

import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.util.Mercator;
import cz.kruch.track.ui.Position;
import cz.kruch.track.io.LineReader;
import cz.kruch.track.configuration.Config;

import java.util.Vector;
import java.io.IOException;
import java.io.InputStream;

import org.xmlpull.v1.XmlPullParser;
import org.kxml2.io.KXmlParser;

/**
 * Calibration info.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
abstract class Calibration {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Calibration");
//#endif

    public static final String OZI_EXT = ".map";
    public static final String GMI_EXT = ".gmi";
    public static final String XML_EXT = ".xml";
    public static final String J2N_EXT = ".j2n";

    private static final ProjectionSetup LATLON_PROJ_SETUP = new ProjectionSetup(ProjectionSetup.PROJ_LATLON);

    // map path
    private String path;

    // map dimensions
    protected int width = -1;
    protected int height = -1;

    // map datum and projection params
    private Datum datum;
    private ProjectionSetup projectionSetup;

    // main (left-top) calibration point
    private Position calibrationXy;
    private GeodeticPosition calibrationGp;

    // reusable info
    private Position proximite;

    // range // TODO get rid of it?
    private QualifiedCoordinates[] range;

    protected Calibration(String path) {
        this.path = path;
        this.proximite = new Position(0, 0);
    }

    public String getPath() {
        return path;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public ProjectionSetup getProjection() {
        return projectionSetup;
    }

    public Datum getDatum() {
        if (datum == null) {
            return Config.currentDatum;
        }

        return datum;
    }

    boolean isWithin(QualifiedCoordinates coordinates) {
/* too rough
        final double lat = coordinates.getLat();
        final double lon = coordinates.getLon();
        QualifiedCoordinates[] _range = range;
        return (lat <= _range[0].getLat() && lat >= _range[3].getLat())
                && (lon >= _range[0].getLon() && lon <= _range[3].getLon());
*/
        Position p = transform(coordinates);
        final int x = p.getX();
        if (x >=0 && x < width) {
            final int y = p.getY();
            if (y >= 0 && y < height) {
                return true;
            }
        }
        return false;
    }

    QualifiedCoordinates[] getRange() {
        return range;
    }

    QualifiedCoordinates transform(Position position) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("transform " + position);
//#endif

        QualifiedCoordinates qc;

        if (calibrationGp instanceof Mercator.Coordinates) {
            Mercator.Coordinates utm = (Mercator.Coordinates) toGp(position);
            qc = Mercator.MercatortoLL(utm, getDatum().getEllipsoid(),
                                       (Mercator.ProjectionSetup) projectionSetup);
/*
            qc.setDatum(datum == Datum.DATUM_WGS_84 ? null : datum);
*/
            Mercator.Coordinates.releaseInstance(utm);
        } else /*if (calibrationGp instanceof QualifiedCoordinates)*/ {
            qc = (QualifiedCoordinates) toGp(position);
        }

        return qc;
    }

    Position transform(QualifiedCoordinates coordinates) {
        GeodeticPosition gp;

        if (calibrationGp instanceof Mercator.Coordinates) {
            gp = Mercator.LLtoMercator(coordinates, getDatum().getEllipsoid(),
                                       (Mercator.ProjectionSetup) projectionSetup);
        } else /*if (calibrationGp instanceof QualifiedCoordinates)*/ {
            gp = coordinates;
        }

        double _v = v2;
        double _h = h2;

        final double cgph = calibrationGp.getH();
        final double cgpv = calibrationGp.getV();
        final int cxyx = calibrationXy.getX();
        final int cxyy = calibrationXy.getY();

        double fx = (gp.getH() - cgph + (cxyx * _h) + (ek0 * cxyy) - (ek0 / _v) * (- gp.getV() + cgpv + (cxyy * _v) - (nk0 * cxyx))) / (_h + (nk0 * ek0) / _v);
        double fy = (- gp.getV() + cgpv + (cxyy * _v) + (nk0 * (fx - cxyx))) / _v;

        /* better precision calculations with known x,y */

        _v = gridLVscale + (fx - cxyx) * vScale;
        _h = gridTHscale + (fy - cxyy) * hScale;

        fx = (gp.getH() - cgph + (cxyx * _h) + (ek0 * cxyy) - (ek0 / _v) * (- gp.getV() + cgpv + (cxyy * _v) - (nk0 * cxyx))) / (_h + (nk0 * ek0) / _v);
        fy = (- gp.getV() + cgpv + (cxyy * _v) + (nk0 * (fx - cxyx))) / _v;

        int x = (int) fx;
        if ((fx - x) > 0.5) {
            x++;
        }
        int y = (int) fy;
        if ((fy - y) > 0.5) {
            y++;
        }

        proximite.setXy(x, y);

        if (gp instanceof Mercator.Coordinates) {
            Mercator.Coordinates.releaseInstance((Mercator.Coordinates) gp);
        }

        return proximite;
    }

    protected void doFinal(Datum datum, ProjectionSetup setup,
                           Vector xy, Vector ll) throws InvalidMapException {
        // assertions
        if ((xy.size() < 2) || (ll.size() < 2)) {
            throw new InvalidMapException("Too few calibration points");
        }

        // set datum
        this.datum = datum;
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("using datum " + datum);
//#endif

        // set projection setup
        if (setup == null) {
            projectionSetup = Mercator.getUTMSetup((QualifiedCoordinates) ll.firstElement());
        } else {
            projectionSetup = setup;
        }
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("using projection setup " + projectionSetup);
//#endif

        // remember main calibration point x-y
        calibrationXy = (Position) xy.elementAt(0);

        // compute grid
        if (projectionSetup instanceof Mercator.ProjectionSetup) {

            // setup is for Mercator projection
            Mercator.ProjectionSetup msetup = (Mercator.ProjectionSetup) projectionSetup;
            Datum.Ellipsoid ellipsoid = getDatum().getEllipsoid();

            /*
             * performance optimization: reuse existing vector
             */

            // lat,lon -> easting,northing
//            Vector tm = new Vector(ll.size());
            for (int N = ll.size(), i = 0; i < N; i++) {
                QualifiedCoordinates local = (QualifiedCoordinates) ll.elementAt(i);
                Mercator.Coordinates utm = Mercator.LLtoMercator(local, ellipsoid, msetup);
//                tm.addElement(utm);
                ll.setElementAt(utm, i);
            }

            // remember main calibration point easting-northing and zone
//            calibrationGp = (GeodeticPosition) tm.elementAt(0);
            calibrationGp = (GeodeticPosition) ll.elementAt(0);

            // compute pixel grid for TM
//            computeGrid(xy, tm);
            computeGrid(xy, ll);

        } else {

            // remember main calibration point lat-lon
            calibrationGp = (GeodeticPosition) ll.elementAt(0);

            // compute pixel grid for LL
            computeGrid(xy, ll);
        }

        // precompute some values for faster decisions
        computeRange();
    }

    private double gridTHscale, gridLVscale;
    private double ek0, nk0;

    private double h2, v2;
    private double hScale, vScale;

    private void computeGrid(Vector xy, Vector gp) {
        Position p;

        p = Position.newInstance(width, 0);
        int[] index = verticalAxisByX(xy, p);
        Position.releaseInstance(p);

        double gridRVscale = Math.abs((((GeodeticPosition) gp.elementAt(index[1])).getV() - ((GeodeticPosition) gp.elementAt(index[0])).getV()) / (((Position) xy.elementAt(index[1])).getY() - ((Position) xy.elementAt(index[0])).getY()));
        int v1 = ((Position) xy.elementAt(index[0])).getX();

        p = Position.newInstance(0, 0);
        index = horizontalAxisByY(xy, p);
        Position.releaseInstance(p);

        int dx = (((Position) xy.elementAt(index[1])).getX() - ((Position) xy.elementAt(index[0])).getX());
        gridTHscale = Math.abs((((GeodeticPosition) gp.elementAt(index[1])).getH() - ((GeodeticPosition) gp.elementAt(index[0])).getH()) / dx);
        int h0 = ((Position) xy.elementAt(index[0])).getY();
        double nk0d = (((Position) xy.elementAt(index[1])).getY() - h0) * gridRVscale;
        nk0 = (((GeodeticPosition) gp.elementAt(index[1])).getV() + nk0d - ((GeodeticPosition) gp.elementAt(index[0])).getV()) / dx;

        p = Position.newInstance(0, height);
        index = horizontalAxisByY(xy, p);
        Position.releaseInstance(p);

        double gridBHscale = Math.abs((((GeodeticPosition) gp.elementAt(index[1])).getH() - ((GeodeticPosition) gp.elementAt(index[0])).getH()) / (((Position) xy.elementAt(index[1])).getX() - ((Position) xy.elementAt(index[0])).getX()));
        int h1 = ((Position) xy.elementAt(index[0])).getY();

        p = Position.newInstance(0, 0);
        index = verticalAxisByX(xy, p);
        Position.releaseInstance(p);

        int dy = (((Position) xy.elementAt(index[1])).getY() - ((Position) xy.elementAt(index[0])).getY());
        gridLVscale = Math.abs((((GeodeticPosition) gp.elementAt(index[1])).getV() - ((GeodeticPosition) gp.elementAt(index[0])).getV()) / dy);
        int v0 = ((Position) xy.elementAt(index[0])).getX();
        double ek0d = (((Position) xy.elementAt(index[1])).getX() - v0) * gridBHscale;
        ek0 = (((GeodeticPosition) gp.elementAt(index[1])).getH() - ek0d - ((GeodeticPosition) gp.elementAt(index[0])).getH()) / dy;

        h2 = (gridTHscale + gridBHscale) / 2D;
        v2 = (gridLVscale + gridRVscale) / 2D;

        hScale = (gridBHscale - gridTHscale) / (h1 - h0);
        vScale = (gridRVscale - gridLVscale) / (v1 - v0);

        if (gp.size() == 2) {
            hScale = vScale = nk0 = ek0 = 0D;
        }
    }

    private void computeRange() {
        range = new QualifiedCoordinates[4];
        Position p = proximite;
        p.setXy(0, 0);
        range[0] = transform(p);
        p.setXy(width, 0);
        range[1] = transform(p);
        p.setXy(0, height);
        range[2] = transform(p);
        p.setXy(width, height);
        range[3] = transform(p);
    }

    private GeodeticPosition toGp(Position position) {
        int dy = position.getY() - calibrationXy.getY();
        int dx = position.getX() - calibrationXy.getX();
        double h = calibrationGp.getH() + (ek0 * dy) + (dx * (gridTHscale + dy * hScale));
        double v = calibrationGp.getV() + (nk0 * dx) - (dy * (gridLVscale + dx * vScale));

        if (calibrationGp instanceof Mercator.Coordinates) {
            return Mercator.Coordinates.newInstance(((Mercator.ProjectionSetup) projectionSetup).zone, h, v);
        } else /*if (calibrationGp instanceof QualifiedCoordinates)*/ {
            return QualifiedCoordinates.newInstance(v, h);
        }
    }

    private static int[] verticalAxisByX(Vector xy, Position position) {
        final int x = position.getX();
        int i0 = -1, i1 = -1;
        int d0 = Integer.MAX_VALUE, d1 = Integer.MAX_VALUE;
        for (int N = xy.size(), i = 0; i < N; i++) {
            int dx = Math.abs(x - ((Position) xy.elementAt(i)).getX());
            if (dx < d0) {
                if (i0 > -1) {
                    d1 = d0;
                    i1 = i0;
                }
                d0 = dx;
                i0 = i;
            } else if (dx < d1) {
                d1 = dx;
                i1 = i;
            }
        }

        if (Math.abs(position.getY() - ((Position) xy.elementAt(i0)).getY()) < Math.abs(position.getY() - ((Position) xy.elementAt(i1)).getY())) {
            return new int[]{ i0, i1 };
        } else {
            return new int[]{ i1, i0 };
        }
    }

    private static int[] horizontalAxisByY(Vector xy, Position position) {
        final int y = position.getY();
        int i0 = -1, i1 = -1;
        int d0 = Integer.MAX_VALUE, d1 = Integer.MAX_VALUE;
        for (int N = xy.size(), i = 0; i < N; i++) {
            int dy = Math.abs(y - ((Position) xy.elementAt(i)).getY());
            if (dy < d0) {
                if (i0 > -1) {
                    d1 = d0;
                    i1 = i0;
                }
                d0 = dy;
                i0 = i;
            } else if (dy < d1) {
                d1 = dy;
                i1 = i;
            }
        }

        if (Math.abs(position.getX() - ((Position) xy.elementAt(i0)).getX()) < Math.abs(position.getX() - ((Position) xy.elementAt(i1)).getX())) {
            return new int[]{ i0, i1 };
        } else {
            return new int[]{ i1, i0 };
        }
    }

    static final class GMI extends Calibration {

        private static final char[] DELIM = { ';' };

        GMI(InputStream in, String path) throws IOException {
            super(path);

            Vector xy = new Vector();
            Vector ll = new Vector();
            CharArrayTokenizer tokenizer = new CharArrayTokenizer();

            // text reader
            LineReader reader = new LineReader(in);

            // base info
            reader.readLine(false); // ignore - intro line
            reader.readLine(false); // ignore - path to image file
            width = Integer.parseInt(reader.readLine(false)); // image width
            height = Integer.parseInt(reader.readLine(false)); // image width

            // additional data
            String line = reader.readLine(false);
            while (line != null) {
                if (line.startsWith("Additional Calibration Data"))
                    break;
                if (line.startsWith("Border and Scale")) {
                    line = reader.readLine(false);
                    if (line != null) {
                        xy.removeAllElements();
                        ll.removeAllElements();
                        tokenizer.init(line, DELIM, false);
                        parseBorder(tokenizer, xy, ll);
                    }
                    break;
                }
                if (line == LineReader.EMPTY_LINE) // '==' is ok
                    break;

                tokenizer.init(line, DELIM, false);
                parsePoint(tokenizer, xy, ll);

                line = null; // gc hint
                line = reader.readLine(false);
            }

            // close reader
            reader.close();
            reader = null; // gc hint

            // dispose tokenizer
            tokenizer.dispose();
            tokenizer = null; // gc hint

            doFinal(null, LATLON_PROJ_SETUP, xy, ll);

            // gc hints
            xy.removeAllElements();
            ll.removeAllElements();
        }

        private static void parsePoint(CharArrayTokenizer tokenizer, Vector xy, Vector ll) {
            int x = tokenizer.nextInt();
            int y = tokenizer.nextInt();
            double lon = tokenizer.nextDouble();
            double lat = tokenizer.nextDouble();
            xy.addElement(Position.newInstance(x, y));
            ll.addElement(QualifiedCoordinates.newInstance(lat, lon));
        }

        private void parseBorder(CharArrayTokenizer tokenizer, Vector xy, Vector ll) {
            double lat = tokenizer.nextDouble();
            double lon = tokenizer.nextDouble();
            xy.addElement(Position.newInstance(0, 0));
            ll.addElement(QualifiedCoordinates.newInstance(lat, lon));
            lat = tokenizer.nextDouble();
            lon = tokenizer.nextDouble();
            xy.addElement(Position.newInstance(width - 1, height - 1));
            ll.addElement(QualifiedCoordinates.newInstance(lat, lon));
        }
    }

    static class J2N extends Calibration {
        private static final String TAG_NAME        = "name";
        private static final String TAG_POSITION    = "position";
        private static final String TAG_LATITUDE    = "latitude";
        private static final String TAG_LONGITUDE   = "longitude";
        private static final String TAG_IMAGEWIDTH  = "imageWidth";
        private static final String TAG_IMAGEHEIGHT = "imageHeight";

        J2N(InputStream in, String path) throws InvalidMapException {
            super(path);

            Vector xy = new Vector();
            Vector ll = new Vector();
            KXmlParser parser = new KXmlParser();

            try {
                parser.setInput(in, null); // null is for encoding autodetection

                boolean keepParsing = true;
                String currentTag = null;

                int x0 = -1, y0 = -1;
                double lat0 = 0D, lon0 = 0D;

                while (keepParsing) {
                    switch (parser.next()) {
                        case XmlPullParser.START_TAG: {
                            currentTag = parser.getName();
                            if (TAG_POSITION.equals(currentTag)) {
                                x0 = Integer.parseInt(parser.getAttributeValue(null, "x"));
                                y0 = Integer.parseInt(parser.getAttributeValue(null, "y"));
                            }
                        } break;
                        case XmlPullParser.END_TAG: {
                            if (TAG_POSITION.equals(parser.getName())) {
                                xy.addElement(Position.newInstance(x0, y0));
                                ll.addElement(QualifiedCoordinates.newInstance(lat0, lon0));
                            }
                            currentTag = null;
                        } break;
                        case XmlPullParser.TEXT: {
                            if (currentTag != null) {
                                String text = parser.getText().trim();
                                if (TAG_NAME.equals(currentTag)) {
//                                    this.path = text + ".png";
                                } else if (TAG_LATITUDE.equals(currentTag)) {
                                    lat0 = Double.parseDouble(text);
                                } else if (TAG_LONGITUDE.equals(currentTag)) {
                                    lon0 = Double.parseDouble(text);
                                } else if (TAG_IMAGEWIDTH.equals(currentTag)) {
                                    width = Integer.parseInt(text);
                                } else if (TAG_IMAGEHEIGHT.equals(currentTag)) {
                                    height = Integer.parseInt(text);
                                }
                            }
                        } break;
                        case XmlPullParser.END_DOCUMENT: {
                            keepParsing = false;
                        } break;
                    }
                }

            } catch (Exception e) {
                throw new InvalidMapException(e);
            } finally {
                try {
                    parser.close();
                } catch (IOException e) {
                    // ignore
                }
            }

            doFinal(null, LATLON_PROJ_SETUP, xy, ll);

            // gc hints
            xy.removeAllElements();
            ll.removeAllElements();
        }
    }

    static final class XML extends J2N {
        XML(InputStream in, String path) throws InvalidMapException {
            super(in, path);
        }
    }

    static final class Ozi extends Calibration {

        private static final String LINE_POINT              = "Point";
        private static final String LINE_MAP_PROJECTION     = "Map Projection";
        private static final String LINE_PROJECTION_SETUP   = "Projection Setup";
        private static final String LINE_MMPXY              = "MMPXY";
        private static final String LINE_MMPLL              = "MMPLL";
        private static final String LINE_IWH                = "IWH";

        Ozi(InputStream in, String path) throws IOException {
            super(path);

            int lines = 0;

            String projectionType = ProjectionSetup.PROJ_TRANSVERSE_MERCATOR;
            Vector xy = new Vector(4), ll = new Vector(4);
            Datum datum = null;
            ProjectionSetup projectionSetup = null;
            CharArrayTokenizer tokenizer = new CharArrayTokenizer();

            // read content
            LineReader reader = new LineReader(in/*, true*/);
            CharArrayTokenizer.Token line = reader.readToken(false);
            while (line != null) {
                lines++;
                if (line.startsWith(LINE_POINT)) {
                    tokenizer.init(line, true);
                    parsePoint(tokenizer, xy, ll);
                } else if (line.startsWith(LINE_MAP_PROJECTION)) {
                    tokenizer.init(line, false);
                    projectionType = parseProjectionType(tokenizer);
                    /*
                     * projection setup for known grids
                     */
                    if (ProjectionSetup.PROJ_LATLON.equals(projectionType)) {
                        projectionSetup = LATLON_PROJ_SETUP;
                    } else if (ProjectionSetup.PROJ_BNG.equals(projectionType)) {
                            projectionSetup = new Mercator.ProjectionSetup(projectionType,
                                                                           -1, 'Z', -2D, 49D,
                                                                           0.9996012717D,
                                                                           400000, -100000);
                    } else if (ProjectionSetup.PROJ_SG.equals(projectionType)) {
                            projectionSetup = new Mercator.ProjectionSetup(projectionType,
                                                                           -1, 'Z', 15.808277777778D, 0D,
                                                                           1D,
                                                                           1500000, 0);
                    } else if (ProjectionSetup.PROJ_IG.equals(projectionType)) {
                            projectionSetup = new Mercator.ProjectionSetup(projectionType,
                                                                           -1, 'Z', -8D, 53.5D,
                                                                           1.000035D,
                                                                           200000, 250000);
                    } else if (ProjectionSetup.PROJ_SUI.equals(projectionType)) {
                            projectionSetup = new Mercator.ProjectionSetup(projectionType,
                                                                           -1, 'Z', 7.4395833333334D, 46.9524055555556D,
                                                                           1.0D,
                                                                           200000, 600000);
                    }
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("projection type: " + projectionType);
//#endif
                } else if (line.startsWith(LINE_PROJECTION_SETUP)) {
                    /*
                     * not-crippled Ozi calibration - use MMPXY/LL instead
                     */
                    xy.removeAllElements();
                    ll.removeAllElements();

                    if (ProjectionSetup.PROJ_TRANSVERSE_MERCATOR.equals(projectionType)) {
                        tokenizer.init(line, true);
                        projectionSetup = parseProjectionSetup(tokenizer);
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("projection setup parsed");
//#endif
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
                } else {
                    if (lines == 5) {
                        tokenizer.init(line, false);
                        datum = (Datum) Config.datumMappings.get("map:" + parseDatum(tokenizer));
                    }
                }
                line = null; // gc hint
                line = reader.readToken(false);
            }

            // close reader
            reader.close();
            reader = null; // gc hint

            // dispose tokenizer
            tokenizer.dispose();
            tokenizer = null;

            // dimension check
            if (width == -1 || height == -1) {
                throw new InvalidMapException("Invalid map dimension");
            }

            // paranoia
            if (xy.size() != ll.size()) {
                throw new InvalidMapException("MMPXY:MMPLL size mismatch");
            }

            // fix projection
            if (ProjectionSetup.PROJ_UTM.equals(projectionType)) {
                projectionSetup = Mercator.getUTMSetup((QualifiedCoordinates) ll.firstElement());
            } else if (ProjectionSetup.PROJ_MERCATOR.equals(projectionType)) {
                projectionSetup = Mercator.getMercatorSetup(ll);
            }

            doFinal(datum, projectionSetup, xy, ll);

            // gc hints
            xy.removeAllElements();
            ll.removeAllElements();
        }

        private static boolean parsePoint(CharArrayTokenizer tokenizer,
                                          Vector xy, Vector ll/*, Vector utm*/) throws InvalidMapException {
            int index = 0;
            int x = -1;
            int y = -1;
//            String easting, northing, zone = "N";
            double lat = 0D;
            double lon = 0D;

            try {
                while (tokenizer.hasMoreTokens()) {
                    CharArrayTokenizer.Token token = tokenizer.next();
                    if (token.isDelimiter) {
                        index++;
                    } else if (index == 2) {
                        if (token.isEmpty()) {
                            index = Integer.MAX_VALUE;
                        } else {
                            x = CharArrayTokenizer.parseInt(token);
                        }
                    } else if (index == 3) {
                        if (token.isEmpty()) {
                            index = Integer.MAX_VALUE;
                        } else {
                            y = CharArrayTokenizer.parseInt(token);
                        }
                    } else if (index == 6) {
                        lat += CharArrayTokenizer.parseInt(token);
                    } else if (index == 7) {
                        lat += CharArrayTokenizer.parseDouble(token) / 60D;
                    } else if (index == 8) {
                        if (token.startsWith('S')) {
                            lat *= -1D;
                        }
                    } else if (index == 9) {
                        lon += CharArrayTokenizer.parseInt(token);
                    } else if (index == 10) {
                        lon += CharArrayTokenizer.parseDouble(token) / 60D;
                    } else if (index == 11) {
                        if (token.startsWith('W')) {
                            lon *= -1D;
                        }
                    } else if (index == 14) {
    //                    easting = token;
                    } else if (index == 15) {
    //                    northing = token;
                    } else if (index == 16) {
    //                    zone = token;
                    }
                    if (index > 11/*16*/) {
                        break;
                    }
                }

                // empty cal point check
                if (x < -1 || y < -1) {
                    return false;
                }
                if (lat == 0D || lon == 0D) {
                    return false;
                }

                xy.addElement(Position.newInstance(x, y));
                ll.addElement(QualifiedCoordinates.newInstance(lat, lon));

            } catch (Exception e) {
                throw new InvalidMapException("Invalid Point", e);
            }

            return true;
        }

        private static String parseProjectionType(CharArrayTokenizer tokenizer) throws InvalidMapException {
            try {
                tokenizer.next(); // Map Projection
                return tokenizer.next().toString();
            } catch (Exception e) {
                throw new InvalidMapException("Failed to parse projection type", e);
            }
        }

        private static String parseDatum(CharArrayTokenizer tokenizer) throws InvalidMapException {
            try {
                return tokenizer.next().toString();
            } catch (Exception e) {
                throw new InvalidMapException("Failed to parse map datum", e);
            }
        }

        private static Mercator.ProjectionSetup parseProjectionSetup(CharArrayTokenizer tokenizer)
                throws InvalidMapException {

            int index = 0;
            double latOrigin, lonOrigin;
            double k;
            double falseEasting, falseNorthing;

            latOrigin = lonOrigin = k = falseEasting = falseNorthing = Double.NaN;

            try {
                while (tokenizer.hasMoreTokens()) {
                    CharArrayTokenizer.Token token = tokenizer.next();
                    if (token.isDelimiter) {
                        index++;
                    } else if (index == 1) {
                        latOrigin = CharArrayTokenizer.parseDouble(token);
                    } else if (index == 2) {
                        lonOrigin = CharArrayTokenizer.parseDouble(token);
                    } else if (index == 3) {
                        k = CharArrayTokenizer.parseDouble(token);
                    } else if (index == 4) {
                        falseEasting = CharArrayTokenizer.parseDouble(token);
                    } else if (index == 5) {
                        falseNorthing = CharArrayTokenizer.parseDouble(token);
                    } else if (index > 5) {
                        break;
                    }
                }

                // got valid data?
                if (Double.isNaN(latOrigin) || Double.isNaN(lonOrigin)
                    || Double.isNaN(k) || Double.isNaN(falseEasting) || Double.isNaN(falseNorthing)) {
                    throw new NumberFormatException("?");
                }

                return new Mercator.ProjectionSetup(ProjectionSetup.PROJ_TRANSVERSE_MERCATOR,
                                                    -1, 'Z', lonOrigin, latOrigin,
                                                    k, falseEasting, falseNorthing);
            } catch (Exception e) {
                throw new InvalidMapException("Invalid Projection Setup", e);
            }
        }

        private static boolean parseXY(CharArrayTokenizer tokenizer, Vector xy) throws InvalidMapException {
            try {
                tokenizer.next(); // MMPXY
                tokenizer.next(); // index [1-4]
                int x = tokenizer.nextInt();
                int y = tokenizer.nextInt();
                xy.addElement(Position.newInstance(x, y));
            } catch (Exception e) {
                throw new InvalidMapException("Invalid MMPXY", e);
            }

            return true;
        }

        private static boolean parseLL(CharArrayTokenizer tokenizer, Vector ll) throws InvalidMapException {
            try {
                tokenizer.next(); // MMPLL
                tokenizer.next(); // index [1-4]
                double lon = tokenizer.nextDouble();
                double lat = tokenizer.nextDouble();
                ll.addElement(QualifiedCoordinates.newInstance(lat, lon));
            } catch (Exception e) {
                throw new InvalidMapException("Invalid MMPLL", e);
            }

            return true;
        }

        private void parseIwh(CharArrayTokenizer tokenizer) throws InvalidMapException {
            try {
                tokenizer.next(); // IWH
                tokenizer.next(); // Map Image Width/Height
                width = getDimension(tokenizer.nextInt());
                height = getDimension(tokenizer.nextInt());
            } catch (InvalidMapException e) {
                throw  e;
            } catch (Exception e) {
                throw new InvalidMapException("Invalid IWH");
            }
        }

        private static short getDimension(int i) throws InvalidMapException {
            if (i > Short.MAX_VALUE) {
                throw new InvalidMapException("Map too large");
            }

            return (short) i;
        }
    }
}
