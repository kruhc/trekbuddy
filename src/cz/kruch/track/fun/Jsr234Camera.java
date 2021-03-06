// @LICENSE@

package cz.kruch.track.fun;

//#ifndef __CN1__

import cz.kruch.track.configuration.Config;

import javax.microedition.amms.control.camera.CameraControl;
import javax.microedition.amms.control.camera.FocusControl;
import javax.microedition.amms.control.camera.SnapshotControl;
import javax.microedition.amms.control.camera.FlashControl;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Control;
import javax.microedition.io.Connector;
import java.util.Vector;

import api.file.File;

/**
 * JSR-234 camera.
 *
 * @author kruhc@seznam.cz
 */
final class Jsr234Camera extends Jsr135Camera {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Jsr234Camera");
//#endif

    private String callbackResult;
    private Throwable callbackException;

    Jsr234Camera() {
    }

    void getResolutions(final Vector v) {
        int[] res = null;
        Player player = null;
        try {
            player = Manager.createPlayer(Config.captureLocator);
            player.realize();
            res = ((CameraControl) player.getControl("javax.microedition.amms.control.camera.CameraControl")).getSupportedStillResolutions();
        } catch (Exception e) {
            // ignore
        } finally {
            try {
                player.close();
            } catch (Exception e) { // NPE or ME
                // ignore
            }
        }
        if (res != null) {
            for (int N = res.length, i = 0; i < N; ) {
                v.addElement((new StringBuffer(16)).append(res[i]).append('x').append(res[i + 1]).toString());
                i += 2;
            }
        }
    }

    void addPlayerListener(final Player player) {
        player.addPlayerListener(this);
    }

    public void playerUpdate(Player player, String event, Object eventData) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("event " + event + "; data " + eventData);
//#endif
        state.append(event).append('(').append(eventData instanceof Control ? "ctrl" : eventData).append(')').append(" -> ");

        if (event.equals(PlayerListener.CLOSED)) {

            // player is zombie
            this.player = null;

            // shutdown
            shutdown();

            // notify
            finished(callbackResult, callbackException);

        } else if (event.equals(PlayerListener.END_OF_MEDIA) || event.equals(PlayerListener.ERROR) || event.equals(PlayerListener.STOPPED)) {

            // close player
            player.close();

        } else if (event.equals(SnapshotControl.SHOOTING_STOPPED)) {

            // storage error preceeded?
            if (callbackException == null) {

                // prepare result
                if (((String) eventData).indexOf(FOLDER_PREFIX) > -1) { // eventData may be URL in some implementations
                    callbackResult = ((String) eventData).substring(((String) eventData).indexOf(FOLDER_PREFIX));
                } else { // eventData is filenameonly, as it should be
                    callbackResult += eventData;
                }

                // rename image
// 2012-01-14: not needed, numbering is done with setFileSuffix                
//                callbackResult = moveImage(callbackResult);
            }

            // close player
            player.close();

        } else if (event.equals(SnapshotControl.STORAGE_ERROR)) {

            // prepare result
            callbackResult = null;
            callbackException = new MediaException(event + ": " + eventData);

            // close player
            player.close();

        }
//#ifdef __LOG__
        else {
            log.warn("unhandled event " + event + "; data " + eventData);
        }
//#endif
    }

    public void run() {

        try {

            // use snapshot control
            if (!Config.snapshotFormat.startsWith("encoding")) {
                state.append("x-jsr234 action -> ");

                // prepare storage
                String imagePath = createImagesFolder(true);
                if (cz.kruch.track.TrackingMIDlet.nokia && imagePath.startsWith(File.PATH_SEPARATOR)) {
                    imagePath = imagePath.substring(1);
                }

                // result (1st part, filename will be appended in the listener method)
                callbackResult = imagePath.substring(imagePath.indexOf(FOLDER_PREFIX));

                // setup controls
                setupControls();

                // shoot one picture
                final SnapshotControl snapshotCtrl = (SnapshotControl) player.getControl("javax.microedition.amms.control.camera.SnapshotControl");
                snapshotCtrl.setDirectory(imagePath);
                snapshotCtrl.setFilePrefix(PIC_PREFIX);
                snapshotCtrl.setFileSuffix(Integer.toString(++imgNum) + PIC_SUFFIX);
//#ifndef __SYMBIAN__
                snapshotCtrl.start(SnapshotControl.FREEZE);
//#else
                snapshotCtrl.start(1);
//#endif

            } else { // old school
                state.append(" x-jsr135 action -> ");

                // take it
                callbackResult = takePicture(control);

                // shutdown
                shutdown();

                // report result
                finished(callbackResult, null);

            }

        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
            state.append("run error: ").append(t.toString()).append(" -> ");

            // report error
            callbackException = t;

            // shutdown
            shutdown();

            // report result
            finished(null, callbackException);
        }
    }

    private void setupControls() {

        // set camera resolution and shutter feedback
        final CameraControl cameraCtrl = (CameraControl) player.getControl("javax.microedition.amms.control.camera.CameraControl");
        cameraCtrl.setStillResolution(Config.snapshotFormatIdx);
        try {
            cameraCtrl.enableShutterFeedback(true);
        } catch (Exception e) {
            // ignore
        }

        // adjust focus
        try {
            final FocusControl focusCtrl = (FocusControl) player.getControl("javax.microedition.amms.control.camera.FocusControl");
            if (focusCtrl != null && focusCtrl.isAutoFocusSupported()) {
                focusCtrl.setFocus(FocusControl.AUTO);
            }
        } catch (Exception e) {
            // ignore
        }
/* // outdoor does not usually need flash
        // adjust flash
        try {
            final FlashControl flashCtrl = (FlashControl) player.getControl("javax.microedition.amms.control.camera.FlashControl");
            if (flashCtrl != null) {
                flashCtrl.setMode(FlashControl.AUTO);
                for (int i = 0; i < 12 && !flashCtrl.isFlashReady(); i++) { // wait 3 sec (12 * 250 ms) max
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
*/
    }

    private String moveImage(final String relPath) {
        // construct 'old school' name
        final StringBuffer sb = new StringBuffer(16);
        sb.append(PIC_PREFIX).append(++imgNum).append(PIC_SUFFIX);
        final String newName = sb.toString();
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("rename picture to " + newName);
//#endif

        File f = null;
        try {
            // rename file
            f = File.open(Config.getFileURL(Config.FOLDER_WPTS, relPath), Connector.READ_WRITE);
            f.rename(newName);
            // update link path
            sb.insert(0, File.PATH_SEPCHAR);
            sb.insert(0, cz.kruch.track.location.GpxTracklog.dateToFileDate(filestamp));
            sb.insert(0, FOLDER_PREFIX);
        } catch (Exception e) {
            sb.setLength(0);
            sb.append(relPath);
        } finally {
            try {
                f.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("new relative path: " + sb.toString());
//#endif

        return sb.toString();
    }
}

//#endif
