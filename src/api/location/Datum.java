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

package api.location;

/**
 * Geodetic datum. <b>There is no such thing in JSR-179</b>.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class Datum {
    public static final Datum DATUM_WGS_84 = new Datum("WGS 84", Ellipsoid.ELLIPSOIDS[Ellipsoid.ELLIPSOIDS.length - 1], 0, 0, 0);
    public static Datum contextDatum;

    public final String name;
    public final Ellipsoid ellipsoid;

    private final double dx, dy, dz;

    public Datum(String name, Ellipsoid ellipsoid, double dx, double dy, double dz) {
        this.name = name;
        this.ellipsoid = ellipsoid;
        this.dx = -dx;
        this.dy = -dy;
        this.dz = -dz;
    }

    public String toString() {
        return (new StringBuffer(32)).append(name).append('{').append(ellipsoid).append(',').append(-dx).append(',').append(-dy).append(',').append(-dz).append('}').toString();
    }

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
}

