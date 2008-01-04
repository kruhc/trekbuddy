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

package cz.kruch.track.maps;

import cz.kruch.track.Resources;
import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.io.LineReader;
import cz.kruch.track.maps.io.FileInput;
import cz.kruch.track.ui.NavigationScreens;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;

import api.file.File;

import javax.microedition.io.Connector;

final class DirLoader extends Map.Loader implements Atlas.Loader {

    /*
     * Map.Loader contract.
     */

    private String dir;
    private FileInput input;

    DirLoader() {
        super();
        this.input = new FileInput(null);
    }

    public void init(Map map, String url) throws IOException {
        super.init(map, url);
        this.isGPSka = url.endsWith(Calibration.XML_EXT);

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("init map " + url);
//#endif

        int i = url.lastIndexOf(File.PATH_SEPCHAR);
        if (i == -1 || i + 1 == url.length()) {
            throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_INVALID_MAP_URL) + " '" + url + "'");
        }
        dir = url.substring(0, i + 1);

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("slices are in " + dir);
//#endif

        // read calibration
        if (getMapCalibration() == null) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("do not have calibration yet");
//#endif

            // helper loader
            FileInput fileInput = new FileInput(map.getPath());

            // parse known calibration
            try {
                i = map.getPath().lastIndexOf('.');
                if (i > -1) {

                    // path points to calibration file
                    map.setCalibration(Calibration.newInstance(buffered.setInputStream(fileInput._getInputStream()), map.getPath()));

                    // clear buffered
                    buffered.setInputStream(null);
                }

                // check calibration
                if (getMapCalibration() == null) {
                    throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_UNKNOWN_CAL_FILE) + ": " + map.getPath());
                }

                // GPSka hack
                if (isGPSka) {
                    basename = getMapCalibration().getImgname();
                    extension = ".png";
                    addSlice(null);
                }

            } catch (InvalidMapException e) {
                throw e;
            } catch (IOException e) {
                throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_PARSE_CAL_FAILED) + ": " + map.getPath(), e);
            } finally {
                // close helper loader
                fileInput.close();
            }
        }

        // prepare slices
        if (getMapSlices() == null) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("do not have slices yet");
//#endif
            // try listing file first
            File file = null;
            try {
                // check for .set file
                String setFile = map.getPath().substring(0, map.getPath().lastIndexOf('.')) + ".set";
                file = File.open(Connector.open(setFile, Connector.READ));
                if (file.exists()) {
                    // each line is a slice filename
                    LineReader reader = null;
                    CharArrayTokenizer.Token token;
                    try {
                        reader = new LineReader(buffered.setInputStream(file.openInputStream()));
                        token = reader.readToken(false);
                        while (token != null) {
                            addSlice(token);
                            token = null; // gc hint
                            token = reader.readToken(false);
                        }
                    } catch (InvalidMapException e) {
                        throw e;
                    } catch (IOException e) {
                        throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_PARSE_SET_FAILED), e);
                    } finally {
                        // close reader - closes the file stream (via buffered)
                        if (reader != null) {
                            try {
                                reader.close();
                            } catch (IOException e) {
                                // ignore
                            }
                        }
                        // detach buffered stream
                        buffered.setInputStream(null);
                    }
                } else {
                    throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_NO_SET_FILE) + setFile);
                }
            } finally {
                // close file
                if (file != null) {
                    file.close();
                }
            }
        }
    }

    public void loadSlice(Slice slice) throws IOException {
        // path sb
        final StringBuffer sb = cz.kruch.track.TrackingMIDlet.newInstance(32);

        // construct slice path
        sb.append(dir).append(SET_DIR_PREFIX).append(basename);
        if (!isGPSka) {
            slice.appendPath(sb);
        }
        sb.append(extension);

        // prepare path
        String slicePath = sb.toString();
        cz.kruch.track.TrackingMIDlet.releaseInstance(sb);

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("load slice image from " + slicePath);
//#endif

        // file input
        input.setUrl(slicePath);

        // read image
        try {

            // read image
            slice.setImage(NavigationScreens.createImage(buffered.setInputStream(input._getInputStream())));

        } finally {

            // gc hint
            buffered.setInputStream(null);

            // close input
            try {
                input.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /*
     * Atlas.Loader contract.
     */

    public void loadIndex(Atlas atlas, String url, String baseUrl) throws IOException {
        // file
        File file = null;

        try {
            // open atlas dir
            file = File.open(Connector.open(baseUrl, Connector.READ));

            // pooled path holder
            final StringBuffer sb = cz.kruch.track.TrackingMIDlet.newInstance(32);

            // iterate over layers
            for (Enumeration le = file.list(); le.hasMoreElements();) {
                String layerEntry = (String) le.nextElement();
                if (File.isDir(layerEntry)) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("new layer: " + layerEntry);
//#endif

                    // get map collection for current layer
                    Hashtable layerCollection = atlas.getLayerCollection(atlas, layerEntry.substring(0, layerEntry.length() - 1));

                    // set file connection
                    file.setFileConnection(layerEntry);

                    // iterate over layer
                    for (Enumeration me = file.list(); me.hasMoreElements();) {
                        String mapEntry = (String) me.nextElement();
                        if (File.isDir(mapEntry)) {
//#ifdef __LOG__
                            if (log.isEnabled()) log.debug("new map? " + mapEntry);
//#endif
                            // set file connection
                            file.setFileConnection(mapEntry);

                            // iterate over map dir
                            for (Enumeration ie = file.list(); ie.hasMoreElements();) {
                                String fileEntry = (String) ie.nextElement();

                                // is dir
                                if (File.isDir(fileEntry))
                                    continue;

                                // has ext
                                final int indexOf = fileEntry.lastIndexOf('.');
                                if (indexOf == -1) {
                                    continue;
                                }

                                // calibration
                                sb.delete(0, sb.length());
                                String path = sb.append(file.getURL()).append(fileEntry).toString();
                                FileInput fileInput = new FileInput(path);

                                // load map calibration file
                                Calibration calibration = Calibration.newInstance(fileInput._getInputStream(), path);

                                // close file input
                                fileInput.close();
                                fileInput = null; // gc hint

                                // found calibration
                                if (calibration != null) {
//#ifdef __LOG__
                                    if (log.isEnabled())
                                        log.debug("calibration loaded: " + calibration + "; layer = " + layerEntry + "; mName = " + mapEntry);
//#endif
                                    // save calibration for given map
                                    layerCollection.put(mapEntry.substring(0, mapEntry.length() - 1), calibration);

                                    /* only one calibration per map allowed :-) */
                                    break;
                                }
                            }

                            // back to layer dir
                            file.setFileConnection(File.PARENT_DIR);
                        }
                    }

                    // go back to atlas root
                    file.setFileConnection(File.PARENT_DIR);
                }
            }

            // release pooled object
            cz.kruch.track.TrackingMIDlet.releaseInstance(sb);

        } finally {
            // close file
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }
}
