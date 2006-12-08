// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.event.Callback;

import javax.microedition.lcdui.List;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Command;
import java.util.Enumeration;

final class ItemSelection extends List implements CommandListener {
    private Callback callback;
    private Displayable next;

    public ItemSelection(Displayable next, String title, Callback callback) {
        this(next, title, "Select", callback);
    }

    public ItemSelection(Displayable next, String title, String selectLabel, Callback callback) {
        super(title, List.IMPLICIT);
        this.callback = callback;
        this.next = next;
        addCommand(new Command("Cancel", Command.BACK, 1));
        setSelectCommand(new Command(selectLabel, Command.ITEM, 1));
        setCommandListener(this);
    }

    public void show(Enumeration items) {
        while (items.hasMoreElements()) {
            String item = items.nextElement().toString();
            append(item, null);
        }
        Desktop.display.setCurrent(this);
    }

    public void commandAction(Command command, Displayable displayable) {
        // restore desktop UI
        Desktop.display.setCurrent(next);
        // invoke callback
        if (command.getCommandType() == Command.ITEM) {
            callback.invoke(getString(getSelectedIndex()), null);
        } else {
            callback.invoke(null, null);
        }
    }
}
