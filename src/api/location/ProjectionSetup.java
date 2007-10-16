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
 * Represents map projection information. <b>There is not such thing in JSR-179</b>.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public class ProjectionSetup {

    /* known grids */
    public static final String PROJ_LATLON      = "Latitude/Longitude";
    public static final String PROJ_MERCATOR    = "Mercator";
    public static final String PROJ_TRANSVERSE_MERCATOR = "Transverse Mercator";
    public static final String PROJ_UTM         = "(UTM) Universal Transverse Mercator";
    public static final String PROJ_BNG         = "(BNG) British National Grid";
    public static final String PROJ_SG          = "(SG) Swedish Grid";
    public static final String PROJ_IG          = "(IG) Irish Grid";
    public static final String PROJ_SUI         = "(SUI) Swiss Grid";
    public static final String PROJ_FRANCE_I    = "(I) France Zone I";
    public static final String PROJ_FRANCE_II   = "(II) France Zone II";
    public static final String PROJ_FRANCE_III  = "(III) France Zone III";
    public static final String PROJ_FRANCE_IV   = "(IV) France Zone IV";

    public static ProjectionSetup contextProjection;

    public final String name;

    public ProjectionSetup(String name) {
        this.name = name;
    }

    public String toString() {
        return (new StringBuffer(32)).append(name).append('{').append('}').toString();
    }
}
