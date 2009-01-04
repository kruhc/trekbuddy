// @LICENSE@

package cz.kruch.track.util;

import api.location.QualifiedCoordinates;
import api.location.Datum;
import api.location.CartesianCoordinates;
import api.location.Ellipsoid;
import api.location.ProjectionSetup;

import java.util.Vector;

import cz.kruch.track.configuration.Config;

/**
 * Helper for spherical <-> cartesian transformations, grids etc.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class Mercator {

    private static Datum ntf;
    private static ProjectionSetup cachedUtmSetup;

    public static void initialize() {
        ntf = Config.getDatum("NTF");
    }

    public static ProjectionSetup getUTMSetup(final QualifiedCoordinates qc) {
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
        final char zoneLetter = UTMLetterDesignator(lat);
        final double lonOrigin = (zoneNumber - 1) * 6 - 180 + 3; // +3 puts origin in middle of zone
        final double falseNorthing = lat < 0D ? 10000000D : 0D;

        final ProjectionSetup cached = cachedUtmSetup;
        if (cached != null) {
            if (cached.lonOrigin == lonOrigin
                && cached.falseNorthing == falseNorthing
                && cached.zoneNumber == zoneNumber
                && cached.zoneLetter == zoneLetter) {
                return cached;
            } else {
                cachedUtmSetup = null;
            }
        }

        cachedUtmSetup = new ProjectionSetup("UTM", zoneNumber, zoneLetter,
                                             lonOrigin, 0D,
                                             0.9996, 500000D, falseNorthing);

        return cachedUtmSetup;
    }

    public static ProjectionSetup getMercatorSetup(final Vector ll) {
        double lmin = Double.MAX_VALUE;
        double lmax = Double.MIN_VALUE;

        for (int i = ll.size(); --i >= 0; ) {
            final double lon = ((QualifiedCoordinates) ll.elementAt(i)).getLon();
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
     * Conversions.
     */

    public static CartesianCoordinates LLtoUTM(final QualifiedCoordinates qc) {
        return LLtoMercator(qc, Datum.WGS_84.ellipsoid, getUTMSetup(qc));
    }

    public static CartesianCoordinates LLtoGrid(final QualifiedCoordinates qc) {
        final CartesianCoordinates utm = LLtoMercator(qc, Datum.contextDatum.ellipsoid, ProjectionSetup.contextProjection);

        /*
         * handle specific grids
         */

        switch (ProjectionSetup.contextProjection.code) {

            case api.location.ProjectionSetup.PROJECTION_SUI: {

                final double temp = utm.easting;
                utm.easting = utm.northing;
                utm.northing = temp;

            } break;

            case api.location.ProjectionSetup.PROJECTION_BNG: {

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
                utm.easting = utm.easting % 100000;
                utm.northing = utm.northing % 100000;

            } break;

            case api.location.ProjectionSetup.PROJECTION_IG: {

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

            } break;
        }

        return utm;
    }

    public static CartesianCoordinates LLtoMercator(final QualifiedCoordinates qc,
                                                    final Ellipsoid ellipsoid,
                                                    final ProjectionSetup setup) {
        final CartesianCoordinates coords;

        switch (setup.code) {

            case api.location.ProjectionSetup.PROJECTION_MERCATOR: {

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

            } break;

            case api.location.ProjectionSetup.PROJECTION_SUI: {

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

            } break;

            case api.location.ProjectionSetup.PROJECTION_FRANCE_n: {
                
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
                final double n = ExtraMath.ln(m1 / m2) / ExtraMath.ln(t1 / t2);

                QualifiedCoordinates localQc = ntf.toLocal(qc);
                fi = Math.toRadians(localQc.getLat());
                sinfi = Math.sin(fi);
                lambda = Math.toRadians(localQc.getLon());
                QualifiedCoordinates.releaseInstance(localQc);
                localQc = null; // gc hint

                final double t = Math.tan(Math.PI / 4 - fi / 2) / ExtraMath.pow(((1 - e * sinfi) / (1 + e * sinfi)), e / 2);
                final double F = m1 / (n * ExtraMath.pow(t1, n));
                final double ro0 = a * F * ExtraMath.pow(t0, n);
                final double theta = n * (lambda - Math.toRadians(setup.lonOrigin));
                final double ro = a * F * ExtraMath.pow(t, n);

                final double X = setup.falseEasting + ro * Math.sin(theta);
                final double Y = setup.falseNorthing + ro0 - ro * Math.cos(theta);

                coords = CartesianCoordinates.newInstance(setup.zone, X, Y);

            } break;

            case api.location.ProjectionSetup.PROJECTION_LCC: {

/*
                qc = QualifiedCoordinates.newInstance(50.6795725D, 5.807370277D);
                ellipsoid = Ellipsoid.ELLIPSOIDS[18];
                setup = new ProjectionSetup(ProjectionSetup.PROJ_LCC,
                                            null, 4.356939722D, 90D,
                                            Double.NaN,
                                            150000.01D, 5400088.44D,
                                            49.8333333D, 51.1666666D);
*/

                final double ecc = Math.sqrt(ellipsoid.getEccentricitySquared());
                final double eccSquared = ellipsoid.getEccentricitySquared();
                final double a = ellipsoid.getEquatorialRadius();

                final double fi1 = Math.toRadians(setup.parallel1);
                final double fi2 = Math.toRadians(setup.parallel2);
                final double fi0 = Math.toRadians(setup.latOrigin);
                final double fi = Math.toRadians(qc.getLat());
                final double lambda = Math.toRadians(qc.getLon());
                final double lambda0 = Math.toRadians(setup.lonOrigin);

                final double sinfi1 = Math.sin(fi1);
                final double sinfi2 = Math.sin(fi2);
                final double sinfi0 = Math.sin(fi0);
                final double sinfi = Math.sin(fi);

                final double m1 = Math.cos(fi1) / Math.sqrt(1 - eccSquared * sinfi1 * sinfi1);
                final double m2 = Math.cos(fi2) / Math.sqrt(1 - eccSquared * sinfi2 * sinfi2);
                final double t1 = Math.tan(Math.PI / 4 - fi1 / 2) / ExtraMath.pow((1 - ecc * sinfi1) / (1 + ecc * sinfi1), ecc / 2);
                final double t2 = Math.tan(Math.PI / 4 - fi2 / 2) / ExtraMath.pow((1 - ecc * sinfi2) / (1 + ecc * sinfi2), ecc / 2);
                final double t0 = Math.tan(Math.PI / 4 - fi0 / 2) / ExtraMath.pow((1 - ecc * sinfi0) / (1 + ecc * sinfi0), ecc / 2);
                final double n = ExtraMath.ln(m1 / m2) / ExtraMath.ln(t1 / t2);
                final double F = m1 / (n * ExtraMath.pow(t1, n));
                final double ro0 = a * F * ExtraMath.pow(t0, n);
                final double t = Math.tan(Math.PI / 4 - fi / 2) / ExtraMath.pow((1 - ecc * sinfi) / (1 + ecc * sinfi), ecc / 2);
                final double ro = a * F * ExtraMath.pow(t, n);
                final double theta = n * (lambda - lambda0);

                final double x = setup.falseEasting + ro * Math.sin(theta);
                final double y = setup.falseNorthing + ro0 - ro * Math.cos(theta);

                coords = CartesianCoordinates.newInstance(setup.zone, x, y);

            } break;

            default: { // the rest is Transverse Mercator

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
        }

        return coords;
    }

    public static QualifiedCoordinates UTMtoLL(final CartesianCoordinates utm) {
        return MercatortoLL(utm, Datum.WGS_84.ellipsoid, cachedUtmSetup);
    }

    public static QualifiedCoordinates GridtoLL(final CartesianCoordinates utm) {
        CartesianCoordinates clone = CartesianCoordinates.newInstance(utm.zone,
                                                                      utm.easting,
                                                                      utm.northing);

        /*
         * handle specific grids
         */

        switch (ProjectionSetup.contextProjection.code) {

            case api.location.ProjectionSetup.PROJECTION_SUI: {

                final double temp = clone.northing;
                clone.northing = clone.easting;
                clone.easting = temp;

            } break;

            case api.location.ProjectionSetup.PROJECTION_BNG: {

                int ek = 0, nk = 0;
                switch (clone.zone[0]) {
                    case 'H':
                        nk = 2;
                        break;
                    case 'N':
                        nk = 1;
                        break;
                    case 'O':
                        ek = nk = 1;
                        break;
                    case 'S':
                        break;
                    case 'T':
                        ek = 1;
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid zone");
                }

                clone.easting += ek * 500000;
                clone.northing += nk * 500000;

                int i = clone.zone[1] - 'A';
                if (i > ('H' - 'A')) { // skipped 'I'
                    i--;
                }
                int row = 4 - i / 5;
                int col = i % 5;

                clone.easting += col * 100000;
                clone.northing += row * 100000;

            } break;

            case api.location.ProjectionSetup.PROJECTION_IG: {

                int nk, ek;
                int i = utm.zone[0];
                if (i >= 'A' && i <= 'D') {
                    nk = 4;
                    ek = i - 'A';
                } else if (i >= 'F' && i <= 'J') {
                    nk = 3;
                    ek = i - 'F';
                } else if (i >= 'L' && i <= 'O') {
                    nk = 2;
                    ek = i - 'L';
                } else if (i >= 'Q' && i <= 'T') {
                    nk = 1;
                    ek = i - 'Q';
                } else if (i >= 'V' && i <= 'Y') {
                    nk = 0;
                    ek = i - 'V';
                } else {
                    throw new IllegalArgumentException("Invalid zone");
                }

                clone.easting += ek * 100000;
                clone.northing += nk * 100000;

            } break;
        }

        final QualifiedCoordinates qc = MercatortoLL(clone, Datum.contextDatum.ellipsoid, ProjectionSetup.contextProjection);
        CartesianCoordinates.releaseInstance(clone);

        return qc;
    }

    public static QualifiedCoordinates MercatortoLL(final CartesianCoordinates utm,
                                                    final Ellipsoid ellipsoid,
                                                    final ProjectionSetup setup) {
        final QualifiedCoordinates qc;

        switch (setup.code) {

            case api.location.ProjectionSetup.PROJECTION_MERCATOR: {

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

            } break;

            case api.location.ProjectionSetup.PROJECTION_SUI: {

                final double X = (utm.easting - 200000) / 1000000;
                final double Y = (utm.northing - 600000) / 1000000;

                final double a1 = 4.72973056D + 0.7925714D * X + 0.132812D * X * X + 0.02550D * X * X * X + 0.0048D * X * X * X * X;
                final double a3 = - 0.044270D - 0.02550D * X - 0.0096D * X * X;
                final double a5 = 0.00096D;

                final double p0 = 3.23864877D * X - 0.0025486D * X * X - 0.013245D * X * X * X + 0.000048D * X * X * X * X;
                final double p2 = - 0.27135379D - 0.0450442D * X - 0.007553D * X * X - 0.00146D * X * X * X;
                final double p4 = 0.002442D + 0.00132D * X;

                double lon = 2.67825D + a1 * Y + a3 * Y * Y * Y + a5 * Y * Y * Y * Y * Y;
                double lat = 16.902866D + p0 + p2 * Y * Y + p4 * Y * Y * Y * Y;

                lon /= (3600D / 10000D);
                lat /= (3600D / 10000D);

                qc = QualifiedCoordinates.newInstance(lat, lon);

            } break;

            case api.location.ProjectionSetup.PROJECTION_FRANCE_n: {

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

                final double n = ExtraMath.ln(m1 / m2) / ExtraMath.ln(t1 / t2);
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

            } break;

            case api.location.ProjectionSetup.PROJECTION_LCC: {

                final double ecc = Math.sqrt(ellipsoid.getEccentricitySquared());
                final double eccSquared = ellipsoid.getEccentricitySquared();
                final double eccSquared4 = eccSquared * eccSquared;
                final double eccSquared6 = eccSquared * eccSquared * eccSquared;
                final double eccSquared8 = eccSquared * eccSquared * eccSquared * eccSquared;
                final double a = ellipsoid.getEquatorialRadius();

                final double x = utm.easting - setup.falseEasting;
                final double y = utm.northing - setup.falseNorthing;

                final double fi1 = Math.toRadians(setup.parallel1);
                final double fi2 = Math.toRadians(setup.parallel2);
                final double fi0 = Math.toRadians(setup.latOrigin);
                final double lambda0 = Math.toRadians(setup.lonOrigin);

                final double m1 = Math.cos(fi1) / Math.sqrt(1 - eccSquared * Math.sin(fi1) * Math.sin(fi1));
                final double m2 = Math.cos(fi2) / Math.sqrt(1 - eccSquared * Math.sin(fi2) * Math.sin(fi2));
                final double t1 = Math.tan(Math.PI / 4 - fi1 / 2) / ExtraMath.pow((1 - ecc * Math.sin(fi1)) / (1 + ecc * Math.sin(fi1)), ecc / 2);
                final double t2 = Math.tan(Math.PI / 4 - fi2 / 2) / ExtraMath.pow((1 - ecc * Math.sin(fi2)) / (1 + ecc * Math.sin(fi2)), ecc / 2);
                final double t0 = Math.tan(Math.PI / 4 - fi0 / 2) / ExtraMath.pow((1 - ecc * Math.sin(fi0)) / (1 + ecc * Math.sin(fi0)), ecc / 2);
                final double n = ExtraMath.ln(m1 / m2) / ExtraMath.ln(t1 / t2);
                final double F = m1 / (n * ExtraMath.pow(t1, n));
                final double ro0 = a * F * ExtraMath.pow(t0, n);
                final double theta = ExtraMath.atan(x / (ro0 - y));
                final double lambda = theta / n + lambda0;
                final double ro = Math.sqrt((x * x) + (ro0 - y) * (ro0 - y)) * (n < 0D ? -1 : 1);
                final double t = ExtraMath.pow(ro / (a * F), 1 / n);
                final double w = Math.PI / 2 - 2 * ExtraMath.atan(t);
                final double fi = w + (eccSquared / 2 + 5 * eccSquared4 / 24 + eccSquared6 / 12 + 13 * eccSquared8 / 360) * Math.sin(2 * w)
                                  + (7 * eccSquared4 / 48 + 29 * eccSquared6 / 240 + 811 * eccSquared8 / 11520) * Math.sin(4 * w)
                                  + (7 * eccSquared6 / 120 + 81 * eccSquared8 / 1120) * Math.sin(6 * w)
                                  + (4279 * eccSquared8 / 161280) * Math.sin(8 * w);

                qc = QualifiedCoordinates.newInstance(Math.toDegrees(fi), Math.toDegrees(lambda));

            } break;
            
            default: { // the rest is Transverse Mercator

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
        }

        return qc;
    }

    private static char UTMLetterDesignator(final double lat) {
        final char letterDesignator;

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
