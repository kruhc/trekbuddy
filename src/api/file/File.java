 /*
 * Copyright 2006-2007 Ales Pour <kruhc@seznam.cz>.
 * All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package api.file;

import javax.microedition.io.Connection;
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
    public static final String PATH_SEPARATOR   = "/";
    public static final char   PATH_SEPCHAR     = '/';
    public static final String PARENT_DIR       = "..";

    public static final int FS_UNKNOWN      = 0;
    public static final int FS_JSR75        = 1;
    public static final int FS_SIEMENS      = 2;
    public static final int FS_SXG75        = 3;
    public static final int FS_MOTOROLA     = 4;
    public static final int FS_MOTOROLA1000 = 5;

    public static int fsType = FS_UNKNOWN;

    private static Class factory;
    private static File registry;

    protected StreamConnection fc;

    public static void initialize(final boolean traverseBug) {
//#ifdef __JSR75__
        try {
            Class.forName("javax.microedition.io.file.FileConnection");
            fsType = traverseBug ? FS_SXG75 : FS_JSR75;
            factory = Class.forName("api.file.Jsr75File");
        } catch (Throwable t) {
        }
//#endif
//#ifdef __RIM__
        if (fsType == FS_UNKNOWN) { /* repeat for Blackberry */
            try {
                Class.forName("javax.microedition.io.file.FileConnection");
                fsType = traverseBug ? FS_SXG75 : FS_JSR75;
                factory = Class.forName("api.file.Jsr75File");
            } catch (Throwable t) {
            }
        }
//#endif
//#ifdef __S65__
        if (fsType == FS_UNKNOWN) {
            try {
                Class.forName("com.siemens.mp.io.file.FileConnection");
                fsType = FS_SIEMENS;
                factory = Class.forName("api.file.SiemensFile");
            } catch (Throwable t) {
            }
        }
//#endif
//#ifdef __A780__
        if (fsType == FS_UNKNOWN) {
            try {
                Class.forName("com.motorola.io.FileConnection");
                fsType = FS_MOTOROLA;
                factory = Class.forName("api.file.MotorolaFile");
            } catch (Throwable t) {
            }
        }
//#endif
//#ifdef __A1000__
        if (fsType == FS_UNKNOWN) {
            try {
                Class.forName("com.motorola.io.file.FileConnection");
                fsType = FS_MOTOROLA1000;
                factory = Class.forName("api.file.Motorola1000File");
            } catch (Throwable t) {
            }
        }
//#endif
    }

    public static Enumeration listRoots() {
        if (registry == null) {
            registry = open(null);
        }

        return registry.getRoots();
    }

    public static File open(Connection c) {
        try {
            if (factory == null) {
                throw new IllegalStateException("No file API");
            }

            File instance = (File) factory.newInstance();
            instance.fc = (StreamConnection) c;

            return instance;

        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("File API error: " + t.toString());
        }
    }

    public final InputStream openInputStream() throws IOException {
        return fc.openInputStream();
    }

    public final OutputStream openOutputStream() throws IOException {
        return fc.openOutputStream();
    }

    public final void close() throws IOException {
        fc.close();
        fc = null;
    }

    public final boolean isBrokenTraversal() {
        return fsType == FS_SXG75 || fsType == FS_MOTOROLA || fsType == FS_MOTOROLA1000;
    }

    abstract Enumeration getRoots();
    public abstract Enumeration list() throws IOException;
    public abstract Enumeration list(String pattern, boolean hidden) throws IOException;
    public abstract void create() throws IOException;
    public abstract void mkdir() throws IOException;
    public abstract long fileSize() throws java.io.IOException;
    public abstract boolean exists();
    public abstract boolean isDirectory();
    public abstract String getURL();
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
//#ifdef __A780__
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

    public static boolean isDir(String path) {
        return File.PATH_SEPCHAR == path.charAt(path.length() - 1);
    }
}
