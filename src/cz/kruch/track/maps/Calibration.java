// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps;

import api.location.QualifiedCoordinates;
import api.location.Datum;
import api.location.ProjectionSetup;
import api.location.GeodeticPosition;

import cz.kruch.track.util.CharArrayTokenizer;
//#ifdef __LOG__
import cz.kruch.track.util.Logger;
//#endif
import cz.kruch.track.util.Mercator;
import cz.kruch.track.ui.Position;
import cz.kruch.track.io.LineReader;
import cz.kruch.track.configuration.Config;

import java.util.Vector;
import java.util.Enumeration;
import java.io.IOException;
import java.io.InputStream;

import org.xmlpull.v1.XmlPullParser;
import org.kxml2.io.KXmlParser;

public abstract class Calibration {
//#ifdef __LOG__
    private static final Logger log = new Logger("Calibration");
//#endif

    // map/slice path // TODO bad design - path to slice should be in Slice
    protected String path;

    // map/slice dimensions
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

            // lat,lon -> easting,northing
            Vector tm = new Vector(ll.size());
            for (Enumeration e = ll.elements(); e.hasMoreElements(); ) {
                QualifiedCoordinates local = (QualifiedCoordinates) e.nextElement();
                Mercator.UTMCoordinates utm = Mercator.LLtoTM(local, getDatum().getEllipsoid(),
                                                              (Mercator.ProjectionSetup) projectionSetup);
                tm.addElement(utm);
            }

            // remember main calibration point easting-northing and zone
            calibrationGp = (GeodeticPosition) tm.elementAt(0);
            zone = ((Mercator.UTMCoordinates) tm.elementAt(0)).zone;

            // compute pixel grid for TM
            computeGrid(xy, tm);

        } else {

            // remember main calibration point lat-lon
            calibrationGp = (GeodeticPosition) ll.elementAt(0);

            // compute pixel grid for LL
            computeGrid(xy, ll);
        }

        // precompute some values for faster decisions
        computeRange();
    }

    private String zone;

    private double gridTHscale, gridLVscale;
    private double ek0, nk0;

/*
    double halfHStep, halfVStep;
*/

    double h2, v2;
    double hScale, vScale;

    private void computeGrid(Vector xy, Vector gp) {
        int[] index = verticalAxisByX(xy, new Position(getWidth(), 0));
        double gridRVscale = Math.abs((((GeodeticPosition) gp.elementAt(index[1])).getV() - ((GeodeticPosition) gp.elementAt(index[0])).getV()) / (((Position) xy.elementAt(index[1])).getY() - ((Position) xy.elementAt(index[0])).getY()));
        int v1 = ((Position) xy.elementAt(index[0])).getX();

        index = horizontalAxisByY(xy, new Position(0, 0));
        int dx = (((Position) xy.elementAt(index[1])).getX() - ((Position) xy.elementAt(index[0])).getX());
        gridTHscale = Math.abs((((GeodeticPosition) gp.elementAt(index[1])).getH() - ((GeodeticPosition) gp.elementAt(index[0])).getH()) / dx);
        int h0 = ((Position) xy.elementAt(index[0])).getY();
        double nk0d = (((Position) xy.elementAt(index[1])).getY() - h0) * gridRVscale;
        nk0 = (((GeodeticPosition) gp.elementAt(index[1])).getV() + nk0d - ((GeodeticPosition) gp.elementAt(index[0])).getV()) / dx;

        index = horizontalAxisByY(xy, new Position(0, getHeight()));
        double gridBHscale = Math.abs((((GeodeticPosition) gp.elementAt(index[1])).getH() - ((GeodeticPosition) gp.elementAt(index[0])).getH()) / (((Position) xy.elementAt(index[1])).getX() - ((Position) xy.elementAt(index[0])).getX()));
        int h1 = ((Position) xy.elementAt(index[0])).getY();

        index = verticalAxisByX(xy, new Position(0, 0));
        int dy = (((Position) xy.elementAt(index[1])).getY() - ((Position) xy.elementAt(index[0])).getY());
        gridLVscale = Math.abs((((GeodeticPosition) gp.elementAt(index[1])).getV() - ((GeodeticPosition) gp.elementAt(index[0])).getV()) / dy);
        int v0 = ((Position) xy.elementAt(index[0])).getX();
        double ek0d = (((Position) xy.elementAt(index[1])).getX() - v0) * gridBHscale;
        ek0 = (((GeodeticPosition) gp.elementAt(index[1])).getH() - ek0d - ((GeodeticPosition) gp.elementAt(index[0])).getH()) / dy;

/*
        halfHStep = Math.min(gridTHscale / 2D, gridBHscale / 2D);
        halfVStep = Math.min(gridLVscale / 2D, gridRVscale / 2D);
*/

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
        range[0] = transform(new Position(0, 0));
        range[1] = transform(new Position(width, 0));
        range[2] = transform(new Position(0, height));
        range[3] = transform(new Position(width, height));
    }

    public boolean isWithin(QualifiedCoordinates coordinates) {
/*
        double lat = coordinates.getLat();
        double lon = coordinates.getLon();
        QualifiedCoordinates[] _range = range;
        return (lat <= _range[0].getLat() && lat >= _range[3].getLat())
                && (lon >= _range[0].getLon() && lon <= _range[3].getLon());
*/
        return isWithin(transform(coordinates));
    }
    
    public boolean isWithin(Position p) {
        return (p.getX() >=0 && p.getY() >= 0 && p.getX() < width && p.getY() < height);
    }

    public QualifiedCoordinates[] getRange() {
        return range;
    }

    public QualifiedCoordinates transform(Position position) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("transform " + position);
//#endif

        QualifiedCoordinates qc = null;

        if (calibrationGp instanceof Mercator.UTMCoordinates) {
            qc = Mercator.TMtoLL((Mercator.UTMCoordinates) toGp(position),
                                 getDatum().getEllipsoid(),
                                 (Mercator.ProjectionSetup) projectionSetup);
            qc.setDatum(datum == Datum.DATUM_WGS_84 ? null : datum);
        } else /*if (calibrationGp instanceof QualifiedCoordinates)*/ {
            qc = (QualifiedCoordinates) toGp(position);
        }

        return qc;
    }

    private GeodeticPosition toGp(Position position) {
        int dy = position.getY() - calibrationXy.getY();
        int dx = position.getX() - calibrationXy.getX();
        double h = calibrationGp.getH() + (ek0 * dy) + (dx * (gridTHscale + dy * hScale));
        double v = calibrationGp.getV() + (nk0 * dx) - (dy * (gridLVscale + dx * vScale));

        if (calibrationGp instanceof Mercator.UTMCoordinates) {
            return new Mercator.UTMCoordinates(zone, h, v);
        } else /*if (calibrationGp instanceof QualifiedCoordinates)*/ {
            return new QualifiedCoordinates(v, h);
        }
    }

    public Position transform(QualifiedCoordinates coords) {
        return proximitePosition(coords);
    }

    private Position proximitePosition(QualifiedCoordinates coordinates) {
        GeodeticPosition gp = null;

        if (calibrationGp instanceof Mercator.UTMCoordinates) {
            gp = Mercator.LLtoTM(coordinates, getDatum().getEllipsoid(),
                                 (Mercator.ProjectionSetup) projectionSetup);
        } else /*if (calibrationGp instanceof QualifiedCoordinates)*/ {
            gp = coordinates;
        }

        double _v = v2;
        double _h = h2;

        double fx = (gp.getH() - calibrationGp.getH() + (calibrationXy.getX() * _h) + (ek0 * calibrationXy.getY()) - (ek0 / _v) * (- gp.getV() + calibrationGp.getV() + (calibrationXy.getY() * _v) - (nk0 * calibrationXy.getX()))) / (_h + (nk0 * ek0) / _v);
        double fy = (- gp.getV() + calibrationGp.getV() + (calibrationXy.getY() * _v) + (nk0 * (fx - calibrationXy.getX()))) / _v;

        /* better precision calculations with known x,y */

        _v = gridLVscale + (fx - calibrationXy.getX()) * vScale;
        _h = gridTHscale + (fy - calibrationXy.getY()) * hScale;

        fx = (gp.getH() - calibrationGp.getH() + (calibrationXy.getX() * _h) + (ek0 * calibrationXy.getY()) - (ek0 / _v) * (- gp.getV() + calibrationGp.getV() + (calibrationXy.getY() * _v) - (nk0 * calibrationXy.getX()))) / (_h + (nk0 * ek0) / _v);
        fy = (- gp.getV() + calibrationGp.getV() + (calibrationXy.getY() * _v) + (nk0 * (fx - calibrationXy.getX()))) / _v;

        int x = (int) fx;
        if ((fx - x) > 0.5) {
            x++;
        }
        int y = (int) fy;
        if ((fy - y) > 0.5) {
            y++;
        }

        proximite.setXy(x, y);

        return proximite;
    }

    private int[] verticalAxisByX(Vector xy, Position position) {
        int x = position.getX();
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

    private int[] horizontalAxisByY(Vector xy, Position position) {
        int y = position.getY();
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

    public static final class GMI extends Calibration {
        public GMI(InputStream in, String path) throws IOException {
            super(path);

            LineReader reader = new LineReader(in);
            reader.readLine(false); // ignore - intro line
            reader.readLine(false); // ignore - path to image file
            width = Integer.parseInt(reader.readLine(false));   // image width
            height = Integer.parseInt(reader.readLine(false));  // image height

            Vector xy = new Vector();
            Vector ll = new Vector();

            CharArrayTokenizer tokenizer = new CharArrayTokenizer();
            String line = reader.readLine(false);
            while (line != null) {
                if (line.startsWith("Additional Calibration Data"))
                    break;
                if (line.startsWith("Border and Scale")) {
                    line = reader.readLine(false);
                    if (line != null) {
                        xy.removeAllElements();
                        ll.removeAllElements();
                        tokenizer.init(line, ';', false);
                        parseBorder(tokenizer, xy, ll);
                    }
                    break;
                }
                if (line == LineReader.EMPTY_LINE) // '==' is ok
                    break;

                tokenizer.init(line, ';', false);
                parsePoint(tokenizer, xy, ll);

                line = null; // gc hint
                line = reader.readLine(false);
            }
            reader.dispose();
            reader = null; // gc hint

            tokenizer.dispose();
            tokenizer = null; // gc hint

            doFinal(null, new ProjectionSetup("Latitude/Longitude"), xy, ll);
        }

        private void parsePoint(CharArrayTokenizer tokenizer, Vector xy, Vector ll) {
            int x = CharArrayTokenizer.parseInt(tokenizer.next());
            int y = CharArrayTokenizer.parseInt(tokenizer.next());
            double lon = CharArrayTokenizer.parseDouble(tokenizer.next());
            double lat = CharArrayTokenizer.parseDouble(tokenizer.next());
            xy.addElement(new Position(x, y));
            ll.addElement(new QualifiedCoordinates(lat, lon));
        }

        private void parseBorder(CharArrayTokenizer tokenizer, Vector xy, Vector ll) {
            double lat = CharArrayTokenizer.parseDouble(tokenizer.next());
            double lon = CharArrayTokenizer.parseDouble(tokenizer.next());
            xy.addElement(new Position(0, 0));
            ll.addElement(new QualifiedCoordinates(lat, lon));
            lat = CharArrayTokenizer.parseDouble(tokenizer.next());
            lon = CharArrayTokenizer.parseDouble(tokenizer.next());
            xy.addElement(new Position(width - 1, height - 1));
            ll.addElement(new QualifiedCoordinates(lat, lon));
        }
    }

    public static class XML extends Calibration {
        private static final String TAG_NAME        = "name";
        private static final String TAG_POSITION    = "position";
        private static final String TAG_LATITUDE    = "latitude";
        private static final String TAG_LONGITUDE   = "longitude";
        private static final String TAG_IMAGEWIDTH  = "imageWidth";
        private static final String TAG_IMAGEHEIGHT = "imageHeight";

        public XML(InputStream in, String path) throws InvalidMapException {
            super(path);

            Vector xy = new Vector();
            Vector ll = new Vector();

            try {
                KXmlParser parser = new KXmlParser();
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
//                                if ((x0 == 0 && y0 == 0) || (x0 != 0 && y0 != 0)) {
                                    xy.addElement(new Position(x0, y0));
                                    ll.addElement(new QualifiedCoordinates(lat0, lon0));
//                                } else {
//#ifdef __LOG__
//                                    if (log.isEnabled()) log.debug("ignore cal point " + new Position(x0, y0));
//#endif
//                                }
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
            }

            doFinal(null, new ProjectionSetup("Latitude/Longitude"), xy, ll);
        }
    }

    public static final class J2N extends XML {
        public J2N(InputStream in, String path) throws InvalidMapException {
            super(in, path);
        }
    }

    /**
     * Slice info.
     */
    public static final class Best /*extends Calibration*/ {
        protected int width, height;
        protected int x, y;

        public Best(String path, boolean standard) throws InvalidMapException {
            this.x = this.y = this.width = this.height = -1;
            if (standard) {
                parseXy(path);
            } else { // single slice of gpska map
                x = y = 0;
            }
        }

        public static String getBasename(String path) throws InvalidMapException {
//            char[] n = path.toCharArray();
            int p0 = -1, p1 = -1;
            int i = 0;
//            for (int N = n.length - 4; i < N; i++) {
//                if ('_' == n[i]) {
            for (int N = path.length() - 4; i < N; i++) {
                if ('_' == path.charAt(i)) {
                    p0 = p1;
                    p1 = i;
                }
            }
            if (p0 == -1 || p1 == -1) {
                throw new InvalidMapException("Invalid slice filename: " + path);
            }

            return path.substring(0, p0);
        }

        public String getPath() {
            return (new StringBuffer(16)).append("_").append(x).append('_').append(y).append(".png").toString();
        }

        protected void fixDimension(int xNext, int yNext, int xs, int ys) {
            if (x + xs < xNext)
                xNext = x + xs;
            if (y + ys < yNext)
                yNext = y + ys;
            width = xNext - x;
            height = yNext - y;
        }

        private void parseXy(String path) throws InvalidMapException {
//            char[] n = path.toCharArray();
            int p0 = -1, p1 = -1;
            int i = 0;
//            for (int N = n.length - 4; i < N; i++) {
//                if ('_' == n[i]) {
            for (int N = path.length() - 4; i < N; i++) {
                if ('_' == path.charAt(i)) {
                    p0 = p1;
                    p1 = i;
                }
            }
            if (p0 == -1 || p1 == -1) {
                throw new InvalidMapException("Invalid slice filename: " + path);
            }

            x = parseInt(path, p0 + 1, p1);
            y = parseInt(path, p1 + 1, i);
        }

        private static int parseInt(String value, int offset, int end) {
            if (offset == end || value == null) {
                throw new NumberFormatException("No input");
            }

            int result = 0;

            while (offset < end) {
//                char ch = value[offset++];
                char ch = value.charAt(offset++);
                if (ch >= '0' && ch <= '9') {
                    result *= 10;
                    result += ch - '0';
                } else {
                    throw new NumberFormatException("Not a digit: " + ch);
                }
            }

            return result;
        }
    }

    public static final class Ozi extends Calibration {
        private static final String PROJ_TRANSVERSE_MERCATOR = "Transverse Mercator";
/*
        public Position computeAbsolutePosition(Calibration parent) {
            int absx = parent.positions[0].getX() - positions[0].getX();
            int absy = parent.positions[0].getY() - positions[0].getY();
            return new Position(absx, absy);
        }
*/

        public Ozi(InputStream in, String path) throws IOException {
            super(path);

            int lines = 0;
            CharArrayTokenizer tokenizer = new CharArrayTokenizer();
            String projectionType = PROJ_TRANSVERSE_MERCATOR;
            Vector xy = new Vector(4), ll = new Vector(4)/*, utm = new Vector()*/;
            Datum datum = null;
            ProjectionSetup projectionSetup = null;

            LineReader reader = new LineReader(in);
            String line = reader.readLine(false);
            while (line != null) {
                lines++;
                if (line.startsWith("Point")) {
                    tokenizer.init(line, ',', true);
                    boolean b = parsePoint(tokenizer, xy, ll);
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("point parsed? " + b);
//#endif
                } else
                if (line.startsWith("Map Projection")) {
                    tokenizer.init(line, ',', false);
                    projectionType = parseProjectionType(tokenizer);
                    /*
                     * projection setup for known grids
                     */
                    if ("Latitude/Longitude".equals(projectionType)) {
                        projectionSetup = new ProjectionSetup("Latitude/Longitude");
                    } else if ("(BNG) British National Grid".equals(projectionType)) {
                            projectionSetup = new Mercator.ProjectionSetup(projectionType,
                                                                           null, -2D, 49D,
                                                                           0.9996012717,
                                                                           400000, -100000);
                    } else if ("(SG) Swedish Grid".equals(projectionType)) {
                            projectionSetup = new Mercator.ProjectionSetup(projectionType,
                                                                           null, 15.808277777778, 0D,
                                                                           1D,
                                                                           1500000, 0);
                    }
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("projection type: " + projectionType);
//#endif
                } else if (line.startsWith("Projection Setup")) {
                    /*
                     * not-crippled Ozi calibration - use MMPXY/LL instead
                     */
                    xy.removeAllElements();
                    ll.removeAllElements();

                    if (PROJ_TRANSVERSE_MERCATOR.equals(projectionType)) {
                        tokenizer.init(line, ',', true);
                        projectionSetup = parseProjectionSetup(tokenizer);
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("projection setup parsed");
//#endif
                    }
                } else if (line.startsWith("MMPXY")) {
                    tokenizer.init(line, ',', false);
                    boolean b = parseXY(tokenizer, xy);
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("mmpxy parsed? " + b);
//#endif
                } else if (line.startsWith("MMPLL")) {
                    tokenizer.init(line, ',', false);
                    boolean b = parseLL(tokenizer, ll);
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("mmpll parsed? " + b);
//#endif
                } else if (line.startsWith("IWH")) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("parse IWH");
//#endif
                    tokenizer.init(line, ',', false);
                    parseIwh(tokenizer);
                } else {
                    if (lines == 5) {
                        tokenizer.init(line, ',', false);
                        datum = (Datum) Config.datumMappings.get("map:" + parseDatum(tokenizer));
                    }
                }
                line = null; // gc hint
                line = reader.readLine(false);
            }
            reader.dispose();
            reader = null; // gc hint

            // dispose tokenizer
            tokenizer.dispose();
            tokenizer = null;

            // fix projection
            if ("(UTM) Universal Transverse Mercator".equals(projectionType)) {
                projectionSetup = Mercator.getUTMSetup((QualifiedCoordinates) ll.firstElement());
            }

            // check
            if (width == -1  || height == -1) {
                throw new InvalidMapException("Invalid map dimension");
            }

            // paranoia
            if (xy.size() != ll.size()) {
                throw new IllegalStateException("MMPXY:MMPLL size mismatch");
            }

            doFinal(datum, projectionSetup, xy, ll);
        }

        private boolean parsePoint(CharArrayTokenizer tokenizer,
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

                xy.addElement(new Position(x, y));
                ll.addElement(new QualifiedCoordinates(lat, lon));

            } catch (NumberFormatException e) {
                throw new InvalidMapException("Invalid Projection Setup", e);
            } catch (NullPointerException e) {
                throw new InvalidMapException("Invalid Projection Setup", e);
            }

            return true;
        }

        private String parseProjectionType(CharArrayTokenizer tokenizer) throws InvalidMapException {
            tokenizer.next(); // Map Projection
            CharArrayTokenizer.Token token = tokenizer.next();

            return token.toString();
        }

        private String parseDatum(CharArrayTokenizer tokenizer) {
            return tokenizer.next().toString();
        }

        private Mercator.ProjectionSetup parseProjectionSetup(CharArrayTokenizer tokenizer)
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

                return new Mercator.ProjectionSetup(PROJ_TRANSVERSE_MERCATOR,
                                                    null, lonOrigin, latOrigin,
                                                    k, falseEasting, falseNorthing);

            } catch (NumberFormatException e) {
                throw new InvalidMapException("Invalid Projection Setup", e);
            }
        }

        private boolean parseXY(CharArrayTokenizer tokenizer, Vector xy) throws InvalidMapException {
            try {
                tokenizer.next(); // MMPXY
                tokenizer.next(); // index [1-4]
                int x = CharArrayTokenizer.parseInt(tokenizer.next());
                int y = CharArrayTokenizer.parseInt(tokenizer.next());
                xy.addElement(new Position(x, y));
            } catch (NumberFormatException e) {
                throw new InvalidMapException("Invalid Projection Setup", e);
            }

            return true;
        }

        private boolean parseLL(CharArrayTokenizer tokenizer, Vector ll) {
            try {
                tokenizer.next(); // MMPLL
                tokenizer.next(); // index [1-4]
                double lon = CharArrayTokenizer.parseDouble(tokenizer.next());
                double lat = CharArrayTokenizer.parseDouble(tokenizer.next());
                ll.addElement(new QualifiedCoordinates(lat, lon));
            } catch (NumberFormatException e) {
                return false;
            }

            return true;
        }

        private void parseIwh(CharArrayTokenizer tokenizer) {
            try {
                tokenizer.next(); // IWH
                tokenizer.next(); // Map Image Width/Height
                width = CharArrayTokenizer.parseInt(tokenizer.next());
                height = CharArrayTokenizer.parseInt(tokenizer.next());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid IWH");
            }
        }
    }

/*
    // TODO optimize
    public static final class ProximitePosition extends Position {
        public ProximitePosition(int x, int y) {
            super(x, y);
        }

        public void decrementX() {
            x -= 1;
        }

        public void incrementX() {
            x += 1;
        }

        public void decrementY() {
            y -= 1;
        }

        public void incrementY() {
            y += 1;
        }

        public Position getPosition() {
            return new Position(x, y);
        }
    }
*/
}
