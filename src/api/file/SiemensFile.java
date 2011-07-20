// @LICENSE@

package api.file;

import java.util.Enumeration;
import java.io.IOException;

/**
 * Siemens Sx5 File API implementation.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class SiemensFile extends File {

    SiemensFile() {
    }

    Enumeration getRoots() {
        return com.siemens.mp.io.file.FileSystemRegistry.listRoots();
    }

    public Enumeration list() throws IOException {
        return ((com.siemens.mp.io.file.FileConnection) fc).list();
    }

    public Enumeration list(String string, boolean b) throws IOException {
        return ((com.siemens.mp.io.file.FileConnection) fc).list(string, b);
    }

    public void create() throws IOException {
        ((com.siemens.mp.io.file.FileConnection) fc).create();
    }

    public void delete() throws IOException {
        ((com.siemens.mp.io.file.FileConnection) fc).delete();
    }

    public void mkdir() throws IOException {
        ((com.siemens.mp.io.file.FileConnection) fc).mkdir();
    }

    public void rename(String newName) throws IOException {
        ((com.siemens.mp.io.file.FileConnection) fc).rename(newName);
    }

    public long fileSize() throws IOException {
        return ((com.siemens.mp.io.file.FileConnection) fc).fileSize();
    }

    public long directorySize(boolean includeSubDirs) throws IOException {
        return ((com.siemens.mp.io.file.FileConnection) fc).directorySize(includeSubDirs);
    }

    public boolean exists() {
        return ((com.siemens.mp.io.file.FileConnection) fc).exists();
    }

    public boolean isDirectory() {
        return ((com.siemens.mp.io.file.FileConnection) fc).isDirectory();
    }

    public String getURL() {
        return ((com.siemens.mp.io.file.FileConnection) fc).getURL();
    }

    public String getName() {
        return ((com.siemens.mp.io.file.FileConnection) fc).getName();
    }

    public String getPath() {
        return ((com.siemens.mp.io.file.FileConnection) fc).getPath();
    }

    public void setFileConnection(String path) throws IOException {
        ((com.siemens.mp.io.file.FileConnection) fc).setFileConnection(path);
    }
}
