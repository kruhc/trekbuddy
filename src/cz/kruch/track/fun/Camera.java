// @LICENSE@

package cz.kruch.track.fun;

import cz.kruch.track.event.Callback;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.ui.Desktop;
import cz.kruch.track.Resources;

//#ifndef __ANDROID__
import javax.microedition.media.Player;
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Control;
//#endif
import javax.microedition.io.Connector;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Image;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.util.Vector;

import api.file.File;

/**
 * Multimeda helper for taking pictures and playing sounds.
 *
 * @author kruhc@seznam.cz
 */
public abstract class Camera implements
//#ifndef __ANDROID__
        CommandListener,
//#endif
        Runnable {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Camera");
//#endif

    protected static final String PIC_PREFIX    = "pic-";
    protected static final String PIC_SUFFIX    = ".jpg";
    protected static final String FOLDER_PREFIX = "images-";

    public static final String TYPE_JSR234 = "JSR234";
    public static final String TYPE_JSR135 = "JSR135";

    // thumbnail byte marker
    private static final byte BYTE_FF = (byte) 0xFF;

    // supported still resolutions
    private static String[] resolutions;

    // hack
    private static boolean jsr234fixed;

    // image counter
    protected static int imgNum;
    protected static long timestamp;

//#ifndef __ANDROID__
    // common members
    protected Player player;
    protected Control control;
//#endif

    // video capture members
    private Displayable next;
    private Callback callback;

    // camera type
    public static String type;

//#ifndef __ANDROID__
    abstract void getResolutions(final Vector v);
    abstract void createFinder(final Form form) throws MediaException;
//#endif
    abstract boolean playSound(final String url);

    public static boolean play(final String url) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("play " + url);
//#endif
        if (Config.dataDirExists) {
            try {
                return createPlayback().playSound(url);
            } catch (Exception e) {
                // ignore
            }
        }
        return false;
    }

    public static void show(final Displayable next, final Callback callback,
                            final long timestamp) throws Throwable {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("capture locator: " + Config.captureLocator);
//#endif
//#ifndef __ANDROID__
        createRecorder(next, callback, timestamp).open();
//#endif
    }

    public static String[] getStillResolutions() {
        if (resolutions == null) {
            final Vector v = new Vector(8);
//#ifndef __ANDROID__
            createRecorder(null, null, -1).getResolutions(v);
//#endif            
            resolutions = new String[v.size()];
            v.copyInto(resolutions);
        }

        return resolutions;
    }

//#ifndef __ANDROID__

    public void commandAction(Command c, Displayable d) {
        if (c.getCommandType() == Command.SCREEN) {
            Desktop.getDiskWorker().enqueue(this);
        } else {
            shutdown();
        }
    }

    private static void fixJsr234() {
        // run once only
        if (jsr234fixed) {
            return;
        }
        jsr234fixed = true;

        // detect broken amms
        Player player = null;
        try {
            player = Manager.createPlayer(Config.captureLocator);
            player.realize();
            if (player.getControl("javax.microedition.amms.control.camera.CameraControl") == null
                    || player.getControl("javax.microedition.amms.control.camera.SnapshotControl") == null) {
                cz.kruch.track.TrackingMIDlet.jsr234 = false;
            }
        } catch (Exception e) {
            // ignore
        } finally {
            try {
                player.close();
            } catch (Exception e) { // NPE or ME
                // ignore
            }
        }
    }

//#endif /* !__ANDROID__ */    

    private static Camera createPlayback() throws Exception {
        Camera instance;
//#ifdef __ANDROID__
        instance = (Camera) Class.forName("cz.kruch.track.fun.AndroidCamera").newInstance();
//#else
        instance = (Camera) Class.forName("cz.kruch.track.fun.Jsr135Camera").newInstance();
//#endif
        return instance;
    }

//#ifndef __ANDROID__

    private static Camera createRecorder(final Displayable next,
                                         final Callback callback,
                                         final long timestamp) {
        Camera delegate;
        try {
            fixJsr234();
            if (cz.kruch.track.TrackingMIDlet.jsr234) {
                delegate = (Camera) Class.forName("cz.kruch.track.fun.Jsr234Camera").newInstance();
                type = TYPE_JSR234;
            } else {
                delegate = (Camera) Class.forName("cz.kruch.track.fun.Jsr135Camera").newInstance();
                type = TYPE_JSR135;
            }
            delegate.next = next;
            delegate.callback = callback;
            delegate.timestamp = timestamp;
        } catch (Exception e) {
//#ifdef __LOG__
            e.printStackTrace();
//#endif
            throw new IllegalStateException(e.toString());
        }
        return delegate;
    }

    private void open() throws Throwable {
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

            // create form
            final Form form = new Form(null/*Resources.getString(Resources.NAV_TITLE_CAMERA)*/);
            form.addCommand(new Command(Resources.getString(Resources.NAV_CMD_TAKE), Command.SCREEN, 1));
            form.addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Desktop.CANCEL_CMD_TYPE, 1));
            form.setCommandListener(this);

            // show camera window
            Desktop.display.setCurrent(form);

            // create view finder item
            createFinder(form);

            // start camera
            player.start();

        } catch (Throwable t) {

//#ifdef __LOG__
            if (log.isEnabled()) log.error("camera failed: " + t);
            t.printStackTrace();
//#endif

            // cleanup
            shutdown();

            // bail out
            throw t;
        }
    }

    void finished(final Object result, final Throwable throwable) {
        // notify
        callback.invoke(result, throwable, this);
    }

    void shutdown() {
        // close player
        if (player != null) {
            player.close();
            player = null;
        }

        // gc hints
        control = null;

        // restore UI
        if (next != null) {
            Desktop.display.setCurrent(next);
        }
    }

    static String createImagesFolder(final boolean pathOnly) throws IOException {
        // result
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
                result = file.getPath() + file.getName(); //url.substring(7 /* "file://".length() */);
            } else {
                result = file.getURL(); //url
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


//#endif /* !__ANDROID__ */

    public static Object getThumbnail(String url) {
        InputStream is = null;
        Object result = null;
        try {
            is = new api.io.BufferedInputStream(Connector.openInputStream(url), 4096);
            result = Image.createImage(new ByteArrayInputStream(getThumbnail(is)));
        } catch (Throwable t) { // could be OOM?
            result = t;
        } finally {
            try {
                is.close();
            } catch (Exception e) { // NPE or IOE or other
                // ignore
            }
        }

        return result;
    }

    public static byte[] getThumbnail(InputStream is) throws IOException {
        int offset = 0;
        ByteArrayOutputStream raw = new ByteArrayOutputStream(4096);

        // JPEG check
        final byte header0 = (byte) (pop(is, offset++) & 0xFF);
        final byte header1 = (byte) (pop(is, offset++) & 0xFF);
        if ((header0 & 0xFF) == 0xFF && (header1 & 0xFF) == 0xD8) {
            do { // look for APP1

                // marker
                final byte marker0 = (byte)(pop(is, offset++) & 0xFF);
                final byte marker1 = (byte)(pop(is, offset++) & 0xFF);

                // segment size [high-byte] [low-byte], includes size bytes
                final int length = (((byte)(pop(is, offset++) & 0xFF) << 8) & 0xFF00) | ((byte)(pop(is, offset++) & 0xFF) & 0xFF) - 2;

                // APP1?
                if ((marker0 & 0xFF) == 0xFF && (marker1 & 0xFF) == 0xE1) {

                    // find SOI
                    int skipped = skipTo(is, null, (byte)0xD8);
                    if (skipped == -1) {
                        return null;
                    }

                    // start saving thumb data
                    raw.write((byte)0xFF);
                    raw.write((byte)0xD8);

                    // find EOI
                    skipped = skipTo(is, raw, (byte)0xD9);
                    if (skipped == -1) {
                        return null;
                    }

                    // result
                    return raw.toByteArray();

                } else {

                    // skip segment
                    offset += skip(is, length);

                }

            } while (true);
        }

        return null;
    }

    private static byte pop(InputStream is, int ignored) throws IOException {
        final int i = is.read();
        if (i != -1) {
            return (byte) i;
        }
        throw new EOFException(); // TODO this is hack, getThumbnail should check for -1
    }

    private static long skip(InputStream is, int length) throws IOException {
        return is.skip(length);
    }

    private static int skipTo(InputStream is, OutputStream os, final byte marker) throws IOException {
        int c = 0;

        while (true) {
            int b = pop(is, c++) & 0xFF;
            if (os != null) {
                os.write(b);
            }
            if ((byte) (b & 0xFF) == BYTE_FF) {
                b = pop(is, c++) & 0xFF;
                if (os != null) {
                    os.write(b);
                }
                if (marker == (byte) (b & 0xFF)) {
                    break;
                }
            }
        }

        return c;
    }
}
