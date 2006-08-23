// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import api.location.LocationProvider;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import java.io.IOException;

final class OSD extends Bar {
    private static final String NO_INFO = "Lon: ? Lat: ?";

    private Image providerAvailable;
    private Image providerUnavailable;
    private Image providerOutOfService;

    private int semaforX, semaforY;
    private volatile int providerStatus = LocationProvider.OUT_OF_SERVICE;
    private volatile String recording = null;
    private volatile String extendedInfo;

    private int rw;

    public OSD(int gx, int gy, int width, int height) throws IOException {
        super(gx, gy, width, height);
        this.rw = Desktop.font.charWidth('R');
        this.providerAvailable = Image.createImage("/resources/s_green.png");
        this.providerUnavailable = Image.createImage("/resources/s_orange.png");
        this.providerOutOfService = Image.createImage("/resources/s_red.png");
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

        // draw info + extended info bg
        graphics.drawImage(bar, gx, gy, Graphics.TOP | Graphics.LEFT);
        if (extendedInfo != null) {
            graphics.drawImage(bar, gx, gy + bh, Graphics.TOP | Graphics.LEFT);
        }

        // not ok? change color...
        if (!ok) {
            graphics.setColor(255, 0, 0);
        }

        // draw info + extended info text
        graphics.drawString(info, gx, gy, Graphics.TOP | Graphics.LEFT);
        if (extendedInfo != null) {
            graphics.drawString(extendedInfo, gx, gy + bh, Graphics.TOP | Graphics.LEFT);
        }

        // gpx recording
        if (recording != null) {
            if (ok) { // text was 'ok', so change the color now
                graphics.setColor(255, 0, 0);
            }
            graphics.drawChar('R', semaforX - rw, 0, Graphics.TOP | Graphics.LEFT);
        }

        // restore default color
        if (!ok || recording != null) {
            graphics.setColor(255, 255, 255);
        }

        // draw provider status
        switch (providerStatus) {
            case LocationProvider.AVAILABLE:
                graphics.drawImage(providerAvailable, semaforX, semaforY, Graphics.TOP | Graphics.LEFT);
                break;
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                graphics.drawImage(providerUnavailable, semaforX, semaforY, Graphics.TOP | Graphics.LEFT);
                break;
            case LocationProvider.OUT_OF_SERVICE:
                graphics.drawImage(providerOutOfService,  semaforX, semaforY, Graphics.TOP | Graphics.LEFT);
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
    }

    public int[] getClip() {
        if (!visible && !update)
            return null;

        return new int[]{ gx, gy, width, extendedInfo == null ? bh : 2 * bh };
    }
}
