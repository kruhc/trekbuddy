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

import cz.kruch.track.Resources;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.device.SymbianService;
import cz.kruch.track.io.LineReader;
import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.ui.NavigationScreens;

final class TarLoader extends Map.Loader implements Atlas.Loader {

    /*
     * Map.Loader contract.
     */

    private File nativeFile;
    private InputStream nativeIn;
    private TarInputStream tarIn;

    TarLoader() {
        super();
        this.isTar = true;
    }

    protected Slice newSlice(final CharArrayTokenizer.Token token) throws InvalidMapException {
        return new TarSlice(token);
    }

    void loadMeta(final Map map) throws IOException {
        // input stream
        InputStream in = null;

        try {
            // open native file
            nativeFile = File.open(map.getPath());
            if (!nativeFile.exists()) {
                throw new IOException("File " + map.getPath() + " not found");
            }

            // open stream
            if (cz.kruch.track.TrackingMIDlet.symbian) {
                in = SymbianService.openInputStream(map.getPath());
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("remote stream? " + in);
//#endif
            }
            if (in == null) {
                in = nativeFile.openInputStream();
            }

            /*
            * test quality of File API
            */

            if (Map.useReset) {
                if (in.markSupported()) {
                    try {
                        in.mark((int) nativeFile.fileSize());
                        nativeIn = in;
                        Map.fileInputStreamResetable = 1;
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("input stream support marking, very good");
//#endif
                    } catch (Throwable t) {
                        /* OutOfMemoryError on S60 3rd without helper service */
                    }
                } else {
                    try {
                        in.reset(); // try reset
                        nativeIn = in;
                        Map.fileInputStreamResetable = 2;
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("input stream may be resetable");
//#endif
                    } catch (Throwable t) {
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

            // try tar metainfo file first
            if (getMapSlices() == null) {
                loadTmi(map);
            }

            // open tar input
            tarIn = new TarInputStream(in);

            // do not have calibration or slices yet
            if (getMapCalibration() == null || getMapSlices() == null) {

                // changes during loading - we need to remember initial state
                final boolean gotSlices = getMapSlices() != null;
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("calibration or slices missing; " + gotSlices);
//#endif

                // iterate over tar
                TarEntry entry = tarIn.getNextEntry();
                while (entry != null) {

                    // get entry name
                    final CharArrayTokenizer.Token entryName = entry.getName();

                    if (!gotSlices && entryName.startsWith(SET_DIR_PREFIX)
                        && (entryName.endsWith(".png") || entryName.endsWith(".jpg"))) { // slice

                        // add slice
                        ((TarSlice) addSlice(entryName)).setStreamOffset((int) (entry.getPosition()));

                    } else if (entryName.indexOf(File.PATH_SEPCHAR) == -1
                        && getMapCalibration() == null) { // no calibration nativeFile yet
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("do not have calibration yet");
//#endif
                        // try this root entry as a calibration
                        map.setCalibration(Calibration.newInstance(tarIn, entryName.toString()));

                        // skip the rest if we already have them
                        if (gotSlices && getMapCalibration() != null) {
//#ifdef __LOG__
                            if (log.isEnabled()) log.debug("calibration is all we need");
//#endif
                            break;
                        }
                    }

                    // next
                    entry = tarIn.getNextEntry();
                }
            }
            
        } finally {

            // reusable
            if (nativeIn != null) {

                // get ready for reuse
                nativeIn.reset();
                buffered.setInputStream(nativeIn);
                tarIn.setStreamOffset(0);

            } else {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("input stream not reusable -> close it");
//#endif
                // close native stream
                try {
                    in.close();
                } catch (Exception e) { // NPE or IOE
                    // ignore
                }
            }

            // detach tar stream
            if (tarIn != null) {
                tarIn.setInputStream(null);
            }
        }
    }

    private void loadTmi(final Map map) throws IOException {
        // var
        File file = null;

        try {
            // check for .ser nativeFile
            final String tmiPath = map.getPath().substring(0, map.getPath().lastIndexOf('.')) + ".tmi";
            file = File.open(tmiPath);
            if (file.exists()) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("gonna use " + tmiPath);
//#endif
                // each line is a slice filename
                LineReader reader = null;
                final CharArrayTokenizer tokenizer = new CharArrayTokenizer();
                final char[] delims = { ':' };
                try {
                    // read entry meta info
                    reader = new LineReader(buffered.setInputStream(file.openInputStream()));
                    CharArrayTokenizer.Token token = reader.readToken(false);
                    while (token != null) {

                        // skip leading "block  ", if any...
                        token.skipNonDigits();

                        // init tokenizer
                        tokenizer.init(token, delims, false);

                        // get block offset
                        final int block = tokenizer.nextInt();

                        // move to slice name
                        token = tokenizer.next();

                        // trim
                        token.trim();
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("tmi line filename: " + token.toString());
//#endif

                        // add slice
                        if (token.startsWith("set/") && (token.endsWith(".png") || token.endsWith(".jpg"))) {
                            ((TarSlice) addSlice(token)).setStreamOffset(block * TarInputStream.DEFAULT_RCDSIZE);
                        }

                        // next line
                        token = null; // gc hint
                        token = reader.readToken(false);
                    }
                } catch (InvalidMapException e) {
                    throw e;
                } catch (IOException e) {
                    throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_PARSE_SET_FAILED), e);
                } finally {

                    // close reader
                    try {
                        reader.close();
                    } catch (Exception e) { // NPE or IOE
                        // ignore
                    }
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("tmi utilized: " + tmiPath);
//#endif
                }
            }

        } finally {

            // close file
            try {
                file.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
        }
    }

    public void dispose() throws IOException {
        // close native stream
        if (nativeIn != null) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("closing native stream");
//#endif
            try {
                nativeIn.close();
            } catch (IOException e) {
                // ignore
            }
            nativeIn = null; // gc hint
        }

        // close nativeFile
        if (nativeFile != null) {
            try {
                nativeFile.close();
            } catch (IOException e) {
                // ignore
            }
            nativeFile = null; // gc hint
        }

        // dispose tar stream
        if (tarIn != null) {
            tarIn.setInputStream(null);
            tarIn = null; // gc hint
        }

        // dispose buffered stream
        if (buffered != null) {
            buffered.setInputStream(null);
        }

        // parent
        super.dispose();
    }

    public void loadSlice(final Slice slice) throws IOException {
        // input stream
        InputStream in = null;

        // slice entry stream offset
        final int streamOffset = ((TarSlice) slice).getStreamOffset();

        try {
            // local ref
            final TarInputStream tarIn = this.tarIn;

            // resetable stream
            if (nativeIn != null) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("reuse stream");
//#endif
                if (streamOffset < tarIn.getStreamOffset() || Config.siemensIo) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("but reset it first");
//#endif
                    nativeIn.reset();
                    buffered.setInputStream(nativeIn);
                    tarIn.setStreamOffset(0);
                }
            } else { // non-resetable stream
                if (cz.kruch.track.TrackingMIDlet.symbian) {
                    in = SymbianService.openInputStream(nativeFile.getURL());
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("remote stream? " + in);
//#endif
                }
                if (in == null) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("open new input stream");
//#endif
                    in = nativeFile.openInputStream();
                }
                buffered.setInputStream(in);
                tarIn.setStreamOffset(0);
            }

            // prepare tar stream
            tarIn.setInputStream(buffered);
            tarIn.skip(streamOffset - tarIn.getStreamOffset());
            tarIn.getNextEntry();

            // read image
            slice.setImage(NavigationScreens.createImage(tarIn));

        } finally {

            // close native stream when not reusable
            if (in != null) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("input stream not reusable -> close it");
//#endif
                // close buffered
                try {
                    buffered.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    /*
     * Atlas.Loader contract.
     */

    public void loadIndex(final Atlas atlas, final String url, final String baseUrl) throws IOException {
        // vars
        File file = null;
        InputStream in = null;
        TarInputStream tar = null;

        try {
            // create stream
            file = File.open(url);
            tar = new TarInputStream(in = file.openInputStream());

            // shared vars
            final char[] delims = { File.PATH_SEPCHAR };
            final CharArrayTokenizer tokenizer = new CharArrayTokenizer();
            final StringBuffer sb = new StringBuffer(32);

            // iterate over archive
            TarEntry entry = tar.getNextEntry();
            while (entry != null) {

                // search for plain files
                if (!entry.isDirectory()) {

                    // tokenize
                    final CharArrayTokenizer.Token entryName = entry.getName();
                    final int indexOf = entryName.lastIndexOf('.');
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

                            // prepare sb
                            sb.delete(0, sb.length()).append(baseUrl);

                            // construct URLs
                            final String realUrl = entryName.append(sb).toString();
                            final String fakeUrl;
                            if (url.endsWith(".idx")) {
                                fakeUrl = realUrl;
                            } else {
                                fakeUrl = sb.delete(0, sb.length()).append(baseUrl).append(lName).append(File.PATH_SEPCHAR).append(mName).append(File.PATH_SEPCHAR).append(mName).append(".tar").toString();
                            }

                            // load map calibration file
                            final Calibration calibration = Calibration.newInstance(tar, fakeUrl, realUrl);

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

                // next entry
                entry = null; // gc hint
                entry = tar.getNextEntry();
            }

        } finally {

            // close input stream
            try {
                in.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }

            // close native file
            try {
                file.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }

            // dispose tar stream
            if (tar != null) {
                tar.setInputStream(null);
            }
        }
    }
}
