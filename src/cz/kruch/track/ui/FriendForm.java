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

import api.location.QualifiedCoordinates;

import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.Command;

import cz.kruch.track.event.Callback;
import cz.kruch.track.Resources;

/**
 * Form for SMS sending (I Am Here, Meet You There).
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class FriendForm extends Form implements CommandListener {
    public static final String MENU_SEND;

    static {
        MENU_SEND = Resources.getString(Resources.NAV_CMD_SEND);
    }

    private final Displayable next;
    private final Callback callback;
    private final Object closure;

    private final TextField fieldNumber;
    private final TextField fieldMessage;

    FriendForm(Displayable next, String title, QualifiedCoordinates coordinates,
               Callback callback, Object closure) {
        super(title);
        this.next = next;
        this.callback = callback;
        this.closure = closure;
        append(this.fieldNumber = new TextField(Resources.getString(Resources.NAV_FLD_RECIPIENT), null, 16, TextField.PHONENUMBER));
        append(this.fieldMessage = new TextField(Resources.getString(Resources.NAV_FLD_MESSAGE), null, 64, TextField.ANY));
        StringBuffer sb = new StringBuffer(32);
        NavigationScreens.toStringBuffer(coordinates, sb);
        append(new StringItem(Resources.getString(Resources.NAV_FLD_LOC), sb.toString()));
        sb = null; // gc hint
        addCommand(new Command(MENU_SEND, Command.OK, 1));
        addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Command.BACK, 1));
    }

    public void show() {
        // command handling
        setCommandListener(this);

        // show
        Desktop.display.setCurrent(this);
    }

    public void commandAction(Command command, Displayable displayable) {
        // restore previous
        Desktop.display.setCurrent(next);

        // handle wpt action
        if (command.getCommandType() == Command.OK) {
            callback.invoke(new Object[]{ FriendForm.MENU_SEND,
                                          fieldNumber.getString(),
                                          fieldMessage.getString(),
                                          closure
                                        }, null, this);
        }
    }
}
