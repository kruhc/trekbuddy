// @LICENSE@

package api.location;

/**
 * Geodetic datum. <b>There is no such thing in JSR-179</b>.
 *
 * @author kruhc@seznam.cz
 */
public final class Datum {
    public static Datum WGS_84;
    public static Datum contextDatum;

    public final String name;
    public final Ellipsoid ellipsoid;

    private final double dx, dy, dz;

    public Datum(final String name, final Ellipsoid ellipsoid,
                 final double dx, final double dy, final double dz) {
        this.name = name;
        this.ellipsoid = ellipsoid;
        this.dx = -dx;
        this.dy = -dy;
        this.dz = -dz;
    }

    public String toString() {
        return (new StringBuffer(32)).append(name).append('{').append(ellipsoid).append(',').append(-dx).append(',').append(-dy).append(',').append(-dz).append('}').toString();
    }

    public QualifiedCoordinates toLocal(final QualifiedCoordinates qc) {
        if (this == WGS_84) {
            return qc._clone();
        }

        return transform(qc, WGS_84.ellipsoid, ellipsoid, -1);
    }

    public QualifiedCoordinates toWgs84(final QualifiedCoordinates qc) {
        if (this == WGS_84) {
            return qc._clone();
        }

        return transform(qc, ellipsoid, WGS_84.ellipsoid, 1);
    }

    /**
     * Standard Molodensky transformation.
     */
    private QualifiedCoordinates transform(final QualifiedCoordinates local,
                                           final Ellipsoid fromEllipsoid,
                                           final Ellipsoid toEllipsoid,
                                           final int sign) {
        final double feer = fromEllipsoid.equatorialRadius;
        final double fees = fromEllipsoid.eccentricitySquared;
        
        final double da = toEllipsoid.equatorialRadius - feer;
        final double df = toEllipsoid.flattening - fromEllipsoid.flattening;
        final double lat = Math.toRadians(local.getLat());
        final double lon = Math.toRadians(local.getLon());

        final double slat = Math.sin(lat);
        final double clat = Math.cos(lat);
        final double slon = Math.sin(lon);
        final double clon = Math.cos(lon);
        final double ssqlat = slat * slat;
        final double bda = 1D - fromEllipsoid.flattening;
        double dlat, dlon /*, dh*/;

        final double v = 1D - fees * ssqlat;
        final double rn = feer / Math.sqrt(v);
        final double rm = feer * (1D - fees) / Math.sqrt(v * v * v); // sqrt(v^3) = pow(v, 1.5)

        final double sign_x_dx = sign * dx;
        final double sign_x_dy = sign * dy;
        final double slat_x_clat = slat * clat;

        dlat = (((((sign_x_dx * slat * clon + sign_x_dy * slat * slon) - (sign * dz) * clat)
                + (da * ((rn * fees * slat_x_clat) / feer)))
                + (df * (rm * bda + rn / bda) * slat_x_clat)))
                / (rm /* + from.h*/);

        dlon = (sign_x_dx * slon - sign_x_dy * clon) / ((rn /* + from.h*/) * clat);

/*
        dh = (- dx * clat * clon) + (- dy * clat * slon) + (- dz * slat)
                - (da * (from_a / rn)) + ((df * rn * ssqlat) / adb);
*/

        return QualifiedCoordinates.newInstance(Math.toDegrees(lat + dlat),
                                                Math.toDegrees(lon + dlon)/*, from.h + dh*/);

    }
}

