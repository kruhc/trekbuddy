 // @LICENSE@

package api.file;

import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.Connection;
import java.util.Enumeration;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * File abstraction.
 *
 * @author kruhc@seznam.cz
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
    public static final int FS_CN1              = 6;

    public static int fsType; // 0 (= FS_UNKNOWN)

    private static Class factory;
    private static File registry;

    protected StreamConnection fc;

    public static void initialize(final boolean traverseBug) {
        try {
            Class.forName("javax.microedition.io.file.FileConnection");
            factory = Class.forName("api.file.Jsr75File");
//#ifndef __CN1__
            fsType = traverseBug ? FS_SXG75 : FS_JSR75;
//#else
            fsType = FS_CN1;
//#endif
        } catch (Throwable t) {
        }
//#if __ALL__ && !__SYMBIAN__
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
//#ifdef __RIM__
                ((net.rim.device.api.io.file.ExtendedFileConnection) instance.fc).setAutoEncryptionResolveMode(true);
//#endif
            }

            return instance;

        } catch (Exception e) {
            throw new IllegalStateException(getExceptionMessage("Open failed. ", e.toString(), url));
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
        return fsType == FS_SXG75 || fsType == FS_MOTOROLA || fsType == FS_MOTOROLA1000 /*|| fsType == FS_CN1*/;
    }

    public static boolean isOfType(final String filename, final String extension) {
        final String candidate = filename.toLowerCase();
//#if __SYMBIAN__ || __RIM__ || __ANDROID__ || __CN1__
        return candidate.endsWith(extension);
//#else
        if (candidate.endsWith(extension)) {
            return true;
        } else if (cz.kruch.track.TrackingMIDlet.iden) {
            return candidate.endsWith(extension + PATH_SEPARATOR);
        }
        return false;
//#endif
    }

    public static String idenFix(final String filename) {
//#if __SYMBIAN__ || __RIM__ || __ANDROID__ || __CN1__
        return filename;
//#else
        if (!cz.kruch.track.TrackingMIDlet.iden || !filename.endsWith(PATH_SEPARATOR)) {
            return filename;
        }

        return filename.substring(0, filename.length() - 1);
//#endif
    }

    public static void closeQuietly(final File file) {
        if (file != null) {
            try {
                file.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
        }
    }

    public static void closeQuietly(final Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
        }
    }

    public static void closeQuietly(final InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
        }
    }

    public static void closeQuietly(final OutputStream out) {
        if (out != null) {
            try {
                out.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
        }
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

    final void traverse(String path) throws IOException {

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
        } catch (Exception e) {
            throw new IllegalStateException(getExceptionMessage("Traverse failed. ", e.getMessage(), url + path));
        }
    }

    public static boolean isDir(final String path) {
//#ifndef __CN1__
        return File.PATH_SEPCHAR == path.charAt(path.length() - 1) || File.PARENT_DIR.equals(path);
//#else
        return File.PATH_SEPCHAR == path.charAt(path.length() - 1) || File.PARENT_DIR.equals(path) || javax.microedition.io.file.FileConnection.OS_PATH_SEPCHAR == path.charAt(path.length() - 1);
//#endif
    }

//#ifdef __RIM__

     public static String resolveEncrypted(String path) {
         if (path.endsWith(".rem")) {
             path = path.substring(0, path.length() - 4);
         }
         return path;
     }

//#endif

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

    private static String getExceptionMessage(final String intro,
                                              final String message,
                                              final String url) {
        final StringBuffer sb = new StringBuffer(64);
        sb.append(intro).append(message).append(" (").append(url).append(')');
        return sb.toString();        
    }
}
