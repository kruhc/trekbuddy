 // @LICENSE@

package api.file;

import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import java.util.Enumeration;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

 /**
  * File abstraction.
  *
  * @author Ales Pour <kruhc@seznam.cz>
  */
public abstract class File {

    public static final String FILE_PROTOCOL    = "file://";
    public static final String PARENT_DIR       = "..";
    public static final String PATH_SEPARATOR   = "/";
    public static final char PATH_SEPCHAR       = '/';

    public static final int FS_UNKNOWN          = 0;
    public static final int FS_JSR75            = 1;
    public static final int FS_SIEMENS          = 2;
    public static final int FS_SXG75            = 3;
    public static final int FS_MOTOROLA         = 4;
    public static final int FS_MOTOROLA1000     = 5;
//#ifdef __CN1__
    public static final int FS_CN1              = 6;
//#endif

    public static int fsType; // 0 (= FS_UNKNOWN)

    private static Class factory;
    private static File registry;

    protected StreamConnection fc;

    public static void initialize(final boolean traverseBug) {
        try {
            Class.forName("javax.microedition.io.file.FileConnection");
            factory = Class.forName("api.file.Jsr75File");
            fsType = traverseBug ? FS_SXG75 : FS_JSR75;
        } catch (Throwable t) {
        }
//#ifdef __CN1__
        if (factory == null) {
            try {
                Class.forName("com.codename1.io.FileSystemStorage");
                factory = Class.forName("api.file.Cn1File");
                fsType = FS_CN1;
            } catch (Throwable t) {
            }
        }
//#endif
//#ifdef __ALL__
        if (factory == null) {
            try {
                Class.forName("com.siemens.mp.io.file.FileConnection");
                factory = Class.forName("api.file.SiemensFile");
                fsType = FS_SIEMENS;
            } catch (Throwable t) {
            }
        }
        if (factory == null) {
            try {
                Class.forName("com.motorola.io.FileConnection");
                factory = Class.forName("api.file.MotorolaFile");
                fsType = FS_MOTOROLA;
            } catch (Throwable t) {
            }
        }
        if (factory == null) {
            try {
                Class.forName("com.motorola.io.file.FileConnection");
                factory = Class.forName("api.file.Motorola1000File");
                fsType = FS_MOTOROLA1000;
            } catch (Throwable t) {
            }
        }
//#endif /* __ALL__ */
    }

    public static boolean isFs() {
        return factory != null;
    }

    public static Enumeration listRoots() throws IOException {
        if (registry == null) {
            registry = open(null);
        }

        return registry.getRoots();
    }

    public static File open(final String url) throws IOException {
        return open(url, Connector.READ);
    }

    public static File open(final String url, final int mode) throws IOException {
        if (factory == null) {
            throw new IllegalStateException("No file API");
        }
        try {
            final File instance = (File) factory.newInstance();
            if (url != null) {
                instance.fc = (StreamConnection) Connector.open(url, mode);
            }

            return instance;

        } catch (InstantiationException e) {
            throw new IllegalStateException("File API error: " + e.toString());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("File API error: " + e.toString());
        }
    }

    public InputStream openInputStream() throws IOException {
        return fc.openInputStream();
    }

    public OutputStream openOutputStream() throws IOException {
        return fc.openOutputStream();
    }

    public void close() throws IOException {
        if (fc != null) {
            try {
                fc.close();
            } finally {
                fc = null;
            }
        }
    }

    public void setHidden(boolean hidden)throws IOException {
        // support only in JSR-75 impl
    }

    public static boolean isBrokenTraversal() {
        return fsType == FS_SXG75 || fsType == FS_MOTOROLA || fsType == FS_MOTOROLA1000;
    }

    abstract Enumeration getRoots();
     
    public abstract Enumeration list() throws IOException;
    /** @deprecated may not work on all platforms */   
    public abstract Enumeration list(String pattern, boolean hidden) throws IOException;
    public abstract void create() throws IOException;
    public abstract void delete() throws IOException;
    public abstract void mkdir() throws IOException;
    public abstract void rename(String newName) throws IOException;
    public abstract long directorySize(boolean includeSubDirs) throws java.io.IOException;
    public abstract long fileSize() throws IOException;
    public abstract boolean exists();
    public abstract boolean isDirectory();
    public abstract String getURL();
    public abstract String getName();
    public abstract String getPath();
    public abstract void setFileConnection(String path) throws IOException;

    protected final void traverse(String path) throws IOException {
        // get current path
        String url = getURL();

        // handle broken paths
        if (PARENT_DIR.equals(path)) {
            path = "";
            url = url.substring(0, url.length() - 1);
            url = url.substring(0, url.lastIndexOf(PATH_SEPCHAR) + 1);
        }
//#ifdef __ALL__
          else if (fsType == FS_MOTOROLA || fsType == FS_MOTOROLA1000) {
            if (url.endsWith(PATH_SEPARATOR) && path.startsWith(PATH_SEPARATOR)) {
                url = url.substring(0, url.length() - 1);
            }
        }
//#endif

        // close existing connection
        try {
            close();
        } catch (IOException e) {
            // ignore
        }

        // open new connection
        try {
            fc = (StreamConnection) Connector.open(url + path, Connector.READ);
        } catch (IOException e) {
            throw new IOException(e.getMessage() + " [" + url + path + "]");
        }
    }

    public static boolean isDir(final String path) {
        return File.PATH_SEPCHAR == path.charAt(path.length() - 1) || File.PARENT_DIR.equals(path);
    }

    public static String encode(String path) {
        int idx = path.indexOf(' ');
        if (idx > -1) {
            final StringBuffer sb = new StringBuffer(path.length());
            int mark = 0;
            while (idx > -1) {
                sb.append(path.substring(mark, idx)).append("%20");
                mark = idx + 1;
                idx = path.indexOf(' ', mark);
            }
            sb.append(path.substring(mark));
            path = sb.toString();
        }
        return path;
    }

    public static String decode(String path) {
        int idx = path.indexOf("%20");
        if (idx > -1) {
            final StringBuffer sb = new StringBuffer(path.length());
            int mark = 0;
            while (idx > -1) {
                sb.append(path.substring(mark, idx)).append(' ');
                mark = idx + 3;
                idx = path.indexOf("%20", mark);
            }
            sb.append(path.substring(mark));
            path = sb.toString();
        }
        return path;
    }
}
