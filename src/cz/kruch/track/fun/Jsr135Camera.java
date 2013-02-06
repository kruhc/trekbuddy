// @LICENSE@

package cz.kruch.track.fun;

//#ifndef __CN1__

import cz.kruch.track.configuration.Config;
import cz.kruch.track.Resources;
import cz.kruch.track.ui.Desktop;

import javax.microedition.media.MediaException;
import javax.microedition.media.Control;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.control.VideoControl;
import javax.microedition.io.Connector;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import java.util.Vector;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;

import api.file.File;

/**
 * JSR-135 camera.
 *
 * @author kruhc@seznam.cz
 */
class Jsr135Camera extends Camera implements Runnable, CommandListener, PlayerListener {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Jsr135Camera");
//#endif

    // common members
    protected Player player;
    protected Control control;

    Jsr135Camera() {
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

    void open() throws MediaException {
        try {
            // create player
            player = Manager.createPlayer(Config.captureLocator);
            addPlayerListener(player);
            player.realize();
            player.prefetch(); // workaround for some S60 3rd, harmless(?) to others
            state.append("x-prefetched -> ");

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
            createFinder(form, (VideoControl) control);

            // start camera
            player.start();
            state.append("x-started -> ");

        } catch (Throwable t) {
//#ifdef __LOG__
            if (log.isEnabled()) log.error("camera failed: " + t);
            t.printStackTrace();
//#endif
            state.append("open error: ").append(t.toString()).append(" -> ");

            // cleanup
            shutdown();

            // bail out
            throw new MediaException(t.toString());
        }
    }

    void addPlayerListener(Player player) {
    }

    public void playerUpdate(Player player, String event, Object eventData) {
    }

    void shutdown() {
        // close player
        if (player != null) {
            player.close();
            player = null;
        }

        // gc hints
        control = null;

        // parent
        super.shutdown();
    }

    public void run() {
        
        // exported values
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

    public void commandAction(Command c, Displayable d) {
        if (c.getCommandType() == Command.SCREEN) {
            Desktop.getDiskWorker().enqueue(this);
        } else {
            state.append("x-cancel -> ");
            shutdown();
        }
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

//#endif
