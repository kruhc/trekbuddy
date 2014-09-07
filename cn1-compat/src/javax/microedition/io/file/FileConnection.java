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
        com.codename1.io.Log.p("FileConnection.openDataInputStream not implemented", com.codename1.io.Log.ERROR);
        throw new Error("FileConnection.openDataInputStream not implemented");
    }

    public OutputStream openOutputStream() throws IOException {
        return storage.openOutputStream(url);
    }

    public DataOutputStream openDataOutputStream() throws IOException {
        com.codename1.io.Log.p("FileConnection.openDataOutputStream not implemented", com.codename1.io.Log.ERROR);
        throw new Error("FileConnection.openDataOutputStream not implemented");
    }

    public void close() throws IOException {
        com.codename1.io.Log.p("FileConnection.close - nothing to do (???)", com.codename1.io.Log.DEBUG);
    }

    public Enumeration list() throws IOException {
        return toEnumeration(storage.listFiles(url));
    }

    public Enumeration list(String pattern, boolean hidden) throws IOException {
        com.codename1.io.Log.p("FileConnection.list(...) not implemented", com.codename1.io.Log.ERROR);
        throw new Error("FileConnection.list(...) not implemented");
    }

    public void create() throws IOException {
        com.codename1.io.Log.p("FileConnection.create " + url + " - nothing to do", com.codename1.io.Log.WARNING);
        // nothing to do
    }

    public void delete() throws IOException {
//#ifdef __LOG__
        com.codename1.io.Log.p("FileConnection.delete " + url, com.codename1.io.Log.DEBUG);
//#endif
        storage.delete(url);
    }

    public void mkdir() throws IOException {
//#ifdef __LOG__
        com.codename1.io.Log.p("FileConnection.mkdir " + url, com.codename1.io.Log.DEBUG);
//#endif
        storage.mkdir(url);
    }

    public void rename(String newName) throws IOException {
//#ifdef __LOG__
        com.codename1.io.Log.p("FileConnection.rename " + getPath() + " to " + newName, com.codename1.io.Log.WARNING);
//#endif
        storage.rename(getPath(), newName);
    }

    public long fileSize() throws IOException {
        return storage.getLength(url);
    }

    public long directorySize(boolean includeSubDirs) throws IOException {
        com.codename1.io.Log.p("FileConnection.directorySize not supported properly", com.codename1.io.Log.WARNING);
        final String[] content = storage.listFiles(url);
        if (content == null || content.length == 0) {
            return -1;
        }
        return 1; // wrong but good enough for me now
    }

    public boolean exists() {
//#ifdef __LOG__
        com.codename1.io.Log.p("FileConnection.exists? " + url + "; " + storage.exists(url), com.codename1.io.Log.DEBUG);
//#endif
        return storage.exists(url);
    }

    public boolean isDirectory() {
//#ifdef __LOG__
        com.codename1.io.Log.p("FileConnection.isDirectory? " + url + "; " + storage.isDirectory(url), com.codename1.io.Log.DEBUG);
//#endif
        return storage.isDirectory(url);
    }

    public String getURL() {
        return url;
    }

    public String getName() {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    public String getPath() {
        return url.substring(FILE_PROTOCOL.length() + 1).replace('/', '\\');
    }

    public void setFileConnection(String path) throws IOException {
        com.codename1.io.Log.p("FileConnection.setFileConnection: " + path + "; current = " + url, com.codename1.io.Log.DEBUG);
        if ("..".equals(path)) {
            final int idx = url.lastIndexOf(PATH_SEPCHAR, url.length() - 1 - 1);
            if (idx > FILE_PROTOCOL.length()) {
                url = url.substring(0, idx + 1);
            }
        } else if (path != null && path.length() != 0) {
            url = url.concat(path);
        }
        com.codename1.io.Log.p("FileConnection.setFileConnection; url is now " + url, com.codename1.io.Log.DEBUG);
    }

    public void setHidden(boolean hidden) throws IOException {
        storage.setHidden(getPath(), hidden);
    }

    private Enumeration toEnumeration(final String[] items) {
        final Vector<String> array = new Vector<String>();
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

