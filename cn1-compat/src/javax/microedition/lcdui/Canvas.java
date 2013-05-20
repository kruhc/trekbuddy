package javax.microedition.lcdui;

import java.util.Arrays;

public abstract class Canvas extends Displayable {
    public static final int DOWN = 6;
    public static final int FIRE = 8;
    public static final int GAME_A = 9;
    public static final int GAME_B = 10;
    public static final int GAME_C = 11;
    public static final int GAME_D = 12;
    public static final int KEY_NUM0 = 48;
    public static final int KEY_NUM1 = 49;
    public static final int KEY_NUM2 = 50;
    public static final int KEY_NUM3 = 51;
    public static final int KEY_NUM4 = 52;
    public static final int KEY_NUM5 = 53;
    public static final int KEY_NUM6 = 54;
    public static final int KEY_NUM7 = 55;
    public static final int KEY_NUM8 = 56;
    public static final int KEY_NUM9 = 57;
    public static final int KEY_POUND = 35;
    public static final int KEY_STAR = 42;
    public static final int LEFT = 2;
    public static final int RIGHT = 5;
    public static final int UP = 1;

    protected Image offscreen;

    protected Canvas() {
        super.setDisplayable(new Impl());
        this.offscreen = Image.createImage(com.codename1.ui.Display.getInstance().getDisplayWidth(),
                                           com.codename1.ui.Display.getInstance().getDisplayHeight());
    }

    public boolean hasPointerEvents() {
        return true;
    }

    public boolean hasRepeatEvents() {
        return true;
    }

    public boolean isDoubleBuffered() {
        return false;
    }

    protected abstract void paint(Graphics g);

    public final void repaint() {
        System.out.println("WARN Canvas.repaint");
        getDisplayable().repaint();
    }

    public int getKeyCode(int gameAction) {
        int keyCode;
        switch (gameAction) {
            case UP:
                keyCode = KEY_NUM2;
            break;
            case LEFT:
                keyCode = KEY_NUM4;
            break;
            case RIGHT:
                keyCode = KEY_NUM6;
            break;
            case DOWN:
                keyCode = KEY_NUM8;
            break;
            default:
                throw new IllegalArgumentException(Integer.toString(gameAction));
        }
        return keyCode;
    }

    public int getGameAction(int keyCode) {
        int gameAction;
        switch (keyCode) {
            case Canvas.KEY_NUM5:
            case -5:
                gameAction = Canvas.FIRE;
            break;
            case -1:
                gameAction = Canvas.UP;
            break;
            case -2:
                gameAction = Canvas.DOWN;
            break;
            case -3:
                gameAction = Canvas.LEFT;
            break;
            case -4:
                gameAction = Canvas.RIGHT;
            break;
            default:
                gameAction = 0;
        }

        return gameAction;
    }

    protected void showNotify() {
    }

    protected void hideNotify() {
    }

    protected void keyPressed(int keyCode) {
    }

    protected void keyRepeated(int keyCode) {
    }

    protected void keyReleased(int keyCode) {
    }

    protected void pointerPressed(int x, int y) {
    }

    protected void pointerReleased(int x, int y) {
    }

    protected void pointerDragged(int x, int y) {
    }

    protected void sizeChanged(int w, int h) {
    }

    public final void serviceRepaints() {
        throw new Error("Canvas.serviceRepaints not implemented");
    }

    public void setFullScreenMode(boolean mode) {
        System.out.println("WARN setFullScreenMode not implemented");
    }

    private class Impl extends com.codename1.ui.Form {

        public Impl() {
        }

        public void paint(com.codename1.ui.Graphics g) {
            System.out.println("Canvas$Impl.paint");
            System.out.println("Canvas$Impl.paint; " + Arrays.toString(g.getClip()));
            System.out.println("Canvas$Impl.paint; " + offscreen.getImage().getWidth() + "x" + offscreen.getImage().getHeight());
            g.drawImage(offscreen.getImage(), 0, 0);
        }

        protected void hideNotify() {
            System.out.println("Canvas$Impl.hideNotify");
            Canvas.this.hideNotify();
        }

        protected void showNotify() {
            System.out.println("Canvas$Impl.showNotify");
//            super.showNotify();
            Canvas.this.showNotify();
        }

        protected void onShow() {
            System.out.println("Canvas$Impl.onShow");
        }

        protected void sizeChanged(int width, int height) {
            System.out.println("Canvas$Impl.sizeChanged; " + width + "x" + height);
            Canvas.this.sizeChanged(width, height);
        }

        public void keyPressed(int i) {
            System.out.println("Canvas$Impl.keyPressed; " + i);
            Canvas.this.keyPressed(i);
        }

        public void keyReleased(int i) {
            System.out.println("Canvas$Impl.keyReleased; " + i);
            Canvas.this.keyReleased(i);
        }

        protected void longKeyPress(int i) {
            System.out.println("Canvas$Impl.longKeyPress; " + i);
            Canvas.this.keyRepeated(i);
        }

        public void pointerPressed(int x, int y) {
            System.out.println("Canvas$Impl.pointerPressed; " + x + "x" + y);
            Canvas.this.pointerPressed(x, y);
        }

        public void pointerReleased(int x, int y) {
            System.out.println("Canvas$Impl.pointerPressed; " + x + "x" + y);
            Canvas.this.pointerReleased(x, y);
        }

        public void longPointerPress(int x, int y) {
            System.out.println("Canvas$Impl.longPointerPressed; " + x + "x" + y);
        }

        public void pointerDragged(int x, int y) {
            System.out.println("Canvas$Impl.pointerDragged; " + x + "x" + y);
            Canvas.this.pointerDragged(x, y);
        }

        public void pointerDragged(int[] x, int[] y) {
            System.out.println("Canvas$Impl.pointerDragged; " + x + "x" + y);
            System.out.println(Arrays.toString(x));
            System.out.println(Arrays.toString(y));
            Canvas.this.pointerDragged(x[0], y[0]);
        }
    }
}
