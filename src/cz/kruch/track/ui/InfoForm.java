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

public class InfoForm implements CommandListener {
    private Display display;
    private Displayable previous;

    public InfoForm(Display display) {
        this.display = display;
        this.previous = display.getCurrent();
    }

    public void show() {
        // gc
        System.gc();

        // form
        Form form = new Form("Info");
        form.append(new StringItem("In emulator", (new Boolean(TrackingMIDlet.isEmulator())).toString()));
        form.append(new StringItem("Total memory", Long.toString(Runtime.getRuntime().totalMemory())));
        form.append(new StringItem("Free memory", Long.toString(Runtime.getRuntime().freeMemory())));
        form.addCommand(new Command("Close", Command.CANCEL, 1));
        form.setCommandListener(this);

        // show
        display.setCurrent(form);
    }

    public void commandAction(Command command, Displayable displayable) {
        display.setCurrent(previous);
    }
}
