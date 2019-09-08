// @LICENSE@

package cz.kruch.track.fun;

import javax.microedition.media.PlayerListener;
import javax.microedition.media.Control;
import javax.microedition.media.Player;
import javax.microedition.media.Manager;
import javax.microedition.media.control.VolumeControl;
import javax.microedition.io.Connector;
import java.io.InputStream;

/**
 * JSR-135 sound player.
 *
 * @author kruhc@seznam.cz
 */
final class Jsr135Playback extends Playback implements PlayerListener {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Jsr135Playback");
//#endif

    private Control control;
    private InputStream in;
    private int level;
    private boolean closing;

    Jsr135Playback() {
    }

    boolean playSounds() {

        // must be called from background on S^3
        /*
         * 2014-08-25: When called for wpt reached, it is already called from event worker thread.
         * 2014-08-26: Using Disk Worker may block stopping Serial/Bluetooth location provider
         *             when audio hangs. It is better not to use any worker because MMAPI seems
         *             unrealiable on most devices.
         */
        (new Thread(this)).start();

        // no way to tell what will happen, so be positive
        return true;
    }

    boolean sound(final String url) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("play sound " + url);
//#endif

        // result
        boolean result = false;

        // player
        Player player = null;

        try {
            // create player - prefer progressive download using file URL
//#ifndef __RIM__
            if (cz.kruch.track.TrackingMIDlet.nokia || cz.kruch.track.TrackingMIDlet.jp6plus) { // both S40 and S60, also SE JP6+
//#endif
                player = Manager.createPlayer(url);
//#ifndef __RIM__
            } else {
                player = Manager.createPlayer(in = Connector.openInputStream(url), getContentType(url));
            }
//#endif

            // add listener and begin init
            player.addPlayerListener(this);
            player.realize();
            state.append("x-realized -> ");

            // get volume control and set it to max
            control = player.getControl("VolumeControl");
            if (control != null) {
                level = setVolume(100);
                state.append("x-volume(100) -> ");
            }

            // start
            player.start();
            state.append("x-started -> ");

            // success
            result = true;

            // wait for end
            synchronized (this) {
                wait(5000); // allow max 5 secs for playback
            }
            state.append("x-finished -> ");

        } catch (Throwable t) {
//#ifdef __LOG__
            log.error("playback failed: " + t);
            t.printStackTrace();
//#endif
            state.append("error: ").append(t.toString()).append(" -> ");

            // abort
            if (player != null) {
                player.close();
            }
        }

        return result;
    }

    public void playerUpdate(Player player, String event, Object eventData) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("event " + event + "; data " + eventData);
//#endif
//#ifdef __RIM__
        if (event != null && !event.startsWith("com.rim")) // do not log RIM-specific event
//#endif
        state.append("event: ").append(event).append('(').append(eventData instanceof Control ? "ctrl" : eventData).append(')').append(" -> ");

        if (PlayerListener.CLOSED.equals(event)) {

            // update state
            state.append("x-closed -> ");

            // signal end
            synchronized (this) {
                notify();
            }

        } else if (PlayerListener.END_OF_MEDIA.equals(event) || PlayerListener.ERROR.equals(event) || PlayerListener.STOPPED.equals(event)) {

            // process only once when any of the above events occur
            if (closing) {
                return;
            }
            closing = true;

            // restore volume
            if (level != 100) {
                setVolume(level);
                state.append("x-volume(").append(level).append(") -> ");
            }
            control = null; // gc hint

            // close input if provided
            api.file.File.closeQuietly(in);
            in = null; // gc hint

            // update state
            state.append("x-closing -> ");

            // close player
            player.close();
        }
//#ifdef __LOG__
        else {
            log.warn("unhandled event [" + event + "]; data " + eventData);
        }
//#endif
    }

    private int setVolume(final int volume) {
        int level = 0;
        if (control instanceof VolumeControl) {
            try {
                final VolumeControl ctrl = (VolumeControl) control;
                level = ctrl.getLevel();
                ctrl.setLevel(volume);
            } catch (Exception e) {
                // ignore
            }
        }
        return level;
    }

    private static String getContentType(final String url) {
        String contentType = null;
        final String candidate = url.toLowerCase();
        if (candidate.endsWith(".amr")) {
            contentType = "audio/amr";
        } else if (candidate.endsWith(".wav")) {
            contentType = "audio/x-wav";
        } else if (candidate.endsWith(".mp3")) {
            contentType = "audio/mpeg";
        } else if (candidate.endsWith(".aac")) {
            contentType = "audio/aac";
        } else if (candidate.endsWith(".m4a")) {
            contentType = "audio/m4a";
        } else if (candidate.endsWith(".3gp")) {
            contentType = "audio/3gpp";
        }
        return contentType;
    }
}
