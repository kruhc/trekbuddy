package cz.kruch.track.util;

import api.location.QualifiedCoordinates;
import api.location.Datum;
import api.location.GeodeticPosition;
import api.location.ProjectionSetup;

import java.util.Vector;

public final class Mercator {

    public static final class Coordinates implements GeodeticPosition {
        public double easting, northing;
        public char[] zone;

        /*
         * POOL
         */

        private static final Coordinates[] pool = new Coordinates[8];
        private static int countFree;

        public synchronized static Coordinates newInstance(char[] zone,
                                                           final double easting,
                                                           final double northing) {
            Coordinates result;

            if (countFree == 0) {
                result = new Coordinates(zone, easting, northing);
            } else {
                result = pool[--countFree];
                pool[countFree] = null;
                result.zone = zone;
                result.easting = easting;
                result.northing = northing;
            }

            return result;
        }

        public synchronized static void releaseInstance(Coordinates utm) {
            if (countFree < pool.length) {
                pool[countFree++] = utm;
            }
        }

        /*
         * ~POOL
         */

        private Coordinates(char[] zone, final double easting, final double northing) {
            this.zone = zone;
            this.easting = easting;
            this.northing = northing;
        }

        public double getH() {
            return easting;
        }

        public double getV() {
            return northing;
        }

//#ifdef __LOG__
        public String toString() {
            return (new String(zone) + " " + easting + " " + northing);
        }
//#endif
    }

    public static final class ProjectionSetup extends api.location.ProjectionSetup {
        public double lonOrigin, latOrigin;
        public double k0;
        public double falseEasting, falseNorthing;
        public short zoneNumber;
        public char zoneLetter;
        public char[] zone;

        public ProjectionSetup(String name,
                               final int zoneNumber, final char zoneLetter,
                               final double lonOrigin, final double latOrigin,
                               final double k0,
                               final double falseEasting, final double falseNorthing) {
            super(name);
            this.zoneNumber = (short) zoneNumber;
            this.zoneLetter = zoneLetter;
            if ("UTM".equals(name)) {
                this.zone = (new StringBuffer().append(zoneNumber).append(zoneLetter)).toString().toCharArray();
            }
            this.lonOrigin = lonOrigin;
            this.latOrigin = latOrigin;
            this.k0 = k0;
            this.falseEasting = falseEasting;
            this.falseNorthing = falseNorthing;
        }

        // TODO optimize
        public String toString() {
/*
            if (PROJ_GMERCATOR.equals(getName())) {
                return super.toString();
            }
*/
            StringBuffer sb = new StringBuffer(32);
            sb.append(getName()).append('{');
            if (zone != null) {
                sb.append(zone).append(',');
            }
            sb.append(lonOrigin).append(',').append(latOrigin).append(',').append(k0).append(',').append(falseEasting).append(',').append(falseNorthing).append('}');

            return sb.toString();
        }
    }

/*
    public static final Vector KNOWN_GRIDS = new Vector();
*/

    public static Datum contextDatum;
    public static ProjectionSetup contextProjection;

/*
    public static void initialize() {
        KNOWN_GRIDS.addElement(PROJ_TRANSVERSE_MERCATOR);
        KNOWN_GRIDS.addElement(PROJ_UTM);
        KNOWN_GRIDS.addElement(PROJ_BNG);
        KNOWN_GRIDS.addElement(PROJ_SG);
    }
*/

    public static boolean isGrid() {
        return contextProjection != null && !api.location.ProjectionSetup.PROJ_MERCATOR.equals(contextProjection.getName());
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

        return new ProjectionSetup(api.location.ProjectionSetup.PROJ_MERCATOR, -1, 'Z', (lmin + lmax) / 2, 0D,
                                   1D, 0D, 0D);
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

    public static Coordinates LLtoUTM(QualifiedCoordinates qc) {
        return LLtoMercator(qc, Datum.DATUM_WGS_84.getEllipsoid(), getUTMSetup(qc));
    }

    public static Coordinates LLtoGrid(QualifiedCoordinates qc) {
        Coordinates utm = LLtoMercator(qc, contextDatum.getEllipsoid(), contextProjection);

        /*
         * handle specific grids
         */

        if (api.location.ProjectionSetup.PROJ_BNG.equals(contextProjection.getName())) {
            if (utm.zone == null || utm.zone.length != 2) {
                utm.zone = new char[2];
            }
            utm.easting = QualifiedCoordinates.round(utm.easting);
            utm.northing = QualifiedCoordinates.round(utm.northing);
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
            utm.easting -= (int) (utm.easting / grade(utm.easting)) * grade(utm.easting);
            utm.northing -= (int) (utm.northing / grade(utm.northing)) * grade(utm.northing);
        } else if (api.location.ProjectionSetup.PROJ_IG.equals(contextProjection.getName())) {
            if (utm.zone == null || utm.zone.length != 1) {
                utm.zone = new char[1];
            }
            utm.easting = QualifiedCoordinates.round(utm.easting);
            utm.northing = QualifiedCoordinates.round(utm.northing);
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
        }

        return utm;
    }

    public static Coordinates LLtoMercator(QualifiedCoordinates qc,
                                              Datum.Ellipsoid ellipsoid,
                                              ProjectionSetup setup) {
        double a = ellipsoid.getEquatorialRadius();
        double eccSquared = ellipsoid.getEccentricitySquared();

        if (api.location.ProjectionSetup.PROJ_MERCATOR.equals(setup.getName())) {  // Mercator (1SP)

            double e = Math.sqrt(eccSquared);
            double latRad = Math.toRadians(qc.getLat());
            double sinPhi = Math.sin(latRad);

            double easting = setup.falseEasting + a * setup.k0
                             * (Math.toRadians(qc.getLon() - setup.lonOrigin));
            double northing = setup.falseNorthing + (a * setup.k0) / 2
                              * ln(((1 + sinPhi) / (1 - sinPhi))* pow((1 - e * sinPhi) / (1 + e * sinPhi), e));
/* simplified Google
            double northing = setup.falseNorthing + (a * setup.k0) / 2
                              * ExtraMath.ln(((1 + sinPhi) / (1 - sinPhi)));
*/

            return Coordinates.newInstance(setup.zone, easting, northing);

        } else { // Transverse Mercator

            double eccPrimeSquared = ellipsoid.getEccentricityPrimeSquared();

            double N, T, C, A, M, Mo;

            double lonOriginRad = Math.toRadians(setup.lonOrigin);
            double latOriginRad = Math.toRadians(setup.latOrigin);

            double latRad = Math.toRadians(qc.getLat());
            double lonRad = Math.toRadians(qc.getLon());

            double temp_sinLatRad = Math.sin(latRad);
            double temp_tanLatRad = Math.tan(latRad);
            double temp_cosLatRad = Math.cos(latRad);
            double eccSquared2 = eccSquared * eccSquared;
            double eccSquared3 = eccSquared * eccSquared * eccSquared;

            N = a / Math.sqrt(1 - eccSquared * temp_sinLatRad * temp_sinLatRad);
            T = temp_tanLatRad * temp_tanLatRad;
            C = eccPrimeSquared * temp_cosLatRad * temp_cosLatRad;
            A = (lonRad - lonOriginRad) * temp_cosLatRad;
            M = a * ((1 - eccSquared / 4 - 3 * eccSquared2 / 64 - 5 * eccSquared3 / 256) * latRad
                    - (3 * eccSquared / 8 + 3 * eccSquared2 / 32 + 45 * eccSquared3 / 1024) * Math.sin(2 * latRad)
                    + (15 * eccSquared2 / 256 + 45 * eccSquared3 / 1024) * Math.sin(4 * latRad)
                    - (35 * eccSquared3 / 3072) * Math.sin(6 * latRad));
            Mo = a * ((1 - eccSquared / 4 - 3 * eccSquared2 / 64 - 5 * eccSquared3 / 256) * latOriginRad
                    - (3 * eccSquared / 8 + 3 * eccSquared2 / 32 + 45 * eccSquared3 / 1024) * Math.sin(2 * latOriginRad)
                    + (15 * eccSquared2 / 256 + 45 * eccSquared3 / 1024) * Math.sin(4 * latOriginRad)
                    - (35 * eccSquared3 / 3072) * Math.sin(6 * latOriginRad));

            double easting = setup.falseEasting + setup.k0 * N * (A + ((1 - T + C) * A * A * A / 6)
                                 + ((5 - 18 * T + T * T + 72 * C - 58 * eccPrimeSquared) * A * A * A * A * A / 120));
            double northing = setup.falseNorthing + setup.k0 * (M - Mo + N * temp_tanLatRad * (A * A / 2 + ((5 - T + 9 * C + 4 * C * C) * A * A * A * A / 24)
                                  + ((61 - 58 * T + T * T + 600 * C - 330 * eccPrimeSquared) * A * A * A * A * A * A / 720)));

            return Coordinates.newInstance(setup.zone, easting, northing);
        }
    }

    public static QualifiedCoordinates MercatortoLL(Coordinates utm,
                                              Datum.Ellipsoid ellipsoid,
                                              ProjectionSetup setup) {
        double a = ellipsoid.getEquatorialRadius();
        double eccSquared = ellipsoid.getEccentricitySquared();

        if (api.location.ProjectionSetup.PROJ_MERCATOR.equals(setup.getName())) {  // Mercator

            double eccSquared6 = eccSquared * eccSquared * eccSquared;
            double eccSquared8 = eccSquared * eccSquared * eccSquared * eccSquared;
            double t = pow(Math.E, (setup.falseNorthing - utm.northing) / (a * setup.k0));
            double chi = Math.PI / 2 - 2 * public_domain.Xedarius.atan(t);

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

            lon = ((utm.easting -  setup.falseEasting) / (a * setup.k0));
            lon = setup.lonOrigin + Math.toDegrees(lon);

            return QualifiedCoordinates.newInstance(lat, lon);

        } else { // Transverse Mercator

            double eccPrimeSquared = ellipsoid.getEccentricityPrimeSquared();
            double e1 = (1 - Math.sqrt(1 - eccSquared)) / (1 + Math.sqrt(1 - eccSquared));

            double N1, T1, C1, R1, D, Mi, Mo;
            double mu, phi1Rad;

            double eccSquared2 = eccSquared * eccSquared;
            double eccSquared3 = eccSquared * eccSquared * eccSquared;
            double latOriginRad = Math.toRadians(setup.latOrigin);
            Mo = a * ((1 - eccSquared / 4 - 3 * eccSquared2 / 64 - 5 * eccSquared3 / 256) * latOriginRad
                    - (3 * eccSquared / 8 + 3 * eccSquared2 / 32 + 45 * eccSquared3 / 1024) * Math.sin(2 * latOriginRad)
                    + (15 * eccSquared2 / 256 + 45 * eccSquared3 / 1024) * Math.sin(4 * latOriginRad)
                    - (35 * eccSquared3 / 3072) * Math.sin(6 * latOriginRad));
            Mi = Mo + (utm.northing - setup.falseNorthing) / setup.k0;

            mu = Mi / (a * (1 - eccSquared / 4 - 3 * eccSquared * eccSquared / 64 - 5 * eccSquared * eccSquared * eccSquared / 256));
            phi1Rad = mu + (3 * e1 / 2 - 27 * e1 * e1 * e1 / 32) * Math.sin(2 * mu)
                    + (21 * e1 * e1 / 16 - 55 * e1 * e1 * e1 * e1 / 32) * Math.sin(4 * mu)
                    + (151 * e1 * e1 * e1 / 96) * Math.sin(6 * mu)
                    + (1097 * e1 * e1 * e1 * e1 / 512) * Math.sin(8 * mu);

            double temp_sinPhi1 = Math.sin(phi1Rad);
            double temp_tanPhi1 = Math.tan(phi1Rad);
            double temp_cosPhi1 = Math.cos(phi1Rad);

            N1 = a / Math.sqrt(1 - eccSquared * temp_sinPhi1 * temp_sinPhi1);
            T1 = temp_tanPhi1 * temp_tanPhi1;
            C1 = eccPrimeSquared * temp_cosPhi1 * temp_cosPhi1;

            // pow(x, 1.5)
            double v = 1 - eccSquared * temp_sinPhi1 * temp_sinPhi1;
            v = Math.sqrt(v * v * v);
            // ~

            R1 = a * (1 - eccSquared) / v;
            D = (utm.easting - setup.falseEasting) / (N1 * setup.k0);

            double lat;
            double lon;

            lat = phi1Rad - (N1 * temp_tanPhi1 / R1) * (D * D / 2 - (5 + 3 * T1 + 10 * C1 - 4 * C1 * C1 - 9 * eccPrimeSquared) * D * D * D * D / 24
                    + (61 + 90 * T1 + 298 * C1 + 45 * T1 * T1 - 252 * eccPrimeSquared - 3 * C1 * C1) * D * D * D * D * D * D / 720);
            lat = Math.toDegrees(lat);

            lon = (D - (1 + 2 * T1 + C1) * D * D * D / 6 + (5 - 2 * C1 + 28 * T1 - 3 * C1 * C1 + 8 * eccPrimeSquared + 24 * T1 * T1)
                    * D * D * D * D * D / 120) / temp_cosPhi1;
            lon = setup.lonOrigin + Math.toDegrees(lon);

            return QualifiedCoordinates.newInstance(lat, lon);
        }
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

    public static int grade(final double d) {
        int i = 1;
        while ((d / i) >= 10) {
            i *= 10;
        }

        return i;
    }

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
}
