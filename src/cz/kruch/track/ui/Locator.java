// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.util.Logger;
import cz.kruch.track.location.Navigator;
import cz.kruch.track.location.Waypoint;

import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Canvas;

import api.location.Location;
import api.location.QualifiedCoordinates;

import java.io.IOException;

final class Locator extends GameCanvas implements Navigator {
//#ifdef __LOG__
    private static final Logger log = new Logger("Locator");
//#endif

    private static final int SHORT_HISTORY_DEPTH = 20;
    private static final int LONG_HISTORY_DEPTH = 2 * SHORT_HISTORY_DEPTH;
    private static final String MSG_NO_WAYPOINT = "NO WPT";

    private Navigator navigator;

    private Location location, location2;
    private Location[] locations;
    private Location[] locations2;
    private int count, count2;
    private QualifiedCoordinates coordinates, coordinates2;
    private float hdopAvg, hdopAvg2;
    private Image point, point2, pointAvg;

    private int phase = 0;

    private int width, height;
    private int ptSize;
    private int dx, dy;
    private int lineLength;

    private int rangeIdx = 3, rangeIdx2 = 2;
    private boolean termSwitch;

    private static double[][] ranges;
    private static String[] rangesStr;

    private int fontHeight;
    private int navigationStrWidth;

    static {
        ranges = new double[][]{
/*  0^ */   { 0.0045, 0.00225, 0.0009, 0.00045, 0.000225, 0.00009, 0.000045 },
/* 10^ */   { 0.0045694, 0.0022847, 0.0009139, 0.00045694, 0.00022847, 0.00009139, 0.000045694 },
/* 20^ */   { 0.0047889, 0.002394, 0.000958, 0.000478, 0.0002394, 0.0000958, 0.0000478 },
/* 30^ */   { 0.0051958, 0.0025981, 0.0010392, 0.00051958, 0.00025981, 0.00010392, 0.000051958 },
/* 40^ */   { 0.0058778, 0.0029444, 0.001175, 0.0005872, 0.0002936, 0.0001175, 0.0000589 },
/* 50^ */   { 0.007, 0.0035, 0.0014, 0.0007, 0.00035, 0.00014, 0.00007 },
/* 60^ */   { 0.009, 0.0045, 0.0018, 0.0009, 0.00045, 0.00018, 0.00009 },
/* 70^ */   { 0.0131583, 0.0065778, 0.0026333, 0.0013158, 0.0006577, 0.0002633, 0.00013158 },
/* 80^ */   { 0.02597, 0.012958, 0.005194, 0.002597, 0.0012958, 0.0005194, 0.0002597 },
/* 90^ */   { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 }
        };
        rangesStr = new String[]{
            "500 m", "250 m", "100 m", "50 m", "25 m", "10 m", "5 m"
        };
    }

    public Locator(Navigator navigator) throws IOException {
        super(false);
        setFullScreenMode(true);
        NavigationScreens.ensureInitialized();
        this.navigator = navigator;
        this.locations = new Location[SHORT_HISTORY_DEPTH];
        this.locations2 = new Location[LONG_HISTORY_DEPTH];
        this.point = Image.createImage("/resources/lpt_green.png");
        this.point2 = Image.createImage("/resources/lpt_blue.png");
        this.pointAvg = Image.createImage("/resources/lpt_yellow.png");
        this.ptSize = this.point.getWidth();
        this.width = this.getWidth();
        this.height = this.getHeight();
        this.lineLength = Math.min(width - width / 10, height - height / 10);
        this.dx = (width - lineLength) / 2;
        this.dy = (height - lineLength) / 2;
        this.fontHeight = Desktop.font.getHeight();
        this.navigationStrWidth = Math.max(Desktop.font.stringWidth(MSG_NO_WAYPOINT), Desktop.font.stringWidth("14999 m"));
        this.count = this.count2 = 0;
        this.hdopAvg = this.hdopAvg2 = -1f;
    }

    public void show() {
        recalc();
        render();
        Desktop.display.setCurrent(this);
    }

    public void update(Location l) {
        // update last position
        location = l;

        // update short-term array
        append(locations, l);

        // recalc and show
        recalc();
        if (isShown()) {
            render();
        }
    }

    private void recalc() {
        // compute short-term avg
        int c = compute(false);

        // is we have some data, compute long-term avg
        if (c > 0) {
            if ((phase++ % 5) == 0) {
                // create long-term avg location (some values are irrelevant)
                location2 = new Location(coordinates, -1, -1,
                                         location.getSat(), hdopAvg);

                // update long-term array
                append(locations2, location2);

                // compute long-term avg
                c = compute(true);
            }
        }
    }

    private void append(Location[] array, Location l) {
        int idx = -1;
        for (int N = array.length, i = 0; i < N; i++) {
            if (array[i] == null) {
                array[idx = i] = l;
                break;
            }
        }
        if (idx == -1) {
            System.arraycopy(array, 1, array, 0, array.length - 1);
            array[array.length - 1] = l;
        }
    }

    private int compute(boolean longTerm) {
        double latAvg = 0D, lonAvg = 0D;
        float hdopSum = 0F, wSum = 0F;
        int c = 0;

        Location[] array = longTerm ? locations2 : locations;

        // calculate short-term avg lat/lon and hdop
        for (int i = array.length; --i >= 0; ) {
            Location l = array[i];
            if (l != null) {
                hdopSum += l.getHdop();
                float w = 1f / l.getHdop();
                QualifiedCoordinates qc = l.getQualifiedCoordinates();
                latAvg += qc.getLat() * w;
                lonAvg += qc.getLon() * w;
                c++;
                wSum += w;
            }
        }

        // calculate avg coordinates
        if (c > 0) {
            // calculate avg coordinates
            latAvg /= wSum;
            lonAvg /= wSum;
            if (longTerm) {
                hdopAvg2 = hdopSum / c;
                coordinates2 = new QualifiedCoordinates(latAvg, lonAvg);
                coordinates2.setHp(true);
                count2 = c;
            } else {
                hdopAvg = hdopSum / c;
                coordinates = new QualifiedCoordinates(latAvg, lonAvg);
                coordinates.setHp(true);
                count = c;
            }
        }

        return c;
    }

    private void render() {
        // local copies for faster access
        int _width = width;
        int _width2 = _width / 2;
        int _height = height;
        int _height2 = _height / 2;
        int _ptSize = ptSize;
        int _ptSize2 = _ptSize / 2;

        // term vars
        QualifiedCoordinates _coordinates = termSwitch ? coordinates2 : coordinates;
        Location[] _locations = termSwitch ? locations2 : locations;
        Location _location = termSwitch ? location2 : location;
        Image _point = termSwitch ? point2 : point;
        float _hdopAvg = termSwitch ? hdopAvg2 : hdopAvg;
        int _count = termSwitch ? count2 : count;
        int _rangeIdx = termSwitch ? rangeIdx2 : rangeIdx;

        // draw crosshair
        Graphics g = getGraphics();
        g.setFont(Desktop.font);
        g.setColor(0x00000000);
        g.fillRect(0,0, getWidth(), getHeight());
        g.setColor(0x00808080);
        g.drawLine(_width2, dy, _width2, _height - dy);
        g.drawLine(dx, _height2, _width - dx, _height2);

        // draw points
        if (_count > 0) {
            // locals
            double latAvg = _coordinates.getLat();
            double lonAvg = _coordinates.getLon();

            // get scales for given latitude
            double flat = latAvg / 10D;
            int ilat = (int) flat;
            if ((ilat < 8) && (flat - ilat) > 0.75D) {
                ilat++;
            }
            double xScale = lineLength / (2 * ranges[ilat][_rangeIdx]);
            double yScale = lineLength / (2 * ranges[0][_rangeIdx]);

            // draw points
            for (int i = _locations.length; --i >= 0; ) {
                Location l = _locations[i];
                if (l != null) {
                    QualifiedCoordinates qc = l.getQualifiedCoordinates();
                    int x = _width2 + (int) ((qc.getLon() - lonAvg) * xScale);
                    int y = _height2 - (int) ((qc.getLat() - latAvg) * yScale);
//                    System.out.println("x = " + x + ", y = " + y + " [" + width / 2 + ", " + height / 2);
                    g.drawImage(_point, x - _ptSize2, y - _ptSize2, 0);
//                    System.out.println(l);
                }
            }

            // draw calculated (avg) position
            g.setColor(0x00ffffff);
            g.drawString(_coordinates.toString(), 0, 0, 0);
            g.drawString(Double.toString(_hdopAvg).substring(0, 3), 0, fontHeight, 0);
            g.drawString(Integer.toString(_location.getSat()), 0, 2 * fontHeight, 0);
            g.drawImage(pointAvg, _width2 - _ptSize2, _height2 - _ptSize2, 0);

            // draw waypoint
            g.setColor(0x00ffff00);
            int wptIdx = navigator.getNavigateTo();
            if (wptIdx > -1) {
                QualifiedCoordinates qc = navigator.getPath()[wptIdx].getQualifiedCoordinates();
                int x = _width2 + (int) ((qc.getLon() - lonAvg) * xScale);
                int y = _height2 - (int) ((qc.getLat() - latAvg) * yScale);
                float distance = _coordinates.distance(qc);
                String distanceStr = "???";
                if (distance < 5f) {
                    distanceStr = Float.toString(distance).substring(0, 3) + " m";
                } else if (distance < 15000f) {
                    distanceStr = Integer.toString((int) distance) + " m";
                } else {
                    distanceStr = Integer.toString((int) (distance / 1000)) + " km";
                }
                int azimuth = _coordinates.azimuthTo(qc, distance);
                NavigationScreens.drawWaypoint(g, x, y, 0);
                NavigationScreens.drawArrow(g, azimuth, _width2, _height2, 0);
                g.drawString(distanceStr,
                             width - navigationStrWidth,
                             height - 2 * fontHeight,
                             0);
                g.drawString(Integer.toString(azimuth) + " " + QualifiedCoordinates.SIGN,
                             width - navigationStrWidth,
                             height - fontHeight,
                             0);
            } else {
                g.drawString(MSG_NO_WAYPOINT,
                             width - navigationStrWidth,
                             height - fontHeight,
                             0);
            }
        } else {
            g.setColor(0x00ff0000);
            g.drawString("NO POSITION", 0, 0, 0);
        }

        // draw range
        g.setColor(0x00808080);
        g.drawLine(dx, _height - 3, dx, _height - 1);
        g.drawLine(dx, _height - 2, _width / 2, _height - 2);
        g.drawLine(_width2, _height - 3, _width2, _height - 1);
        g.drawString(rangesStr[_rangeIdx], dx + 3, _height - fontHeight - 5, 0);

        // update screen
        flushGraphics();
    }

    protected void keyPressed(int i) {
        int action = getGameAction(i);
        switch (action) {
            case Canvas.LEFT:
                if (termSwitch) {
                    if (rangeIdx2 > 0) {
                        rangeIdx2--;
                    }
                } else {
                    if (rangeIdx > 0) {
                        rangeIdx--;
                    }
                }
                render();
                break;
            case Canvas.RIGHT:
                if (termSwitch) {
                    if (rangeIdx2 < (ranges[0].length - 1)) {
                        rangeIdx2++;
                    }
                } else {
                    if (rangeIdx < (ranges[0].length - 1)) {
                        rangeIdx++;
                    }
                }
                render();
                break;
            case Canvas.UP:
            case Canvas.DOWN: {
                termSwitch = !termSwitch;
                render();
            } break;
            default: {
                switch (i) {
                    case KEY_NUM1: {
                        (new Waypoints(this)).show();
                    } break;
                    case KEY_NUM3: {
                        cz.kruch.track.ui.nokia.DeviceControl.setBacklight();
                    } break;
                    default:
                        Desktop.display.setCurrent((Displayable) navigator);
                        break;
                }
            }
        }
    }

    /*
     * Navigator contract.
     */

    public boolean isTracking() {
        return navigator.isTracking();
    }

    public Location getLocation() {
        return termSwitch ? location2 : location;
    }

    public QualifiedCoordinates getPointer() {
        return termSwitch ? coordinates2 : coordinates;
    }

    public Waypoint[] getPath() {
        return navigator.getPath();
    }

    public void setPath(Waypoint[] path) {
        navigator.setPath(path);
    }

    public void setNavigateTo(int pathIdx) {
        navigator.setNavigateTo(pathIdx);
        Desktop.display.setCurrent(this); // hack
        render(); // for waypoint to get rendered
    }

    public int getNavigateTo() {
        return navigator.getNavigateTo();
    }

    public void addWaypoint(Waypoint wpt) {
        navigator.addWaypoint(wpt);
    }

    public void recordWaypoint(Waypoint wpt) {
        navigator.recordWaypoint(wpt);
    }
}
