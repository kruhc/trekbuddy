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

import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.io.IOException;
import java.util.Vector;

import cz.kruch.track.ui.Position;
import api.location.QualifiedCoordinates;

class J2NCalibration extends Calibration {
    private static final String TAG_NAME        = "name";
    private static final String TAG_POSITION    = "position";
    private static final String TAG_LATITUDE    = "latitude";
    private static final String TAG_LONGITUDE   = "longitude";
    private static final String TAG_IMAGEWIDTH  = "imageWidth";
    private static final String TAG_IMAGEHEIGHT = "imageHeight";

    J2NCalibration() {
        super();
    }

    void init(InputStream in, String path) throws InvalidMapException {
        super.init(path);

        Vector xy = new Vector();
        Vector ll = new Vector();
        KXmlParser parser = new KXmlParser(null);

        try {
            parser.setInput(in, null); // null is for encoding autodetection

            boolean keepParsing = true;
            String currentTag = null;

            int x0 = -1, y0 = -1;
            double lat0 = 0D, lon0 = 0D;

            while (keepParsing) {
                switch (parser.next()) {
                    case XmlPullParser.START_TAG: {
                        currentTag = parser.getName();
                        if (TAG_POSITION.equals(currentTag)) {
                            x0 = Integer.parseInt(parser.getAttributeValue(null, "x"));
                            y0 = Integer.parseInt(parser.getAttributeValue(null, "y"));
                        }
                    }
                    break;
                    case XmlPullParser.END_TAG: {
                        if (TAG_POSITION.equals(parser.getName())) {
                            xy.addElement(Position.newInstance(x0, y0));
                            ll.addElement(QualifiedCoordinates.newInstance(lat0, lon0));
                        }
                        currentTag = null;
                    }
                    break;
                    case XmlPullParser.TEXT: {
                        if (currentTag != null) {
                            String text = parser.getText().trim();
                            if (TAG_NAME.equals(currentTag)) {
                                imgname = text;
                            } else if (TAG_LATITUDE.equals(currentTag)) {
                                lat0 = Double.parseDouble(text);
                            } else if (TAG_LONGITUDE.equals(currentTag)) {
                                lon0 = Double.parseDouble(text);
                            } else if (TAG_IMAGEWIDTH.equals(currentTag)) {
                                width = Integer.parseInt(text);
                            } else if (TAG_IMAGEHEIGHT.equals(currentTag)) {
                                height = Integer.parseInt(text);
                            }
                        }
                    }
                    break;
                    case XmlPullParser.END_DOCUMENT: {
                        keepParsing = false;
                    }
                    break;
                }
            }

        } catch (Exception e) {
            throw new InvalidMapException(e);
        } finally {
            try {
                parser.close();
            } catch (IOException e) {
                // ignore
            }
        }

        // finalize
        doFinal(null, LATLON_PROJ_SETUP, xy, ll);

        // gc hints
        xy.removeAllElements();
        ll.removeAllElements();
    }
}
