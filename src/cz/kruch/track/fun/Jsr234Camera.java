// @LICENSE@

package cz.kruch.track.fun;

import cz.kruch.track.configuration.Config;

import javax.microedition.amms.control.camera.CameraControl;
import javax.microedition.amms.control.camera.FocusControl;
import javax.microedition.amms.control.camera.SnapshotControl;
import javax.microedition.media.Player;
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.control.VideoControl;
import javax.microedition.io.Connector;
import javax.microedition.lcdui.Form;
import java.util.Vector;
import java.io.IOException;

import api.file.File;

/**
 * JSR-234 camera.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class Jsr234Camera extends Camera implements PlayerListener {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Jsr234Camera");
//#endif

    private String callbackResult;
    private Throwable callbackException;

    public Jsr234Camera() {
    }

    public void getResolutions(Vector v) {
        int[] res = null;
        Player player = null;
        try {
            player = Manager.createPlayer(Config.captureLocator);
            player.realize();
            CameraControl camCtrl = (CameraControl) player.getControl("javax.microedition.amms.control.camera.CameraControl");
            res = camCtrl.getSupportedStillResolutions();
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
            final StringBuffer sb = new StringBuffer(16);
            for (int N = res.length, i = 0; i < N; ) {
                sb.delete(0, sb.length());
                v.addElement(sb.append(res[i]).append('x').append(res[i + 1]).toString());
                i += 2;
            }
        }
    }

    public void beforeShoot() throws MediaException {
        if (cz.kruch.track.TrackingMIDlet.jsr234) {

            // set camera resolution
            final CameraControl cameraCtrl = (CameraControl) player.getControl("javax.microedition.amms.control.camera.CameraControl");
            cameraCtrl.setStillResolution(Config.snapshotFormatIdx);

            // adjust focus
            final FocusControl focusCtrl = (FocusControl) player.getControl("javax.microedition.amms.control.camera.FocusControl");
            if (focusCtrl != null) {
                if (focusCtrl.isAutoFocusSupported()) {
                    focusCtrl.setFocus(FocusControl.AUTO);
                } else {
                    focusCtrl.setFocus(Integer.MAX_VALUE);
                }
            }
        }
    }

    public void createFinder(final Form form) throws MediaException {
        Jsr135Camera.createFinder(form, (VideoControl) control);
    }

    public void playerUpdate(Player player, String event, Object eventData) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("event " + event + "; data " + eventData);
//#endif
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
        } else if (event.equals(/*"SHOOTING_STOPPED"*/SnapshotControl.SHOOTING_STOPPED)) {
            // storage error preceeded?
            if (callbackException == null) {
                // prepare result
                if (((String) eventData).indexOf(FOLDER_PREFIX) > -1) { // eventData may be URL in some implementations
                    callbackResult = ((String) eventData).substring(((String) eventData).indexOf(FOLDER_PREFIX));
                } else { // eventData is filenameonly, as it should be
                    callbackResult += eventData;
                }
                // rename image
                callbackResult = moveImage(callbackResult);
            }
            // close player
            player.close();
        } else if (event.equals(/*"STORAGE_ERROR"*/SnapshotControl.STORAGE_ERROR)) {
            // prepare result
            callbackResult = null;
            callbackException = new MediaException(event + ": " + eventData);
            // close player
            player.close();
        }
//#ifdef __LOG__
        else {
            if (log.isEnabled()) log.warn("unhandled event " + event + "; data " + eventData);
        }
//#endif
    }

    public void run() {
        try {

            // create images folder
            final String path = createImagesFolder(true);
            callbackResult = path.substring(path.indexOf(FOLDER_PREFIX));

            // set snapshot attributes
            SnapshotControl snapshotCtrl = (SnapshotControl) player.getControl("javax.microedition.amms.control.camera.SnapshotControl");
            snapshotCtrl.setFilePrefix(PIC_PREFIX);
            snapshotCtrl.setFileSuffix(PIC_SUFFIX);
            snapshotCtrl.setDirectory(path);

            // we need to listen
            player.addPlayerListener(this);

            // shoot one picture
            snapshotCtrl.start(1);

        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
            // report error
            callbackException = t;

            // shut camera
            shutdown();
        }
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
            f = File.open(Config.getFolderURL(Config.FOLDER_WPTS) + relPath, Connector.READ_WRITE);
            f.rename(newName);
            // update link path
            sb.insert(0, File.PATH_SEPCHAR);
            sb.insert(0, cz.kruch.track.location.GpxTracklog.dateToFileDate(timestamp));
            sb.insert(0, FOLDER_PREFIX);
        } catch (IOException e) {
            sb.delete(0, sb.length());
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
