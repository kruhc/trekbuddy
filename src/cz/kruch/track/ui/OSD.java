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

    private int providerStatus = LocationProvider.OUT_OF_SERVICE;
    private int semaforX, semaforY;
    private String gpxRecording = null;
    private String extendedInfo;

    private int bh;
    private int rw;

    public OSD(int gx, int gy, int width, int height) throws IOException {
        super(gx, gy, width, height);
        this.providerAvailable = Image.createImage("/resources/s_green.png");
        this.providerUnavailable = Image.createImage("/resources/s_orange.png");
        this.providerOutOfService = Image.createImage("/resources/s_red.png");
        this.semaforX = this.width - this.providerAvailable.getWidth() - BORDER;
        this.semaforY = Math.abs((this.font.getHeight() - this.providerAvailable.getHeight())) / 2;
        this.bh = bar.getHeight();
        this.rw = font.charWidth('R');
    }

    public void render(Graphics graphics) {
        if (!visible) {
            return;
        }
        
        if (info == null) {
            info = NO_INFO;
        }

        // draw position
        graphics.drawImage(bar, gx, gy, Graphics.TOP | Graphics.LEFT);
        if (ok) {
            graphics.setColor(255, 255, 255);
        } else {
            graphics.setColor(255, 0, 0);
        }
        graphics.setFont(font);
        graphics.drawString(info, gx, gy, Graphics.TOP | Graphics.LEFT);

        // gpx recording
        if (gpxRecording != null) {
            graphics.setColor(255, 0, 0);
            graphics.drawChar('R', semaforX - rw, 0, Graphics.TOP | Graphics.LEFT);
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

        // draw extende info
        if (extendedInfo != null) {
            if (ok) {
                graphics.setColor(255, 255, 255);
            } else {
                graphics.setColor(255, 0, 0);
            }
            graphics.drawImage(bar, gx, gy + bh, Graphics.TOP | Graphics.LEFT);
            graphics.drawString(extendedInfo, gx, gy + bh, Graphics.TOP | Graphics.LEFT);
        }
    }

    public void setProviderStatus(int providerStatus) {
        this.providerStatus = providerStatus;
    }

    public void setGpxRecording(String gpxRecording) {
        this.gpxRecording = gpxRecording;
    }

    public void setExtendedInfo(String extendedInfo) {
        this.extendedInfo = extendedInfo;
    }
}
