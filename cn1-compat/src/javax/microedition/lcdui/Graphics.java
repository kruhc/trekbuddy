package javax.microedition.lcdui;

import javax.microedition.lcdui.game.Sprite;

public class Graphics {
    public static final int BASELINE = 64;
    public static final int BOTTOM = 32;
    public static final int DOTTED = 1;
    public static final int HCENTER = 1;
    public static final int LEFT = 4;
    public static final int RIGHT = 8;
    public static final int SOLID = 0;
    public static final int TOP = 16;
    public static final int VCENTER = 2;

    private Font font;
    private com.codename1.ui.Graphics cn1Graphics;

    Graphics(com.codename1.ui.Graphics graphics) {
        this.cn1Graphics = graphics;
    }

    public com.codename1.ui.Graphics getGraphics() {
        return cn1Graphics;
    }

    public void clipRect(int x, int y, int width, int height) {
        cn1Graphics.clipRect(x, y, width, height);
    }

    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        cn1Graphics.drawArc(x, y, width, height, startAngle, arcAngle);
    }

    public void drawChar(char character, int x, int y, int anchor) {
        if (anchor == 0 || anchor == (Graphics.TOP | Graphics.LEFT)) {
            cn1Graphics.drawChar(character, x, y);
        } else
            com.codename1.io.Log.p("ERROR Graphics.drawChar not implemented; anchor " + anchor, com.codename1.io.Log.ERROR);
    }

    public void drawChars(char[] data, int offset, int length, int x, int y, int anchor) {
        if (anchor == 0 || anchor == (Graphics.TOP | Graphics.LEFT)) {
            cn1Graphics.drawChars(data, offset, length, x, y);
        } else
            com.codename1.io.Log.p("ERROR Graphics.drawChars not implemented; anchor " + anchor, com.codename1.io.Log.ERROR);
    }

    public void drawImage(Image img, int x, int y, int anchor) {
        drawImage(img.getNativeImage(), x, y, anchor);
    }

    public void drawLine(int x1, int y1, int x2,int y2) {
        cn1Graphics.drawLine(x1, y1, x2, y2);
    }

    public void drawRect(int x, int y, int width, int height) {
        cn1Graphics.drawRect(x, y, width, height);
    }

    public void drawRegion(Image src,
                           int x_src, int y_src, int width, int height, int transform,
                           int x_dest, int y_dest, int anchor) {
        if (anchor == 0) {
            anchor = javax.microedition.lcdui.Graphics.TOP | javax.microedition.lcdui.Graphics.LEFT;
        }
        if (anchor == (javax.microedition.lcdui.Graphics.TOP | javax.microedition.lcdui.Graphics.LEFT)) {
            final Object nativeGraphics = com.codename1.ui.FriendlyAccess.getNativeGraphics(cn1Graphics);
            com.codename1.ui.FriendlyAccess.getImplementation()
                    .drawRegion(nativeGraphics, src.getNativeImage().getImage(),
                                x_src + cn1Graphics.getTranslateX(), y_src + cn1Graphics.getTranslateY(),
                                width, height, transform,
                                x_dest + cn1Graphics.getTranslateX(), y_dest + cn1Graphics.getTranslateY());
        } else
            com.codename1.io.Log.p("ERROR Graphics.drawRegion not implemented", com.codename1.io.Log.ERROR);
    }

    public void drawRGB(int[] rgbData, int offset, int scanlength,
                        int x, int y, int width, int height, boolean processAlpha) {
        com.codename1.io.Log.p("ERROR Graphics.drawRGB not implemented", com.codename1.io.Log.ERROR);
    }

    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        cn1Graphics.drawRoundRect(x, y, width, height, arcWidth, arcHeight);
    }

    public void drawString(String str, int x, int y, int anchor) {
        if (anchor == 0 || anchor == (Graphics.TOP | Graphics.LEFT)) {
            cn1Graphics.drawString(str, x, y);
        } else
            com.codename1.io.Log.p("ERROR Graphics.drawString not implemented; anchor " + Integer.toHexString(anchor) + "\"" + str + "\"", com.codename1.io.Log.ERROR);
    }

    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        cn1Graphics.fillArc(x, y, width, height, startAngle, arcAngle);
    }

    public void fillRect(int x, int y, int width, int height) {
        cn1Graphics.fillRect(x, y, width, height);
    }

    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        cn1Graphics.fillRoundRect(x, y, width, height, arcWidth, arcHeight);
    }

    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3) {
        cn1Graphics.fillTriangle(x1, y1, x2, y2, x3, y3);
    }

    public int getColor() {
        return cn1Graphics.getColor();
    }

    public void setColor(int rgb) {
        cn1Graphics.setColor(rgb);
    }

    public void setClip(int x, int y, int width, int height) {
        cn1Graphics.setClip(x, y, width, height);
    }

    public void setStrokeStyle(int style) {
        cn1Graphics.setStrokeStyle(style);
    }

    // non-MIDP
    public void setStrokeWidth(int width) {
        cn1Graphics.setStrokeWidth(width);
    }

    public Font getFont() {
        return font;
    }

    public void setFont(Font font) {
        this.font = font;
        cn1Graphics.setFont(font.getNativeFont());
    }

    private void drawImage(com.codename1.ui.Image img, int x, int y, int anchor) {
        if (anchor == 0) {
            anchor = javax.microedition.lcdui.Graphics.TOP | javax.microedition.lcdui.Graphics.LEFT;
        }

        if ((anchor & javax.microedition.lcdui.Graphics.RIGHT) != 0) {
            x -= img.getWidth();
        } else if ((anchor & javax.microedition.lcdui.Graphics.HCENTER) != 0) {
            x -= img.getWidth() / 2;
        }
        if ((anchor & javax.microedition.lcdui.Graphics.BOTTOM) != 0) {
            y -= img.getHeight();
        } else if ((anchor & javax.microedition.lcdui.Graphics.VCENTER) != 0) {
            y -= img.getHeight() / 2;
        }

        cn1Graphics.drawImage(img, x, y);
    }
}
