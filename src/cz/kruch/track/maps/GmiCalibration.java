// @LICENSE@

package cz.kruch.track.maps;

import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.util.Mercator;
import cz.kruch.track.io.LineReader;
import cz.kruch.track.ui.Position;

import java.io.InputStream;
import java.io.IOException;
import java.util.Vector;

import api.location.QualifiedCoordinates;
import api.location.ProjectionSetup;

/**
 * MapCalibrator calibration support.
 *
 * @author kruhc@seznam.cz
 */
final class GmiCalibration extends Calibration {
    private static final char[] DELIM = {';'};

    GmiCalibration() {
        super();
    }

    void init(final InputStream in, final String path) throws IOException {
        super.init(path);

        // vars
        final Vector xy = new Vector(4);
        final Vector ll = new Vector(4);

        // text reader
        LineReader reader = new LineReader(in);

        // base info
        reader.readLine(false); // ignore - intro line
        reader.readLine(false); // ignore - path to image file
        setWidth(getDimension(Integer.parseInt(reader.readLine(false)))); // image width
        setHeight(getDimension(Integer.parseInt(reader.readLine(false)))); // image width

        // additional data
        CharArrayTokenizer tokenizer = new CharArrayTokenizer();
        String line;
        while ((line = reader.readLine(false)) != null) {
            if (line.startsWith("Additional Calibration Data"))
                break;
            if (line.startsWith("Border and Scale")) {
                line = reader.readLine(false);
                if (line != null) {
                    xy.removeAllElements();
                    ll.removeAllElements();
                    tokenizer.init(line, DELIM, false);
                    parseBorder(tokenizer, xy, ll);
                }
                break;
            }
//#ifndef __CN1__
            if (line == LineReader.EMPTY_LINE) // '==' is OK
//#else
            if (line.equals(LineReader.EMPTY_LINE))
//#endif
                break;

            tokenizer.init(line, DELIM, false);
            parsePoint(tokenizer, xy, ll);
        }

        // gc hint
        tokenizer = null;

        // close reader
        reader.close();
        reader = null;

        // finalize
        doFinal(null, new ProjectionSetup(ProjectionSetup.PROJ_LATLON), xy, ll);
    }

    private static void parsePoint(final CharArrayTokenizer tokenizer, final Vector xy, final Vector ll) {
        final int x = tokenizer.nextInt();
        final int y = tokenizer.nextInt();
        final double lon = tokenizer.nextDouble();
        final double lat = tokenizer.nextDouble();
        xy.addElement(Position.newInstance(x, y));
        ll.addElement(QualifiedCoordinates.newInstance(lat, lon));
    }

    private void parseBorder(final CharArrayTokenizer tokenizer, final Vector xy, final Vector ll) {
        double lat = tokenizer.nextDouble();
        double lon = tokenizer.nextDouble();
        xy.addElement(Position.newInstance(0, 0));
        ll.addElement(QualifiedCoordinates.newInstance(lat, lon));
        lat = tokenizer.nextDouble();
        lon = tokenizer.nextDouble();
        xy.addElement(Position.newInstance(getWidth() - 1, getHeight() - 1));
        ll.addElement(QualifiedCoordinates.newInstance(lat, lon));
    }
}
