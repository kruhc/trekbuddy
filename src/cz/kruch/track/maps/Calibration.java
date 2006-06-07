// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps;

import cz.kruch.j2se.util.StringTokenizer;
import cz.kruch.track.ui.Position;
import api.location.QualifiedCoordinates;

import java.util.Vector;

public abstract class Calibration {
    // map/slice path
    protected String path;

    // map/slice dimensions
    protected int width = -1;
    protected int height = -1;

    // calibration point info
    protected Position[] positions;
    protected QualifiedCoordinates[] coordinates;

    protected Calibration() {
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

    // TODO make this unneeded
    public Position[] getPositions() {
        return positions;
    }

    public QualifiedCoordinates transform(Position position) {
        int[] index = findNearestCalibrationPoints(position);

        Position ref0 = positions[index[0]];

        int dx = position.getX() - ref0.getX();
        int dy = position.getY() - ref0.getY();

        double xScale = Math.abs(coordinates[index[1]].getLon() - coordinates[index[0]].getLon()) / Math.abs(positions[index[1]].getX() - positions[index[0]].getX());
        double yScale = Math.abs(coordinates[index[1]].getLat() - coordinates[index[0]].getLat()) / Math.abs(positions[index[1]].getY() - positions[index[0]].getY());

        double lon = coordinates[index[0]].getLon() + dx * xScale;
        double lat = coordinates[index[0]].getLat() - dy * yScale;

        return new QualifiedCoordinates(lat, lon);
    }

    public Position transform(QualifiedCoordinates coordinates) {
        int[] index = findNearestCalibrationPoints(coordinates);

        QualifiedCoordinates ref0 = this.coordinates[index[0]];
        QualifiedCoordinates ref1 = this.coordinates[index[1]];

        double dlon = coordinates.getLon() - ref0.getLon();
        double dlat = coordinates.getLat() - ref0.getLat();

        double xScale = Math.abs(ref1.getLon() - ref0.getLon()) / Math.abs(positions[index[1]].getX() - positions[index[0]].getX());
        double yScale = Math.abs(ref1.getLat() - ref0.getLat()) / Math.abs(positions[index[1]].getY() - positions[index[0]].getY());

//        System.out.println("dlon = " + dlon + "; dlat = " + dlat);
//        System.out.println("xscale = " + xScale + "; yscale = " + yScale);

        Double dx = new Double(dlon / xScale);
        Double dy = new Double(dlat / yScale);
        int intDx = dx.intValue();
        int intDy = dy.intValue();
        if ((dx.doubleValue() - intDx) > 0.50D) {
//            System.out.println("fixing dx rounding " + dx + ";" + intDx);
            intDx++;
        }
        if ((dy.doubleValue() - intDy) > 0.50D) {
//            System.out.println("fixing dy rounding " + dy + ";" + intDy);
            intDy++;
        }

        int x = positions[index[0]].getX() + intDx;
        int y = positions[index[0]].getY() - intDy;

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

    public static class GMI extends Calibration {
        public static Calibration create(String content) {
            return new GMI(content);
        }

        private GMI(String content) {
            super();
            StringTokenizer st = new StringTokenizer(content, "\r\n", false);
            st.nextToken();                             // "Map Calibration maps file v2.0"
            parsePath(st.nextToken());                  // path to file
            width = Integer.parseInt(st.nextToken());   // image width
            height = Integer.parseInt(st.nextToken());  // image height
            Vector pos = new Vector();
            Vector coords = new Vector();
            while (st.hasMoreTokens()) {
                parsePoint(st.nextToken(), pos, coords);
            }
            if ((pos.size() < 2) || (coords.size() < 2)) {
                throw new IllegalArgumentException("Too few calibration points");
            }
            positions = new Position[pos.size()];
            coordinates = new QualifiedCoordinates[coords.size()];
            pos.copyInto(positions);
            coords.copyInto(coordinates);
        }

        private void parsePath(String line) {
            int idxUnix = line.lastIndexOf('/');
            int idxWindows = line.lastIndexOf('\\');
            int idx = idxUnix > -1 ? idxUnix : (idxWindows > -1 ? idxWindows : -1);
            if (idx > -1) {
                path = line.substring(idx + 1);
            } else {
                path = line;
            }
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
}
