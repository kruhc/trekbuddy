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

package cz.kruch.track.util;

import java.util.NoSuchElementException;

/**
 * Tokenizer for char arrays.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class CharArrayTokenizer {
    private static final char[] DEFAULT_DELIMS = { ',' };

    private char[] array;
    private char[] delimiters;
    private boolean returnDelim;

    private int position;
    private int end;
    private int dl;

    private final Token token;

    public CharArrayTokenizer() {
        this.token = new Token();
    }

    public void init(Token token, boolean returnDelim) {
        this.init(token, DEFAULT_DELIMS, returnDelim);
    }

    public void init(String s, boolean returnDelim) {
        this.init(s, DEFAULT_DELIMS, returnDelim);
    }

    public void init(char[] array, int length, char[] delimiters, boolean returnDelim) {
        this.init(array, delimiters, returnDelim);
        /* set end explicitly */
        this.end = length;
    }

    public void init(char[] array, int length, boolean returnDelim) {
        this.init(array, DEFAULT_DELIMS, returnDelim);
        /* set end explicitly */
        this.end = length;
    }

    public void init(Token token, char[] delimiters, boolean returnDelim) {
        this.init(token.array, delimiters, returnDelim);
        /* set start and end explicitly */
        this.position = token.begin;
        this.end = token.begin + token.length;
    }

    private void init(char[] array, char[] delimiters, boolean returnDelim) {
        /* gc hint */
        this.array = null;
        this.delimiters = null;
        /* init */
        this.array = array;
        this.delimiters = delimiters;
        this.returnDelim = returnDelim;
        this.position = 0;
        this.end = array.length;
        this.dl = delimiters.length;
    }

    public void init(String s, char[] delimiters, boolean returnDelim) {
        int sLength = s.length();
        /* if current array is not big enough, just release it */
        if (this.array != null && this.array.length < sLength) {
            this.array = null;
        }
        /* allocate new array if needed */
        if (this.array == null) {
            this.array = new char[sLength + sLength >> 1];
        }
        s.getChars(0, sLength, this.array, 0);
        this.delimiters = null; // gc hint
        this.delimiters = delimiters;
        this.returnDelim = returnDelim;
        this.position = 0;
        this.end = sLength;
        this.dl = delimiters.length;
    }

    public boolean hasMoreTokens() {
        return position < end;
    }

    public int nextInt() {
        return parseInt(next());
    }

    public double nextDouble() {
        return parseDouble(next());
    }

    public Token next() {
        // local ref
        final int end = this.end;

        if (position < end) {  /* == hasMoreTokens() */
            // local refs
            final char[] array = this.array;
            final Token token = this.token;

            // clear flag
            token.isDelimiter = false;

            // token beginning
            int begin = position;

            if (isDelim(array[position])) { // TODO should be 'while'
                // step fwd
                position++;
                // delims wanted?
                if (returnDelim) {
                    // set delimiter flag
                    token.isDelimiter = true;
                    // return
                    return token;
                }
                // move data start
                begin++;
                // check boundary
                if (position == end) {
                    throw new NoSuchElementException();
                }
            }

            int offset = position;
            while (offset < end) {
                final char ch = array[offset];
                if (isDelim(ch)) {
                    /*if (offset == position) return next();*/
                    break;
                } else {
                    offset++;
                }
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

    public static int parseInt(final char[] value, int offset, int length) {
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
            } else if (ch == ' ') {
                // ignore whitespace
            } else if (ch == '-') {
                sign = -1;
            } else {
                throw new NumberFormatException("Not a digit: " + ch);
            }
        }

        return result * sign;
    }

    public static float parseFloat(CharArrayTokenizer.Token token) {
        return parseFloat(token.array, token.begin, token.length);
    }

    public static float parseFloat(final char[] value, int offset, int length) {
        if (length == 0) {
            throw new NumberFormatException("No input");
        }

        final int end = offset + length;
        long decSeen = 0;
        float result = 0F; // TODO is this correct initial value???
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
                    final float fdigit = ch - '0';
                    if (decSeen > 0) {
                        result += (fdigit / decSeen);
                        decSeen *= 10;
                    } else {
                        result *= 10F;
                        result += fdigit;
                    }
                } else if (ch == ' ') {
                    // ignore whitespace
                }  else if (ch == '-') {
                    sign = -1;
                } else {
                    throw new NumberFormatException("Not a digit: " + ch);
                }
            }
        }

        return result * sign;
    }

    public static double parseDouble(CharArrayTokenizer.Token token) {
        return parseDouble(token.array, token.begin, token.length);
    }

    public static double parseDouble(final char[] value, int offset, int length) {
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

        public Token() {
        }

        public void init(char[] array, int begin, int length) {
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

        public boolean startsWith(String s) {
            final int sl = s.length();
            if (sl > length) {
                return false;
            }

            final char[] array = this.array;
            final int begin = this.begin;
            int offset = 0;

            while (offset < sl) {
                final char b = array[begin + offset];
                if (b != s.charAt(offset)) {
                    return false;
                }
                offset++;
            }

            return true;
        }

        public boolean endsWith(String s) {
            final int sl = s.length();
            if (sl > length) {
                return false;
            }

            final char[] array = this.array;
            final int start = this.begin + this.length - sl;
            int offset = 0;

            while (offset < sl) {
                final char b = array[start + offset];
                if (b != s.charAt(offset)) {
                    return false;
                }
                offset++;
            }

            return true;
        }

        public boolean equals(String s) {
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
                char b = array[begin + offset];
                if (b == c) {
                    return offset;
                }
                offset--;
            }

            return -1;
        }

        public String toString() {
            return new String(array, begin, length);
        }
    }
}
