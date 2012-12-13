// @LICENSE@

package cz.kruch.track.maps;

import cz.kruch.track.io.LineReader;
import cz.kruch.track.Resources;
import cz.kruch.track.util.CharArrayTokenizer;

import javax.microedition.lcdui.Image;
import java.io.IOException;
import java.io.InputStream;

final class JarLoader extends Map.Loader /*implements Atlas.Loader*/ {
    private static final String DEFAULT_OZI_MAP = "/resources/world.map";
    private static final String DEFAULT_GMI_MAP = "/resources/world.gmi";
    private static final String RESOURCES_SET_DIR = "/resources/set/";
    private static final String RESOURCES_SET_FILE = "/resources/world.set";

    private final StringBuffer snsb;

    JarLoader() {
        this.snsb = new StringBuffer(64);
    }

    void loadMeta() throws IOException {
        // local ref
        final Map map = this.map;
        
        // input
        InputStream in = null;
        LineReader reader = null;

        // embedded map path
        String path;

        try {
            // look for Ozi calibration first
            in = JarLoader.class.getResourceAsStream(path = DEFAULT_OZI_MAP);
            if (in == null) {
                // look for GMI then
                in = JarLoader.class.getResourceAsStream(path = DEFAULT_GMI_MAP);
                if (in == null) {
                    // we are screwed
                    throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_LOAD_MAP_FAILED));
                }
            }

            // process calibration
            map.setCalibration(Calibration.newInstance(in, path, path));

            // parse tileset file
            reader = new LineReader(JarLoader.class.getResourceAsStream(RESOURCES_SET_FILE));
            CharArrayTokenizer.Token token = reader.readToken(false);
            while (token != null) {
                addSlice(token);
                token = null; // gc hint
                token = reader.readToken(false);
            }
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("in-jar tileset file processed");
//#endif
        } catch (InvalidMapException e) {
            throw e;
        } catch (IOException e) {
            throw new InvalidMapException("Corrupted built-in map: " + e.toString());
        } finally {

            // check for Palm - it resets it :-(
            if (!cz.kruch.track.TrackingMIDlet.palm) {
                try {
                    in.close();
                } catch (Exception e) { // NPE or IOE
                    // ignore
                }
                try {
                    reader.close();
                } catch (Exception e) { // NPE or IOE
                    // ignore
                }
            }
        }
    }

    void loadSlice(final Slice slice) throws IOException {
        // path sb
        final StringBuffer sb = snsb.delete(0, snsb.length());

        // construct slice path
        sb.append(RESOURCES_SET_DIR).append(basename);
//#ifdef __SUPPORT_GPSKA__
        if (!isGPSka) {
//#endif
            slice.appendPath(sb);
//#ifdef __SUPPORT_GPSKA__
        }
//#endif        
        sb.append(extension);

        // get full url
        final String url = sb.toString();

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("load slice image from " + url);
//#endif

        // read image
        try {

            // read image
            slice.setImage(scaleImage(buffered(JarLoader.class.getResourceAsStream(url))));

        } finally {

            // check for Palm - it resets :-(
            if (!cz.kruch.track.TrackingMIDlet.palm) {
                try {
                    bufferel();
                } catch (Exception e) {
                    // ignore
                }
            }

        }
    }

    /*
    * Atlas.Loader contract.
    */

    void loadIndex(Atlas atlas, String url, String baseUrl) throws IOException {
        throw new IllegalStateException("Not supported");
    }
}

