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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;

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
    private static final String MSG_UNEXPECTED_END_OF_STREAM = "Unexpected end of stream";

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
            Form form = new Form("Camera");
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

    public static boolean play(String file) {
        Player p = null;
        InputStream in = null;
        try {
            p = Manager.createPlayer(in = Connector.openInputStream(Config.getFolderSounds() + file), "audio/amr");
            p.realize();
            p.addPlayerListener(new Camera(in, (VolumeControl) p.getControl("VolumeControl")));
            p.start();
        } catch (Throwable t) {
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

    public void capture() {
        byte[] result = null;
        Throwable throwable = null;

        try {
            // fix the format
            String format = Config.captureFormat;
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

    public static byte[] getThumbnail(byte[] image) throws Exception {
        // stream
        InputStream in = new ByteArrayInputStream(image);

        // JPEG check
        if (!isJpeg(in)) {
            return null;
        }

        // find EXIF
        do {
            // segment identifier
            int identifier = in.read();
            if (identifier == -1) {
                break;
            }
            if (((byte)(identifier & 0xFF)) != BYTE_FF) {
                throw new IOException("Segment start not found");
            }

            // segment marker
            byte marker = readByte(in);

            // segment size [high-byte] [low-byte]
            byte[] lengthBytes = new byte[2];
            lengthBytes[0] = readByte(in);
            lengthBytes[1] = readByte(in);
            int length = ((lengthBytes[0] << 8) & 0xFF00) | (lengthBytes[1] & 0xFF);

            // segment length includes size bytes
            length -= 2;
            if (length < 0) {
                throw new IOException("Negative segment size");
            }

            if (marker == (byte)0xDA) {
                goTo(in, (byte)0xD9, null);
            } else if (marker == (byte)0xE1) { // thumbnail segment
                int l = length;
                ByteArrayOutputStream out = new ByteArrayOutputStream(length);
                l -= goTo(in, (byte)0xD8, null);
                out.write(BYTE_FF);
                out.write((byte)0xD8);
                l -= goTo(in, (byte)0xD9, out);
                out.write(BYTE_FF);
                out.write((byte)0xD9);
                out.flush();
                if (l != 0) {
                    throw new IllegalStateException("Wrong thumbnail position");
                }
                return out.toByteArray();
            } else {
                skipSegment(in, length);
            }
        } while (true);

        return null;
    }

    private static boolean isJpeg(InputStream in) throws IOException {
        final byte header0 = readByte(in);
        final byte header1 = readByte(in);

        return (header0 & 0xFF) == 0xFF && (header1 & 0xFF) == 0xD8;
    }

    private static void skipSegment(InputStream in, long length) throws IOException {
        while (length > 0) {
            long l = in.skip(length);
            if (l == -1) {
                throw new IOException(MSG_UNEXPECTED_END_OF_STREAM);
            }
            length -= l;
        }
    }

    private static byte readByte(InputStream in) throws IOException {
        final int b = in.read();
        if (b == -1) {
            throw new IOException(MSG_UNEXPECTED_END_OF_STREAM);
        }

        return (byte)(b & 0xFF);
    }

    private static int goTo(InputStream in, byte marker, ByteArrayOutputStream out) throws IOException {
        int count = 0;
        while (true) {
            int b = readByte(in);
            count++;
            if ((byte)(b & 0xFF) == BYTE_FF) {
                b = readByte(in);
                count++;
                if (marker == (byte)(b & 0xFF)) {
                    break;
                } else {
                    if (out != null) {
                        out.write(BYTE_FF);
                        out.write(b);
                    }
                }
            } else {
                if (out != null) {
                    out.write(b);
                }
            }
        }

        return count;
    }
}
