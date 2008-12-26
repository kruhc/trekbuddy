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

import java.util.Enumeration;
import java.io.IOException;

/**
 * Motorola 1000 File API implementation
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class Motorola1000File extends File {

    Motorola1000File() {
    }

    Enumeration getRoots() {
        return com.motorola.io.file.FileSystemRegistry.listRoots();
    }

    public Enumeration list() throws IOException {
        return ((com.motorola.io.file.FileConnection) fc).list();
    }

    public Enumeration list(String pattern, boolean hidden) throws IOException {
        return ((com.motorola.io.file.FileConnection) fc).list(pattern, hidden);
    }

    public void create() throws IOException {
        ((com.motorola.io.file.FileConnection) fc).create();
    }

    public void delete() throws IOException {
        ((com.motorola.io.file.FileConnection) fc).delete();
    }

    public void mkdir() throws IOException {
        ((com.motorola.io.file.FileConnection) fc).mkdir();
    }

    public void rename(String newName) throws IOException {
        ((com.motorola.io.file.FileConnection) fc).rename(newName);
    }

    public long fileSize() throws IOException {
        return ((com.motorola.io.file.FileConnection) fc).fileSize();
    }

    public boolean exists() {
        return ((com.motorola.io.file.FileConnection) fc).exists();
    }

    public boolean isDirectory() {
        return ((com.motorola.io.file.FileConnection) fc).isDirectory();
    }

    public String getURL() {
        return ((com.motorola.io.file.FileConnection) fc).getURL();
    }

    public String getPath() {
        return ((com.motorola.io.file.FileConnection) fc).getPath();
    }

    public void setFileConnection(String path) throws IOException {
        traverse(path);
    }
}
