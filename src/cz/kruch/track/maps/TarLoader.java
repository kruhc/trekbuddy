// @LICENSE@

package cz.kruch.track.maps;

import api.file.File;

import java.io.InputStream;
import java.io.IOException;

import com.ice.tar.TarInputStream;
import com.ice.tar.TarEntry;

import cz.kruch.track.Resources;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.io.LineReader;
import cz.kruch.track.ui.NavigationScreens;
import cz.kruch.track.util.CharArrayTokenizer;

import javax.microedition.lcdui.Image;

/**
 * Packed map and atlas support.
 *
 * @author kruhc@seznam.cz
 */
final class TarLoader extends Map.Loader implements Atlas.Loader {

    /*
     * Map.Loader contract.
     */

    private File nativeFile;
    private InputStream nativeIn;
    private TarInputStream tarIn;

    private long[] pointers;
    private int numberOfPointers;
    private int hintTmiFileSize, increment, calBlockOffset;
    private String calEntryName;

    TarLoader() {
        this.isTar = true;
        ((api.io.BufferedInputStream) bufferef()).setAutofill(false);
    }

    Slice newSlice() {
        return new TarSlice();
    }

    Slice getSlice(int x, int y) {
        final Slice slice = super.getSlice(x, y);
        final long xy = slice.getXyLong();
        final long[] pointers = this.pointers;
        for (int i = numberOfPointers; --i >= 0; ) {
            final long pointer = pointers[i];
            if (((pointer & 0x000000ffffffffffL) ^ xy) == 0) {
                ((TarSlice) slice).setStreamOffset((int)(pointer >> 40));
                break;
            }
        }
        return slice;
    }

    boolean hasSlices() {
        return pointers != null;
    }

    void loadMeta() throws IOException {
        // local ref
        final Map map = this.map;

        // input stream
        InputStream in = null;

        try {
            // open native file
            nativeFile = File.open(map.getPath());

            // do file existence check
            if (!nativeFile.exists()) {
                throw new IOException("File " + map.getPath() + " not found");
            }

            // open stream
//#ifdef __SYMBIAN__
			if (Config.useNativeService && Map.networkInputStreamAvailable) {
                try {
                    in = cz.kruch.track.device.SymbianService.openInputStream(map.getPath());
                    Map.networkInputStreamAvailable = true;
                } catch (Exception e) { // IOE or SE
                    // service not running/available
                    Map.networkInputStreamAvailable = false;
                }
            }
//#endif
            if (in == null) {
                in = nativeFile.openInputStream();
            }

            /*
            * test quality of File API
            */

            if (Map.useReset) {
                if (in.markSupported()) {
                    try {
                        if (!Config.lowmemIo) {
                            in.mark((int) nativeFile.fileSize());
                        }
                        nativeIn = in;
                        Map.fileInputStreamResetable = 1;
                    } catch (OutOfMemoryError e) {
                        /*
                         * OutOfMemoryError on S60. Let's try smaller buffer.
                         */
                        // TODO
                        Map.fileInputStreamResetable = -7;
                    } catch (Throwable t) {
                        Map.fileInputStreamResetable = -1;
                    }
                } else {
                    try {
                        in.reset(); // try reset
                        nativeIn = in;
                        Map.fileInputStreamResetable = 2;
                    } catch (Throwable t) {
                        Map.fileInputStreamResetable = -2;
                    }
                }
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("input stream resetable? " + Map.fileInputStreamResetable);
//#endif
            }

            /*
            * ~
            */

            // try tar metainfo file first
            if (pointers == null) {
                loadTmi(map);
            }

            // open tar input
            tarIn = new TarInputStream(in);

            // do not have calibration or slices yet
            if (getMapCalibration() == null || pointers == null) {

                // changes during loading - we need to remember initial state
                final boolean gotSlices = pointers != null;
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("calibration or slices missing; " + gotSlices);
//#endif

                // need only calibration?
                if (gotSlices && calEntryName != null) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("calibration " + calEntryName + " is at block " + calBlockOffset);
//#endif

                    // skip to calibration
                    if (calBlockOffset > 0) {
                        tarIn.skip(calBlockOffset * TarInputStream.DEFAULT_RCDSIZE);
                    }
                    tarIn.getNextEntry();

                    // try this root entry as a calibration
                    map.setCalibration(Calibration.newInstance(tarIn, map.getPath(), calEntryName));

                } else { // do a full scan

                    // iterate over tar
                    TarEntry entry = tarIn.getNextEntry();
                    while (entry != null) {

                        // get entry name
                        final CharArrayTokenizer.Token entryName = entry.getName();

                        if (!gotSlices && entryName.startsWith(SET_DIR_PREFIX)
                            && (entryName.endsWith(EXT_PNG) || entryName.endsWith(EXT_JPG))) { // slice

                            // add slice
                            registerSlice(entryName, (int) (entry.getPosition() / TarInputStream.DEFAULT_RCDSIZE));

                        } else if (entryName.indexOf(File.PATH_SEPCHAR) == -1
                            && getMapCalibration() == null) { // no calibration nativeFile yet
//#ifdef __LOG__
                            if (log.isEnabled()) log.debug("do not have calibration yet");
//#endif
                            // try this root entry as a calibration
                            map.setCalibration(Calibration.newInstance(tarIn, map.getPath(), entryName.toString()));

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
            }
            
        } finally {

            // detach tar stream
            if (tarIn != null) {
                tarIn.setInputStream(null);
            }

            // reusable
            if (nativeIn != null) {

                // get ready for reuse
                if (!Config.lowmemIo) {
                    nativeIn.reset();
                } else {
                    nativeIn.close();
                    nativeIn = null; // gc hint
                    nativeIn = nativeFile.openInputStream();
                }
                buffered(nativeIn);
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
        }
    }

    void dispose(final boolean deep) throws IOException {
        // detach buffered stream
        buffered(null);

        // release pointers when deep
        if (deep) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("release pointers");
//#endif
            pointers = null;
        }

        // dispose tar stream
        if (tarIn != null) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("release tar input stream");
//#endif
            tarIn.setInputStream(null);
            tarIn = null; // gc hint
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

        // parent
        super.dispose(deep);
    }

    void loadSlice(final Slice slice) throws IOException {
        // slice entry stream offset
        final int streamOffset = ((TarSlice) slice).getBlockOffset() * TarInputStream.DEFAULT_RCDSIZE;

        // slice exists in the archive
        if (streamOffset >= 0) {

            // input stream
            InputStream in = null;

            // local ref
            final TarInputStream tarIn = this.tarIn;

            try {
                // resetable stream
                if (nativeIn != null) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("reuse stream");
//#endif
                    if (streamOffset < tarIn.getStreamOffset() || Config.siemensIo) {
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("but reset it first");
//#endif
                        if (!Config.lowmemIo) {
                            nativeIn.reset();
                        } else {
                            nativeIn.close();
                            nativeIn = null; // gc hint
                            nativeIn = nativeFile.openInputStream();
                        }
                        buffered(nativeIn);
                        tarIn.setStreamOffset(0);
                    }
                } else { // non-resetable stream
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("new stream");
//#endif
                    in = nativeFile.openInputStream();
                    buffered(in);
                    tarIn.setStreamOffset(0);
                }

                // prepare tar stream
                tarIn.setInputStream(bufferef());
                tarIn.skip(streamOffset - tarIn.getStreamOffset());
                tarIn.getNextEntry();

                // read image
                slice.setImage(Image.createImage(tarIn));

            } finally {

                // close native stream when not reusable
                if (in != null) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("input stream not reusable -> close it");
//#endif
                    // close buffered
                    try {
                        bufferel();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
            
        } else { // incomplete archive handling

            // use empty image
            slice.setImage(Slice.NO_IMAGE);

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
                            lName = tokenizer.next2().toString();
                            if (tokenizer.hasMoreTokens()) {
                                mName = tokenizer.next2().toString();
                            }
                        }

                        // got layer and map name?
                        if (lName != null && mName != null) {

                            // prepare sb
                            sb.delete(0, sb.length()).append(baseUrl);

                            // construct URLs
                            final String realUrl = entryName.append(sb).toString();
                            final String fakeUrl;
                            if (url.endsWith(".idx") || url.endsWith(".IDX")) {
                                fakeUrl = escape(realUrl);
                            } else {
                                fakeUrl = escape(sb.delete(0, sb.length()).append(baseUrl).append(lName).append(File.PATH_SEPCHAR).append(mName).append(File.PATH_SEPCHAR).append(mName).append(".tar").toString());
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

            // dispose tar stream
            if (tar != null) {
                tar.setInputStream(null);
            }

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
        }
    }

    private void loadTmi(final Map map) throws IOException {
        // var
        File file = null;

        try {
            // check for .tmi existence
            final String tmiPath = map.getPath().substring(0, map.getPath().lastIndexOf('.')) + ".tmi";
            file = File.open(tmiPath);
            if (file.exists()) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("gonna use " + tmiPath);
//#endif
                // helper member
                hintTmiFileSize = (int) file.fileSize();

                // each line is a slice filename
                LineReader reader = null;
                CharArrayTokenizer.Token token = null;
                final CharArrayTokenizer tokenizer = new CharArrayTokenizer();
                final char[] delims = { ':' };
                try {
                    // read entry meta info
                    reader = new LineReader(file.openInputStream(), 8192);
                    token = reader.readToken(false);
                    while (token != null) {

                        // skip leading "block  ", if any
                        token.skipNonDigits();

                        // init tokenizer
                        tokenizer.init(token, delims, false);

                        // get block offset
                        final int block = tokenizer.nextInt2();

                        // move to slice name
                        token = tokenizer.next2();

                        // trim
                        token.trim();
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("tmi line filename: " + token.toString());
//#endif

                        // add slice
                        if (token.startsWith(SET_DIR_PREFIX) && (token.endsWith(EXT_PNG) || token.endsWith(EXT_JPG))) {

                            // entry is slice
                            registerSlice(token, block);

                        } else if (calEntryName == null && Calibration.isCalibration(token)) {

                            // entry is map calibration
                            calEntryName = token.toString();
                            calBlockOffset = block;
//#ifdef __LOG__
                            if (log.isEnabled()) log.debug("found calibration " + calEntryName + " at block " + calBlockOffset);
//#endif
                        }

                        // next line
                        token = null; // gc hint
                        token = reader.readToken(false);
                    }

                    // tmi used
                    isTmi = true;
                    
                } catch (InvalidMapException e) {
                    throw e;
                } catch (Exception e) {
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

    private void registerSlice(final CharArrayTokenizer.Token token,
                               final int block) throws InvalidMapException {
        // add slice
        final long xy = addSlice(token);

        // already got some?
        if (pointers != null) {

            // ensure array capacity
            if (numberOfPointers == pointers.length) {

                // allocate new array
                final long[] array = new long[numberOfPointers + increment];

                // copy existing pointers
                System.arraycopy(pointers, 0, array, 0, numberOfPointers);

                // use new array
                pointers = null; // gc hint
                pointers = array;
            }

        } else { // no, first slice being added

            // suggest pointers capacity
            int initialCapacity;
            if (hintTmiFileSize > 0 && token.length > 0) {
                initialCapacity = (hintTmiFileSize / token.length) / 2;
                if (initialCapacity < 64) {
                    initialCapacity = 64;
                }
            } else {
                initialCapacity = 4096;
            }

            // alloc initial array
            pointers = new long[initialCapacity];
            numberOfPointers = 0;
            increment = initialCapacity / 4;
        }

        // cook and add pointer
        pointers[numberOfPointers++] = (long) block << 40 | xy;
    }
}
