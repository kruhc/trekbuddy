// @LICENSE@

package api.file;

//#ifdef __CN1__

import com.codename1.io.FileSystemStorage;

import java.util.Enumeration;
import java.util.Vector;
import java.io.IOException;

/**
 * CN1 file implementation.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class Cn1File extends File {

    private static FileSystemStorage storage;

    static {
        storage = FileSystemStorage.getInstance();
    }

    Cn1File() {
    }

    Enumeration getRoots() {
//        return toEnumeration(storage.getRoots());
        return toEnumeration(new String[]{ "sdcard/", });
    }

    public Enumeration list() throws IOException {
        return toEnumeration(storage.listFiles(getPath()));
    }

    public Enumeration list(String pattern, boolean hidden) throws IOException {
        System.err.println("Cn1File.list(...) not implemented");
        throw new Error("not implemented");
    }

    public void create() throws IOException {
        // nothing to do
    }

    public void delete() throws IOException {
        storage.delete(getPath());
    }

    public void mkdir() throws IOException {
        storage.mkdir(getPath());
    }

    public void rename(String newName) throws IOException {
        storage.rename(getPath(), newName);
    }

    public long fileSize() throws IOException {
        return -1;
    }

    public long directorySize(boolean includeSubDirs) throws IOException {
        return -1;
    }

    public boolean exists() {
        return storage.exists(getPath());
    }

    public boolean isDirectory() {
        return storage.isDirectory(getPath());
    }

    public String getURL() {
        return File.FILE_PROTOCOL + getPath();
    }

    public String getName() {
        System.err.println("Cn1File.getName(...) not implemented");
        throw new Error("not implemented");
    }

    public String getPath() {
        System.err.println("Cn1File.list(...) not implemented");
        throw new Error("not implemented");
    }

    public void setFileConnection(String path) throws IOException {
        System.err.println("Cn1File.setFileConnection(...) not implemented");
        throw new Error("not implemented");
    }

    public void setHidden(boolean hidden) throws IOException {
        storage.setHidden(getPath(), hidden);
    }

    private static Enumeration toEnumeration(final String[] items) {
        final Vector array = new Vector();
        if (items != null) {
            for (int i = 0, N = items.length; i < N; i++) {
                array.addElement(items[i]);
            }
        }
        return array.elements();
    }
}

//#endif
