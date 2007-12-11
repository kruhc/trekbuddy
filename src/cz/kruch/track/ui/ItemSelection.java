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

import cz.kruch.track.event.Callback;
import cz.kruch.track.Resources;

import javax.microedition.lcdui.List;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Command;
import java.util.Enumeration;

/**
 * Generic select-from-list form.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class ItemSelection implements CommandListener {
    private final Callback callback;
    private final Displayable next;
    private final String title, selectLabel;

    public ItemSelection(Displayable next, String title, Callback callback) {
        this(next, title, Resources.getString(Resources.DESKTOP_CMD_SELECT), callback);
    }

    private ItemSelection(Displayable next, String title, String selectLabel, Callback callback) {
        this.callback = callback;
        this.next = next;
        this.title = Resources.prefixed(title);
        this.selectLabel = selectLabel;
    }

    public void show(Enumeration items) {
        // add items and commands
        List list = FileBrowser.sort2list(title, items, null);
        list.addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Command.BACK, 1));
        list.setSelectCommand(new Command(selectLabel, Command.ITEM, 1));
        list.setCommandListener(this);

        // show selection
        Desktop.display.setCurrent(list);
    }

    public void commandAction(Command command, Displayable displayable) {
        // close selection
        Desktop.display.setCurrent(next);

        // invoke callback
        if (command.getCommandType() == Command.ITEM) {
            List list = (List) displayable;
            callback.invoke(list.getString(list.getSelectedIndex()), null, this);
        } else {
            callback.invoke(null, null, this);
        }
    }
}
