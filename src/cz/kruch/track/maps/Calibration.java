// @LICENSE@

package cz.kruch.track.maps;

import api.location.QualifiedCoordinates;
import api.location.Datum;
import api.location.ProjectionSetup;
import api.location.GeodeticPosition;
import api.location.CartesianCoordinates;
import api.location.Ellipsoid;

import cz.kruch.track.util.Mercator;
import cz.kruch.track.util.ExtraMath;
import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.ui.Position;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.Resources;

import java.util.Vector;
import java.io.IOException;
import java.io.InputStream;

/**
 * Map calibration.
 *
 * Dimensions are scaled and magnified when set. Unlike Slice, they cannot
 * be calculated on-the-fly because grid computation may not be linear.
 *
 * @author kruhc@seznam.cz
 */
abstract class Calibration {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Calibration");
//#endif

    public static final String OZI_EXT = ".map";
    public static final String GMI_EXT = ".gmi";
    public static final String XML_EXT = ".xml";
//    public static final String J2N_EXT = ".j2n";

    // map path and filename
    private String path;
    protected String imgname;

    // map datum and projection params
    private Datum datum;
    private ProjectionSetup projectionSetup;

    // cal points
    private Vector xy, ll;

    // map dimensions
    private int w, h;
    private int wu, hu;

    // main (left-top) calibration point
    private int cxyx, cxyy;
    private double cgph, cgpv;

    // grid info
    private double gridTHscale, gridLVscale;
    private double ek0, nk0;
    private double h2, v2;
    private double hScale, vScale;

    // reusable info
    private Position proximite;

    // prescale and magnifier
    int iprescale, x2;
    float fprescale;

    protected Calibration() {
        this.iprescale = Config.prescale;
        this.fprescale = ((float)Config.prescale) / 100f;
    }

    protected final void init(final String path) {
        this.path = path;
        this.proximite = new Position(0, 0);
    }

    public String getPath() {
        return path;
    }

    public String getImgname() {
        return imgname;
    }

    public int getWidth() {
        return w;
    }

    public int getHeight() {
        return h;
    }

    protected void setWidth(int width) {
        this.w = prescale(this.wu = width);
    }

    protected void setHeight(int height) {
        this.h = prescale(this.hu = height);
    }

    public ProjectionSetup getProjection() {
        return projectionSetup;
    }

    public Datum getDatum() {
        if (datum == null) {
            return Config.currentDatum;
        }

        return datum;
    }

    final void magnify(final int x2) {
        if (this.x2 != x2) {
            this.x2 = x2;
            if (x2 == 0) {
                w >>= 1;
                h >>= 1;
            } else {
                w <<= 1;
                h <<= 1;
            }
            final Vector xy = this.xy;
            for (int i = 0, N = xy.size(); i < N; i++) {
                final Position p = (Position) xy.elementAt(i);
                if (x2 == 0) {
                    p.setXy(p.getX() >> 1, p.getY() >> 1);
                } else {
                    p.setXy(p.getX() << 1, p.getY() << 1);
                }
            }
            computeInternals(xy, ll);
        }
    }

    final boolean isWithin(final QualifiedCoordinates coordinates) {
        final Position p = transform(coordinates);
        final int x = p.getX();
        if (x >= 0 && x < getWidth()) {
            final int y = p.getY();
            if (y >= 0 && y < getHeight()) {
                return true;
            }
        }
        return false;
    }

    final QualifiedCoordinates transform(final Position position) {
        return transform(position.getX(), position.getY());
    }

    final QualifiedCoordinates transform(final int px, final int py) {
        final QualifiedCoordinates localQc;
        final int dy = py - cxyy;
        final int dx = px - cxyx;
        final double h = cgph + (ek0 * dy) + (dx * (gridTHscale + dy * hScale));
        final double v = cgpv + (nk0 * dx) - (dy * (gridLVscale + dx * vScale));

        // get local coordinates
        if (projectionSetup.isCartesian()) {
            final CartesianCoordinates cc = CartesianCoordinates.newInstance(projectionSetup.zone, h, v);
            localQc = Mercator.MercatortoLL(cc, getDatum().ellipsoid, projectionSetup);
            CartesianCoordinates.releaseInstance(cc);
        } else {
            localQc = QualifiedCoordinates.newInstance(v, h);
        }

        // to WGS84
        final QualifiedCoordinates qc = getDatum().toWgs84(localQc);

        // release local
        QualifiedCoordinates.releaseInstance(localQc);

        return qc;
    }

    final Position transform(final QualifiedCoordinates qc) {
        final double H, V;

        // get local coordinates
        final QualifiedCoordinates localQc = getDatum().toLocal(qc);

        // get h,v
        if (projectionSetup.isCartesian()) {
            final CartesianCoordinates cc = Mercator.LLtoMercator(localQc,
                                                                  getDatum().ellipsoid,
                                                                  projectionSetup);
            H = cc.getH();
            V = cc.getV();
            CartesianCoordinates.releaseInstance(cc);
        } else {
            H = localQc.getH();
            V = localQc.getV();
        }

        // release local
        QualifiedCoordinates.releaseInstance(localQc);

        double _v = v2;
        double _h = h2;

        final double cgph = this.cgph;
        final double cgpv = this.cgpv;
        final int cxyx = this.cxyx;
        final int cxyy = this.cxyy;
        final double ek0 = this.ek0;
        final double nk0 = this.nk0;

        double fx = (H - cgph + (cxyx * _h) + (ek0 * cxyy) - (ek0 / _v) * (- V + cgpv + (cxyy * _v) - (nk0 * cxyx))) / (_h + (nk0 * ek0) / _v);
        double fy = (- V + cgpv + (cxyy * _v) + (nk0 * (fx - cxyx))) / _v;

        /* better precision calculations with known x,y */

        _v = gridLVscale + (fx - cxyx) * vScale;
        _h = gridTHscale + (fy - cxyy) * hScale;

        fx = (H - cgph + (cxyx * _h) + (ek0 * cxyy) - (ek0 / _v) * (- V + cgpv + (cxyy * _v) - (nk0 * cxyx))) / (_h + (nk0 * ek0) / _v);
        fy = (- V + cgpv + (cxyy * _v) + (nk0 * (fx - cxyx))) / _v;

        final int x = (int) ExtraMath.round(fx);
        final int y = (int) ExtraMath.round(fy);

        proximite.setXy(x, y);

        return proximite;
    }

    protected final void doFinal(final Datum datum, final ProjectionSetup setup,
                                 final Vector xy, final Vector ll) throws InvalidMapException {
        // dimension check
        if (w == 0 || h == 0) {
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_INVALID_MAP_DIMENSION));
        }

        // assertions
        if ((xy.size() < 2) || (ll.size() < 2)) {
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_TOO_FEW_CALPOINTS));
        }

        // paranoia
        if (xy.size() != ll.size()) {
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_MM_SIZE_MISMATCH));
        }

        // set datum
        this.datum = datum;
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("using datum " + datum);
//#endif

        // set projection setup
        if (setup == null) {
            projectionSetup = Mercator.getUTMSetup((QualifiedCoordinates) ll.firstElement());
        } else {
            projectionSetup = setup;
        }
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("using projection setup " + projectionSetup);
//#endif

        // prepare projection setup (if necessary)
        if (projectionSetup.isCartesian()) {

            // setup is for Mercator projection
            final ProjectionSetup msetup = projectionSetup;
            final Ellipsoid ellipsoid = getDatum().ellipsoid;

            /*
             * performance optimization: reuse existing vector
             */

            // lat,lon -> easting,northing
            for (int N = ll.size(), i = 0; i < N; i++) {
                final QualifiedCoordinates local = (QualifiedCoordinates) ll.elementAt(i);
                final CartesianCoordinates utm = Mercator.LLtoMercator(local, ellipsoid, msetup);
                QualifiedCoordinates.releaseInstance(local); // yes we can do it
                ll.setElementAt(utm, i);
            }

        }

        // save cal points
        this.xy = prescale(xy);
        this.ll = ll;

        // computer internals
        computeInternals(this.xy, ll);
    }

    public int prescale(final int i) {
        if (iprescale == 100) {
            return i;
        }
        return ExtraMath.prescale(fprescale, i);
    }

    public int descale(final int i) {
        if (iprescale == 100) {
            return i;
        }
        return ExtraMath.descale(fprescale, i);
    }

    int getWidthUnscaled() {
        return wu;
    }

    int getHeightUnscaled() {
        return hu;
    }
    
    private Vector prescale(final Vector xy) {
        if (iprescale == 100) {
            return xy;
        }
        final Vector result = new Vector(xy.size());
        for (int i = 0, N = xy.size(); i < N; i++) {
            final Position p = (Position) xy.elementAt(i);
            result.addElement(new Position(prescale(p.getX()), prescale(p.getY())));
        }
        return result;
    }

    private void computeInternals(final Vector xy, final Vector ll) {
        // remember main calibration point x-y
        final Position calibrationXy = (Position) xy.elementAt(0);
        cxyx = calibrationXy.getX();
        cxyy = calibrationXy.getY();

        // remember main calibration point l-l
        GeodeticPosition calibrationGp = (GeodeticPosition) ll.elementAt(0);
        cgph = calibrationGp.getH();
        cgpv = calibrationGp.getV();

        // compute pixel grid
        computeGrid(xy, ll);
    }

    private void computeGrid(final Vector xy, final Vector gp) {
        final int[] index = new int[2];

        verticalAxisByX(xy, getWidth(), 0, index);

        final double gridRVscale = Math.abs((((GeodeticPosition) gp.elementAt(index[1])).getV() - ((GeodeticPosition) gp.elementAt(index[0])).getV()) / (((Position) xy.elementAt(index[1])).getY() - ((Position) xy.elementAt(index[0])).getY()));
        final int v1 = ((Position) xy.elementAt(index[0])).getX();

        horizontalAxisByY(xy, 0, 0, index);

        final int dx = (((Position) xy.elementAt(index[1])).getX() - ((Position) xy.elementAt(index[0])).getX());
        gridTHscale = Math.abs((((GeodeticPosition) gp.elementAt(index[1])).getH() - ((GeodeticPosition) gp.elementAt(index[0])).getH()) / dx);
        final int h0 = ((Position) xy.elementAt(index[0])).getY();
        final double nk0d = (((Position) xy.elementAt(index[1])).getY() - h0) * gridRVscale;
        nk0 = (((GeodeticPosition) gp.elementAt(index[1])).getV() + nk0d - ((GeodeticPosition) gp.elementAt(index[0])).getV()) / dx;

        horizontalAxisByY(xy, 0, getHeight(), index);

        final double gridBHscale = Math.abs((((GeodeticPosition) gp.elementAt(index[1])).getH() - ((GeodeticPosition) gp.elementAt(index[0])).getH()) / (((Position) xy.elementAt(index[1])).getX() - ((Position) xy.elementAt(index[0])).getX()));
        final int h1 = ((Position) xy.elementAt(index[0])).getY();

        verticalAxisByX(xy, 0, 0, index);

        final int dy = (((Position) xy.elementAt(index[1])).getY() - ((Position) xy.elementAt(index[0])).getY());
        gridLVscale = Math.abs((((GeodeticPosition) gp.elementAt(index[1])).getV() - ((GeodeticPosition) gp.elementAt(index[0])).getV()) / dy);
        final int v0 = ((Position) xy.elementAt(index[0])).getX();
        final double ek0d = (((Position) xy.elementAt(index[1])).getX() - v0) * gridBHscale;
        ek0 = (((GeodeticPosition) gp.elementAt(index[1])).getH() - ek0d - ((GeodeticPosition) gp.elementAt(index[0])).getH()) / dy;

        h2 = (gridTHscale + gridBHscale) / 2D;
        v2 = (gridLVscale + gridRVscale) / 2D;

        hScale = (gridBHscale - gridTHscale) / (h1 - h0);
        vScale = (gridRVscale - gridLVscale) / (v1 - v0);

        if (gp.size() == 2) {
            hScale = vScale = nk0 = ek0 = 0D;
        }
    }

    private static void verticalAxisByX(final Vector xy, final int px, final int py,
                                        final int[] result) {
        int i0 = -1, i1 = -1;
        int d0 = Integer.MAX_VALUE, d1 = Integer.MAX_VALUE;
        for (int N = xy.size(), i = 0; i < N; i++) {
            final int dx = Math.abs(px - ((Position) xy.elementAt(i)).getX());
            if (dx < d0) {
                if (i0 > -1) {
                    d1 = d0;
                    i1 = i0;
                }
                d0 = dx;
                i0 = i;
            } else if (dx < d1) {
                d1 = dx;
                i1 = i;
            }
        }

        if (Math.abs(py - ((Position) xy.elementAt(i0)).getY()) < Math.abs(py - ((Position) xy.elementAt(i1)).getY())) {
            result[0] = i0;
            result[1] = i1;
        } else {
            result[0] = i1;
            result[1] = i0;
        }
    }

    private static void horizontalAxisByY(final Vector xy, final int px, final int py,
                                          final int[] result) {
        int i0 = -1, i1 = -1;
        int d0 = Integer.MAX_VALUE, d1 = Integer.MAX_VALUE;
        for (int N = xy.size(), i = 0; i < N; i++) {
            final int dy = Math.abs(py - ((Position) xy.elementAt(i)).getY());
            if (dy < d0) {
                if (i0 > -1) {
                    d1 = d0;
                    i1 = i0;
                }
                d0 = dy;
                i0 = i;
            } else if (dy < d1) {
                d1 = dy;
                i1 = i;
            }
        }

        if (Math.abs(px - ((Position) xy.elementAt(i0)).getX()) < Math.abs(px - ((Position) xy.elementAt(i1)).getX())) {
            result[0] = i0;
            result[1] = i1;
        } else {
            result[0] = i1;
            result[1] = i0;
        }
    }

    public static boolean isCalibration(String url) {
        url = url.toLowerCase();
        return url.endsWith(Calibration.OZI_EXT)
                || url.endsWith(Calibration.GMI_EXT)
                || url.endsWith(Calibration.XML_EXT);
//                || url.endsWith(Calibration.J2N_EXT);
    }

    public static boolean isCalibration(CharArrayTokenizer.Token token) {
        return token.endsWith(Calibration.OZI_EXT)
                || token.endsWith(Calibration.GMI_EXT)
                || token.endsWith(Calibration.XML_EXT);
//                || token.endsWith(Calibration.J2N_EXT);
    }

    public static Calibration newInstance(final InputStream in,
                                          final String path,
                                          final String url) throws IOException {
        final Calibration c;

        try {
            final Class factory;
            final String lurl = url.toLowerCase();
            if (lurl.endsWith(Calibration.OZI_EXT)) {
                factory = Class.forName("cz.kruch.track.maps.OziCalibration");
            } else if (lurl.endsWith(Calibration.GMI_EXT)) {
                factory = Class.forName("cz.kruch.track.maps.GmiCalibration");
//            } else if (lurl.endsWith(Calibration.XML_EXT) || lurl.endsWith(Calibration.J2N_EXT)) {
//                factory = Class.forName("cz.kruch.track.maps.J2NCalibration");
            } else {
                return null;
            }
            c = (Calibration) factory.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Calibration instance error: " + e.toString());
        }

        if (c != null) {
            try {
//#ifdef __CRC__
                if (Config.calcCrc)
                    vendorCheckedInit(c, in, path, url);
                if (!Config.calcCrc)
//#endif /* weird 'if-if' construct to satisfy IntelliJ IDEA
                    c.init(in, path);
            } catch (InvalidMapException e) {
                e.setName(path);
                throw e;
            }
        }

        return c;
    }

    protected static int getDimension(final int i) throws InvalidMapException {
        if (i < 0x100000) {
            return i;
        }
        throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_MAP_TOO_LARGE));
    }

    abstract void init(InputStream in, String path) throws IOException;

//#ifdef __CRC__

    private static void vendorCheckedInit(final Calibration c, final InputStream in,
                                          final String path, final String url) throws IOException {
        // let's spy with CRC calculation
        cz.kruch.track.io.CrcInputStream crcIn = new cz.kruch.track.io.CrcInputStream(in);
        c.init(crcIn, path);        // usual init
        while (crcIn.read() > -1);  // complete reading
        crcIn.dispose();            // really closes the stream
    }

//#endif

}
