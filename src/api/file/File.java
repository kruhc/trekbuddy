// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package api.file;

import javax.microedition.io.Connection;
import java.util.Enumeration;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class File {
    public static final String FILE_SEPARATOR  = "/";

//#ifdef __JSR75__
    private javax.microedition.io.file.FileConnection fc;
//#elif __S65__
    private com.siemens.mp.io.file.FileConnection fc_S65;
//#elif __A780__
    private com.motorola.io.FileConnection fc_a780;
//#endif

    public static Enumeration listRoots() {
//#ifdef __JSR75__
        if (true) {
            return javax.microedition.io.file.FileSystemRegistry.listRoots();
        }
//#elif __S65__
        if (cz.kruch.track.TrackingMIDlet.isS65()) {
            return com.siemens.mp.io.file.FileSystemRegistry.listRoots();
        }
//#elif __A780__
        if (cz.kruch.track.TrackingMIDlet.isA780()) {
            return new StringEnumeration(com.motorola.io.FileSystemRegistry.listRoots());
        }
//#endif
        throw new Error("Corrupted build: listRoots()");
    }

    public File(Connection c) {
//#ifdef __JSR75__
        this.fc = (javax.microedition.io.file.FileConnection) c;
//#elif __S65__
        if (cz.kruch.track.TrackingMIDlet.isS65()) {
            this.fc_S65 = (com.siemens.mp.io.file.FileConnection) c;
        }
//#elif __A780__
        if (cz.kruch.track.TrackingMIDlet.isA780()) {
            this.fc_a780 = (com.motorola.io.FileConnection) c;
        }
//#endif
    }

    public InputStream openInputStream() throws IOException {
//#ifdef __JSR75__
        if (true) {
            return fc.openInputStream();
        }
//#elif __S65__
        if (cz.kruch.track.TrackingMIDlet.isS65()) {
            return fc_S65.openInputStream();
        }
//#elif __A780__
        if (cz.kruch.track.TrackingMIDlet.isA780()) {
            return fc_a780.openInputStream();
        }
//#endif
        throw new Error("Corrupted build: openInputStream()");
    }

    public OutputStream openOutputStream() throws IOException {
//#ifdef __JSR75__
        if (true) {
            return fc.openOutputStream();
        }
//#elif __S65__
        if (cz.kruch.track.TrackingMIDlet.isS65()) {
            return fc_S65.openOutputStream();
        }
//#elif __A780__
        if (cz.kruch.track.TrackingMIDlet.isA780()) {
            return fc_a780.openOutputStream();
        }
//#endif
        throw new Error("Corrupted build: openOutputStream()");
    }

    public Enumeration list() throws IOException {
//#ifdef __JSR75__
        if (true) {
            return fc.list();
        }
//#elif __S65__
        if (cz.kruch.track.TrackingMIDlet.isS65()) {
            return fc_S65.list();
        }
//#elif __A780__
        if (cz.kruch.track.TrackingMIDlet.isA780()) {
            return new StringEnumeration(fc_a780.list());
        }
//#endif
        throw new Error("Corrupted build: list()");
    }

    public Enumeration list(String string, boolean b) throws IOException {
//#ifdef __JSR75__
        if (true) {
            return fc.list();
        }
//#elif __S65__
        if (cz.kruch.track.TrackingMIDlet.isS65()) {
            return fc_S65.list(string, b);
        }
//#elif __A780__
        if (cz.kruch.track.TrackingMIDlet.isA780()) {
            return new StringEnumeration(fc_a780.list(), string, b);
        }
//#endif
        throw new Error("Corrupted build: list(String, boolean)");
    }

    public void create() throws IOException {
//#ifdef __JSR75__
        fc.create();
//#elif __S65__
        if (cz.kruch.track.TrackingMIDlet.isS65()) {
            fc_S65.create();
        }
//#elif __A780__
        if (cz.kruch.track.TrackingMIDlet.isA780()) {
            fc_a780.create();
        }
//#endif
    }

    public void mkdir() throws IOException {
//#ifdef __JSR75__
        fc.mkdir();
//#elif __S65__
        if (cz.kruch.track.TrackingMIDlet.isS65()) {
            fc_S65.mkdir();
        }
//#elif __A780__
        if (cz.kruch.track.TrackingMIDlet.isA780()) {
            fc_a780.mkdir();
        }
//#endif
    }

    public boolean exists() {
//#ifdef __JSR75__
        if (true) {
            return fc.exists();
        }
//#elif __S65__
        if (cz.kruch.track.TrackingMIDlet.isS65()) {
            return fc_S65.exists();
        }
//#elif __A780__
        if (cz.kruch.track.TrackingMIDlet.isA780()) {
            return fc_a780.exists();
        }
//#endif
        throw new Error("Corrupted build: exists()");
    }

    public boolean isDirectory() {
//#ifdef __JSR75__
        if (true) {
            return fc.isDirectory();
        }
//#elif __S65__
        if (cz.kruch.track.TrackingMIDlet.isS65()) {
            return fc_S65.isDirectory();
        }
//#elif __A780__
        if (cz.kruch.track.TrackingMIDlet.isA780()) {
            return fc_a780.isDirectory();
        }
//#endif
        throw new Error("Corrupted build: isDirectory()");
    }

    public void setFileConnection(String string) throws IOException {
//#ifdef __JSR75__
        fc.setFileConnection(string);
//#elif __S65__
        if (cz.kruch.track.TrackingMIDlet.isS65()) {
            fc_S65.setFileConnection(string);
        }
//#elif __A780__
        if (cz.kruch.track.TrackingMIDlet.isA780()) {
            throw new Error("Unsupported operation: setFileConnection");
        }
//#endif
    }

    public String getURL() {
//#ifdef __JSR75__
        if (true) {
            return fc.getURL();
        }
//#elif __S65__
        if (cz.kruch.track.TrackingMIDlet.isS65()) {
            return fc_S65.getURL();
        }
//#elif __A780__
        if (cz.kruch.track.TrackingMIDlet.isA780()) {
            return fc_a780.getURL();
        }
//#endif
        throw new Error("Corrupted build: getURL()");
    }

    public void close() throws IOException {
//#ifdef __JSR75__
        fc.close();
        fc = null;
//#elif __S65__
        if (cz.kruch.track.TrackingMIDlet.isS65()) {
            fc_S65.close();
            fc_S65 = null;
        }
//#elif __A780__
        if (cz.kruch.track.TrackingMIDlet.isA780()) {
            fc_a780.close();
            fc_a780 = null;
        }
//#endif
    }

//#ifdef __A780__

    static class StringEnumeration implements Enumeration {
        private String[] list;
        private int offset;

        public StringEnumeration(String[] list) {
            this.list = list;
            this.offset = 0;
        }

        public StringEnumeration(String[] list, String pattern, boolean unknown) {
            this(list);
            // TODO apply pattern
        }

        public boolean hasMoreElements() {
            return offset < list.length;
        }

        public Object nextElement() {
            if (offset >= list.length) {
                throw new java.util.NoSuchElementException();
            }

            return removeDir(list[offset++]);
        }
    }

    private static String removeDir(String f) {
        // only if / is found and is not the end
        while (f.indexOf('/') >= 0 && f.indexOf('/') != (f.length() - 1)) {
            f = f.substring(f.indexOf('/') + 1);
        }

        return f;
    }

//#endif

}
