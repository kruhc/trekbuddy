/* Copyright 2005-2006 by data2c.com

Authors:
Wolfgang S. Kechel - wolfgang.kechel@data2c.com
Jörn Marcks - joern.marcks@data2c.com

Wolfgang S. Kechel, Jörn Marcks

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.hecl.net;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.util.Enumeration;
import java.util.Hashtable;

//#ifdef j2me
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
//#else
import java.net.HttpURLConnection;
import java.net.URL;
//#endif

public class HttpRequest /*extends Thread*/ {
    static public final short SETUP = 0;
    static public final short CONNECTED = 1;
    static public final short ERROR = 2;
    static public final short TIMEOUT = 3;
    static public final short OK = 4;

//#ifdef j2me
    public static final int HTTP_UNAUTHORIZED = HttpConnection.HTTP_UNAUTHORIZED;
    public static final int HTTP_FORBIDDEN = HttpConnection.HTTP_FORBIDDEN;
//#else
    public static final int HTTP_UNAUTHORIZED = HttpURLConnection.HTTP_UNAUTHORIZED;
    public static final int HTTP_FORBIDDEN = HttpURLConnection.HTTP_FORBIDDEN;

    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final String HEAD = "HEAD";
    public static final String OPTIONS = "OPTIONS";
    public static final String PUT = "PUT";
    public static final String DELETE = "DELETE";
    public static final String TRACE = "TRACE";
//#endif

//#ifdef j2me
    private HttpConnection conn;
//#else
    private HttpURLConnection conn;
//#endif

    private String url;

    private Hashtable requestHeaders;
    private Hashtable responseHeaders = new Hashtable();

    private int responseCode = -1;
    private int status = SETUP;
    private byte[] responseData;
    private String responseBody = "";

    private String qdata;
    private QueryParam[] qparams;
    private String requestMethod =
//#ifdef j2me
            HttpConnection.GET
//#else
            GET
//#endif
        ;

    private Exception error;

    public HttpRequest(String url, QueryParam[] params,
                       boolean validate, Hashtable headerfields) {
        this.url = url;
        this.requestHeaders = headerfields;
        this.qparams = params;
        setup(validate);
    }

    public HttpRequest(String url, String queryData,
                       boolean validate, Hashtable headerfields) {
        this.url = url;
        this.requestHeaders = headerfields;
        this.qdata = queryData;
        setup(validate);
    }

    private void setup(boolean validate) {
        if (qdata != null || qparams != null) {
            requestMethod =
//#ifdef j2me
                    HttpConnection.POST
//#else
                    POST
//#endif
                ;
        } else {
            if (validate)
                requestMethod =
//#ifdef j2me
                        HttpConnection.HEAD
//#else
                        HEAD
//#endif
                    ;
        }
    }

    public String getURL() {
        return url;
    }

    public static String hexdump(byte[] buf) {
        final StringBuffer sb = new StringBuffer();

        for (int i = 0, N = buf.length; i < N; ++i) {
            final byte b = buf[i];
            sb.append(Integer.toHexString((b & 0xf0) >> 4));
            sb.append(Integer.toHexString(b & 0x0f));
            sb.append(' ');
            if (i != 0 && ((i + 1) % 8) == 0)
                sb.append(' ');
            if (i != 0 && ((i + 1) % 16) == 0)
                sb.append('\n');
        }
        if (sb.charAt(sb.length() - 1) != '\n')
            sb.append('\n');

        return sb.toString();
    }

    public void run() {
        try {
//#ifdef j2me
            conn = (HttpConnection) Connector.open(url);
//#else
            conn = (HttpURLConnection) (new URL(url)).openConnection();
//#endif
            connect();
            status = CONNECTED;
            responseCode = conn.getResponseCode();
            responseHeaders = readHeaders();
            responseData = readContent();
            responseBody = processContent();
            status = OK;
        } catch (Exception e) {
            e.printStackTrace();
            error = e;
            status = ERROR;
        } finally {
//#ifdef j2me
            try {
//#endif
//#ifndef j2me
                conn.disconnect();
//#else
                conn.close();
//#endif
//#ifdef j2me
            } catch (Exception e) {
                // ignore
            }
//#endif
        }
    }

    public static byte[] asISOBytes(String s) {
        final byte[] buf = new byte[s.length()];
        for (int i = 0; i < s.length(); ++i) {
            final char ch = s.charAt(i);
            buf[i] = (byte) ch;
        }
        return buf;
    }

    public static String bytesToString(byte[] buf) {
        return bytesToString(buf, 0, buf.length);
    }

    public static String bytesToString(byte[] buf, int start, int n) {
        return bytesToStringBuffer(buf, start, n).toString();
    }

    public static StringBuffer bytesToStringBuffer(byte[] buf) {
        return bytesToStringBuffer(buf, 0, buf.length);
    }

    public static StringBuffer bytesToStringBuffer(byte[] buf, int start, int n) {
        final StringBuffer sb = new StringBuffer(buf.length);
        for (int i = start; n > 0; ++i, --n) {
            sb.append((char) buf[i]);
        }
        return sb;
    }

    public int getStatus() {
        return status;
    }

    public Exception getException() {
        return error;
    }

    public String getBody() {
        return responseBody;
    }

//#ifdef notdef
    public byte[] getBytes() {
        return responseData;
    }
//#endif

    public int getRC() {
        return responseCode;
    }

    public static String getStatusText(int status) {
        switch (status) {
            case SETUP:
                return "setup";
            case CONNECTED:
                return "connected";
            case ERROR:
                return "error";
            case TIMEOUT:
                return "timeout";
            case OK:
                return "ok";
            default:
                return "unknown";
        }
    }

    public String getResponseFieldValue(String key) {
        return (String) responseHeaders.get(key);
    }

    public Enumeration getResponseFieldNames() {
        return responseHeaders.keys();
    }

    public static byte[] IRIencode(String str) {
        final int strlen = str.length();
        final char[] charr = new char[strlen];
        int utflen = 0;
        int c, count = 0;

        str.getChars(0, strlen, charr, 0);

        for (int i = 0; i < strlen; i++) {
            c = charr[i];
            if ((c >= 0x0001) && (c <= 0x007F)) {
                utflen++;
            } else if (c > 0x07FF) {
                utflen += 3;
            } else {
                utflen += 2;
            }
        }

        final byte[] bytearr = new byte[utflen];
        for (int i = 0; i < strlen; i++) {
            c = charr[i];
            if ((c >= 0x0001) && (c <= 0x007F)) {
                bytearr[count++] = (byte) c;
            } else if (c > 0x07FF) {
                bytearr[count++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
                bytearr[count++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                bytearr[count++] = (byte) (0x80 | ((c >> 0) & 0x3F));
            } else {
                bytearr[count++] = (byte) (0xC0 | ((c >> 6) & 0x1F));
                bytearr[count++] = (byte) (0x80 | ((c >> 0) & 0x3F));
            }
        }
        return bytearr;
    }

    public static String urlencode(byte[] s, int start, int n) {
        final StringBuffer sb = new StringBuffer();
        for (int i = start; n > 0; ++i, --n) {
            sb.append(urlencodemap[s[i] & 0xff]);
        }
        return sb.toString();
    }

    public static String urlencode(byte[] s) {
        return urlencode(s, 0, s.length);
    }

    public static String urlencode(String[] elems) {
        if (elems == null || elems.length == 0)
            return null;

        final StringBuffer sb = new StringBuffer();
        for (int i = 0, N = elems.length; i < N; ++i) {
            if (i > 0) {
                sb.append((i % 2) != 0 ? '=' : '&');
            }
            sb.append(urlencode(IRIencode(elems[i])));
        }
        return sb.toString();
    }

    private void connect() throws IOException {
        conn.setRequestMethod(requestMethod);
        for (Enumeration e = requestHeaders.keys(); e.hasMoreElements(); ) {
            final String key = (String) e.nextElement();
            conn.setRequestProperty(key, (String) requestHeaders.get(key));
        }
//#ifndef j2me
        if (qdata != null || qparams != null) {
            conn.setDoOutput(true);
        }
        // Only JDK: Calling connect will open the connection
        conn.connect();
//#endif
        if (qdata != null || qparams != null) {
            OutputStream os = null;
            try {
//#ifdef j2me
                os = conn.openOutputStream();
//#else
                os = conn.getOutputStream();
//#endif
                if (qdata != null) {
                    os.write(qdata.getBytes(/*DEFCHARSET*/));
                } else if (qparams != null) {
                    for (int i = 0, N = qparams.length; i < N; ++i) {
                        if (i != 0) {
                            os.write('&');
                        }
                        qparams[i].send(os);
                    }
                }
                os.flush();
            } finally {
                try {
                    os.close();
                } catch (Exception exc) { // NPE or IOE
                    // ignore
                }
            }
        }
    }

    private Hashtable readHeaders() {
        final Hashtable headers = new Hashtable();
        // Some implementations may treat the 0th header field as special,
        // i.e. as the status line returned by the HTTP server.
        // In this case, getHeaderField(0) returns the status line,
        // but getHeaderFieldKey(0) returns null.
        // For now, it is not clear if this happens on midlets as well.
        int idx = 0;
//#ifndef j2me
        if (conn.getHeaderFieldKey(0) == null) {
            ++idx;
        }
//#endif
        String key = "";
        while (key != null) {
//#ifdef j2me
            try {
//#endif
                key = conn.getHeaderFieldKey(idx++);
                if (key != null) {
                    headers.put(key.toLowerCase(), conn.getHeaderField(key));
                }
//#ifdef j2me
            } catch (IOException e) {
                // ignore
            }
//#endif
        }
        return headers;
    }

    private byte[] readContent() throws IOException {
        int len;
//#ifdef j2me
        len = (int) conn.getLength();
//#else
        len = conn.getContentLength();
//#endif
        if (len != 0) {
            InputStream is = null;
            try {
//#ifdef j2me
                is = conn.openInputStream();
//#else
                is = conn.getInputStream();
//#endif
                final int BUFSIZE = 1024;
                final ByteArrayOutputStream baos = new ByteArrayOutputStream(len > -1 ? len : BUFSIZE);
                final byte[] buf = new byte[BUFSIZE];
                int c = is.read(buf, 0, BUFSIZE);
                while (c > -1) {
                    baos.write(buf, 0, c);
                    c = is.read(buf, 0, BUFSIZE);
                }
                return baos.toByteArray();
            } finally {
                try {
                    is.close();
                } catch (Exception e) { // NPE or IOE
                    // ignore
                }
            }
        }

        return NOBODY;
    }

    private String processContent() {
        if (responseData == null || responseData.length == 0) {
            return "";
        }

        // string result
        String result = null;

        // if no charset is given, use the default (see below).
        String charset = DEFCHARSET;
        String ct = conn.getType(); //(String) responseHeaders.get(CONTENTTYPE);
        String coding = conn.getEncoding(); // (String) responseHeaders.get(CONTENTENCODING);

        // handle text content
        if (coding == null && isTextType(ct)) {
            // textual transfer
            responseHeaders.put("binary", "0");
            // get charset
            if (ct != null) {
                int begin = ct.toLowerCase().indexOf("charset=");
                if (begin >= 0) {
                    // In a midlet, an empty encoding string would result
                    // in an UnsupportedEncodingException when creating a
                    // string object.
                    begin += 8;        // # of chars in 'charset='
                    int end = ct.indexOf(';', begin);
                    if (end == -1) {
                        end = ct.length();
                    }
                    charset = ct.substring(begin, end);
                }
            }
            // charset is now detected, create a string holding the result.
            if (charset == DEFCHARSET || isISOCharset(charset)) {
                result = bytesToString(responseData, 0, responseData.length);
                charset = DEFCHARSET;
                responseHeaders.put("charset", charset);
            } else {
                for (int i = 0; i < 3; ++i) {
                    switch (i) {
                        case 0:
                            break;
                        case 1:
                            charset = charset.toLowerCase();
                            break;
                        case 2:
                            charset = charset.toUpperCase();
                            break;
                    }
                    responseHeaders.put("charset", charset);
                    try {
                        result = new String(responseData, charset);
                        break;
                    } catch (Exception e) {
                        result = "xxx-encoding-failed-xxx\n" + e.getMessage();
                    }
                }
            }
        } else {
            // binary transfer
            responseHeaders.put("binary", "1");
        }

        return result;
    }

    private boolean isISOCharset(String charset) {
        final String lower = charset.toLowerCase();
        for (int i = 0, N = ISOALIASES.length; i < N; ++i) {
            if (lower.equals(ISOALIASES[i]))
                return true;
        }
        return false;
    }

    private static boolean isTextType(String ct) {
        return ((ct != null) &&
                (ct.startsWith("text/") || ct.startsWith("application/javascript")
                || ct.startsWith("application/json") || ct.startsWith("application/xml")));
    }

    private static String[] urlencodemap = new String[256];
    private static String validUrlChars =
            "-_.!~*'()\"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final char[] hexchars = "0123456789ABCDEF".toCharArray();
    private static final String ISOALIASES[] = {
            "iso-8859-1", "iso8859-1", "iso8859_1", "iso_8859_1", "iso-8859_1", "iso_8859-1"
    };

    public static String DEFCHARSET = "ISO8859-1";
    public static final String CONTENTTYPE = "content-type";
    public static final String CONTENTENCODING = "content-encoding";
    public static final byte[] NOBODY = new byte[0];

    static {
        final char[] cbuf = new char[3];
        for (int i = 0; i < 256; i++) {
            final char ch = (char) i;
            final int idx = validUrlChars.indexOf(ch);
            if (idx >= 0) {
                urlencodemap[i] = validUrlChars.substring(idx, idx + 1);
            } else {
                // !!! Do not use
                // urlencodemap[i] = "%" + Integer.toHexString(i);
                // since it does not print leading 0s
                cbuf[0] = '%';
                cbuf[1] = hexchars[(i & 0xf0) >> 4];
                cbuf[2] = hexchars[i & 0x0f];
                urlencodemap[i] = new String(cbuf);
            }
        }
        urlencodemap[' '] = "+";
        //urlencodemap['\n'] = "%0D%0A";
    }
}
