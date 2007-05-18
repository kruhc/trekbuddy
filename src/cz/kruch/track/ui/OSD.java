// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import api.location.LocationProvider;
import api.location.QualifiedCoordinates;

import javax.microedition.lcdui.Graphics;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.AssertionFailedException;

final class OSD extends Bar {
    protected int providerStatus;
    private String recording;
    private boolean ok;
    private int sat;

    protected int semaforX, semaforY;
    protected final int str1Width, str2Width;
    private final StringBuffer sb;
    private final int rw;

    private final char[] cInfo, cExtInfo;
    private int cInfoLength, cExtInfoLength;

    public OSD(int gx, int gy, int width, int height) {
        super(gx, gy, width, height);
        this.providerStatus = LocationProvider.OUT_OF_SERVICE;
        this.rw = Desktop.font.charWidth('R');
        this.clip = new int[]{ gx, gy, -1, -1 };
        this.str1Width = Desktop.font.stringWidth("4*");
        this.str2Width = Desktop.font.stringWidth("44*");
        this.sb = new StringBuffer(64);
        this.cInfo = new char[64];
        this.cExtInfo = new char[64];
        resize(width, height);
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

        // draw info + extended info bg
        if (!Config.osdNoBackground) {
            if (isBasicInfo) {
                graphics.drawImage(Desktop.bar, gx, gy, 0);
            }
            if (isExtInfo && isBasicInfo && cExtInfoLength > 0) {
                graphics.drawImage(Desktop.bar, gx, gy + bh, 0);
            }
        }

        // not ok? change color...
        if (!ok) {
            graphics.setColor(0x00FF0000);
        }

        // draw info + extended info text
        if (isBasicInfo && cInfoLength > 0) {
            graphics.drawChars(cInfo, 0, cInfoLength, gx + BORDER, gy, 0);
        }
        if (isExtInfo && cExtInfoLength > 0) {
            graphics.drawChars(cExtInfo, 0, cExtInfoLength, gx + BORDER, gy + (isBasicInfo ? bh : 0), 0);
            if (sat > 0) {
                String s = NavigationScreens.nStr[sat];
                graphics.drawString(s, width - BORDER - (sat < 10 ? str1Width : str2Width),
                                    gy + bh, 0);
            }
        }

        // gpx recording
        if (recording != null) {
            if (ok) { // text was 'ok', so change the color now
                graphics.setColor(0x00FF0000);
            }
            graphics.drawChar('R', semaforX - rw, 0, 0/*Graphics.TOP | Graphics.LEFT*/);
        }

        // restore default color
        if (!ok || recording != null) {
            graphics.setColor(Config.osdBlackColor ? 0x00000000 : 0x00FFFFFF);
        }

        // draw provider status
        NavigationScreens.drawProviderStatus(graphics, providerStatus,
                                             semaforX, semaforY, 0);
    }

    public StringBuffer _getSb() {
//        sb.setLength(0);
        return sb.delete(0, sb.length());
    }

    public void setProviderStatus(int providerStatus) {
        this.providerStatus = providerStatus;
    }

    public void setRecording(String recording) {
        this.recording = recording;
    }

    public void setSat(int sat) {
        this.sat = sat;
    }

    public void resetExtendedInfo() {
        this.cExtInfoLength = 0;
    }

    public void setExtendedInfo(StringBuffer sb) {
        if (sb != this.sb) {
            throw new AssertionFailedException("Alien StringBuffer");
        }
        cExtInfoLength = sb.length();
        if (cExtInfoLength > cExtInfo.length) {
            throw new AssertionFailedException("Extended info length = " + cExtInfoLength);
        }
        sb.getChars(0, sb.length(), cExtInfo, 0);
    }

    public void setInfo(QualifiedCoordinates qc, boolean ok) {
        sb.delete(0, sb.length());
        qc.setHp(Config.decimalPrecision);
        qc.toStringBuffer(sb);
        cInfoLength = sb.length();
        if (cInfoLength > cInfo.length) {
            throw new AssertionFailedException("Info length = " + cInfoLength);
        }
        sb.getChars(0, sb.length(), cInfo, 0);
        this.ok = ok;
    }

    public int[] getClip() {
        if (!visible && !update)
            return null;

        clip[2] = width;
        clip[3] = /*cExtInfoLength == 0 ? bh : */2 * bh;

        return clip;
    }
}
