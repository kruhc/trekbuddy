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

public final class InfoForm extends Form implements CommandListener {

    public InfoForm() {
        super("Info");
    }

    public void show(LocationException le) {
        // gc (for memory info to be correct)
        System.gc();

        append(new StringItem("Memory", Long.toString(Runtime.getRuntime().totalMemory()) + "/" + Long.toString(Runtime.getRuntime().freeMemory())));
        append(new StringItem("AppFlags", TrackingMIDlet.getFlags()));
        append(new StringItem("Jsr75", "resetable? " + (new Boolean(cz.kruch.track.maps.Map.fileInputStreamResetable)).toString()));
        append(new StringItem("ProviderStatus", le == null ? "" : le.toString()));
        addCommand(new Command("Close", Command.CANCEL, 1));
        setCommandListener(this);

        // show
        Desktop.display.setCurrent(this);
    }

    public void commandAction(Command command, Displayable displayable) {
        Desktop.display.setCurrent(Desktop.screen);
    }
}
