// @LICENSE@

package cz.kruch.track.maps;

import cz.kruch.track.io.LineReader;
import cz.kruch.track.Resources;
import cz.kruch.track.util.CharArrayTokenizer;

import java.io.IOException;
import java.io.InputStream;

import api.file.File;

final class JarLoader extends /*Map.*/Loader /*implements Atlas.Loader*/ {
    private static final String DEFAULT_OZI_MAP = "/resources/world.map";
    private static final String RESOURCES_SET_DIR = "/resources/set/";
    private static final String RESOURCES_SET_FILE = "/resources/world.set";

    JarLoader() {
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
            in = getResourceAsStream(path = DEFAULT_OZI_MAP);
            if (in == null) {
                // we are screwed
                throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_LOAD_MAP_FAILED));
            }

            // process calibration
            map.setCalibration(Calibration.newInstance(in, path, path));

            // parse tileset file
            reader = new LineReader(getResourceAsStream(RESOURCES_SET_FILE));
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
                File.closeQuietly(in);
                LineReader.closeQuietly(reader);
            }
        }
    }

    void loadSlice(final Slice slice) throws IOException {
        // path sb
        final StringBuffer sb = new StringBuffer(64);

        // construct slice path
        sb.append(RESOURCES_SET_DIR).append(basename);
//#ifdef __SUPPORT_GPSKA__
        if (!isGPSka) {
//#endif
            slice.appendPath(sb);
//#ifdef __SUPPORT_GPSKA__
        }
//#endif        
//#ifndef __CN1__
        sb.append(extension);
//#else
        sb.append(extension, 0, extension.length);
//#endif

        // get full url
        final String url = sb.toString();

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("load slice image from " + url);
//#endif

        // read image
        try {

            // read image
            slice.setImage(scaleImage(buffered(getResourceAsStream(url))));

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

    private static InputStream getResourceAsStream(final String resource) {
//#ifndef __CN1__
        return JarLoader.class.getResourceAsStream(resource);
//#else
        return com.codename1.ui.FriendlyAccess.getResourceAsStream(resource);
//#endif
    }
}

