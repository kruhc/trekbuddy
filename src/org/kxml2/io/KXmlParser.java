/* Copyright (c) 2002,2003, Stefan Haustein, Oberhausen, Rhld., Germany
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The  above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE. */

// Contributors: Paul Hackenberger (unterminated entity handling in relaxed mode)

package org.kxml2.io;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.util.Hashtable;
import java.io.Reader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

/**
 * A simple, pull based XML parser. This classe replaces the kXML 1
 * XmlParser class and the corresponding event classes.
 */
public final class KXmlParser implements XmlPullParser {
    private static final String UNEXPECTED_EOF  = "Unexpected EOF";
    private static final String ILLEGAL_TYPE    = "Wrong event type";
    private static final String CONSTANT_XMLNS  = "xmlns";
    private static final String CONSTANT_EMPTY  = "";

    private static final String ENC_UTF_32BE    = "UTF-32BE";
    private static final String ENC_UTF_32LE    = "UTF-32LE";
    private static final String ENC_UTF_16BE    = "UTF-16BE";
    private static final String ENC_UTF_16LE    = "UTF-16LE";
    private static final String ENC_UTF_8       = "UTF-8";

    private static final int LEGACY     = 999;
    private static final int XML_DECL   = 998;

    private Object location;

    // general

    private String version;
    private Boolean standalone;

    private boolean processNsp;
    private boolean relaxed;
    private Hashtable entityMap;
    private int depth;
    private String[] elementStack;
    private String[] nspStack;
    private int[] nspCounts;

    // source

    private Reader reader;
    private String encoding;

    private final char[] srcBuf;
    private int srcPos;
    private int srcCount;

    private int line;
    private int column;

    // txtbuffer

    private char[] txtBuf;
    private int txtPos;

    // event-related

    private int type;
    private boolean isWhitespace;
    private String namespace;
    private String prefix;
    private String name;
    private Int hash;

    private final String[] composite;
    private final Hashtable nameCache;
    private Int txtHash;

    private boolean degenerated;
    private int attributeCount;
    private String[] attributes;
    private int stackMismatch;
    private String error;

    /**
     * A separate peek buffer seems simpler than managing
     * wrap around in the first level read buffer
     */

    private final int[] peek;
    private int peekCount;
    private boolean wasCR;

    private boolean unresolved;
    private boolean token;

    public KXmlParser() {
        this.elementStack = new String[16];
        this.nspStack = new String[8];
        this.nspCounts = new int[4];
        this.srcBuf = new char[4096];
        this.txtBuf = new char[512];
        this.attributes = new String[32];
        this.peek = new int[2];
        this.hash = new Int(0);
        this.composite = new String[4];
        this.nameCache = new Hashtable(64);
        this.txtHash = new Int(0);
    }

    private boolean isProp(String n1, boolean prop, String n2) {
        if (!n1.startsWith("http://xmlpull.org/v1/doc/"))
            return false;
        if (prop)
            return n1.substring(42).equals(n2);
        else
            return n1.substring(40).equals(n2);
    }

    private boolean adjustNsp() throws XmlPullParserException {
        boolean any = false;

        for (int i = 0; i < attributeCount << 2; i += 4) {
            // * 4 - 4; i >= 0; i -= 4) {

            String attrName = attributes[i + 2];
            int cut = attrName.indexOf(':');
            String prefix;

            if (cut != -1) {
                prefix = attrName.substring(0, cut);
                attrName = attrName.substring(cut + 1);
            } else if (attrName.equals(CONSTANT_XMLNS)) {
                prefix = attrName;
                attrName = null;
            } else {
                continue;
            }

            if (!prefix.equals(CONSTANT_XMLNS)) {
                any = true;
            } else {
                final int j = (nspCounts[depth]++) << 1;

                if (nspStack.length < j + 2) {
                    final String[] bigger = new String[j + 2 + 16];
                    System.arraycopy(nspStack, 0, bigger, 0, nspStack.length);
                    nspStack = null;
                    nspStack = bigger;
                }

                nspStack[j] = attrName;
                nspStack[j + 1] = attributes[i + 3];

                if (attrName != null && attributes[i + 3].equals(CONSTANT_EMPTY)) {
                    error("illegal empty namespace");
                }

                //  prefixMap = new PrefixMap (prefixMap, attrName, attr.getValue ());

                //System.out.println (prefixMap);

                System.arraycopy(attributes, i + 4, attributes, i, ((--attributeCount) << 2) - i);
                i -= 4;
            }
        }

        if (any) {
            for (int i = (attributeCount << 2) - 4; i >= 0; i -= 4) {

                String attrName = attributes[i + 2];
                final int cut = attrName.indexOf(':');

                if (cut == 0 && !relaxed) {
                    throw new RuntimeException("illegal attribute name: " + attrName + " at " + this);
                } else if (cut != -1) {
                    final String attrPrefix = attrName.substring(0, cut);
                    attrName = attrName.substring(cut + 1);
                    final String attrNs = getNamespace(attrPrefix);

                    if (attrNs == null && !relaxed) {
                        throw new RuntimeException("Undefined Prefix: " + attrPrefix + " in " + this);
                    }

                    attributes[i] = attrNs;
                    attributes[i + 1] = attrPrefix;
                    attributes[i + 2] = attrName;

                    /*
                                        if (!relaxed) {
                                            for (int j = (attributeCount << 2) - 4; j > i; j -= 4)
                                                if (attrName.equals(attributes[j + 2])
                                                    && attrNs.equals(attributes[j]))
                                                    exception(
                                                        "Duplicate Attribute: {"
                                                            + attrNs
                                                            + "}"
                                                            + attrName);
                                        }
                        */
                }
            }
        }

        final int cut = name.indexOf(':');
        if (cut == 0) {
            error("illegal tag name: " + name);
        }
        if (cut != -1) {
            prefix = name.substring(0, cut);
            name = name.substring(cut + 1);
        }

        this.namespace = getNamespace(prefix);
        if (this.namespace == null) {
            if (prefix != null) {
                error("undefined prefix: " + prefix);
            }
            this.namespace = NO_NAMESPACE;
        }

        return any;
    }

    private void error(final String desc) throws XmlPullParserException {
        if (relaxed) {
            if (error == null) {
                error = "ERR: " + desc;
            }
        } else {
            exception(desc);
        }
    }

    private void exception(final String desc) throws XmlPullParserException {
        throw new XmlPullParserException(desc.length() < 100 ? desc : desc.substring(0, 100) + "\n",
                                         this, null);
    }

    /**
     * common base for next and nextToken. Clears the state, except from 
     * txtPos and whitespace. Does not set the type variable */

    private void nextImpl() throws IOException, XmlPullParserException {
        if (reader == null) {
            exception("No Input specified");
        }
        if (type == END_TAG) {
            depth--;
        }
        while (true) {
            attributeCount = -1;

            // degenerated needs to be handled before error because of possible
            // processor expectations(!)

            if (degenerated) {
                degenerated = false;
                type = END_TAG;
                return;
            }


            if (error != null) {
                for (int N = error.length(), i = 0; i < N; i++) {
                    push(error.charAt(i));
                }
                //				text = error;
                error = null;
                type = COMMENT;
                return;
            }


            if (relaxed && (stackMismatch > 0 || (peek(0) == -1 && depth > 0))) {
                final int sp = (depth - 1) << 2;
                type = END_TAG;
                namespace = elementStack[sp];
                prefix = elementStack[sp + 1];
                name = elementStack[sp + 2];
                if (stackMismatch != 1) {
                    error = "missing end tag /" + name + " inserted";
                }
                if (stackMismatch > 0) {
                    stackMismatch--;
                }
                return;
            }

            prefix = null;
            name = null;
            namespace = null;
            //            text = null;

            type = peekType();

            switch (type) {

                case ENTITY_REF :
                    pushEntity();
                    return;

                case START_TAG :
                    parseStartTag(false);
                    return;

                case END_TAG :
                    parseEndTag();
                    return;

                case END_DOCUMENT :
                    return;

                case TEXT :
                    copyText();
                    if (depth == 0) {
                        if (isWhitespace) {
                            type = IGNORABLE_WHITESPACE;
                        }
                        // make exception switchable for instances.chg... !!!!
                        //	else 
                        //    exception ("text '"+getText ()+"' not allowed outside root element");
                    }
                    return;

                default :
                    type = parseLegacy(token);
                    if (type != XML_DECL) {
                        return;
                    }
            }
        }
    }

    private int parseLegacy(boolean push) throws IOException, XmlPullParserException {
        String req = CONSTANT_EMPTY;
        int term;
        int result;
        int prev = 0;

        read(); // <
        int c = read();

        switch (c) {
            case '?': {
                if ((peek(0) == 'x' || peek(0) == 'X')
                    && (peek(1) == 'm' || peek(1) == 'M')) {

                    if (push) {
                        push(peek(0));
                        push(peek(1));
                    }
                    read();
                    read();

                    if ((peek(0) == 'l' || peek(0) == 'L') && peek(1) <= ' ') {

                        if (line != 1 || column > 4) {
                            error("PI must not start with xml");
                        }

                        parseStartTag(true);

                        if (attributeCount < 1 || !"version".equals(attributes[2])) {
                            error("version expected");
                        }

                        version = attributes[3];

                        int pos = 1;

                        if (pos < attributeCount && "encoding".equals(attributes[2 + 4])) {
                            encoding = attributes[3 + 4];
                            pos++;
                        }

                        if (pos < attributeCount && "standalone".equals(attributes[4 * pos + 2])) {
                            String st = attributes[3 + 4 * pos];
                            if ("yes".equals(st)) {
                                standalone = Boolean.TRUE;
                            } else if ("no".equals(st)) {
                                standalone = Boolean.FALSE;
                            } else {
                                error("illegal standalone value: " + st);
                            }
                            pos++;
                        }

                        if (pos != attributeCount) {
                            error("illegal xmldecl");
                        }

                        isWhitespace = true;
                        txtPos = 0;

                        return XML_DECL;
                    }
                }

                term = '?';
                result = PROCESSING_INSTRUCTION;
            } break;

            case '!': {
                if (peek(0) == '-') {
                    result = COMMENT;
                    req = "--";
                    term = '-';
                } else if (peek(0) == '[') {
                    result = CDSECT;
                    req = "[CDATA[";
                    term = ']';
                    push = true;
                } else {
                    result = DOCDECL;
                    req = "DOCTYPE";
                    term = -1;
                }
            } break;

            default: {
                error("illegal: <" + c);
                return COMMENT;
            }
        }

        for (int N = req.length(), i = 0; i < N; i++) {
            read(req.charAt(i));
        }

        if (result == DOCDECL) {
            parseDoctype(push);
        } else {
            while (true) {
                c = read();
                if (c == -1){
                    error(UNEXPECTED_EOF);
                    return COMMENT;
                }

                if (push)
                    push(c);

                if ((term == '?' || c == term) && peek(0) == term && peek(1) == '>')
                    break;

                prev = c;
            }

            if (term == '-' && prev == '-') {
                error("illegal comment delimiter: --->");
            }

            read();
            read();

            if (push && term != '?') {
                txtPos--;
            }
        }

        return result;
    }

    /* precondition: &lt! consumed */

    private void parseDoctype(boolean push) throws IOException, XmlPullParserException {

        int nesting = 1;
        boolean quoted = false;

        // read();

        while (true) {
            int i = read();
            switch (i) {

                case -1 :
                    error(UNEXPECTED_EOF);
                    return;

                case '\'' :
                    quoted = !quoted;
                    break;

                case '<' :
                    if (!quoted)
                        nesting++;
                    break;

                case '>' :
                    if (!quoted) {
                        if ((--nesting) == 0)
                            return;
                    }
                    break;
            }
            if (push)
                push(i);
        }
    }

    /* precondition: &lt;/ consumed */

    private void parseEndTag() throws IOException, XmlPullParserException {

        read(); // '<'
        read(); // '/'
        name = readName(hash);
        skip();
        read('>');

        final int sp = (depth - 1) << 2;

        if (depth == 0) {
            error("element stack empty");
            type = COMMENT;
            return;
        }

        final String[] elementStack = this.elementStack; 

        if (!name.equals(elementStack[sp + 3])) {
            error("expected: /" + elementStack[sp + 3] + " read: " + name);

            // become case insensitive in relaxed mode

            int probe = sp;
            while (probe >= 0 && !name.toLowerCase().equals(elementStack[probe + 3].toLowerCase())) {
                stackMismatch++;
                probe -= 4;
            }

            if (probe < 0) {
                stackMismatch = 0;
                //			text = "unexpected end tag ignored";
                type = COMMENT;
                return;
            }
        }

        namespace = elementStack[sp];
        prefix = elementStack[sp + 1];
        name = elementStack[sp + 2];
    }

    private int peekType() throws IOException {
        switch (peek(0)) {
            case -1 :
                return END_DOCUMENT;
            case '&' :
                return ENTITY_REF;
            case '<' :
                switch (peek(1)) {
                    case '/' :
                        return END_TAG;
                    case '?' :
                    case '!' :
                        return LEGACY;
                    default :
                        return START_TAG;
                }
            default :
                return TEXT;
        }
    }

    private String get(final int pos) {
        return new String(txtBuf, pos, txtPos - pos);
    }

/*
    private String getCached(final int pos) {
        if (nameCache != null) {
            final int length = txtPos - pos;
            final char[] buf = txtBuf;
            final String[] cache = nameCache;
            for (int i = cache.length; --i >= 0; ) {
                final String item = cache[i];
                if (length != item.length())
                    continue;
                int j = length;
                while (--j >= 0) {
                    if (buf[pos + j] != item.charAt(j))
                        break;
                }
                if (j < 0) {
                    return item;
                }
            }
        }

        return get(pos);
    }
*/

/*
    private String pop(int pos) {
        String result = new String (txtBuf, pos, txtPos - pos);
        txtPos = pos;
        return result;
    }
*/

    private void push(final int c) {
        isWhitespace &= c <= ' ';

        if (txtPos == txtBuf.length) {
            final char[] bigger = new char[txtPos * 4 / 3 + 4];
            System.arraycopy(txtBuf, 0, bigger, 0, txtPos);
            txtBuf = null; // gc hint
            txtBuf = bigger;
        }

        txtBuf[txtPos++] = (char) c;
    }

    /** Sets name and attributes */

    private void parseStartTag(final boolean xmldecl) throws IOException, XmlPullParserException {

        if (!xmldecl) {
            read(); // '<'
        }

        name = readName(hash);

        int sp = depth++ << 2;

        if (depth >= nspCounts.length) {
            final int[] bigger = new int[depth + 4];
            System.arraycopy(nspCounts, 0, bigger, 0, nspCounts.length);
            nspCounts = null; // gc hint
            nspCounts = bigger;
        }
        nspCounts[depth] = nspCounts[depth - 1];

        if (elementStack.length < sp + 4) {
            final String[] bigger = new String[sp + 4 + 16];
            System.arraycopy(elementStack, 0, bigger, 0, elementStack.length);
            elementStack = null;
            elementStack = bigger;
        }

        final String[] elementStack = this.elementStack;
        final String[] composite = this.composite;
        elementStack[sp] = composite[0];
        elementStack[sp + 1] = composite[1];
        elementStack[sp + 2] = composite[2];
        elementStack[sp + 3] = name;
        name = composite[2];

        attributeCount = 0;

        while (true) {
            skip();

            final int c = peek(0);

            if (xmldecl) {
                if (c == '?') {
                    read();
                    read('>');
                    return;
                }
            } else {
                if (c == '/') {
                    degenerated = true;
                    read();
                    skip();
                    read('>');
                    break;
                }

                if (c == '>' && !xmldecl) {
                    read();
                    break;
                }
            }

            if (c == -1) {
                error(UNEXPECTED_EOF);
                //type = COMMENT;
                return;
            }

            final String attrName = readName(txtHash);

            if (attrName.length() == 0) {
                error("attr name expected");
               //type = COMMENT;
                break;
            }

            int i = (attributeCount++) << 2;

            if (attributes.length < i + 4) {
                final String[] bigger = new String[i + 4 + 16];
                System.arraycopy(attributes, 0, bigger, 0, attributes.length);
                attributes = null;
                attributes = bigger;
            }

            final String[] attributes = this.attributes;
            attributes[i++] = CONSTANT_EMPTY;
            attributes[i++] = null;
            attributes[i++] = attrName;

            skip();

            if (peek(0) != '=') {
                error("attr value missing for " + attrName);
                attributes[i] = "1";
            } else {
                read('=');
                skip();
                int delimiter = peek(0);

                if (delimiter != '\'' && delimiter != '"') {
                    error("attr value delimiter missing!");
                    delimiter = ' ';
                } else {
                    read();
                }

                final int p = txtPos;
                pushText(delimiter, true);

                attributes[i] = get(p);
                txtPos = p;

                if (delimiter != ' ') {
                    read(); // skip endquote
                }
            }
        }

/*
        int sp = depth++ << 2;

        elementStack = ensureCapacity(elementStack, sp + 4);
        elementStack[sp + 3] = name;

        if (depth >= nspCounts.length) {
            final int[] bigger = new int[depth + 4];
            System.arraycopy(nspCounts, 0, bigger, 0, nspCounts.length);
            nspCounts = null; // gc hint
            nspCounts = bigger;
        }

        nspCounts[depth] = nspCounts[depth - 1];
*/

        /*
        		if(!relaxed){
                for (int i = attributeCount - 1; i > 0; i--) {
                    for (int j = 0; j < i; j++) {
                        if (getAttributeName(i).equals(getAttributeName(j)))
                            exception("Duplicate Attribute: " + getAttributeName(i));
                    }
                }
        		}
        */

/*
        if (processNsp) {
            adjustNsp();
        } else {
            namespace = CONSTANT_EMPTY;
        }

        elementStack[sp] = namespace;
        elementStack[sp + 1] = prefix;
        elementStack[sp + 2] = name;
*/
    }

    /**
     * result: isWhitespace; if the setName parameter is set,
     * the name of the entity is stored in "name" */

    private void pushEntity() throws IOException, XmlPullParserException {

        push(read()); // &

        int pos = txtPos;

        while (true) {
            final int c = read();
            if (c == ';')
                break;
            if (c < 128
                && (c < '0' || c > '9')
                && (c < 'a' || c > 'z')
                && (c < 'A' || c > 'Z')
                && c != '_'
                && c != '-'
                && c != '#') {
                if (!relaxed) {
                    error("unterminated entity ref");
                }
                //; ends with:"+(char)c);           
                if (c != -1) {
                    push(c);
                }
                return;
            }

            push(c);
        }

        final String code = get(pos);
        txtPos = pos - 1;
        if (token && type == ENTITY_REF){
            name = code;
        }

        if (code.charAt(0) == '#') {
            final int c = (code.charAt(1) == 'x' ? Integer.parseInt(code.substring(2), 16) : Integer.parseInt(code.substring(1)));
            push(c);
            return;
        }

        final String result = (String) entityMap.get(code);

        unresolved = result == null;

        if (unresolved) {
            if (!token) {
                error("unresolved: &" + code + ";");
            }
        } else {
            for (int N = result.length(), i = 0; i < N; i++) {
                push(result.charAt(i));
            }
        }
    }

    /** types:
    '<': parse to any token (for nextToken ())
    '"': parse to quote
    ' ': parse to whitespace or '>'
    */

    private void pushText(final int delimiter, final boolean resolveEntities)
            throws IOException, XmlPullParserException {

        int next = peek(0);
        int cbrCount = 0;

        while (next != -1 && next != delimiter) { // covers eof, '<', '"'

            if (delimiter == ' ') {
                if (next <= ' ' || next == '>') {
                    break;
                }
            }

            if (next == '&') {
                if (!resolveEntities) {
                    break;
                }
                pushEntity();
            } else if (next == '\n' && type == START_TAG) {
                read();
                push(' ');
            } else {
                push(read());
            }

            if (next == '>' && cbrCount >= 2 && delimiter != ']') {
                error("Illegal: ]]>");
            }

            if (next == ']') {
                cbrCount++;
            } else {
                cbrCount = 0;
            }

            next = peek(0);
        }
    }

    /** text-node optimized version of pushText()
     '<' is delimiter
     */

    private void copyText() throws IOException, XmlPullParserException {
        
        int next = peek(0);

        while (next != -1 && next != '<') { // covers eof, '<'

            if (next != '&') {
                push(read());
                fastCopyText();
            } else {
                pushEntity();
            }

            next = peek(0);
        }
    }

    /*
     * fast text copy (local variant of push-read-peek with some whitespaces hack)
     * 2008-12-18: whitespace hack commented out
     */

    private void fastCopyText() throws IOException {
        final char[] srcBuf = this.srcBuf;
        final char[] txtBuf = this.txtBuf;
        final int lengh = txtBuf.length;
        final int srcCount = this.srcCount;
        int srcPos = this.srcPos;
        int txtPos = this.txtPos;
        int column = this.column;
        int wsCount = 0;
        while (srcPos < srcCount && txtPos < lengh) {
            final char c = srcBuf[srcPos++];
            if (c == '<' || c == '&') {
                srcPos--;
                break;
            } else if (c <= ' ') {
                column++;
                if (c == '\n') {
                    line++;
                    column = 1;
                    if (wsCount++ > 0) { // let's ignore multiple CRLFs
                        continue;
                    }
                }
/* 2008-12-18: reformats users breaks
                if (wsCount++ > 0) {
                    continue;
                }
                c = ' ';
*/
            } else {
                column++;
                wsCount = 0;
            }
            txtBuf[txtPos++] = c;
        }
        this.srcPos = srcPos;
        this.txtPos = txtPos;
        this.column = column;
    }

    /*
     * fast name copy (local variant of push-read-peek with valid char check)
     */

    private int fastCopyName() throws IOException {
        final char[] srcBuf = this.srcBuf;
        final char[] txtBuf = this.txtBuf;
        final int lengh = txtBuf.length;
        final int srcCount = this.srcCount;
        int srcPos = this.srcPos;
        int txtPos = this.txtPos;
        int column = this.column;
        while (srcPos < srcCount && txtPos < lengh) {
            final char c = srcBuf[srcPos++];
            if ((c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '_'
                || c == '-'
                || c == '.'
                || c >= 0x0b7) {
                txtBuf[txtPos++] = c;
                column++;
                continue;
            }
            srcPos--;
            break;
        }
        this.srcPos = srcPos;
        this.txtPos = txtPos;
        this.column = column;
        return peek(0);
    }

    private void read(final char c) throws IOException, XmlPullParserException {
        final int a = read();
        if (a != c) {
            error("expected: '" + c + "' actual: '" + ((char) a) + "'");
        }
    }

    private int read() throws IOException {
        final int result;

        if (peekCount == 0) {
            result = peek(0);
        } else {
            final int[] peek = this.peek;
            result = peek[0];
            peek[0] = peek[1];
        }
        //		else {
        //			result = peek[0]; 
        //			System.arraycopy (peek, 1, peek, 0, peekCount-1);
        //		}

        peekCount--;
        column++;

        if (result == '\n') {
            line++;
            column = 1;
        }

        return result;
    }

    /* Does never read more than needed */

    private int peek(final int pos) throws IOException {
        final int[] peek = this.peek;

        while (pos >= peekCount) {
            final int nw;

            if (srcPos < srcCount) {
                nw = srcBuf[srcPos++];
            } else {
                srcCount = reader.read(srcBuf, 0, srcBuf.length);
                if (srcCount <= 0) {
                    nw = -1;
                } else {
                    nw = srcBuf[0];
                }
                srcPos = 1;
            }

            switch (nw) {
                case '\n': {
                    if (!wasCR) {
                        peek[peekCount++] = '\n';
                    }
                    wasCR = false;
                } break;
                case '\r': {
                    wasCR = true;
                    peek[peekCount++] = '\n';
                } break;
                default: {
                    peek[peekCount++] = nw;
                    wasCR = false;
                }
            }
        }

        return peek[pos];
    }

    private String readName(final Int hash) throws IOException, XmlPullParserException {
        int pos = txtPos, semipos = txtPos;
        int c = peek(0);
        if ((c < 'a' || c > 'z')
            && (c < 'A' || c > 'Z')
            && c != '_'
            && c != ':'
            && c < 0x0c0
            && !relaxed) {
                error("name expected");
        }

        final String[] composite = this.composite;

        composite[0] = NO_NAMESPACE;
        composite[1] = null;

        do {
            push(read());
/*
            c = peek(0);
*/
            if (peekCount > 0) { // == 1
                c = peek[0];
            } else {
                c = fastCopyName();
            }
        } while ((c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '_'
                || c == '-'
//                || c == ':'
                || c == '.'
                || c >= 0x0b7);
        if (c == ':') {
            if (processNsp) {
                composite[1] = getCached(pos, txtHash);
                composite[0] = getNamespace(composite[1]);
                semipos = txtPos;
                semipos++;
            }
            do {
                push(read());
/*
                c = peek(0);
*/
                if (peekCount > 0) { // == 1
                    c = peek[0];
                } else {
                    c = fastCopyName();
                }
            } while ((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_'
                    || c == '-'
                    || c == '.'
                    || c >= 0x0b7);
        }

        composite[2] = getCached(semipos, hash); // localname
        composite[3] = getCached(pos, txtHash); // element/attribute "as is"

/*
        final String result = getCached(pos); // element/attribute "as is"
        final String result = get(pos); // getCached(pos);
*/
        txtPos = pos;

        return composite[3];
    }

    private int hash(int pos) {
        final char[] txt = txtBuf;
        int h = 0;
        for (int i = txtPos - pos; --i >= 0; ) {
            h = 31 * h + txt[pos++];
        }
        return h;
    }

    private String getCached(final int pos, final Int hash) {
        hash.setValue(hash(pos));
        String result = (String) nameCache.get(hash);
        if (result == null) {
            result = get(pos);
            nameCache.put(hash.clone(), result);
        }
        return result;
    }

    private void skip() throws IOException {
        while (true) {
            int c = peek(0);
            if (c > ' ' || c == -1) {
                break;
            }
            read();
        }
    }

    //  public part starts here...

    public void setInput(Reader reader) throws XmlPullParserException {
        this.reader = reader;
        this.line = 1;
        this.column = 0;
        this.type = START_DOCUMENT;
        this.name = null;
        this.namespace = null;
        this.degenerated = false;
        this.attributeCount = -1;
        this.encoding = null;
        this.version = null;
        this.standalone = null;

        if (reader == null) {
            return;
        }

        this.srcPos = 0;
        this.srcCount = 0;
        this.peekCount = 0;
        this.depth = 0;

        this.entityMap = null;
        this.entityMap = new Hashtable();
        this.entityMap.put("amp", "&");
        this.entityMap.put("apos", "'");
        this.entityMap.put("gt", ">");
        this.entityMap.put("lt", "<");
        this.entityMap.put("quot", "\"");
    }

    public void setInput(InputStream is, String _enc) throws XmlPullParserException {
        if (is == null) {
            throw new IllegalArgumentException("Input stream is null");
        }

        try {
            String enc = _enc;
            if (enc == null) {

                // vars
                int bom = 0;
                final char[] srcBuf = this.srcBuf;

                // read four bytes
                while (srcCount < 4) {
                    final int i = is.read();
                    if (i == -1)
                        break;
                    bom = (bom << 8) | i;
                    srcBuf[srcCount++] = (char) i;
                }

                if (srcCount == 4) {
                    switch (bom) {
                        case 0x00000FEFF:
                            enc = ENC_UTF_32BE;
                            srcCount = 0;
                            break;
                        case 0x0FFFE0000:
                            enc = ENC_UTF_32LE;
                            srcCount = 0;
                            break;
                        case 0x03c:
                            enc = ENC_UTF_32BE;
                            srcBuf[0] = '<';
                            srcCount = 1;
                            break;
                        case 0x03c000000:
                            enc = ENC_UTF_32LE;
                            srcBuf[0] = '<';
                            srcCount = 1;
                            break;
                        case 0x0003c003f:
                            enc = ENC_UTF_16BE;
                            srcBuf[0] = '<';
                            srcBuf[1] = '?';
                            srcCount = 2;
                            break;
                        case 0x03c003f00:
                            enc = ENC_UTF_16LE;
                            srcBuf[0] = '<';
                            srcBuf[1] = '?';
                            srcCount = 2;
                            break;
                        case 0x03c3f786d: // 8-bit encoding
                            while (true) {
                                final int i = is.read();
                                if (i == -1) {
                                    break;
                                }
                                srcBuf[srcCount++] = (char) i;
                                if (i == '>') {
                                    final String s = new String(srcBuf, 0, srcCount);
                                    int i0 = s.indexOf("encoding");
                                    if (i0 != -1) {
                                        while (s.charAt(i0) != '"' && s.charAt(i0) != '\'') {
                                            i0++;
                                        }
                                        final char deli = s.charAt(i0++);
                                        final int i1 = s.indexOf(deli, i0);
                                        enc = s.substring(i0, i1);
                                    }
                                    break;
                                } else if (srcCount == srcBuf.length) {
                                    throw new IllegalArgumentException("Malformed XML");
                                }
                            }
                            break;
                        default: {
                            if ((bom & 0x0ffff0000) == 0x0FEFF0000) {
                                enc = ENC_UTF_16BE;
                                srcBuf[0] = (char) ((srcBuf[2] << 8) | srcBuf[3]);
                                srcCount = 1;
                            } else if ((bom & 0x0ffff0000) == 0x0fffe0000) {
                                enc = ENC_UTF_16LE;
                                srcBuf[0] = (char) ((srcBuf[3] << 8) | srcBuf[2]);
                                srcCount = 1;
                            } else if ((bom & 0x0ffffff00) == 0x0EFBBBF00) {
                                enc = ENC_UTF_8;
                                srcBuf[0] = srcBuf[3];
                                srcCount = 1;
                            }
                        }
                    }
                }
            }

            final int sc = srcCount;
            if (enc == null) {
                setInput(new InputStreamReader(is));
                encoding = System.getProperty("microedition.encoding");
            } else {
                setInput(new InputStreamReader(is, enc));
                encoding = enc;
            }
            srcCount = sc;

        } catch (Exception e) {
            throw new XmlPullParserException("Invalid stream or encoding: " + e, this, e);
        }
    }

    public boolean getFeature(String feature) {
        if (XmlPullParser.FEATURE_PROCESS_NAMESPACES.equals(feature)) {
            return processNsp;
        } else if (isProp(feature, false, "relaxed")) {
            return relaxed;
        }

        return false;
    }

    public String getInputEncoding() {
        return encoding;
    }

    public void defineEntityReplacementText(String entity, String value) throws XmlPullParserException {
        throw new RuntimeException("Not supported");
/*
        if (entityMap == null) {
            throw new RuntimeException("entity replacement text must be defined after setInput!");
        }
        entityMap.put(entity, value);
*/
    }

    public Object getProperty(String property) {
        if (isProp(property, true, "xmldecl-version")) {
            return version;
        }
        if (isProp(property, true, "xmldecl-standalone")) {
            return standalone;
        }
        if (isProp(property, true, "location")) {
            return location != null ? location : reader.toString();
        }

        return null;
    }

    public int getNamespaceCount(int depth) {
        if (depth > this.depth) {
            throw new IndexOutOfBoundsException();
        }

        return nspCounts[depth];
    }

    public String getNamespacePrefix(int pos) {
        return nspStack[pos << 1];
    }

    public String getNamespaceUri(int pos) {
        return nspStack[(pos << 1) + 1];
    }

    public String getNamespace(String prefix) {
        if ("xml".equals(prefix)) {
            return "http://www.w3.org/XML/1998/namespace";
        }
        if (CONSTANT_XMLNS.equals(prefix)) {
            return "http://www.w3.org/2000/xmlns/";
        }

        for (int i = (getNamespaceCount(depth) << 1) - 2; i >= 0; i -= 2) {
            if (prefix == null) {
                if (nspStack[i] == null) {
                    return nspStack[i + 1];
                }
            } else if (prefix.equals(nspStack[i])) {
                return nspStack[i + 1];
            }
        }

        return null;
    }

    public int getDepth() {
        return depth;
    }

    public String getPositionDescription() {
        StringBuffer buf = new StringBuffer(type < TYPES.length ? TYPES[type] : "unknown");
        buf.append(' ');

        if (type == START_TAG || type == END_TAG) {
            if (degenerated) {
                buf.append("(empty) ");
            }
            buf.append('<');
            if (type == END_TAG) {
                buf.append('/');
            }
            if (prefix != null) {
                buf.append('{').append(namespace).append('}').append(prefix).append(':');
            }
            buf.append(name);

            final int cnt = attributeCount << 2;
            for (int i = 0; i < cnt; i += 4) {
                buf.append(' ');
                if (attributes[i + 1] != null) {
                    buf.append('{').append(attributes[i]).append('}').append(attributes[i + 1]).append(':');
                }
                buf.append(attributes[i + 2]).append('=').append('\'').append(attributes[i + 3]).append('\'');
            }

            buf.append('>');
        } else if (type == IGNORABLE_WHITESPACE) {
        } else if (type != TEXT) {
            buf.append(getText());
        } else if (isWhitespace) {
            buf.append("(whitespace)");
        } else {
            String text = getText();
            if (text.length() > 16) {
                text = text.substring(0, 16) + "...";
            }
            buf.append(text);
        }

        buf.append('@').append(line).append(':').append(column);
        if (location != null){
            buf.append(" in ");
            buf.append(location);
        } else if (reader != null) {
            buf.append(" in ");
            buf.append(reader.toString());
        }
        return buf.toString();
    }

    public int getLineNumber() {
        return line;
    }

    public int getColumnNumber() {
        return column;
    }

    public boolean isWhitespace() throws XmlPullParserException {
        if (type != TEXT && type != IGNORABLE_WHITESPACE && type != CDSECT)
            exception(ILLEGAL_TYPE);
        return isWhitespace;
    }

    public String getText() {
        return type < TEXT || (type == ENTITY_REF && unresolved) ? null : get(0);
    }

    public char[] getChars() {
        if (type == TEXT) {
            final int l = txtPos;
            final char[] result = new char[l];
            System.arraycopy(txtBuf, 0, result, 0, l);

            return result;
        }

        return null;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }

    public String getPrefix() {
        return prefix;
    }

    public int getHash() {
        return hash.getValue();
    }
    
    public boolean isEmptyElementTag() throws XmlPullParserException {
        if (type != START_TAG) {
            exception(ILLEGAL_TYPE);
        }
        return degenerated;
    }

    public int getAttributeCount() {
        return attributeCount;
    }

    public String getAttributeType(int index) {
        return "CDATA";
    }

    public boolean isAttributeDefault(int index) {
        return false;
    }

    public String getAttributeNamespace(int index) {
        if (index >= attributeCount) {
            throw new IndexOutOfBoundsException();
        }

        return attributes[index << 2];
    }

    public String getAttributeName(int index) {
        if (index >= attributeCount) {
            throw new IndexOutOfBoundsException();
        }

        return attributes[(index << 2) + 2];
    }

    public String getAttributePrefix(int index) {
        if (index >= attributeCount) {
            throw new IndexOutOfBoundsException();
        }

        return attributes[(index << 2) + 1];
    }

    public String getAttributeValue(int index) {
        if (index >= attributeCount) {
            throw new IndexOutOfBoundsException();
        }

        return attributes[(index << 2) + 3];
    }

    public String getAttributeValue(String namespace, String name) {
        String[] attributes = this.attributes;
        for (int i = (attributeCount << 2) - 4; i >= 0; i -= 4) {
            if (attributes[i + 2].equals(name)
                && (namespace == null || attributes[i].equals(namespace)))
                return attributes[i + 3];
        }

        return null;
    }

    public int getEventType() throws XmlPullParserException {
        return type;
    }

    public int next() throws XmlPullParserException, IOException {
        txtPos = 0;
        isWhitespace = true;
        token = false;

        int minType = 9999;

        do {
            nextImpl();
            if (type < minType) {
                minType = type;
            }
            //	    if (curr <= TEXT) type = curr;
        } while (minType > ENTITY_REF // ignorable
                || (minType >= TEXT && peekType() >= TEXT));

        type = minType;
        if (type > TEXT) {
            type = TEXT;
        }

        return type;
    }

    public int nextToken() throws XmlPullParserException, IOException {
        isWhitespace = true;
        txtPos = 0;
        token = true;

        nextImpl();

        return type;
    }

    //
    // utility methods to make XML parsing easier ...

    public int nextTag() throws XmlPullParserException, IOException {
        next();

        if (type == TEXT && isWhitespace) {
            next();
        }

        if (type != END_TAG && type != START_TAG) {
            exception("unexpected type");
        }

        return type;
    }

    public void require(int type, String namespace, String name)
            throws XmlPullParserException, IOException {

        if (type != this.type
            || (namespace != null && !namespace.equals(getNamespace()))
            || (name != null && !name.equals(getName()))) {
            exception("expected: " + TYPES[type] + " {" + namespace + "}" + name);
        }
    }

    public String nextText() throws XmlPullParserException, IOException {
        if (type != START_TAG) {
            exception("precondition: START_TAG");
        }

        next();

        String result;

        if (type == TEXT) {
            result = getText();
            next();
        } else {
            result = CONSTANT_EMPTY;
        }

        if (type != END_TAG) {
            exception("END_TAG expected");
        }

        return result;
    }

    public char[] nextChars() throws XmlPullParserException, IOException {
        if (type != START_TAG) {
            exception("precondition: START_TAG");
        }

        next();

        char[] result;

        if (type == TEXT) {
            result = getChars();
            next();
        } else {
            result = null;
        }

        if (type != END_TAG) {
            exception("END_TAG expected");
        }

        return result;
    }

    public void setFeature(String feature, boolean value) throws XmlPullParserException {
        if (XmlPullParser.FEATURE_PROCESS_NAMESPACES.equals(feature)) {
            processNsp = value;
        } else if (isProp(feature, false, "relaxed")) {
            relaxed = value;
        } else {
            exception("unsupported feature: " + feature);
        }
    }

    public void setProperty(String property, Object value) throws XmlPullParserException {
        if (isProp(property, true, "location")) {
            location = value;
        } else {
            throw new XmlPullParserException("unsupported property: " + property);
        }
    }

    /**
      * Skip sub tree that is currently porser positioned on.
      * <br>NOTE: parser must be on START_TAG and when funtion returns
      * parser will be positioned on corresponding END_TAG. 
      */

    //	Implementation copied from Alek's mail... 

    public void skipSubTree() throws XmlPullParserException, IOException {
        require(START_TAG, null, null);

        int level = 1;
        while (level > 0) {
            switch (next()) {
                case START_TAG:
                    ++level;
                    break;
                case END_TAG:
                    --level;
                    break;
            }
        }
/*
        if (!degenerated) {
            int c, skipped = 0, level = 1;
            do {
                if (skipped > 0) {
                    read();
                }
                c = peek(0);
                if (c == -1) {
                    break;
                }
                switch (c) {
                    case '<': {
                        if (peek(1) == '/') {
                            --level;
                        } else {
                            ++level;
                        }
                    } break;
                    case '/': {
                        if (peek(1) == '>') {
                            --level;
                        }
                    } break;
                }
                skipped++;
            } while (level > 0);
        }
*/
    }

    public void close() throws IOException {
        if (reader != null) {
            try {
                reader.close();
            } finally {
                reader = null;
            }
        }
    }

    private static final class Int {
        private int value;

        public Int(int value) {
            this.value = value;
        }

        public Int clone() {
            return new Int(value);
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }

        public int hashCode() {
            return value;
        }

        public boolean equals(Object object) {
            if (object instanceof Int) {
                return ((Int) object).value == value;
            }
            return false;
        }

        public String toString() {
            return Integer.toString(value);
        }
    }
}
