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

package cz.kruch.track.util;

import api.location.QualifiedCoordinates;
import api.location.Datum;
import api.location.GeodeticPosition;
import api.location.CartesianCoordinates;

import java.util.Vector;

import cz.kruch.track.configuration.Config;

/**
 * Helper for Mercator transformation. And also Lambert (since 0.9.65).
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class Mercator {

    public static final class ProjectionSetup extends api.location.ProjectionSetup {
        public final double lonOrigin, latOrigin;
        public final double parallel1, parallel2;
        public final double k0;
        public final double falseEasting, falseNorthing;
        public final short zoneNumber;
        public final char zoneLetter;
        public final char[] zone;

        /**
         * UTM constructor.
         */
        public ProjectionSetup(String name,
                               final int zoneNumber, final char zoneLetter,
                               final double lonOrigin, final double latOrigin,
                               final double k0,
                               final double falseEasting, final double falseNorthing) {
            super(name);
            this.zoneNumber = (short) zoneNumber;
            this.zoneLetter = zoneLetter;
            this.zone = (new StringBuffer().append(zoneNumber).append(zoneLetter)).toString().toCharArray();
            this.lonOrigin = lonOrigin;
            this.latOrigin = latOrigin;
            this.parallel1 = this.parallel2 = Double.NaN;
            this.k0 = k0;
            this.falseEasting = falseEasting;
            this.falseNorthing = falseNorthing;
        }

        /**
         * Mercator and Transverse Mercator constructor.
         */
        public ProjectionSetup(String name, char[] zone,
                               final double lonOrigin, final double latOrigin,
                               final double k0,
                               final double falseEasting, final double falseNorthing) {
            super(name);
            this.zoneNumber = -1;
            this.zoneLetter = 'Z';
            this.zone = zone;
            this.lonOrigin = lonOrigin;
            this.latOrigin = latOrigin;
            this.parallel1 = this.parallel2 = Double.NaN;
            this.k0 = k0;
            this.falseEasting = falseEasting;
            this.falseNorthing = falseNorthing;
        }

        /**
         * Lambert constructor.
         */
        public ProjectionSetup(String name, char[] zone,
                               final double lonOrigin, final double latOrigin,
                               final double parallel1, final double parallel2,
                               final double falseEasting, final double falseNorthing) {
            super(name);
            this.zoneNumber = -1;
            this.zoneLetter = 'Z';
            this.zone = zone;
            this.lonOrigin = lonOrigin;
            this.latOrigin = latOrigin;
            this.parallel1 = parallel1;
            this.parallel2 = parallel2;
            this.k0 = 1D;
            this.falseEasting = falseEasting;
            this.falseNorthing = falseNorthing;
        }

        // TODO optimize
        public String toString() {
            if (PROJ_MERCATOR.equals(name)) {
                return super.toString();
            }

            StringBuffer sb = new StringBuffer(32);
            sb.append(name).append('{');
            if (zone != null) {
                sb.append(zone).append(',');
            }
            sb.append(lonOrigin).append(',').append(latOrigin).append(',');
            sb.append(k0);sb.append(',');
            sb.append(falseEasting).append(',').append(falseNorthing).append('}');

            return sb.toString();
        }
    }

    public static void initialize() {
        ntf = Config.getDatum("NTF");        
    }

    /*
     * Projection setup helpers.
     */

    private static ProjectionSetup cachedUtmSetup = null;

    public static ProjectionSetup getUTMSetup(QualifiedCoordinates qc) {
        final double lon = qc.getLon();
        final double lat = qc.getLat();

        int zoneNumber = (int) ((lon + 180) / 6) + 1;
        if (lat >= 56D && lat < 64D && lon >= 3D && lon < 12D) {
            zoneNumber = 32;
        }
        if (lat >= 72D && lat < 84D) {
            if (lon >= 0D && lon < 9D) zoneNumber = 31;
            else if (lon >= 9D && lon < 21D) zoneNumber = 33;
            else if (lon >= 21D && lon < 33D) zoneNumber = 35;
            else if (lon >= 33D && lon < 42D) zoneNumber = 37;
        }
        char zoneLetter = UTMLetterDesignator(lat);
        double lonOrigin = (zoneNumber - 1) * 6 - 180 + 3; // +3 puts origin in middle of zone
        double falseNorthing = lat < 0D ? 10000000D : 0D;

        if (cachedUtmSetup != null) {
            if (cachedUtmSetup.lonOrigin == lonOrigin
                && cachedUtmSetup.falseNorthing == falseNorthing
                && cachedUtmSetup.zoneNumber == zoneNumber
                && cachedUtmSetup.zoneLetter == zoneLetter) {
                return cachedUtmSetup;
            } else {
                cachedUtmSetup = null;
            }
        }

        cachedUtmSetup = new ProjectionSetup("UTM", zoneNumber, zoneLetter,
                                             lonOrigin, 0D,
                                             0.9996, 500000D, falseNorthing);

        return cachedUtmSetup;
    }

    public static ProjectionSetup getMercatorSetup(Vector ll) {
        double lmin = Double.MAX_VALUE;
        double lmax = Double.MIN_VALUE;

        for (int i = ll.size(); --i >= 0; ) {
            double lon = ((QualifiedCoordinates) ll.elementAt(i)).getLon();
            if (lon < lmin) {
                lmin = lon;
            }
            if (lmax < lon) {
                lmax = lon;
            }
        }

        return new ProjectionSetup(api.location.ProjectionSetup.PROJ_MERCATOR, null,
                                   (lmin + lmax) / 2, 0D,
                                   1D,
                                   0D, 0D);
    }

/*
    public static Datum getGoogleDatum(Vector ll, int width) {
        double lmin = Double.MAX_VALUE;
        double lmax = Double.MIN_VALUE;

        for (Enumeration e = ll.elements(); e.hasMoreElements(); ) {
            double lon = ((QualifiedCoordinates) e.nextElement()).getLon();
            if (lon < lmin) {
                lmin = lon;
            }
            if (lmax < lon) {
                lmax = lon;
            }
        }

        // calculate zoom level
        final double z0dpp = 0.0000107288360595703125;
        final double ln2 = 0.69314718056;
        double zNdpp = Math.abs(lmax - lmin) / width;
        double N = zNdpp / z0dpp;
        double Z = ExtraMath.ln(N) / ln2;
        int iZ = (int) Z;
        if (Z - iZ > 0.5) {
            iZ++;
        }

        return new Datum("(G)", new Datum.Ellipsoid(Integer.toString(iZ), ExtraMath.pow(2, 17 - iZ) * 256 / (2 * Math.PI), 0D),
                         0D, 0D, 0D);
    }
*/

    /*
     * Conversions.
     */

    public static Datum ntf;

    public static CartesianCoordinates LLtoUTM(QualifiedCoordinates qc) {
        return LLtoMercator(qc, Datum.DATUM_WGS_84.ellipsoid, getUTMSetup(qc));
    }

    public static CartesianCoordinates LLtoGrid(QualifiedCoordinates qc) {
        CartesianCoordinates utm = LLtoMercator(qc, Datum.contextDatum.ellipsoid, (Mercator.ProjectionSetup) ProjectionSetup.contextProjection);
        String ctxProjectionName = ProjectionSetup.contextProjection.name;

        /*
         * handle specific grids
         */

        if (api.location.ProjectionSetup.PROJ_BNG.equals(ctxProjectionName)) {
            if (utm.zone == null || utm.zone.length != 2) {
                utm.zone = new char[2];
            }
            utm.easting = ExtraMath.round(utm.easting);
            utm.northing = ExtraMath.round(utm.northing);
            final int ek = (int) (utm.easting / 500000);
            final int nk = (int) (utm.northing / 500000);
            if (ek > 0) {
                if (nk > 0) {
                    utm.zone[0] = 'O';
                } else {
                    utm.zone[0] = 'T';
                }
            } else {
                if (nk > 1) {
                    utm.zone[0] = 'H';
                } else if (nk > 0) {
                    utm.zone[0] = 'N';
                } else {
                    utm.zone[0] = 'S';
                }
            }
            final int row = (int) (utm.northing - nk * 500000) / 100000;
            final double column = (int) (utm.easting - ek * 500000) / 100000;
            int i = (int) ((4 - row) * 5 + column);
            if (i > ('H' - 'A')) { // skip 'I'
                i++;
            }
            utm.zone[1] = (char) ('A' + i);
            utm.easting -= (int) (utm.easting / ExtraMath.grade(utm.easting)) * ExtraMath.grade(utm.easting);
            utm.northing -= (int) (utm.northing / ExtraMath.grade(utm.northing)) * ExtraMath.grade(utm.northing);
        } else if (api.location.ProjectionSetup.PROJ_IG.equals(ctxProjectionName)) {
            if (utm.zone == null || utm.zone.length != 1) {
                utm.zone = new char[1];
            }
            utm.easting = ExtraMath.round(utm.easting);
            utm.northing = ExtraMath.round(utm.northing);
            final int ek = (int) (utm.easting / 100000);
            final int nk = (int) (utm.northing / 100000);
            switch (nk) {
                case 0:
                    utm.zone[0] = (char) ((byte) 'V' + ek);
                    break;
                case 1:
                    utm.zone[0] = (char) ((byte) 'Q' + ek);
                    break;
                case 2:
                    utm.zone[0] = (char) ((byte) 'L' + ek);
                    break;
                case 3:
                    utm.zone[0] = (char) ((byte) 'F' + ek);
                    break;
                case 4:
                    utm.zone[0] = (char) ((byte) 'A' + ek);
                    break;
            }
            utm.easting -= ek * 100000;
            utm.northing -= nk * 100000;
        } else if (api.location.ProjectionSetup.PROJ_SUI.equals(ctxProjectionName)) {
            double temp = utm.easting;
            utm.easting = utm.northing;
            utm.northing = temp;
        }

        return utm;
    }

    public static CartesianCoordinates LLtoMercator(QualifiedCoordinates qc,
                                                    Datum.Ellipsoid ellipsoid,
                                                    ProjectionSetup setup) {
        String projectionName = setup.name;
        CartesianCoordinates coords;

        if (api.location.ProjectionSetup.PROJ_MERCATOR.equals(projectionName)) {  // Mercator (1SP)

            final double a = ellipsoid.getEquatorialRadius();
            final double eccSquared = ellipsoid.getEccentricitySquared();
            final double e = Math.sqrt(eccSquared);

            final double latRad = Math.toRadians(qc.getLat());
            final double sinPhi = Math.sin(latRad);

            final double easting = setup.falseEasting + a * setup.k0
                                   * (Math.toRadians(qc.getLon() - setup.lonOrigin));
            final double northing = setup.falseNorthing + (a * setup.k0) / 2
                                    * ExtraMath.ln(((1 + sinPhi) / (1 - sinPhi)) * ExtraMath.pow((1 - e * sinPhi) / (1 + e * sinPhi), e));
/* simplified Google
            double northing = setup.falseNorthing + (a * setup.k0) / 2
                              * ExtraMath.ln(((1 + sinPhi) / (1 - sinPhi)));
*/

            coords = CartesianCoordinates.newInstance(setup.zone, easting, northing);

        } else if (api.location.ProjectionSetup.PROJ_SUI.equals(projectionName)) { // hack!

            final double F = qc.getLat() * (3600D / 10000D) - 16.902866D;
            final double L = qc.getLon() * (3600D / 10000D) - 2.67825D;

            final double y1 = 0.2114285339D - 0.010939608D * F - 0.000002658D * F * F - 0.00000853D * F * F * F;
            final double y3 = - 0.0000442327D + 0.000004291D * F - 0.000000309D * F * F;
            final double y5 = 0.0000000197D;

            final double x0 = 0.3087707463D * F + 0.000075028D * F * F + 0.000120435D * F * F * F + 0.00000007D * F * F * F * F * F;
            final double x2 = 0.0037454089D - 0.0001937927D * F + 0.000004340D * F * F - 0.000000376D * F * F * F;
            final double x4 = - 0.0000007346D + 0.0000001444D * F;

            double Y = y1 * L + y3 * L * L * L + y5 * L * L * L * L * L;
            double X = x0 + x2 * L * L + x4 * L * L * L * L;

            Y *= 1000000;
            Y += 600000; // LV03
            X *= 1000000;
            X += 200000; // LV03

            coords = CartesianCoordinates.newInstance(setup.zone, X, Y);

        } else if (projectionName.indexOf("France Zone") > -1) { // NTF / Lambert France

            /* Clarke 1880 IGN ellipsoid */
            final double a = 6378249.2D;
            final double e = 0.08248325676D;
            final double eccSquared = e * e;

            double fi, lambda, sinfi;
            fi = Math.toRadians(setup.latOrigin);
            sinfi = Math.sin(fi);
            final double t0 = Math.tan(Math.PI / 4 - fi / 2) / ExtraMath.pow(((1 - e * sinfi) / (1 + e * sinfi)), e / 2);
            fi = Math.toRadians(setup.parallel1);
            sinfi = Math.sin(fi);
            final double t1 = Math.tan(Math.PI / 4 - fi / 2) / ExtraMath.pow(((1 - e * sinfi) / (1 + e * sinfi)), e / 2);
            final double m1 = Math.cos(fi) / Math.sqrt(1 - eccSquared * sinfi * sinfi);
            fi = Math.toRadians(setup.parallel2);
            sinfi = Math.sin(fi);
            final double t2 = Math.tan(Math.PI / 4 - fi / 2) / ExtraMath.pow(((1 - e * sinfi) / (1 + e * sinfi)), e / 2);
            final double m2 = Math.cos(fi) / Math.sqrt(1 - eccSquared * sinfi * sinfi);
            final double n = (ExtraMath.ln(m1) - ExtraMath.ln(m2)) / (ExtraMath.ln(t1) - ExtraMath.ln(t2));

            QualifiedCoordinates localQc = ntf.toLocal(qc);
            fi = Math.toRadians(localQc.getLat());
            sinfi = Math.sin(fi);
            lambda = Math.toRadians(localQc.getLon());
            QualifiedCoordinates.releaseInstance(localQc);

            final double t = Math.tan(Math.PI / 4 - fi / 2) / ExtraMath.pow(((1 - e * sinfi) / (1 + e * sinfi)), e / 2);
            final double F = m1 / (n * ExtraMath.pow(t1, n));
            final double ro0 = a * F * ExtraMath.pow(t0, n);
            final double theta = n * (lambda - Math.toRadians(setup.lonOrigin));
            final double ro = a * F * ExtraMath.pow(t, n);

            final double X = setup.falseEasting + ro * Math.sin(theta);
            final double Y = setup.falseNorthing + ro0 - ro * Math.cos(theta);

            coords = CartesianCoordinates.newInstance(setup.zone, X, Y);

        } else { // Transverse Mercator

            final double a = ellipsoid.getEquatorialRadius();
            final double eccSquared = ellipsoid.getEccentricitySquared();
            final double eccPrimeSquared = ellipsoid.getEccentricityPrimeSquared();
            final double eccSquared2 = eccSquared * eccSquared;
            final double eccSquared3 = eccSquared * eccSquared * eccSquared;

            final double lonOriginRad = Math.toRadians(setup.lonOrigin);
            final double latOriginRad = Math.toRadians(setup.latOrigin);

            final double latRad = Math.toRadians(qc.getLat());
            final double lonRad = Math.toRadians(qc.getLon());

            final double temp_sinLatRad = Math.sin(latRad);
            final double temp_tanLatRad = Math.tan(latRad);
            final double temp_cosLatRad = Math.cos(latRad);

            final double N = a / Math.sqrt(1 - eccSquared * temp_sinLatRad * temp_sinLatRad);
            final double T = temp_tanLatRad * temp_tanLatRad;
            final double C = eccPrimeSquared * temp_cosLatRad * temp_cosLatRad;
            final double A = (lonRad - lonOriginRad) * temp_cosLatRad;
            final double M = a * ((1 - eccSquared / 4 - 3 * eccSquared2 / 64 - 5 * eccSquared3 / 256) * latRad
                             - (3 * eccSquared / 8 + 3 * eccSquared2 / 32 + 45 * eccSquared3 / 1024) * Math.sin(2 * latRad)
                             + (15 * eccSquared2 / 256 + 45 * eccSquared3 / 1024) * Math.sin(4 * latRad)
                             - (35 * eccSquared3 / 3072) * Math.sin(6 * latRad));
            final double Mo = a * ((1 - eccSquared / 4 - 3 * eccSquared2 / 64 - 5 * eccSquared3 / 256) * latOriginRad
                              - (3 * eccSquared / 8 + 3 * eccSquared2 / 32 + 45 * eccSquared3 / 1024) * Math.sin(2 * latOriginRad)
                              + (15 * eccSquared2 / 256 + 45 * eccSquared3 / 1024) * Math.sin(4 * latOriginRad)
                              - (35 * eccSquared3 / 3072) * Math.sin(6 * latOriginRad));

            final double easting = setup.falseEasting + setup.k0 * N * (A + ((1 - T + C) * A * A * A / 6)
                                   + ((5 - 18 * T + T * T + 72 * C - 58 * eccPrimeSquared) * A * A * A * A * A / 120));
            final double northing = setup.falseNorthing + setup.k0 * (M - Mo + N * temp_tanLatRad * (A * A / 2 + ((5 - T + 9 * C + 4 * C * C) * A * A * A * A / 24)
                                    + ((61 - 58 * T + T * T + 600 * C - 330 * eccPrimeSquared) * A * A * A * A * A * A / 720)));

            coords = CartesianCoordinates.newInstance(setup.zone, easting, northing);
        }

        return coords;
    }

    public static QualifiedCoordinates MercatortoLL(CartesianCoordinates utm,
                                                    Datum.Ellipsoid ellipsoid,
                                                    ProjectionSetup setup) {
        String projectionName = setup.name;
        QualifiedCoordinates qc;

        if (api.location.ProjectionSetup.PROJ_MERCATOR.equals(projectionName)) {  // Mercator

            final double a = ellipsoid.getEquatorialRadius();
            final double eccSquared = ellipsoid.getEccentricitySquared();
            final double eccSquared6 = eccSquared * eccSquared * eccSquared;
            final double eccSquared8 = eccSquared * eccSquared * eccSquared * eccSquared;

            final double t = ExtraMath.pow(Math.E, (setup.falseNorthing - utm.northing) / (a * setup.k0));
            final double chi = Math.PI / 2 - 2 * ExtraMath.atan(t);

            double lat, lon;

            lat = chi + (eccSquared / 2 + 5 * eccSquared * eccSquared / 24
                  + eccSquared6 / 12 + 13 * eccSquared8 / 380) * Math.sin(2 * chi)
                  + (7 * eccSquared * eccSquared / 48 + 29 * eccSquared6 / 240
                  + 811 * eccSquared8 / 11520) * Math.sin(4 * chi)
                  + (7 * eccSquared6 / 120 + 81 * eccSquared8 / 1120) * Math.sin(6 * chi)
                  + (4279 * eccSquared8 / 161280) * Math.sin(8 * chi);
/* simplified Google
            lat = chi;
*/
            lat = Math.toDegrees(lat);

            lon = ((utm.easting - setup.falseEasting) / (a * setup.k0));
            lon = setup.lonOrigin + Math.toDegrees(lon);

            qc = QualifiedCoordinates.newInstance(lat, lon);

        } else if (api.location.ProjectionSetup.PROJ_SUI.equals(projectionName)) { // hack!

            final double X = (utm.easting - 200000) / 1000000;
            final double Y = (utm.northing - 600000) / 1000000;

            final double a1 = 4.72973056D + 0.7925714D * X + 0.132812D * X * X + 0.02550D * X * X * X + 0.0048D * X * X * X * X;
            final double a3 = - 0.044270D - 0.02550D * X - 0.0096D * X * X;
            final double a5 = 0.00096D;

            final double p0 = 3.23864877D * X - 0.0025486D * X * X - 0.013245D * X * X * X + 0.000048D * X * X * X * X;
            final double p2 = - 0.27135379D - 0.0450442D * X - 0.007553D * X * X - 0.00146D * X * X * X;
            final double p4 = 0.002442D + 0.00132D * X;

            double lon = 2.67825D + a1 * Y + a3 * Y * Y * Y + a5 * Y * Y * Y * Y * Y;
            double lat = 16.902866 + p0 + p2 * Y * Y + p4 * Y * Y * Y * Y;

            lon /= (3600D / 10000D);
            lat /= (3600D / 10000D);

            qc = QualifiedCoordinates.newInstance(lat, lon);

        } else if (projectionName.indexOf("France Zone") > -1) { // NTF / Lambert France

            /* Clarke 1880 IGN ellipsoid */
            final double a = 6378249.2D;
            final double e = 0.08248325676D;

            final double eccSquared = e * e;
            final double eccSquared2 = eccSquared * eccSquared;
            final double eccSquared3 = eccSquared2 * eccSquared;
            final double eccSquared4 = eccSquared2 * eccSquared2;

            final double X = utm.easting - setup.falseEasting;
            final double Y = utm.northing - setup.falseNorthing;

            double fi, sinfi;
            fi = Math.toRadians(setup.latOrigin);
            sinfi = Math.sin(fi);
            final double t0 = Math.tan(Math.PI / 4 - fi / 2) / ExtraMath.pow(((1 - e * sinfi) / (1 + e * sinfi)), e / 2);
            fi = Math.toRadians(setup.parallel1);
            sinfi = Math.sin(fi);
            final double t1 = Math.tan(Math.PI / 4 - fi / 2) / ExtraMath.pow(((1 - e * sinfi) / (1 + e * sinfi)), e / 2);
            final double m1 = Math.cos(fi) / Math.sqrt(1 - eccSquared * sinfi * sinfi);
            fi = Math.toRadians(setup.parallel2);
            sinfi = Math.sin(fi);
            final double t2 = Math.tan(Math.PI / 4 - fi / 2) / ExtraMath.pow(((1 - e * sinfi) / (1 + e * sinfi)), e / 2);
            final double m2 = Math.cos(fi) / Math.sqrt(1 - eccSquared * sinfi * sinfi);

            final double n = (ExtraMath.ln(m1) - ExtraMath.ln(m2)) / (ExtraMath.ln(t1) - ExtraMath.ln(t2));
            final double F = m1 / (n * ExtraMath.pow(t1, n));
            final double ro0 = a * F * ExtraMath.pow(t0, n);
            final double theta = ExtraMath.atan(X / (ro0 - Y));
            final double ro = (n < 0D ? -1 : 1) * Math.sqrt(X * X + (ro0 - Y) * (ro0 - Y));
            final double t = ExtraMath.pow(ro / (a * F), 1 / n);
            final double kapa = Math.PI / 2 - 2 * ExtraMath.atan(t);

            double lon = theta / n + Math.toRadians(setup.lonOrigin);
            double lat = kapa + ((eccSquared) / 2
                            + (5 * eccSquared2) / 24
                            + (eccSquared3) / 12
                            + (13 * eccSquared4) / 360) * Math.sin(2 * kapa)
                         + ((7 * eccSquared2) / 48
                            + (29 * eccSquared3) / 240
                            + (811 * eccSquared4) / 11520) * Math.sin(4 * kapa)
                         + ((7 * eccSquared3) / 120
                            + (81 * eccSquared4) / 1120) * Math.sin(6 * kapa)
                         + ((4279 * eccSquared4) / 161280) * Math.sin(8 * kapa);

            lat = Math.toDegrees(lat);
            lon = Math.toDegrees(lon);

            QualifiedCoordinates localQc = QualifiedCoordinates.newInstance(lat, lon);
            qc = ntf.toWgs84(localQc);
            QualifiedCoordinates.releaseInstance(localQc);

        } else { // Transverse Mercator

            final double a = ellipsoid.getEquatorialRadius();
            final double eccSquared = ellipsoid.getEccentricitySquared();
            final double eccPrimeSquared = ellipsoid.getEccentricityPrimeSquared();
            final double e1 = (1 - Math.sqrt(1 - eccSquared)) / (1 + Math.sqrt(1 - eccSquared));
            final double eccSquared2 = eccSquared * eccSquared;
            final double eccSquared3 = eccSquared * eccSquared * eccSquared;

            final double latOriginRad = Math.toRadians(setup.latOrigin);
            final double Mo = a * ((1 - eccSquared / 4 - 3 * eccSquared2 / 64 - 5 * eccSquared3 / 256) * latOriginRad
                              - (3 * eccSquared / 8 + 3 * eccSquared2 / 32 + 45 * eccSquared3 / 1024) * Math.sin(2 * latOriginRad)
                              + (15 * eccSquared2 / 256 + 45 * eccSquared3 / 1024) * Math.sin(4 * latOriginRad)
                              - (35 * eccSquared3 / 3072) * Math.sin(6 * latOriginRad));
            final double Mi = Mo + (utm.northing - setup.falseNorthing) / setup.k0;

            final double mu = Mi / (a * (1 - eccSquared / 4 - 3 * eccSquared * eccSquared / 64 - 5 * eccSquared * eccSquared * eccSquared / 256));
            final double phi1Rad = mu + (3 * e1 / 2 - 27 * e1 * e1 * e1 / 32) * Math.sin(2 * mu)
                                   + (21 * e1 * e1 / 16 - 55 * e1 * e1 * e1 * e1 / 32) * Math.sin(4 * mu)
                                   + (151 * e1 * e1 * e1 / 96) * Math.sin(6 * mu)
                                   + (1097 * e1 * e1 * e1 * e1 / 512) * Math.sin(8 * mu);

            final double temp_sinPhi1 = Math.sin(phi1Rad);
            final double temp_tanPhi1 = Math.tan(phi1Rad);
            final double temp_cosPhi1 = Math.cos(phi1Rad);

            final double N1 = a / Math.sqrt(1 - eccSquared * temp_sinPhi1 * temp_sinPhi1);
            final double T1 = temp_tanPhi1 * temp_tanPhi1;
            final double C1 = eccPrimeSquared * temp_cosPhi1 * temp_cosPhi1;

            // pow(x, 1.5)
            double v = 1 - eccSquared * temp_sinPhi1 * temp_sinPhi1;
            v = Math.sqrt(v * v * v);
            // ~

            final double R1 = a * (1 - eccSquared) / v;
            final double D = (utm.easting - setup.falseEasting) / (N1 * setup.k0);

            double lat;
            double lon;

            lat = phi1Rad - (N1 * temp_tanPhi1 / R1) * (D * D / 2 - (5 + 3 * T1 + 10 * C1 - 4 * C1 * C1 - 9 * eccPrimeSquared) * D * D * D * D / 24
                    + (61 + 90 * T1 + 298 * C1 + 45 * T1 * T1 - 252 * eccPrimeSquared - 3 * C1 * C1) * D * D * D * D * D * D / 720);
            lat = Math.toDegrees(lat);

            lon = (D - (1 + 2 * T1 + C1) * D * D * D / 6 + (5 - 2 * C1 + 28 * T1 - 3 * C1 * C1 + 8 * eccPrimeSquared + 24 * T1 * T1)
                    * D * D * D * D * D / 120) / temp_cosPhi1;
            lon = setup.lonOrigin + Math.toDegrees(lon);

            qc = QualifiedCoordinates.newInstance(lat, lon);
        }

        return qc;
    }

    private static char UTMLetterDesignator(final double lat) {
        char letterDesignator;

        if ((84D >= lat) && (lat >= 72D)) letterDesignator = 'X';
        else if ((72D > lat) && (lat >= 64D)) letterDesignator = 'W';
        else if ((64D > lat) && (lat >= 56D)) letterDesignator = 'V';
        else if ((56D > lat) && (lat >= 48D)) letterDesignator = 'U';
        else if ((48D > lat) && (lat >= 40D)) letterDesignator = 'T';
        else if ((40D > lat) && (lat >= 32D)) letterDesignator = 'S';
        else if ((32D > lat) && (lat >= 24D)) letterDesignator = 'R';
        else if ((24D > lat) && (lat >= 16D)) letterDesignator = 'Q';
        else if ((16D > lat) && (lat >= 8D)) letterDesignator = 'P';
        else if ((8D > lat) && (lat >= 0D)) letterDesignator = 'N';
        else if ((0D > lat) && (lat >= -8D)) letterDesignator = 'M';
        else if ((-8D > lat) && (lat >= -16D)) letterDesignator = 'L';
        else if ((-16D > lat) && (lat >= -24D)) letterDesignator = 'K';
        else if ((-24D > lat) && (lat >= -32D)) letterDesignator = 'J';
        else if ((-32D > lat) && (lat >= -40D)) letterDesignator = 'H';
        else if ((-40D > lat) && (lat >= -48D)) letterDesignator = 'G';
        else if ((-48D > lat) && (lat >= -56D)) letterDesignator = 'F';
        else if ((-56D > lat) && (lat >= -64D)) letterDesignator = 'E';
        else if ((-64D > lat) && (lat >= -72D)) letterDesignator = 'D';
        else if ((-72D > lat) && (lat >= -80D)) letterDesignator = 'C';
        else letterDesignator = 'Z'; // error flag to show that the latitude is outside the UTM limits

        return letterDesignator;
    }

/*
    private static final double[] EXTRA_MATH_N = {
            2,
            1.1,
            1.01,
            1.001,
            1.0001,
            1.00001,
            1.000001,
            1.0000001,
            1.00000001,
            1.000000001,
            1.0000000001,
            1.00000000001,
            1.000000000001,
            1.0000000000001
    };
    private static final double[] EXTRA_MATH_LN = {
            0.69314718055994531941723212145818,
            0.095310179804324860043952123280765,
            0.0099503308531680828482153575442607,
            9.9950033308353316680939892053501e-4,
            9.9995000333308335333166680951131e-5,
            9.9999500003333308333533331666681e-6,
            9.9999950000033333308333353333317e-7,
            9.9999995000000333333308333335333e-8,
            9.9999999500000003333333308333334e-9,
            9.9999999950000000033333333308333e-10,
            9.9999999995000000000333333333308e-11,
            9.9999999999500000000003333333333e-12,
            9.9999999999950000000000033333333e-13,
            9.9999999999995000000000000333333e-14
        };
    private static final double EXTRA_MATH_LN10 = 2.3025850929940456840179914546844;

    public static double ln(double value) {
        if (value < 0D) {
            throw new IllegalArgumentException("ln(" + value + ")");
        }

        double fix = 0D;
        while (value < 1D) {
            value *= 10;
            fix -= EXTRA_MATH_LN10;
        }
        while (value > 10D) {
            value /= 10;
            fix += EXTRA_MATH_LN10;
        }

        final double[] N = EXTRA_MATH_N;
        final double[] LN = EXTRA_MATH_LN;
        double result = EXTRA_MATH_LN10;
        double inter = value;

        for (int n = N.length, i = 0; i < n; ) {
            double interi = inter * N[i];
            if (interi > 10D) {
                i++;
            } else {
                inter *= N[i];
                result -= LN[i];
            }
        }

        return result + fix;
    }

    public static double pow(final double arg1, final double arg2) {
        if (arg1 == 0D) {
            return 0D;
        }
        if (arg2 == 0D) {
            return 1D;
        }

        final double[] N = EXTRA_MATH_N;
        final double[] LN = EXTRA_MATH_LN;
        double lnresult = arg2 * ln(arg1);
        double result = 1D;
        double inter = lnresult;

        if (lnresult < 0D) {
            for (int n = N.length, i = 1; i < n; ) {
                double interi = inter + LN[i];
                if (interi > 0D) {
                    i++;
                } else {
                    inter += LN[i];
                    result /= N[i];
                }
            }
        } else {
            for (int n = N.length, i = 1; i < n; ) {
                double interi = inter - LN[i];
                if (interi < 0D) {
                    i++;
                } else {
                    inter -= LN[i];
                    result *= N[i];
                }
            }
        }

        return result;
    }
*/
}
