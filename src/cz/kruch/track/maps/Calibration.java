// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps;

import cz.kruch.j2se.util.StringTokenizer;
import cz.kruch.track.ui.Position;
import cz.kruch.track.util.Logger;
import api.location.QualifiedCoordinates;

import java.util.Vector;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;

import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;

public abstract class Calibration {
    // log
    private static final Logger log = new Logger("Calibration");

    // map/slice path
    protected String path;

    // map/slice dimensions
    protected int width = -1;
    protected int height = -1;

    // calibration point info
    protected Position[] positions;
    protected QualifiedCoordinates[] coordinates;

    protected Calibration(String path) {
        this.path = parsePath(path) + ".png";
    }

    public abstract Position computeAbsolutePosition(Calibration parent);

    public String getPath() {
        return path;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    // TODO make this unneeded
    public Position[] getPositions() {
        return positions;
    }

    public QualifiedCoordinates transform(Position position) {
        int[] index = findNearestCalibrationPoints(position);

        double lon = 0D;
        double lat = 0D;

        int x = position.getX();
        int y = position.getY();

        // check x and y match with any of the calibration points
        for (int N = positions.length, i = 0; i < N; i++) {
            Position p0 = positions[i];
            if (x == p0.getX() && lon == 0D) {
                if (log.isEnabled()) log.debug("direct use lon of cal point " + i);
                lon = coordinates[i].getLon();
            }
            if (y == p0.getY() && lat == 0D) {
                if (log.isEnabled()) log.debug("direct use lat of cal point " + i);
                lat = coordinates[i].getLat();
            }
        }

        /*
         * because it is unlikely to hit x or y calpoint,
         * it is better for performance to have local refs to
         * neighbours positions and coordinates to avoid
         * array index check etc
         */

        Position ref0 = positions[index[0]];
        Position ref1 = positions[index[1]];
        QualifiedCoordinates refC1 = coordinates[index[1]];
        QualifiedCoordinates refC0 = coordinates[index[0]];

        // no match for x with any calpoint
        if (lon == 0D) {
            int dx = x - ref0.getX();
            double xScale = Math.abs(refC1.getLon() - refC0.getLon()) / Math.abs(ref1.getX() - ref0.getX());
            lon = refC0.getLon() + dx * xScale;  // closer neighbour is ref point
        }

        // no match for y with any calpoint
        if (lat == 0D) {
            int dy = y - ref0.getY();
            double yScale = Math.abs(refC1.getLat() - refC0.getLat()) / Math.abs(ref1.getY() - ref0.getY());
            lat = refC0.getLat() - dy * yScale;  // closer neighbour is ref point
        }

        return new QualifiedCoordinates(lat, lon);
    }

    public Position transform(QualifiedCoordinates coordinates) {
        int[] index = findNearestCalibrationPoints(coordinates);

        Position ref0 = positions[index[0]]; // closer neighbour is ref point
        QualifiedCoordinates refC0 = this.coordinates[index[0]];
        QualifiedCoordinates refC1 = this.coordinates[index[1]];

        double dlon = coordinates.getLon() - refC0.getLon();
        double dlat = coordinates.getLat() - refC0.getLat();

        double xScale = Math.abs(refC1.getLon() - refC0.getLon()) / Math.abs(positions[index[1]].getX() - ref0.getX());
        double yScale = Math.abs(refC1.getLat() - refC0.getLat()) / Math.abs(positions[index[1]].getY() - ref0.getY());

        Double dx = new Double(dlon / xScale);
        Double dy = new Double(dlat / yScale);
        int intDx = dx.intValue();
        int intDy = dy.intValue();
        if ((dx.doubleValue() - intDx) > 0.50D) {
            intDx++;
        }
        if ((dy.doubleValue() - intDy) > 0.50D) {
            intDy++;
        }

        int x = ref0.getX() + intDx;
        int y = ref0.getY() - intDy;

        return new Position(x, y);
    }

    private int[] findNearestCalibrationPoints(Position position) {
        double r0 = Double.MAX_VALUE;
        double r1 = Double.MAX_VALUE;
        int i0 = -1;
        int i1 = -1;
        int x = position.getX();
        int y = position.getY();
        for (int N = positions.length, i = 0; i < N; i++) {
            int dx = x - positions[i].getX();
            int dx2 = dx * dx;
            int dy = y - positions[i].getY();
            int dy2 = dy * dy;
            int r = dx2 + dy2; // sqrt not necessary, we just compare
            if (r < r0) {
                if (i1 == -1) { // r0 - >r1
                    r1 = r0;
                    i1 = i0;
                }
                r0 = r;
                i0 = i;
            } else if (r < r1) {
                r1 = r;
                i1 = i;
            }
        }

//        System.out.println("nearest calibration points indexes are " + i0 + "," + i1);

        return i0 < i1 ? new int[]{ i0, i1 } : new int[]{ i1, i0 };
    }

    private int[] findNearestCalibrationPoints(QualifiedCoordinates coordinates) {
        double r0 = Double.MAX_VALUE;
        double r1 = Double.MAX_VALUE;
        int i0 = -1;
        int i1 = -1;
        double lon = coordinates.getLon();
        double lat = coordinates.getLat();
        for (int N = this.coordinates.length, i = 0; i < N; i++) {
            double dlon = lon - this.coordinates[i].getLon();
            double dlon2 = dlon * dlon;
            double dlat = lat - this.coordinates[i].getLat();
            double dlat2 = dlat * dlat;
            double r = dlat2 + dlon2; // sqrt not necessary, we just compare
            if (r < r0) {
                if (i1 == -1) { // r0 - >r1
                    r1 = r0;
                    i1 = i0;
                }
                r0 = r;
                i0 = i;
            } else if (r < r1) {
                r1 = r;
                i1 = i;
            }
        }

//        System.out.println("nearest calibration points indexes are " + i0 + "," + i1);

        return i0 < i1 ? new int[]{ i0, i1 } : new int[]{ i1, i0 };
    }

    private String parsePath(String line) {
        int idxUnix = line.lastIndexOf('/');
        int idxWindows = line.lastIndexOf('\\');
        int idx = idxUnix > -1 ? idxUnix : (idxWindows > -1 ? idxWindows : -1);
        if (idx > -1) {
            line = line.substring(idx + 1);
        }
        idx = line.lastIndexOf('.');
        if (idx > -1) {
            line = line.substring(0, idx);
        }

        return line;
    }

    public static class GMI extends Calibration {

        public Position computeAbsolutePosition(Calibration parent) {
            int absx = parent.positions[0].getX() - positions[0].getX();
            int absy = parent.positions[0].getY() - positions[0].getY();
            return new Position(absx, absy);
        }

        public GMI(String content, String path) throws InvalidMapException {
            super(path);

            StringTokenizer st = new StringTokenizer(content, "\r\n", false);
            st.nextToken();                             // "Map Calibration maps file v2.0"
            st.nextToken();                             // path to image file
            width = Integer.parseInt(st.nextToken());   // image width
            height = Integer.parseInt(st.nextToken());  // image height

            Vector pos = new Vector();
            Vector coords = new Vector();
            while (st.hasMoreTokens()) {
                parsePoint(st.nextToken(), pos, coords);
            }
            if ((pos.size() < 2) || (coords.size() < 2)) {
                throw new InvalidMapException("Too few calibration points");
            }
            positions = new Position[pos.size()];
            coordinates = new QualifiedCoordinates[coords.size()];
            pos.copyInto(positions);
            coords.copyInto(coordinates);
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

        public Position computeAbsolutePosition(Calibration parent) {
            if (parent.getClass().equals(getClass())) {
                return new Position(0, 0);
            }

            StringTokenizer st = new StringTokenizer(path, "_.", false);
            st.nextToken();
            int absx = Integer.parseInt(st.nextToken());
            int absy = Integer.parseInt(st.nextToken());

            return new Position(absx, absy);
        }

        public XML(String content, String path) throws InvalidMapException {
            super(path);

            Vector pos = new Vector();
            Vector coords = new Vector();

            try {
                KXmlParser parser = new KXmlParser();
                parser.setInput(new InputStreamReader(new ByteArrayInputStream(content.getBytes())));

                boolean keepParsing = true;
                String currentTag = null;

                int x0 = -1, y0 = -1;
                double lat0 = 0D, lon0 = 0D;

                while (keepParsing) {
                    switch (parser.next()) {
                        case XmlPullParser.START_TAG: {
                            currentTag = parser.getName();
//                            System.out.println("start of " + currentTag);
                            if (TAG_POSITION.equals(currentTag)) {
                                x0 = Integer.parseInt(parser.getAttributeValue(null, "x"));
                                y0 = Integer.parseInt(parser.getAttributeValue(null, "y"));
                            }
                        } break;
                        case XmlPullParser.END_TAG: {
//                            System.out.println("end of " + parser.getName());
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
//                                System.out.println("content of " + currentTag + " " + text);
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
        }
    }

    public static class J2N extends XML {
        public J2N(String content, String path) throws InvalidMapException {
            super(content, path);
        }

        public Position computeAbsolutePosition(Calibration parent) {
            return new Position(0, 0);
        }
    }

    /**
     * For simplified J2N format (aka Best) - no slice calibrations.
     */
    public static class Best extends Calibration {
        private Position position;

        public Best(String path) {
            super(path);
        }

        public Position computeAbsolutePosition(Calibration parent) {
            // share calibration with parent
            positions = parent.positions;
            coordinates = parent.coordinates;

            if (parent instanceof Best || parent instanceof GMI || parent instanceof J2N || parent instanceof Ozi) {
                // position is encoded in filename (tb, j2n)
                StringTokenizer st = new StringTokenizer(path, "_.", false);
                st.nextToken();
                int absx = Integer.parseInt(st.nextToken());
                int absy = Integer.parseInt(st.nextToken());
                position = new Position(absx, absy);
            } else { // gpska
                position = new Position(0, 0);
            }

            return position;
        }

        public void fixDimension(Calibration parent, Slice[] siblings) {
            int xNext = parent.width;
            int yNext = parent.height;
            for (int N = siblings.length, i = 0; i < N; i++) {
                Slice s = siblings[i];
                Position p = s.getAbsolutePosition();
                int x = p.getX();
                int y = p.getY();
                if ((x > position.getX()) && (x < xNext)) {
                    xNext = x;
                }
                if ((y > position.getY()) && (y < yNext)) {
                    yNext = y;
                }
            }
            width = xNext - position.getX();
            height = yNext - position.getY();
        }
    }

    public static class Ozi extends Calibration {
        public Position computeAbsolutePosition(Calibration parent) {
            int absx = parent.positions[0].getX() - positions[0].getX();
            int absy = parent.positions[0].getY() - positions[0].getY();
            return new Position(absx, absy);
        }

        public Ozi(String content, String path) throws InvalidMapException {
            super(path);

            int count = 0;
            Vector xy = new Vector(), ll = new Vector();
            StringTokenizer st = new StringTokenizer(content, "\n\r", false);
            while (st.hasMoreTokens()) {
                String line = st.nextToken();
                if (line.startsWith("Point")) {
                    boolean b = parsePoint(line, xy, ll);
                    if (b) count++;
                    if (log.isEnabled()) log.debug("point parsed? " + b);
                } else if (line.startsWith("MMPXY")) {
                    if (count < 2) {
                        boolean b = parseXy(line, xy);
                        if (log.isEnabled()) log.debug("mmpxy parsed? " + b);
                    }
                } else if (line.startsWith("MMPLL")) {
                    if (count < 2) {
                        boolean b = parseLl(line, ll);
                        if (log.isEnabled()) log.debug("mmpll parsed? " + b);
                    }
                } else if (line.startsWith("IWH")) {
                    if (log.isEnabled()) log.debug("parse IWH");
                    parseIwh(line);
                }
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
        }

        private boolean parsePoint(String line, Vector xy, Vector ll) {
            int index = 0;
            String px = null, py = null;
            String lath = null, latm = null, lats = "N";
            String lonh = null, lonm = null, lons = "E";
            StringTokenizer st = new StringTokenizer(line, ",", true);
            while (st.hasMoreTokens()) {
                String token = st.nextToken().trim();
                if (",".equals(token)) {
                    index++;
                }/* else if (" ".equals(token) || "\t".equals(token)) {
                    // nothing
                }*/ else if (index == 2) {
                    px = token;
                } else if (index == 3) {
                    py = token;
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
                } else if (index > 11) {
                    break;
                }
            }

            try {
                int x = Integer.parseInt(px);
                int y = Integer.parseInt(py);
                double lat = Integer.parseInt(lath) + (Double.parseDouble(latm) / 60D);
                if (lats.startsWith("S")) lat *= -1.0D;
                double lon = Integer.parseInt(lonh) + (Double.parseDouble(lonm) / 60D);
                if (lons.startsWith("W")) lon *= -1.0D;

                Position p = new Position(x, y);
                xy.addElement(p);
                QualifiedCoordinates qc = new QualifiedCoordinates(lat, lon);
                ll.addElement(qc);

            } catch (NumberFormatException e) {
                return false;
            } catch (NullPointerException e) {
                return false;
            }

            return true;
        }

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
                e.printStackTrace();
                return false;
            }

            return true;
        }

        private boolean parseLl(String line, Vector ll) {
            try {
                StringTokenizer st = new StringTokenizer(line, ",", false);
                st.nextToken();
                String index = st.nextToken();
                double lat = Double.parseDouble(st.nextToken().trim());
                double lon = Double.parseDouble(st.nextToken().trim());

                QualifiedCoordinates qc = new QualifiedCoordinates(lat, lon);
                ll.addElement(qc);

            } catch (NumberFormatException e) {
                e.printStackTrace();
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
}
