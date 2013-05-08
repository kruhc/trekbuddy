package javax.microedition.lcdui;

import java.io.IOException;
import java.io.InputStream;

public class Image {

    private Graphics graphics;
    private com.codename1.ui.Image cn1Image;

    private Image(com.codename1.ui.Image image) {
        this.cn1Image = image;
    }

    com.codename1.ui.Image getImage() {
        return cn1Image;
    }

    public static Image createImage(byte[] data, int offset, int len) {
        return new Image(com.codename1.ui.Image.createImage(data, offset, len));
    }

    public static Image createImage(int width, int height) {
        return new Image(com.codename1.ui.Image.createImage(width, height));
    }

    public static Image createImage(Image source) {
        return source;
    }

    public static Image createImage(String name) throws IOException {
//        return new Image(com.codename1.ui.Image.createImage(name));
        return createImage(com.codename1.ui.Display.getInstance().getResourceAsStream(Image.class, name));
    }

    public static Image createImage(InputStream stream) throws IOException {
        return new Image(com.codename1.ui.Image.createImage(stream));
    }

    public static Image createRGBImage(int[] rgb, int width, int height, boolean processAlpha) {
        return new Image(com.codename1.ui.Image.createImage(rgb, width, height));
    }

    public Graphics getGraphics() {
        if (graphics == null) {
            graphics = new Graphics(cn1Image.getGraphics());
        }
        return graphics;
    }

    public int getHeight() {
        return cn1Image.getHeight();
    }

    public void getRGB(int[] rgbData, int offset, int scanlength, int x, int y, int width, int height) {
        throw new Error("not implemented");
    }

    public int getWidth() {
        return cn1Image.getWidth();
    }

    // non-javax.microedition.lcdui

    public Image scaled(int width, int height) {
        cn1Image = cn1Image.scaled(width, height);
        return this;
    }
}
