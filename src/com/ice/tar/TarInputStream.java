/*
** Authored by Timothy Gerard Endres
** <mailto:time@gjt.org>  <http://www.trustice.com>
** 
** This work has been placed into the public domain.
** You may use this work in any way and for any purpose you wish.
**
** THIS SOFTWARE IS PROVIDED AS-IS WITHOUT WARRANTY OF ANY KIND,
** NOT EVEN THE IMPLIED WARRANTY OF MERCHANTABILITY. THE AUTHOR
** OF THIS SOFTWARE, ASSUMES _NO_ RESPONSIBILITY FOR ANY
** CONSEQUENCE RESULTING FROM THE USE, MODIFICATION, OR
** REDISTRIBUTION OF THIS SOFTWARE. 
** 
*/

package com.ice.tar;

import java.io.InputStream;
import java.io.IOException;

/**
 * The TarInputStream reads a UNIX tar archive as an InputStream.
 * methods are provided to position at each successive entry in
 * the archive, and the read each entry as a normal input stream
 * using read().
 * <p/>
 * Kerry Menzel <kmenzel@cfl.rr.com> Contributed the code to support
 * file sizes greater than 2GB (longs versus ints).
 *
 * @author Timothy Gerard Endres, <time@gjt.org>
 * @version $Revision$
 * @see TarHeader
 * @see TarEntry
 *
 * @modified by Ales Pour, <kruhc@seznam.cz> 
 */
public final class TarInputStream extends InputStream {

    private static final int DEFAULT_RCDSIZE = 512;

    // underlying stream
    private InputStream in;

    // stream state
    private int recordSize;
    private boolean hasHitEOF;
    private long entrySize;
    private long entryOffset;
    private byte[] oneBuffer;
    private byte[] headerBuffer;
    private TarEntry currEntry;

    /* rewind support */
    private long streamOffset;

    /* for skip bug workaround */
    private static final int READ_SKIP_BUFFER_LENGTH = 4096;
    public static boolean useSkipBug;
    private byte[] readSkipBuffer;

    /**
     * Creates tar input stream.
     * @param in native input stream
     */
    public TarInputStream(InputStream in) {
        this.in = in;
        this.recordSize = DEFAULT_RCDSIZE;
        this.oneBuffer = new byte[1];
        this.headerBuffer = new byte[this.recordSize/*blockSize*/];
        this.hasHitEOF = false;
        this.entryOffset = this.entrySize = 0;
        this.streamOffset = 0;

        /* broken skip */
        if (useSkipBug) {
            readSkipBuffer = new byte[READ_SKIP_BUFFER_LENGTH];
        }
    }

    /**
     * Closes this stream.
     */
    public void close() throws IOException {
/* underlying stream is closed elsewhere
        if (this.in == null) {
            return;
        }
        this.in.close();
*/
    }

    /**
     * Get the available data that can be read from the current
     * entry in the archive. This does not indicate how much data
     * is left in the entire archive, only in the current entry.
     * This value is determined from the entry's size header field
     * and the amount of data already read from the current entry.
     *
     * @return The number of available bytes for the current entry.
     */
    public int available() throws IOException {
        return (int) (this.entrySize - this.entryOffset);
    }

    /**
     * Skips bytes using underlying stream skip(long bytes) method.
     *
     * @param numToSkip
     * @return number of bytes actually skipped
     * @throws IOException
     */
    public long skip(long numToSkip) throws IOException {
        long num = numToSkip;

        for (; num > 0;) {
            long numRead = -1;

            if (readSkipBuffer == null) {
                numRead = this.in.skip(num);
                /*
                 * Check for vendor bug - stream position returned by skip
                 */
                if (numRead == streamOffset + numToSkip) {
                    num = numRead; // trick - 'for' cycle will quit
                }
            } else {
                numRead = this.in.read(readSkipBuffer, 0, num > READ_SKIP_BUFFER_LENGTH ? READ_SKIP_BUFFER_LENGTH : (int) num);
            }

            if (numRead < 0) {
                break;
            }

            num -= numRead;
        }

        if (num > 0) {
            throw new IOException(num + " bytes left to be skipped");
        }

        this.streamOffset += numToSkip - num;

        return (numToSkip - num);
    }

    /**
     * Gets stream offset.
     */
    public long getStreamOffset() {
        return streamOffset;
    }

    /**
     * Sets stream offset.
     */
    public void setStreamOffset(long streamOffset) {
        this.streamOffset = streamOffset;
    }

    /**
     * Sets stream offset to given value.
     * @param position stream position
     * @throws IOException
     */
    public void rewind(long position) throws IOException {
        if (position < streamOffset) {
            throw new IllegalStateException("Stream is dirty");
        }

        this.skip(position - streamOffset);
    }

    /**
     * Get the next entry in this tar archive. This will skip
     * over any remaining data in the current entry, if there
     * is one, and place the input stream at the header of the
     * next entry, and read the header and instantiate a new
     * TarEntry from the header bytes and return that entry.
     * If there are no more entries in the archive, null will
     * be returned to indicate that the end of the archive has
     * been reached.
     *
     * @return The next TarEntry in the archive, or null.
     */
    public TarEntry getNextEntry()
            throws IOException {
        if (this.hasHitEOF)
            return null;

        if (this.currEntry != null) {
            long numToSkip = 0;

            long liveBytes = this.entrySize - this.entryOffset;
            if (liveBytes > 0) {
                numToSkip += liveBytes;
            }
            long padding = (this.entryOffset + liveBytes) % recordSize;
            if (padding > 0) {
                numToSkip += (recordSize - padding);
            }

            this.skip(numToSkip);
        }

        long entryPosition = streamOffset;

        byte[] headerBuf = this.readHeader();

        if (headerBuf == null) {
            this.hasHitEOF = true;
        } else if (this.isEOFBlock(headerBuf)) {
            this.hasHitEOF = true;
        }

        if (this.hasHitEOF) {
            this.currEntry = null;
        } else {
            try {
                if (this.currEntry == null) {
                    this.currEntry = new TarEntry(headerBuf, entryPosition);
                } else {
                    this.currEntry.update(headerBuf, entryPosition);
                }
                this.entryOffset = 0;
                this.entrySize = this.currEntry.getSize();
            } catch (InvalidHeaderException e) {
                this.currEntry = null;
                this.entryOffset = 0;
                this.entrySize = 0;
                throw e;
            }
        }

        return this.currEntry;
    }

    /**
     * Reads a byte from the current tar archive entry.
     * <p/>
     * This method simply calls read( byte[], int, int ).
     *
     * @return The byte read, or -1 at EOF.
     */
    public int read() throws IOException {
        int num = this.read(this.oneBuffer, 0, 1);
        if (num == -1)
            return num;
        else
            return (int) this.oneBuffer[0];
    }

    /**
     * Reads bytes from the current tar archive entry.
     * <p/>
     * This method simply calls read( byte[], int, int ).
     *
     * @param buf The buffer into which to place bytes read.
     * @return The number of bytes read, or -1 at EOF.
     */
    public int read(byte[] buf) throws IOException {
        return this.read(buf, 0, buf.length);
    }

    /**
     * Reads bytes from the current tar archive entry.
     * This method is aware of the boundaries of the current
     * entry in the archive and will deal with them as if they
     * were this stream's start and EOF.
     *
     * @param buffer    The buffer into which to place bytes read.
     * @param offset    The offset at which to place bytes read.
     * @param numToRead The number of bytes to read.
     * @return The number of bytes read, or -1 at EOF.
     */
    public int read(byte[] buffer, int offset, int numToRead) throws IOException {
        if (this.entryOffset >= this.entrySize)
            return -1;

        if ((numToRead + this.entryOffset) > this.entrySize) {
            numToRead = (int) (this.entrySize - this.entryOffset);
        }

        int c = this.in.read(buffer, offset, numToRead);
        if (c > -1) {
            this.entryOffset += c;
            this.streamOffset += c;
        }

        return c;
    }

    /**
     * @return null if End-Of-File, else block buffer
     */
    private byte[] readHeader() throws IOException {
        int offset = 0;
        int bytesNeeded = this.recordSize;
        for (; bytesNeeded > 0;) {
            long numBytes = this.in.read(headerBuffer, offset, bytesNeeded);

            if (numBytes == -1) {
                throw new IOException("Broken archive - EOF block not found");
            }

            offset += numBytes;
            bytesNeeded -= numBytes;

            this.streamOffset += numBytes;
        }

        if (offset != this.recordSize) {
            throw new IOException("Incomplete header: " + offset + " of " + this.recordSize + " bytes read.");
        }

        return headerBuffer;
    }

    /**
     * Determine if an archive record indicate End of Archive. End of
     * archive is indicated by a record that consists entirely of null bytes.
     *
     * @param block block data to check.
     */
    private boolean isEOFBlock(byte[] block) {
        for (int i = this.recordSize; --i >= 0; ) {
            if (block[i] != 0)
                return false;
        }

        return true;
    }

    /**
     * Reuse this with new stream.
     * @param in new input stream
     */
    public void reuse(InputStream in) {
        this.in = null; // gc hint
        this.in = in;
        this.hasHitEOF = false;
        this.currEntry = null;
        this.entryOffset = this.entrySize = 0;
    }

    /**
     * Disposes resources.
     */
    public void dispose() {
        this.oneBuffer = null;
        this.headerBuffer = null;
        this.readSkipBuffer = null;
    }
}