package javax.microedition.lcdui;

//#define __XAML__

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
//#ifdef __XAML__
            instance = new Display(null);
//#else
            instance = new Display(com.codename1.ui.Display.getInstance());
            instance.cn1Display.setNoSleep(true);
//#endif
        }
        return instance;
    }

    public void callSerially(Runnable r) {
//        cn1Display.callSerially(r);
        com.codename1.ui.FriendlyAccess.getImplementation().callSerially(r);
    }

    public boolean flashBacklight(int duration) {
//        cn1Display.flashBacklight(duration);
        com.codename1.ui.FriendlyAccess.getImplementation().flashBacklight(duration);
        return false;
    }

    public int numAlphaLevels() {
//        return cn1Display.numAlphaLevels();
        return com.codename1.ui.FriendlyAccess.getImplementation().numAlphaLevels();
    }

    public int getColor(int colorSpecifier) {
        com.codename1.io.Log.p("ERROR Display.getColor not implemented", com.codename1.io.Log.ERROR);
        throw new Error("Display.getColor not implemented");
    }

    public Displayable getCurrent() {
        return current;
    }

    public void setCurrent(Alert alert, Displayable nextDisplayable) {
//#ifdef __LOG__
//        com.codename1.io.Log.p("Display.setCurrent alert + " + nextDisplayable + "; " + nextDisplayable.getDisplayable(), com.codename1.io.Log.DEBUG);
        com.codename1.io.Log.p("Display.setCurrent alert + " + nextDisplayable, com.codename1.io.Log.DEBUG);
//#endif
        alert.nextDisplayable = nextDisplayable;
        setCurrent(alert);
    }

    public synchronized void setCurrent(final Displayable nextDisplayable) {
//#ifdef __LOG__
//        com.codename1.io.Log.p("Display.setCurrent " + nextDisplayable + "; " + nextDisplayable.getDisplayable(), com.codename1.io.Log.DEBUG);
        com.codename1.io.Log.p("Display.setCurrent " + nextDisplayable, com.codename1.io.Log.DEBUG);
//#endif
        if (nextDisplayable == current) {
//#ifdef __LOG__
            com.codename1.io.Log.p("already current", com.codename1.io.Log.DEBUG);
//#endif
            return;
        }
        if (nextDisplayable instanceof Alert) {
//#ifdef __LOG__
            com.codename1.io.Log.p("Alert: " + ((Alert) nextDisplayable).getString(), com.codename1.io.Log.DEBUG);
//#endif
//#ifdef __XAML__
            ((Alert)nextDisplayable).show();
//#else
            ((com.codename1.ui.Dialog) nextDisplayable.getDisplayable()).showModeless();
//#endif
            return;
        }
        if (current instanceof Canvas) {
            ((Canvas) current).hideNotify();
        }
        nextDisplayable.setShown(false);
        current = nextDisplayable;
//#ifdef __XAML__
        if (nextDisplayable instanceof List) {
            ((List) nextDisplayable).show();
        } else if (nextDisplayable instanceof Form) {
            ((Form) nextDisplayable).show();
        } else if (nextDisplayable instanceof Canvas) {
            ((Canvas) nextDisplayable).showNotify();
            ((Canvas) nextDisplayable).show();
        } else { // unsupported screen
            com.codename1.io.Log.p("ERROR Display.setCurrent show component; " + nextDisplayable, com.codename1.io.Log.ERROR);
            throw new IllegalArgumentException(nextDisplayable.getClass().getName());
        }
//#else
        if (nextDisplayable instanceof Screen) {
            ((com.codename1.ui.Form) nextDisplayable.getDisplayable()).show();
        } else if (nextDisplayable instanceof Canvas) {
            ((Canvas) nextDisplayable).showNotify();
            ((com.codename1.ui.Form) nextDisplayable.getDisplayable()).show();
        } else { // unsupported screen
            com.codename1.io.Log.p("ERROR Display.setCurrent show component; " + nextDisplayable, com.codename1.io.Log.ERROR);
            throw new IllegalArgumentException(nextDisplayable.getClass().getName());
        }
//#endif
        nextDisplayable.setShown(true);
    }

    public void setCurrentItem(Item item) {
        com.codename1.io.Log.p("ERROR Display.setCurrentItem not implemented", com.codename1.io.Log.ERROR);
        throw new Error("Display.setCurrentItem not implemented");
    }

    public boolean vibrate(int duration) {
//        cn1Display.vibrate(duration);
        com.codename1.ui.FriendlyAccess.getImplementation().vibrate(duration);
        return false;
    }
}
