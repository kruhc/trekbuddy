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

import cz.kruch.track.util.CharArrayTokenizer;

import java.io.UnsupportedEncodingException;

/**
 * This class represents an entry in a Tar archive. It consists
 * of the entry's header, as well as the entry's File. Entries
 * can be instantiated in one of three ways, depending on how
 * they are to be used.
 * <p/>
 * TarEntries that are created from the header bytes read from
 * an archive are instantiated with the TarEntry( byte[] )
 * constructor. These entries will be used when extracting from
 * or listing the contents of an archive. These entries have their
 * header filled in using the header bytes. They also set the File
 * to null, since they reference an archive entry not a file.
 * <p/>
 * TarEntries that are created from Files that are to be written
 * into an archive are instantiated with the TarEntry( File )
 * constructor. These entries have their header filled in using
 * the File's information. They also keep a reference to the File
 * for convenience when writing entries.
 * <p/>
 * Finally, TarEntries can be constructed from nothing but a name.
 * This allows the programmer to construct the entry by hand, for
 * instance when only an InputStream is available for writing to
 * the archive, and the header information is constructed from
 * other information. In this case the header fields are set to
 * defaults and the File is set to null.
 * <p/>
 * <pre>
 * <p/>
 * Original Unix Tar Header:
 * <p/>
 * Field  Field     Field
 * Width  Name      Meaning
 * -----  --------- ---------------------------
 *   100  name      name of file
 *     8  mode      file mode
 *     8  uid       owner user ID
 *     8  gid       owner group ID
 *    12  size      length of file in bytes
 *    12  mtime     modify time of file
 *     8  chksum    checksum for header
 *     1  link      indicator for links
 *   100  linkname  name of linked file
 * <p/>
 * </pre>
 * <p/>
 * <pre>
 * <p/>
 * POSIX "ustar" Style Tar Header:
 * <p/>
 * Field  Field     Field
 * Width  Name      Meaning
 * -----  --------- ---------------------------
 *   100  name      name of file
 *     8  mode      file mode
 *     8  uid       owner user ID
 *     8  gid       owner group ID
 *    12  size      length of file in bytes
 *    12  mtime     modify time of file
 *     8  chksum    checksum for header
 *     1  typeflag  type of file
 *   100  linkname  name of linked file
 *     6  magic     USTAR indicator
 *     2  version   USTAR version
 *    32  uname     owner user name
 *    32  gname     owner group name
 *     8  devmajor  device major number
 *     8  devminor  device minor number
 *   155  prefix    prefix for file name
 * <p/>
 * struct posix_header
 *   {                     byte offset
 *   char name[100];            0
 *   char mode[8];            100
 *   char uid[8];             108
 *   char gid[8];             116
 *   char size[12];           124
 *   char mtime[12];          136
 *   char chksum[8];          148
 *   char typeflag;           156
 *   char linkname[100];      157
 *   char magic[6];           257
 *   char version[2];         263
 *   char uname[32];          265
 *   char gname[32];          297
 *   char devmajor[8];        329
 *   char devminor[8];        337
 *   char prefix[155];        345
 *   };                       500
 * <p/>
 * </pre>
 * <p/>
 * Note that while the class does recognize GNU formatted headers,
 * it does not perform proper processing of GNU archives. I hope
 * to add the GNU support someday.
 * <p/>
 * Directory "size" fix contributed by:
 * Bert Becker <becker@informatik.hu-berlin.de>
 *
 * @author Timothy Gerard Endres, <time@gjt.org>
 * @modified by Ales Pour <kruhc@seznam.cz>
 */

public final class TarEntry {
    /**
     * The length of the name field in a header buffer.
     */
    private static final int NAMELEN = 100;
    /**
     * The offset of the name field in a header buffer.
     */
    private static final int NAMEOFFSET = 0;
    /**
     * The length of the name prefix field in a header buffer.
     */
    private static final int PREFIXLEN = 155;
    /**
     * The offset of the name prefix field in a header buffer.
     */
    private static final int PREFIXOFFSET = 345;
    /**
     * The length of the mode field in a header buffer.
     */
    private static final int MODELEN = 8;
    /**
     * The length of the user id field in a header buffer.
     */
    private static final int UIDLEN = 8;
    /**
     * The length of the group id field in a header buffer.
     */
    private static final int GIDLEN = 8;
    /**
     * The length of the checksum field in a header buffer.
     */
    private static final int CHKSUMLEN = 8;
    /**
     * The length of the size field in a header buffer.
     */
    private static final int SIZELEN = 12;
    /**
     * The length of the magic field in a header buffer.
     */
    private static final int MAGICLEN = 8;
    /**
     * The length of the modification time field in a header buffer.
     */
    private static final int MODTIMELEN = 12;
    /**
     * The length of the user name field in a header buffer.
     */
    private static final int UNAMELEN = 32;
    /**
     * The length of the group name field in a header buffer.
     */
    private static final int GNAMELEN = 32;
    /**
     * The length of the devices field in a header buffer.
     */
    private static final int DEVLEN = 8;

    /**
     * Directory file type.
     */
    private static final byte LF_DIR = (byte) '5';

    /**
     * The entry's name.
     */
    private CharArrayTokenizer.Token name;

    /**
     * Entry's absolute position in archive.
     */
    private long position;

    /**
     * The entry's size.
     */
    private long size;

    /**
     * The entry's link flag.
     */
    private byte linkFlag;

    /**
     * Construct an entry.
     */
    public TarEntry() {
        this.name = new CharArrayTokenizer.Token();
        this.name.init(new char[100], 0, 100);
    }

    /**
     * Injects new entry data.
     *
     * @param headerBuf header bytes from a tar archive entry
     * @param position stream position (offset)
     * @throws InvalidHeaderException when something goes wrong
     */
    public void init(final byte[] headerBuf, final long position) throws InvalidHeaderException {
        this.parseHeader(headerBuf);
        this.position = position;
    }

    /**
     * Get this entry's name.
     *
     * @return This entry's name.
     */
    public CharArrayTokenizer.Token getName() {
        return this.name;
    }

    /**
     * Get this entry's file size.
     *
     * @return This entry's file size.
     */
    public long getSize() {
        return this.size;
    }

    /**
     * Gets this entry's position in archive.
     *
     * @return position in archive (header start).
     */
    public long getPosition() {
        return this.position;
    }

    /**
     * Return whether or not this entry represents a directory.
     *
     * @return True if this entry is a directory.
     */
    public boolean isDirectory() {
        return this.linkFlag == LF_DIR || this.name.endsWith("/");
    }

    /**
     * Parses header.
     *
     * @param headerBuf header bytes
     * @throws InvalidHeaderException when something goes wrong
     */
    private void parseHeader(final byte[] headerBuf) throws InvalidHeaderException {
        if (headerBuf[257] == 'u'
                && headerBuf[258] == 's'
                && headerBuf[259] == 't'
                && headerBuf[260] == 'a'
                && headerBuf[261] == 'r'
                && headerBuf[262] == 0) {
        } else if (headerBuf[257] == 'u'
                && headerBuf[258] == 's'
                && headerBuf[259] == 't'
                && headerBuf[260] == 'a'
                && headerBuf[261] == 'r'
                && headerBuf[262] != 0
                && headerBuf[263] != 0) {
        } else if (headerBuf[257] == 0
                && headerBuf[258] == 0
                && headerBuf[259] == 0
                && headerBuf[260] == 0
                && headerBuf[261] == 0) {
        } else {
            final StringBuffer sb = new StringBuffer(64);
            sb.append("Invalid header: '");
            sb.append(Integer.toHexString(headerBuf[257] & 0xff)).append(' ');
            sb.append(Integer.toHexString(headerBuf[258] & 0xff)).append(' ');
            sb.append(Integer.toHexString(headerBuf[259] & 0xff)).append(' ');
            sb.append(Integer.toHexString(headerBuf[260] & 0xff)).append(' ');
            sb.append(Integer.toHexString(headerBuf[261] & 0xff)).append(' ');
            sb.append(Integer.toHexString(headerBuf[262] & 0xff)).append(' ');
            sb.append(Integer.toHexString(headerBuf[263] & 0xff));
            sb.append('\'');

            throw new InvalidHeaderException(sb.toString());
        }

//        name = parseFileName(headerBuf);
        name = parseEntryName(headerBuf);

        int offset = NAMELEN + MODELEN + UIDLEN + GIDLEN;

        size = parseOctal(headerBuf, offset, SIZELEN);

        offset += SIZELEN + MODTIMELEN + CHKSUMLEN;

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
    private static long parseOctal(final byte[] header, final int offset, final int length) {
        long result = 0;
        boolean stillPadding = true;
        final int end = offset + length;

        for (int i = offset; i < end; ++i) {
            final byte b = header[i];

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
     * Parse a file name from a header buffer. This is simplified variant
     * of {@link #parseFileName(byte[])}.
     *
     * @param header header
     * @return token
     */
    private CharArrayTokenizer.Token parseEntryName(final byte[] header) {
        final char[] array = this.name.array;
        int i = 0;
        for (; i < 100; i++) {
            final byte b = header[i];
            if (b == 0) {
                break;
            }
            array[i] = (char) b;
        }

        name.begin = 0;
        name.length = i;

        return name;
    }

    /**
     * Parse a file name from a header buffer. This is different from
     * parseName() in that is recognizes 'ustar' names and will handle
     * adding on the "prefix" field to the name.
     *
     * Contributed by Dmitri Tikhonov <dxt2431@yahoo.com>
     *
     * @param header The header buffer from which to parse.
     * @return The header's entry name.
     */
    private static String parseFileName(final byte[] header) /*throws InvalidHeaderException*/ {
//        String prefix = null;

        /*
         * If header[345] is not equal to zero, then it is the "prefix"
         * that 'ustar' defines. It must be prepended to the "normal"
         * name field. We are responsible for the separating '/'.
         */
//        if (header[PREFIXOFFSET] != 0) {
//            int l = 0;
//            for (int i = PREFIXOFFSET; i < 500; i++) {
//                byte b = header[i];
//                if (b == 0)
//                    break;
//                l++;
//            }
//
//            prefix = new String(header, PREFIXOFFSET, l);
//        }

        int i = 0;
        for (; i < 100; i++) {
            final byte b = header[i];
            if (b == 0) {
                break;
            }
        }

        return new String(header, 0, i);

//        try {
//            return new String(header, 0, i, "US-ASCII");
//            String name = new String(header, 0, i, "US-ASCII");
//            if (prefix == null) {
//                return name;
//            }
//            return (new StringBuffer(prefix)).append('/').append(name).toString();
//        } catch (UnsupportedEncodingException e) {
//            throw new InvalidHeaderException(e.toString());
//        }
    }
}

