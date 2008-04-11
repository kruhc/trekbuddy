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
import cz.kruch.track.util.ExtraMath;
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
    protected int wh;

    // map datum and projection params
    private Datum datum;
    private ProjectionSetup projectionSetup;

    // main (left-top) calibration point
    private int cxyx, cxyy;
    private double cgph, cgpv;

    // calibration coordinate system flag
    private boolean cartesian;
    
    // reusable info
    private Position proximite;

    // range // TODO get rid of it?
    private QualifiedCoordinates[] range;

    protected Calibration() {
    }

    protected final void init(final String path) {
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
        return (this.wh >> 16) & 0x0000ffff;
    }

    public int getHeight() {
        return this.wh & 0x0000ffff;
    }

    public void setWidth(int width) {
        this.wh |= (width << 16) & 0xffff0000;
    }

    public void setHeight(int height) {
        this.wh |= height & 0x0000ffff;
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
        if (range == null) {
            computeRange();
        }
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
        if (x >= 0 && x < getWidth()) {
            final int y = p.getY();
            if (y >= 0 && y < getHeight()) {
                return true;
            }
        }
        return false;
    }

    final QualifiedCoordinates transform(final Position position) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("transform " + position);
//#endif

        final QualifiedCoordinates qc;

        if (cartesian) {
            final CartesianCoordinates utm = (CartesianCoordinates) toGp(position);
            qc = Mercator.MercatortoLL(utm, getDatum().ellipsoid,
                                       (Mercator.ProjectionSetup) projectionSetup);
/*
            qc.setDatum(datum == Datum.DATUM_WGS_84 ? null : datum);
*/
            CartesianCoordinates.releaseInstance(utm);
        } else {
            qc = (QualifiedCoordinates) toGp(position);
        }

        return qc;
    }

    final Position transform(final QualifiedCoordinates coordinates) {
        final GeodeticPosition gp;

        if (cartesian) {
            gp = Mercator.LLtoMercator(coordinates, getDatum().ellipsoid,
                                       (Mercator.ProjectionSetup) projectionSetup);
        } else {
            gp = coordinates;
        }

        double _v = v2;
        double _h = h2;

        final double cgph = this.cgph;
        final double cgpv = this.cgpv;
        final int cxyx = this.cxyx;
        final int cxyy = this.cxyy;

        double fx = (gp.getH() - cgph + (cxyx * _h) + (ek0 * cxyy) - (ek0 / _v) * (- gp.getV() + cgpv + (cxyy * _v) - (nk0 * cxyx))) / (_h + (nk0 * ek0) / _v);
        double fy = (- gp.getV() + cgpv + (cxyy * _v) + (nk0 * (fx - cxyx))) / _v;

        /* better precision calculations with known x,y */

        _v = gridLVscale + (fx - cxyx) * vScale;
        _h = gridTHscale + (fy - cxyy) * hScale;

        fx = (gp.getH() - cgph + (cxyx * _h) + (ek0 * cxyy) - (ek0 / _v) * (- gp.getV() + cgpv + (cxyy * _v) - (nk0 * cxyx))) / (_h + (nk0 * ek0) / _v);
        fy = (- gp.getV() + cgpv + (cxyy * _v) + (nk0 * (fx - cxyx))) / _v;

        if (cartesian) {
            CartesianCoordinates.releaseInstance((CartesianCoordinates) gp);
        }
        
        final int x = ExtraMath.round(fx);
        final int y = ExtraMath.round(fy);

        proximite.setXy(x, y);

        return proximite;
    }

    protected final void doFinal(final Datum datum, final ProjectionSetup setup,
                                 final Vector xy, final Vector ll) throws InvalidMapException {
        // assertions
        if ((xy.size() < 2) || (ll.size() < 2)) {
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_TOO_FEW_CALPOINTS));
        }

        // dimension check
        if (wh == 0) {
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
        final Position calibrationXy = (Position) xy.elementAt(0);
        cxyx = calibrationXy.getX();
        cxyy = calibrationXy.getY();

        // compute grid
        if (projectionSetup instanceof Mercator.ProjectionSetup) {

            // use cartesian system
            cartesian = true;

            // setup is for Mercator projection
            final Mercator.ProjectionSetup msetup = (Mercator.ProjectionSetup) projectionSetup;
            final Ellipsoid ellipsoid = getDatum().ellipsoid;

            /*
             * performance optimization: reuse existing vector
             */

            // lat,lon -> easting,northing
            for (int N = ll.size(), i = 0; i < N; i++) {
                final QualifiedCoordinates local = (QualifiedCoordinates) ll.elementAt(i);
                final CartesianCoordinates utm = Mercator.LLtoMercator(local, ellipsoid, msetup);
                ll.setElementAt(utm, i);
            }

        }

        // remember main calibration point
        GeodeticPosition calibrationGp = (GeodeticPosition) ll.elementAt(0);
        cgph = calibrationGp.getH();
        cgpv = calibrationGp.getV();

        // compute pixel grid
        computeGrid(xy, ll);
    }

    private double gridTHscale, gridLVscale;
    private double ek0, nk0;

    private double h2, v2;
    private double hScale, vScale;

    private void computeGrid(final Vector xy, final Vector gp) {
        Position p;

        p = Position.newInstance(getWidth(), 0);
        int[] index = verticalAxisByX(xy, p);
        Position.releaseInstance(p);

        final double gridRVscale = Math.abs((((GeodeticPosition) gp.elementAt(index[1])).getV() - ((GeodeticPosition) gp.elementAt(index[0])).getV()) / (((Position) xy.elementAt(index[1])).getY() - ((Position) xy.elementAt(index[0])).getY()));
        final int v1 = ((Position) xy.elementAt(index[0])).getX();

        p = Position.newInstance(0, 0);
        index = horizontalAxisByY(xy, p);
        Position.releaseInstance(p);

        final int dx = (((Position) xy.elementAt(index[1])).getX() - ((Position) xy.elementAt(index[0])).getX());
        gridTHscale = Math.abs((((GeodeticPosition) gp.elementAt(index[1])).getH() - ((GeodeticPosition) gp.elementAt(index[0])).getH()) / dx);
        final int h0 = ((Position) xy.elementAt(index[0])).getY();
        final double nk0d = (((Position) xy.elementAt(index[1])).getY() - h0) * gridRVscale;
        nk0 = (((GeodeticPosition) gp.elementAt(index[1])).getV() + nk0d - ((GeodeticPosition) gp.elementAt(index[0])).getV()) / dx;

        p = Position.newInstance(0, getHeight());
        index = horizontalAxisByY(xy, p);
        Position.releaseInstance(p);

        final double gridBHscale = Math.abs((((GeodeticPosition) gp.elementAt(index[1])).getH() - ((GeodeticPosition) gp.elementAt(index[0])).getH()) / (((Position) xy.elementAt(index[1])).getX() - ((Position) xy.elementAt(index[0])).getX()));
        final int h1 = ((Position) xy.elementAt(index[0])).getY();

        p = Position.newInstance(0, 0);
        index = verticalAxisByX(xy, p);
        Position.releaseInstance(p);

        final int dy = (((Position) xy.elementAt(index[1])).getY() - ((Position) xy.elementAt(index[0])).getY());
        gridLVscale = Math.abs((((GeodeticPosition) gp.elementAt(index[1])).getV() - ((GeodeticPosition) gp.elementAt(index[0])).getV()) / dy);
        final int v0 = ((Position) xy.elementAt(index[0])).getX();
        final double ek0d = (((Position) xy.elementAt(index[1])).getX() - v0) * gridBHscale;
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
        final Position p = proximite;
        p.setXy(0, 0);
        range[0] = transform(p);
        p.setXy(getWidth(), 0);
        range[1] = transform(p);
        p.setXy(0, getHeight());
        range[2] = transform(p);
        p.setXy(getWidth(), getHeight());
        range[3] = transform(p);
    }

    private GeodeticPosition toGp(final Position position) {
        final int dy = position.getY() - cxyy;
        final int dx = position.getX() - cxyx;
        final double h = cgph + (ek0 * dy) + (dx * (gridTHscale + dy * hScale));
        final double v = cgpv + (nk0 * dx) - (dy * (gridLVscale + dx * vScale));

        if (cartesian) {
            return CartesianCoordinates.newInstance(((Mercator.ProjectionSetup) projectionSetup).zone, h, v);
        } else {
            return QualifiedCoordinates.newInstance(v, h);
        }
    }

    private static int[] verticalAxisByX(final Vector xy, final Position position) {
        final int x = position.getX();
        int i0 = -1, i1 = -1;
        int d0 = Integer.MAX_VALUE, d1 = Integer.MAX_VALUE;
        for (int N = xy.size(), i = 0; i < N; i++) {
            final int dx = Math.abs(x - ((Position) xy.elementAt(i)).getX());
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
            final int dy = Math.abs(y - ((Position) xy.elementAt(i)).getY());
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

    public static boolean isCalibration(String url) {
        url = url.toLowerCase();
        return url.endsWith(Calibration.OZI_EXT)
                || url.endsWith(Calibration.GMI_EXT)
                || url.endsWith(Calibration.XML_EXT)
                || url.endsWith(Calibration.J2N_EXT);
    }

    public static Calibration newInstance(final InputStream in, final String url) throws IOException {
        return newInstance(in, url, url);
    }

    public static Calibration newInstance(final InputStream in, final String path, final String url) throws IOException {
        final Calibration c;

        try {
            final Class factory;
            final String lurl = url.toLowerCase();
            if (lurl.endsWith(Calibration.OZI_EXT)) {
                factory = Class.forName("cz.kruch.track.maps.OziCalibration");
            } else if (lurl.endsWith(Calibration.GMI_EXT)) {
                factory = Class.forName("cz.kruch.track.maps.GmiCalibration");
            } else if (lurl.endsWith(Calibration.XML_EXT) || lurl.endsWith(Calibration.J2N_EXT)) {
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

    protected static short getDimension(final int i) throws InvalidMapException {
        if (i <= Short.MAX_VALUE) {
            return (short) i;
        }
        throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_MAP_TOO_BIG));
    }

    abstract void init(InputStream in, String path) throws IOException;
}
