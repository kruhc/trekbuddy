// @LICENSE@

package cz.kruch.track.fun;

import cz.kruch.track.configuration.Config;

import javax.microedition.media.MediaException;
import javax.microedition.media.Control;
import javax.microedition.media.control.VideoControl;
import javax.microedition.io.Connector;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Item;
import java.util.Vector;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;

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

    void getResolutions(final Vector v) {
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

	void createFinder(final Form form) throws MediaException {
		createFinder(form, (VideoControl) control);
	}

	boolean playSound(final String url) {
		return sound(url);
	}

    public void run() {
        String result = null;
        Throwable throwable = null;

        try {

            // take it
            result = takePicture(control);
            
        } catch (Throwable t) {

            // remember
            throwable = t;

        }

        // shut camera
        shutdown();

        // report result
        finished(result, throwable);
    }

    static String takePicture(final Control control) throws MediaException, IOException {
        // fix the format
        String format = Config.snapshotFormat.trim();
        if ("".equals(format)) {
            format = null;
        }

        // prepare for Armageddon
//#ifndef __RIM__
        System.gc(); // unconditional!!!
//#endif

        // take the snapshot
        return saveImage(((VideoControl) control).getSnapshot(format));
    }

    static String saveImage(final byte[] raw) throws IOException {
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

    static boolean sound(final String url) {
        InputStream in = null;
        try {
            return (new Playback(in = Connector.openInputStream(url), url)).sound();
        } catch (Exception e) {
            try {
                in.close();
            } catch (Exception exc) { // NPE or IOE
                // ignore
            }
        }
        return false;
    }
}
