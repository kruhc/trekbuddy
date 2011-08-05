// @LICENSE@

package cz.kruch.track.io;

import cz.kruch.track.util.CharArrayTokenizer;

import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Line reading class for text files (calibration files, NMEA logs, ...).
 *
 * @author kruhc@seznam.cz
 */
public final class LineReader extends InputStreamReader {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("LineReader");
//#endif

    public static final String EMPTY_LINE = "";

    private static final int MAX_LINE_LENGTH    = 256;
    private static final int DEFAULT_BUFF_SIZE  = 1024;

    private CharArrayTokenizer.Token token;
    private char[] buffer;
    private int count;
    private int position;

    public LineReader(InputStream in, int buffSize) {
        super(in);
        this.token = new CharArrayTokenizer.Token();
        this.buffer = new char[buffSize];
        this.token.array = buffer;
    }

    public LineReader(InputStream in) {
        this(in, DEFAULT_BUFF_SIZE);
    }

    public String readLine(boolean ignoreLF) throws IOException {
        final CharArrayTokenizer.Token result = readToken(ignoreLF);
        if (result != null) {
            if (!result.isEmpty()) {
                return result.toString().trim();
            }
            return EMPTY_LINE;
        }
        return null;
    }

    public CharArrayTokenizer.Token readToken(final boolean ignoreLF) throws IOException {
        if (count == -1) {
            return null;
        }

        final char[] buffer = this.buffer;
        final int buffSize = buffer.length;

        /* reaching end of buffer - shift remaining bytes and refill */
        if (position > buffSize - MAX_LINE_LENGTH) {
            System.arraycopy(buffer, position, buffer, 0, count - position);
            count -= position;
            position = 0;
        }

        int count = this.count;
        int offset = this.position;
        int chars = 0;

        while (offset < buffSize) {
            final int c;
            if (offset < count) {
                c = buffer[offset++];
            } else {
                final int _count = read(buffer, offset, buffSize - offset);
                if (_count == -1) {
                    this.count = -1;
                    if (chars == 0) {
                        return null;
                    } else {
                        break;
                    }
//                } else if (_count == 0) {
//                    throw new RuntimeException("reader.read([])=0");
                } else {
                    this.count += _count;
                    count += _count;
                    c = buffer[offset++];
                }
            }

            if (c >= 0x20) { // most common situation
                chars++;
            } else if (c == '\r') { // '\n' should follow
            } else if (c == '\n') { // end of line
                break;
            }
            /* TODO other control chars ignored? */
        }

        if (offset < buffSize) {

            // local ref
            final CharArrayTokenizer.Token token = this.token;

            // prepare token
            token.begin = position;
            token.length = chars;

            // update in-buffer position
            this.position = offset;

            return token;
        }

        throw new IllegalStateException("Line length > " + MAX_LINE_LENGTH);
    }
}
