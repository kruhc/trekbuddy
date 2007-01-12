// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.util;

import java.util.NoSuchElementException;

public final class CharArrayTokenizer {
    private char[] array;
    private char[] delimiters;
    private boolean returnDelim;

    private int pos;
    private int length;
    private int dl;

    private Token token;

    public CharArrayTokenizer() {
        this.token = new Token();
    }

    public void init(char[] array, char delimiter, boolean returnDelim) {
        init(array, new char[]{ delimiter }, returnDelim);
    }

    public void init(String s, char delimiter, boolean returnDelim) {
        init(s, new char[]{ delimiter }, returnDelim);
    }

    public void init(char[] array, char[] delimiters, boolean returnDelim) {
        this.array = array;
        this.delimiters = delimiters;
        this.returnDelim = returnDelim;
        this.pos = 0;
        this.length = array.length;
        this.dl = delimiters.length;
    }

    public void init(String s, char[] delimiters, boolean returnDelim) {
        int sLength = s.length();
        if (this.array != null && this.array.length < sLength) {
            this.array = null;
        }
        if (this.array == null) {
            this.array = new char[sLength + sLength >> 1];
        }
        s.getChars(0, sLength, this.array, 0);
        this.delimiters = delimiters;
        this.returnDelim = returnDelim;
        this.pos = 0;
        this.length = sLength;
        this.dl = delimiters.length;
    }

    public void dispose() {
        array = null;
    }

    public boolean hasMoreTokens() {
        return pos < length;
    }

    public Token next() {
        if (hasMoreTokens()) {
            int begin = pos;
            if (isDelim(array[pos])) {
                pos++;
                if (returnDelim) {
                    // init the token with valid data
                    token.delimiter();

                    // return
                    return token;
                } else {
                    begin++;
                }
            }
            int i = pos;
            while (i < length) {
                char ch = array[i];
                if (isDelim(ch)) {
                    break;
                } else {
                    i++;
                }
            }
            pos = i;

            // init the token with valid data
            token.init(array, begin, pos - begin);

            return token;
        }

        throw new NoSuchElementException();
    }

    private boolean isDelim(char c) {
        for (int i = dl; --i >= 0; ) {
            if (delimiters[i] == c) {
                return true;
            }
        }

        return false;
    }

    public static int parseInt(CharArrayTokenizer.Token token) {
        return parseInt(token.array, token.begin, token.length);
    }

    public static int parseInt(char[] value, int offset, int length) {
        if (length == 0) {
            throw new NumberFormatException("No input");
        }

        int end = offset + length;
        int result = 0; // TODO is this correct initial value???

        while (offset < end) {
            char ch = value[offset++];
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
            } else if (ch == ' ') {
                // ignore whitespace
            } else {
                throw new NumberFormatException("Not a digit: " + ch);
            }
        }

        return result;
    }

    public static float parseFloat(CharArrayTokenizer.Token token) {
        return parseFloat(token.array, token.begin, token.length);
    }

    public static float parseFloat(char[] value, int offset, int length) {
        if (length == 0) {
            throw new NumberFormatException("No input");
        }

        long decSeen = 0;
        int end = offset + length;
        float result = 0F; // TODO is this correct initial value

        while (offset < end) {
            char ch = value[offset++];
            if (ch == '.') {
                decSeen = 10;
            } else {
/* too slow
                int idigit = Character.digit(ch, 10);
*/
                if (ch >= '0' && ch <= '9') {
                    float fdigit = ch - '0';
                    if (decSeen > 0) {
                        result += (fdigit / decSeen);
                        decSeen *= 10;
                    } else {
                        result *= 10F;
                        result += fdigit;
                    }
                } else if (ch == ' ') {
                    // ignore whitespace
                }  else {
                    throw new NumberFormatException("Not a digit: " + ch);
                }
            }
        }

        return result;
    }

    public static double parseDouble(CharArrayTokenizer.Token token) {
        return parseDouble(token.array, token.begin, token.length);
    }

    public static double parseDouble(char[] value, int offset, int length) {
        if (length == 0) {
            throw new NumberFormatException("No input");
        }

        long decSeen = 0;
        int end = offset + length;
        double result = 0D; // TODO is this correct initial value?
        int sign = 1;

        while (offset < end) {
            char ch = value[offset++];
            if (ch == '.') {
                decSeen = 10;
            } else {
/* too slow
                int idigit = Character.digit(ch, 10);
*/
                if (ch >= '0' && ch <= '9') {
                    double fdigit = ch - '0';
                    if (decSeen > 0) {
                        result += (fdigit / decSeen);
                        decSeen *= 10;
                    } else {
                        result *= 10D;
                        result += fdigit;
                    }
                } else if (ch == ' ') {
                    // ignore whitespace
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

        public void init(char[] array, int begin, int length) {
            this.array = array;
            this.begin = begin;
            this.length = length;
            this.isDelimiter = false;
        }

        public void delimiter() {
            this.isDelimiter = true;
        }

        public boolean startsWith(char c) {
            int offset = 0;
            while (offset < length) {
                char b = array[begin + offset];
                if (b != ' ') {
                    return b == c;
                }
                offset++;
            }

            return false;
        }

        public boolean isEmpty() {
            if (length > 0) {
                int offset = 0;
                while (offset < length) {
                    if (array[begin + offset++] != ' ') {
                        return false;
                    }
                }
            }

            return true;
        }

        public String toString() {
            return new String(array, begin, length);
        }
    }
}
