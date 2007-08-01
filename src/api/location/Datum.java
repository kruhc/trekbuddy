// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.location;

public final class Datum {
    private String name;
    private Ellipsoid ellipsoid;
    public double dx, dy, dz;

    public Datum(String name, Ellipsoid ellipsoid, double dx, double dy, double dz) {
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
        return (new StringBuffer(32)).append(name).append('{').append(ellipsoid).append(',').append(dx).append(',').append(dy).append(',').append(dz).append('}').toString();
    }

    public static final Ellipsoid[] ELLIPSOIDS = {
        new Ellipsoid("Airy 1830", 6377563.396, 299.3249646),
        new Ellipsoid("Modified Airy", 6377340.189, 299.3249646),
        new Ellipsoid("Australian National", 6378160, 298.25),
        new Ellipsoid("Bessel 1841 (Namibia)", 6377483.865, 299.1528128),
        new Ellipsoid("Bessel 1841", 6377397.155, 299.1528153513206),
        new Ellipsoid("Clarke 1866", 6378206.4, 294.9786982),
        new Ellipsoid("Clarke 1880", 6378249.145, 293.465),
        new Ellipsoid("Everest (1830)", 6377276.345, 300.8017),
        new Ellipsoid("Everest (Sarawak)", 6377298.556, 300.8017),
        new Ellipsoid("Everest (1956)", 6377301.243, 300.8017),
        new Ellipsoid("Everest (1969)", 6377295.664, 300.8017),
        new Ellipsoid("Everest (Singapur)", 6377304.063, 300.8017),
        new Ellipsoid("Everest (Pakistan)", 6377309.613, 300.8017),
        new Ellipsoid("Fischer 1960", 6378155, 298.3),
        new Ellipsoid("Helmert 1906", 6378200, 298.3),
        new Ellipsoid("Hough 1960", 6378270, 297),
        new Ellipsoid("Indonesian 1974", 6378160, 298.247),
        new Ellipsoid("International 1924", 6378388, 297),
        new Ellipsoid("Krassovsky 1940", 6378245, 298.3),
        new Ellipsoid("GRS 67", 6378160, 298.2471674),
        new Ellipsoid("GRS 80", 6378137, 298.257222101),
        new Ellipsoid("South American 1969", 6378160, 298.25),
        new Ellipsoid("WGS 72", 6378135, 298.26),
        new Ellipsoid("WGS 84", 6378137, 298.257223563)
    };

    public static final Datum DATUM_WGS_84 = new Datum("WGS 84", ELLIPSOIDS[ELLIPSOIDS.length - 1], 0, 0, 0);

    public QualifiedCoordinates toLocal(QualifiedCoordinates wgs84) {
        if (this == DATUM_WGS_84) {
            return wgs84.clone();
        }

        return transform(wgs84, DATUM_WGS_84.ellipsoid, ellipsoid, -1);
    }

    public QualifiedCoordinates toWgs84(QualifiedCoordinates local) {
        if (this == DATUM_WGS_84) {
            return local.clone();
        }

        return transform(local, ellipsoid, DATUM_WGS_84.ellipsoid, 1);
    }

    /**
     * Standard Molodensky transformation.
     */
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

        return QualifiedCoordinates.newInstance(Math.toDegrees(lat + dlat),
                                                Math.toDegrees(lon + dlon)/*, from.h + dh*/);

    }

    public final static class Ellipsoid {
        private String name;
        private double equatorialRadius;
        private double flattening;
        private double eccentricitySquared, eccentricityPrimeSquared;

        public Ellipsoid(String name, double radius, double invertedFlattening) {
            this.name = name;
            this.equatorialRadius = radius;
            this.flattening = 1D / invertedFlattening;
            this.eccentricitySquared = 2 * flattening - flattening * flattening;
            this.eccentricityPrimeSquared = (eccentricitySquared) / (1D - eccentricitySquared);
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

        public String toString() {
            return name;
        }
    }
}

