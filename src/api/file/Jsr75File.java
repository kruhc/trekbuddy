// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.file;

import java.util.Enumeration;
import java.io.IOException;

final class Jsr75File extends File {
    Enumeration getRoots() {
        return javax.microedition.io.file.FileSystemRegistry.listRoots();
    }

    public Enumeration list() throws IOException {
        return ((javax.microedition.io.file.FileConnection) fc).list();
    }

    public Enumeration list(String pattern, boolean hidden) throws IOException {
        return ((javax.microedition.io.file.FileConnection) fc).list(pattern, hidden);
    }

    public void create() throws IOException {
        ((javax.microedition.io.file.FileConnection) fc).create();
    }

    public void mkdir() throws IOException {
        ((javax.microedition.io.file.FileConnection) fc).mkdir();
    }

    public long fileSize() throws IOException {
        return ((javax.microedition.io.file.FileConnection) fc).fileSize();
    }

    public boolean exists() {
        return ((javax.microedition.io.file.FileConnection) fc).exists();
    }

    public boolean isDirectory() {
        return ((javax.microedition.io.file.FileConnection) fc).isDirectory();
    }

    public String getURL() {
        return ((javax.microedition.io.file.FileConnection) fc).getURL();
    }

    public void setFileConnection(String path) throws IOException {
        if (fsType == FS_SXG75) {
            traverse(path);
        } else {
            ((javax.microedition.io.file.FileConnection) fc).setFileConnection(path);
        }
    }
}
