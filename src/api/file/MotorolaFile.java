// @LICENSE@ 

package api.file;

import java.util.Enumeration;
import java.io.IOException;

/**
 * Motorola File API implementation.
 *
 * @author kruhc@seznam.cz
 */
final class MotorolaFile extends File {

    MotorolaFile() {
    }

    Enumeration getRoots() {
        return new StringEnumeration(com.motorola.io.FileSystemRegistry.listRoots());
    }

    public Enumeration list() throws IOException {
        return new StringEnumeration(((com.motorola.io.FileConnection) fc).list());
    }

    public Enumeration list(String pattern, boolean hidden) throws IOException {
        return new StringEnumeration(((com.motorola.io.FileConnection) fc).list(), pattern, hidden);
    }

    public void create() throws IOException {
        ((com.motorola.io.FileConnection) fc).create();
    }

    public void delete() throws IOException {
        ((com.motorola.io.FileConnection) fc).delete();
    }

    public void mkdir() throws IOException {
        ((com.motorola.io.FileConnection) fc).mkdir();
    }

    public void rename(String newName) throws IOException {
        ((com.motorola.io.FileConnection) fc).rename(newName);
    }

    public long fileSize() throws IOException {
        return ((com.motorola.io.FileConnection) fc).fileSize();
    }

    public long directorySize(boolean includeSubDirs) throws IOException {
        return ((com.motorola.io.FileConnection) fc).directorySize(includeSubDirs);
    }

    public boolean exists() {
        return ((com.motorola.io.FileConnection) fc).exists();
    }

    public boolean isDirectory() {
        return ((com.motorola.io.FileConnection) fc).isDirectory();
    }

    public String getURL() {
        return ((com.motorola.io.FileConnection) fc).getURL();
    }

    public String getName() {
        return null;
    }

    public String getPath() {
        return ((com.motorola.io.FileConnection) fc).getPath();
    }

    public void setFileConnection(String path) throws IOException {
        traverse(path);
    }

    /**
     * String enumeration. TODO replace with Vector?
     */
    private static final class StringEnumeration implements Enumeration {
        private String[] list;
        private int offset;

        public StringEnumeration(String[] list) {
            this.list = list;
            this.offset = 0;
        }

        public StringEnumeration(String[] list, String pattern, boolean unknown) {
            this(list);
            // TODO apply pattern
        }

        public boolean hasMoreElements() {
            return offset < list.length;
        }

        public Object nextElement() {
            if (offset >= list.length) {
                throw new java.util.NoSuchElementException();
            }

            return removeDir(list[offset++]);
        }

        private static String removeDir(String f) {
            // only if / is found and is not the end
            while (f.indexOf(PATH_SEPCHAR) >= 0 && f.indexOf(PATH_SEPCHAR) != (f.length() - 1)) {
                f = f.substring(f.indexOf(PATH_SEPCHAR) + 1);
            }

            return f;
        }
    }
}
