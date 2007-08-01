// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.file;

import java.util.Enumeration;
import java.io.IOException;

final class SiemensFile extends File {

    SiemensFile() {
    }

    Enumeration getRoots() {
        return com.siemens.mp.io.file.FileSystemRegistry.listRoots();
    }

    public Enumeration list() throws IOException {
        return ((com.siemens.mp.io.file.FileConnection) fc).list();
    }

    public Enumeration list(String string, boolean b) throws IOException {
        return ((com.siemens.mp.io.file.FileConnection) fc).list(string, b);
    }

    public void create() throws IOException {
        ((com.siemens.mp.io.file.FileConnection) fc).create();
    }

    public void mkdir() throws IOException {
        ((com.siemens.mp.io.file.FileConnection) fc).mkdir();
    }

    public long fileSize() throws IOException {
        return ((com.siemens.mp.io.file.FileConnection) fc).fileSize();
    }

    public boolean exists() {
        return ((com.siemens.mp.io.file.FileConnection) fc).exists();
    }

    public boolean isDirectory() {
        return ((com.siemens.mp.io.file.FileConnection) fc).isDirectory();
    }

    public String getURL() {
        return ((com.siemens.mp.io.file.FileConnection) fc).getURL();
    }

    public void setFileConnection(String path) throws IOException {
        ((com.siemens.mp.io.file.FileConnection) fc).setFileConnection(path);
    }
}
