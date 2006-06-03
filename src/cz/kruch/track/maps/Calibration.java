// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps;

import cz.kruch.j2se.util.StringTokenizer;
import cz.kruch.track.location.Position;
import cz.kruch.track.location.Coordinates;

public abstract class Calibration {
    // slice path
    protected String path;

    // slice dimensions
    protected int width = -1;
    protected int height = -1;

    // calibration point info
    protected Position[] positions = new Position[2];
    protected Coordinates[] coordinates = new Coordinates[2];

    // scale
    protected double xScale = 0.0;
    protected double yScale = 0.0;

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

    public Coordinates transform(Position position) {
        double dx0 = position.getX() - positions[0].getX();
        double dx02 = dx0 * dx0;
        double dy0 = position.getY() - positions[0].getY();
        double dy02 = dy0 * dy0;
        double r0 = Math.sqrt(dx02 + dy02);

        double dx1 = position.getX() - positions[1].getX();
        double dx12 = dx1 * dx1;
        double dy1 = position.getY() - positions[1].getY();
        double dy12 = dy1 * dy1;
        double r1 = Math.sqrt(dx12 + dy12);

        double lon, lat;
        if (r0 < r1) {
            lon = coordinates[0].getLon() + dx0 * xScale;
            lat = coordinates[0].getLat() + dy0 * yScale;
        } else {
            lon = coordinates[1].getLon() + dx1 * xScale;
            lat = coordinates[1].getLat() + dy1 * yScale;
        }
        Coordinates coordinates = new Coordinates(lon, lat);

        return coordinates;
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
            parsePoint(st.nextToken(), 0);              // calibration point
            parsePoint(st.nextToken(), 1);              // calibration point
            xScale = Math.abs(coordinates[1].getLon() - coordinates[0].getLon()) / Math.abs(positions[1].getX() - positions[0].getX());
            yScale = Math.abs(coordinates[1].getLat() - coordinates[0].getLat()) / Math.abs(positions[1].getY() - positions[0].getY());
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

        private void parsePoint(String line, int index) {
            StringTokenizer st = new StringTokenizer(line, ";", false);
            int x = Integer.parseInt(st.nextToken());
            int y = Integer.parseInt(st.nextToken());
            double delka = Double.parseDouble(st.nextToken());
            double sirka = Double.parseDouble(st.nextToken());
            positions[index] = new Position(x, y);
            coordinates[index] = new Coordinates(delka, sirka);
        }
    }
}
