// @LICENSE@

package cz.kruch.track.maps;

import api.file.File;
import api.io.BufferedOutputStream;
import api.io.BufferedInputStream;

import java.io.InputStream;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.DataInputStream;

import com.ice.tar.TarInputStream;
import com.ice.tar.TarEntry;

import cz.kruch.track.Resources;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.io.LineReader;
import cz.kruch.track.util.CharArrayTokenizer;

import javax.microedition.io.Connector;

/**
 * Packed map and atlas support.
 * <p>
 * Binary index (.tmc) format:
 * <pre>
 * signature:=int
 * version:=int
 * mapName:=String
 * mapCalibration:=String (can be empty)
 * mapCalibrationOffset:=int
 * mapWidth:=int
 * mapHeight:=int
 * tileWidth:=int
 * tileHeight:=int
 * numberOfTiles:=int
 * N*tileInfo:={
 *   x:=int
 *   y:=int
 *   offset:=int
 * }
 * </pre>
 * </p>
 *
 * @author kruhc@seznam.cz
 */
final class TarLoader extends Map.Loader /*implements Atlas.Loader*/ {

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
    }

    Slice newSlice() {
        return new TarSlice();
    }

    Slice getSlice(final int x, final int y) {
        final Slice slice = super.getSlice(x, y);
        final long xu = slice.getX();
        final long yu = slice.getY();
        final long xy = xu << 20 | yu;
        final long[] pointers = this.pointers;
        for (int i = numberOfPointers; --i >= 0; ) {
            final long pointer = pointers[i];
            if (((pointer & 0x000000ffffffffffL) ^ xy) == 0) {
                ((TarSlice) slice).setBlockOffset((int)(pointer >> 40));
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

/*
        try {
*/
            // open stream
//#ifdef __SYMBIAN__
            if (Config.useNativeService && Map.networkInputStreamAvailable) {
                try {
                    in = cz.kruch.track.device.SymbianService.openInputStream(map.getPath());
                    Map.networkInputStreamAvailable = true;
                } catch (Exception e) { // IOE or SE = service not running/available
                    Map.networkInputStreamAvailable = false;
                }
            }
//#endif
            /*
             * On Symbian when service is used, we don not need native file connection,
             * but for other platforms we do.
             * Well, for Android it should not be needed too, but let's not make
             * too many changes...
             */
            if (in == null) {
                nativeFile = File.open(map.getPath());
//#ifdef __ANDROID__
                in = new api.io.RandomAccessInputStream(map.getPath());
//#else
                in = nativeFile.openInputStream();
//#endif
            }

            // debug info
            Map.fileInputStreamClass = in.getClass().getName();

            /*
            * test quality of File API
            */

            if (Map.useReset) {
                if (in.markSupported()) {
                    try {
                        if (!Config.lowmemIo) {
                            /*
                             * Use int.MAX_VALUE to test for stupid implementation.
                             * Another dumb way of implementing it is aggregation up to the limit.
                             */
                            in.mark(Integer.MAX_VALUE/*(int) nativeFile.fileSize()*/);
//                            nativeIn = in;
                            Map.fileInputStreamResetable = 1;
                        } else {
                            try {
                                in.reset();
//                                nativeIn = in;
                                Map.fileInputStreamResetable = 3;
                            } catch (Throwable t) {
                                Map.fileInputStreamResetable = -3;
                            }
                        }
                    } catch (OutOfMemoryError e) {
                        /*
                         * Occurs when implementation is stupid and creates a buffer of mark size :D.
                         */
                        try {
                            in.reset();
//                            nativeIn = in;
                            Map.fileInputStreamResetable = 7;
                        } catch (Throwable t) {
                            Map.fileInputStreamResetable = -7;
                        }
                    } catch (Throwable t) {
                        Map.fileInputStreamResetable = -1;
                    }
                } else {
                       try {
                        in.reset(); // try reset
//                        nativeIn = in;
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
                loadTmc(map);
            }
            if (pointers == null) {
                loadTmi(map);
            }

            // open tar inputstream
            tarIn = new TarInputStream(in);

            // do not have calibration or slices yet
            if (getMapCalibration() == null || pointers == null) {

                // local ref
                final String path = map.getPath();

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
                    map.setCalibration(Calibration.newInstance(tarIn, path, calEntryName));

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
                            && getMapCalibration() == null) { // no calibration yet
//#ifdef __LOG__
                            if (log.isEnabled()) log.debug("do not have calibration yet");
//#endif
                            // try this root entry as a calibration
                            map.setCalibration(Calibration.newInstance(tarIn, path, entryName.toString()));

                            // remember
                            calEntryName = entryName.toString();
                            calBlockOffset = (int)(entry.getPosition() / TarInputStream.DEFAULT_RCDSIZE);

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

            // trim pointers
            if (pointers != null) {
                trimPointers();
            }

            // detach tar inputstream from file inputstream
            if (tarIn != null) {
                tarIn.setInputStream(null);
            }

            // always reusable
            nativeIn = in;

            // reusable
            if (nativeIn != null) {

                // get ready for reuse
                if (Map.fileInputStreamResetable > 0) {
                    nativeIn.reset();
                } else {
                    nativeIn.close();
                    nativeIn = null; // gc hint
                    nativeIn = nativeFile.openInputStream();
                }

                // preset
                buffered(nativeIn);
                tarIn.setStreamOffset(0);

            }
/*
        } finally {

            // close stream when not reused
            if (nativeIn == null) {
                try {
                    in.close();
                } catch (Exception e) { // NPE or IOE
                    // ignore
                }
            }

        }
*/
    }

    /** @Override */
    protected void onLoad() {
        // create binary index
        if (!isTmc) {
            saveTmc();
        }
    }

    void dispose(final boolean deep) throws IOException {
        // release pointers when deep
        if (deep) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("release pointers");
//#endif
            pointers = null;
        }
/*
        // detach buffered stream
        buffered(null);
*/
        // dispose tar inputstream
        if (tarIn != null) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("release tar input stream");
//#endif
            tarIn.setInputStream(null);
            tarIn = null; // gc hint
        }
        // close native inputstream
        if (nativeIn != null) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("closing native stream");
//#endif
            File.closeQuietly(nativeIn);
            nativeIn = null; // gc hint
        }

        // close file
        if (nativeFile != null) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("closing native file");
//#endif
            File.closeQuietly(nativeFile);
            nativeFile = null; // gc hint
        }

        // parent
        super.dispose(deep);
    }

    void loadSlice(final Slice slice) throws IOException {
        
        // slice entry block offset
        final long blockOffset = ((TarSlice) slice).getBlockOffset();

        // slice exists in the archive
        if (blockOffset >= 0) {

            // local ref
            final TarInputStream tarIn = this.tarIn;

            // stream offset
            final long streamOffset = blockOffset << 9 ; // * TarInputStream.DEFAULT_RCDSIZE;

/*
            try {
                // resetable stream
                if (nativeIn != null) {
*/
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("reuse stream");
//#endif
//#ifndef __CN1__
                    if (streamOffset < tarIn.getStreamOffset() || Config.siemensIo || Config.lowmemIo)
//#else
                    if (streamOffset < tarIn.getStreamOffset() || Config.lowmemIo)
//#endif
                    {
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("but reset it first");
//#endif
                        if (Map.fileInputStreamResetable > 0) {
                            nativeIn.reset();
                        } else {
                            nativeIn.close();
                            nativeIn = null; // gc hint
                            nativeIn = nativeFile.openInputStream();
                        }
                        buffered(nativeIn);
                        tarIn.setStreamOffset(0);
                    }
/*
                } else { // non-resetable stream
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("new stream");
//#endif
                    in = nativeFile.openInputStream();
                    buffered(in);
                    tarIn.setStreamOffset(0);
                }
*/

                // prepare tar inputstream
                tarIn.setInputStream(bufferef());
                tarIn.skip(streamOffset - tarIn.getStreamOffset());
                final TarEntry te = tarIn.getNextEntry();

                // read image
                slice.setImage(scaleImage(tarIn));

/*
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
*/

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
            // open stream
            file = File.open(url);
            tar = new TarInputStream(in = file.openInputStream());

            // local vars
            final char[] delims = { File.PATH_SEPCHAR };
            final CharArrayTokenizer tokenizer = new CharArrayTokenizer();
            final StringBuffer sb = new StringBuffer(32);

            // iterate over archive
            TarEntry entry = tar.getNextEntry();
            while (entry != null) {

                // entry name
                final CharArrayTokenizer.Token entryName = entry.getName();

                // HACK this could be map
                if (entryName.startsWith(SET_DIR_PREFIX)) {
                    throw new InvalidMapException("ATLAS->MAP");
                }

                // search for plain files
                if (!entry.isDirectory()) {

                    // tokenize
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

                            // idx is tar-made index for untarred atlases
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
            File.closeQuietly(in);

            // close native file
            File.closeQuietly(file);
        }
    }

    private void loadTmc(final Map map) throws IOException {
        // var
        File file = null;

        try {
            // check for .tmc existence
            file = getMetaFile(".tmc", Connector.READ);
            if (file.exists()) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("gonna use tmc");
//#endif
                // load binary meta
                DataInputStream dai = null;
                try {
                    // read meta
                    dai = new DataInputStream(new BufferedInputStream(file.openInputStream(), 4096));
                    dai.readInt(); // signature
                    dai.readInt(); // version
                    calEntryName = dai.readUTF();
                    calBlockOffset = dai.readInt();
                    dai.readUTF(); // map calibration
                    dai.readInt(); // map width
                    dai.readInt(); // map height
                    tileWidth = dai.readInt();
                    tileHeight = dai.readInt();
                    final int N = dai.readInt();
                    final long[] pointers = new long[N];
                    for (int i = 0; i < N; i++) {
                        final long x = dai.readInt();
                        final long y = dai.readInt();
                        final long block = dai.readInt();
                        pointers[i] = block << 40 | x << 20 | y;
                    }

                    // got pointers
                    this.numberOfPointers = N;
                    this.pointers = pointers;

                    // tmc used
                    isTmc = true;
                    
                } catch (Exception e) {
                    throw new InvalidMapException(Resources.getString(Resources.DESKTOP_MSG_PARSE_SET_FAILED), e);
                } finally {
                    // close stream
                    File.closeQuietly(dai);
                }
            }

        } finally {

            // close file
            File.closeQuietly(file);
        }
    }

    private void saveTmc() {
        // local ref
        final Map map = this.map;

        // var
        File file = null;
        DataOutputStream dao = null;

        try {
            // create .tmc
            file = getMetaFile(".tmc", Connector.READ_WRITE);
            file.create();

            // serialize meta
            dao = new DataOutputStream(new BufferedOutputStream(file.openOutputStream(), 4096, true));
            dao.writeInt(0xEB4D4910);
            dao.writeInt(1);
            dao.writeUTF(calEntryName);
            dao.writeInt(calBlockOffset);
            dao.writeUTF("");
            dao.writeInt(map.getWidth());
            dao.writeInt(map.getHeight());
            dao.writeInt(tileWidth);
            dao.writeInt(tileHeight);
            dao.writeInt(numberOfPointers);
            final int N = numberOfPointers;
            final long[] pointers = this.pointers;
            for (int i = 0; i < N; i++) {
                final long l = pointers[i];
                final int block = (int) (l >> 40);
                final int x = (int) ((l >> 20) & 0xfffff);
                final int y = (int) (l & 0xfffff);
                dao.writeInt(x);
                dao.writeInt(y);
                dao.writeInt(block);
            }

        } catch (Exception e) {

            // delete trash
            try {
                file.delete();
            } catch (Exception exc) { // NPE or IOE or SE
                // ignore
            }

        } finally {

            // close stream
            File.closeQuietly(dao);

            // close file
            File.closeQuietly(file);
        }
    }

    private void loadTmi(final Map map) throws IOException {
        // var
        File file = null;

        try {
            // check for .tmi existence
//            final String path = map.getPath();
//            final String tmiPath = path.substring(0, path.lastIndexOf('.')) + ".tmi";
//            file = File.open(tmiPath);
            file = getMetaFile(".tmi", Connector.READ);
            if (file.exists()) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("gonna use tmi");
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
                    reader = new LineReader(file.openInputStream(), 4096);
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
                    LineReader.closeQuietly(reader);
                }
            }

        } finally {

            // close file
            File.closeQuietly(file);
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
                } else if (initialCapacity > 4096) {
                    initialCapacity = 4096;
                }
            } else {
                initialCapacity = 2048;
            }

            // alloc initial array
            pointers = new long[initialCapacity];
            numberOfPointers = 0;
            increment = initialCapacity / 4;
        }

        // cook and add pointer
        pointers[numberOfPointers++] = (long) block << 40 | xy;
    }

    private void trimPointers() {
        if (pointers.length - numberOfPointers >= 512) { // ie. 4 kB memory
            final long[] array = new long[numberOfPointers];
            System.arraycopy(pointers, 0, array, 0, numberOfPointers);
            pointers = null; // gc hint
            pointers = array;
        }
    }
}
