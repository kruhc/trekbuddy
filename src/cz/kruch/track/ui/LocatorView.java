// @LICENSE@

package cz.kruch.track.ui;

import api.location.QualifiedCoordinates;
import api.location.Location;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.location.TripStatistics;
import cz.kruch.track.util.ExtraMath;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Font;

/**
 * HPS aka compass screen.
 *
 * @author kruhc@seznam.cz
 */
final class LocatorView extends View {
    private static final int[] RANGES = {
        1000, 500, 250, 100, 50, 25, 10, 5
    };
    private static final String[] RANGES_STR = {
        "1000 m", "500 m", "250 m", "100 m", "50 m", "25 m", "10 m", "5 m"
    };

    private static final int COLOR_CROSSHAIR    = 0x00808080;
    private static final int COLOR_RANGE        = 0x00808080;
    private static final int COLOR_NO_POSITION  = 0x00FF0000;
    private static final int COLOR_MIDST        = 0x00E0E000;
    private static final int COLOR_AVGT         = 0x0000ff00;
    private static final int COLOR_NONAVGT      = 0x0000ffff;

    private float[] lastCourse;
    private int orientation;

    private final int[] rangeIdx;
    private int term, mode;

    private int dx, dy;
    private int lineLength;

    private int navigationStrWidth, courseStrWidth;

    private final int[] center, vertex;
    private final int[] triangle0, triangle1, triangle2;

    private final StringBuffer sb;
    private final char[] sbChars;

    LocatorView(/*Navigator*/Desktop navigator) {
        super(navigator);
        this.lastCourse = new float[2];
        this.orientation = -1;
        this.rangeIdx = new int[]{ 2, 2 };
        this.center = new int[2];
        this.vertex = new int[2];
        this.triangle0 = new int[2];
        this.triangle1 = new int[2];
        this.triangle2 = new int[2];
        this.sb = new StringBuffer(32);
        this.sbChars = new char[32];
        reset();
        resetFont();
    }

    void sizeChanged(int w, int h) {
        this.lineLength = Math.min(w - w / 10, h - h / 10);
        this.dx = (w - lineLength) >> 1;
        this.dy = (h - lineLength) >> 1;
        this.center[0] = w >> 1;
        this.center[1] = h >> 1;
    }

    void trackingStarted() {
        reset();
    }

    void trackingStopped() {
        reset();
    }

    int locationUpdated(Location l) {
        return Desktop.MASK_SCREEN;
    }

    int orientationChanged(int heading) {
        // remember
        orientation = heading;

        return Desktop.MASK_SCREEN;
    }

    void setVisible(final boolean b) {
        super.setVisible(b);
        orientation = -1;
        if (b) {
            if (Config.hpsMagneticNeedle) {
                cz.kruch.track.ui.nokia.DeviceControl.senseOn(navigator);
            }
        } else {
            cz.kruch.track.ui.nokia.DeviceControl.senseOff(navigator);
        }
    }

    int handleAction(final int action, final boolean repeated) {
        // ignore repeated
        if (repeated) {
            return Desktop.MASK_NONE;
        }

        // result repaint mask
        int mask = Desktop.MASK_ALL;

        // handle action
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
            case Canvas.FIRE: {
                mode++;
            } break;
            default:
                mask = Desktop.MASK_NONE;
        }

        return mask;
    }

    int handleKey(final int keycode, final boolean repeated) {
        // result repaint mask
        int mask = Desktop.MASK_NONE;

        // handle key
        switch (keycode) {
//#ifdef __ANDROID__
            case -25:
//#elifdef __RIM__
            case -151:
//#else
            case -37: // SE
//#endif
                if (!Config.easyZoomVolumeKeys)
                    break;
            case Canvas.KEY_NUM7: {
                if (rangeIdx[term] > 0) {
                    rangeIdx[term]--;
                    mask = Desktop.MASK_ALL;
                }
            } break;

//#ifdef __ANDROID__
            case -24:
//#elifdef __RIM__
            case -150:
//#else
            case -36: // SE
//#endif
                if (!Config.easyZoomVolumeKeys)
                    break;
            case Canvas.KEY_NUM9: {
                if (rangeIdx[term] < (RANGES.length - 1)) {
                    rangeIdx[term]++;
                    mask = Desktop.MASK_ALL;
                }
            } break;
        }

        return mask;
    }

    int changeDayNight(final int dayNight) {
        return Desktop.MASK_SCREEN; 
    }

    void render(final Graphics graphics, final Font font, final int mask) {
        // local references for faster access
        final StringBuffer sb = this.sb;
        final OSD osd = Desktop.osd;
        final int w = Desktop.width;
        final int h = Desktop.height;
        final int wHalf = w >> 1;
        final int hHalf = h >> 1;
        final int dx = this.dx;
        final int dy = this.dy;
        final int term = this.term;
        final int rangeIdx = this.rangeIdx[term];
        final int fh = osd.bh;
        final int orientation = this.orientation;
        final char[] sbChars = this.sbChars;
        final float a = DeviceScreen.density;
        final int ai = ExtraMath.round(a);

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
        graphics.setColor(COLOR_CROSSHAIR);
//#if __ANDROID__ || __CN1__
        graphics.setAliasing(false);
        if (a > 1.0f)
            graphics.setStrokeWidth(1 + a / 2);
//#endif
        graphics.drawLine(wHalf, dy, wHalf, h - dy);
        graphics.drawLine(dx, hHalf, w - dx, hHalf);
//#if __ANDROID__ || __CN1__
        graphics.setAliasing(true);
        if (a > 1.0f)
            graphics.setStrokeWidth(1);
//#endif

        // draw compas
        float course;
        final int hires = Desktop.getHiresLevel();
        final int ifh = 4 + hires * 4, efh = 7 + hires * 4;

        // draw internal compass value
        if (orientation != -1) {
            drawCompas(wHalf, hHalf, fh, ifh, graphics, orientation, true,
                       0x00FFD700, 0x00FFAA00);
        }

        /* block */ {
            final Location current = TripStatistics.locations[term][TripStatistics.positions[term]];
            if (current != null) {
                course = current.getCourse();
            } else {
                course = Float.NaN;
            }
            final float[] lastCourse = this.lastCourse;
            final boolean fresh = !Float.isNaN(course);
            if (fresh) {
                lastCourse[term] = course;
            } else {
                course = lastCourse[term];
            }
            if (!Float.isNaN(course)) {
                final float useCourse = mode % 2 == 0 ? course : 0;
                final int colorHi, colorLo;
                if (term == 0) {
                    colorHi = 0x00D00000;
                    colorLo = 0x00A00000;
                } else {
                    colorHi = 0x001E90FF;
                    colorLo = 0x001560BD;
                }
                drawCompas(wHalf, hHalf, fh, efh, graphics, useCourse, fresh, colorHi, colorLo);
            }
        } /* ~ */

        // compas boundary
        graphics.setColor(0x00008080);
        graphics.drawArc(dx + fh, dy + fh,
                         lineLength - (fh << 1),
                         lineLength - (fh << 1),
                         0, 360);

        // draw north lock
        if (mode % 2 != 0) {
            graphics.drawImage(NavigationScreens.nlock,
                               wHalf - NavigationScreens.nlockSize2,
                               hHalf - (lineLength >> 1) - NavigationScreens.nlockSize2,
                               Graphics.LEFT | Graphics.TOP);
        }

        // wpt index
        final Waypoint wpt = navigator.getNavigateTo();

        // draw points
        if (TripStatistics.counts[term] > 0) {

            // print course
            graphics.setColor(fgColor);
            sb.setLength(0);
            NavigationScreens.append(sb, (int) course).append(NavigationScreens.SIGN);
            final int cl = sb.length();
            sb.getChars(0, cl, sbChars, 0);
            graphics.drawChars(sbChars, 0, cl, w - courseStrWidth, fh, Graphics.LEFT | Graphics.TOP);

            // local refs
            final Location[] locations = TripStatistics.locations[term];
            final QualifiedCoordinates coordsAvg = locations[TripStatistics.positions[term]].getQualifiedCoordinates();
            final double latAvg = coordsAvg.getLat();
            final double lonAvg = coordsAvg.getLon();
            final int[] xy = this.vertex;
            final int[] center = this.center;

            // get scales
            final double v = ((double) RANGES[rangeIdx]) / 111319.490D;
            final double yScale = ((double) (lineLength >> 1)) / (v);
            final double xScale = ((double) (lineLength >> 1)) / (v / Math.cos(Math.toRadians(latAvg)));

            // points color
            final int inc = (192/* = 256 - 64*/) / TripStatistics.HISTORY_DEPTH;
            int cstep = inc;
            cstep <<= 8;
            if (term > 0) cstep += inc;
            int color = (term == 0 ? COLOR_AVGT : COLOR_NONAVGT) - TripStatistics.counts[term] * cstep;

            // draw points
            int offset = TripStatistics.positions[term] + 1; // to start from the oldest
            if (offset == TripStatistics.HISTORY_DEPTH) {
                offset = 0;
            }
            for (int N = TripStatistics.HISTORY_DEPTH, i = N; --i >= 0; ) {
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
                    if (course > 1F && mode % 2 == 0) {
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
                }
            }

            /*
             * draw calculated (avg) position
             */

            // set color
            graphics.setColor(fgColor);

            // draw lat/lon
            sb.setLength(0);
            NavigationScreens.printTo(sb, coordsAvg, QualifiedCoordinates.LAT, true);
            int l = sb.length();
            sb.getChars(0, l, sbChars, 0);
            graphics.drawChars(sbChars, 0, l, OSD.BORDER, 0, Graphics.LEFT | Graphics.TOP);
            sb.setLength(0);
            NavigationScreens.printTo(sb, coordsAvg, QualifiedCoordinates.LON, true);
            l = sb.length();
            sb.getChars(0, l, sbChars, 0);
            graphics.drawChars(sbChars, 0, l, OSD.BORDER, fh, Graphics.LEFT | Graphics.TOP);

            // draw hdop and orientation
            float hAccuracy = coordsAvg.getHorizontalAccuracy();
            if (!Float.isNaN(hAccuracy)) {
                char[] uc = NavigationScreens.DIST_STR_M;
                switch (Config.units) {
                    case Config.UNITS_IMPERIAL:
                    case Config.UNITS_NAUTICAL: {
                        uc = NavigationScreens.DIST_STR_FT;
                        hAccuracy /= 0.3048F;
                    } break;
                }
                sb.setLength(0);
                sb.append(NavigationScreens.PLUSMINUS);
                if (hAccuracy >= 10F) {
                    NavigationScreens.append(sb, (int) hAccuracy);
                } else {
                    NavigationScreens.append(sb, hAccuracy, 1);
                }
                sb.append(' ').append(uc);
                l = sb.length();
                sb.getChars(0, l, sbChars, 0);
                graphics.drawChars(sbChars, 0, l, OSD.BORDER, fh << 1, Graphics.LEFT | Graphics.TOP);
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
                if (course > 1F && mode % 2 == 0) {
                    onScreen = transform(center, course, xy);
                }

                // calculate distance
                final float distance = coordsAvg.distance(qc);

                // calculate bearing
                float bearing = coordsAvg.azimuthTo(qc);
                float arrowa;
                if (course > 0F && mode % 2 == 0) { // relative to top (moving direction)
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
                sb.setLength(0);
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
                sb.setLength(0);
                NavigationScreens.append(sb, (int) bearing).append(NavigationScreens.SIGN);
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
        final int cy = h - Desktop.osd.bh - (int)Math.ceil(4 * (a * 2)); // same Y coordinate as scale on map screen
        final int rsy = h - (int)Math.floor(4 * (a * 2)) + 1 + ai;
        final int rsy0 = rsy - 1 - ai;
        final int rsy1 = rsy + 1 + ai;
        graphics.setColor(COLOR_RANGE);
//#if __ANDROID__ || __CN1__
        graphics.setAliasing(false);
//#endif
        graphics.drawLine(dx, rsy0, dx, rsy1);
//#if __ANDROID__ || __CN1__
        if (a > 1.0f)
            graphics.setStrokeWidth(1 + a);
//#endif
        graphics.drawLine(dx + 1, rsy, wHalf - 1, rsy);
//#if __ANDROID__ || __CN1__
        if (a > 1.0f)
            graphics.setStrokeWidth(1);
//#endif
        graphics.drawLine(wHalf, rsy0, wHalf, rsy1);
//#if __ANDROID__ || __CN1__
        graphics.setAliasing(true);
//#endif
        graphics.drawString(RANGES_STR[rangeIdx],
                            dx + 3, cy/*rsy - fh*/,
                            Graphics.LEFT | Graphics.TOP);

        // draw keylock status
        if (Desktop.screen.isKeylock()) {
            NavigationScreens.drawKeylockStatus(graphics);
        }

        // draw zoom spots
        NavigationScreens.drawZoomSpots(graphics);
        
        // draw visual guides only when autohide to prevent screen obstruction
        if (Desktop.screen.isKeylock() && Config.guideSpotsMode == 2) {
            NavigationScreens.drawGuideSpots(graphics, true);
        }
    }

    int configChanged() {
        resetFont();
        if (isVisible) {
            orientation = -1;
            if (Config.hpsMagneticNeedle && !cz.kruch.track.ui.nokia.DeviceControl.isSense()) {
                cz.kruch.track.ui.nokia.DeviceControl.senseOn(navigator);
            } else if (!Config.hpsMagneticNeedle && cz.kruch.track.ui.nokia.DeviceControl.isSense()) {
                cz.kruch.track.ui.nokia.DeviceControl.senseOff(navigator);
            }
        }
        return super.configChanged();
    }

    private void resetFont() {
        final Font font = Desktop.font;
        this.navigationStrWidth = Math.max(font.stringWidth(MSG_NO_WAYPOINT),
                                           font.stringWidth("9.999 M"));
        this.courseStrWidth = font.stringWidth("359\u00b0");
    }

    private void reset() {
        for (int i = 2; --i >= 0; ) {
            lastCourse[i] = Float.NaN;
        }
    }

    private void drawCompas(final int width2, final int height2, final int fh, final int thick,
                            final Graphics g, final float course, final boolean uptodate,
                            final int colorHi, final int colorLo) {
        final int[] triangle0 = this.triangle0;
        final int[] triangle1 = this.triangle1;
        final int[] triangle2 = this.triangle2;

        triangle0[0] = width2;
        triangle0[1] = height2;
        triangle1[0] = width2;
        triangle1[1] = dy + lineLength - fh;
        triangle2[0] = width2 + thick;
        triangle2[1] = height2;

        g.setColor(0x00707070);
        drawTriangle(g, course, triangle0, triangle1, triangle2);

        triangle0[0] = width2 - thick;
        triangle0[1] = height2;
        triangle1[0] = width2;
        triangle1[1] = dy + lineLength - fh;
        triangle2[0] = width2;
        triangle2[1] = height2;

        g.setColor(0x00909090);
        drawTriangle(g, course, triangle0, triangle1, triangle2);

/*
        triangle0[0] = dx + fh;
        triangle0[1] = height2;
        triangle1[0] = triangle0[0] + 9;
        triangle1[1] = height2 - 3;
        triangle2[0] = triangle1[0];
        triangle2[1] = height2 + 3;

        drawTriangle(g, course, triangle);

        triangle0[0] = dx + lineLength - fh;
        triangle0[1] = height2;
        triangle1[0] = triangle0[0] - 9;
        triangle1[1] = height2 - 3;
        triangle2[0] = triangle1[0];
        triangle2[1] = height2 + 3;

        drawTriangle(g, course, triangle);
*/

        triangle0[0] = width2 - thick;
        triangle0[1] = height2;
        triangle1[0] = width2;
        triangle1[1] = dy + fh;
        triangle2[0] = width2;
        triangle2[1] = triangle0[1];

        if (uptodate) {
            g.setColor(colorHi);
        } else {
            g.setColor(0x00707070);
        }
        drawTriangle(g, course, triangle0, triangle1, triangle2);

        triangle0[0] = width2;
        triangle0[1] = height2;
        triangle1[0] = width2;
        triangle1[1] = dy + fh;
        triangle2[0] = width2 + thick;
        triangle2[1] = triangle0[1];

        if (uptodate) {
            g.setColor(colorLo);
        } else {
            g.setColor(0x00505050);
        }
        drawTriangle(g, course, triangle0, triangle1, triangle2);
    }

    private void drawTriangle(final Graphics g, final float bearing,
                              final int[] triangle0, final int[] triangle1, final int[] triangle2) {
        if (bearing > 1F) {
            final int[] center = this.center;
            transform(center, bearing, triangle0);
            transform(center, bearing, triangle1);
            transform(center, bearing, triangle2);
        }
        g.fillTriangle(triangle0[0], triangle0[1],
                       triangle1[0], triangle1[1],
                       triangle2[0], triangle2[1]);
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
