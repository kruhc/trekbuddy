// @LICENSE@

package cz.kruch.track.maps;

import org.kxml2.io.HXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Vector;

import api.io.BufferedInputStream;
import api.location.Datum;
import api.location.ProjectionSetup;
import api.location.QualifiedCoordinates;
import api.file.File;

import javax.microedition.io.Connector;

import cz.kruch.track.ui.Position;
import cz.kruch.track.util.Mercator;
import cz.kruch.track.util.ExtraMath;
import cz.kruch.track.configuration.Config;

/**
 * No-map atlas loader.
 *
 * @author kruhc@seznam.cz
 */
final class NoMapLoader extends /*Map.*/Loader /*implements Atlas.Loader*/ {

    private static final String TAG_METADATA    = "metadata";
    private static final String TAG_NAME        = "name";
//    private static final String TAG_BOUNDS      = "bounds";
//    private static final String ATTR_MINLAT     = "minlat";
//    private static final String ATTR_MINLON     = "minlon";
//    private static final String ATTR_MAXLAT     = "maxlat";
//    private static final String ATTR_MAXLON     = "maxlon";
    private static final String TAG_EXTENSIONS  = "extensions";
    private static final String TAG_BACKGROUND  = "background";
    private static final String ATTR_COLOR      = "color";

    private static final int MIN_MAP_WIDTH  = 1920; // full HD
    private static final int MIN_MAP_HEIGHT = 1920; // full HD

    private static final String FILE_SUFFIX = ".xml";
    private static final String BASENAME    = "default";

    private static int bgcolor;

    void loadMeta() throws IOException {
        // set loader properties
        basename = BASENAME;
        tileWidth = MIN_MAP_WIDTH;
        tileHeight = MIN_MAP_HEIGHT;

        // set loaded map properties
        map.bgColor = bgcolor;
        map.virtual = true;
    }

    void loadSlice(final Slice slice) throws IOException {
        slice.setImage(Slice.NO_IMAGE);
    }

    public void loadIndex(Atlas atlas, String url, String baseUrl) throws IOException {
        // mark as virtual
        atlas.virtual = true;

        // input stream and parser
        InputStream in = null;
        HXmlParser parser = null;

        try {
            // open input
            in = new BufferedInputStream(Connector.openInputStream(url), 4096);

            // create parser
            parser = new HXmlParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);

            // init parser
            parser.setInput(in, null); // null is for encoding autodetection

            // parse
            parse(parser, atlas);

        } catch (XmlPullParserException e) {

            // rethrow wrapped
            throw new IOException(e.toString());

        } finally {

            // cleanup
            HXmlParser.closeQuietly(parser);
            File.closeQuietly(in);

        }
    }

    private static void parse(final HXmlParser parser, final Atlas atlas) throws IOException, XmlPullParserException {

        String name = null;
//        double minlat = Double.NaN, minlon = Double.NaN, maxlat = Double.NaN, maxlon = Double.NaN;

        for (int depth = 0, eventType = parser.next(); eventType != XmlPullParser.END_DOCUMENT; eventType = parser.next()) {
            switch (eventType) {
                case XmlPullParser.START_TAG: {
                    switch (depth) {
                        case 1: {
                            final String tag = parser.getName();
                            if (TAG_METADATA.equals(tag)) {
                                // found metadata
                                depth = 2;
                            } else {
                                parser.skipSubTree();
                            }
                        } break;
                        case 2: {
                            final String tag = parser.getName();
                            if (TAG_NAME.equals(tag)) {
                                name = parser.nextText();
//                            } else if (TAG_BOUNDS.equals(tag)) {
//                                minlat = Double.parseDouble(parser.getAttributeValue(null, ATTR_MINLAT));
//                                minlon = Double.parseDouble(parser.getAttributeValue(null, ATTR_MINLON));
//                                maxlat = Double.parseDouble(parser.getAttributeValue(null, ATTR_MAXLAT));
//                                maxlon = Double.parseDouble(parser.getAttributeValue(null, ATTR_MAXLON));
                            } else if (TAG_EXTENSIONS.equals(tag)) {
                                depth = 3;
                            } else {
                                parser.skipSubTree();
                            }
                        } break;
                        case 3: {
                            final String tag = parser.getName();
                            if (TAG_BACKGROUND.equals(tag)) {
                                bgcolor = Integer.parseInt(parser.getAttributeValue(null, ATTR_COLOR), 16);
                            } else {
                                parser.skipSubTree();
                            }
                        } break;
                        default: {
                            depth++;
                        }
                    }
                } break;
                case XmlPullParser.END_TAG: {
                    switch (depth) {
// TODO make it make sense
//                        case 1: {
//                            final String tag = parser.getName();
//                            if (TAG_METADATA.equals(tag)) {
//                                createFakeAtlas(atlas, minlat, minlon, maxlat, maxlon);
//                            }
//                        } break;
                        case 2: {
                            final String tag = parser.getName();
                            if (TAG_METADATA.equals(tag)) {
//                                minlat = Config.latAny - 15D;
//                                if (minlat < -85D) minlat = -85D;
//                                maxlat = Config.latAny + 15D;
//                                if (maxlat > 85D) maxlat = 85D;
//                                minlon = Config.lonAny - 30D;
//                                if (minlon < -180D) minlon = -180D;
//                                maxlon = Config.lonAny + 30D;
//                                if (maxlon > 180D) maxlon = 180D;
//                                createFakeAtlas(atlas, minlat, minlon, maxlat, maxlon);
                                createScaleAtlas(atlas);
                            }
                        } break;
                        default: {
                            depth--;
                        }
                    }
                } break;
            }
        }
    }

    private static void createScaleAtlas(final Atlas atlas) throws IOException {
        // vars
        final StringBuffer sb = new StringBuffer(16);
        final int[] scales = { 1000, 500, 100, 50, 25, 10, 5/*, 1*/ };
        int idx = 0, iscale = 0;

        // area boundaries
        final QualifiedCoordinates any = QualifiedCoordinates.newInstance(Config.latAny, Config.lonAny);
        final QualifiedCoordinates qcl = QualifiedCoordinates.project(any, 270D, MIN_MAP_WIDTH * 1000 / 2);
        final QualifiedCoordinates qcr = QualifiedCoordinates.project(any, 90D, MIN_MAP_WIDTH * 1000 / 2);
        final QualifiedCoordinates qct = QualifiedCoordinates.project(any, 0D, MIN_MAP_HEIGHT * 1000 / 2);
        final QualifiedCoordinates qcb = QualifiedCoordinates.project(any, 180D, MIN_MAP_HEIGHT * 1000 / 2);
        final double minlat = qcb.getLat();
        final double maxlat = qct.getLat();
        final double minlon = qcl.getLon();
        final double maxlon = qcr.getLon();

        // for all scales
        while (iscale < scales.length) {

            // create fake map path
            sb.setLength(0);
            sb.append(idx).append('/').append(BASENAME).append('/').append(BASENAME).append(FILE_SUFFIX);

            // calculate map dimensions
            final int w = MIN_MAP_WIDTH * 1000 / scales[iscale];
            final int h = MIN_MAP_HEIGHT * 1000 / scales[iscale];
            final Calibration cal = createFakeCalibration(sb.toString(),
                                                          w, h,
                                                          minlat, minlon, maxlat, maxlon);

            // create leayer name
            sb.setLength(0);
            sb.append('[').append(idx).append("]  1px : ").append(findLayerScale(cal, maxlon - minlon)).append('m');

            // add new layer and its map
            final Hashtable layerCollection = Atlas.getLayerCollection(atlas, sb.toString());
            layerCollection.put(BASENAME, cal);
            idx++;

            // next scale
            iscale++;
        }
    }

/*
    private static void createFakeAtlas(final Atlas atlas,
                                        final double minlat, final double minlon,
                                        final double maxlat, final double maxlon) throws IOException {
        StringBuffer sb = new StringBuffer(16);
        int w = MIN_MAP_WIDTH, h = MIN_MAP_HEIGHT, idx = 0;
        while (w <= 0xfffff) {
            sb.setLength(0);
            sb.append(idx).append('/').append(BASENAME).append('/').append(BASENAME).append(FILE_SUFFIX);
            final Calibration cal = createFakeCalibration(sb.toString(), w, h, minlat, minlon, maxlat, maxlon);
            sb.setLength(0);
            sb.append('[').append(idx).append("]  1 : ").append(findLayerScale(cal, maxlon - minlon));
            final Hashtable layerCollection = atlas.getLayerCollection(atlas, sb.toString());
            layerCollection.put(BASENAME, cal);
            idx++;
            w <<= 1;
            h <<= 1;
            // hack to get into XY limit, see Slice
            if (w == 0x100000) {
                w = 0xfffff;
            }
        }
    }
*/

    private static Calibration createFakeCalibration(final String path,
                                                     final int width, final int height,
                                                     final double minlat, final double minlon,
                                                     final double maxlat, final double maxlon) throws IOException {
        final FakeCalibration calibration = new FakeCalibration();
        calibration.init(null, path);
        calibration.init(width, height, minlat, minlon, maxlat, maxlon);
        return calibration;
    }

    private static String findLayerScale(final Calibration cal, final double lonExt) {
        // use 10% of map width at some lat for calculation
        QualifiedCoordinates qc0 = QualifiedCoordinates.newInstance(Config.latAny, 0);
        QualifiedCoordinates qc1 = QualifiedCoordinates.newInstance(Config.latAny, lonExt / 10);
        double scale = qc0.distance(qc1) / (((float) cal.getWidth()) / 10);

        // valid scale?
        if (scale > 0F) { // this always true now, isn't it?
            long px100 = (long) (scale * 100);
            final int grade = ExtraMath.grade(px100);
            long guess = (px100 / grade) * grade;
            if (px100 - guess > (grade >> 1)) {
                guess += grade;
            }
            return Long.toString(guess / 100);
        }

        return "?";
    }

    private static class FakeCalibration extends Calibration {

        /* to avoid $1 */
        public FakeCalibration() {
        }

        void init(final InputStream in, final String path) throws IOException {
            super.init(path);
        }

        void init(final int width, final int height,
                  final double minlat, final double minlon,
                  final double maxlat, final double maxlon) throws InvalidMapException {
            final Vector xy = new Vector(4);
            final Vector ll = new Vector(4);
            xy.addElement(Position.newInstance(0, 0));
            ll.addElement(QualifiedCoordinates.newInstance(maxlat, minlon));
            xy.addElement(Position.newInstance(width, 0));
            ll.addElement(QualifiedCoordinates.newInstance(maxlat, maxlon));
            xy.addElement(Position.newInstance(width, height));
            ll.addElement(QualifiedCoordinates.newInstance(minlat, maxlon));
            xy.addElement(Position.newInstance(0, height));
            ll.addElement(QualifiedCoordinates.newInstance(minlat, minlon));
            final Datum datum = Datum.WGS_84;
            final ProjectionSetup projectionSetup = Mercator.getMercatorSetup(ll);
            setWidth(getDimension(width));
            setHeight(getDimension(height));
            doFinal(datum, projectionSetup, xy, ll);
        }
    }
}
