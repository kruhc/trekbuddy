package net.trekbuddy.midlet;

/*
import com.codename1.ui.plaf.UIManager;
import com.codename1.ui.util.Resources;
*/

import javax.microedition.midlet.MIDlet;

// need to force xmlvm to convert these
import com.codename1.ui.BrowserComponent;
import com.codename1.ui.animations.CommonTransitions;
import com.codename1.ui.events.BrowserNavigationCallback;

public class TrackingApp {

    private MIDlet midlet;

    public void init(Object context) {
//#ifdef __LOG__
        com.codename1.io.Log.setReportingLevel(com.codename1.io.Log.DEBUG);
//#else
        com.codename1.io.Log.setReportingLevel(com.codename1.io.Log.INFO);
//#endif
        com.codename1.io.Log.setAutoflush(true);
        com.codename1.io.Log.p("init", com.codename1.io.Log.INFO);
/*
        try {
            Resources theme = Resources.openLayered("/theme");
            com.codename1.io.Log.p("theme is " + theme.getThemeResourceNames()[0], com.codename1.io.Log.INFO);
            UIManager.getInstance().setThemeProps(theme.getTheme(theme.getThemeResourceNames()[0]));
            UIManager.getInstance().getLookAndFeel().setDefaultDialogTransitionIn(CommonTransitions.createEmpty());
            UIManager.getInstance().getLookAndFeel().setDefaultDialogTransitionOut(CommonTransitions.createEmpty());
            UIManager.getInstance().getLookAndFeel().setDefaultFormTransitionIn(CommonTransitions.createEmpty());
            UIManager.getInstance().getLookAndFeel().setDefaultFormTransitionOut(CommonTransitions.createEmpty());
            UIManager.getInstance().getLookAndFeel().setDefaultTensileDrag(false);
        } catch (Throwable t) {
            com.codename1.io.Log.e(t);
        }
*/
    }

    public void start() {
        com.codename1.io.Log.p("start", com.codename1.io.Log.INFO);
        try {
            if (midlet == null) {
                midlet = new cz.kruch.track.TrackingMIDlet();
            }
            midlet.start();
        } catch (Throwable t) {
            com.codename1.io.Log.e(t);
        }
    }

    public void stop() {
        com.codename1.io.Log.p("stop", com.codename1.io.Log.INFO);
        midlet.pause();
    }

    public void destroy() {
        com.codename1.io.Log.p("destroy", com.codename1.io.Log.INFO);
        try {
            midlet.destroy();
        } catch (Throwable t) {
            com.codename1.io.Log.e(t);
        }
    }
}
