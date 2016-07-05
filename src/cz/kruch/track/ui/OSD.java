// @LICENSE@

package cz.kruch.track.ui;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Font;

import api.location.LocationProvider;
import api.location.QualifiedCoordinates;

import cz.kruch.track.configuration.Config;

/**
 * OSD.
 *
 * @author kruhc@seznam.cz
 */
final class OSD extends Bar {
    private static final char[] MM = { '<', '>' };
    private static final String NSAT = "3*4*5*6*7*8*9*10*11*12*";
    private static final int NSAT_MAX = 12;

    int providerStatus;

    private boolean recording;
    private boolean ok;
    private int sat;

    int semaforX, semaforY;

    private final StringBuffer sb;
    private int rw, mmw, nsatw, nsatw2;

    private final char[] cInfo, cExtInfo;
    private int cInfoLength, cExtInfoLength;

    OSD(int gx, int gy, int width, int height) {
        super(gx, gy, width, height);
        this.providerStatus = LocationProvider.OUT_OF_SERVICE;
        this.sb = new StringBuffer(64);
        this.cInfo = new char[64];
        this.cExtInfo = new char[64];
        resize(width, height);
        resetFont();
    }

    public void resetFont() {
        super.resetFont();
        final Font font = Desktop.font;
        this.rw = font.charWidth('R');
        this.mmw = font.stringWidth("<>");
        this.nsatw = font.stringWidth("5*");
        this.nsatw2 = font.stringWidth("15*");
    }

    public void resize(int width, int height) {
        super.resize(width, height);
        this.semaforX = this.width - NavigationScreens.bulletSize - BORDER;
        this.semaforY = Math.abs((this.bh - NavigationScreens.bulletSize)) >> 1;
    }

    public void render(Graphics graphics) {
        if (!visible) {
            return;
        }

        final boolean isBasicInfo = Config.osdBasic;
        final boolean isExtInfo = Config.osdExtended;
        final int gx = this.gx;
        final int gy = this.gy;
        final boolean ok = this.ok;

        // draw info + extended info bg
        if (!Config.osdNoBackground) {
            if (isBasicInfo) {
//--//#ifndef __CN1__
//#if !__ANDROID__ && !__CN1__
                graphics.drawImage(Desktop.bar, gx, gy, Graphics.TOP | Graphics.LEFT);
//#else
                final int cc = graphics.getColor();
                graphics.setARGBColor(Desktop.bar_c);
                graphics.fillRect(gx, gy, Desktop.bar_w, Desktop.bar_h);
                graphics.setColor(cc);
                graphics.setAlpha(0xff);
//#endif
            }
            if (isExtInfo && cExtInfoLength > 0) {
//--//#ifndef __CN1__
//#if !__ANDROID__ && !__CN1__
                graphics.drawImage(Desktop.bar, gx, gy + (isBasicInfo ? bh : 0), Graphics.TOP | Graphics.LEFT);
//#else
                final int cc = graphics.getColor();
                graphics.setARGBColor(Desktop.bar_c);
                graphics.fillRect(gx, gy + (isBasicInfo ? bh : 0), Desktop.bar_w, Desktop.bar_h);
                graphics.setColor(cc);
                graphics.setAlpha(0xff);
//#endif
            }
        }

        // not ok? change color...
        if (!ok) {
            graphics.setColor(0x00FF0000);
        }

        // draw info + extended info text
        if (isBasicInfo && cInfoLength > 0) {
            graphics.drawChars(cInfo, 0, cInfoLength, gx + BORDER, gy, Graphics.TOP | Graphics.LEFT);
        }
        if (isExtInfo && cExtInfoLength > 0) {
            graphics.drawChars(cExtInfo, 0, cExtInfoLength, gx + BORDER, gy + (isBasicInfo ? bh : 0), 0);
            if (Desktop.browsing) {
                graphics.drawChars(MM, 0, 2,
                                   width - BORDER - mmw,
                                   gy + bh, Graphics.TOP | Graphics.LEFT);
            } else {
//#ifndef __CN1__
                final int sat = this.sat;
                if (sat >= 3) {
                    if (sat < 10) {
                        graphics.drawSubstring(NSAT, (sat - 3) * 2, 2,
                                               width - BORDER - nsatw,
                                               gy + bh, Graphics.TOP | Graphics.LEFT);
                    } else if (sat <= NSAT_MAX) {
                        graphics.drawSubstring(NSAT, 14 + (sat - 10) * 3, 3,
                                               width - BORDER - nsatw2,
                                               gy + bh, Graphics.TOP | Graphics.LEFT);
                    }
                }
//#endif
            }
        }

        // recording
        if (recording) {
            if (ok) { // text was 'ok', so change the color now
                graphics.setColor(0x00FF0000);
            }
            graphics.drawChar('R', semaforX - rw, 0, Graphics.TOP | Graphics.LEFT);
        }

        // restore default color
        if (!ok || recording) {
            graphics.setColor(Config.osdBlackColor ? 0x00000000 : 0x00FFFFFF);
        }

        // draw provider status
        NavigationScreens.drawProviderStatus(graphics, providerStatus,
                                             semaforX, semaforY,
                                             Graphics.TOP | Graphics.LEFT);
    }

    public StringBuffer _getSb() {
        sb.setLength(0);
        return sb;
    }

    public void setProviderStatus(int providerStatus) {
        this.providerStatus = providerStatus;
    }

    public void setRecording(boolean b) {
        this.recording = b;
    }

    public void setSat(int sat) {
        this.sat = sat;
    }

    public void resetNavigationInfo() {
        this.cExtInfoLength = 0;
    }

    public void resetExtendedInfo() {
        this.cExtInfoLength = 0;
    }

    public void setNavigationInfo(final StringBuffer sb) {
        setExtendedInfo(sb);
    }

    public void setExtendedInfo(final StringBuffer sb) {
        if (sb != this.sb) {
            throw new IllegalStateException("Alien StringBuffer");
        }
        cExtInfoLength = sb.length();
        if (cExtInfoLength > cExtInfo.length) {
            throw new IllegalStateException("Extended info length = " + cExtInfoLength);
        }
        sb.getChars(0, cExtInfoLength, cExtInfo, 0);
    }

    public void setInfo(final QualifiedCoordinates qc, final boolean ok) {
        final StringBuffer sb = _getSb();
        NavigationScreens.printTo(qc, sb);
        cInfoLength = sb.length();
        if (cInfoLength > cInfo.length) {
            cInfoLength = cInfo.length;
        }
        sb.getChars(0, cInfoLength, cInfo, 0);
        this.ok = ok;
    }
}
