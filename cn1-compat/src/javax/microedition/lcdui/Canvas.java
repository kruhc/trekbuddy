package javax.microedition.lcdui;

//#define __XAML__

import com.codename1.ui.FriendlyAccess;

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

//    private CanvasImpl form;

    protected Canvas() {
        //super.setDisplayable(form = (new CanvasImpl()).init());
    }

//#ifdef __XAML__

    public void show() {
//        if (form == null) {
//            super.setDisplayable(form = (new CanvasImpl()).init());
//            form.show();
//        } else {
            FriendlyAccess.execute("show-canvas", new Object[]{ this });
//        }
    }

//#endif
    
    public boolean hasPointerEvents() {
        return true;
    }

    public boolean hasRepeatEvents() {
//#ifdef __XAML__
        return false;
//#else
        return true;
//#endif
    }

    public boolean isDoubleBuffered() {
        return true;
    }

    @Override
    public int getWidth() {
//#ifdef __XAML__
//        return com.codename1.ui.Display.getInstance().getDisplayWidth();
        return com.codename1.ui.FriendlyAccess.getImplementation().getDisplayWidth();
//#else
        return form.getWidth();
//#endif
    }

    @Override
    public int getHeight() {
//#ifdef __XAML__
//        return com.codename1.ui.Display.getInstance().getDisplayHeight();
        return com.codename1.ui.FriendlyAccess.getImplementation().getDisplayHeight();
//#else
        return form.getHeight();
//#endif
    }

    protected abstract void paint(Graphics g);

    public final void repaint() {
//#ifdef __XAML__
        paint(offscreen.getGraphics());
//#else
//#ifdef __LOG__
        com.codename1.io.Log.p("Canvas.repaint", com.codename1.io.Log.DEBUG);
//        com.codename1.io.Log.p(" - displayable: " + getDisplayable(), com.codename1.io.Log.DEBUG);
//        com.codename1.io.Log.p(" - canvas size: " + form.getCanvas().getWidth() + "x" + form.getCanvas().getHeight());
        com.codename1.io.Log.p(" - canvas size: " + form.getWidth() + "x" + form.getHeight());
//#endif
//        getDisplayable().repaint();
//        form.getCanvas().repaint(); // bypass Form.repaint???
        form.repaint();
//#endif
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
            case Canvas.KEY_NUM2:
            case -1:
                gameAction = Canvas.UP;
            break;
            case Canvas.KEY_NUM8:
            case -2:
                gameAction = Canvas.DOWN;
            break;
            case Canvas.KEY_NUM4:
            case -3:
                gameAction = Canvas.LEFT;
            break;
            case Canvas.KEY_NUM6:
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

    protected void pointerRepeated(int x, int y) {
    }

    protected void pointerReleased(int x, int y) {
    }

    protected void pointerDragged(int x, int y) {
    }

    protected void sizeChanged(int w, int h) {
    }

    public final void serviceRepaints() {
        com.codename1.io.Log.p("ERROR Canvas.serviceRepaints not implemented", com.codename1.io.Log.ERROR);
        throw new Error("Canvas.serviceRepaints not implemented");
    }

    public void setFullScreenMode(boolean mode) {
        com.codename1.io.Log.p("Canvas.setFullScreenMode not implemented yet", com.codename1.io.Log.WARNING);
    }

//#ifndef __XAML__

    private class CanvasImpl extends com.codename1.ui.Canvas {

//        private Impl impl;
        private int width, height;
        private int pointerX, pointerY;

        public CanvasImpl() {
        }

        public CanvasImpl init() {
//            addComponent(impl = new Impl(this));
//            com.codename1.ui.plaf.Style s = impl.getStyle();
//            s.setPadding(0, 0, 0, 0);
//            s.setMargin(0, 0, 0, 0);
            return this;
        }

//        public Impl getCanvas() {
//            return impl;
//        }

        @Override
        public boolean animate() {
            return false;
        }

        @Override
        public void paintBackground(com.codename1.ui.Graphics graphics) {
        }
        
        @Override
        protected void hideNotify() {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$ImplScreen.hideNotify", com.codename1.io.Log.DEBUG);
//#endif
            Canvas.this.hideNotify();
        }

        @Override
        protected void showNotify() {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$ImplScreen.showNotify", com.codename1.io.Log.DEBUG);
//#endif
            Canvas.this.showNotify();
        }

        @Override
        protected void onShow() {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$ImplScreen.onShow", com.codename1.io.Log.DEBUG);
//#endif
        }

        @Override
        protected void onShowCompleted() {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$ImplScreen.onShowCompleted", com.codename1.io.Log.DEBUG);
//#endif
        }

        @Override
        protected void sizeChanged(int width, int height) {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$ImplSreen.sizeChanged; " + width + "x" + height, com.codename1.io.Log.DEBUG);
            com.codename1.io.Log.p(" - self: " + getWidth() + "x" + getHeight(), com.codename1.io.Log.DEBUG);
//#endif
            // form does not have the new dimensions yet!!!

//            impl.setShouldCalcPreferredSize(true);
            setShouldCalcPreferredSize(true);
        }

        @Override
        public void paint(com.codename1.ui.Graphics g) {
            // assert
            if (offscreen == null) {
//                throw new IllegalStateException("back buffer is null");
                com.codename1.io.Log.p("Canvas$Impl.paint: back buffer is null!", com.codename1.io.Log.WARNING);
                return;
            }
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.paint; clip: " + Arrays.toString(g.getClip()) + " offscreen image: " + offscreen.getNativeImage().getWidth() + "x" + offscreen.getNativeImage().getHeight(), com.codename1.io.Log.DEBUG);
//#endif
            // mimic Form's paint cycle
            beforePaint(g);

            // render back buffer
            g.drawImage(offscreen.getNativeImage(), 0, 0);

            // mimic Form's paint cycle 
            afterPaint(g);
        }

        @Override
        protected com.codename1.ui.geom.Dimension calcPreferredSize() {
            width = getWidth();
            height = getHeight();
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.calcPreferredSize - " + width + "x" + height, com.codename1.io.Log.DEBUG);
            com.codename1.io.Log.p(" - self: " + getWidth() + "x" + getHeight(), com.codename1.io.Log.DEBUG);
//#endif
            return new com.codename1.ui.geom.Dimension(width, height);
        }

        @Override
        protected void laidOut() {
            int newWidth = getWidth();
            int newHeight = getHeight();
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.laidOut; " + getWidth() + "x" + getHeight(), com.codename1.io.Log.DEBUG);
            com.codename1.io.Log.p(" - new self: " + newWidth + "x" + newHeight, com.codename1.io.Log.DEBUG);
            com.codename1.io.Log.p(" - self: " + width + "x" + height, com.codename1.io.Log.DEBUG);
            com.codename1.io.Log.p(" - selfc: " + getWidth() + "x" + getHeight(), com.codename1.io.Log.DEBUG);
//#endif
            if (width != newWidth || height != newHeight) {
//#ifdef __LOG__
                com.codename1.io.Log.p(" - dimension changed, invalidate itself", com.codename1.io.Log.DEBUG);
//#endif                
                width = newWidth;
                height = newHeight;
                Canvas.this.offscreen = null;
                Canvas.this.sizeChanged(width, height);
            }
        }

        @Override
        public void keyPressed(int i) {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.keyPressed; " + i, com.codename1.io.Log.DEBUG);
//#endif
            Canvas.this.keyPressed(i);
        }

        @Override
        public void keyReleased(int i) {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.keyReleased; " + i, com.codename1.io.Log.DEBUG);
//#endif
            Canvas.this.keyReleased(i);
        }

        @Override
        protected void longKeyPress(int i) {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.longKeyPress; " + i, com.codename1.io.Log.DEBUG);
//#endif
            Canvas.this.keyRepeated(i);
        }

        @Override
        public void pointerPressed(int x, int y) {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.pointerPressed; " + x + "x" + y, com.codename1.io.Log.DEBUG);
//#endif
            Canvas.this.pointerPressed(pointerX = x, pointerY = y);
        }

        @Override
        public void pointerReleased(int x, int y) {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.pointerReleased; " + x + "x" + y, com.codename1.io.Log.DEBUG);
//#endif
            if (pointerX != x || pointerY != y) {
                Canvas.this.pointerDragged(x, y);
            }
            Canvas.this.pointerReleased(x, y);
        }

/*
        @Override
        public void longPointerPress(int x, int y) {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.longPointerPressed; " + x + "x" + y, com.codename1.io.Log.DEBUG);
//#endif
            Canvas.this.pointerRepeated(x, y);
        }
*/

        @Override
        public void pointerDragged(int x, int y) {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.pointerDragged; " + x + "x" + y, com.codename1.io.Log.DEBUG);
//#endif
            Canvas.this.pointerDragged(pointerX = x, pointerY = y);
        }

        @Override
        public void pointerDragged(int[] x, int[] y) {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.pointerDragged; " + x + "x" + y + "; " + Arrays.toString(x) + Arrays.toString(y), com.codename1.io.Log.DEBUG);
//#endif
            Canvas.this.pointerDragged(pointerX = x[0], pointerY = y[0]);
        }
    }

    private class Impl extends com.codename1.ui.Component {

        private CanvasImpl parent;

        private int width, height;
        private int pointerX, pointerY;

        public Impl(CanvasImpl parent) {
            this.parent = parent;
        }

        @Override
        public boolean animate() {
            return false;
        }

        @Override
        protected com.codename1.ui.geom.Dimension calcPreferredSize() {
            int w = parent.getWidth();
            int h = parent.getHeight();
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.calcPreferredSize - " + w + "x" + h, com.codename1.io.Log.DEBUG);
            com.codename1.io.Log.p(" - content pane: " + parent.getContentPane().getWidth() + "x" + parent.getContentPane().getHeight(), com.codename1.io.Log.DEBUG);
//#endif
            return new com.codename1.ui.geom.Dimension(w, h);
        }

        @Override
        protected void laidOut() {
            int newWidth = parent.getContentPane().getWidth();
            int newHeight = parent.getContentPane().getHeight();
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.laidOut; " + getWidth() + "x" + getHeight(), com.codename1.io.Log.DEBUG);
            com.codename1.io.Log.p(" - new self: " + newWidth + "x" + newHeight, com.codename1.io.Log.DEBUG);
            com.codename1.io.Log.p(" - self: " + width + "x" + height, com.codename1.io.Log.DEBUG);
            com.codename1.io.Log.p(" - selfc: " + getWidth() + "x" + getHeight(), com.codename1.io.Log.DEBUG);
//#endif
            if (width != newWidth || height != newHeight) {
                width = newWidth;
                height = newHeight;
                Canvas.this.offscreen = null;
                Canvas.this.sizeChanged(width, height);
            }
        }

        @Override
        public void paintBackground(com.codename1.ui.Graphics graphics) {
        }

        @Override
        public void paint(com.codename1.ui.Graphics g) {
            // assert
            if (offscreen == null) {
//                throw new IllegalStateException("back buffer is null");
                com.codename1.io.Log.p("Canvas$Impl.paint: back buffer is null!", com.codename1.io.Log.WARNING);
                return;
            }
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.paint; clip: " + Arrays.toString(g.getClip()) + " offscreen image: " + offscreen.getNativeImage().getWidth() + "x" + offscreen.getNativeImage().getHeight(), com.codename1.io.Log.DEBUG);
//#endif
            // render back buffer
            g.drawImage(offscreen.getNativeImage(), 0, 0);
        }

/*
        @Override
        protected void hideNotify() {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.hideNotify", com.codename1.io.Log.DEBUG);
//#endif
            Canvas.this.hideNotify();
        }

        @Override
        protected void showNotify() {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.showNotify", com.codename1.io.Log.DEBUG);
//#endif
            Canvas.this.showNotify();
        }

        @Override
        protected void onShow() {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.onShow", com.codename1.io.Log.DEBUG);
//#endif
        }

        @Override
        protected void onShowCompleted() {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.onShowCompleted", com.codename1.io.Log.DEBUG);
//#endif
        }

        @Override
        protected void sizeChanged(int width, int height) {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.sizeChanged; " + width + "x" + height, com.codename1.io.Log.DEBUG);
//#endif
            Canvas.this.sizeChanged(width, height);
        }
*/
        @Override
        public void keyPressed(int i) {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.keyPressed; " + i, com.codename1.io.Log.DEBUG);
//#endif
            Canvas.this.keyPressed(i);
        }

        @Override
        public void keyReleased(int i) {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.keyReleased; " + i, com.codename1.io.Log.DEBUG);
//#endif
            Canvas.this.keyReleased(i);
        }

        @Override
        protected void longKeyPress(int i) {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.longKeyPress; " + i, com.codename1.io.Log.DEBUG);
//#endif
            Canvas.this.keyRepeated(i);
        }

        @Override
        public void pointerPressed(int x, int y) {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.pointerPressed; " + x + "x" + y, com.codename1.io.Log.DEBUG);
//#endif
            Canvas.this.pointerPressed(pointerX = x, pointerY = y);
        }

        @Override
        public void pointerReleased(int x, int y) {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.pointerReleased; " + x + "x" + y, com.codename1.io.Log.DEBUG);
//#endif
            if (pointerX != x || pointerY != y) {
                Canvas.this.pointerDragged(x, y);
            }
            Canvas.this.pointerReleased(x, y);
        }

/*
        @Override
        public void longPointerPress(int x, int y) {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.longPointerPressed; " + x + "x" + y, com.codename1.io.Log.DEBUG);
//#endif
            Canvas.this.pointerRepeated(x, y);
        }
*/

        @Override
        public void pointerDragged(int x, int y) {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.pointerDragged; " + x + "x" + y, com.codename1.io.Log.DEBUG);
//#endif
            Canvas.this.pointerDragged(pointerX = x, pointerY = y);
        }

        @Override
        public void pointerDragged(int[] x, int[] y) {
//#ifdef __LOG__
            com.codename1.io.Log.p("Canvas$Impl.pointerDragged; " + x + "x" + y + "; " + Arrays.toString(x) + Arrays.toString(y), com.codename1.io.Log.DEBUG);
//#endif
            Canvas.this.pointerDragged(pointerX = x[0], pointerY = y[0]);
        }
    }

//#endif

}
