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
final class AndroidPlayback extends Playback implements MediaPlayer.OnCompletionListener {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("AndroidPlayback");
//#endif
    private static final String TAG = cz.kruch.track.TrackingMIDlet.APP_TITLE;

    private long t0;

    AndroidPlayback() {
    }

    boolean playSounds() {

        // better be called on background
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
        MediaPlayer player = null;

        // duration measurement
        t0 = System.currentTimeMillis();

        try {
            // create player
            player = new MediaPlayer();
            player.setOnCompletionListener(this);
            state.append("x-set listener -> ");
            player.setDataSource(new RandomAccessFile(new File(new URI(url)), "r").getFD());
            state.append("x-set datasource -> ");
            player.prepare();
            state.append("x-prepared -> ");

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
            
            // log error
            Log.e(TAG, "playback failed", t);
            state.append("sound error: ").append(t.toString()).append(" -> ");

            // cleanup
            if (player != null) {
                player.release();
            }
        }

        return result;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {

        // release player
        mp.release();

        // log duration
        final long t1 = System.currentTimeMillis();
        state.append("x-on completion (").append(t1 - t0).append(" ms) -> ");

        // signal
        synchronized (this) {
            notify();
        }
    }
}

//#endif /* __ANDROID__ */