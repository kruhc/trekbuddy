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

import java.io.UnsupportedEncodingException;

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
        if (headerBuf[257] == 'u'
                && headerBuf[258] == 's'
                && headerBuf[259] == 't'
                && headerBuf[260] == 'a'
                && headerBuf[261] == 'r'
                && headerBuf[262] == 0) {
            // OK
        } else if (headerBuf[257] == 'u'
                && headerBuf[258] == 's'
                && headerBuf[259] == 't'
                && headerBuf[260] == 'a'
                && headerBuf[261] == 'r'
                && headerBuf[262] != 0
                && headerBuf[263] != 0) {
            // OK?
        } else if (headerBuf[257] == 0
                && headerBuf[258] == 0
                && headerBuf[259] == 0
                && headerBuf[260] == 0
                && headerBuf[261] == 0) {
            // OK
        } else {
            StringBuffer sb = new StringBuffer(64);

            sb.append("Unknown tar format. Header: '");
            sb.append(headerBuf[257]);
            sb.append(headerBuf[258]);
            sb.append(headerBuf[259]);
            sb.append(headerBuf[260]);
            sb.append(headerBuf[261]);
            sb.append(headerBuf[262]);
            sb.append(headerBuf[263]);
            sb.append("'");

            throw new InvalidHeaderException(sb.toString());
        }

        name = parseFileName(headerBuf);

        int offset = NAMELEN;
        offset += MODELEN;
        offset += UIDLEN;
        offset += GIDLEN;

        size = parseOctal(headerBuf, offset, TarHeader.SIZELEN);

        offset += SIZELEN;
        offset += MODTIMELEN;
        offset += CHKSUMLEN;

        linkFlag = headerBuf[offset++];

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

        for (int i = offset; i < end; ++i) {
            byte b = header[i];

            if (b == 0)
                break;

            if (b == ' ' || b == '0') {
                if (stillPadding)
                    continue;
                if (b == ' ')
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
/*
        char[] result = new char[256];
        int k = 0;
*/
        String prefix = null;

        // If header[345] is not equal to zero, then it is the "prefix"
        // that 'ustar' defines. It must be prepended to the "normal"
        // name field. We are responsible for the separating '/'.
        if (header[PREFIXOFFSET] != 0) {
            int l = 0;
            for (int i = PREFIXOFFSET; i < 500; i++) {
                byte b = header[i];
                if (b == 0)
                    break;
                l++;
/*
                result[k++] = (char) header[i];
*/
            }

            prefix = new String(header, PREFIXOFFSET, l);
/*
            result[k++] = '/';
*/
        }

        int i = 0;
        for (; i < 100; i++) {
            byte b = header[i];
            if (b == 0)
                break;
/*
            result[k++] = (char) header[i];
*/
        }

        try {
            String name = new String(header, 0, i, "US-ASCII");
            if (prefix != null) {
                return (new StringBuffer(prefix)).append('/').append(name).toString();
            }
            return name;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e.toString());
        }
/*
        return new String(result, 0, k);
*/
    }

    /**
     * Dispose all resources.
     */
    public void dispose() {
        name = null;
    }
}
 
