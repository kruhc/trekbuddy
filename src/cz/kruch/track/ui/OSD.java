// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import api.location.LocationProvider;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import java.io.IOException;

import cz.kruch.track.configuration.Config;

final class OSD extends Bar {
    private static final String NO_INFO = "Lon: ? Lat: ?";

    private Image providerStarting;
    private Image providerAvailable;
    private Image providerUnavailable;
    private Image providerOutOfService;

    private int semaforX, semaforY;
    private volatile int providerStatus = LocationProvider.OUT_OF_SERVICE;
    private volatile String recording = null;
    private volatile String extendedInfo;
    private volatile int sat;

    private int str1Width, str2Width;

    private int rw;
    private int[] clip;

    public OSD(int gx, int gy, int width, int height) throws IOException {
        super(gx, gy, width, height);
        this.rw = Desktop.font.charWidth('R');
        this.providerStarting = Image.createImage("/resources/s_blue.png");
        this.providerAvailable = Image.createImage("/resources/s_green.png");
        this.providerUnavailable = Image.createImage("/resources/s_orange.png");
        this.providerOutOfService = Image.createImage("/resources/s_red.png");
        this.clip = new int[]{ gx, gy, -1, -1 };
        this.str1Width = Desktop.font.stringWidth("4*");
        this.str2Width = Desktop.font.stringWidth("44*");
        resize(width, height);
    }

    public void resize(int width, int height) {
        super.resize(width, height);
        this.semaforX = this.width - this.providerAvailable.getWidth() - BORDER;
        this.semaforY = Math.abs((this.bh - this.providerAvailable.getHeight())) / 2;
    }

    public void render(Graphics graphics) {
        if (!visible) {
            return;
        }

        if (info == null) {
            info = NO_INFO;
        }

        boolean isExtInfo = extendedInfo != null && Config.getSafeInstance().isOsdExtended();

        // draw info + extended info bg
        if (!Config.getSafeInstance().isOsdNoBackground()) {
            graphics.drawImage(bar, gx, gy, 0/*Graphics.TOP | Graphics.LEFT*/);
            if (isExtInfo) {
                graphics.drawImage(bar, gx, gy + bh, 0/*Graphics.TOP | Graphics.LEFT*/);
            }
        }

        // not ok? change color...
        if (!ok) {
            graphics.setColor(0xff, 0, 0);
        }

        // draw info + extended info text
        graphics.drawString(info, gx + BORDER, gy, 0/*Graphics.TOP | Graphics.LEFT*/);
        if (isExtInfo) {
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
        switch (providerStatus) {
            case LocationProvider._STARTING:
                graphics.drawImage(providerStarting, semaforX, semaforY, 0/*Graphics.TOP | Graphics.LEFT*/);
                break;
            case LocationProvider.AVAILABLE:
                graphics.drawImage(providerAvailable, semaforX, semaforY, 0/*Graphics.TOP | Graphics.LEFT*/);
                break;
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                graphics.drawImage(providerUnavailable, semaforX, semaforY, 0/*Graphics.TOP | Graphics.LEFT*/);
                break;
            case LocationProvider.OUT_OF_SERVICE:
            case LocationProvider._CANCELLED:
                graphics.drawImage(providerOutOfService,  semaforX, semaforY, 0/*Graphics.TOP | Graphics.LEFT*/);
                break;
        }
    }

    public void setProviderStatus(int providerStatus) {
        this.providerStatus = providerStatus;
    }

    public void setRecording(String recording) {
        this.recording = recording;
    }

    public void setExtendedInfo(String extendedInfo) {
        this.extendedInfo = extendedInfo;
        this.clip = new int[]{ gx, gy, width, extendedInfo == null ? bh : 2 * bh };
    }

    public void setSat(int sat) {
        this.sat = sat;
    }

    public int[] getClip() {
        if (!visible && !update)
            return null;

        clip[2] = width;
        clip[3] = extendedInfo == null ? bh : 2 * bh;
        
        return clip;
    }
}
