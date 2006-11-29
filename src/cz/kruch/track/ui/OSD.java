// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import api.location.LocationProvider;
import api.location.QualifiedCoordinates;
import api.location.Location;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.Sprite;

import cz.kruch.track.configuration.Config;

final class OSD extends Bar {
    private static final String NO_INFO = "Lon: ? Lat: ?";

    private int semaforX, semaforY;
    private int bulletSize;
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
        this.bulletSize = cz.kruch.track.TrackingMIDlet.providers/*[0]*/.getHeight();
        this.semaforX = this.width - this.bulletSize - BORDER;
        this.semaforY = Math.abs((this.bh - this.bulletSize)) >> 1;
    }

    public void render(Graphics graphics) {
        if (!visible) {
            return;
        }

        if (info == null) {
            info = NO_INFO;
        }

        boolean isExtInfo = Config.getSafeInstance().isOsdExtended();

        // draw info + extended info bg
        if (!Config.getSafeInstance().isOsdNoBackground()) {
            graphics.drawImage(bar, gx, gy, 0/*Graphics.TOP | Graphics.LEFT*/);
            if (isExtInfo && extendedInfo != null) {
                graphics.drawImage(bar, gx, gy + bh, 0/*Graphics.TOP | Graphics.LEFT*/);
            }
        }

        // not ok? change color...
        if (!ok) {
            graphics.setColor(0xff, 0, 0);
        }

        // draw info + extended info text
        graphics.drawString(info, gx + BORDER, gy, 0/*Graphics.TOP | Graphics.LEFT*/);
        if (isExtInfo && extendedInfo != null) {
            graphics.drawString(extendedInfo, gx + BORDER, gy + bh, 0/*Graphics.TOP | Graphics.LEFT*/);
            if (sat > 0) {
                String s = sat + "*";
                int w;
                if (sat < 10) {
                    w = str1Width;
                } else {
                    w = str2Width;
                }
                graphics.drawString(s, width - BORDER - w, gy + bh, 0/*Graphics.TOP | Graphics.LEFT*/);
            }
        }

        // gpx recording
        if (recording != null) {
            if (ok) { // text was 'ok', so change the color now
                graphics.setColor(0xff, 0, 0);
            }
            graphics.drawChar('R', semaforX - rw, 0, 0/*Graphics.TOP | Graphics.LEFT*/);
        }

        // restore default color
        if (!ok || recording != null) {
            graphics.setColor(Config.getSafeInstance().isOsdBlackColor() ? 0x00000000 : 0x00ffffff);
        }

        // draw provider status
        int status = providerStatus < LocationProvider._CANCELLED ? providerStatus : LocationProvider.OUT_OF_SERVICE;
/*
        graphics.drawImage(TrackingMIDlet.providers[status], semaforX, semaforY, 0);
*/
        graphics.drawRegion(cz.kruch.track.TrackingMIDlet.providers,
                            status * bulletSize, 0, bulletSize, bulletSize,
                            Sprite.TRANS_NONE,
                            semaforX, semaforY, 0);
    }

    public StringBuffer _getSb() {
        sb.setLength(0);
        return sb;
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
