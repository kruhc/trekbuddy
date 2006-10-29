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

/*
    public boolean isOpen() {
        return fc.isOpen();
    }
*/
    public InputStream openInputStream() throws IOException {
        return fc.openInputStream();
    }
/*
    public DataInputStream openDataInputStream() throws IOException {
        return fc.openDataInputStream();
    }
*/
    public OutputStream openOutputStream() throws IOException {
        return fc.openOutputStream();
    }
/*
    public DataOutputStream openDataOutputStream() throws IOException {
        return fc.openDataOutputStream();
    }

    public OutputStream openOutputStream(long l) throws IOException {
        return fc.openOutputStream(l);
    }

    public long totalSize() {
        return fc.totalSize();
    }

    public long availableSize() {
        return fc.availableSize();
    }

    public long usedSize() {
        return fc.usedSize();
    }

    public long directorySize(boolean b) throws IOException {
        return fc.directorySize(b);
    }

    public long fileSize() throws IOException {
        return fc.fileSize();
    }

    public boolean canRead() {
        return fc.canRead();
    }

    public boolean canWrite() {
        return fc.canWrite();
    }

    public boolean isHidden() {
        return fc.isHidden();
    }

    public void setReadable(boolean b) throws IOException {
        fc.setReadable(b);
    }

    public void setWritable(boolean b) throws IOException {
        fc.setWritable(b);
    }

    public void setHidden(boolean b) throws IOException {
        fc.setHidden(b);
    }
*/
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
/*
    public void delete() throws IOException {
        fc.delete();
    }

    public void rename(String string) throws IOException {
        fc.rename(string);
    }

    public void truncate(long l) throws IOException {
        fc.truncate(l);
    }
*/
    public void setFileConnection(String string) throws IOException {
        fc.setFileConnection(string);
    }
/*
    public String getName() {
        return fc.getName();
    }

    public String getPath() {
        return fc.getPath();
    }
*/
    public String getURL() {
        return fc.getURL();
    }
/*
    public long lastModified() {
        return fc.lastModified();
    }
*/
    public void close() throws IOException {
        fc.close();
        fc = null; // gc hint
    }
}
