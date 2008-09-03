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

import api.location.QualifiedCoordinates;
import api.location.Location;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.util.ExtraMath;
import cz.kruch.track.Resources;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Font;

/**
 * HPS.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class LocatorView extends View {
    private static final String MSG_NO_WAYPOINT = Resources.getString(Resources.DESKTOP_MSG_NO_WPT);

    private static final int[] RANGES = {
        1000, 500, 250, 100, 50, 25, 10, 5
    };
    private static final String[] RANGES_STR = {
        "1000 m", "500 m", "250 m", "100 m", "50 m", "25 m", "10 m", "5 m"
    };

    private static final int COLOR_RANGE        = 0x00808080;
    private static final int COLOR_NO_POSITION  = 0x00FF0000;
    private static final int COLOR_MIDST        = 0x00E0E000;
    private static final int COLOR_AVGT         = 0x0000ff00;
    private static final int COLOR_NONAVGT      = 0x0000ffff;

    private static final int HISTORY_DEPTH = 20;

    private final Location[][] locations;
    private int[] count;
    private int[] position;

//    private final QualifiedCoordinates[] coordinatesAvg;
//    private final int[] satAvg;
    private final int[] rangeIdx;

    private int term;

    private int dx, dy;
    private int lineLength;

    private float lastCourse = Float.NaN;

    private final int navigationStrWidth;

    private final int[] center, vertex;
    private final int[][] triangle;

    private final StringBuffer sb;
    private final char[] sbChars;

    LocatorView(/*Navigator*/Desktop navigator) {
        super(navigator);
        this.locations = new Location[2][];
        this.locations[0] = new Location[HISTORY_DEPTH];
        this.locations[1] = new Location[HISTORY_DEPTH];
//        this.coordinatesAvg = new QualifiedCoordinates[2];
//        this.satAvg = new int[2];
        this.count = new int[2];
        this.position = new int[2];
        this.rangeIdx = new int[]{ 2, 2 };
        this.navigationStrWidth = Math.max(Desktop.font.stringWidth(MSG_NO_WAYPOINT),
                                           Desktop.font.stringWidth("9.999 M"));
        this.center = new int[2];
        this.vertex = new int[2];
        this.triangle = new int[3][2];
        this.sb = new StringBuffer(32);
        this.sbChars = new char[32];
        reset();
    }

    public void sizeChanged(int w, int h) {
        this.lineLength = Math.min(w - w / 10, h - h / 10);
        this.dx = (w - lineLength) >> 1;
        this.dy = (h - lineLength) >> 1;
        this.center[0] = w >> 1;
        this.center[1] = h >> 1;
    }

    public void reset() {
        for (int i = 2; --i >= 0; ) {
            final Location[] array = locations[i];
            for (int j = HISTORY_DEPTH; --j >= 0; ) {
                if (array[j] != null) {
                    Location.releaseInstance(array[j]);
                    array[j] = null; // gc hint
                }
            }
            count[i] = position[i] = 0;
        }
        lastCourse = Float.NaN;
    }

    public int locationUpdated(Location l) {
        // only fix is good enough
        if (l.getFix() > 0) {

            // update array
            append(0, l.clone());

            // recalc
            recalc(l.getTimestamp());
        }

        return Desktop.MASK_SCREEN;
    }

    public int handleAction(final int action, final boolean repeated) {
        if (repeated) {
            return 0;
        }

        switch (action) {
            case Canvas.LEFT:
                if (rangeIdx[term] > 0) {
                    rangeIdx[term]--;
                }
                break;
            case Canvas.RIGHT:
                if (rangeIdx[term] < (RANGES.length - 1)) {
                    rangeIdx[term]++;
                }
                break;
            case Canvas.UP:
            case Canvas.DOWN: {
                term = term == 0 ? 1 : 0;
            } break;
        }

        return Desktop.MASK_ALL;
    }

    private void append(final int term, final Location l) {
        final Location[] array = this.locations[term];
        final int[] count = this.count;
        int position = this.position[term];

        // rotate
        if (++position == HISTORY_DEPTH) {
            position = 0;
        }

        // release previous
        if (array[position] != null) {
            Location.releaseInstance(array[position]);
            array[position] = null; // gc hint
        }

        // save location
        array[position] = l;

        // update term position
        this.position[term] = position;

        // update term counter
        count[term]++;
        if (count[term] > HISTORY_DEPTH) {
            count[term] = HISTORY_DEPTH;
        }
    }

    private void recalc(final long timestamp) {
        // local ref for faster access
        final Location[] array = locations[0];

        // calc avg values
        double latAvg = 0D, lonAvg = 0D;
        float accuracySum = 0F, wSum = 0F/*, altAvg = 0F*/;
        int c = 0/*, satSum = 0*/;

        // calculate avg qcoordinates
        for (int i = HISTORY_DEPTH; --i >= 0; ) {
            final Location l = array[i];
            if (l != null) {
                final QualifiedCoordinates qc = l.getQualifiedCoordinates();
                final float hAccuracy = qc.getHorizontalAccuracy();
/*
                System.out.println("haccuracy = " + hAccuracy);
*/
                if (!Float.isNaN(hAccuracy)) {
                    final float w = 5F / hAccuracy;
                    accuracySum += hAccuracy;
//                  satSum += l.getSat();
                    latAvg += qc.getLat() * w;
                    lonAvg += qc.getLon() * w;
//                  altAvg += qc.getAlt();
                    wSum += w;
                    c++;
                }
            }
        }
        if (c > 0) {
            latAvg /= wSum;
            lonAvg /= wSum;
//            altAvg /= c;
/*
            QualifiedCoordinates.releaseInstance(coordinatesAvg[0]);
            coordinatesAvg[0] = null; // gc hint
            coordinatesAvg[0] = QualifiedCoordinates.newInstance(latAvg, lonAvg);
            coordinatesAvg[0].setHorizontalAccuracy(accuracySum / c);
*/
//            satAvg[term] = satSum / c;

            final QualifiedCoordinates qc = QualifiedCoordinates.newInstance(latAvg, lonAvg);
            qc.setHorizontalAccuracy(accuracySum / c);
            final Location l = Location.newInstance(qc, timestamp, -1);
            append(1, l);
        }

/*
        // set non-avg qcoordinates - it is last position
        QualifiedCoordinates.releaseInstance(coordinatesAvg[1]);
        coordinatesAvg[1] = null; // gc hint
        coordinatesAvg[1] = locations[position].getQualifiedCoordinates().clone();

        // remember number of valid position in array
        count = c;
*/
    }

    public int changeDayNight(final int dayNight) {
        return Desktop.MASK_SCREEN; 
    }

    public void render(final Graphics graphics, final Font font, final int mask) {
        // local references for faster access
        final int w = Desktop.width;
        final int h = Desktop.height;
        final int wHalf = w >> 1;
        final int hHalf = h >> 1;
        final int term = this.term;
        final int rangeIdx = this.rangeIdx[term];
        final OSD osd = Desktop.osd;
        final int fh = osd.bh;

        // main colors
        final int bgColor, fgColor, wptColor;
        if (Config.dayNight == 0) {
            bgColor = 0x00ffffff;
            fgColor = 0x00000000;
            wptColor = 0x00b28006;
        } else {
            bgColor = 0x00000000;
            fgColor = 0x00ffffff;
            wptColor = 0x00e0e00b;
        }

        // clear
        graphics.setFont(font);
        graphics.setColor(bgColor);
        graphics.fillRect(0,0, w, h);
        graphics.setStrokeStyle(Graphics.SOLID);

        // draw crosshair
        graphics.setColor(0x00404040);
        graphics.drawLine(wHalf, dy, wHalf, h - dy);
        graphics.drawLine(dx, hHalf, w - dx, hHalf);

        // draw compas
        float course;

        /* block */ {
            final Location current = this.locations[0][this.position[0]];
            if (current == null) {
                course = Float.NaN;
            } else {
                course = current.getCourse();
            }
            final boolean fresh;
            if (Float.isNaN(course)) {
                course = lastCourse;
                fresh = false;
            } else {
                lastCourse = course;
                fresh = true;
            }
            drawCompas(wHalf, hHalf, fh, graphics, course, fresh);
        } /* ~ */

        // compas boundary
        graphics.setColor(0x00005050);
        graphics.drawArc(dx + fh, dy + fh,
                         lineLength - (fh << 1),
                         lineLength - (fh << 1),
                         0, 360);

        // wpt index
        final Waypoint wpt = navigator.getNavigateTo();

        // draw points
        if (count[term] > 0) {

            // local refs
            final Location[] locations = this.locations[term];
            final QualifiedCoordinates coordsAvg = locations[this.position[term]].getQualifiedCoordinates();
            final double latAvg = coordsAvg.getLat();
            final double lonAvg = coordsAvg.getLon();
            final int[] xy = this.vertex;
            final int[] center = this.center;
            final StringBuffer sb = this.sb;
            final char[] sbChars = this.sbChars;

            // get scales
            final double v = ((double) RANGES[rangeIdx]) / 111319.490D;
            final double yScale = ((double) (lineLength >> 1)) / (v);
            final double xScale = ((double) (lineLength >> 1)) / (v / Math.cos(Math.toRadians(latAvg)));

            // points color
            final int inc = (192/* = 256 - 64*/) / HISTORY_DEPTH;
            int cstep = inc;
            cstep <<= 8;
            if (term > 0) cstep += inc;
            int color = (term == 0 ? COLOR_AVGT : COLOR_NONAVGT) - this.count[term] * cstep;

            // draw points
            int offset = this.position[term] + 1; // to start from the oldest
            if (offset == HISTORY_DEPTH) {
                offset = 0;
            }
            for (int N = HISTORY_DEPTH, i = N; --i >= 0; ) {
                Location l = locations[offset++];
                if (offset == N) {
                    offset = 0;
                }
                if (l != null) {
                    // set vertex
                    final QualifiedCoordinates qc = l.getQualifiedCoordinates();
                    xy[0] = wHalf + (int) ((qc.getLon() - lonAvg) * xScale);
                    xy[1] = hHalf - (int) ((qc.getLat() - latAvg) * yScale);

                    // tranform
                    if (course > 1F) {
                        transform(center, course, xy);
                    }

                    // wtf is this... :-?
                    xy[0] -= 4;
                    xy[1] -= 4;

                    // draw point
                    graphics.setColor(color);
                    if (i == 0) {
                        graphics.fillArc(xy[0]/* - 4*/, xy[1]/* - 4*/, 9, 9, 0, 360);
                    }
                    graphics.drawArc(xy[0]/* - 4*/, xy[1]/* - 4*/, 9, 9, 0, 360);
                    color += cstep;

                    // gc hints
                    l = null;
                }
            }

            /*
             * draw calculated (avg) position
             */

            // set color
            graphics.setColor(fgColor);

            // draw lat/lon
            sb.delete(0, sb.length());
            NavigationScreens.printTo(coordsAvg, sb);
            int l = sb.length();
            sb.getChars(0, l, sbChars, 0);
            graphics.drawChars(sbChars, 0, l, OSD.BORDER, 0, Graphics.LEFT | Graphics.TOP);

            // draw hdop
            final float hAccuracy = coordsAvg.getHorizontalAccuracy();
            if (!Float.isNaN(hAccuracy)) {
                sb.delete(0, sb.length());
                sb.append(NavigationScreens.PLUSMINUS);
                if (hAccuracy >= 10F) {
                    NavigationScreens.append(sb, (int) hAccuracy);
                } else {
                    NavigationScreens.append(sb, hAccuracy, 1);
                }
                sb.append(' ').append('m');
                l = sb.length();
                sb.getChars(0, l, sbChars, 0);
                graphics.drawChars(sbChars, 0, l, OSD.BORDER, fh, 0);
            }

            // draw sat
/* makes little sense
            if (satAvg[term] > 0) {
                // same position as in OSD
                graphics.drawString(NavigationScreens.nStr[satAvg[term]],
                                    osd.width - OSD.BORDER - (satAvg[term] < 10 ? osd.str1Width : osd.str2Width),
                                    osd.gy + osd.bh,
                                    Graphics.LEFT | Graphics.TOP);
            }
*/

            // draw central point
            graphics.setColor(COLOR_MIDST);
            graphics.drawArc(wHalf - 4, hHalf - 4, 9, 9, 0, 360);

            // wtp color
            graphics.setColor(wptColor);

            // draw waypoint
            if (wpt != null) {
                final QualifiedCoordinates qc = wpt.getQualifiedCoordinates();
                xy[0] = wHalf + (int) ((qc.getLon() - lonAvg) * xScale);
                xy[1] = hHalf - (int) ((qc.getLat() - latAvg) * yScale);

                // transform vertex
                boolean onScreen = true;
                if (course > 1F) {
                    onScreen = transform(center, course, xy);
                }

                // calculate distance
                final float distance = coordsAvg.distance(qc);

                // calculate bearing
                float bearing = coordsAvg.azimuthTo(qc, distance);
                float arrowa;
                if (course > 0F) { // relative to top (moving direction)
                    arrowa = bearing + 360 - course;
                } else {
                    arrowa = bearing;
                }

                // draw icons
                if (onScreen) {
                    NavigationScreens.drawWaypoint(graphics, xy[0], xy[1],
                                                   Graphics.LEFT | Graphics.TOP);
                }
                NavigationScreens.drawArrow(NavigationScreens.ARROW_NAVI,
                                            graphics, arrowa, wHalf, hHalf,
                                            Graphics.LEFT | Graphics.TOP);

                // construct distance string
                sb.delete(0, sb.length());
                NavigationScreens.printDistance(sb, distance);
                l = sb.length();
                sb.getChars(0, l, sbChars, 0);

                // draw distance
                graphics.drawChars(sbChars, 0, l,
                                   w - navigationStrWidth, h - (fh << 1),
                                   Graphics.LEFT | Graphics.TOP);

                // true or relative wpt-azi?
                if (!Config.hpsWptTrueAzimuth) {
                    bearing = arrowa % 360;
                }
                
                // draw azimuth
                sb.delete(0, sb.length());
                NavigationScreens.append(sb, (int) bearing).append(' ').append(NavigationScreens.SIGN);
                l = sb.length();
                sb.getChars(0, l, sbChars, 0);
                graphics.drawChars(sbChars, 0, l,
                                   w - navigationStrWidth, h - fh,
                                   Graphics.LEFT | Graphics.TOP);
            } else {
                graphics.drawString(MSG_NO_WAYPOINT,
                                   w - navigationStrWidth, h - fh,
                                   Graphics.LEFT | Graphics.TOP);
            }
        } else {
            graphics.setColor(COLOR_NO_POSITION);
            graphics.drawString(MSG_NO_POSITION,
                                OSD.BORDER, 0, Graphics.LEFT | Graphics.TOP);
            if (wpt == null) {
                graphics.setColor(wptColor);
                graphics.drawString(MSG_NO_WAYPOINT,
                                   w - navigationStrWidth, h - fh,
                                   Graphics.LEFT | Graphics.TOP);
            }
        }

        // draw provider status
        NavigationScreens.drawProviderStatus(graphics, osd.providerStatus,
                                             osd.semaforX, osd.semaforY,
                                             Graphics.LEFT | Graphics.TOP);

        // draw range
        graphics.setColor(COLOR_RANGE);
        graphics.drawLine(dx, h - 4, dx, h - 2);
        graphics.drawLine(dx, h - 3, wHalf, h - 3);
        graphics.drawLine(wHalf, h - 4, wHalf, h - 2);
        graphics.drawString(RANGES_STR[rangeIdx],
                            dx + 3, h - fh - 2,
                            Graphics.LEFT | Graphics.TOP);

/*
        // flush
        flushGraphics();
*/
    }

    private void drawCompas(final int width2, final int height2, final int fh,
                            final Graphics g, final float course, final boolean uptodate) {
        final int[][] triangle = this.triangle;

        triangle[0][0] = width2;
        triangle[0][1] = height2;
        triangle[1][0] = width2;
        triangle[1][1] = dy + lineLength - fh;
        triangle[2][0] = width2 + 7;
        triangle[2][1] = height2;

        g.setColor(0x00909090);
        drawTriangle(g, course, triangle);

        triangle[0][0] = width2 - 7;
        triangle[0][1] = height2;
        triangle[1][0] = width2;
        triangle[1][1] = dy + lineLength - fh;
        triangle[2][0] = width2;
        triangle[2][1] = height2;

        g.setColor(0x00707070);
        drawTriangle(g, course, triangle);

        triangle[0][0] = dx + fh;
        triangle[0][1] = height2;
        triangle[1][0] = triangle[0][0] + 9;
        triangle[1][1] = height2 - 3;
        triangle[2][0] = triangle[1][0];
        triangle[2][1] = height2 + 3;

        drawTriangle(g, course, triangle);

        triangle[0][0] = dx + lineLength - fh;
        triangle[0][1] = height2;
        triangle[1][0] = triangle[0][0] - 9;
        triangle[1][1] = height2 - 3;
        triangle[2][0] = triangle[1][0];
        triangle[2][1] = height2 + 3;

        drawTriangle(g, course, triangle);
        triangle[0][0] = width2 - 7;
        triangle[0][1] = height2;
        triangle[1][0] = width2;
        triangle[1][1] = dy + fh;
        triangle[2][0] = width2;
        triangle[2][1] = triangle[0][1];

        if (uptodate) {
            g.setColor(0x00A00000);
        }
        drawTriangle(g, course, triangle);

        triangle[0][0] = width2;
        triangle[0][1] = height2;
        triangle[1][0] = width2;
        triangle[1][1] = dy + fh;
        triangle[2][0] = width2 + 7;
        triangle[2][1] = triangle[0][1];

        if (uptodate) {
            g.setColor(0x00D00000);
        }
        drawTriangle(g, course, triangle);
    }

    private void drawTriangle(final Graphics g, final float bearing, final int[][] triangle) {
        if (bearing > 1F) {
            final int[] center = this.center;
            transform(center, bearing, triangle[0]);
            transform(center, bearing, triangle[1]);
            transform(center, bearing, triangle[2]);
        }
        g.fillTriangle(triangle[0][0], triangle[0][1],
                       triangle[1][0], triangle[1][1],
                       triangle[2][0], triangle[2][1]);
    }

    private static boolean transform(final int[] center,
                                     final float course,
                                     final int[] result) {
        int a = result[0] - center[0];
        int b = result[1] - center[1];
        final double c = Math.sqrt(a * a + b * b);

        if (Double.isNaN(c)) {
            return false;
        }

        int alpha = (int) Math.toDegrees(ExtraMath.asin(Math.abs((double) a) / c));
        if (b > 0) {
            if (a > 0) {
                alpha = 180 - alpha;
            } else {
                alpha = 180 + alpha;
            }
        } else {
            if (a < 0) {
                alpha = 360 - alpha;
            }
        }
        int alpha2 = (alpha - (int) course) % 360;
        int xs = 1;
        int ys = 1;
        if (alpha2 > 270) {
            alpha2 = 360 - alpha2;
            xs = ys = -1;
        } else if (alpha2 > 180) {
            alpha2 -= 180;
            xs = -1;
        } else if (alpha2 > 90) {
            alpha2 = 180 - alpha2;
        } else {
            ys = -1;
        }
        final double alpha2Rad = Math.toRadians(alpha2);

/*
            double alpha = Xedarius.asin(Math.abs((double) a) / c);
            if (b > 0) {
                if (a > 0) {
                    alpha = Math.PI - alpha;
                } else {
                    alpha = Math.PI + alpha;
                }
            } else {
                if (a < 0) {
                    alpha = 2 * Math.PI - alpha;
                }
            }
            double alpha2 = (alpha - Math.toRadians(bearing)) % (2 * Math.PI);
            int xs = 1;
            int ys = 1;
            if (alpha2 > (1.5 * Math.PI)) {
                alpha2 = 2 * Math.PI - alpha2;
                xs = ys = -1;
            } else if (alpha2 > Math.PI) {
                alpha2 -= Math.PI;
                xs = -1;
            } else if (alpha2 > (0.5 * Math.PI)) {
                alpha2 = Math.PI - alpha2;
            } else {
                ys = -1;
            }
            double alpha2Rad = alpha2;
*/

        final double da = (c * Math.sin(alpha2Rad)) * xs;
        a = (int) da;
        if ((da - a) > 0.5) {
            a++;
        }
        final double db = (c * Math.cos(alpha2Rad)) * ys;
        b = (int) db;
        if ((db - b) > 0.5) {
            b++;
        }

        result[0] = center[0] + a;
        result[1] = center[1] + b;

        return true;
    }
}
