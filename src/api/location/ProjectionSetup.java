// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.location;

public class ProjectionSetup {
    public static final String PROJ_LATLON      = "Latitude/Longitude";
    public static final String PROJ_MERCATOR    = "Mercator";
    public static final String PROJ_TRANSVERSE_MERCATOR = "Transverse Mercator";
    public static final String PROJ_UTM         = "(UTM) Universal Transverse Mercator";
    public static final String PROJ_BNG         = "(BNG) British National Grid";
    public static final String PROJ_SG          = "(SG) Swedish Grid";
    public static final String PROJ_IG          = "(IG) Irish Grid";

    private String name;

    public ProjectionSetup(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String toString() {
        return (new StringBuffer(32)).append(getName()).append('{').append('}').toString();
    }
}
