// @LICENSE@

package cz.kruch.track.fun;

import android.media.MediaPlayer;
import android.net.Uri;

/*
import javax.microedition.media.MediaException;
import javax.microedition.lcdui.Form;
import java.util.Vector;
*/

/**
 * Android multimedia.
 */
final class AndroidCamera extends Camera {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("AndroidCamera");
//#endif

    public AndroidCamera() {
    }

/*
	void getResolutions(final Vector v) {
	}

	void beforeShoot() throws MediaException {
	}

	void createFinder(final Form form) throws MediaException {
	}
*/

    boolean playSound(final String url) {
        MediaPlayer player = MediaPlayer.create(org.microemu.android.MicroEmulator.context,
                                                Uri.parse(url));
        try {
            player.start();
            return true;
        } catch (Exception e) { // NPE od IOE
//#ifdef __LOG__
            e.printStackTrace();
//#endif
            if (player != null) {
                player.release();
            }
        }
        return false;
    }

    public void run() {
    }
}
