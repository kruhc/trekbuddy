// @LICENSE@

package cz.kruch.track.fun;

import cz.kruch.track.configuration.Config;

import javax.microedition.media.MediaException;
import javax.microedition.media.control.VideoControl;
import javax.microedition.io.Connector;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Item;
import java.util.Vector;
import java.io.IOException;
import java.io.OutputStream;

import api.file.File;

/**
 * JSR-135 camera.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class Jsr135Camera extends Camera {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Jsr135Camera");
//#endif

    public Jsr135Camera() {
    }

    public void getResolutions(Vector v) {
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
    }

    public void beforeShoot() throws MediaException {
    }

    public void run() {
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

        }

        // shut camera
        shutdown();

        // report result
        finished(result, throwable);
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

    public void createFinder(final Form form) throws MediaException {
        createFinder(form, (VideoControl) control);
    }

    static void createFinder(final Form form, final VideoControl control) throws MediaException {
        final Item item = (Item) control.initDisplayMode(VideoControl.USE_GUI_PRIMITIVE, null);
        item.setLayout(Item.LAYOUT_TOP | Item.LAYOUT_LEFT);
        form.append(item);
        try {
            control.setDisplayLocation(0, 0);
            control.setDisplaySize(form.getWidth(), form.getHeight() - 2);
        } catch (MediaException mex) {
            // ignore
        }
        control.setVisible(true);

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
    }
}
