// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.TrackingMIDlet;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;

public class InfoForm extends Form implements CommandListener {
    private Display display;
    private Displayable previous;

    public InfoForm(Display display) {
        super("Info");
        this.display = display;
        this.previous = display.getCurrent();
    }

    public void show() {
        // gc
        System.gc();

        append(new StringItem("Memory", Long.toString(Runtime.getRuntime().totalMemory()) + "/" + Long.toString(Runtime.getRuntime().freeMemory())));
        append(new StringItem("AppFlags", TrackingMIDlet.getFlags()));
        addCommand(new Command("Close", Command.CANCEL, 1));
        setCommandListener(this);

        // show
        display.setCurrent(this);
    }

    public void commandAction(Command command, Displayable displayable) {
        display.setCurrent(previous);
    }
}
