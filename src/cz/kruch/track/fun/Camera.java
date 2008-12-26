// @LICENSE@

package cz.kruch.track.fun;

import cz.kruch.track.event.Callback;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.ui.Desktop;
import cz.kruch.track.Resources;

import javax.microedition.media.Player;
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.Control;
import javax.microedition.media.control.VideoControl;
import javax.microedition.media.control.VolumeControl;
import javax.microedition.io.Connector;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Item;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Vector;

import api.file.File;

/**
 * Helper for MMAPI. Used to capture snapshots, play sounds etc.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class Camera implements CommandListener, PlayerListener, Runnable {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Camera");
//#endif

    private static final String PIC_PREFIX      = "pic-";
    private static final String PIC_SUFFIX      = ".jpg";
    private static final String FOLDER_PREFIX   = "images-";
    private static final byte BYTE_FF = (byte) 0xFF;

    // supported still resolutions
    private static String[] resolutions;

    // image counter
    private static int imgNum;

    // common members
    private Player player;
    private Control control;

    // video capture members
    private Displayable next;
    private Callback callback;
    private String callbackResult;
    private Throwable callbackException;
    private long timestamp;

    // sound player members
    private InputStream in;
    private String contentType;
    private int level;

    private Camera(Displayable next, Callback callback, long timestamp) {
        this.next = next;
        this.callback = callback;
        this.timestamp = timestamp;
    }

    private Camera(InputStream in, String name) {
        this.in = in;
        if (name.endsWith(".amr")) {
            contentType = "audio/amr";
        } else if (name.endsWith(".wav")) {
            contentType = "audio/x-wav";
        } else if (name.endsWith(".mp3")) {
            contentType = "audio/mpeg";
        }
    }

    /**
     * Opens a camera for shooting.
     *
     * @param next next displayable
     * @param callback event callback
     * @param timestamp timestamp (for picture taking)
     * @throws Throwable if anything goes wrong
     */
    public static void take(final Displayable next, final Callback callback,
                            final long timestamp) throws Throwable {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("capture locator: " + Config.captureLocator);
//#endif
        (new Camera(next, callback, timestamp)).photo();
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
                return (new Camera(in = Connector.openInputStream(Config.getFolderURL(Config.FOLDER_SOUNDS) + name), name)).sound();
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

    public static String[] getStillResolutions() {
        if (resolutions == null) {
            final Vector v = new Vector(8);
//#ifndef __RIM__
            if (cz.kruch.track.TrackingMIDlet.jsr234) {
                int[] res = null;
                Player player = null;
                try {
                    player = Manager.createPlayer(Config.captureLocator);
                    player.realize();
                    javax.microedition.amms.control.camera.CameraControl camCtrl = (javax.microedition.amms.control.camera.CameraControl) player.getControl("javax.microedition.amms.control.camera.CameraControl");
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
            } else {
//#endif
                final String encodings = System.getProperty("video.snapshot.encodings");
                int start = encodings.indexOf("encoding=");
                while (start > -1) {
                    int end = encodings.indexOf("encoding=", start + 9);
                    final String item;
                    if (end > -1) {
                        item = encodings.substring(start, end).trim();
                    } else {
                        item = encodings.substring(start).trim();
                    }
                    v.addElement(item);
                    start = end;
                }
//#ifndef __RIM__
            }
//#endif            
            resolutions = new String[v.size()];
            v.copyInto(resolutions);
        }

        return resolutions;
    }

    public void playerUpdate(Player player, String event, Object eventData) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("event " + event + "; data " + eventData);
//#endif
        if (event.equals(PlayerListener.CLOSED)) {
            // just close
            onClose();
        } else if (event.equals(PlayerListener.END_OF_MEDIA) || event.equals(PlayerListener.ERROR) || event.equals(PlayerListener.STOPPED)) {
            // close player
            player.close();
        } else if (event.equals("SHOOTING_STOPPED"/*javax.microedition.amms.control.camera.SnapshotControl.SHOOTING_STOPPED*/)) {
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
        } else if (event.equals("STORAGE_ERROR"/*javax.microedition.amms.control.camera.SnapshotControl.STORAGE_ERROR*/)) {
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

    public void commandAction(Command c, Displayable d) {
        if (c.getCommandType() == Command.SCREEN) {
            if (cz.kruch.track.TrackingMIDlet.jsr234) {
                (new Thread(this)).start();
            } else {
                run();
            }
        } else {
            shutdown(null);
        }
    }

    public void run() {
//#ifndef __RIM__
        if (cz.kruch.track.TrackingMIDlet.jsr234) {
            capture234();
        } else {
//#endif
            capture135();
//#ifndef __RIM__
        }
//#endif        
    }

    private void onClose() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("on close");
//#endif

        /* sound player members */
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                // ignore
            }
            in = null; // gc hint
        }

        /* common members */
        if (control instanceof VolumeControl) {
            ((VolumeControl) control).setLevel(level);
        }
        control = null; // gc hint

        /* common actions */
        if (next != null) {
            Desktop.display.setCurrent(next);
        }
        if (callback != null) {
            callback.invoke(callbackResult, callbackException, this);
        }
    }

    private void photo() throws Throwable {
        try {
            // create player
            player = Manager.createPlayer(Config.captureLocator);
            player.realize();
            player.prefetch(); // workaround for some S60 3rd, harmless(?) to others

            // get video control
            control = player.getControl("VideoControl");
            if (control == null) {
                throw new MediaException("Capture not supported");
            }
//#ifndef __RIM__
            // advanced settings (first time only)
            if (cz.kruch.track.TrackingMIDlet.jsr234) {

                // set camera resolution
                final javax.microedition.amms.control.camera.CameraControl cameraCtrl = (javax.microedition.amms.control.camera.CameraControl) player.getControl("javax.microedition.amms.control.camera.CameraControl");
                cameraCtrl.setStillResolution(Config.snapshotFormatIdx);

                // adjust focus
                final javax.microedition.amms.control.camera.FocusControl focusCtrl = (javax.microedition.amms.control.camera.FocusControl) player.getControl("javax.microedition.amms.control.camera.FocusControl");
                if (focusCtrl != null) {
                    if (focusCtrl.isAutoFocusSupported()) {
                        focusCtrl.setFocus(javax.microedition.amms.control.camera.FocusControl.AUTO);
                    } else {
                        focusCtrl.setFocus(Integer.MAX_VALUE);
                    }
                }
            }
//#endif
            // create form
            final Form form = new Form(null/*Resources.getString(Resources.NAV_TITLE_CAMERA)*/);
            form.addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Command.BACK, 1));
            form.addCommand(new Command(Resources.getString(Resources.NAV_CMD_TAKE), Command.SCREEN, 1));
            form.setCommandListener(this);

            // show camera window
            Desktop.display.setCurrent(form);

            // create view finder item
            final Item item = (Item) ((VideoControl) control).initDisplayMode(VideoControl.USE_GUI_PRIMITIVE, null);
            item.setLayout(Item.LAYOUT_TOP | Item.LAYOUT_LEFT);
            form.append(item);
            try {
                ((VideoControl) control).setDisplayLocation(0, 0);
                ((VideoControl) control).setDisplaySize(form.getWidth(), form.getHeight() - 2);
            } catch (MediaException mex) {
                // ignore
            }
            ((VideoControl) control).setVisible(true);

/*
            // create viewfinder
            view = new VideoCanvas();
            view.addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Command.BACK, 1));
            view.addCommand(new Command(Resources.getString(Resources.NAV_CMD_TAKE), Command.SCREEN, 1));
            view.setCommandListener(this);

            // create view finder item
            ((VideoControl) control).initDisplayMode(VideoControl.USE_DIRECT_VIDEO, view);
            try {
                ((VideoControl) control).setDisplayFullScreen(true);
            } catch (MediaException mex) {
                // ignore
            }
            ((VideoControl) control).setVisible(true);
*/

            // start camera
            player.start();

        } catch (Throwable t) {

//#ifdef __LOG__
            if (log.isEnabled()) log.error("camera failed: " + t);
            t.printStackTrace();
//#endif

            // cleanup
            shutdown(null);

            // bail out
            throw t;
        }
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

            // silence
            shutdown(player);

            return false;
        }
    }

    private void shutdown(final Player player) {
        // close player
        if (player != null) {
            player.close();
        }

        // gc hints
        control = null;

        // restore UI
        if (next != null) {
            Desktop.display.setCurrent(next);
        }
    }

    private void capture135() {
        /*byte[] result = null;*/
        String result = null;
        Throwable throwable = null;

        try {
            // fix the format
            String format = Config.snapshotFormat.trim();
            if ("".equals(format)) {
                format = null;
            }

            // prepare for Armageddon
            System.gc();

            // take the snapshot
            /*result = ((VideoControl) video).getSnapshot(format);*/
            result = saveImage(((VideoControl) control).getSnapshot(format));

        } catch (Throwable t) {

            // remember
            throwable = t;

        } finally {

            // shut camera
            shutdown(null);
        }

        // report result
        callback.invoke(result, throwable, this);
    }
//#ifndef __RIM__
    private void capture234() {
        try {

            // create images folder
            final String path = createImagesFolder(true);
            callbackResult = path.substring(path.indexOf(FOLDER_PREFIX));

            // set snapshot attributes
            javax.microedition.amms.control.camera.SnapshotControl snapshotCtrl = (javax.microedition.amms.control.camera.SnapshotControl) player.getControl("javax.microedition.amms.control.camera.SnapshotControl");
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
            shutdown(null);
        }
    }
//#endif
    private String createImagesFolder(final boolean pathOnly) throws IOException {
        String result = null;

        // create folder url
        final StringBuffer sb = new StringBuffer(32);
        sb.append(Config.getFolderURL(Config.FOLDER_WPTS));
        sb.append(FOLDER_PREFIX).append(cz.kruch.track.location.GpxTracklog.dateToFileDate(timestamp));
        sb.append('/');
        final String url = sb.toString();

        // create it if it does not exist
        File file = null;
        try {
            file = File.open(url, Connector.READ_WRITE);
            if (!file.exists()) {
                file.mkdir();
            }
            if (pathOnly) {
                result = url.substring(7 /* "file://".length() */);
            } else {
                result = url;
            }
        } finally {
            try {
                file.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
        }

        return result;
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

    private String saveImage(final byte[] raw) throws IOException {
        File file = null;
        OutputStream output = null;

        try {
            // create folder
            final String dir = createImagesFolder(false);

            // image filename
            final StringBuffer sb = new StringBuffer(dir);
            sb.append(PIC_PREFIX).append(++imgNum).append(PIC_SUFFIX);
            final String url = sb.toString();
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("save image data to " + url);
//#endif

            // save picture
            file = File.open(url, Connector.READ_WRITE);
            if (!file.exists()) {
                file.create();
            } 
            output = file.openOutputStream();
            output.write(raw);

            // return relative path
            return url.substring(url.indexOf(FOLDER_PREFIX));

        } finally {

            // cleanup
            try {
                output.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
            try {
                file.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
        }
    }

    public static int[] getThumbnail(byte[] jpg) {
        final int N = jpg.length;
        int offset = 0;

        // JPEG check
        final byte header0 = (byte) (jpg[offset++] & 0xFF);
        final byte header1 = (byte) (jpg[offset++] & 0xFF);
        if ((header0 & 0xFF) == 0xFF && (header1 & 0xFF) == 0xD8) {
            do { // look for APP1

                // marker
                final byte marker0 = (byte)(jpg[offset++] & 0xFF);
                final byte marker1 = (byte)(jpg[offset++] & 0xFF);

                // segment size [high-byte] [low-byte], includes size bytes
                final int length = (((byte)(jpg[offset++] & 0xFF) << 8) & 0xFF00) | ((byte)(jpg[offset++] & 0xFF) & 0xFF) - 2;

                // APP1?
                if ((marker0 & 0xFF) == 0xFF && (marker1 & 0xFF) == 0xE1) {

                    // find SOI
                    int skipped = goTo(jpg, offset, (byte)0xD8);
                    if (skipped == -1) {
                        return null;
                    }
                    final int start = offset + skipped - 2;
                    offset += skipped;

                    // find EOI
                    skipped = goTo(jpg, offset, (byte)0xD9);
                    if (skipped == -1) {
                        return null;
                    }
                    final int end = offset + skipped;

                    // result
                    return new int[]{ start, end };

                } else {

                    // skip segment
                    offset += length;

                }

            } while (offset < N);
        }

        return null;
    }

    private static int goTo(byte[] jpg, final int offset, final byte marker) {
        final int length = jpg.length;
        int position = offset;

        while (position < length) {
            int b = jpg[position++] & 0xFF;
            if ((byte) (b & 0xFF) == BYTE_FF) {
                b = jpg[position++] & 0xFF;
                if (marker == (byte) (b & 0xFF)) {
                    break;
                }
            }
        }

        if (position >= length) {
            return -1;
        }

        return position - offset;
    }
}
