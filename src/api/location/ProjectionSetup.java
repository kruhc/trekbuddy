// @LICENSE@

package api.location;

/**
 * Represents map projection information. <b>There is no such thing in JSR-179</b>.
 *
 * @author kruhc@seznam.cz
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
    public static final String PROJ_LCC         = "Lambert Conformal Conic";

    public static final int PROJECTION_MERCATOR     = 0;
    public static final int PROJECTION_SUI          = 1;
    public static final int PROJECTION_FRANCE_n     = 2;
    public static final int PROJECTION_BNG          = 3;
    public static final int PROJECTION_IG           = 4;
    public static final int PROJECTION_LCC          = 5;
    public static final int PROJECTION_UTM          = 6;
    public static final int PROJECTION_LATLON       = 666;

    public static ProjectionSetup contextProjection;

    public final String name;
    public final double lonOrigin, latOrigin;
    public final double parallel1, parallel2;
    public final double k0;
    public final double falseEasting, falseNorthing;
    public final short zoneNumber;
    public final char zoneLetter;
    public final char[] zone;

    public int code;

    /*
     * Latitude/Longitude constructor.
     */
    public ProjectionSetup(final String name) {
        this.name = name;
        decode(name);
        this.zoneNumber = -1;
        this.zoneLetter = 'Z';
        this.k0 = Double.NaN;
        this.lonOrigin = this.latOrigin = Double.NaN;
        this.parallel1 = this.parallel2 = Double.NaN;
        this.falseEasting = this.falseNorthing = Double.NaN;
        this.zone = null;
    }

    /*
     * UTM constructor.
     */
    public ProjectionSetup(final String name,
                           final int zoneNumber, final char zoneLetter,
                           final double lonOrigin, final double latOrigin,
                           final double k0,
                           final double falseEasting, final double falseNorthing) {
        this.name = name;
        decode(name);
        this.zoneNumber = (short) zoneNumber;
        this.zoneLetter = zoneLetter;
        this.zone = (new StringBuffer(32)).append(zoneNumber).append(zoneLetter).toString().toCharArray();
        this.lonOrigin = lonOrigin;
        this.latOrigin = latOrigin;
        this.parallel1 = this.parallel2 = Double.NaN;
        this.k0 = k0;
        this.falseEasting = falseEasting;
        this.falseNorthing = falseNorthing;
    }

    /*
     * Mercator and Transverse Mercator constructor.
     */
    public ProjectionSetup(final String name, final char[] zone,
                           final double lonOrigin, final double latOrigin,
                           final double k0,
                           final double falseEasting, final double falseNorthing) {
        this.name = name;
        decode(name);
        this.zoneNumber = -1;
        this.zoneLetter = 'Z';
        this.zone = zone;
        this.lonOrigin = lonOrigin;
        this.latOrigin = latOrigin;
        this.parallel1 = this.parallel2 = Double.NaN;
        this.k0 = k0;
        this.falseEasting = falseEasting;
        this.falseNorthing = falseNorthing;
    }

    /*
     * Generic constructor.
     */
    public ProjectionSetup(final String name, final char[] zone,
                           final double lonOrigin, final double latOrigin,
                           final double k0,
                           final double falseEasting, final double falseNorthing,
                           final double parallel1, final double parallel2) {
        this.name = name;
        decode(name);
        this.zoneNumber = -1;
        this.zoneLetter = 'Z';
        this.zone = zone;
        this.lonOrigin = lonOrigin;
        this.latOrigin = latOrigin;
        this.parallel1 = parallel1;
        this.parallel2 = parallel2;
        this.k0 = k0;
        this.falseEasting = falseEasting;
        this.falseNorthing = falseNorthing;
    }

    // TODO optimize
    public String toString() {
        if (PROJ_MERCATOR.equals(name) || PROJ_LATLON.equals(name)) {
            return (new StringBuffer(32)).append(name).append('{').append('}').toString();
        }

        final StringBuffer sb = new StringBuffer(32);
        sb.append(name).append('{');
        if (zone != null) {
            sb.append(zone).append(',');
        }
        sb.append(lonOrigin).append(',').append(latOrigin).append(',');
        if (!Double.isNaN(k0)) sb.append(k0);
        sb.append(',');
        if (!Double.isNaN(falseEasting)) sb.append(falseEasting);
        sb.append(',');
        if (!Double.isNaN(falseNorthing)) sb.append(falseNorthing);
        sb.append(',');
        if (!Double.isNaN(parallel1)) sb.append(parallel1);
        sb.append(',');
        if (!Double.isNaN(parallel2)) sb.append(parallel2);
        sb.append('}');

        return sb.toString();
    }

    public boolean isCartesian() {
        return code != PROJECTION_LATLON; 
    }

    private void decode(final String name) {
        if (PROJ_MERCATOR.equals(name)) {
            this.code = PROJECTION_MERCATOR;
        } else if (PROJ_SUI.equals(name)) {
            this.code = PROJECTION_SUI;
        } else if (name.indexOf("France Zone") > -1) {
            this.code = PROJECTION_FRANCE_n;
        } else if (PROJ_BNG.equals(name)) {
            this.code = PROJECTION_BNG;
        } else if (PROJ_IG.equals(name)) {
            this.code = PROJECTION_IG;
        } else if (PROJ_LCC.equals(name)) {
            this.code = PROJECTION_LCC;
        } else if (PROJ_UTM.equals(name)) {
            this.code = PROJECTION_UTM;
        } else if (PROJ_LATLON.equals(name)) {
            this.code = PROJECTION_LATLON;
        } else {
            this.code = -1;
        }
    }
}
