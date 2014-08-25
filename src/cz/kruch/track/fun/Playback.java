// @LICENSE@

package cz.kruch.track.fun;

import cz.kruch.track.configuration.Config;

import javax.microedition.lcdui.AlertType;

import api.file.File;

/**
 * Sound player.
 *
 * @author kruhc@seznam.cz
 */
public abstract class Playback implements Runnable {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Playback");
//#endif
    public static StringBuffer state;

    private String baseFolder, userLink, defaultLink;
    private AlertType alert;

    // contract
    abstract boolean playSounds();
    abstract boolean sound(String url);

//#if !__ANDROID__ && !__CN1__
    private static Class factory;
//#endif

    protected Playback() {
        state = new StringBuffer(128);
    }

    public static void play(final String baseFolder,
                            final String userLink, final String defaultLink,
                            final AlertType alert) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("play " + userLink + "," + defaultLink);
//#endif

        if (Config.dataDirExists) {
            try {
                createPlayback(baseFolder, userLink, defaultLink, alert).playSounds();
            } catch (Exception e) {
                // ignore
            }
        } else {
            playAlert(alert);
        }
    }

    public void run() {
        boolean played = false;
        if (!played && userLink != null) {
            played = playLink(userLink);
            state.append("played ").append(userLink).append("? ").append(played).append(" -> ");
        }
        if (!played && defaultLink != null) {
            played = playLink(defaultLink);
            state.append("played ").append(defaultLink).append("? ").append(played).append(" -> ");
        }
        if (!played && alert != null) {
            playAlert(alert);
            state.append("played alert -> ");
        }
    }

    private static Playback createPlayback(final String baseFolder,
                                           final String userLink, final String defaultLink,
                                           final AlertType alert) throws Exception {
        Playback instance;
//#ifdef __ANDROID__
        instance = new AndroidPlayback();
//#elifdef __CN1__
        instance = new WPPlayback();
//#else
        if (factory == null) {
            factory = Class.forName("cz.kruch.track.fun.Jsr135Playback");
        }
        instance = (Playback) factory.newInstance();
//#endif
        instance.baseFolder = baseFolder;
        instance.userLink = userLink;
        instance.defaultLink = defaultLink;
        instance.alert = alert;

        return instance;
    }

    private boolean playLink(final String fileLink) {
        final String url = Config.getFileURL(baseFolder, fileLink);
        try {
            final File f = File.open(url);
            final boolean exists = f.exists();
            f.close();
            if (exists) {
                return sound(url);
            }
        } catch (Exception e) { // IOE or SE
            // ignore
        }
        return false;
    }

    private static void playAlert(final AlertType alert) {
        alert.playSound(cz.kruch.track.ui.Desktop.display);
    }
}
