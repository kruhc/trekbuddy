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

package cz.kruch.track.ui;

import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Item;
import java.util.TimeZone;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.maps.Map;
import cz.kruch.track.Resources;

/**
 * Info form.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class InfoForm extends Form implements CommandListener {

    public InfoForm() {
        super(cz.kruch.track.TrackingMIDlet.wm ? Resources.getString(Resources.DESKTOP_CMD_INFO) + " (TrekBuddy)" : Resources.getString(Resources.DESKTOP_CMD_INFO));
    }

    public void show(Desktop desktop, Throwable le, Object ps, Map map) {
        // gc (for memory info to be correct)
        System.gc();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();

        // items
        append(newItem("Platform", cz.kruch.track.TrackingMIDlet.getPlatform()));
        append(newItem("Memory", Long.toString(totalMemory) + "/" + Long.toString(freeMemory)));
        append(newItem("ExtraJsr", (api.file.File.fsType == api.file.File.FS_JSR75 || api.file.File.fsType == api.file.File.FS_SXG75 ? "75 " : "")
                                   + (cz.kruch.track.TrackingMIDlet.jsr82 ? "82 " : "")
                                   + (cz.kruch.track.TrackingMIDlet.jsr120 ? "120 " : "")
                                   + (cz.kruch.track.TrackingMIDlet.jsr135 ? "135 " : "")
                                   + (cz.kruch.track.TrackingMIDlet.jsr179 ? "179" : "")));
        if (cz.kruch.track.TrackingMIDlet.getFlags() != null) {
            append(newItem("AppFlags", cz.kruch.track.TrackingMIDlet.getFlags()));
        }
        append(newItem("I18n", System.getProperty("microedition.locale") + " " + System.getProperty("microedition.encoding")));
        append(newItem("Fs", "type? " + Integer.toString(api.file.File.fsType) + "; resetable? " + Integer.toString(cz.kruch.track.maps.Map.fileInputStreamResetable)));
        append(newItem("Ports", cz.kruch.track.TrackingMIDlet.hasPorts() + "; " + System.getProperty("microedition.commports")));
        append(newItem("TimeZone", TimeZone.getDefault().getID() + "; " + TimeZone.getDefault().useDaylightTime() + "; " + TimeZone.getDefault().getRawOffset()));
        append(newItem("Desktop", "S60 renderer? " + Config.S60renderer + "; hasRepeatEvents? " + Desktop.hasRepeatEvents + "; " + Desktop.screen.getWidth() + "x" + Desktop.screen.getHeight()));
        if (map == null) {
            append(newItem("Map", ""));
        } else {
            append(newItem("Map", "datum: " + map.getDatum() + " projection: " + map.getProjection()));
        }
        append(new StringItem("ProviderStatus", (ps == null ? "" : ps.toString()) + "; restarts=" + cz.kruch.track.location.StreamReadingLocationProvider.restarts + "; syncs=" + cz.kruch.track.location.StreamReadingLocationProvider.syncs + "; mismatches=" + cz.kruch.track.location.StreamReadingLocationProvider.mismatches + "; checksums=" + cz.kruch.track.location.StreamReadingLocationProvider.checksums));
        append(new StringItem("ProviderError", le == null ? "" : le.toString()));
/*
        if (cz.kruch.track.TrackingMIDlet.supportsVideoCapture()) {
            append(new StringItem("SnapshotEncodings", System.getProperty("video.snapshot.encodings")));
        }
        StringBuffer sb = new StringBuffer(64);
        sb.append("amr=");
        appendEncodings("audio/amr", sb);
        sb.append(";mp3(1)=");
        appendEncodings("audio/mpeg", sb);
        sb.append(";mp3(2)=");
        appendEncodings("audio/mp3", sb);
        append(new StringItem("Audio", sb.toString()));
*/
/*
        append(new StringItem("Diagnostics", Integer.toString(cz.kruch.track.TrackingMIDlet.pauses)));
*/
        addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Command.BACK, 1));
        setCommandListener(this);

        // show
        Desktop.display.setCurrent(this);
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command.getCommandType() == Command.BACK) {
            // restore desktop UI
            Desktop.display.setCurrent(Desktop.screen);
        }
    }

    private static StringItem newItem(String label, String text) {
        StringItem item = new StringItem(label, text);
        item.setLayout(Item.LAYOUT_NEWLINE_AFTER);
        return item;
    }

/*
    private static StringBuffer appendEncodings(String contentType, StringBuffer sb) {
        String[] protocolsXWav = Manager.getSupportedProtocols(contentType);
        for (int i = protocolsXWav.length; --i >= 0; )
            sb.append(protocolsXWav[i]).append(' ');
        return sb;
    }
*/
}
