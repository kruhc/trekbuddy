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

import javax.microedition.io.file.FileSystemRegistry;
import javax.microedition.io.file.FileConnection;
import java.util.Enumeration;
import java.io.IOException;

/**
 * JSR-75 file implementation.
 *  
 * @author Ales Pour <kruhc@seznam.cz>
 */
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

    public void delete() throws IOException {
        ((FileConnection) fc).delete();
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
