// @LICENSE@

package cz.kruch.track.util;

import java.util.NoSuchElementException;

/**
 * Tokenizer for char arrays.
 *
 * @author kruhc@seznam.cz
 */
public final class CharArrayTokenizer {
    public static final char[] DEFAULT_DELIMS = { ',' };

    private char[] array;
    private char[] delimiters;
    private char delimiter1, delimiter2;
    private boolean returnDelim;

    private int position;
    private int end;
    private int dl;

    private final Token token;

    public CharArrayTokenizer() {
        this.token = new Token();
    }

    public void init(final Token token, final boolean returnDelim) {
        this.init(token, DEFAULT_DELIMS, returnDelim);
    }

    public void init(final char[] array, final int length, final boolean returnDelim) {
        this.init(array, DEFAULT_DELIMS, returnDelim);
        /* set end explicitly */
        this.end = length;
    }

    public void init(final Token token, final char[] delimiters, final boolean returnDelim) {
        this.init(token.array, delimiters, returnDelim);
        /* set start and end explicitly */
        this.position = token.begin;
        this.end = token.begin + token.length;
    }

    public void init(final char[] array, final int length, final char[] delimiters, final boolean returnDelim) {
        this.init(array, delimiters, returnDelim);
        /* set end explicitly */
        this.end = length;
    }

    public void init(final String s, final char[] delimiters, final boolean returnDelim) {
        final int sLength = s.length();
        /* if current array is not big enough, just release it */
        if (this.array != null && this.array.length < sLength) {
            this.array = null;
        }
        /* allocate new array if needed */
        if (this.array == null) {
            this.array = new char[sLength + sLength >> 1];
        }
        s.getChars(0, sLength, this.array, 0);
        this.delimiters = delimiters;
        this.returnDelim = returnDelim;
        this.position = 0;
        this.end = sLength;
        this.dl = delimiters.length;
    }

    private void init(final char[] array, final char[] delimiters, final boolean returnDelim) {
        /* gc hint */
        this.array = null;
        this.delimiters = null;
        /* init */
        this.array = array;
        this.delimiters = delimiters;
        switch (delimiters.length) {
            case 1: {
                this.delimiter1 = delimiters[0];
                this.delimiter2 = 0;
            } break;
            case 2: {
                this.delimiter1 = delimiters[0];
                this.delimiter2 = delimiters[1];
            } break;
        }
        this.returnDelim = returnDelim;
        this.position = 0;
        this.end = array.length;
        this.dl = delimiters.length;
    }

    public boolean hasMoreTokens() {
        return position < end;
    }

    public int nextInt() {
        return parseInt(next());
    }

    public int nextInt2() {
        return parseInt(next2());
    }

    public double nextDouble() {
        return parseDouble(next());
    }

    public double nextDouble2() {
        return parseDouble(next2());
    }

    public String nextTrim() {
        return next().toString().trim();
    }

    public Token next() {
        // local ref
        final int end = this.end;

        if (position < end) {  /* == hasMoreTokens() */
            // local refs
            final Token token = this.token;
            final char[] array = this.array;

            // clear flag
            token.isDelimiter = false;

            // token beginning
            int begin = position;

            if (isDelim(array[begin])) { // TODO should be 'while'
                // step fwd
                begin++;
                // delims wanted?
                if (returnDelim) {
                    // save position
                    position = begin;
                    // set delimiter flag
                    token.isDelimiter = true;
                    // return
                    return token;
                }
                // check boundary
                if (begin == end) {
                    throw new NoSuchElementException();
                }
            }

            int offset = begin;
            while (offset < end) {
                if (isDelim(array[offset]))
                    break;
                offset++;
            }

            // update position
            position = offset;

            // fill token
            token.init(array, begin, offset - begin);

            return token;
        }

        throw new NoSuchElementException();
    }

    public Token next2() {
        // local ref
        final int end = this.end;

        if (position < end) {  /* == hasMoreTokens() */
            // local refs
            final Token token = this.token;
            final char[] array = this.array;
            final char delimiter1 = this.delimiter1;
            final char delimiter2 = this.delimiter2;

            // clear flag
            token.isDelimiter = false;

            // token beginning
            int begin = position;
            char ch = array[begin];

            if (ch == delimiter1 || ch == delimiter2) { // TODO should be 'while'
                // step fwd
                begin++;
                // delims wanted?
                if (returnDelim) {
                    // save position
                    position = begin;
                    // set delimiter flag
                    token.isDelimiter = true;
                    // return
                    return token;
                }
                // check boundary
                if (begin == end) {
                    throw new NoSuchElementException();
                }
            }

            int offset = begin;
            while (offset < end) {
                ch = array[offset];
                if (ch == delimiter1 || ch == delimiter2)
                    break;
                offset++;
            }

            // update position
            position = offset;

            // fill token
            token.init(array, begin, offset - begin);

            return token;
        }

        throw new NoSuchElementException();
    }

    private boolean isDelim(final char c) {
        final char[] delimiters = this.delimiters;
        for (int i = dl; --i >= 0; ) {
            if (delimiters[i] == c) {
                return true;
            }
        }

        return false;
    }

    public static int parseInt(final CharArrayTokenizer.Token token) {
        return parseInt(token.array, token.begin, token.length);
    }

    public static int parseInt(final char[] value, int offset, final int length) {
        if (length == 0) {
            throw new NumberFormatException("No input");
        }

        final int end = offset + length;
        int result = 0; // TODO is this correct initial value???
        int sign = 1;

        while (offset < end) {
            final char ch = value[offset++];
/* too slow
            int digit = Character.digit(ch, 10);
*/
            int digit = -1;
            if (ch >= '0' && ch <= '9') {
                digit = ch - '0';
            }
            if (digit > -1) {
                result *= 10;
                result += digit;
            } else if (ch == ' ' || ch == '+') {
                // ignore whitespace and leading + sign
            } else if (ch == '-') {
                sign = -1;
            } else if (ch == '.') {
                break;
            } else {
                throw new NumberFormatException("Not a digit: " + ch);
            }
        }

        return result * sign;
    }

    public static float parseFloat(final CharArrayTokenizer.Token token) {
        return (float) parseDouble(token.array, token.begin, token.length);
    }

    public static double parseDouble(final CharArrayTokenizer.Token token) {
        return parseDouble(token.array, token.begin, token.length);
    }

    public static double parseDouble(final char[] value, int offset, final int length) {
        if (length == 0) {
            throw new NumberFormatException("No input");
        }

        final int end = offset + length;
        long decSeen = 0;
        double result = 0D; // TODO is this correct initial value???
        int sign = 1;

        while (offset < end) {
            final char ch = value[offset++];
            if (ch == '.') {
                decSeen = 10;
            } else {
/* too slow
                int idigit = Character.digit(ch, 10);
*/
                if (ch >= '0' && ch <= '9') {
                    final double fdigit = ch - '0';
                    if (decSeen > 0) {
                        result += (fdigit / decSeen);
                        decSeen *= 10;
                    } else {
                        result *= 10D;
                        result += fdigit;
                    }
                } else if (ch == ' ' || ch == '+') {
                    // ignore whitespace and leading + sign
                } else if (ch == '-') {
                    sign = -1;
                } else {
                    throw new NumberFormatException("Not a digit: " + ch);
                }
            }
        }

        return result * sign;
    }

    public static final class Token {
        public char[] array;
        public int begin;
        public int length;
        public boolean isDelimiter;

        public Token() {
        }

        public void init(final char[] array, final int begin, final int length) {
            this.array = null; // gc hint
            this.array = array;
            this.begin = begin;
            this.length = length;
            this.isDelimiter = false;
        }

        public boolean startsWith(final char c) {
            final char[] array = this.array;
            final int begin = this.begin;
            final int length = this.length;
            int offset = 0;

            while (offset < length) {
                final char b = array[begin + offset];
                if (b != ' ') {
                    return b == c;
                }
                offset++;
            }

            return false;
        }

        public void skipNonDigits() {
            final char[] array = this.array;
            final int end = begin + length;
            int offset = begin;
            while (offset < end) {
                final char c = array[offset];
                if (c >= '0' && c <= '9') {
                    break;
                }
                offset++;
            }
            length = end - offset;
            begin = offset;
        }

        public void trim() {
            final char[] array = this.array;
            int end = begin + length;
            while (end > begin) {
                if (array[end] != ' ') {
                    break;
                }
                end--;
            }
            int offset = begin;
            while (offset < end) {
                if (array[offset] != ' ') {
                    break;
                }
                offset++;
            }
            begin = offset;
            length = end - offset;
        }

        public boolean startsWith(final char[] s) {
            int sl = s.length;
            if (sl <= length) {
                final char[] array = this.array;
                int affset = this.begin;
                int offset = 0;
                boolean cond = true;

                while (--sl >= 0) {
                    if (array[affset] != s[offset]) {
                        cond = false; break;
                    }
                    affset++;
                    offset++;
                }

                return cond;
            }

            return false;
        }

        public boolean endsWith(final char[] s) {
            final int tl = this.length;
            final int sl = s.length;
            if (sl <= tl) {
                final char[] array = this.array;
                int affset = this.begin + tl - 1;
                int offset = sl - 1;
                boolean cond = true;

                while (offset >= 0) {
                    if (array[affset] != s[offset]) {
                        cond = false; break;
                    }
                    affset--;
                    offset--;
                }

                return cond;
            }

            return false;
        }

        /** @deprecated */
        public boolean startsWith(final String s) {
            int sl = s.length();
            if (sl > length) {
                return false;
            }

            final char[] array = this.array;
            int affset = this.begin;
            int offset = 0;
            boolean cond = true;

            while (--sl >= 0) {
                if (array[affset] != s.charAt(offset)) {
                    cond = false; break;
                }
                affset++;
                offset++;
            }

            return cond;
        }

        public boolean equals(final String s) {
            return s.length() == length && startsWith(s);
        }

        public boolean isEmpty() {
            final int length = this.length;
            if (length > 0) {
                final char[] array = this.array;
                final int begin = this.begin;
                int offset = 0;
                while (offset < length) {
                    if (array[begin + offset++] != ' ') {
                        return false;
                    }
                }
            }

            return true;
        }

        public int indexOf(final char c) {
            final char[] array = this.array;
            final int begin = this.begin;
            final int length = this.length;
            int offset = 0;

            while (offset < length) {
                final char b = array[begin + offset];
                if (b == c) {
                    return offset;
                }
                offset++;
            }

            return -1;
        }

        public int lastIndexOf(final char c) {
            final char[] array = this.array;
            final int begin = this.begin;
            final int length = this.length;
            int offset = length - 1;

            while (offset > begin) {
                final char b = array[begin + offset];
                if (b == c) {
                    return offset;
                }
                offset--;
            }

            return -1;
        }

        public StringBuffer append(final StringBuffer sb) {
            return sb.append(array, begin, length);
        }

        public String toString() {
            return new String(array, begin, length);
        }

        public String substring(final int offset) {
            return new String(array, begin + offset, length - offset);
        }
    }
}
