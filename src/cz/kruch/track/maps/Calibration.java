// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps;

import api.location.QualifiedCoordinates;

import cz.kruch.j2se.util.StringTokenizer;
import cz.kruch.j2se.io.BufferedReader;
import cz.kruch.track.util.Logger;
import cz.kruch.track.ui.Position;

import java.util.Vector;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.InputStream;

import org.xmlpull.v1.XmlPullParser;
import org.kxml2.io.KXmlParser;

public abstract class Calibration {
    // log
    private static final Logger log = new Logger("Calibration");

    public static final Vector KNOWN_EXTENSIONS = new Vector();

    // map/slice path // TODO bad design - path to slice should be in Slice
    protected String path;

    // map/slice dimensions
    protected int width = -1;
    protected int height = -1;

    // calibration points
    protected Position[] positions;
    protected QualifiedCoordinates[] coordinates;

    // range and scales
    private QualifiedCoordinates[] range;
    private double xScale, yScale;

    static {
        KNOWN_EXTENSIONS.addElement("gmi");
        KNOWN_EXTENSIONS.addElement("map");
        KNOWN_EXTENSIONS.addElement("xml");
        KNOWN_EXTENSIONS.addElement("j2n");
    }

    protected Calibration(String path) {
        this.path = path;
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
        gridTHy = (positions[index[0]].getY() + positions[index[1]].getY()) / 2;
        gridTHscale = Math.abs((coordinates[index[1]].getLon() - coordinates[index[0]].getLon()) / (positions[index[1]].getX() - positions[index[0]].getX()));

        index = horizontalAxisByY(new Position(0, getHeight()));
        gridBHx = positions[index[0]].getX();
        gridBHlon = coordinates[index[0]].getLon();
        gridBHy = (positions[index[0]].getY() + positions[index[1]].getY()) / 2;
        gridBHscale = Math.abs((coordinates[index[1]].getLon() - coordinates[index[0]].getLon()) / (positions[index[1]].getX() - positions[index[0]].getX()));

        index = verticalAxisByX(new Position(0, 0));
        gridLVy = positions[index[0]].getY();
        gridLVlat = coordinates[index[0]].getLat();
        gridLVx = (positions[index[0]].getX() + positions[index[1]].getX()) / 2;
        gridLVscale = Math.abs((coordinates[index[1]].getLat() - coordinates[index[0]].getLat()) / (positions[index[1]].getY() - positions[index[0]].getY()));

        index = verticalAxisByX(new Position(getWidth(), 0));
        gridRVy = positions[index[0]].getY();
        gridRVlat = coordinates[index[0]].getLat();
        gridRVx = (positions[index[0]].getX() + positions[index[1]].getX()) / 2;
        gridRVscale = Math.abs((coordinates[index[1]].getLat() - coordinates[index[0]].getLat()) / (positions[index[1]].getY() - positions[index[0]].getY()));
        
        halfHStep = Math.min(gridTHscale / 2, gridBHscale / 2);
        halfVStep = Math.min(gridLVscale / 2, gridRVscale / 2);
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
        return (lat <= range[0].getLat() && lat >= range[3].getLat())
                && (lon >= range[0].getLon() && lon <= range[3].getLon());
    }

    public QualifiedCoordinates[] getRange() {
        return range;
    }

    public QualifiedCoordinates transform(Position position) {
        if (log.isEnabled()) log.debug("transform " + position);

        int x = position.getX();
        int y = position.getY();

        double latLshare;
        double latRshare;
        if (x <= gridLVx) {
            latLshare = 1D;
            latRshare = 0D;
        } else if (x >= gridRVx) {
            latLshare = 0D;
            latRshare = 1D;
        } else {
            latLshare = 1 - Math.abs((double) (x - gridLVx) / (gridRVx - gridLVx));
            latRshare = 1 - Math.abs((double) (x - gridRVx) / (gridRVx - gridLVx));
        }
        double latL = (gridLVlat - (y - gridLVy) * gridLVscale);
        double latR = (gridRVlat - (y - gridRVy) * gridRVscale);
        double lat = latL * latLshare + latR * latRshare;

        double lonTshare;
        double lonBshare;
        if (y <= gridTHy) {
            lonTshare = 1D;
            lonBshare = 0D;
        } else if (y >= gridBHy) {
            lonTshare = 0D;
            lonBshare = 1D;
        } else {
            lonTshare = 1 - Math.abs((double) (y - gridTHy) / (gridTHy - gridBHy));
            lonBshare = 1 - Math.abs((double) (y - gridBHy) / (gridTHy - gridBHy));
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
            qc = transform(proximite);
        }
        while ((qc.getLon() < coords.getLon()) && !minorDiff(qc, coords, 0)) {
            proximite.incrementX();
            qc = transform(proximite);
        }
        while ((qc.getLat() > coords.getLat()) && !minorDiff(qc, coords, 1)) {
            proximite.incrementY();
            qc = transform(proximite);
        }
        while ((qc.getLat() < coords.getLat()) && !minorDiff(qc, coords, 1)) {
            proximite.decrementY();
            qc = transform(proximite);
        }

        return proximite;
    }

    private ProximitePosition proximitePosition(QualifiedCoordinates coordinates) {
        QualifiedCoordinates leftTopQc = range[0];

        double dlon = coordinates.getLon() - leftTopQc.getLon();
        double dlat = coordinates.getLat() - leftTopQc.getLat();

        Double dx = new Double(dlon / xScale);
        Double dy = new Double(dlat / yScale);

        int intDx = dx.intValue();
        int intDy = dy.intValue();

        int x = 0 + intDx;
        int y = 0 - intDy;

        return new Calibration.ProximitePosition(x, y);
    }

    private final boolean minorDiff(QualifiedCoordinates qc1, QualifiedCoordinates qc2, int axis) {
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

            BufferedReader reader = new BufferedReader(new InputStreamReader(in), Map.SMALL_BUFFER_SIZE);
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
                parsePoint(line, pos, coords);
                line = reader.readLine(false);
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
                parser.setInput(new InputStreamReader(in));

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
                                    if (log.isEnabled()) log.debug("ignore cal point " + new Position(x0, y0));
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
        private String path;
        protected int width = -1, height = -1;
        protected int x = -1, y = -1;

        public Best(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }

/*
        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
*/

        protected void computeAbsolutePosition(boolean friendly) throws InvalidMapException {
            if (friendly) { // slice of trekbuddy or j2n map
                parseXy();
            } else { // single slice of gpska map
                x = y = 0;
            }
        }

        protected void fixDimension(int xNext, int yNext, int xs, int ys) {
            if (x + xs < xNext)
                xNext = x + xs;
            if (y + ys < yNext)
                yNext = y + ys;
            width = xNext - x;
            height = yNext - y;
        }

        private void parseXy() throws InvalidMapException {
            char[] n = path.toCharArray();
            int p0 = -1, p1 = -1;
            int i = 0;
            for (int N = n.length - 4; i < N; i++) {
                if ('_' == n[i]) {
                    p0 = p1;
                    p1 = i;
                }
            }
            if (p0 == -1 || p1 == -1) {
                throw new InvalidMapException("Invalid slice filename");
            }

            x = parseInt(n, p0 + 1, p1);
            y = parseInt(n, p1 + 1, i);
        }

        private static int parseInt(char[] value, int offset, int end) {
            if (offset == end || value == null) {
                throw new NumberFormatException("No input");
            }

            int result = 0;

            while (offset < end) {
                char ch = value[offset++];
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
            Vector xy = new Vector(), ll = new Vector()/*, utm = new Vector()*/;
            BufferedReader reader = new BufferedReader(new InputStreamReader(in), Map.SMALL_BUFFER_SIZE);
            String line = reader.readLine(false);
            while (line != null) {
                if (line.startsWith("Point")) {
                    boolean b = parsePoint(line, xy, ll/*, utm*/);
                    if (b) count++;
                    if (log.isEnabled()) log.debug("point parsed? " + b);
                } else if (line.startsWith("MMPXY")) {
                    if (count < 2) {
                        boolean b = parseXy(line, xy);
                        if (log.isEnabled()) log.debug("mmpxy parsed? " + b);
                    }
                } /*else if (line.startsWith("Projection Setup")) {
                    if (utm.size() > 0) {
                        parseProjectionSetup(line, ll, utm);
                        if (log.isEnabled()) log.debug("projection setup parsed");
                    }
                }*/ else if (line.startsWith("MMPLL")) {
                    if (count < 2) {
                        boolean b = parseLl(line, ll);
                        if (log.isEnabled()) log.debug("mmpll parsed? " + b);
                    }
                } else if (line.startsWith("IWH")) {
                    if (log.isEnabled()) log.debug("parse IWH");
                    parseIwh(line);
                }
                line = reader.readLine(false);
            }

            // check
            if (width == -1  || height == -1) {
                throw new InvalidMapException("Invalid dimension");
            }

            // paranoia
            if (xy.size() != ll.size()) {
                throw new IllegalStateException("Collection size mismatch");
            }

            positions = new Position[xy.size()];
            coordinates = new QualifiedCoordinates[ll.size()];
            xy.copyInto(positions);
            ll.copyInto(coordinates);

            doFinal();
        }

        private boolean parsePoint(String line, Vector xy, Vector ll/*, Vector utm*/) {
            int index = 0;
            String px = null, py = null;
            String lath = null, latm = null, lats = "N";
            String lonh = null, lonm = null, lons = "E";
            String easting = null, northing = null, zone = "N";
            StringTokenizer st = new StringTokenizer(line, ",", true);
            while (st.hasMoreTokens()) {
                String token = st.nextToken().trim();
                if (",".equals(token)) {
                    index++;
                } else if (index == 2) {
                    px = token;
                    if (token == null || token.length() == 0) {
                        index = Integer.MAX_VALUE;
                    }
                } else if (index == 3) {
                    py = token;
                    if (token == null || token.length() == 0) {
                        index = Integer.MAX_VALUE;
                    }
                } else if (index == 6) {
                    lath = token;
                } else if (index == 7) {
                    latm = token;
                } else if (index == 8) {
                    lats = token;
                } else if (index == 9) {
                    lonh = token;
                } else if (index == 10) {
                    lonm = token;
                } else if (index == 11) {
                    lons = token;
                } else if (index == 14) {
                    easting = token;
                } else if (index == 15) {
                    northing = token;
                } else if (index == 16) {
                    zone = token;
                }
                if (index > 16) {
                    break;
                }
            }

            // empty cal point
            if (px == null || px.length() == 0 || py == null || py.length() == 0) {
                return false;
            }
            if (lath == null || lath.length() == 0 || lonh == null || lonh.length() == 0) {
                return false;
            }

            try {
                int x = Integer.parseInt(px);
                int y = Integer.parseInt(py);

                if (lath != null && lath.length() > 0 && lonh != null && lonh.length() > 0) {
                    double lat = Integer.parseInt(lath) + (Double.parseDouble(latm) / 60D);
                    if (lats.startsWith("S")) lat *= -1.0D;
                    double lon = Integer.parseInt(lonh) + (Double.parseDouble(lonm) / 60D);
                    if (lons.startsWith("W")) lon *= -1.0D;

                    Position p = new Position(x, y);
                    xy.addElement(p);
                    QualifiedCoordinates qc = new QualifiedCoordinates(lat, lon);
                    ll.addElement(qc);
                }/* else if (easting != null && easting.length() > 0 && northing != null & northing.length() > 0) {
                    Integer east = Integer.valueOf(easting);
                    Integer north = Integer.valueOf(northing);

                    Position p = new Position(x, y);
                    xy.addElement(p);
                    utm.addElement(new Object[]{ east, north, zone });
                }*/

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

        private boolean parseXy(String line, Vector xy) {
            try {
                StringTokenizer st = new StringTokenizer(line, ",", false);
                st.nextToken();
                String index = st.nextToken();
                int x = Integer.parseInt(st.nextToken().trim());
                int y = Integer.parseInt(st.nextToken().trim());

                Position p = new Position(x, y);
                xy.addElement(p);

            } catch (NumberFormatException e) {
                return false;
            }

            return true;
        }

        private boolean parseLl(String line, Vector ll) {
            try {
                StringTokenizer st = new StringTokenizer(line, ",", false);
                st.nextToken();
                String index = st.nextToken();
                double lon = Double.parseDouble(st.nextToken().trim());
                double lat = Double.parseDouble(st.nextToken().trim());

                QualifiedCoordinates qc = new QualifiedCoordinates(lat, lon);
                ll.addElement(qc);

            } catch (NumberFormatException e) {
                return false;
            }

            return true;
        }

        private void parseIwh(String line) {
            int index = 0;
            StringTokenizer st = new StringTokenizer(line, ",", true);
            while (st.hasMoreTokens()) {
                String token = st.nextToken().trim();
                if (",".equals(token)) {
                    index++;
                } else if (index == 2) {
                    width = Integer.parseInt(token);
                } else if (index == 3) {
                    height = Integer.parseInt(token);
                }
            }
        }
    }

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
    }
}
