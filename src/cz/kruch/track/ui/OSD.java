// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import api.location.LocationProvider;
import api.location.QualifiedCoordinates;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

import cz.kruch.track.configuration.Config;

final class OSD extends Bar {
    private static final String NO_INFO = "Lon: ? Lat: ?";

    private int semaforX, semaforY;
    private volatile int providerStatus = LocationProvider.OUT_OF_SERVICE;
    private volatile String recording = null;
    private volatile String extendedInfo;
    private volatile int sat;

    private int str1Width, str2Width;
    private StringBuffer sb;

    private int rw;
    private int[] clip;

    public OSD(int gx, int gy, int width, int height, Image bar) {
        super(gx, gy, width, height, bar);
        this.rw = Desktop.font.charWidth('R');
        this.clip = new int[]{ gx, gy, -1, -1 };
        this.str1Width = Desktop.font.stringWidth("4*");
        this.str2Width = Desktop.font.stringWidth("44*");
        this.sb = new StringBuffer(32);
        resize(width, height, bar);
    }

    public void resize(int width, int height, Image bar) {
        super.resize(width, height, bar);
        this.semaforX = this.width - NavigationScreens.bulletSize - BORDER;
        this.semaforY = Math.abs((this.bh - NavigationScreens.bulletSize)) >> 1;
    }

    public void render(Graphics graphics) {
        if (!visible) {
            return;
        }

        if (info == null) {
            info = NO_INFO;
        }

        Config cfg = Config.getSafeInstance();
        boolean isBasicInfo = cfg.isOsdBasic();
        boolean isExtInfo = cfg.isOsdExtended();

        // draw info + extended info bg
        if (!cfg.isOsdNoBackground()) {
            if (isBasicInfo) {
                graphics.drawImage(bar, gx, gy, 0);
            }
            if (isExtInfo && extendedInfo != null) {
                graphics.drawImage(bar, gx, gy + (isBasicInfo ? bh : 0), 0);
            }
        }

        // not ok? change color...
        if (!ok) {
            graphics.setColor(0x00FF0000);
        }

        // draw info + extended info text
        if (isBasicInfo) {
            graphics.drawString(info, gx + BORDER, gy, 0);
        }
        if (isExtInfo && extendedInfo != null) {
            graphics.drawString(extendedInfo, gx + BORDER, gy + (isBasicInfo ? bh : 0), 0);
            if (sat > 0) {
                String s = cz.kruch.track.TrackingMIDlet.nStr[sat];
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
            graphics.setColor(cfg.isOsdBlackColor() ? 0x00000000 : 0x00FFFFFF);
        }

        // draw provider status
        NavigationScreens.drawProviderStatus(graphics, providerStatus,
                                             semaforX, semaforY, 0);
    }

    public StringBuffer _getSb() {
        sb.setLength(0);
        return sb;
    }

    public int getProviderStatus() {
        return providerStatus;
    }

    public void setProviderStatus(int providerStatus) {
        this.providerStatus = providerStatus;
    }

    public void setRecording(String recording) {
        this.recording = recording;
    }

    public void setExtendedInfo(String extendedInfo) {
        this.extendedInfo = null;
        this.extendedInfo = extendedInfo;
    }

    public void setSat(int sat) {
        this.sat = sat;
    }

    public void setInfo(QualifiedCoordinates qc, boolean ok) {
        sb.setLength(0);
        if (Config.getSafeInstance().isUseGeocachingFormat() || Config.getSafeInstance().isUseUTM()) {
            qc = qc.toWgs84();
        }
        qc.setHp(Config.getSafeInstance().isDecimalPrecision());
        setInfo(qc.toStringBuffer(sb).toString(), ok);
    }

    public int[] getClip() {
        if (!visible && !update)
            return null;

        clip[2] = width;
        clip[3] = extendedInfo == null ? bh : 2 * bh;

        return clip;
    }
}
