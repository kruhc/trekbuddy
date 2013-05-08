package javax.microedition.io.file;

import com.codename1.io.FileSystemStorage;

import javax.microedition.io.StreamConnection;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Enumeration;
import java.util.Vector;

public class FileConnection implements StreamConnection {
    public static final String FILE_PROTOCOL    = "file://";
    public static final String PATH_SEPARATOR   = "/";
    public static final char PATH_SEPCHAR       = '/';

    public static char OS_PATH_SEPCHAR;

    private static FileSystemStorage storage;

    static {
        storage = FileSystemStorage.getInstance();
        OS_PATH_SEPCHAR = storage.getFileSystemSeparator();
    }

    private String url;
    private int mode;

    public FileConnection(String url, int mode) {
        this.url = url;
        this.mode = mode;
    }

    public InputStream openInputStream() throws IOException {
        return storage.openInputStream(url);
    }

    public DataInputStream openDataInputStream() throws IOException {
        System.err.println("ERROR FileConnection.openDataInputStream not implemented");
        throw new Error("FileConnection.openDataInputStream not implemented");
    }

    public OutputStream openOutputStream() throws IOException {
        System.err.println("ERROR FileConnection.openOutputStream not implemented");
        throw new Error("FileConnection.openOutputStream not implemented");
    }

    public DataOutputStream openDataOutputStream() throws IOException {
        System.err.println("ERROR FileConnection.openDataOutputStream not implemented");
        throw new Error("FileConnection.openDataOutputStream not implemented");
    }

    public void close() throws IOException {
        System.err.println("WARN FileConnection.close not implemented");
    }

    public Enumeration list() throws IOException {
        return toEnumeration(storage.listFiles(url));
    }

    public Enumeration list(String pattern, boolean hidden) throws IOException {
        System.err.println("FileConnection.list(...) not implemented");
        throw new Error("FileConnection.list(...) not implemented");
    }

    public void create() throws IOException {
        System.out.println("FileConnection.create " + url);
        // nothing to do
    }

    public void delete() throws IOException {
        System.out.println("FileConnection.delete " + url);
        storage.delete(url);
    }

    public void mkdir() throws IOException {
        System.out.println("FileConnection.mkdir " + url);
        storage.mkdir(url);
    }

    public void rename(String newName) throws IOException {
        System.out.println("WARN FileConnection.rename " + getPath() + " to " + newName);
        storage.rename(getPath(), newName);
    }

    public long fileSize() throws IOException {
        return storage.getLength(url);
    }

    public long directorySize(boolean includeSubDirs) throws IOException {
        System.err.println("WARN FileConnection.directorySize not implemented");
        return -1; // -1 is valid value according to JSR-75
    }

    public boolean exists() {
        System.out.println("FileConnection.exists? " + url + "; " + storage.exists(url));
        return storage.exists(url);
    }

    public boolean isDirectory() {
        System.out.println("FileConnection.isDirectory? " + url + "; " + storage.exists(url));
        return storage.isDirectory(url);
    }

    public String getURL() {
        return url;
    }

    public String getName() {
        return url.substring(url.lastIndexOf('/'));
    }

    public String getPath() {
        return url.substring(FILE_PROTOCOL.length() + 1).replace('/', '\\');
    }

    public void setFileConnection(String path) throws IOException {
        System.err.println("ERROR FileConnection.setFileConnection not implemented");
        throw new Error("FileConnection.setFileConnection not implemented");
/*
        System.err.println(url);
        System.err.println(getPath());
        System.err.println(path);
        if (path == null) {
            return;
        } else if ("..".equals(path)) {
            final int idx = url.lastIndexOf(PATH_SEPCHAR, url.length() - 1 - 1);
            if (idx > FILE_PROTOCOL.length()) {
                url = url.substring(0, idx + 1);
            }
            System.err.println("path is .., now: " + url);
        } else {
            url += path;
            System.err.println("path is now: " + url);
        }
*/
    }

    public void setHidden(boolean hidden) throws IOException {
        storage.setHidden(getPath(), hidden);
    }

    public static boolean isDir(String path) {
        return storage.isDirectory(path);
    }

    private Enumeration toEnumeration(final String[] items) {
        final Vector array = new Vector();
        if (items != null) {
            for (int i = 0, N = items.length; i < N; i++) {
                if (storage.isDirectory(url + items[i]) && !items[i].endsWith(PATH_SEPARATOR)) {
                    items[i] += PATH_SEPARATOR;
                }
                array.addElement(items[i]);
            }
        }
        return array.elements();
    }
}

