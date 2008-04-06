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
 * Ellipsoid bean. <b>There is no such thing in JSR-179</b>.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class Ellipsoid {
    final String name;
    final double equatorialRadius;
    final double flattening;
    final double eccentricitySquared, eccentricityPrimeSquared;

    public Ellipsoid(final String name, final double radius, final double invertedFlattening) {
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

    public static final Ellipsoid[] ELLIPSOIDS = {
        new Ellipsoid("Airy 1830", 6377563.396, 299.3249646),
        new Ellipsoid("Modified Airy", 6377340.189, 299.3249646),
        new Ellipsoid("Australian National", 6378160, 298.25),
        new Ellipsoid("Bessel 1841 (Namibia)", 6377483.865, 299.1528128),
        new Ellipsoid("Bessel 1841", 6377397.155, 299.1528153513206),
        new Ellipsoid("Clarke 1866", 6378206.4, 294.9786982),
        new Ellipsoid("Clarke 1880", 6378249.145, 293.465),
        new Ellipsoid("Clarke 1880 IGN", 6378249.2, 293.466021),
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
}