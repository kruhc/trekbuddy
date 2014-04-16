package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import java.io.IOException;

public class GameCanvas extends Canvas {
    public static final int DOWN_PRESSED = 64;
    public static final int FIRE_PRESSED = 256;
    public static final int GAME_A_PRESSED = 512;
    public static final int GAME_B_PRESSED = 1024;
    public static final int GAME_C_PRESSED = 2048;
    public static final int GAME_D_PRESSED = 4096;
    public static final int LEFT_PRESSED = 4;
    public static final int RIGHT_PRESSED = 32;
    public static final int UP_PRESSED = 2;

    protected GameCanvas(boolean suppressKeyEvents) {
    }

    protected Graphics getGraphics() {
        if (offscreen == null) {
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) {
//                w = com.codename1.ui.Display.getInstance().getDisplayWidth();
//                h = com.codename1.ui.Display.getInstance().getDisplayHeight();
                w = com.codename1.ui.FriendlyAccess.getImplementation().getDisplayWidth();
                h = com.codename1.ui.FriendlyAccess.getImplementation().getDisplayHeight();
            }
//#ifdef __LOG__
            com.codename1.io.Log.p("GameCanvas.getGraphics; new graphics + " + w + "x" + h, com.codename1.io.Log.DEBUG);
//#endif
            offscreen = Image.createImage(w, h);
            if (offscreen != null) {
                ((ExtendedGraphics) com.codename1.ui.FriendlyAccess.getNativeGraphics(offscreen.getNativeImage())).setFlushable(true);
            }
        }
//#ifdef __LOG__
        com.codename1.io.Log.p(" - offscreen native graphics:  " + com.codename1.ui.FriendlyAccess.getNativeGraphics(offscreen.getNativeImage()), com.codename1.io.Log.DEBUG);
//#endif
        return offscreen.getGraphics();
    }

    public void clearGraphics() {
//#ifdef __LOG__
        com.codename1.io.Log.p("GameCanvas.clearGraphics", com.codename1.io.Log.DEBUG);
//#endif
        if (offscreen == null) {
            com.codename1.io.Log.p("GameCanvas.clearGraphics: back buffer is null!", com.codename1.io.Log.WARNING);
            return;
        }
        // clears backbuffer
        ((ExtendedGraphics) getNativeGraphics()).reset();
    }

    public void flushGraphics() {
//#ifdef __LOG__
        com.codename1.io.Log.p("GameCanvas.flushGraphics", com.codename1.io.Log.DEBUG);
//#endif
        if (offscreen == null) {
            com.codename1.io.Log.p("GameCanvas.flushGraphics: back buffer is null!", com.codename1.io.Log.WARNING);
            return;
        }
        // flushes backbuffer
        ((ExtendedGraphics) getNativeGraphics()).flush();
        // triggers canvas paint
//        repaint();
//        com.codename1.ui.FriendlyAccess.getImplementation().flushGraphics();
    }

    protected void paint(Graphics g) {
        com.codename1.io.Log.p("ERROR GameCanvas.paint not implemented", com.codename1.io.Log.ERROR);
        throw new Error("GameCanvas.paint not implemented");
    }

    public Object getNativeGraphics() {
        return com.codename1.ui.FriendlyAccess.getNativeGraphics(offscreen.getNativeImage());
    }
}
