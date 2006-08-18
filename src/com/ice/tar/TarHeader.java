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

final class TarHeader {
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
     * Directory file type.
     */
    public static final byte LF_DIR = (byte) '5';

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
     * Default constructor.
     */
    public TarHeader(byte[] headerBuf) throws InvalidHeaderException {
        this.name = null;
        parseTarHeader(this, headerBuf);
    }

    /**
     * Parse an entry's TarHeader information from a header buffer.
     * Old unix-style code contributed by David Mehringer <dmehring@astro.uiuc.edu>.
     *
     * @param hdr header to fill in from the buffer information
     * @param headerBuf The tar entry header buffer to get information from
     */
    private static void parseTarHeader(TarHeader hdr, byte[] headerBuf)
            throws InvalidHeaderException {
        //
        // NOTE Recognize archive header format.
        //
        if (headerBuf[257] == 0
                && headerBuf[258] == 0
                && headerBuf[259] == 0
                && headerBuf[260] == 0
                && headerBuf[261] == 0) {
/*
            this.unixFormat = true;
            this.ustarFormat = false;
            this.gnuFormat = false;
*/
        } else if (headerBuf[257] == 'u'
                && headerBuf[258] == 's'
                && headerBuf[259] == 't'
                && headerBuf[260] == 'a'
                && headerBuf[261] == 'r'
                && headerBuf[262] == 0) {
/*
            this.ustarFormat = true;
            this.gnuFormat = false;
            this.unixFormat = false;
*/
        } else if (headerBuf[257] == 'u'
                && headerBuf[258] == 's'
                && headerBuf[259] == 't'
                && headerBuf[260] == 'a'
                && headerBuf[261] == 'r'
                && headerBuf[262] != 0
                && headerBuf[263] != 0) {
            // REVIEW
/*
            this.gnuFormat = true;
            this.unixFormat = false;
            this.ustarFormat = false;
*/
        } else {
            StringBuffer buf = new StringBuffer(128);

            buf.append("header magic is not 'ustar' or unix-style zeros, it is '");
            buf.append(headerBuf[257]);
            buf.append(headerBuf[258]);
            buf.append(headerBuf[259]);
            buf.append(headerBuf[260]);
            buf.append(headerBuf[261]);
            buf.append(headerBuf[262]);
            buf.append(headerBuf[263]);
            buf.append("', or (dec) ");
            buf.append((int) headerBuf[257]);
            buf.append(", ");
            buf.append((int) headerBuf[258]);
            buf.append(", ");
            buf.append((int) headerBuf[259]);
            buf.append(", ");
            buf.append((int) headerBuf[260]);
            buf.append(", ");
            buf.append((int) headerBuf[261]);
            buf.append(", ");
            buf.append((int) headerBuf[262]);
            buf.append(", ");
            buf.append((int) headerBuf[263]);

            throw new InvalidHeaderException(buf.toString());
        }

        hdr.name = parseFileName(headerBuf);

        int offset = NAMELEN;

//        hdr.mode = (int) TarHeader.parseOctal(headerBuf, offset, TarHeader.MODELEN);

        offset += MODELEN;

//        hdr.userId = (int) TarHeader.parseOctal(headerBuf, offset, TarHeader.UIDLEN);

        offset += UIDLEN;

//        hdr.groupId = (int) TarHeader.parseOctal(headerBuf, offset, TarHeader.GIDLEN);

        offset += GIDLEN;

        hdr.size = parseOctal(headerBuf, offset, TarHeader.SIZELEN);

        offset += SIZELEN;

//        hdr.modTime = TarHeader.parseOctal(headerBuf, offset, TarHeader.MODTIMELEN);

        offset += MODTIMELEN;

//        hdr.checkSum = (int) TarHeader.parseOctal(headerBuf, offset, TarHeader.CHKSUMLEN);

        offset += CHKSUMLEN;

        hdr.linkFlag = headerBuf[offset++];

//        hdr.linkName = TarHeader.parseName(headerBuf, offset, TarHeader.NAMELEN);

        offset += NAMELEN;
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
    private static long parseOctal(byte[] header, int offset, int length) {
        long result = 0;
        boolean stillPadding = true;
        int end = offset + length;
        byte space = (byte) ' ';

        for (int i = offset; i < end; ++i) {
            byte b = header[i];

            if (b == 0)
                break;

            if (b == space || b == '0') {
                if (stillPadding)
                    continue;

                if (b == space)
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
    private static String parseFileName(byte[] header) {
        /*StringBuffer result = new StringBuffer(256);*/
        char[] result = new char[256];
        int k = 0;

        // If header[345] is not equal to zero, then it is the "prefix"
        // that 'ustar' defines. It must be prepended to the "normal"
        // name field. We are responsible for the separating '/'.
        //
        if (header[PREFIXOFFSET] != 0) {
            for (int i = PREFIXOFFSET; header[i] != 0 && i < 500; ++i) {
                /*result.append((char) header[i]);*/
                result[k++] = (char) header[i];
            }

            /*result.append("/");*/
            result[k++] = '/';
        }

        for (int i = 0; header[i] != 0 && i < 100; ++i) {
            /*result.append((char) header[i]);*/
            result[k++] = (char) header[i];
        }

        /*return result.toString();*/
        return new String(result, 0, k);
    }
}
 
