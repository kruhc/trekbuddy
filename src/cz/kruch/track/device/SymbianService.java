// @LICENSE@

package cz.kruch.track.device;

import javax.microedition.io.Connector;
import javax.microedition.io.SocketConnection;
import javax.microedition.io.StreamConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.DataInputStream;
import java.io.OutputStream;

/**
 * Designed to work for TarLoader and S60DeviceControl together with native service.
 * <p>
 * Service protocol packet is { 0xFF, 0xEB, 0x00, <tt>byte</tt> <i>action</i>, <tt>int</tt> <i>context param</i> }. Actions are
 * <ul>
 * <li>0x00 - reset inactivity timer (to prevent screensaver)
 * <li>0x01 - file open
 * <li>0x02 - file close
 * <li>0x03 - file read
 * <li>0x04 - file reset
 * <li>0x05 - file skip
 * </ul>
 * </p>
 */
public final class SymbianService {

    private static final String URL = "socket://127.0.0.1:20175";
    private static final byte[] PKT = new byte[8];

    /**
     * Opens networked stream.
     *
     * @param name file URL
     * @return instance of <code>InputStream</code>
     * @throws IOException if the connection to <b>TrekBuddyService</b> cannot be opened
     */
    public static InputStream openInputStream(String name) throws IOException {
        return new NetworkedInputStream(name);
    }

    /**
     * Creates "inactivity" control.
     *
     * @return instance of <code>TimerTask</code>, or <code>null</code>
     *         if it cannot be created - it does <b>not (!)</b> throw <code>IOException</code>
     *
     * @throws IOException
     */
    public static Inactivity openInactivity() throws IOException {
        return new Inactivity();
    }

    /**
     * Sends packet.
     *
     * @param output output stream
     * @param action protocol command
     * @param param command generic parameter
     *
     * @throws IOException if anything goes wrong
     */
    private static void sendPacket(final OutputStream output,
                                   final byte action, final int param) throws IOException {
        PKT[0] = (byte)0xFF;
        PKT[1] = (byte)0xEB;
        PKT[2] = (byte)0x00;
        PKT[3] = action;
        PKT[4] = (byte)(param >> 24);
        PKT[5] = (byte)(param >> 16);
        PKT[6] = (byte)(param >> 8);
        PKT[7] = (byte)(param);
        output.write(PKT);
        output.flush();
    }

    /**
     * Sends packet with extra data.
     *
     * @param output output stream
     * @param action protocol command
     * @param param command generic parameter
     * @param extra extra data
     *
     * @throws IOException if anything goes wrong
     */
    private static void sendPacketEx(final OutputStream output,
                                     final byte action, final int param,
                                     final byte[] extra) throws IOException {
        final byte[] large = new byte[8 + extra.length];
        large[0] = (byte)0xFF;
        large[1] = (byte)0xEB;
        large[2] = (byte)0x00;
        large[3] = action;
        large[4] = (byte)(param >> 24);
        large[5] = (byte)(param >> 16);
        large[6] = (byte)(param >> 8);
        large[7] = (byte)(param);
        System.arraycopy(extra, 0, large, 8, extra.length);
        output.write(large);
        output.flush();
    }

	private static StreamConnection openConnection(String url) throws IOException {
        final SocketConnection connection = (SocketConnection) Connector.open(url, Connector.READ_WRITE);
        connection.setSocketOption(SocketConnection.DELAY, 0);
//        connection.setSocketOption(SocketConnection.RCVBUF, 26280); // 18 * 1460
        return connection;
	}

	/**
     * Service helper for backlight control.
     */
    public static class Inactivity {
        private StreamConnection connection;
        private OutputStream output;

        Inactivity() throws IOException {
            try {
                this.connection = openConnection(URL);
                this.output = connection.openOutputStream();
            } catch (IOException e) {
                close();
                throw e;
            }
        }

        public void setLights(int value) throws IOException {
            sendPacket(output, (byte) 0x00, value);
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
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException e) {
                    // ignore
                }
                connection = null;
            }
        }
    }

    /**
     * Networked stream fast tar-ed maps.
     */
    public static final class NetworkedInputStream extends InputStream {
        private StreamConnection connection;
        private DataInputStream input;
        private OutputStream output;
        private final byte[] one, header;

        NetworkedInputStream(String name) throws IOException {
            // get UTF-8 file name
            final byte[] utf8name = name.getBytes("UTF-8");
            if (utf8name.length > 256) {
                throw new IllegalArgumentException("Filename too long");
            }

            // buffers
            this.one = new byte[1];
            this.header = new byte[4];

            // init communication
            try {
                // open I/O
                this.connection = openConnection(URL);
                this.output = connection.openOutputStream();
                this.input = connection.openDataInputStream();

                // open remote file
                sendPacketEx(output, (byte) 0x01, utf8name.length, utf8name);

            } catch (IOException e) {

                // cleanup
                destroy();

                // rethrow
                throw e; 
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
            destroy();
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

        private void destroy() {
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
    }
}
