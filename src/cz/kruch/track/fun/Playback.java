// @LICENSE@

package cz.kruch.track.fun;

import cz.kruch.track.configuration.Config;

import javax.microedition.media.PlayerListener;
import javax.microedition.media.Player;
import javax.microedition.media.Manager;
import javax.microedition.media.Control;
import javax.microedition.media.control.VolumeControl;
import javax.microedition.io.Connector;
import java.io.InputStream;
import java.io.IOException;

/**
 * Sound player.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class Playback implements PlayerListener {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Playback");
//#endif

    private Control control;
    private InputStream in;
    private String contentType;
    private int level;

    private Playback(InputStream in, String name) {
        this.in = in;
        if (name.endsWith(".amr")) {
            contentType = "audio/amr";
        } else if (name.endsWith(".wav")) {
            contentType = "audio/x-wav";
        } else if (name.endsWith(".mp3")) {
            contentType = "audio/mpeg";
        }
    }

    public void playerUpdate(Player player, String event, Object eventData) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("event " + event + "; data " + eventData);
//#endif
        if (event.equals(PlayerListener.CLOSED)) {
            // cleanup
            onClose();
        } else if (event.equals(PlayerListener.END_OF_MEDIA) || event.equals(PlayerListener.ERROR) || event.equals(PlayerListener.STOPPED)) {
            // close player
            player.close();
        }
//#ifdef __LOG__
        else {
            if (log.isEnabled()) log.warn("unhandled event " + event + "; data " + eventData);
        }
//#endif
    }

    /**
     * Plays a sound from file.

     * @param name filename
     * @return <code>true</code> if player started playing; <code>false</code> otherwise
     */
    public static boolean play(final String name) {
        if (Config.dataDirExists/* && api.file.File.isFs()*/ && name != null) {
            InputStream in = null;
            try {
                return (new Playback(in = Connector.openInputStream(Config.getFolderURL(Config.FOLDER_SOUNDS) + name), name)).sound();
            } catch (Throwable t) {
                try {
                    in.close();
                } catch (Exception e) { // NPE or IOE
                    // ignore
                }
            }
        }

        return false;
    }

    private boolean sound() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("play sound");
//#endif

        // player
        Player player = null;

        try {
            // create player
            player = Manager.createPlayer(in, contentType);
            player.realize();
            player.prefetch();

            // get volume control and set it to max
            control = player.getControl("VolumeControl");
            if (control != null) {
                level = ((VolumeControl) control).getLevel();
                ((VolumeControl) control).setLevel(100);
            }

            // we need to listen
            player.addPlayerListener(this);

            // start
            player.start();

            return true;

        } catch (Throwable t) {
//#ifdef __LOG__
            if (log.isEnabled()) log.error("play failed: " + t);
            t.printStackTrace();
//#endif

            // abort
            if (player != null) {
                player.close();
            }

            return false;
        }
    }

    private void onClose() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("on close");
//#endif

        // close input
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                // ignore
            }
            in = null; // gc hint
        }

        // restore volume
        if (control instanceof VolumeControl) {
            ((VolumeControl) control).setLevel(level);
        }
        control = null; // gc hint
    }
}
