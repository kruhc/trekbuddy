// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.util;

import api.location.QualifiedCoordinates;

public final class Datum {
    private String name;
    private Ellipsoid ellipsoid;
    public int dx, dy, dz;

    public Datum(String name, Ellipsoid ellipsoid, int dx, int dy, int dz) {
        this.name = name;
        this.ellipsoid = ellipsoid;
        this.dx = -1 * dx;
        this.dy = -1 * dy;
        this.dz = -1 * dz;
    }

    public String getName() {
        return name;
    }

    public Ellipsoid getEllipsoid() {
        return ellipsoid;
    }

    public String toString() {
        return name;
    }

    private static final Ellipsoid ELLIPSOID_AIRY_1830              = new Ellipsoid("Airy 1830", 6377563.396, 299.3249646);
    private static final Ellipsoid ELLIPSOID_AUSTRALIAN             = new Ellipsoid("Australian National", 6378160, 298.25);
//    private static final Ellipsoid ELLIPSOID_BESSEL_1841_NAMIBIA    = new Ellipsoid("Bessel 1841 (Namibia)", 6377483.865, 299.1528128);
    private static final Ellipsoid ELLIPSOID_BESSEL_1841            = new Ellipsoid("Bessel 1841", 6377397.155, 299.1528128);
    private static final Ellipsoid ELLIPSOID_CLARKE_1866            = new Ellipsoid("Clarke 1866", 6378206.4, 294.9786982);
//    private static final Ellipsoid ELLIPSOID_CLARKE_1880            = new Ellipsoid("Clarke 1880", 6378249.145, 293.465);
//    private static final Ellipsoid ELLIPSOID_EVEREST_1830           = new Ellipsoid("Everest (1830)", 6377276.345, 300.8017);
//    private static final Ellipsoid ELLIPSOID_EVEREST_SARAWAK        = new Ellipsoid("Everest (Sarawak)", 6377298.556, 300.8017);
//    private static final Ellipsoid ELLIPSOID_EVEREST_1956           = new Ellipsoid("Everest (1956)", 6377301.243, 300.8017);
//    private static final Ellipsoid ELLIPSOID_EVEREST_1969           = new Ellipsoid("Everest (1969)", 6377295.664, 300.8017);
//    private static final Ellipsoid ELLIPSOID_EVEREST_SINGAPUR       = new Ellipsoid("Everest (Singapur)", 6377304.063, 300.8017);
//    private static final Ellipsoid ELLIPSOID_EVEREST_PAKISTAN       = new Ellipsoid("Everest (Pakistan)", 6377309.613, 300.8017);
//    private static final Ellipsoid ELLIPSOID_FISCHER_1960           = new Ellipsoid("Fischer 1960", 6378155, 298.3);
//    private static final Ellipsoid ELLIPSOID_HELMERT_1906           = new Ellipsoid("Helmert 1906", 6378200, 298.3);
//    private static final Ellipsoid ELLIPSOID_HOUGH_1960             = new Ellipsoid("Hough 1960", 6378270, 297);
//    private static final Ellipsoid ELLIPSOID_INDONESIAN_1974        = new Ellipsoid("Indonesian 1974", 6378160, 298.247);
//    private static final Ellipsoid ELLIPSOID_INTERNATIONAL_1924     = new Ellipsoid("International 1924", 6378388, 297);
    private static final Ellipsoid ELLIPSOID_KRASSOVSKY_1940        = new Ellipsoid("Krassovsky 1940", 6378245, 298.3);
//    private static final Ellipsoid ELLIPSOID_GRS80                  = new Ellipsoid("GRS 80", 6378137, 298.257222101);
//    private static final Ellipsoid ELLIPSOID_SOUTH_AMERICAN_1969    = new Ellipsoid("South American 1969", 6378160, 298.25);
//    private static final Ellipsoid ELLIPSOID_WGS72                  = new Ellipsoid("WGS 72", 6378135, 298.26);
    private static final Ellipsoid ELLIPSOID_WGS84                  = new Ellipsoid("WGS 84", 6378137, 298.257223563);

    /* todo make private */
    public static final Datum DATUM_WGS_84          = new Datum("WGS84", ELLIPSOID_WGS84, 0, 0, 0);
    public static final Datum DATUM_AGD_66          = new Datum("AGD66", ELLIPSOID_AUSTRALIAN, -133, -48, 148);
    public static final Datum DATUM_CH_1903         = new Datum("CH1903", ELLIPSOID_BESSEL_1841, 660, 14, 369);
    public static final Datum DATUM_NAD_27_CONUS    = new Datum("NAD27 (CONUS)", ELLIPSOID_CLARKE_1866, -8, 160, 176);
    public static final Datum DATUM_OSGB_36         = new Datum("OSGB36", ELLIPSOID_AIRY_1830, 375, -111, 431);
    public static final Datum DATUM_S_42_CZ         = new Datum("S-42 (CZ)", ELLIPSOID_KRASSOVSKY_1940, 26, -121, -78);
    public static final Datum DATUM_S_42_POLAND     = new Datum("S-42 (Poland)", ELLIPSOID_KRASSOVSKY_1940, 23, -124, -82);
    public static final Datum DATUM_S_42_RUSSIA     = new Datum("S-42 (Russia)", ELLIPSOID_KRASSOVSKY_1940, 28, -130, -95);
    public static final Datum DATUM_S_JTSK          = new Datum("S-JTSK", ELLIPSOID_BESSEL_1841, 589, 76, 480);

    public static final Datum[] DATUMS = new Datum[]{
        DATUM_WGS_84,
        DATUM_AGD_66,
        DATUM_CH_1903,
        DATUM_NAD_27_CONUS,
        DATUM_OSGB_36,
        DATUM_S_42_CZ,
        DATUM_S_42_POLAND,
        DATUM_S_42_RUSSIA,
        DATUM_S_JTSK
    };

    public static Datum current = DATUM_WGS_84;

    public static String use(String id) {
        for (int i = DATUMS.length; --i >= 0; ) {
            if (id.equals(DATUMS[i].getName())) {
                current = DATUMS[i];
            }
        }

        return id;
    }

    public QualifiedCoordinates toLocal(QualifiedCoordinates wgs84) {
        if (this == DATUM_WGS_84) {
            return wgs84;
        }

        return transform(wgs84, DATUM_WGS_84.ellipsoid, ellipsoid, -1);
    }

    public QualifiedCoordinates toWgs84(QualifiedCoordinates local) {
        if (this == DATUM_WGS_84) {
            return local;
        }

        return transform(local, ellipsoid, DATUM_WGS_84.ellipsoid, 1);
    }

    private QualifiedCoordinates transform(QualifiedCoordinates local,
                                           Ellipsoid fromEllipsoid,
                                           Ellipsoid toEllipsoid,
                                           int sign) {
        double da = toEllipsoid.equatorialRadius - fromEllipsoid.equatorialRadius;
        double df = toEllipsoid.flattening - fromEllipsoid.flattening;
        double lat = Math.toRadians(local.getLat());
        double lon = Math.toRadians(local.getLon());

        double slat = Math.sin(lat);
        double clat = Math.cos(lat);
        double slon = Math.sin(lon);
        double clon = Math.cos(lon);
        double ssqlat = slat * slat;
        double bda = 1D - fromEllipsoid.flattening;
        double dlat, dlon /*, dh*/;

        double v = 1D - fromEllipsoid.eccentricitySquared * ssqlat;
        double rn = fromEllipsoid.equatorialRadius / Math.sqrt(v);
        double rm = fromEllipsoid.equatorialRadius * (1D - fromEllipsoid.eccentricitySquared) / Math.sqrt(v * v * v); // sqrt(v^3) = pow(v, 1.5)

        dlat = ((((((sign * dx) * slat * clon + (sign * dy) * slat * slon) - (sign * dz) * clat)
                + (da * ((rn * fromEllipsoid.eccentricitySquared * slat * clat) / fromEllipsoid.equatorialRadius)))
                + (df * (rm * bda + rn / bda) * slat * clat)))
                / (rm /* + from.h*/);

        dlon = ((sign * dx) * slon - (sign * dy) * clon) / ((rn /* + from.h*/) * clat);

/*
        dh = (- dx * clat * clon) + (- dy * clat * slon) + (- dz * slat)
                - (da * (from_a / rn)) + ((df * rn * ssqlat) / adb);
*/

        return new QualifiedCoordinates(Math.toDegrees(lat + dlat),
                                        Math.toDegrees(lon + dlon)/*, from.h + dh*/);

    }

    public final static class Ellipsoid {
        private String name;
        private double equatorialRadius;
        private double flattening;
        private double eccentricitySquared, eccentricityPrimeSquared;
        private double degree2meter;

        public Ellipsoid(String name, double radius, double invertedFlattening) {
            this.name = name;
            this.equatorialRadius = radius;
            this.flattening = 1D / invertedFlattening;
            this.eccentricitySquared = 2 * flattening - flattening * flattening;
            this.eccentricityPrimeSquared = (eccentricitySquared) / (1D - eccentricitySquared);
            this.degree2meter = 2 * Math.PI * radius / 360D;
        }

        public String getName() {
            return name;
        }

        public double getFlattening() {
            return flattening;
        }

        public double getEquatorialRadius() {
            return equatorialRadius;
        }

        public double getEccentricitySquared() {
            return eccentricitySquared;
        }

        public double getEccentricityPrimeSquared() {
            return eccentricityPrimeSquared;
        }

        public double getDegree2meter() {
            return degree2meter;
        }

        public String toString() {
            return name;
        }
    }
}

