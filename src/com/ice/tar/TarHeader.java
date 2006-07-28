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

/**
 * This class encapsulates the Tar Entry Header used in Tar Archives.
 * The class also holds a number of tar constants, used mostly in headers.
 *
 * @author Timothy Gerard Endres, <time@gjt.org>
 */

public final class TarHeader {
    /**
     * The length of the name field in a header buffer.
     */
    public static final int NAMELEN = 100;
    /**
     * The offset of the name field in a header buffer.
     */
    public static final int NAMEOFFSET = 0;
    /**
     * The length of the name prefix field in a header buffer.
     */
    public static final int PREFIXLEN = 155;
    /**
     * The offset of the name prefix field in a header buffer.
     */
    public static final int PREFIXOFFSET = 345;
    /**
     * The length of the mode field in a header buffer.
     */
    public static final int MODELEN = 8;
    /**
     * The length of the user id field in a header buffer.
     */
    public static final int UIDLEN = 8;
    /**
     * The length of the group id field in a header buffer.
     */
    public static final int GIDLEN = 8;
    /**
     * The length of the checksum field in a header buffer.
     */
    public static final int CHKSUMLEN = 8;
    /**
     * The length of the size field in a header buffer.
     */
    public static final int SIZELEN = 12;
    /**
     * The length of the magic field in a header buffer.
     */
    public static final int MAGICLEN = 8;
    /**
     * The length of the modification time field in a header buffer.
     */
    public static final int MODTIMELEN = 12;
    /**
     * The length of the user name field in a header buffer.
     */
    public static final int UNAMELEN = 32;
    /**
     * The length of the group name field in a header buffer.
     */
    public static final int GNAMELEN = 32;
    /**
     * The length of the devices field in a header buffer.
     */
    public static final int DEVLEN = 8;

    /**
     * LF_ constants represent the "link flag" of an entry, or more commonly,
     * the "entry type". This is the "old way" of indicating a normal file.
     */
    public static final byte LF_OLDNORM = 0;
    /**
     * Normal file type.
     */
    public static final byte LF_NORMAL = (byte) '0';
    /**
     * Link file type.
     */
    public static final byte LF_LINK = (byte) '1';
    /**
     * Symbolic link file type.
     */
    public static final byte LF_SYMLINK = (byte) '2';
    /**
     * Character device file type.
     */
    public static final byte LF_CHR = (byte) '3';
    /**
     * Block device file type.
     */
    public static final byte LF_BLK = (byte) '4';
    /**
     * Directory file type.
     */
    public static final byte LF_DIR = (byte) '5';
    /**
     * FIFO (pipe) file type.
     */
    public static final byte LF_FIFO = (byte) '6';
    /**
     * Contiguous file type.
     */
    public static final byte LF_CONTIG = (byte) '7';

    /**
     * The magic tag representing a POSIX tar archive.
     */
    public static final String TMAGIC = "ustar";

    /**
     * The magic tag representing a GNU tar archive.
     */
    public static final String GNU_TMAGIC = "ustar  ";

    /**
     * The entry's name.
     */
    public String name;
    /**
     * The entry's size.
     */
    public long size;
    /**
     * The entry's link flag.
     */
    public byte linkFlag;
    /**
     * The entry's magic tag.
     */
    public String magic;

    /**
     * Default constructor.
     */
    public TarHeader() {
        this.magic = TarHeader.TMAGIC;
        this.name = null;
    }

    /**
     * Parse an octal string from a header buffer. This is used for the
     * file permission mode value.
     *
     * @param header The header buffer from which to parse.
     * @param offset The offset into the buffer from which to parse.
     * @param length The number of header bytes to parse.
     * @return The long value of the octal string.
     */
    public static long parseOctal(byte[] header, int offset, int length)
            throws InvalidHeaderException {

        long result = 0;
        boolean stillPadding = true;
        int end = offset + length;

        for (int i = offset; i < end; ++i) {
            byte b = header[i];

            if (b == 0)
                break;

            if (b == (byte) ' ' || b == '0') {
                if (stillPadding)
                    continue;

                if (b == (byte) ' ')
                    break;
            }

            stillPadding = false;

            result = (result << 3) + (b - '0');
        }

        return result;
    }

    /**
     * Parse a file name from a header buffer. This is different from
     * parseName() in that is recognizes 'ustar' names and will handle
     * adding on the "prefix" field to the name.
     * <p/>
     * Contributed by Dmitri Tikhonov <dxt2431@yahoo.com>
     *
     * @param header The header buffer from which to parse.
     * @return The header's entry name.
     */
    public static String parseFileName(byte[] header) {
        StringBuffer result = new StringBuffer(256);

        // If header[345] is not equal to zero, then it is the "prefix"
        // that 'ustar' defines. It must be prepended to the "normal"
        // name field. We are responsible for the separating '/'.
        //
        if (header[345] != 0) {
            for (int i = 345; i < 500 && header[i] != 0; ++i) {
                result.append((char) header[i]);
            }

            result.append("/");
        }

        for (int i = 0; i < 100 && header[i] != 0; ++i) {
            result.append((char) header[i]);
        }

        return result.toString();
    }

    /**
     * Parse an entry name from a header buffer.
     *
     * @param header The header buffer from which to parse.
     * @param offset The offset into the buffer from which to parse.
     * @param length The number of header bytes to parse.
     * @return The header's entry name.
     */
    public static String parseName(byte[] header, int offset, int length)
            throws InvalidHeaderException {
        StringBuffer result = new StringBuffer(length);

        int end = offset + length;
        for (int i = offset; i < end; ++i) {
            byte b = header[i];
            if (b == 0)
                break;
            result.append((char) b);
        }

        return result.toString();
    }
}
 
