/*
 * Copyright 2006-2007 Ales Pour <kruhc@seznam.cz>.
 * All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package cz.kruch.track.fun;

import cz.kruch.track.event.Callback;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.ui.Desktop;
import cz.kruch.track.Resources;

import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Item;
import javax.microedition.media.Player;
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.control.VideoControl;
import javax.microedition.media.control.VolumeControl;
import javax.microedition.io.Connector;
import java.io.InputStream;
import java.io.IOException;

/**
 * Helper for MMAPI. Used to capture snapshots, play sounds etc.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class Camera implements CommandListener, PlayerListener/*, Runnable*/ {

//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Desktop");
//#endif

    private static final byte BYTE_FF = (byte) 0xFF;

    // video capture members
    private Displayable next;
    private Callback callback;
    private Player player;
    private VideoControl video;

    // sound player members
    private InputStream in;
    private VolumeControl volume;
    private int level;

    public Camera(Displayable next, Callback callback) {
        this.next = next;
        this.callback = callback;
    }

    private Camera(InputStream in, VolumeControl volume) {
        this.in = in;
        if (volume != null) {
            this.volume = volume;
            this.level = volume.getLevel();
            volume.setLevel(100);
        }
    }

    public void show() throws Throwable {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("capture locator: " + Config.captureLocator);
//#endif

        try {
            // create player
            player = Manager.createPlayer(Config.captureLocator);
            player.realize();
            player.prefetch(); // workaround for some S60 3rd, harmless(?) to others

            // get video control
            video = (VideoControl) player.getControl("VideoControl");
            if (video == null) {
                throw new MediaException("Capture not supported");
            }

            // create form
            Form form = new Form(Resources.getString(Resources.NAV_TITLE_CAMERA));
            form.addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Command.BACK, 1));
            form.addCommand(new Command(Resources.getString(Resources.NAV_CMD_TAKE), Command.SCREEN, 1));
            form.setCommandListener(this);

            // create view finder item
            Item item = (Item) video.initDisplayMode(VideoControl.USE_GUI_PRIMITIVE, null);
            item.setLayout(Item.LAYOUT_CENTER);
            form.append(item);

            // show camera window
            Desktop.display.setCurrent(form);

            // start camera
            player.start();

        } catch (Throwable t) {

            // release video resources
            destroy();

            // bail out
            throw t;
        }
    }

    public static boolean play(String name) {
        Player p = null;
        InputStream in = null;
        try {
            p = Manager.createPlayer(in = Connector.openInputStream(Config.getFolderURL(Config.FOLDER_SOUNDS) + name), "audio/amr");
            p.realize();
            p.prefetch();
            p.addPlayerListener(new Camera(in, (VolumeControl) p.getControl("VolumeControl")));
            p.start();
        } catch (Throwable t) {
//#ifdef __LOG__
            System.out.println("play " + name + " failed: " + t);
//#endif
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            if (p != null) {
                p.close();
            }
            return false;
        }
        return true;
    }

    public void playerUpdate(Player player, String event, Object closure) {
        if (event.equals(PlayerListener.END_OF_MEDIA) || event.equals(PlayerListener.ERROR)) {
            if (volume != null) {
                volume.setLevel(level);
                volume = null; // gc hint
            }
            player.close();
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
                in = null; // gc hint
            }
        }
    }

    private void destroy() {
        // gc hint
        video = null;

        // close player
        if (player != null) {
            player.close();
            player = null;
        }

        // close this screen
        Desktop.display.setCurrent(next);
    }

    public void commandAction(Command c, Displayable d) {
        if (c.getCommandType() == Command.SCREEN) {
            /* All samples I saw gets snapshot directly from callback too... */
//            (new Thread(this)).start();
            capture();
        } else {
            destroy();
        }
    }

    private void capture() {
        byte[] result = null;
        Throwable throwable = null;

        try {
            // fix the format
            String format = Config.snapshotFormat;
            if ("".equals(format)) {
                format = null;
            }

            // prepare for Armageddon
            System.gc();

            // take the snapshot
            result = video.getSnapshot(format);

        } catch (Throwable t) {

            // remember
            throwable = t;

        } finally {

            // destroy
            destroy();
        }

        // report result
        callback.invoke(result, throwable, this);
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
