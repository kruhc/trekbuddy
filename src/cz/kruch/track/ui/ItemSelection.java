// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.event.Callback;
import cz.kruch.track.util.Arrays;

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
        super(cz.kruch.track.TrackingMIDlet.wm ? title + " (TrekBuddy)" : title, List.IMPLICIT);
        this.callback = callback;
        this.next = next;
        addCommand(new Command("Cancel", Command.BACK, 1));
        setSelectCommand(new Command(selectLabel, Command.ITEM, 1));
        setCommandListener(this);
    }

    public void show(Enumeration items) {
        // add items
        Arrays.sort2list(this, items);

        // show selection
        Desktop.display.setCurrent(this);
    }

    public void commandAction(Command command, Displayable displayable) {
        // close selection
        Desktop.display.setCurrent(next);

        // invoke callback
        if (command.getCommandType() == Command.ITEM) {
            callback.invoke(getString(getSelectedIndex()), null, this);
        } else {
            callback.invoke(null, null, this);
        }
    }
}
