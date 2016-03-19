// @LICENSE@

package cz.kruch.track.maps;

import api.file.File;
import api.io.BufferedOutputStream;
import api.io.BufferedInputStream;

import java.io.InputStream;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.util.Enumeration;

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
final class TarLoader extends /*Map.*/Loader /*implements Atlas.Loader*/ implements api.util.Comparator {

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

    void sortSlices(java.util.Vector list) {
        cz.kruch.track.ui.FileBrowser.sort(((cz.kruch.track.util.NakedVector) list).getData(), this, 0, list.size() - 1);
    }

    void loadMeta() throws IOException {
        // local ref
        final Map map = this.map;
        final String path = map.getPath();

/*
        try {
*/
            // input stream
            final InputStream in = getInputStream(path);

            // debug info
//#ifndef __CN1__
            Map.fileInputStreamClass = in.getClass().getName();
//#else
            Map.fileInputStreamClass = in.toString(); // see InputStreamWrapper.cs
//#endif

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
            if (calEntryName == null || pointers == null) {
                loadTmc(map);
            }
            if (calEntryName == null || pointers == null) {
                loadTmi(map);
            }

            // open tar inputstream
            tarIn = new TarInputStream(in);

            /*
             * NOTE: map from atlas has calibration but not pointers nor calibration entry info
             */

            // have no meta yet
            if (calEntryName == null || pointers == null) {

                // iterate over tar
                TarEntry entry = tarIn.getNextEntry();
                while (entry != null) {

                    // get entry name
                    final CharArrayTokenizer.Token entryName = entry.getName();

                    // slice
                    if (entryName.startsWith(SET_DIR_PREFIX)
                        && (entryName.endsWith(EXT_PNG) || entryName.endsWith(EXT_JPG))) {

                        // add slice
                        registerSlice(entryName, (int) (entry.getPosition() / TarInputStream.DEFAULT_RCDSIZE));

                    } else if (calEntryName == null
                        && Calibration.isCalibration(entryName)) { // calibration
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("do not have calibration yet");
//#endif
                        // try entry as a calibration
                        if (getMapCalibration() == null) {
                            map.setCalibration(Calibration.newInstance(tarIn, path, entryName.toString()));
                        }

                        // remember
                        calEntryName = entryName.toString();
                        calBlockOffset = (int)(entry.getPosition() / TarInputStream.DEFAULT_RCDSIZE);
                    }

                    // next
                    entry = tarIn.getNextEntry();
                }
            }

            // load calibration for single map
            if (getMapCalibration() == null) {

                // skip to calibration
                if (calBlockOffset > 0) {
                    tarIn.skip(calBlockOffset * TarInputStream.DEFAULT_RCDSIZE);
                }
                tarIn.getNextEntry();

                // try entry as a calibration
                map.setCalibration(Calibration.newInstance(tarIn, path, calEntryName));
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
    void loadCalibration() throws IOException {
        // local ref
        final String path = map.getPath();

/*
        try {
*/
            // input stream
            final InputStream in = getInputStream(path);

            // open tar inputstream
            tarIn = new TarInputStream(in);

            // iterate over tar
            TarEntry entry = tarIn.getNextEntry();
            while (entry != null) {

                // get entry name
                final CharArrayTokenizer.Token entryName = entry.getName();

                if (Calibration.isCalibration(entryName)) { // calibration
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("do not have calibration yet");
//#endif
                    // we found calibration
                    map.setCalibration(Calibration.newInstance(tarIn, path, entryName.toString()));

                    break;
                }

                // next
                entry = tarIn.getNextEntry();
            }

            // for disposal to work as usual
            nativeIn = in;


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

    private InputStream getInputStream(String path) throws IOException {
        // result
        InputStream in = null;

//#ifdef __SYMBIAN__

        // open network stream
        if (Config.useNativeService && Map.networkInputStreamAvailable) {
            try {
                in = cz.kruch.track.device.SymbianService.openInputStream(path);
                Map.networkInputStreamAvailable = true;
            } catch (Exception e) { // IOE or SE = service not running/available
                Map.networkInputStreamAvailable = false;
                Map.networkInputStreamError = e.toString();
            }
        }

//#endif

        /*
         * On Symbian when service is used, we do not need native file connection,
         * but for other platforms we do. Except Android.
         */
//#ifdef __ANDROID__
        in = new api.io.RandomAccessInputStream(path);
//#else
        if (in == null) {
            nativeFile = File.open(path);
            in = nativeFile.openInputStream();
        }
//#endif
//#ifdef __RIM50__
        if (in instanceof net.rim.device.api.io.Seekable) {
            in = new api.io.SeekableInputStream(in);
        }
//#endif

        return in;
    }

    private void loadTmc(final Map map) throws IOException {
        // var
        File file = null;

        // reset flag
        isTmc = false;

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

        // reset flag
        isTmi = false;

        try {
            // check for .tmi existence
            file = getMetaFile(".tmi", Connector.READ);
            if (file.exists()) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("gonna use tmi");
//#endif
//#ifndef __CN1__
                // size guess
                hintTmiFileSize = (int) file.fileSize();
//#endif

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
                        if (token.startsWith(SET_DIR_PREFIX)
                                && (token.endsWith(EXT_PNG) || token.endsWith(EXT_JPG))) {

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

    /*
     * Comparator contract.
     */

    public int compare(Object o1, Object o2) {
        final TarSlice ts1 = (TarSlice) o1;
        final TarSlice ts2 = (TarSlice) o2;
        return ts1.getBlockOffset() - ts2.getBlockOffset();
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

            // iterate over archive
            TarEntry entry = tar.getNextEntry();
            while (entry != null) {

                // entry name
                final CharArrayTokenizer.Token entryName = entry.getName();
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("entry: " + entryName.toString());
//#endif

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

                            // construct URLs
                            final String realUrl = entryName.toString();
                            final String fakeUrl;
                            final StringBuffer sb = new StringBuffer(32);

                            // idx is tar-made index for untarred atlases
                            if (url.endsWith(".idx") || url.endsWith(".IDX")) {
                                fakeUrl = escape(sb.append(baseUrl).append(realUrl).toString());
                            } else {
                                fakeUrl = escape(sb.append(baseUrl).append(lName).append(File.PATH_SEPCHAR).append(mName).append(File.PATH_SEPCHAR).append(mName).append(".tar").toString());
                            }

                            // load map calibration file
                            final Calibration calibration = Calibration.newInstance(tar, fakeUrl, realUrl);

                            // found calibration file?
                            if (calibration != null) {
//#ifdef __LOG__
                                if (log.isEnabled()) log.debug("calibration loaded: " + calibration + "; layer = " + lName + "; mName = " + mName);
//#endif
                                // save calibration for given map - only one calibration per map allowed :-)
                                if (!Atlas.getLayerCollection(atlas, lName).contains(mName)) {
                                    Atlas.getLayerCollection(atlas, lName).put(mName, calibration);
                                }
                            }

                        } else if (entryName.endsWith(EXT_TBA)) {

                            // loade atlas descriptor
                            loadDesc(atlas, tar);

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

        // since 1.28
        if (atlas.size() == 0) {
            createIndex(atlas, baseUrl);
        }
    }

    private void createIndex(final Atlas atlas, final String baseUrl) throws IOException {
        // file
        File file = null;

        try {
            // open atlas dir
            file = File.open(baseUrl);

            // iterate over layers
            for (final Enumeration le = file.list(); le.hasMoreElements();) {
                final String lEntry = (String) le.nextElement();
                if (File.isDir(lEntry)) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("found layer: " + lEntry);
//#endif
                    // get layer name
                    final String lName = lEntry.substring(0, lEntry.lastIndexOf('/'));

                    // set file connection
                    file.setFileConnection(lEntry);

                    // iterate over layer
                    for (final Enumeration me = file.list(); me.hasMoreElements();) {
                        final String mEntry = (String) me.nextElement();
                        if (File.isDir(mEntry)) {
//#ifdef __LOG__
                            if (log.isEnabled()) log.debug("found map: " + mEntry);
//#endif
                            // get map name
                            final String mName = mEntry.substring(0, mEntry.lastIndexOf('/'));

                            // create URL
                            final String fakeUrl = escape((new StringBuffer(64)).append(file.getURL()).append(mEntry).append(mName).append(".tar").toString());

                            // load map calibration file
                            final Calibration calibration = readCalibration(fakeUrl, mName);

                            // found calibration file?
                            if (calibration != null) {
//#ifdef __LOG__
                                if (log.isEnabled()) log.debug("calibration loaded: " + calibration + "; layer = " + lName + "; mName = " + mName);
//#endif
                                // save calibration for given map - only one calibration per map allowed :-)
                                if (!Atlas.getLayerCollection(atlas, lName).contains(mName)) {
                                    Atlas.getLayerCollection(atlas, lName).put(mName, calibration);
                                }
                            }
                        }
                    }

                    // go back to atlas root
                    file.setFileConnection(File.PARENT_DIR);
                }
            }

        } finally {

            // close file
            File.closeQuietly(file);
        }
    }

    private static Calibration readCalibration(String url, String name) {
        final Map temp = new Map(url, name, null);
        temp.loadCalibration();
        final Calibration result = temp.getCalibration();
        temp.close();
        return result;
    }
}
