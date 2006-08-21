// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.event.Callback;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.location.GpxTracklog;

import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.StringItem;

import api.location.Location;

public final class WaypointForm extends Form implements CommandListener {
    private Callback callback;
    private Displayable next;
    private Location location;

    private TextField fieldName;
    private TextField fieldComment;

    public WaypointForm(Displayable next, Callback callback, Location location) {
        super("AddWaypoint (!EXPERIMENTAL!)");
        this.callback = callback;
        this.next = next;
        this.location = location;
    }

    public void show() {
        // editable fields for waypoint name and comment
        append(new StringItem("Time", GpxTracklog.dateToXsdDate(location.getTimestamp())));
        append(new StringItem("Location", location.getQualifiedCoordinates().toString()));
        fieldName = new TextField("Name", null, 64, TextField.ANY);
        append(fieldName);
        fieldComment = new TextField("Comment", null, 256, TextField.ANY);
        append(fieldComment);

        // add command and handling
        addCommand(new Command("Cancel", Command.BACK, 1));
        addCommand(new Command("Save", Command.SCREEN, 1));
        setCommandListener(this);

        // show
        Desktop.display.setCurrent(this);
    }

    public void commandAction(Command command, Displayable displayable) {
        Waypoint wpt = null;

        // restore screen
        Desktop.display.setCurrent(next);

        if (command.getCommandType() == Command.SCREEN) {
            wpt = new Waypoint(location, fieldName.getString(), fieldComment.getString());
        }

        // notify that we are done
        callback.invoke(wpt, null);
    }
}
