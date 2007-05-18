// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import api.location.QualifiedCoordinates;
import api.location.Location;

import cz.kruch.track.util.Arrays;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.location.Navigator;
import cz.kruch.track.location.Waypoint;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Font;

import public_domain.Xedarius;

final class LocatorView extends View {
    private static final char[] MSG_NO_WAYPOINT =
        { 'N', 'O', ' ', 'W', 'P', 'T' };
    private static final char[] STRING_M =
        { ' ', 'm' };

    public static final int[] RANGES = {
        1000, 500, 250, 100, 50, 25, 10, 5
    };
    public static final String[] RANGES_STR = {
        "1000 m", "500 m", "250 m", "100 m", "50 m", "25 m", "10 m", "5 m"
    };

    private static final int COLOR_RANGE        = 0x00808080;
    private static final int COLOR_NO_POSITION  = 0x00FF0000;
    private static final int COLOR_MIDST        = 0x00E0E000;
    private static final int COLOR_SHORTT       = 0x0000ff00;
    private static final int COLOR_LONGT        = 0x0000ffff;

    private static final int SHORT_HISTORY_DEPTH = 20;
    private static final int LONG_HISTORY_DEPTH =  40;

    private final Location[][] locations;
    private final int[] count;
    private final int[] positions;

    private final QualifiedCoordinates[] coordinatesAvg;
    private final float[] accuracyAvg;
    private final int[] satAvg;
    private final int[] rangeIdx;

    private short phase;
    private int term;

    private int dx, dy;
    private int lineLength;

    private float lastCourse = -1F;

    private final int navigationStrWidth;

    private final int[] center, vertex;
    private final int[][] triangle;

    private final StringBuffer sb;
    private final char[] sbChars;

    public LocatorView(Navigator navigator) {
        super(navigator);
        this.locations = new Location[2][];
        this.locations[0] = new Location[SHORT_HISTORY_DEPTH];
        this.locations[1] = new Location[LONG_HISTORY_DEPTH];
        this.coordinatesAvg = new QualifiedCoordinates[2];
        this.count = new int[2];
        this.positions = new int[2];
        this.accuracyAvg = new float[2];
        this.satAvg = new int[2];
        this.rangeIdx = new int[]{ 3, 2 };
        this.navigationStrWidth = Math.max(Desktop.font.charsWidth(MSG_NO_WAYPOINT, 0, MSG_NO_WAYPOINT.length),
                                           Desktop.font.stringWidth("99.999 M"));
        this.sb = new StringBuffer(32);
        this.sbChars = new char[32];
        this.center = new int[2];
        this.vertex = new int[2];
        this.triangle = new int[3][2];
        reset();
    }

    public void sizeChanged(int w, int h) {
        this.lineLength = Math.min(w - w / 10, h - h / 10);
        this.dx = (w - lineLength) >> 1;
        this.dy = (h - lineLength) >> 1;
        this.center[0] = w >> 1;
        this.center[1] = h >> 1;
    }

    public int reset() {
        Arrays.clear(locations[0]);
        Arrays.clear(locations[1]);
        count[0] = count[1] = 0;
        positions[0] = positions[1] = 0;
        accuracyAvg[0] = accuracyAvg[1] = -1F;
        lastCourse = -1F;

        return super.reset();
    }

    public int locationUpdated(Location l) {
        if (l.getFix() < 1) {
            return Desktop.MASK_NONE;
        }
        
        // update short-term array
        append(0, l);

        // recalc
        recalc();

        return isVisible ? Desktop.MASK_ALL : Desktop.MASK_NONE;
    }

    public int handleAction(int action, boolean repeated) {
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

    private void append(final int term, Location l) {
        final Location[] array = locations[term];
        int position = positions[term];

        if (++position == array.length) {
            position = 0;
        }
        if (array[position] != null) {
            QualifiedCoordinates.releaseInstance(array[position].getQualifiedCoordinates());
            Location.releaseInstance(array[position]);
            array[position] = null; // gc hint
        }
        array[position] = l.clone();

        positions[term] = position;
    }

    private void recalc() {
        // compute short-term avg
        int c = compute(0);

        // is we have some data, compute long-term avg
        if (c > 0) {
            // if we are in right phase... what the fuck is this?!?
            if ((phase++ % 5) == 0) {
                // create long-term avg location (some values are irrelevant)
                Location l = Location.newInstance(coordinatesAvg[0].clone(),
                                                  -1, -1, -1,
                                                  accuracyAvg[0]);

                // update long-term array
                append(1, l);

                // compute long-term avg
                c = compute(1);
            }
        }
    }

    private int compute(final int term) {
        double latAvg = 0D, lonAvg = 0D;
        float accuracySum = 0F, wSum = 0F;
        int c = 0, satSum = 0;

        // local ref for faster access
        final Location[] array = locations[term];
        final QualifiedCoordinates[] qcAvg = coordinatesAvg;

        // calculate avg lat/lon and accuracy
        for (int i = array.length; --i >= 0; ) {
            Location l = array[i];
            if (l != null) {
                final float accuracy = l.getAccuracy();
                final float w = 5F / accuracy;
                accuracySum += accuracy;
                satSum += l.getSat();
                QualifiedCoordinates qc = l.getQualifiedCoordinates();
                latAvg += qc.getLat() * w;
                lonAvg += qc.getLon() * w;
                wSum += w;
                c++;
            }
        }

        // calculate avg coordinates
        if (c > 0) {
            latAvg /= wSum;
            lonAvg /= wSum;
            QualifiedCoordinates.releaseInstance(qcAvg[term]);
            qcAvg[term] = null; // gc hint
            qcAvg[term] = QualifiedCoordinates.newInstance(latAvg, lonAvg, -1F);
            qcAvg[term].setHp(true);
            accuracyAvg[term] = accuracySum / c;
            satAvg[term] = satSum / c;
            count[term] = c;
        }

        return c;
    }

    private boolean transform(final int[] center, final float course, final int[] result) {
        int a = result[0] - center[0];
        int b = result[1] - center[1];
        final double c = Math.sqrt(a * a + b * b);

        if (Double.isNaN(c)) {
            return false;
        }

        int alpha = (int) Math.toDegrees(Xedarius.asin(Math.abs((double) a) / c));
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
        double alpha2Rad = Math.toRadians(alpha2);

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

        double da = (c * Math.sin(alpha2Rad)) * xs;
        a = (int) da;
        if ((da - a) > 0.5) {
            a++;
        }
        double db = (c * Math.cos(alpha2Rad)) * ys;
        b = (int) db;
        if ((db - b) > 0.5) {
            b++;
        }

        result[0] = center[0] + a;
        result[1] = center[1] + b;

        return true;
    }

    public int changeDayNight(int dayNight) {
        return isVisible ? Desktop.MASK_ALL : Desktop.MASK_NONE; 
    }

    public void render(Graphics graphics, Font font, int mask) {
        // local references for faster access
        final int w = Desktop.width;
        final int h = Desktop.height;
        final int wHalf = w >> 1;
        final int hHalf = h >> 1;
        final int term = this.term;
        final int rangeIdx = this.rangeIdx[term];
        final char[] sbChars = this.sbChars;
        final StringBuffer sb = this.sb;
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
            wptColor = 0x00e0e00b/*0x00e0e00*/;
        }

        // clear
        graphics.setFont(font);
        graphics.setColor(bgColor);
        graphics.fillRect(0,0, w, h);

        // draw crosshair
        graphics.setColor(0x00404040);
        graphics.drawLine(wHalf, dy, wHalf, h - dy);
        graphics.drawLine(dx, hHalf, w - dx, hHalf);

        // draw compas
        float course;

        /* block */ {
            Location current = navigator.getLocation();
            if (current == null) {
                course = -1F;
            } else {
                course = current.getCourse();
            }
            boolean uptodate;
            if (course == -1F) {
                course = lastCourse;
                uptodate = false;
            } else {
                uptodate = true;
            }
            if (course > -1F) {
                drawCompas(wHalf, hHalf, fh, graphics, course, uptodate);
                lastCourse = course;
            }
        } /* ~ */

        // compas boundary
        graphics.setColor(0x00005050);
        graphics.drawArc(dx + fh, dy + fh,
                         lineLength - (fh << 1),
                         lineLength - (fh << 1),
                         0, 360);

        // wpt index
        Waypoint wpt = navigator.getNavigateTo();

        // draw points
        if (count[term] > 0) {

            // local refs
            final Location[] locations = this.locations[term];
            final QualifiedCoordinates coordsAvg = this.coordinatesAvg[term];
            final double latAvg = coordsAvg.getLat();
            final double lonAvg = coordsAvg.getLon();
            final int[] xy = this.vertex;
            final int[] center = this.center;

            // get scales
            final double v = ((double) RANGES[rangeIdx]) / 111319.490;
            final double yScale = ((double) (lineLength >> 1)) / (v);
            final double xScale = ((double) (lineLength >> 1)) / (v / Math.cos(Math.toRadians(latAvg)));

            // points color
            final int inc = (192/* = 256 - 64*/) / locations.length;
            int cstep = inc;
            cstep <<= 8;
            if (term > 0) cstep += inc;
            int color = (term == 0 ? COLOR_SHORTT : COLOR_LONGT) - count[term] * cstep;

            // draw points
            int offset = this.positions[term] + 1;
            if (offset == locations.length) {
                offset = 0;
            }
            for (int N = locations.length, i = N; --i >= 0; ) {
                Location l = locations[offset++];
                if (offset == N) {
                    offset = 0;
                }
                if (l != null) {
                    // create vertex
                    QualifiedCoordinates qc = l.getQualifiedCoordinates();
                    xy[0] = wHalf + (int) ((qc.getLon() - lonAvg) * xScale);
                    xy[1] = hHalf - (int) ((qc.getLat() - latAvg) * yScale);

                    // tranform
                    if (course > 1F) {
                        transform(center, course, xy);
                    }

                    //
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
            coordsAvg.toStringBuffer(sb);
            int l = sb.length();
            sb.getChars(0, l, sbChars, 0);
            graphics.drawChars(sbChars, 0, l, OSD.BORDER, 0, Graphics.LEFT | Graphics.TOP);

            // draw hdop
            sb.delete(0, sb.length());
            sb.append(NavigationScreens.PLUSMINUS);
            float accuracy = accuracyAvg[term];
            if (accuracy >= 10F) {
                sb.append((int) accuracy);
            } else {
                NavigationScreens.append(sb, accuracy, 1);
            }
            sb.append(STRING_M);
            l = sb.length();
            sb.getChars(0, l, sbChars, 0);
            graphics.drawChars(sbChars, 0, l, OSD.BORDER, fh, 0);

            // draw sat
            if (satAvg[term] > 0) {
                // same position as in OSD
                graphics.drawString(NavigationScreens.nStr[satAvg[term]],
                                    osd.width - OSD.BORDER - (satAvg[term] < 10 ? osd.str1Width : osd.str2Width),
                                    osd.gy + osd.bh,
                                    Graphics.LEFT | Graphics.TOP);
            }

            // draw central point
            graphics.setColor(COLOR_MIDST);
            graphics.drawArc(wHalf - 4, hHalf - 4, 9, 9, 0, 360);

            // wtp color
            graphics.setColor(wptColor);

            // draw waypoint
            if (wpt != null) {
                QualifiedCoordinates qc = wpt.getQualifiedCoordinates();
                xy[0] = wHalf + (int) ((qc.getLon() - lonAvg) * xScale);
                xy[1] = hHalf - (int) ((qc.getLat() - latAvg) * yScale);

                // transform vertex
                boolean onScreen = true;
                if (course > 1F) {
                    onScreen = transform(center, course, xy);
                }

                // calculate distance
                float distance = coordsAvg.distance(qc);

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
                NavigationScreens.drawArrow(graphics, arrowa, wHalf, hHalf,
                                            Graphics.LEFT | Graphics.TOP);

                // construct distance string
                sb.delete(0, sb.length());
                if (Config.nauticalView) {
                    NavigationScreens.append(sb, distance / 1852F, 0).append(Desktop.DIST_STR_NMI);
                } else {
                    if (distance >= 10000F) { // dist > 10 km
                        NavigationScreens.append(sb, distance / 1000F, 1).append(Desktop.DIST_STR_KM);
                    } else if (distance < 5F) {
                        NavigationScreens.append(sb, distance, 1).append(Desktop.DIST_STR_M);
                    } else {
                        sb.append((int) distance).append(Desktop.DIST_STR_M);
                    }
                }
                l = sb.length();
                sb.getChars(0, l, sbChars, 0);

                // draw distance
                graphics.drawChars(sbChars, 0, l,
                                   w - navigationStrWidth, h - (fh << 1),
                                   Graphics.LEFT | Graphics.TOP);

                // true or relative wpt-azi?
                if (!Config.hpsWptTrueAzimuth) {
                    bearing = arrowa;
                }
                
                // draw azimuth
                sb.delete(0, sb.length());
                sb.append((int) bearing).append(' ').append(NavigationScreens.SIGN);
                l = sb.length();
                sb.getChars(0, l, sbChars, 0);
                graphics.drawChars(sbChars, 0, l,
                                   w - navigationStrWidth, h - fh,
                                   Graphics.LEFT | Graphics.TOP);
            } else {
                graphics.drawChars(MSG_NO_WAYPOINT, 0, MSG_NO_WAYPOINT.length,
                                   w - navigationStrWidth, h - fh,
                                   Graphics.LEFT | Graphics.TOP);
            }
        } else {
            graphics.setColor(COLOR_NO_POSITION);
            graphics.drawChars(MSG_NO_POSITION, 0, MSG_NO_POSITION.length,
                               OSD.BORDER, 0, Graphics.LEFT | Graphics.TOP);
            if (wpt == null) {
                graphics.setColor(wptColor);
                graphics.drawChars(MSG_NO_WAYPOINT, 0, MSG_NO_WAYPOINT.length,
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
        graphics.drawLine(dx, h - 3, dx, h - 1);
        graphics.drawLine(dx, h - 2, wHalf, h - 2);
        graphics.drawLine(wHalf, h - 3, wHalf, h - 1);
        graphics.drawString(RANGES_STR[rangeIdx],
                            dx + 3, h - fh - 5,
                            Graphics.LEFT | Graphics.TOP);

        // flush
        flushGraphics();
    }

    private void drawCompas(final int width2, final int height2, final int fh,
                            Graphics g, final float course, final boolean uptodate) {
        final int[][] triangle = this.triangle;

        triangle[0][0] = width2 - 7;
        triangle[0][1] = height2;
        triangle[1][0] = width2;
        triangle[1][1] = dy + lineLength - fh;
        triangle[2][0] = width2 + 7;
        triangle[2][1] = height2;

        g.setColor(0x00707070);
        drawTriangle(g, course, triangle);

        triangle[0][0] = dx + fh;
        triangle[0][1] = height2;
        triangle[1][0] = triangle[0][0] + 7;
        triangle[1][1] = height2 - 4;
        triangle[2][0] = triangle[1][0];
        triangle[2][1] = height2 + 4;

        drawTriangle(g, course, triangle);

        triangle[0][0] = dx + lineLength - fh;
        triangle[0][1] = height2;
        triangle[1][0] = triangle[0][0] - 7;
        triangle[1][1] = height2 - 4;
        triangle[2][0] = triangle[1][0];
        triangle[2][1] = height2 + 4;

        drawTriangle(g, course, triangle);

        triangle[0][0] = width2 - 7;
        triangle[0][1] = height2;
        triangle[1][0] = width2;
        triangle[1][1] = dy + fh;
        triangle[2][0] = width2 + 7;
        triangle[2][1] = triangle[0][1];

        if (uptodate) {
            g.setColor(0x00A00000);
        }
        drawTriangle(g, course, triangle);
    }

    private void drawTriangle(Graphics g, final float bearing, final int[][] triangle) {
        if (bearing > 1F) {
            transform(center, bearing, triangle[0]);
            transform(center, bearing, triangle[1]);
            transform(center, bearing, triangle[2]);
        }
        g.fillTriangle(triangle[0][0], triangle[0][1],
                       triangle[1][0], triangle[1][1],
                       triangle[2][0], triangle[2][1]);
    }
}
