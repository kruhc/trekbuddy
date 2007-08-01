// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.file;

import javax.microedition.io.file.FileSystemRegistry;
import javax.microedition.io.file.FileConnection;
import java.util.Enumeration;
import java.io.IOException;

final class Jsr75File extends File {

    Jsr75File() {
    }

    Enumeration getRoots() {
        return FileSystemRegistry.listRoots();
    }

    public Enumeration list() throws IOException {
        return ((FileConnection) fc).list();
    }

    public Enumeration list(String pattern, boolean hidden) throws IOException {
        return ((FileConnection) fc).list(pattern, hidden);
    }

    public void create() throws IOException {
        ((FileConnection) fc).create();
    }

    public void mkdir() throws IOException {
        ((FileConnection) fc).mkdir();
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

    public void setFileConnection(String path) throws IOException {
        if (fsType == FS_SXG75) {
            traverse(path);
        } else {
            ((FileConnection) fc).setFileConnection(path);
        }
    }
}
