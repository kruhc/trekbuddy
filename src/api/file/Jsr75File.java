// @LICENSE@

package api.file;

import javax.microedition.io.file.FileSystemRegistry;
import javax.microedition.io.file.FileConnection;
import java.util.Enumeration;
import java.io.IOException;

/**
 * JSR-75 file implementation.
 *  
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class Jsr75File extends File {

    Jsr75File() {
    }

    Enumeration getRoots() {
        return FileSystemRegistry.listRoots();
    }

    public Enumeration list() throws IOException {
//#ifdef __ALL__
        final Enumeration result = ((FileConnection) fc).list("*", true);
        if (result.hasMoreElements()) {
            return result;
        }
//#endif        
        return ((FileConnection) fc).list();
    }

    public Enumeration list(String pattern, boolean hidden) throws IOException {
        return ((FileConnection) fc).list(pattern, hidden);
    }

    public void create() throws IOException {
        ((FileConnection) fc).create();
    }

    public void delete() throws IOException {
        ((FileConnection) fc).delete();
    }

    public void mkdir() throws IOException {
        ((FileConnection) fc).mkdir();
    }

    public void rename(String newName) throws IOException {
        ((FileConnection) fc).rename(newName);
    }

    public long fileSize() throws IOException {
        return ((FileConnection) fc).fileSize();
    }

    public boolean exists() {
        return ((FileConnection) fc).exists();
    }

    public boolean isDirectory() {
        return ((FileConnection) fc).isDirectory();
    }

    public String getURL() {
        return ((FileConnection) fc).getURL();
    }

    public String getName() {
        return ((FileConnection) fc).getName();
    }

    public String getPath() {
        return ((FileConnection) fc).getPath();
    }

    public void setFileConnection(String path) throws IOException {
        if (fsType == FS_JSR75) {
            ((FileConnection) fc).setFileConnection(path);
        } else {
            traverse(path);
        }
    }
}
