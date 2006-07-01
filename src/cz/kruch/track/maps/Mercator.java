// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps;

public class Mercator {

    private static class Ellipsoid {
        public Ellipsoid(int Id, String name, double radius, double ecc,
                         double dX, double dY) {
            this.id = Id;
            this.ellipsoidName = name;
            this.EquatorialRadius = radius;
            this.eccentricitySquared = ecc;
            this.dX = dX;
            this.dY = dY;
        }

        private int id;
        private String ellipsoidName;
        private double EquatorialRadius;
        private double eccentricitySquared;
        private double dX, dY;
    }

    private static final Ellipsoid ELIPSOID_PULKOVO_1942_1 = new Ellipsoid(15, "Pulkovo 1942 (1)", 6378245, 0.006693422, 28D, -130D);
    private static final Ellipsoid ELIPSOID_WGS84 = new Ellipsoid(23, "WGS-84", 6378137, 0.00669438, 0D, 0D);

    public static double[] UTMtoLL(double origin, double k, char zone, double easting, double northing) {
        double k0 = k; // 0.9996D;
        double a = ELIPSOID_PULKOVO_1942_1.EquatorialRadius;
        double eccSquared = ELIPSOID_PULKOVO_1942_1.eccentricitySquared;
        double eccPrimeSquared = (eccSquared) / (1 - eccSquared);
        double e1 = (1 - Math.sqrt(1 - eccSquared)) / (1 + Math.sqrt(1 - eccSquared));

        double N1, T1, C1, R1, D, M;
        double mu, phi1Rad;
        double x, y;

        x = easting - 500000.0D; // remove 500,000 meter offset for longitude
        y = northing;

        x += ELIPSOID_PULKOVO_1942_1.dX;
        y += ELIPSOID_PULKOVO_1942_1.dY;

        if (zone == 'S') {
            y -= 10000000.0D; // remove 10,000,000 meter offset used for southern hemisphere
        }

        M = y / k0;
        mu = M / (a * (1 - eccSquared / 4 - 3 * eccSquared * eccSquared / 64 - 5 * eccSquared * eccSquared * eccSquared / 256 - 7 * eccSquared * eccSquared * eccSquared * eccSquared / 1024));
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
        lon = Math.toDegrees(lon) + origin;

        return new double[] { lon, lat };
    }
}
