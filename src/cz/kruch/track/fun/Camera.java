// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.fun;

import cz.kruch.track.event.Callback;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.ui.Desktop;
//#ifdef __LOG__
import cz.kruch.track.util.Logger;
//#endif

import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Item;
import javax.microedition.media.Player;
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.control.VideoControl;
import javax.microedition.media.control.GUIControl;

public final class Camera extends Form
        implements CommandListener, Runnable {
//#ifdef __LOG__
    private static final Logger log = new Logger("Desktop");
//#endif

    private Displayable next;
    private Callback callback;
    private Player player = null;
    private VideoControl video = null;

    public Camera(Displayable next, Callback callback) {
        super("Picture");
        this.next = next;
        this.callback = callback;
        addCommand(new Command("Close", Command.BACK, 1));
        addCommand(new Command("Capture", Command.SCREEN, 1));
        setCommandListener(this);
    }

    public void show() throws Exception {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("capture locator: " + Config.getSafeInstance().getCaptureLocator());
//#endif

        try {
            // get video control
            player = Manager.createPlayer(Config.getSafeInstance().getCaptureLocator());
            player.realize();
            video = (VideoControl) player.getControl("VideoControl");
            if (video == null) {
                throw new MediaException("Capture not supported");
            }

            // create view finder item
            Item item = (Item) video.initDisplayMode(GUIControl.USE_GUI_PRIMITIVE, null);
            item.setLayout(Item.LAYOUT_CENTER);
            append(item);

            // show camera
            player.start();

        } catch (Exception e) {

            // release video resources
            destroy();

            // bail out
            throw e;
        }

        // show camera window
        Desktop.display.setCurrent(this);
    }

    private void destroy() {
        // release resources
        if (video != null) {
            video.setVisible(false);
            video = null;
        }
        if (player != null) {
            player.close();
            player = null;
        }

        // close this screen
        Desktop.display.setCurrent(next);
    }

    public void commandAction(Command c, Displayable d) {
        if (c.getCommandType() == Command.SCREEN) {
            (new Thread(this)).start();
        } else {
            destroy();
        }
    }

    public void run() {
        try {
            // fix the format
            String format = Config.getSafeInstance().getCaptureFormat();
            if ("".equals(format)) {
                format = null;
            }

            // prepare for Armageddon
            System.gc();

            // take the snapshot
            byte[] raw = video.getSnapshot(format);

            // close the player
            destroy();

            // report result
            callback.invoke(raw, null);

        } catch (Throwable t) {

            // close the player
            destroy();

            // report snapshot taking problem
            callback.invoke(null, t);
        }
    }
}
