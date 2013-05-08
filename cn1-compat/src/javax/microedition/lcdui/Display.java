package javax.microedition.lcdui;

import javax.microedition.midlet.MIDlet;

public class Display {
    public static final int COLOR_BACKGROUND = 0;
    public static final int COLOR_FOREGROUND = 1;
    public static final int COLOR_HIGHLIGHTED_BACKGROUND = 2;
    public static final int COLOR_HIGHLIGHTED_FOREGROUND = 3;

    static Display instance;
    private Displayable current;

    private com.codename1.ui.Display cn1Display;

    private Display(com.codename1.ui.Display display) {
        this.cn1Display = display;
    }

    public static Display getDisplay(MIDlet midlet) {
        if (instance == null) {
            instance = new Display(com.codename1.ui.Display.getInstance());
        }
        return instance;
    }

    public void callSerially(Runnable r) {
        cn1Display.callSerially(r);
    }

    public boolean flashBacklight(int duration) {
        // TODO
        return false;
    }

    public int numAlphaLevels() {
        return cn1Display.numAlphaLevels();
    }

    public int getColor(int colorSpecifier) {
        throw new Error("Display.getColor not implemented");
    }

    public Displayable getCurrent() {
        return current;
    }

    public void setCurrent(Alert alert, Displayable nextDisplayable) {
        alert.nextDisplayable = nextDisplayable;
        setCurrent(alert);
    }

    public void setCurrent(final Displayable nextDisplayable) {
        System.out.println("INFO Display.setCurrent " + nextDisplayable + "; " + nextDisplayable.getDisplayable());
        if (nextDisplayable instanceof Alert) {
            System.err.println("Alert: " + ((Alert) nextDisplayable).getString());
            ((com.codename1.ui.Dialog) nextDisplayable.getDisplayable()).show();
            return;
        }
        current = nextDisplayable;
        if (nextDisplayable instanceof Screen) {
            System.out.println("INFO Display.setCurrent show Screen");
            ((com.codename1.ui.Form) nextDisplayable.getDisplayable()).show();
        } else if (nextDisplayable instanceof Canvas) {
            System.out.println("INFO Display.setCurrent show Canvas");
            ((com.codename1.ui.Form) nextDisplayable.getDisplayable()).show();
        } else { // ?
            System.out.println("INFO Display.setCurrent show component; " + nextDisplayable);
            throw new IllegalArgumentException(nextDisplayable.getClass().getName());
        }
    }

    public void setCurrentItem(Item item) {
        throw new Error("Display.setCurrentItem not implemented");
    }

    public boolean vibrate(int duration) {
        // TODO
        return false;
    }
}
