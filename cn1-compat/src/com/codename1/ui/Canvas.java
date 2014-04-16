package com.codename1.ui;

//#define __XAML__

//#ifndef __XAML__

public class Canvas extends Form {

    public Canvas() {
    }

    public Canvas(String string) {
        super(string);
    }

    public void beforePaint(Graphics g) {
    }

    public void afterPaint(Graphics g) {
        if (tint) {
            g.setColor(getTintColor());
            g.fillRect(0, 0, getWidth(), getHeight(), (byte) ((getTintColor() >> 24) & 0xff));
        }
    }
}

//#endif