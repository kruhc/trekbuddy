// @LICENSE@

package cz.kruch.track.fun;

import cz.kruch.track.event.Callback;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.ui.Desktop;

import javax.microedition.io.Connector;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
import javax.microedition.media.MediaException; // for android use impl from cn1-compat
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.util.Vector;

import api.file.File;
import api.location.QualifiedCoordinates;

/**
 * Camera helper.
 *
 * @author kruhc@seznam.cz
 */
public abstract class Camera {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Camera");
//#endif
    public static StringBuffer state;

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
    protected static long filestamp;

    // video capture members
    private Displayable next;
    protected Callback callback;
    protected QualifiedCoordinates gpsCoords;
    protected long gpsTimestamp;

    // camera type
    public static String type;

    // contract
    abstract void getResolutions(final Vector v);
    abstract void open() throws MediaException;

    protected Camera() {
        state = new StringBuffer(128);
    }

    public static String[] getStillResolutions() {
        if (resolutions == null) {
            final Vector v = new Vector(8);
//#if !__CN1__ && !__BACKPORT__
            createRecorder(null, null, -1, null, -1).getResolutions(v);
//#endif
            resolutions = new String[v.size()];
            v.copyInto(resolutions);
        }

        return resolutions;
    }

    public static void show(final Displayable next, final Callback callback, final long filestamp,
                            final QualifiedCoordinates qc, final long timestamp) throws Throwable {
//#if !__CN1__ && !__BACKPORT__
        createRecorder(next, callback, filestamp, qc, timestamp).open();
//#endif
    }

//#if !__ANDROID__ && !__CN1__

    private static void fixJsr234() {
        // run once only
        if (jsr234fixed) {
            return;
        }
        jsr234fixed = true;

        // detect broken amms
        javax.microedition.media.Player player = null;
        try {
            player = javax.microedition.media.Manager.createPlayer(Config.captureLocator);
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

//#endif /* !__ANDROID__ && !__CN1__ */

    private static Camera createRecorder(final Displayable next,
                                         final Callback callback,
                                         final long filestamp,
                                         final QualifiedCoordinates coords,
                                         final long timestamp) {
        Camera camera;
        try {
//#if !__ANDROID__ && !__CN1__
            fixJsr234();
            if (cz.kruch.track.TrackingMIDlet.jsr234) {
                camera = (Camera) Class.forName("cz.kruch.track.fun.Jsr234Camera").newInstance();
                type = TYPE_JSR234;
            } else {
                camera = (Camera) Class.forName("cz.kruch.track.fun.Jsr135Camera").newInstance();
                type = TYPE_JSR135;
            }
//#else
            camera = (Camera) Class.forName("cz.kruch.track.fun.AndroidCamera").newInstance();
            type = TYPE_JSR234; // yes, this is a trick
//#endif
        } catch (Exception e) {
//#ifdef __LOG__
            e.printStackTrace();
//#endif
            throw new IllegalStateException(e.toString());
        }

        // set camera params
        camera.next = next;
        camera.callback = callback;
        camera.gpsCoords = coords;
        camera.gpsTimestamp = timestamp;

        // TODO make instance?
        Camera.filestamp = filestamp;

        return camera;
    }

    /* assertion: should be called from bg */
    final void finished(final Object result, final Throwable throwable) {
        // notify
        state.append("x-finished -> ").append(result).append(';').append(throwable);
        callback.invoke(result, throwable, this);
    }

    void shutdown() {
        // restore UI
        state.append("x-shutdown -> ");
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
        sb.append(FOLDER_PREFIX).append(cz.kruch.track.location.GpxTracklog.dateToFileDate(filestamp));
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
                result = file.getPath().concat(file.getName()); // url.substring(7 /* "file://".length() */);
            } else {
                result = file.getURL(); // url
            }
            if (!result.endsWith("/")) {
                result = result.concat("/");
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

    static String saveImage(final byte[] raw) throws IOException {
        File file = null;
        OutputStream output = null;

        try {
            // create folder
            final String dir = createImagesFolder(false);

            // image filename
            final StringBuffer sb = new StringBuffer(64);
            sb.append(dir).append(PIC_PREFIX).append(++imgNum).append(PIC_SUFFIX);
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
