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
 * @modified by Ales Pour <kruhc@seznam.cz>
 */
public final class TarInputStream extends InputStream {

    // default block size
    public static final int DEFAULT_RCDSIZE = 512;

    // underlying stream
    private InputStream in;

    // stream state
    private boolean hasHitEOF;
    private long entrySize;
    private long entryOffset;
    private byte[] headerBuffer;
    private TarEntry currEntry;

    /* rewind/skip support */
    private long streamOffset;

    /**
     * Creates tar input stream.
     *
     * @param in native input stream
     */
    public TarInputStream(InputStream in) {
        this.in = in;
        this.headerBuffer = new byte[DEFAULT_RCDSIZE];
        this.currEntry = new TarEntry();
    }

    /**
     * Reuses this with new stream.
     *
     * @param in new input stream
     */
    public void setInputStream(InputStream in) {
        this.in = null; // gc hint
        this.in = in;
        this.hasHitEOF = false;
        this.entryOffset = this.entrySize = 0;
    }

    /**
     * Closes this stream. Closing underlying stream had to be commented out,
     * because Image.createImage() on SE phones closes the stream,
     * which is something we do not want to happen if we want to happen.
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
     * Skips bytes using underlying stream skip(long n) method.
     *
     * @param n number of bytes to skip
     * @return number of bytes actually skipped
     * @throws IOException
     */
    public long skip(long n) throws IOException {

        if (cz.kruch.track.configuration.Config.siemensIo && n > 0) {
            n += this.streamOffset;
            this.streamOffset = 0;
        }

        long num = n;

        for (; num > 0;) {

            final long numRead;

            // use skip method
            if (cz.kruch.track.maps.Map.useSkip || cz.kruch.track.configuration.Config.siemensIo) {
                numRead = this.in.skip(num);
                /*
                 * Check for SE bug, where skip() returns stream position,
                 * ie. return value of seek() :-)
                 * Fortunately this is also compatible with broken Siemens skip() :-)
                 */
                if (numRead == this.streamOffset + n) {
                    num = numRead; // trick - 'for' cycle will quit
                }
            } else { // use read to 'skip' - misuse header buffer here ;-)
                numRead = this.in.read(headerBuffer, 0, num > DEFAULT_RCDSIZE ? DEFAULT_RCDSIZE : (int) num);
            }

            if (numRead < 0) {
                break;
            }

            num -= numRead;
        }

        if (num > 0) {
            throw new IOException(num + " bytes left to be skipped");
        }

        this.streamOffset += (n - num);

        return (n - num);
    }

    /**
     * Gets stream offset.
     *
     * @return stream offset
     */
    public long getStreamOffset() {
        return this.streamOffset;
    }

    /**
     * Sets stream offset.
     *
     * @param streamOffset stream offset
     */
    public void setStreamOffset(long streamOffset) {
        this.streamOffset = streamOffset;
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
     * @throws IOException if something goes wrong
     */
    public TarEntry getNextEntry() throws IOException {
        if (this.hasHitEOF)
            return null;

        if (this.currEntry != null) {
            long numToSkip = 0;

            final long liveBytes = this.entrySize - this.entryOffset;
            if (liveBytes > 0) {
                numToSkip += liveBytes;
            }
            final long padding = (this.entryOffset + liveBytes) % DEFAULT_RCDSIZE;
            if (padding > 0) {
                numToSkip += (DEFAULT_RCDSIZE - padding);
            }

            this.skip(numToSkip);
        }

        final long entryPosition = this.streamOffset;
        final byte[] headerBuf = this.readHeader();

        if (isEOFBlock(headerBuf)) {
            this.hasHitEOF = true;
        } else {
            try {
                this.currEntry.init(headerBuf, entryPosition);
                this.entryOffset = 0;
                this.entrySize = this.currEntry.getSize();
                return this.currEntry;
            } catch (InvalidHeaderException e) {
                this.entryOffset = 0;
                this.entrySize = 0;
                throw e;
            }
        }

        return null;
    }

    /**
     * Reads a byte from the current tar archive entry.
     * <p/>
     * This method simply calls read( byte[], int, int ).
     *
     * @return The byte read, or -1 at EOF.
     */
    public int read() throws IOException {
        final int num = this.read(this.headerBuffer, 0, 1);
        if (num == -1) {
            return num;
        }
        return (int) this.headerBuffer[0];
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

        final int c = this.in.read(buffer, offset, numToRead);
        if (c > -1) {
            this.entryOffset += c;
            this.streamOffset += c;
        }

        return c;
    }

    /**
     * Reads entry header.
     *
     * @return header byte
     * @throws IOException if something goes wrong
     */
    private byte[] readHeader() throws IOException {
        int offset = 0;
        int bytesNeeded = DEFAULT_RCDSIZE;
        for (; bytesNeeded > 0;) {
            final int numBytes = this.in.read(this.headerBuffer, offset, bytesNeeded);

            if (numBytes == -1) {
                throw new IOException("Broken archive - EOF block not found");
            }

            offset += numBytes;
            bytesNeeded -= numBytes;

            this.streamOffset += numBytes;
        }

        if (offset != DEFAULT_RCDSIZE) {
            throw new IOException("Incomplete header: " + offset + " of " + DEFAULT_RCDSIZE + " bytes read.");
        }

        return this.headerBuffer;
    }

    /**
     * Determine if an archive record indicate End of Archive. End of
     * archive is indicated by a record that consists entirely of null bytes.
     *
     * @param block block data to check
     * @return <code>true</code> if block is end of archive; <code>false</code> otherwise
     */
    private static boolean isEOFBlock(final byte[] block) {
        for (int i = DEFAULT_RCDSIZE; --i >= 0; ) {
            if (block[i] != 0) {
                return false;
            }
        }

        return true;
    }
}