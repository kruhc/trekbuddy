package cz.kruch.track.util;

public final class Mercator {

    public final static class UTMCoordinates  {
        public String zone;
        public double easting, northing;

        public UTMCoordinates(String zone, double easting, double northing) {
            this.zone = zone;
            this.easting = easting;
            this.northing = northing;
        }
    }

    public static UTMCoordinates LLtoUTM(double lat, double lon) {
        double a = Datum.current.getEllipsoid().getEquatorialRadius();
        double eccSquared = Datum.current.getEllipsoid().getEccentricitySquared();
        double eccPrimeSquared = Datum.current.getEllipsoid().getEccentricityPrimeSquared();
        double k0 = 0.9996D;

        double N, T, C, A, M;

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

        String UTMZone = Integer.toString(zoneNumber) + UTMLetterDesignator(lat);
        double lonOrigin = (zoneNumber - 1) * 6 - 180 + 3; // +3 puts origin in middle of zone
        double lonOriginRad = Math.toRadians(lonOrigin);

        double latRad = Math.toRadians(lat);
        double lonRad = Math.toRadians(lon);

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

        double UTMEasting = (k0 * N * (A + (1 - T + C) * A * A * A / 6
                             + (5 - 18 * T + T * T + 72 * C - 58 * eccPrimeSquared) * A * A * A * A * A / 120)
                             + 500000.0);
        double UTMNorthing = (k0 * (M + N * temp_tanLatRad * (A * A / 2 + (5 - T + 9 * C + 4 * C * C) * A * A * A * A / 24
                              + (61 - 58 * T + T * T + 600 * C - 330 * eccPrimeSquared) * A * A * A * A * A * A / 720)));

/*
        UTMEasting -= current.dx;
        UTMNorthing -= current.dy;
*/

        if (lat < 0) {
            UTMNorthing += 10000000.0; // 10000000 meter offset for southern hemisphere
        }

        return new UTMCoordinates(UTMZone, UTMEasting, UTMNorthing);
    }

/*
    public static double[] UTMtoLL(String zone, double easting, double northing) {
        double k0 = 0.9996D;
        double a = current.equatorialRadius;
        double eccSquared = current.eccentricitySquared;
        double eccPrimeSquared = current.eccentricityPrimeSquared;
        double e1 = (1 - Math.sqrt(1 - eccSquared)) / (1 + Math.sqrt(1 - eccSquared));

        double N1, T1, C1, R1, D, M, Mo;
        double mu, phi1Rad;
        double x, y;

        x = easting - 500000.0; // remove 500,000 meter offset for longitude
        y = northing;

        x += current.dX;
        y += current.dY;

        double lonOrigin = (Integer.parseInt(zone.substring(0, zone.length() - 1)) - 1) * 6 - 180 + 3; // +3 puts origin in middle of zone

        if (zone.charAt(zone.length() - 1) - 'N' < 0) {
            y -= 10000000.0; // remove 10,000,000 meter offset used for southern hemisphere
        }

//        Mo = a * (1 - 0);
//        M =  Mo +  y / k0;
        M = y / k0;
        mu = M / (a * (1 - eccSquared / 4 - 3 * eccSquared * eccSquared / 64 - 5 * eccSquared * eccSquared * eccSquared / 256));
        phi1Rad = mu + (3 * e1 / 2 - 27 * e1 * e1 * e1 / 32) * Math.sin(2 * mu)
                + (21 * e1 * e1 / 16 - 55 * e1 * e1 * e1 * e1 / 32) * Math.sin(4 * mu)
                + (151 * e1 * e1 * e1 / 96) * Math.sin(6 * mu)
                + (1097 * e1 * e1 * e1 * e1 / 512) * Math.sin(8 * mu);

        N1 = a / Math.sqrt(1 - eccSquared * Math.sin(phi1Rad) * Math.sin(phi1Rad));
        T1 = Math.tan(phi1Rad) * Math.tan(phi1Rad);
        C1 = eccPrimeSquared * Math.cos(phi1Rad) * Math.cos(phi1Rad);

        // pow(x, 1.5)
        double v = 1 - eccSquared * Math.sin(phi1Rad) * Math.sin(phi1Rad);
        v = Math.sqrt(v * v * v);
        // ~

        R1 = a * (1 - eccSquared) / v;
        D = x / (N1 * k0);

        double lat;
        double lon;

        lat = phi1Rad - (N1 * Math.tan(phi1Rad) / R1) * (D * D / 2 - (5 + 3 * T1 + 10 * C1 - 4 * C1 * C1 - 9 * eccPrimeSquared) * D * D * D * D / 24
                + (61 + 90 * T1 + 298 * C1 + 45 * T1 * T1 - 252 * eccPrimeSquared - 3 * C1 * C1) * D * D * D * D * D * D / 720);
        lat = Math.toDegrees(lat);

        lon = (D - (1 + 2 * T1 + C1) * D * D * D / 6 + (5 - 2 * C1 + 28 * T1 - 3 * C1 * C1 + 8 * eccPrimeSquared + 24 * T1 * T1)
                * D * D * D * D * D / 120) / Math.cos(phi1Rad);
        lon = lonOrigin + Math.toDegrees(lon);

        return new double[] { lon, lat };
    }
*/

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
}
