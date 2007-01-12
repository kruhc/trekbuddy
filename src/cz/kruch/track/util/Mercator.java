package cz.kruch.track.util;

import api.location.QualifiedCoordinates;
import api.location.Datum;
import api.location.GeodeticPosition;

import java.util.Vector;

public final class Mercator {

    public static final class UTMCoordinates implements GeodeticPosition {
        public String zone;
        public double easting, northing;

        public UTMCoordinates(String zone, double easting, double northing) {
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
            return zone + " " + easting + " " + northing;
        }
//#endif
    }

    public static final class ProjectionSetup extends api.location.ProjectionSetup {
        public String zone;
        public double lonOrigin, latOrigin;
        public double k0;
        public double falseEasting, falseNorthing;

        public ProjectionSetup(String name, String zone,
                               double lonOrigin, double latOrigin,
                               double k0,
                               double falseEasting, double falseNorthing) {
            super(name);
            this.zone = zone;
            this.lonOrigin = lonOrigin;
            this.latOrigin = latOrigin;
            this.k0 = k0;
            this.falseEasting = falseEasting;
            this.falseNorthing = falseNorthing;
        }

        public String toString() {
            return (new StringBuffer(32)).append(getName()).append('{').append(zone).append(',').append(lonOrigin).append(',').append(latOrigin).append(',').append(k0).append(',').append(falseEasting).append(',').append(falseNorthing).append('}').toString();
        }
    }

    public static final Vector KNOWN_GRIDS = new Vector();

    public static Datum contextDatum;
    public static ProjectionSetup contextProjection;

    public static void initialize() {
        KNOWN_GRIDS.addElement("Transverse Mercator");
        KNOWN_GRIDS.addElement("(BNG) British National Grid");
        KNOWN_GRIDS.addElement("(SG) Swedish Grid");
    }

    public static boolean isContext() {
        return contextProjection != null;
    }

    public static ProjectionSetup getUTMSetup(QualifiedCoordinates qc) {
        double lon = qc.getLon();
        double lat = qc.getLat();

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
        String zone = Integer.toString(zoneNumber) + UTMLetterDesignator(lat);
        double lonOrigin = (zoneNumber - 1) * 6 - 180 + 3; // +3 puts origin in middle of zone
        double falseNorthing = lat < 0D ? 10000000D : 0D;

        return new ProjectionSetup("UTM", zone, lonOrigin, 0D,
                                   0.9996, 500000D, falseNorthing);
    }

/*
    private static ProjectionSetup getUTMSetup(UTMCoordinates utm) {
        double lonOrigin = (Integer.parseInt(utm.zone.substring(0, utm.zone.length() - 1)) - 1) * 6 - 180 + 3; // +3 puts origin in middle of zone
        double falseNorthing = utm.zone.charAt(utm.zone.length() - 1) < 'N' ? 10000000D : 0D;

        return new ProjectionSetup(utm.zone, lonOrigin, 0D,
                                   0.9996, 500000D, falseNorthing);
    }
*/

    public static UTMCoordinates LLtoUTM(QualifiedCoordinates qc) {
        return LLtoTM(qc, Datum.DATUM_WGS_84.getEllipsoid(), getUTMSetup(qc));
    }

    public static UTMCoordinates LLtoGrid(QualifiedCoordinates qc) {
        UTMCoordinates utm = LLtoTM(qc, contextDatum.getEllipsoid(), contextProjection);

        // convert to grid coordinates
        if ("(BNG) British National Grid".equals(contextProjection.getName())) {
            char[] zone = new char[2];
            utm.easting = QualifiedCoordinates.round(utm.easting);
            utm.northing = QualifiedCoordinates.round(utm.northing);
            int ek = (int) (utm.easting / 500000);
            int nk = (int) (utm.northing / 500000);
            if (ek > 0) {
                if (nk > 0) {
                    zone[0] = 'O';
                } else {
                    zone[0] = 'T';
                }
            } else {
                if (nk > 1) {
                    zone[0] = 'H';
                } else if (nk > 0) {
                    zone[0] = 'N';
                } else {
                    zone[0] = 'S';
                }
            }
            int row = (int) (utm.northing - nk * 500000) / 100000;
            double column = (int) (utm.easting - ek * 500000) / 100000;
            int i = (int) ((4 - row) * 5 + column);
            if (i > ('H' - 'A')) { // skip 'I'
                i++;
            }
            zone[1] = (char) ('A' + i);
            utm.zone = new String(zone);
            utm.easting -= (int) (utm.easting / grade(utm.easting)) * grade(utm.easting);
            utm.northing -= (int) (utm.northing / grade(utm.northing)) * grade(utm.northing);
        }

        return utm;
    }

    public static UTMCoordinates LLtoTM(QualifiedCoordinates qc,
                                        Datum.Ellipsoid ellipsoid,
                                        ProjectionSetup setup) {
        double a = ellipsoid.getEquatorialRadius();
        double eccSquared = ellipsoid.getEccentricitySquared();
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

        return new UTMCoordinates(setup.zone, easting, northing);
    }

    public static QualifiedCoordinates TMtoLL(UTMCoordinates utm,
                                              Datum.Ellipsoid ellipsoid,
                                              ProjectionSetup setup) {
        double a = ellipsoid.getEquatorialRadius();
        double eccSquared = ellipsoid.getEccentricitySquared();
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

        return new QualifiedCoordinates(lat, lon);
    }

    private static char UTMLetterDesignator(double lat) {
        char etterDesignator;

        if ((84D >= lat) && (lat >= 72D)) etterDesignator = 'X';
        else if ((72D > lat) && (lat >= 64D)) etterDesignator = 'W';
        else if ((64D > lat) && (lat >= 56D)) etterDesignator = 'V';
        else if ((56D > lat) && (lat >= 48D)) etterDesignator = 'U';
        else if ((48D > lat) && (lat >= 40D)) etterDesignator = 'T';
        else if ((40D > lat) && (lat >= 32D)) etterDesignator = 'S';
        else if ((32D > lat) && (lat >= 24D)) etterDesignator = 'R';
        else if ((24D > lat) && (lat >= 16D)) etterDesignator = 'Q';
        else if ((16D > lat) && (lat >= 8D)) etterDesignator = 'P';
        else if ((8D > lat) && (lat >= 0D)) etterDesignator = 'N';
        else if ((0D > lat) && (lat >= -8D)) etterDesignator = 'M';
        else if ((-8D > lat) && (lat >= -16D)) etterDesignator = 'L';
        else if ((-16D > lat) && (lat >= -24D)) etterDesignator = 'K';
        else if ((-24D > lat) && (lat >= -32D)) etterDesignator = 'J';
        else if ((-32D > lat) && (lat >= -40D)) etterDesignator = 'H';
        else if ((-40D > lat) && (lat >= -48D)) etterDesignator = 'G';
        else if ((-48D > lat) && (lat >= -56D)) etterDesignator = 'F';
        else if ((-56D > lat) && (lat >= -64D)) etterDesignator = 'E';
        else if ((-64D > lat) && (lat >= -72D)) etterDesignator = 'D';
        else if ((-72D > lat) && (lat >= -80D)) etterDesignator = 'C';
        else etterDesignator = 'Z'; // error flag to show that the latitude is outside the UTM limits

        return etterDesignator;
    }

    public static int grade(double d) {
        int i = 1;
        while ((d / i) > 10) {
            i *= 10;
        }

        return i;
    }
}
