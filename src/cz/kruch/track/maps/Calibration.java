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
import api.location.CartesianCoordinates;
import api.location.Ellipsoid;

import cz.kruch.track.util.Mercator;
import cz.kruch.track.ui.Position;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.Resources;

import java.util.Vector;
import java.util.Hashtable;
import java.io.IOException;
import java.io.InputStream;

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

    protected static final ProjectionSetup LATLON_PROJ_SETUP = new ProjectionSetup(ProjectionSetup.PROJ_LATLON);

    // map path and filename
    private String path;
    protected String imgname;

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

    protected Calibration() {
    }

    protected final void init(String path) {
        this.path = path;
        this.proximite = new Position(0, 0);
    }

    public String getPath() {
        return path;
    }

    public String getImgname() {
        return imgname;
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

    QualifiedCoordinates[] getRange() {
        return range;
    }

    final boolean isWithin(final QualifiedCoordinates coordinates) {
/* too rough
        final double lat = coordinates.getLat();
        final double lon = coordinates.getLon();
        QualifiedCoordinates[] _range = range;
        return (lat <= _range[0].getLat() && lat >= _range[3].getLat())
                && (lon >= _range[0].getLon() && lon <= _range[3].getLon());
*/
        final Position p = transform(coordinates);
        final int x = p.getX();
        if (x >= 0 && x < width) {
            final int y = p.getY();
            if (y >= 0 && y < height) {
                return true;
            }
        }
        return false;
    }

    final QualifiedCoordinates transform(final Position position) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("transform " + position);
//#endif

        QualifiedCoordinates qc;

        if (calibrationGp instanceof CartesianCoordinates) {
            CartesianCoordinates utm = (CartesianCoordinates) toGp(position);
            qc = Mercator.MercatortoLL(utm, getDatum().ellipsoid,
                                       (Mercator.ProjectionSetup) projectionSetup);
/*
            qc.setDatum(datum == Datum.DATUM_WGS_84 ? null : datum);
*/
            CartesianCoordinates.releaseInstance(utm);
        } else /*if (calibrationGp instanceof QualifiedCoordinates)*/ {
            qc = (QualifiedCoordinates) toGp(position);
        }

        return qc;
    }

    final Position transform(final QualifiedCoordinates coordinates) {
        GeodeticPosition gp;

        if (calibrationGp instanceof CartesianCoordinates) {
            gp = Mercator.LLtoMercator(coordinates, getDatum().ellipsoid,
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

        if (gp instanceof CartesianCoordinates) {
            CartesianCoordinates.releaseInstance((CartesianCoordinates) gp);
        }

        return proximite;
    }

    protected final void doFinal(final Datum datum, final ProjectionSetup setup,
                                 final Vector xy, final Vector ll) throws InvalidMapException {
        // assertions
        if ((xy.size() < 2) || (ll.size() < 2)) {
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_TOO_FEW_CALPOINTS));
        }

        // dimension check
        if (width == -1 || height == -1) {
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_INVALID_MAP_DIMENSION));
        }

        // paranoia
        if (xy.size() != ll.size()) {
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_MM_SIZE_MISMATCH));
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
            Ellipsoid ellipsoid = getDatum().ellipsoid;

            /*
             * performance optimization: reuse existing vector
             */

            // lat,lon -> easting,northing
            for (int N = ll.size(), i = 0; i < N; i++) {
                QualifiedCoordinates local = (QualifiedCoordinates) ll.elementAt(i);
                CartesianCoordinates utm = Mercator.LLtoMercator(local, ellipsoid, msetup);
                ll.setElementAt(utm, i);
            }

            // remember main calibration point easting-northing and zone
            calibrationGp = (GeodeticPosition) ll.elementAt(0);

            // compute pixel grid for TM
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

    private void computeGrid(final Vector xy, final Vector gp) {
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

    private GeodeticPosition toGp(final Position position) {
        int dy = position.getY() - calibrationXy.getY();
        int dx = position.getX() - calibrationXy.getX();
        double h = calibrationGp.getH() + (ek0 * dy) + (dx * (gridTHscale + dy * hScale));
        double v = calibrationGp.getV() + (nk0 * dx) - (dy * (gridLVscale + dx * vScale));

        if (calibrationGp instanceof CartesianCoordinates) {
            return CartesianCoordinates.newInstance(((Mercator.ProjectionSetup) projectionSetup).zone, h, v);
        } else /*if (calibrationGp instanceof QualifiedCoordinates)*/ {
            return QualifiedCoordinates.newInstance(v, h);
        }
    }

    private static int[] verticalAxisByX(final Vector xy, final Position position) {
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

    private static int[] horizontalAxisByY(final Vector xy, final Position position) {
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

    public static Calibration newInstance(InputStream in, String url) throws IOException {
        return newInstance(in, url, url);
    }

    public static Calibration newInstance(InputStream in, String path, String url) throws IOException {
        Calibration c;

        try {
            Class factory;
            if (url.endsWith(Calibration.OZI_EXT)) {
                factory = Class.forName("cz.kruch.track.maps.OziCalibration");
            } else if (url.endsWith(Calibration.GMI_EXT)) {
                factory = Class.forName("cz.kruch.track.maps.GmiCalibration");
            } else if (url.endsWith(Calibration.XML_EXT) || url.endsWith(Calibration.J2N_EXT)) {
                factory = Class.forName("cz.kruch.track.maps.J2NCalibration");
            } else {
                return null;
            }
            c = (Calibration) factory.newInstance();
        } catch (Exception e) {
            // TODO this is wrong
            throw new IOException(e.toString());
        }

        if (c != null) {
            c.init(in, path);
        }

        return c;
    }

    abstract void init(InputStream in, String path) throws IOException;
}
