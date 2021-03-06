// @LICENSE@

package cz.kruch.track.maps;

import cz.kruch.track.Resources;
import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.io.LineReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Hashtable;

import api.file.File;

import javax.microedition.io.Connector;

/**
 * Loader for unpacked atlases.
 *
 * @author kruhc@seznam.cz
 */
final class DirLoader extends /*Map.*/Loader /*implements Atlas.Loader*/ {

    /*
     * Map.Loader contract.
     */

    private String dir;

    DirLoader() {
    }

    void init(final Map map, final String url) throws IOException {
        super.init(map, url);

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("init map " + url);
//#endif

        // detect base dir
        final int i = url.lastIndexOf(File.PATH_SEPCHAR);
        if (i == -1 || i + 1 == url.length()) {
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_INVALID_MAP_URL) + " '" + url + "'");
        }
        dir = url.substring(0, i + 1);

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("slices are in " + dir);
//#endif
    }

    void loadMeta() throws IOException {
        // local ref
        final Map map = this.map;
        final String path = map.getPath();

        // read calibration
        if (getMapCalibration() == null) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("do not have calibration yet");
//#endif

            // parse known calibration
            try {
                // path points to calibration file
                map.setCalibration(Calibration.newInstance(buffered(Connector.openInputStream(path)),
                                                           path, path));

                // check calibration
                if (getMapCalibration() == null) {
                    throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_UNKNOWN_CAL_FILE) + ": " + path);
                }

//#ifdef __SUPPORT_GPSKA__
                // GPSka hack
                if (isGPSka) {
                    basename = getMapCalibration().getImgname();
                    extension = EXT_PNG;
                    addSlice(null);
                }
//#endif
            } catch (InvalidMapException e) {
                throw e;
            } catch (IOException e) {
                throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_PARSE_CAL_FAILED) + ": " + path, e);
            } finally {

                // close stream
                bufferel();

            }
        }

        // prepare slices
        if (getSliceBasename() == null) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("do not have slices yet");
//#endif
            // try listing file first
            File file = null;
            try {
                // check for .set file
//                final String setFile = path.substring(0, path.lastIndexOf('.')) + ".set";
//                file = File.open(setFile);
                file = getMetaFile(".set", Connector.READ);
                if (file.exists()) {
                    LineReader reader = null;
                    try {
                        // each line is a slice filename
                        reader = new LineReader(buffered(file.openInputStream()));
                        CharArrayTokenizer.Token token;
                        while ((token = reader.readToken(false)) != null) {
                            addSlice(token);
                        }
                    } catch (InvalidMapException e) {
                        throw e;
                    } catch (IOException e) {
                        throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_PARSE_SET_FAILED), e);
                    } finally {
                        // close reader
                        LineReader.closeQuietly(reader);
                    }
                } else {
                    throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_NO_SET_FILE) + file.getPath());
                }
            } finally {
                // close file
                File.closeQuietly(file);
            }
        }
    }

    void loadSlice(final Slice slice) throws IOException {
        // path sb
        final StringBuffer sb = new StringBuffer(64);

        // construct slice path
        sb.append(dir).append(SET_DIR_PREFIX).append(basename);
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
            slice.setImage(scaleImage(buffered(Connector.openInputStream(url))));

        } finally {

            // close stream
            bufferel();

        }

    }

    /*
    * Atlas.Loader contract.
    */

    public void loadIndex(final Atlas atlas, final String url, final String baseUrl) throws IOException {
        // file
        File file = null;

        try {
            // open atlas dir
            file = File.open(baseUrl);

            // iterate over layers
            for (final Enumeration le = file.list(); le.hasMoreElements();) {
                final String layerEntry = (String) le.nextElement();
                if (File.isDir(layerEntry)) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("new layer: " + layerEntry);
//#endif

                    // get map collection for current layer
                    final Hashtable layerCollection = Atlas.getLayerCollection(atlas, layerEntry.substring(0, layerEntry.length() - 1));

                    // set file connection
                    file.setFileConnection(layerEntry);

                    // iterate over layer
                    for (final Enumeration me = file.list(); me.hasMoreElements();) {
                        final String mapEntry = (String) me.nextElement();
                        if (File.isDir(mapEntry)) {
//#ifdef __LOG__
                            if (log.isEnabled()) log.debug("new map? " + mapEntry);
//#endif
                            // set file connection
                            file.setFileConnection(mapEntry);

                            // iterate over map dir
                            for (final Enumeration ie = file.list(); ie.hasMoreElements();) {
                                final String fileEntry = (String) ie.nextElement();
                                if (!File.isDir(fileEntry)) {

                                    // is calibration
                                    if (Calibration.isCalibration(fileEntry)) {

                                        // create URL
                                        final String path = escape((new StringBuffer(64)).append(file.getURL()).append(fileEntry).toString());

                                        // load map calibration file
                                        InputStream in = null;
                                        Calibration calibration = null;
                                        try {
                                            calibration = Calibration.newInstance(in = Connector.openInputStream(path),
                                                                                  path, path);
                                        } finally {
                                            File.closeQuietly(in);
                                        }

                                        // found calibration
                                        if (calibration != null) {
//#ifdef __LOG__
                                            if (log.isEnabled()) log.debug("calibration loaded: " + calibration + "; layer = " + layerEntry + "; mName = " + mapEntry);
//#endif
                                            // save calibration for given map
                                            layerCollection.put(mapEntry.substring(0, mapEntry.length() - 1), calibration);

                                            /* only one calibration per map allowed :-) */
                                            break;
                                        }
                                    }
                                }
                            }

                            // back to layer dir
                            file.setFileConnection(File.PARENT_DIR);
                        }
                    }

                    // go back to atlas root
                    file.setFileConnection(File.PARENT_DIR);

                } else if (layerEntry.endsWith(".tba")) {

                    // load atlas descriptor
                    file.setFileConnection(layerEntry);
                    final InputStream in = file.openInputStream();
                    try {
                        loadDesc(atlas, in);
                    } finally {
                        File.closeQuietly(in);
                    }
                }
            }

        } finally {

            // close file
            File.closeQuietly(file);
        }
    }
}
