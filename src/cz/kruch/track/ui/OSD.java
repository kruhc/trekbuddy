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

import javax.microedition.lcdui.Graphics;

import api.location.LocationProvider;
import api.location.QualifiedCoordinates;

import cz.kruch.track.configuration.Config;

/**
 * OSD bar.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class OSD extends Bar {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("OSD");
//#endif

    private static final String MM = "<>";

    int providerStatus;

    private boolean recording;
    private boolean ok;
    private int sat;

    int semaforX, semaforY;

    private final StringBuffer sb;
    private final int rw, mmw, str1w, str2w;

    private final char[] cInfo, cExtInfo;
    private int cInfoLength, cExtInfoLength;

    OSD(int gx, int gy, int width, int height) {
        super(gx, gy, width, height);
        this.providerStatus = LocationProvider.OUT_OF_SERVICE;
        this.rw = Desktop.font.charWidth('R');
        this.mmw = Desktop.font.stringWidth("<>");
        this.str1w = Desktop.font.stringWidth("4*");
        this.str2w = Desktop.font.stringWidth("44*");
/*
        this.clip = new int[]{ gx, gy, -1, -1 };
*/
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
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("render");
//#endif
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
                graphics.drawImage(Desktop.bar, gx, gy, Graphics.TOP | Graphics.LEFT);
            }
            if (isExtInfo && cExtInfoLength > 0) {
                graphics.drawImage(Desktop.bar, gx, gy + (isBasicInfo ? bh : 0), Graphics.TOP | Graphics.LEFT);
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
                graphics.drawString(MM,
                                    width - BORDER - mmw,
                                    gy + bh, Graphics.TOP | Graphics.LEFT);
            } else {
                final int sat = this.sat;
                if (sat >= 3 && sat <= 12) {
                    graphics.drawString(NavigationScreens.nStr[sat - 3],
                                        width - BORDER - (sat < 10 ? str1w : str2w),
                                        gy + bh, Graphics.TOP | Graphics.LEFT);
                }
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

        // draw backlight status
        if (cz.kruch.track.ui.nokia.DeviceControl.getBacklightStatus() != 0) {
            NavigationScreens.drawBacklightStatus(graphics);
        }
    }

    public StringBuffer _getSb() {
//        sb.setLength(0);
        return sb.delete(0, sb.length());
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

    public void setNavigationInfo(StringBuffer sb) {
        setExtendedInfo(sb);
    }

    public void setExtendedInfo(StringBuffer sb) {
        if (sb != this.sb) {
            throw new IllegalStateException("Alien StringBuffer");
        }
        cExtInfoLength = sb.length();
        if (cExtInfoLength > cExtInfo.length) {
            throw new IllegalStateException("Extended info length = " + cExtInfoLength);
        }
        sb.getChars(0, cExtInfoLength, cExtInfo, 0);
    }

    public void setInfo(QualifiedCoordinates qc, boolean ok) {
        StringBuffer sb = this.sb;
        sb.delete(0, sb.length());
        NavigationScreens.printTo(qc, sb);
        cInfoLength = sb.length();
        if (cInfoLength > cInfo.length) {
            cInfoLength = cInfo.length;
        }
        sb.getChars(0, cInfoLength, cInfo, 0);
        this.ok = ok;
    }

/*
    public int[] getClip() {
        if (!visible && !update)
            return null;

        clip[2] = width;
        clip[3] = / *cExtInfoLength == 0 ? bh : * /2 * bh;

        return clip;
    }
*/
}
