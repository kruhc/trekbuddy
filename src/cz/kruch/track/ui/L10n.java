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

package cz.kruch.track.ui;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.io.LineReader;

import java.util.Hashtable;
import java.io.InputStream;
import java.io.IOException;

import javax.microedition.io.Connector;

/**
 * Localization helper.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class L10n {
    public static final String MENU_START       = "menu.start";
    public static final String MENU_STOP        = "menu.stop";
    public static final String MENU_PAUSE       = "menu.pause";
    public static final String MENU_CONTINUE    = "menu.continue";
    public static final String MENU_LOADMAP     = "menu.loadmap";
    public static final String MENU_LOADATLAS   = "menu.loadatlas";
    public static final String MENU_SETTINGS    = "menu.settings";
    public static final String MENU_INFO        = "menu.info";
    public static final String MENU_EXIT        = "menu.exit";

    private static final Hashtable table = new Hashtable(16);

    public static int initialize() throws IOException {
        int result = 0;

        InputStream in = null;
        try {
            in = Connector.openInputStream(Config.getFolderResources() + "L10n.properties");
            result++;
        } catch (Exception e) {
            // ignore
        }
        if (in == null) {
            in = cz.kruch.track.TrackingMIDlet.class.getResourceAsStream("/resources/L10n.properties");
        }

        LineReader reader = null;
        StringBuffer sb = null;

        try {
            reader = new LineReader(in);
            String entry = reader.readLine(false);
            while (entry != null) {
                if (!entry.startsWith("#")) {
                    final int i = entry.indexOf('=');
                    if (i > -1) {
                        String key = entry.substring(0, i);
                        String value = entry.substring(i + 1);
                        if (value.indexOf('\\') > -1) {
                            if (sb == null) {
                                sb = new StringBuffer(24);
                            }
                            try {
                                value = convert(value, sb);
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                        table.put(key, value);
                    }
                }
                entry = reader.readLine(false);
            }
        } finally {
            // close reader (closes the file stream)
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        return result;
    }

    public static String resolve(String key) {
        String value = (String) table.get(key);
        if (value == null) {
            return key;
        }
        return value;
    }

    private static String convert(String value, StringBuffer sb) {
        sb.delete(0, sb.length());
        for (int N = value.length(), i = 0; i < N; ) {
            char c = value.charAt(i++);
            if (c == '\\') {
                c = value.charAt(i++);
                if (c == 'u') {
                    int unicode = 0;
        		    for (int j = 4; --j >= 0; ) {
		                c = value.charAt(i++);
                        switch (c) {
                            case '0':
                            case '1':
                            case '2':
                            case '3':
                            case '4':
                            case '5':
                            case '6':
                            case '7':
                            case '8':
                            case '9':
                                unicode = (unicode << 4) + c - '0';
                            break;
                            case 'a':
                            case 'b':
                            case 'c':
                            case 'd':
                            case 'e':
                            case 'f':
                                unicode = (unicode << 4) + 10 + c - 'a';
                            break;
                            case 'A':
                            case 'B':
                            case 'C':
                            case 'D':
                            case 'E':
                            case 'F':
                                unicode = (unicode << 4) + 10 + c - 'A';
                            break;
                            default:
                                throw new IllegalArgumentException("Malformed \\uxxxx encoding.");
                        }
                    }
                    sb.append((char) unicode);
                } else {
                    sb.append('\\').append(c);
                }
            } else
                sb.append(c);
        }
        return sb.toString();
    }
}
