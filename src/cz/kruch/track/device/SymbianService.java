// @LICENSE@

package cz.kruch.track.device;

import javax.microedition.io.Connector;
import javax.microedition.io.SocketConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

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

	private static SocketConnection openConnection(String url) throws IOException {
		SocketConnection connection = (SocketConnection) Connector.open(url, Connector.READ_WRITE);
		connection.setSocketOption(SocketConnection.DELAY, 0);
		return connection;
	}

	/**
     * Service helper for backlight control. Misuse <code>TimerTask</code>.
     */
    public static class Inactivity {
        private SocketConnection connection;
        private DataOutputStream output;

        Inactivity() throws IOException {
            try {
                this.connection = openConnection(URL);
                this.output = new DataOutputStream(connection.openOutputStream());
            } catch (IOException e) {
                close();
                throw e;
            }
        }

        public void setLights(int value) {
            try {
                sendPacket(output, (byte) 0x00, value);
            } catch (Exception e) {
                // ignore
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
        private SocketConnection connection;
        private DataInputStream input;
        private DataOutputStream output;
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
                this.input = new DataInputStream(connection.openInputStream());
                this.output = new DataOutputStream(connection.openOutputStream());

                // open remote file
                sendPacket(output, (byte) 0x01, utf8name.length);
                output.write(utf8name);
                output.flush();

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
