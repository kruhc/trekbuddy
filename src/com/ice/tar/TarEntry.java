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
 * @see TarHeader
 */

public final class TarEntry {
    /**
     * This is the entry's header information.
     */
    private TarHeader header;

    /**
     * Entry's absolute position in archive.
     */
    private long position;

    /**
     * Construct an entry from an archive's header bytes. File is set
     * to null.
     *
     * @param headerBuf The header bytes from a tar archive entry.
     */
    public TarEntry(byte[] headerBuf) throws InvalidHeaderException {
        this(headerBuf, -1);
    }

    /**
     * Construct an entry from an archive's header bytes. File is set
     * to null.
     *
     * @param headerBuf The header bytes from a tar archive entry.
     */
    public TarEntry(byte[] headerBuf, long position) throws InvalidHeaderException {
        this.position = position;
        this.header = new TarHeader(headerBuf);
    }

    /**
     * Determine if the two entries are equal. Equality is determined
     * by the header names being equal.
     *
     * @return True if the entries are equal.
     */
    public boolean equals(TarEntry it) {
        return this.header.name.equals(it.header.name);
    }

    /**
     * Get this entry's name.
     *
     * @return This entry's name.
     */
    public String getName() {
        return this.header.name;
    }

    /**
     * Get this entry's file size.
     *
     * @return This entry's file size.
     */
    public long getSize() {
        return this.header.size;
    }

    /**
     * Gets this entry's position in archive.
     *
     * @return position in archive (header start).
     */
    public long getPosition() {
        return position;
    }

    /**
     * Return whether or not this entry represents a directory.
     *
     * @return True if this entry is a directory.
     */
    public boolean isDirectory() {
        if (this.header != null) {
            if (this.header.linkFlag == TarHeader.LF_DIR)
                return true;

            if (this.header.name.endsWith("/"))
                return true;
        }

        return false;
    }

    /**
     * Dispose.
     */
    public void dispose() {
        this.header = null;
    }
}

