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

package cz.kruch.track.maps;

import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.io.LineReader;
import cz.kruch.track.ui.Position;

import java.io.InputStream;
import java.io.IOException;
import java.util.Vector;

import api.location.QualifiedCoordinates;

final class GmiCalibration extends Calibration {
    private static final char[] DELIM = {';'};

    GmiCalibration() {
        super();
    }

    void init(InputStream in, String path) throws IOException {
        super.init(path);

        Vector xy = new Vector();
        Vector ll = new Vector();
        CharArrayTokenizer tokenizer = new CharArrayTokenizer();

        // text reader
        LineReader reader = new LineReader(in);

        // base info
        reader.readLine(false); // ignore - intro line
        reader.readLine(false); // ignore - path to image file
        width = Integer.parseInt(reader.readLine(false)); // image width
        height = Integer.parseInt(reader.readLine(false)); // image width

        // additional data
        String line = reader.readLine(false);
        while (line != null) {
            if (line.startsWith("Additional Calibration Data"))
                break;
            if (line.startsWith("Border and Scale")) {
                line = reader.readLine(false);
                if (line != null) {
                    xy.removeAllElements();
                    ll.removeAllElements();
                    tokenizer.init(line, DELIM, false);
                    parseBorder(tokenizer, xy, ll);
                }
                break;
            }
            if (line == LineReader.EMPTY_LINE) // '==' is ok
                break;

            tokenizer.init(line, DELIM, false);
            parsePoint(tokenizer, xy, ll);

            line = null; // gc hint
            line = reader.readLine(false);
        }

        // close reader
        reader.close();

        // gc hints
        reader = null;
        tokenizer = null;

        doFinal(null, LATLON_PROJ_SETUP, xy, ll);

        // gc hints
        xy.removeAllElements();
        ll.removeAllElements();
    }

    private static void parsePoint(CharArrayTokenizer tokenizer, Vector xy, Vector ll) {
        int x = tokenizer.nextInt();
        int y = tokenizer.nextInt();
        double lon = tokenizer.nextDouble();
        double lat = tokenizer.nextDouble();
        xy.addElement(Position.newInstance(x, y));
        ll.addElement(QualifiedCoordinates.newInstance(lat, lon));
    }

    private void parseBorder(CharArrayTokenizer tokenizer, Vector xy, Vector ll) {
        double lat = tokenizer.nextDouble();
        double lon = tokenizer.nextDouble();
        xy.addElement(Position.newInstance(0, 0));
        ll.addElement(QualifiedCoordinates.newInstance(lat, lon));
        lat = tokenizer.nextDouble();
        lon = tokenizer.nextDouble();
        xy.addElement(Position.newInstance(width - 1, height - 1));
        ll.addElement(QualifiedCoordinates.newInstance(lat, lon));
    }
}
