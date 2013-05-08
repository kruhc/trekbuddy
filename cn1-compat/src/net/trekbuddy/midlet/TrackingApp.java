package net.trekbuddy.midlet;

import com.codename1.ui.plaf.UIManager;
import com.codename1.ui.util.Resources;

import javax.microedition.midlet.MIDlet;

public class TrackingApp {

    private MIDlet midlet;

    public void init(Object context) {
//        com.codename1.io.Log.setReportingLevel(com.codename1.io.Log.DEBUG);
//        com.codename1.io.Log.getInstance().setFileWriteEnabled(true);
//        com.codename1.io.Log.getInstance().setFileURL("cn1.log");
//        com.codename1.io.Log.p("init", com.codename1.io.Log.INFO);
        try {
            Resources theme = Resources.openLayered("/theme");
            UIManager.getInstance().setThemeProps(theme.getTheme(theme.getThemeResourceNames()[0]));
        } catch (Throwable t) {
//            com.codename1.io.Log.e(e);
        }
    }

    public void start() {
//        com.codename1.io.Log.p("start", com.codename1.io.Log.INFO);
        try {
            midlet = new cz.kruch.track.TrackingMIDlet();
            midlet.start();
        } catch (Throwable t) {
//            com.codename1.io.Log.e(e);
        }
    }

    public void stop() {
//        com.codename1.io.Log.p("stop", com.codename1.io.Log.INFO);
        midlet.pause();
    }

    public void destroy() {
//        com.codename1.io.Log.p("destroy", com.codename1.io.Log.INFO);
        try {
            midlet.destroy();
        } catch (Throwable t) {
//            com.codename1.io.Log.e(e);
        }
    }

    private static void showError(final String message, final Throwable t) {
//        com.codename1.ui.Dialog dlg = new com.codename1.ui.Dialog("TrekBuddy");
//        if (message != null) {
//            dlg.addComponent(new com.codename1.ui.Label(message));
//        }
//        if (t != null) {
//            dlg.addComponent(new com.codename1.ui.Label(t.toString()));
//        }
//        dlg.show();
        com.codename1.ui.Dialog.show("TrekBuddy", message + (t == null ? "" : t.toString()), "Close", null);
    }
}
