// @LICENSE@

package cz.kruch.track.fun;

//#ifdef __ANDROID__

import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

import java.io.RandomAccessFile;
import java.io.File;
import java.net.URI;

/**
 * Android sound player.
 *
 * @author kruhc@seznam.cz
 */
final class AndroidPlayback extends Playback {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("AndroidPlayback");
//#endif
    private static final String TAG = cz.kruch.track.TrackingMIDlet.APP_TITLE;

    AndroidPlayback() {
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
        boolean result = false;

        // player
        MediaPlayer player = null;

        try {
            // create player
            player = new MediaPlayer();
            player.setDataSource(new RandomAccessFile(new File(new URI(url)), "r").getFD());
            player.prepare();
            state.append("x-prepared -> ");

            // start
            player.start();
            state.append("x-started -> ");

            // success
            result = true;

        } catch (Throwable t) {
            
            // log error
            Log.e(TAG, "MediaPlayer failed", t);
            state.append("sound error: ").append(t.toString()).append(" -> ");

            // cleanup
            if (player != null) {
                player.release();
            }
        }

        return result;
    }
}

//#endif /* __ANDROID__ */