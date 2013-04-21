// @LICENSE@

package cz.kruch.track.maps;

import org.kxml2.io.HXmlParser;
import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.io.IOException;
import java.util.Vector;

import cz.kruch.track.ui.Position;

import api.location.QualifiedCoordinates;
import api.location.ProjectionSetup;

/**
 * J2N navigation program calibration support.
 *
 * @author kruhc@seznam.cz
 */
final class J2NCalibration extends Calibration {
    private static final String TAG_NAME        = "name";
    private static final String TAG_POSITION    = "position";
    private static final String TAG_LATITUDE    = "latitude";
    private static final String TAG_LONGITUDE   = "longitude";
    private static final String TAG_IMAGEWIDTH  = "imageWidth";
    private static final String TAG_IMAGEHEIGHT = "imageHeight";

    J2NCalibration() {
        super();
    }

    void init(final InputStream in, final String path) throws InvalidMapException {
        super.init(path);

        final Vector xy = new Vector();
        final Vector ll = new Vector();
        HXmlParser parser = new HXmlParser();

        try {
            parser.setInput(in, null); // null is for encoding autodetection

            boolean keepParsing = true;
            String currentTag = null;

            int x0 = -1, y0 = -1;
            double lat0 = 0D, lon0 = 0D;

            while (keepParsing) {
                switch (parser.next()) {
                    case XmlPullParser.START_TAG: {
                        currentTag = parser.getName();
                        if (TAG_POSITION.equals(currentTag)) {
                            x0 = Integer.parseInt(parser.getAttributeValue(null, "x"));
                            y0 = Integer.parseInt(parser.getAttributeValue(null, "y"));
                        }
                    }
                    break;
                    case XmlPullParser.END_TAG: {
                        if (TAG_POSITION.equals(parser.getName())) {
                            xy.addElement(Position.newInstance(x0, y0));
                            ll.addElement(QualifiedCoordinates.newInstance(lat0, lon0));
                        }
                        currentTag = null;
                    }
                    break;
                    case XmlPullParser.TEXT: {
                        if (currentTag != null) {
                            String text = parser.getText().trim();
                            if (TAG_NAME.equals(currentTag)) {
                                imgname = text;
                            } else if (TAG_LATITUDE.equals(currentTag)) {
                                lat0 = Double.parseDouble(text);
                            } else if (TAG_LONGITUDE.equals(currentTag)) {
                                lon0 = Double.parseDouble(text);
                            } else if (TAG_IMAGEWIDTH.equals(currentTag)) {
                                setWidth(getDimension(Integer.parseInt(text)));
                            } else if (TAG_IMAGEHEIGHT.equals(currentTag)) {
                                setHeight(getDimension(Integer.parseInt(text)));
                            }
                        }
                    }
                    break;
                    case XmlPullParser.END_DOCUMENT: {
                        keepParsing = false;
                    }
                    break;
                }
            }

        } catch (Exception e) {
            throw new InvalidMapException(e.toString());
        } finally {
            HXmlParser.closeQuietly(parser);
        }

        // gc hint
        parser = null;

        // finalize
        doFinal(null, new ProjectionSetup(ProjectionSetup.PROJ_LATLON), xy, ll);
    }
}
