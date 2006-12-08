// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.file;

import javax.microedition.io.Connection;
import java.util.Enumeration;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class File /*implements FileConnection*/ {
    public static final String FILE_SEPARATOR  = "/";

    private javax.microedition.io.file.FileConnection fc;

    public static Enumeration listRoots() {
        return javax.microedition.io.file.FileSystemRegistry.listRoots();
    }

    public File(Connection c) {
        this.fc = (javax.microedition.io.file.FileConnection) c;
    }

    public InputStream openInputStream() throws IOException {
        return fc.openInputStream();
    }

    public OutputStream openOutputStream() throws IOException {
        return fc.openOutputStream();
    }

    public Enumeration list() throws IOException {
        return fc.list();
    }

    public Enumeration list(String string, boolean b) throws IOException {
        return fc.list(string, b);
    }

    public void create() throws IOException {
        fc.create();
    }

    public void mkdir() throws IOException {
        fc.mkdir();
    }

    public boolean exists() {
        return fc.exists();
    }

    public boolean isDirectory() {
        return fc.isDirectory();
    }

    public void setFileConnection(String string) throws IOException {
        fc.setFileConnection(string);
    }

    public String getURL() {
        return fc.getURL();
    }

    public void close() throws IOException {
        fc.close();
        fc = null; // gc hint
    }
}
