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

package cz.kruch.track.device;

import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

/**
 * Designed to work for TarLoader and S60DeviceControl together with native service.
 * <p>
 * Service protocol packet is { 0xFF, 0xEB, 0x00, <tt>byte</tt> <i>action</i>, <tt>integer</tt> <i>int context param</i> }. Actions are
 * <ul>
 * <li>0x00 - reset inactivity timer (prevents screensaver to pop up)
 * <li>0x01 - file open
 * <li>0x02 - file close
 * <li>0x03 - file read
 * <li>0x04 - file reset
 * <li>0x05 - file skip
 * </ul>
 * </p>
 */
public final class SymbianService {

    /**
     * Opens "remote" stream.
     *
     * @param name file name (JSR-75 syntax)
     * @return instance of <code>InputStream</code>, or <code>null</code>
     *         if it cannot be opened - it does <b>not (!)</b> throw <code>IOException</code>
     */
    public static InputStream openInputStream(String name) {
        try {
            return new SymbianInputStream(name);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Creates "inactivity" control.
     *
     * @return instance of <code>TimerTask</code>, or <code>null</code>
     *         if it cannot be created - it does <b>not (!)</b> throw <code>IOException</code> 
     */
    public static Inactivity openInactivity() {
        try {
            return new Inactivity();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Sends packet.
     *
     * @param output output stream
     * @param action protocol command
     * @param param command generic parameter
     * @throws IOException if anything goes wrong
     */
    private static void sendPacket(final DataOutputStream output,
                                   final byte action, final int param) throws IOException {
        output.writeByte(0xFF);
        output.writeByte(0xEB);
        output.writeByte(0x00);
        output.writeByte(action);
        output.writeInt(param);
        output.flush();
    }

    /**
     * Service helper for backlight control. Misuse <code>TimerTasl</code>.
     */
    public static class Inactivity {
        private DataOutputStream output;

        public Inactivity() {
        }

        public void setLights(int value) {
            try {
                if (output == null) {
                    output = new DataOutputStream(Connector.openOutputStream("socket://127.0.0.1:20175"));
                }
                sendPacket(output, (byte) 0x00, value);
            } catch (Exception e) {
                try {
                    output.close();
                } catch (Exception exc) {
                    output = null;
                }
            }
        }

        public void close() {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    // ignore
                }
                output = null;
            }
        }
    }

    /**
     * Service helper for fast tar-ed maps.
     */
    private static class SymbianInputStream extends InputStream {
        private StreamConnection connection;
        private DataInputStream input;
        private DataOutputStream output;
        private byte[] one;
        private byte[] header;

        public SymbianInputStream(String name) throws Exception {
            this.connection = (StreamConnection) Connector.open("socket://127.0.0.1:20175", Connector.READ_WRITE);
            this.one = new byte[1];
            this.header = new byte[4];
            try {
                this.input = new DataInputStream(connection.openInputStream());
                this.output = new DataOutputStream(connection.openOutputStream());
                doFileOpen(output, name);
            } catch (Exception e) {
                closeStreams();
                throw e; // rethrow
            }
        }

        public int read() throws IOException {
            return read(one, 0, 1);
        }

        public int read(byte[] b) throws IOException {
            return read(b, 0, b.length);
        }

        public int read(byte[] b, int off, int len) throws IOException {
            // local refs
            final DataInputStream input = this.input;
            final byte[] header = this.header;

            // send file read request
            sendPacket(output, (byte) 0x03, len);

            // read response header
            input.readFully(header);

            // check header
            if (header[0] == (byte)0xFF && header[1] == (byte)0xEB && header[2] == (byte)0x01 && header[3] == (byte)0x03) {

                // read data response size
                final int n = len = input.readInt();

                // read data
                while (len > 0) {
                    final int c = input.read(b, off, len);
                    if (c != -1) {
                        len -= c;
                        off += c;
                    } else {
                        throw new IOException("Unexpected end of stream");
                    }
                }

                return n; 
            }

            // protocol error
            throw new IOException("Invalid service response");
        }

        public long skip(long n) throws IOException {
            sendPacket(output, (byte) 0x05, (int) n);
            return n;
        }

        public int available() throws IOException {
            return 0; // unknown
        }

        public void close() throws IOException {
            try {
                sendPacket(output, (byte) 0x02, 0);
            } catch (Exception e) {
                // ignore
            }
            closeStreams();
        }

        public synchronized void mark(int readlimit) {
            // any limit is supported ;-)
        }

        public boolean markSupported() {
            return true;
        }

        public synchronized void reset() throws IOException {
            sendPacket(output, (byte) 0x04, 0);
        }

        private void closeStreams() {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    // ignore
                }
                output = null;
            }
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    // ignore
                }
                input = null;
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException e) {
                    // ignore
                }
                connection = null;
            }
        }

        private static void doFileOpen(final DataOutputStream output,
                                       final String name) throws IOException {
            final byte[] encoded = name.getBytes("UTF-8");
            if (encoded.length > 256) {
                throw new IllegalArgumentException("Filename too long");
            }
            sendPacket(output, (byte) 0x01, encoded.length);
            output.write(encoded);
            output.flush();
        }
    }
}
