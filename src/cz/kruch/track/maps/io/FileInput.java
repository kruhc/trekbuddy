// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps.io;

import api.file.File;

import javax.microedition.io.Connector;
import java.io.InputStream;
import java.io.IOException;

/**
 * File input helper class.
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