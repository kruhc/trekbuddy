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

import cz.kruch.track.io.LineReader;
import cz.kruch.track.Resources;
import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.ui.NavigationScreens;

import java.io.IOException;
import java.io.InputStream;

final class JarLoader extends Map.Loader /*implements Atlas.Loader*/ {
    private static final String DEFAULT_OZI_MAP = "/resources/world.map";
    private static final String DEFAULT_GMI_MAP = "/resources/world.gmi";
    private static final String RESOURCES_SET_DIR = "/resources/set/";
    private static final String RESOURCES_SET_FILE = "/resources/world.set";

    JarLoader() {
        super();
    }

    void loadMeta(Map map) throws IOException {
        // input
        InputStream in = null;
        LineReader reader = null;

        try {
            // embedded map path
            String path;

            // look for Ozi calibration first
            in = JarLoader.class.getResourceAsStream(path = DEFAULT_OZI_MAP);
            if (in == null) {
                // look for GMI then
                in = JarLoader.class.getResourceAsStream(path = DEFAULT_GMI_MAP);
                if (in == null) {
                    // neither MapCalibrator calibration
                    throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_NO_CALIBRATION));
                }
            }
            try {
                map.setCalibration(Calibration.newInstance(in, path));
            } catch (InvalidMapException e) {
                throw e;
            } catch (IOException e) {
                throw new InvalidMapException("Resource '/resources/world.gmi': " + e.toString());
            }

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("in-jar calibration loaded");
//#endif

            // each line is a slice filename
            reader = new LineReader(JarLoader.class.getResourceAsStream(RESOURCES_SET_FILE));
/*
            String entry = reader.readLine(false);
            while (entry != null) {
                addSlice(entry);
                entry = null; // gc hint
                entry = reader.readLine(false);
            }
*/
            CharArrayTokenizer.Token token = reader.readToken(false);
            while (token != null) {
                addSlice(token);
                token = reader.readToken(false);
            }
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("in-jar .set processed");
//#endif
        } finally {

            // close stream
            if (in != null) {
                // check for Palm - it resets it :-(
                if (!cz.kruch.track.TrackingMIDlet.palm) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }

            // close reader (closes the stream)
            if (reader != null) {
                // check for Palm - it resets it :-(
                if (!cz.kruch.track.TrackingMIDlet.palm) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }
    }

    void loadSlice(Slice slice) throws IOException {
        // path sb
        final StringBuffer sb = cz.kruch.track.TrackingMIDlet.newInstance(32);

        // construct slice path
        sb.append(RESOURCES_SET_DIR).append(basename);
        if (!isGPSka) {
            slice.appendPath(sb);
        }
        sb.append(extension);

        // get slice path
        String slicePath = sb.toString();
        cz.kruch.track.TrackingMIDlet.releaseInstance(sb);

        // input stream
        InputStream in = null;

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("load slice image from " + slicePath);
//#endif

        // read image
        try {

            // read image
            slice.setImage(NavigationScreens.createImage(buffered.setInputStream(in = JarLoader.class.getResourceAsStream(slicePath))));

        } finally {

            // close stream
            if (in != null) {
                // check for Palm - it resets it :-(
                if (!cz.kruch.track.TrackingMIDlet.palm) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }

                // clean buffered
                buffered.setInputStream(null);
            }
        }
    }
}

