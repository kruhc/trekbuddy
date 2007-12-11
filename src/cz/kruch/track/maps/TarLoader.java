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

import api.file.File;

import java.io.InputStream;
import java.io.IOException;

import com.ice.tar.TarInputStream;
import com.ice.tar.TarEntry;

import javax.microedition.io.Connector;

import cz.kruch.track.ui.NavigationScreens;
import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.configuration.Config;

final class TarLoader extends Map.Loader implements Atlas.Loader {

    /*
     * Map.Loader contract.
     */

    private File file;
    private InputStream nativeIn;
    private TarInputStream tarIn;

    static {
        // hacks
        TarInputStream.setSafeSkip(cz.kruch.track.TrackingMIDlet.useSkipBug);
        TarInputStream.setSiemensIo(Config.siemensIo);
    }

    TarLoader() {
        super();
        this.isTar = true;
    }

/*
    protected Slice newSlice(String filename) throws InvalidMapException {
        return new TarSlice(filename);
    }
*/

    protected Slice newSlice(CharArrayTokenizer.Token token) throws InvalidMapException {
        return new TarSlice(token);
    }

    public void init(Map map, String url) throws IOException {
        super.init(map, url);

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("init map " + url);
//#endif

        // input stream
        InputStream in = null;

        try {
            file = File.open(Connector.open(map.getPath(), Connector.READ));
            tarIn = new TarInputStream(in = file.openInputStream());

            /*
            * test quality of File API
            */

            if (Map.useReset) {
                if (in.markSupported()) {
                    try {
                        in.mark((int) file.fileSize());
                        nativeIn = in;
                        Map.fileInputStreamResetable = 1;
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("input stream support marking, very good");
//#endif
                    } catch (Throwable t) {
                        /* OutOfMemoryError on S60 3rd */
                    }
                } else {
                    try {
                        in.reset(); // try reset
                        nativeIn = in;
                        Map.fileInputStreamResetable = 2;
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("input stream may be resetable");
//#endif
                    } catch (IOException e) {
                        Map.fileInputStreamResetable = -1;
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("input stream definitely not resetable");
//#endif
                    }
                }
            }

            /*
            * ~
            */

            // do not have calibration or slices
            if (map.getCalibration() == null || map.getSlices() == null) {

                // iterate over tar
                TarEntry entry = tarIn.getNextEntry();
                while (entry != null) {

                    // get entry name
                    CharArrayTokenizer.Token entryName = entry.getName();

                    // is it tile (in basedir only)
                    if (entryName.startsWith(SET_DIR_PREFIX) && entryName.endsWith(Slice.PNG_EXT)) { // slice

                        // no slices yet
                        if (map.getSlices() == null) {
                            ((TarSlice) addSlice(entryName)).setBlockOffset((int) (entry.getPosition() / TarInputStream.DEFAULT_RCDSIZE));
                        }

                    } else { // no, maybe calibration file

                        // no calibration yet
                        if (map.getCalibration() == null && entryName.indexOf(File.PATH_SEPCHAR) == -1) {
//#ifdef __LOG__
                            if (log.isEnabled()) log.debug("do not have calibration yet");
//#endif
                            // load calibration
                            map.setCalibration(Calibration.newInstance(tarIn, entryName.toString()));
                        }
                    }
                    entry = null; // gc hint
                    entry = tarIn.getNextEntry();
                }
            }
        } catch (Throwable t) {
            throwable = t;
        } finally {

            // close native stream when not reusable
            if (nativeIn == null) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("input stream not reusable -> close it");
//#endif
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }

            } else { // otherwise prepare for reuse

                // reset the stream
                nativeIn.reset();

                // and reuse it in buffered stream
                buffered.setInputStream(nativeIn);
            }
        }
    }

    public void dispose() throws IOException {
        // dispose tar stream
        if (tarIn != null) {
            tarIn.setInputStream(null);
            tarIn = null;
        }

        // close native stream
        if (nativeIn != null) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("closing native stream");
//#endif
            try {
                nativeIn.close();
            } catch (IOException e) {
                // ignore
            } finally {
                nativeIn = null; // gc hint
            }
        }

        // close file
        if (file != null) {
            try {
                file.close();
            } catch (IOException e) {
                // ignore
            } finally {
                file = null; // gc hint
            }
        }

        // parent
        super.dispose();
    }

    public void loadSlice(Slice slice) throws IOException {
        // input stream
        InputStream in = null;

        try {
            // slice entry stream offset
            final long streamOffset = ((TarSlice) slice).getBlockOffset() * TarInputStream.DEFAULT_RCDSIZE;

            // prepare buffered stream
            if (nativeIn != null) { // resetable stream
                if (streamOffset < tarIn.getStreamOffset()) {
                    nativeIn.reset();
                    buffered.setInputStream(nativeIn);
                    tarIn.setStreamOffset(0);
                }
            } else { // non-resetable stream
                buffered.setInputStream(in = file.openInputStream());
                tarIn.setStreamOffset(0);
            }

            // prepare tar stream
            tarIn.setInputStream(buffered);
            tarIn.rewind(streamOffset);

            // go to selected entry
            tarIn.getNextEntry();

            // read image
            slice.setImage(NavigationScreens.createImage(tarIn));

        } finally {

            // close native stream when not reusable
            if (nativeIn == null) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("input stream not reusable -> close it");
//#endif
                if (in != null) {

                    // clean buffered
                    buffered.setInputStream(null);

                    // close native non-reusable stream
                    try {
                        in.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }
    }

    /*
     * Atlas.Loader contract.
     */

    public void loadIndex(Atlas atlas, String url, String baseUrl) throws IOException {
        // vars
        File file = null;
        InputStream in = null;
        TarInputStream tar = null;

        try {
            // create stream
            file = File.open(Connector.open(url, Connector.READ));
            tar = new TarInputStream(in = file.openInputStream());

            // shared vars
            final char[] delims = new char[]{ File.PATH_SEPCHAR };
            final CharArrayTokenizer tokenizer = new CharArrayTokenizer();
            final StringBuffer sb = new StringBuffer(128);

            // iterate over archive
            TarEntry entry = tar.getNextEntry();
            while (entry != null) {

                // search for plain files
                if (!entry.isDirectory()) {

                    // tokenize
                    CharArrayTokenizer.Token entryName = entry.getName();
                    int indexOf = entryName.lastIndexOf('.');
                    if (indexOf > -1) {

                        // get layer and map name
                        String lName = null;
                        String mName = null;
                        tokenizer.init(entryName, delims, false);
                        if (tokenizer.hasMoreTokens()) {
                            lName = tokenizer.next().toString();
                            if (tokenizer.hasMoreTokens()) {
                                mName = tokenizer.next().toString();
                            }
                        }

                        // got layer and map name?
                        if (lName != null && mName != null) {

                            // construct url
                            sb.delete(0, sb.length());
                            String realUrl = sb.append(baseUrl).append(entryName).toString();
                            String fakeUrl = url.endsWith(".idx") ? realUrl : sb.delete(0, sb.length()).append(baseUrl).append(lName).append(File.PATH_SEPCHAR).append(mName).append(File.PATH_SEPCHAR).append(mName).append(Map.TAR_EXT).toString();

                            // load map calibration file
                            Calibration calibration = Calibration.newInstance(tar, fakeUrl, realUrl);

                            // found calibration file?
                            if (calibration != null) {
//#ifdef __LOG__
                                if (log.isEnabled()) log.debug("calibration loaded: " + calibration + "; layer = " + lName + "; mName = " + mName);
//#endif
                                // save calibration for given map - only one calibration per map allowed :-)
                                if (!atlas.getLayerCollection(atlas, lName).contains(mName)) {
                                    atlas.getLayerCollection(atlas, lName).put(mName, calibration);
                                }
                            }
                        }
                    }
                }
                entry = null; // gc hint
                entry = tar.getNextEntry();
            }

            // dispose vars
            tokenizer.dispose();

        } finally {

            // dispose tar stream
            if (tar != null) {
                tar.setInputStream(null);
            }

            // close input stream
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
            }

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
