// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.util.Logger;
import cz.kruch.track.event.Callback;

import javax.microedition.lcdui.List;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Command;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;
import javax.microedition.io.Connector;
import java.util.Enumeration;
import java.io.IOException;

public class ItemSelection extends List implements CommandListener {
    private Display display;
    private Callback callback;
    private Displayable previous;

    public ItemSelection(Display display, Displayable nextDisplayable, String title, Callback callback) {
        super(title, List.IMPLICIT);
        this.display = display;
        this.callback = callback;
        this.previous = nextDisplayable;
        addCommand(new Command("Cancel", Command.CANCEL, 1));
        setCommandListener(this);
        display.setCurrent(this);
    }

    public void show(Enumeration items) {
        while (items.hasMoreElements()) {
            String item = items.nextElement().toString();
            append(item, null);
        }
        addCommand(List.SELECT_COMMAND);
    }

    public void commandAction(Command command, Displayable displayable) {
        display.setCurrent(previous);
        if (command == List.SELECT_COMMAND) {
            callback.invoke(getString(getSelectedIndex()), null);
        }
    }
}
