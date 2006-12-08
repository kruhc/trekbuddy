// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps;

import api.location.QualifiedCoordinates;

import cz.kruch.j2se.util.StringTokenizer;
import cz.kruch.track.util.CharArrayTokenizer;
//#ifdef __LOG__
import cz.kruch.track.util.Logger;
//#endif
import cz.kruch.track.ui.Position;
import cz.kruch.track.io.LineReader;

import java.util.Vector;
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

    // calibration points
    protected Position[] positions;
    protected QualifiedCoordinates[] coordinates;

    // helper
    private ProximitePosition proximite;

    // range and scales
    private QualifiedCoordinates[] range;
    private double xScale, yScale;

    protected Calibration(String path) {
        this.path = path;
        this.proximite = new ProximitePosition(0, 0);
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

    protected void doFinal() {
        computeGrid();
        computeRange();
    }

    private int gridTHx;
    private int gridTHy;
    private double gridTHlon;
    private double gridTHscale;

    private int gridBHx;
    private int gridBHy;
    private double gridBHlon;
    private double gridBHscale;

    private int gridLVy;
    private int gridLVx;
    private double gridLVlat;
    private double gridLVscale;

    private int gridRVy;
    private int gridRVx;
    private double gridRVlat;
    private double gridRVscale;

    double halfHStep;
    double halfVStep;

    private void computeGrid() {
        int[] index = horizontalAxisByY(new Position(0, 0));
        gridTHx = positions[index[0]].getX();
        gridTHlon = coordinates[index[0]].getLon();
        gridTHy = (positions[index[0]].getY() + positions[index[1]].getY()) >> 1;
        gridTHscale = Math.abs((coordinates[index[1]].getLon() - coordinates[index[0]].getLon()) / (positions[index[1]].getX() - positions[index[0]].getX()));

        index = horizontalAxisByY(new Position(0, getHeight()));
        gridBHx = positions[index[0]].getX();
        gridBHlon = coordinates[index[0]].getLon();
        gridBHy = (positions[index[0]].getY() + positions[index[1]].getY()) >> 1;
        gridBHscale = Math.abs((coordinates[index[1]].getLon() - coordinates[index[0]].getLon()) / (positions[index[1]].getX() - positions[index[0]].getX()));

        index = verticalAxisByX(new Position(0, 0));
        gridLVy = positions[index[0]].getY();
        gridLVlat = coordinates[index[0]].getLat();
        gridLVx = (positions[index[0]].getX() + positions[index[1]].getX()) >> 1;
        gridLVscale = Math.abs((coordinates[index[1]].getLat() - coordinates[index[0]].getLat()) / (positions[index[1]].getY() - positions[index[0]].getY()));

        index = verticalAxisByX(new Position(getWidth(), 0));
        gridRVy = positions[index[0]].getY();
        gridRVlat = coordinates[index[0]].getLat();
        gridRVx = (positions[index[0]].getX() + positions[index[1]].getX()) >> 1;
        gridRVscale = Math.abs((coordinates[index[1]].getLat() - coordinates[index[0]].getLat()) / (positions[index[1]].getY() - positions[index[0]].getY()));
        
        halfHStep = Math.min(gridTHscale / 2D, gridBHscale / 2D);
        halfVStep = Math.min(gridLVscale / 2D, gridRVscale / 2D);
    }

    private void computeRange() {
        range = new QualifiedCoordinates[4];
        range[0] = transform(new Position(0, 0));
        range[1] = transform(new Position(0, width));
        range[2] = transform(new Position(height, 0));
        range[3] = transform(new Position(width, height));
        xScale = Math.abs((range[3].getLon() - range[0].getLon()) / width);
        yScale = Math.abs((range[3].getLat() - range[0].getLat()) / height);
    }

    // TODO optimize access to range
    public boolean isWithin(QualifiedCoordinates coordinates) {
        double lat = coordinates.getLat();
        double lon = coordinates.getLon();
        QualifiedCoordinates[] _range = range;
        return (lat <= _range[0].getLat() && lat >= _range[3].getLat())
                && (lon >= _range[0].getLon() && lon <= _range[3].getLon());
    }

    public QualifiedCoordinates[] getRange() {
        return range;
    }

    public QualifiedCoordinates transform(Position position) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("transform " + position);
//#endif

        int _gridRVx = gridRVx;
        int _gridLVx = gridLVx;
        int _gridTHy = gridTHy;
        int _gridBHy = gridBHy;

        int x = position.getX();
        int y = position.getY();

        double latLshare;
        double latRshare;
        if (x <= _gridLVx) {
            latLshare = 1D;
            latRshare = 0D;
        } else if (x >= _gridRVx) {
            latLshare = 0D;
            latRshare = 1D;
        } else {
            latLshare = 1 - Math.abs((double) (x - _gridLVx) / (_gridRVx - _gridLVx));
            latRshare = 1 - Math.abs((double) (x - _gridRVx) / (_gridRVx - _gridLVx));
        }
        double latL = (gridLVlat - (y - gridLVy) * gridLVscale);
        double latR = (gridRVlat - (y - gridRVy) * gridRVscale);
        double lat = latL * latLshare + latR * latRshare;

        double lonTshare;
        double lonBshare;
        if (y <= _gridTHy) {
            lonTshare = 1D;
            lonBshare = 0D;
        } else if (y >= _gridBHy) {
            lonTshare = 0D;
            lonBshare = 1D;
        } else {
            lonTshare = 1 - Math.abs((double) (y - _gridTHy) / (_gridTHy - _gridBHy));
            lonBshare = 1 - Math.abs((double) (y - _gridBHy) / (_gridTHy - _gridBHy));
        }
        double lonT = (gridTHlon + (x - gridTHx) * gridTHscale);
        double lonB = (gridBHlon + (x - gridBHx) * gridBHscale);
        double lon = lonT * lonTshare + lonB * lonBshare;

/* reverse check assertion
        if (!(position instanceof ProximitePosition)) {
            if (log.isEnabled()) log.debug("check reverse xf");
            Position check = transform(new QualifiedCoordinates(lat, lon));
            if (Math.abs(position.getX() - check.getX()) > 1) {
                throw new AssertionFailedException("Reverse longitude transformation failed - diff is " + (position.getX() - check.getX()));
            }
            if (Math.abs(position.getY() - check.getY()) > 1) {
                throw new AssertionFailedException("Reverse latitude transformation failed - diff is " + (position.getY() - check.getY()));
            }
            if (log.isEnabled()) log.debug("reversed position: " + check);
        }
*/

        return new QualifiedCoordinates(lat, lon);
    }

    public Position transform(QualifiedCoordinates coords) {
        ProximitePosition proximite = proximitePosition(coords);
        QualifiedCoordinates qc = transform(proximite);

        while ((qc.getLon() > coords.getLon()) && !minorDiff(qc, coords, 0)) {
            proximite.decrementX();
            qc = null;
            qc = transform(proximite);
        }
        while ((qc.getLon() < coords.getLon()) && !minorDiff(qc, coords, 0)) {
            proximite.incrementX();
            qc = null;
            qc = transform(proximite);
        }
        while ((qc.getLat() > coords.getLat()) && !minorDiff(qc, coords, 1)) {
            proximite.incrementY();
            qc = null;
            qc = transform(proximite);
        }
        while ((qc.getLat() < coords.getLat()) && !minorDiff(qc, coords, 1)) {
            proximite.decrementY();
            qc = null;
            qc = transform(proximite);
        }

        return proximite;
    }

    private ProximitePosition proximitePosition(QualifiedCoordinates coordinates) {
        QualifiedCoordinates leftTopQc = range[0];

        double dlon = coordinates.getLon() - leftTopQc.getLon();
        double dlat = coordinates.getLat() - leftTopQc.getLat();

        int intDx = (int) (dlon / xScale);
        int intDy = (int) (dlat / yScale);

        proximite.setXy(0 + intDx, 0 - intDy);

        return proximite;
    }

    private boolean minorDiff(QualifiedCoordinates qc1, QualifiedCoordinates qc2, int axis) {
        if (axis == 0) {
            return Math.abs(qc1.getLon() - qc2.getLon()) < halfHStep;
        } else {
            return Math.abs(qc1.getLat() - qc2.getLat()) < halfVStep;
        }
    }

    private int[] verticalAxisByX(Position position) {
        int x = position.getX();
        int i0 = -1, i1 = -1;
        int d0 = Integer.MAX_VALUE, d1 = Integer.MAX_VALUE;
        for (int N = positions.length, i = 0; i < N; i++) {
            int dx = Math.abs(x - positions[i].getX());
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

        if (Math.abs(position.getY() - positions[i0].getY()) < Math.abs(position.getY() - positions[i1].getY())) {
            return new int[]{ i0, i1 };
        } else {
            return new int[]{ i1, i0 };
        }
    }

    private int[] horizontalAxisByY(Position position) {
        int y = position.getY();
        int i0 = -1, i1 = -1;
        int d0 = Integer.MAX_VALUE, d1 = Integer.MAX_VALUE;
        for (int N = positions.length, i = 0; i < N; i++) {
            int dy = Math.abs(y - positions[i].getY());
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

        if (Math.abs(position.getX() - positions[i0].getX()) < Math.abs(position.getX() - positions[i1].getX())) {
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

            Vector pos = new Vector();
            Vector coords = new Vector();
            String line = reader.readLine(false);
            while (line != null) {
                if (line.startsWith("Additional Calibration Data"))
                    break;
                if (line.startsWith("Border and Scale"))
                    break;
                parsePoint(line, pos, coords);
                line = null; // gc hint
                line = reader.readLine(false);
            }
            reader.dispose();
            reader = null; // gc hint

            if ((pos.size() < 2) || (coords.size() < 2)) {
                throw new InvalidMapException("Too few calibration points");
            }

            positions = new Position[pos.size()];
            coordinates = new QualifiedCoordinates[coords.size()];
            pos.copyInto(positions);
            coords.copyInto(coordinates);

            doFinal();
        }

        private void parsePoint(String line, Vector pos, Vector coords) {
            StringTokenizer st = new StringTokenizer(line, ";", false);
            int x = Integer.parseInt(st.nextToken());
            int y = Integer.parseInt(st.nextToken());
            double delka = Double.parseDouble(st.nextToken());
            double sirka = Double.parseDouble(st.nextToken());
            pos.addElement(new Position(x, y));
            coords.addElement(new QualifiedCoordinates(sirka, delka));
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

            Vector pos = new Vector();
            Vector coords = new Vector();

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
                                if ((x0 == 0 && y0 == 0) || (x0 != 0 && y0 != 0)) {
                                    pos.addElement(new Position(x0, y0));
                                    coords.addElement(new QualifiedCoordinates(lat0, lon0));
                                } else {
//#ifdef __LOG__
                                    if (log.isEnabled()) log.debug("ignore cal point " + new Position(x0, y0));
//#endif
                                }
                            }
                            currentTag = null;
                        } break;
                        case XmlPullParser.TEXT: {
                            if (currentTag != null) {
                                String text = parser.getText().trim();
                                if (TAG_NAME.equals(currentTag)) {
                                    this.path = text + ".png";
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

            if ((pos.size() < 2) || (coords.size() < 2)) {
                throw new InvalidMapException("Too few calibration points");
            }

            positions = new Position[pos.size()];
            coordinates = new QualifiedCoordinates[coords.size()];
            pos.copyInto(positions);
            coords.copyInto(coordinates);

            doFinal();
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

        public Best(String path) throws InvalidMapException {
            this.x = this.y = this.width = this.height = -1;
            parseXy(path);
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

        protected void gpskaFix() {
            // single slice of gpska map
            x = y = 0;
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
/*
        public Position computeAbsolutePosition(Calibration parent) {
            int absx = parent.positions[0].getX() - positions[0].getX();
            int absy = parent.positions[0].getY() - positions[0].getY();
            return new Position(absx, absy);
        }
*/

        public Ozi(InputStream in, String path) throws IOException {
            super(path);

            int count = 0;
            CharArrayTokenizer tokenizer = new CharArrayTokenizer();
            Vector xy = new Vector(4), ll = new Vector(4)/*, utm = new Vector()*/;
            LineReader reader = new LineReader(in);

            String line = reader.readLine(false);
            while (line != null) {
                if (line.startsWith("Point")) {
                    tokenizer.init(line, ',', true);
                    boolean b = parsePoint(tokenizer, xy, ll/*, utm*/);
                    if (b) count++;
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("point parsed? " + b);
//#endif
                } else if (line.startsWith("MMPXY")) {
                    if (count < 2) {
                        tokenizer.init(line, ',', false);
                        boolean b = parseXY(tokenizer, xy);
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("mmpxy parsed? " + b);
//#endif
                    }
                } /*else if (line.startsWith("Projection Setup")) {
                    if (utm.size() > 0) {
                        parseProjectionSetup(line, ll, utm);
                        if (log.isEnabled()) log.debug("projection setup parsed");
                    }
                }*/ else if (line.startsWith("MMPLL")) {
                    if (count < 2) {
                        tokenizer.init(line, ',', false);
                        boolean b = parseLL(tokenizer, ll);
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("mmpll parsed? " + b);
//#endif
                    }
                } else if (line.startsWith("IWH")) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("parse IWH");
//#endif
                    tokenizer.init(line, ',', false);
                    parseIwh(tokenizer);
                }
                line = null; // gc hint
                line = reader.readLine(false);
            }
            reader.dispose();
            reader = null; // gc hint

            // dispose tokenizer
            tokenizer.dispose();
            tokenizer = null;

            // check
            if (width == -1  || height == -1) {
                throw new InvalidMapException("Invalid dimension");
            }

            // paranoia
            if (xy.size() != ll.size()) {
                throw new IllegalStateException("MMPXY:MMPLL size mismatch");
            }

            positions = new Position[xy.size()];
            coordinates = new QualifiedCoordinates[ll.size()];
            xy.copyInto(positions);
            ll.copyInto(coordinates);

            // gc hints
            xy.removeAllElements();
            ll.removeAllElements();

            doFinal();
        }

        private boolean parsePoint(CharArrayTokenizer tokenizer,
                                   Vector xy, Vector ll/*, Vector utm*/) {
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
                    if (index > 16) {
                        break;
                    }
                }

                // empty cal point
                if (x < -1 || y < -1) {
                    return false;
                }
                if (lat == 0D || lon == 0D) {
                    return false;
                }

                Position p = new Position(x, y);
                xy.addElement(p);
                QualifiedCoordinates qc = new QualifiedCoordinates(lat, lon);
                ll.addElement(qc);

            /* else if (easting != null && easting.length() > 0 && northing != null & northing.length() > 0) {
                Integer east = Integer.valueOf(easting);
                Integer north = Integer.valueOf(northing);

                Position p = new Position(x, y);
                xy.addElement(p);
                utm.addElement(new Object[]{ east, north, zone });
            */

            } catch (NumberFormatException e) {
                return false;
            } catch (NullPointerException e) {
                return false;
            }

            return true;
        }

/*
        private void parseProjectionSetup(String line, Vector ll, Vector utm) {
            int index = 0;
            String lato = null, lono = null;
            String k = null;
            String feast = null, fnorth = null;
            StringTokenizer st = new StringTokenizer(line, ",", true);
            while (st.hasMoreTokens()) {
                String token = st.nextToken().trim();
                if (",".equals(token)) {
                    index++;
                } else if (index == 1) {
                    lato = token;
                } else if (index == 2) {
                    lono = token;
                } else if (index == 3) {
                    k = token;
                } else if (index == 4) {
                    feast = token;
                } else if (index == 5) {
                    fnorth = token;
                } else if (index > 5) {
                    break;
                }
            }

            try {
                double latOrigin = Double.parseDouble(lato);
                double lonOrigin = Double.parseDouble(lono);
                double k0 = Double.parseDouble(k);
                double falseEasting = Double.parseDouble(feast);
                double falseNorthing = Double.parseDouble(fnorth);

                for (Enumeration e = utm.elements(); e.hasMoreElements(); ) {
                    Object[] item = (Object[]) e.nextElement();
                    Integer easting = (Integer) item[0];
                    Integer northing = (Integer) item[1];
                    String zone = (String) item[2];

                    double[] result = Mercator.UTMtoLL(lonOrigin, k0, zone.charAt(0),
                                                       easting.doubleValue() - (falseEasting - 500000),
                                                       northing.doubleValue());
                    ll.addElement(new QualifiedCoordinates(result[1], result[0]));
                }

            } catch (NumberFormatException e) {
                throw new RuntimeException("Invalid UTM Projection Setup");
            } catch (NullPointerException e) {
                throw new RuntimeException("Invalid UTM Projection Setup");
            }
        }
*/

        private boolean parseXY(CharArrayTokenizer tokenizer, Vector xy) {
            try {
                tokenizer.next(); // MMPXY
                tokenizer.next(); // index [1-4]
                int x = CharArrayTokenizer.parseInt(tokenizer.next());
                int y = CharArrayTokenizer.parseInt(tokenizer.next());

                Position p = new Position(x, y);
                xy.addElement(p);

            } catch (NumberFormatException e) {
                return false;
            }

            return true;
        }

        private boolean parseLL(CharArrayTokenizer tokenizer, Vector ll) {
            try {
                tokenizer.next(); // MMPLL
                tokenizer.next(); // index [1-4]
                double lon = CharArrayTokenizer.parseDouble(tokenizer.next());
                double lat = CharArrayTokenizer.parseDouble(tokenizer.next());

                QualifiedCoordinates qc = new QualifiedCoordinates(lat, lon);
                ll.addElement(qc);

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
}
