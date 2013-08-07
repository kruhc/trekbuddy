// @LICENSE@

package cz.kruch.track.fun;

//#ifdef __CN1__

/**
 * WP sound player.
 *
 * @author kruhc@seznam.cz
 */
final class WPPlayback extends Playback {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("WPPlayback");
//#endif

    WPPlayback() {
    }

    boolean playSounds() {

        // better be called from background
        cz.kruch.track.ui.Desktop.getDiskWorker().enqueue(this);

        // no way to tell what will happen, so be positive
        return true;
    }

    boolean sound(final String url) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("play sound " + url);
//#endif

        // result
        boolean result = true;

        // call WP backend
        com.codename1.ui.FriendlyAccess.getImplementation().execute("play-sound", new Object[]{
            url
        });

        return result;
    }
}

//#endif /* __CN1__ */