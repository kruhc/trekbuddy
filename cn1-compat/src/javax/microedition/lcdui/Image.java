package javax.microedition.lcdui;

import com.codename1.ui.FriendlyAccess;
import com.codename1.ui.ImageEx;

import java.io.IOException;
import java.io.InputStream;

//#define __XAML__

public class Image {

    private Graphics graphics;
    private com.codename1.ui.Image cn1Image;

    private Image(com.codename1.ui.Image image) {
        this.cn1Image = image;
    }

    public com.codename1.ui.Image getNativeImage() {
        return cn1Image;
    }

    public static Image createImage(byte[] data, int offset, int len) {
//#ifdef __XAML__
        return new Image(new ImageEx(FriendlyAccess.getImplementation().createImage(data, offset, len)));
//#else
        return new Image(com.codename1.ui.Image.createImage(data, offset, len));
//#endif
    }

    public static Image createImage(int width, int height) {
//#ifdef __XAML__
        return new Image(new ImageEx(FriendlyAccess.getImplementation().createMutableImage(width, height, 0)));
//#else
        return new Image(com.codename1.ui.Image.createImage(width, height, 0));
//#endif
    }

    public static Image createImage(Image source) {
        return source;
    }

    public static Image createImage(String name) throws IOException {
//        return createImage(com.codename1.ui.Display.getInstance().getResourceAsStream(Image.class, name));
        return createImage(com.codename1.ui.FriendlyAccess.getResourceAsStream(name));
    }

    public static Image createImage(InputStream stream) throws IOException {
//#ifdef __XAML__
        return new Image(new ImageEx(FriendlyAccess.getImplementation().createImage(stream)));
//#else
        return new Image(com.codename1.ui.Image.createImage(stream));
//#endif
    }

    public static Image createRGBImage(int[] rgb, int width, int height, boolean processAlpha) {
//#ifdef __XAML__
        return new Image(new ImageEx(FriendlyAccess.getImplementation().createImage(rgb, width, height)));
//#else
        return new Image(com.codename1.ui.Image.createImage(rgb, width, height));
//#endif
    }

    public Graphics getGraphics() {
        if (graphics == null) {
//#ifdef __LOG__
            com.codename1.io.Log.p("Image.getGraphics; " + cn1Image + "; " + cn1Image.getWidth() + "x" + cn1Image.getHeight(), com.codename1.io.Log.DEBUG);
//#endif
            graphics = new Graphics(cn1Image.getGraphics());
        }
        return graphics;
    }

    public int getHeight() {
        return cn1Image.getHeight();
    }

    public void getRGB(int[] rgbData, int offset, int scanlength, int x, int y, int width, int height) {
        com.codename1.io.Log.p("ERROR Image.getRGB not implemented", com.codename1.io.Log.ERROR);
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
