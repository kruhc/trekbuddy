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
import api.file.File;

/**
 * Info form.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class InfoForm extends Form implements CommandListener {

    private Throwable le, te;
    private Object ps;
    private Map map;

    InfoForm() {
        super(Resources.prefixed(Resources.getString(Resources.DESKTOP_CMD_INFO)));
    }

    public void show(Desktop desktop, Throwable le, Throwable te,
                     Object ps, Map map) {
        // members
        this.le = le;
        this.te = te;
        this.ps = ps;
        this.map = map;

        // items
        append(newItem(Resources.getString(Resources.INFO_ITEM_VENDOR), Resources.getString(Resources.INFO_ITEM_VENDOR_VALUE)));
        append(newItem(Resources.getString(Resources.INFO_ITEM_VERSION), cz.kruch.track.TrackingMIDlet.version));
        append(newItem(Resources.getString(Resources.INFO_ITEM_KEYS), ""));
        append(Resources.getString((short) (Resources.INFO_ITEM_KEYS_MS + desktop.mode)));
        addCommand(new Command(Resources.getString(Resources.INFO_CMD_DETAILS), Desktop.POSITIVE_CMD_TYPE, 1));
        addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Command.BACK, 1));
        setCommandListener(this);

        // show
        Desktop.display.setCurrent(this);
    }

    private void details(final Throwable le, final Throwable te, final Object ps, final Map map) {
        // gc - for memory info to be correct...
        System.gc();
        final long totalMemory = Runtime.getRuntime().totalMemory();
        final long freeMemory = Runtime.getRuntime().freeMemory();

        // items
        append(newItem("Platform", cz.kruch.track.TrackingMIDlet.getPlatform()));
        final StringBuffer sb = new StringBuffer(32);
        sb.append(totalMemory).append('/').append(freeMemory);
        append(newItem("Memory", sb.toString()));
        sb.delete(0, sb.length());
        if (File.fsType == File.FS_JSR75 || File.fsType == File.FS_SXG75)
            sb.append("75 ");
        if (cz.kruch.track.TrackingMIDlet.jsr82)
            sb.append("82 ");
        if (cz.kruch.track.TrackingMIDlet.jsr120)
            sb.append("120 ");
        if (cz.kruch.track.TrackingMIDlet.jsr120)
            sb.append("135 ");
        if (cz.kruch.track.TrackingMIDlet.jsr179)
            sb.append("179 ");
        append(newItem("ExtraJsr", sb.toString()));
        if (cz.kruch.track.TrackingMIDlet.getFlags() != null) {
            append(newItem("AppFlags", cz.kruch.track.TrackingMIDlet.getFlags()));
        }
        sb.delete(0, sb.length()).append(System.getProperty("microedition.locale")).append(' ').append(System.getProperty("microedition.encoding"));
        append(newItem("I18n", sb.toString()));
        sb.delete(0, sb.length()).append(File.fsType).append("; resetable? ").append(cz.kruch.track.maps.Map.fileInputStreamResetable);
        append(newItem("Fs", sb.toString()));
        sb.delete(0, sb.length()).append(cz.kruch.track.ui.nokia.DeviceControl.getName());
        append(newItem("DeviceCtrl", sb.toString()));
        sb.delete(0, sb.length()).append(cz.kruch.track.TrackingMIDlet.hasPorts()).append("; ").append(System.getProperty("microedition.commports"));
        append(newItem("Ports", sb.toString()));
        sb.delete(0, sb.length()).append(TimeZone.getDefault().getID()).append("; ").append(TimeZone.getDefault().useDaylightTime()).append("; ").append(TimeZone.getDefault().getRawOffset());
        append(newItem("TimeZone", sb.toString()));
        sb.delete(0, sb.length()).append("safe renderer? ").append(Config.S60renderer).append("; hasRepeatEvents? ").append(Desktop.hasRepeatEvents).append("; ").append(Desktop.screen.getWidth()).append('x').append(Desktop.screen.getHeight());
        append(newItem("Desktop", sb.toString()));
        if (map == null) {
            append(newItem("Map", ""));
        } else {
            sb.delete(0, sb.length()).append("datum: ").append(map.getDatum()).append("; projection: ").append(map.getProjection());
            append(newItem("Map", sb.toString()));
        }
        sb.delete(0, sb.length()).append((ps == null ? "" : ps.toString())).append("; stalls=").append(api.location.LocationProvider.stalls).append("; restarts=").append(api.location.LocationProvider.restarts).append("; syncs=").append(api.location.LocationProvider.syncs).append("; mismatches=").append(api.location.LocationProvider.mismatches).append("; checksums=").append(api.location.LocationProvider.checksums).append("; errors=").append(api.location.LocationProvider.errors).append("; pings=").append(api.location.LocationProvider.pings);
        append(new StringItem("ProviderStatus", sb.toString()));
        if (le != null) {
            append(new StringItem("ProviderError", le.toString()));
        }
        if (te != null) {
            append(new StringItem("TracklogError", te.toString()));
        }
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command.getCommandType() == Command.BACK) {
            // gc hint
            this.le = null;
            this.te = null;
            this.ps = null;
            this.map = null;
            // restore desktop UI
            Desktop.display.setCurrent(Desktop.screen);
        } else {
            // delete basic info
            deleteAll();
            // remove 'Details' command
            removeCommand(command);
            // show technical details
            details(le, te, ps, map);
        }
    }

    private static StringItem newItem(final String label, final String text) {
        final StringItem item = new StringItem(label, text);
        item.setLayout(Item.LAYOUT_2 | Item.LAYOUT_NEWLINE_AFTER);
        return item;
    }
}
