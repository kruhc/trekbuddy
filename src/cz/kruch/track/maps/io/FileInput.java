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

package cz.kruch.track.maps.io;

import api.file.File;

import javax.microedition.io.Connector;
import java.io.InputStream;
import java.io.IOException;

/**
 * File input helper.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class FileInput {
    private String url;
    private api.file.File file;
    private InputStream in;

    public FileInput(String url) {
        this.url = url;
    }

    public InputStream _getInputStream() throws IOException {
        file = File.open(Connector.open(url, Connector.READ));
        if (!file.exists()) {
            throw new IOException("File does not exist: " + url);
        }
        in = file.openInputStream();

        return in;
    }

    public void close() throws IOException {
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                // ignore
            }
            in = null;
        }
        if (file != null) {
            try {
                file.close();
            } catch (IOException e) {
                // ignore
            }
            file = null;
        }
        url = null;
    }
}