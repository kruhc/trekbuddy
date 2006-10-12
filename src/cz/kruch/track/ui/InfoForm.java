// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.TrackingMIDlet;

import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;

import api.location.LocationException;

import java.util.TimeZone;

public final class InfoForm extends Form implements CommandListener {

    public InfoForm() {
        super("Info");
    }

    public void show(LocationException le, Object ps) {
        // gc (for memory info to be correct)
        System.gc();

        append(new StringItem("Memory", Long.toString(Runtime.getRuntime().totalMemory()) + "/" + Long.toString(Runtime.getRuntime().freeMemory())));
        append(new StringItem("AppFlags", TrackingMIDlet.getFlags()));
        append(new StringItem("Platform", TrackingMIDlet.getPlatform()));
        append(new StringItem("TimeZone", TimeZone.getDefault().getID() + "; " + TimeZone.getDefault().useDaylightTime() + "; " + TimeZone.getDefault().getRawOffset()));
        append(new StringItem("Jsr75/Fc", "resetable? " + Integer.toString(cz.kruch.track.maps.Map.fileInputStreamResetable)));
        append(new StringItem("ProviderError", le == null ? "" : le.toString()));
        append(new StringItem("ProviderStatus", ps == null ? "" : ps.toString()));
        append(new StringItem("SnapshotEncodings", System.getProperty("video.snapshot.encodings")));
        addCommand(new Command("Close", Command.BACK, 1));
        setCommandListener(this);

        // show
        Desktop.display.setCurrent(this);
    }

    public void commandAction(Command command, Displayable displayable) {
        Desktop.display.setCurrent(Desktop.screen);
    }
}
