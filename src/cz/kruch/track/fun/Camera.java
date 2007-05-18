// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.fun;

import cz.kruch.track.event.Callback;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.ui.Desktop;

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
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;

public final class Camera extends Form implements CommandListener, Runnable {

    private static final byte BYTE_FF = (byte)0xFF;
    private static final String MSG_UNEXPECTED_END_OF_STREAM = "Unexpected end of stream";

//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Desktop");
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
        if (log.isEnabled()) log.debug("capture locator: " + Config.captureLocator);
//#endif

        try {
            // get video control
            player = Manager.createPlayer(Config.captureLocator);
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
            String format = Config.captureFormat;
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
            callback.invoke(raw, null, this);

        } catch (Throwable t) {

            // close the player
            destroy();

            // report snapshot taking problem
            callback.invoke(null, t, this);
        }
    }

    public static byte[] getThumbnail(byte[] image) throws Exception {
        byte[] result = image;
        InputStream in = new ByteArrayInputStream(image);

        // JPEG check
        isJpeg(in);

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
            } else if (marker == (byte)0xE1) {
                int l = length;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                l -= goTo(in, (byte)0xD8, null);
                out.write(BYTE_FF);
                out.write((byte)0xD8);
                l -= goTo(in, (byte)0xD9, out);
                out.write(BYTE_FF);
                out.write((byte)0xD9);
                out.flush();
                // assertion
                if (l != 0) {
                    throw new Exception("Wrong thumbnail position");
                }
                // result
                result = out.toByteArray();
            } else {
                skipSegment(in, length);
            }
        } while (true);

        in.close();

        return result;
    }

    private static boolean isJpeg(InputStream in) throws IOException {
        byte[] header = new byte[2];
        header[0] = readByte(in);
        header[1] = readByte(in);

        return (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8;
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
        int b = in.read();
        if (b == -1) {
            throw new IOException(MSG_UNEXPECTED_END_OF_STREAM);
        }

        return (byte)(b & 0xFF);
    }

    private static int goTo(InputStream in, byte marker, ByteArrayOutputStream out) throws IOException {
//        System.out.println("goto " + Integer.toHexString(0xFF & marker) + "; record? " + (out != null));
        int count = 0;
        while (true) {
            int b = readByte(in);
            count++;
            if ((byte)(b & 0xFF) == BYTE_FF) {
                b = readByte(in);
                count++;
                if (marker == (byte)(b & 0xFF)) {
//                    System.out.println("mark " + Integer.toHexString(0xFF & marker) + " found");
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
