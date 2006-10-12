// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.event.Callback;
//#ifndef __NO_FS__
import cz.kruch.track.fun.Camera;
//#endif
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.TrackingMIDlet;

import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemCommandListener;
import javax.microedition.lcdui.Image;

import api.location.Location;
import api.location.QualifiedCoordinates;

import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone;

public final class WaypointForm extends Form
        implements CommandListener, ItemCommandListener, Callback {

    public static final String MENU_NAVIGATE_TO = "NavigateTo";
    public static final String MENU_SAVE = "Save";
    public static final String MENU_USE = "Add";

    private static final Calendar CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

    private Displayable next;
    private Location location;
    private Callback callback;

    private TextField fieldName;
    private TextField fieldComment;

/*
    private QualifiedCoordinates coordinates;
*/
    private TextField fieldLat;
    private TextField fieldLon;

    private byte[] imageBytes = null;
    private int imageNum = -1;

/*
    private ChoiceGroup editorSign;
    private TextField editorDeg;
    private TextField editorMin;
*/

    public WaypointForm(Displayable next, Location location, Callback callback) {
        super("Waypoint");
        this.next = next;
        this.location = location;
        this.callback = callback;
        appendWithNewlineAfter(this.fieldName = new TextField("Name", null, 16, TextField.ANY));
        appendWithNewlineAfter(this.fieldComment = new TextField("Comment", null, 256, TextField.ANY));
        appendWithNewlineAfter(new StringItem("Time", dateToString(location.getTimestamp())));
        appendWithNewlineAfter(new StringItem("Location", location.getQualifiedCoordinates().toString()));
//#ifndef __NO_FS__
        if (TrackingMIDlet.isJsr135()) {
            StringItem snapshot = new StringItem("Snapshot", "Take", Item.BUTTON);
            snapshot.setDefaultCommand(new Command("Take", Command.ITEM, 1));
            snapshot.setItemCommandListener(this);
            append(snapshot);
        }
//#endif
        addCommand(new Command("Cancel", Command.BACK, 1));
        addCommand(new Command(MENU_SAVE, Command.SCREEN, 1));
    }

    public WaypointForm(Displayable next, Waypoint wpt, Callback callback) {
        super("Waypoint");
        this.next = next;
        this.location = wpt;
        this.callback = callback;
        appendWithNewlineAfter(new StringItem("Name", wpt.getName()));
        appendWithNewlineAfter(new StringItem("Comment", wpt.getComment()));
        appendWithNewlineAfter(new StringItem("Time", dateToString(wpt.getTimestamp())));
        append(new StringItem("Location", wpt.getQualifiedCoordinates().toString()));
        addCommand(new Command("Close", Command.BACK, 1));
        addCommand(new Command(MENU_NAVIGATE_TO, Command.SCREEN, 1));
    }

    public WaypointForm(Displayable next, Waypoint wpt) {
        super("Waypoint");
        this.next = next;
        appendWithNewlineAfter(new StringItem("Name", wpt.getName()));
        appendWithNewlineAfter(new StringItem("Comment", wpt.getComment()));
        append(new StringItem("Location", wpt.getQualifiedCoordinates().toString()));
        addCommand(new Command("Close", Command.BACK, 1));
    }

    public WaypointForm(Displayable next, Callback callback,
                        QualifiedCoordinates pointer) {
        super("Waypoint");
        this.next = next;
        this.callback = callback;
/*
        this.coordinates = pointer;
*/
        appendWithNewlineAfter(this.fieldName = new TextField("Name", null, 16, TextField.ANY));
        appendWithNewlineAfter(this.fieldComment = new TextField("Comment", null, 64, TextField.ANY));
        appendWithNewlineAfter(this.fieldLat = new TextField("Lat", (new QualifiedCoordinates.MinDec(QualifiedCoordinates.LAT, pointer.getLat())).toString(),
                                                             12, TextField.ANY));
/*
        Command editCmd = new Command("Edit", Command.ITEM, 1);
        this.fieldLat.setDefaultCommand(editCmd);
        this.fieldLat.setItemCommandListener(this);
*/
        appendWithNewlineAfter(this.fieldLon = new TextField("Lon", (new QualifiedCoordinates.MinDec(QualifiedCoordinates.LON, pointer.getLon())).toString(),
                                                             12, TextField.ANY));
/*
        this.fieldLat.setDefaultCommand(editCmd);
        this.fieldLat.setItemCommandListener(this);
*/
        addCommand(new Command("Close", Command.BACK, 1));
        addCommand(new Command(MENU_USE, Command.SCREEN, 1));
    }

    private void appendWithNewlineAfter(Item item) {
        item.setLayout(Item.LAYOUT_2 | Item.LAYOUT_NEWLINE_AFTER);
        append(item);
    }

    public void show() {
        // command handling
        setCommandListener(this);

        // show
        Desktop.display.setCurrent(this);
    }

    public void commandAction(Command command, Item item) {
/*        if (item == fieldLat) {
            editCoordinate(new QualifiedCoordinates.MinDec(QualifiedCoordinates.LAT, coordinates.getLat()));
        } else if (item == fieldLon) {
            editCoordinate(new QualifiedCoordinates.MinDec(QualifiedCoordinates.LAT, coordinates.getLat()));
        } else */
//#ifndef __NO_FS__
        if ("Take".equals(command.getLabel())) {
            try {
                (new Camera(this, this)).show();
            } catch (Throwable t) {
                Desktop.showError("Camera failed", t, this);
            }
        }
//#endif        
    }

    public void invoke(Object result, Throwable throwable) {
        if (result instanceof byte[]) {
            imageBytes = (byte[]) result;
            try {
                Image image = Image.createImage(imageBytes, 0, imageBytes.length);
                delete(imageNum);
                imageNum = append(image);
            } catch (Throwable t) {
                Desktop.showError("Could not create preview but do not worry - image has been saved", t, this);
            }
        } else if (throwable != null) {
            Desktop.showError("Snapshot failed", throwable, this);
        } else {
            Desktop.showError("No snapshot", null, this);
        }
    }

    public void commandAction(Command command, Displayable displayable) {
        // handle action
        if (command.getCommandType() == Command.SCREEN) {
            String label = command.getLabel();
            if (MENU_USE.equals(label)) {
                try {
                    Waypoint wpt = new Waypoint(getCoordinates(), fieldName.getString(),
                                                fieldComment.getString(), System.currentTimeMillis());
                    Desktop.display.setCurrent(next);
                    callback.invoke(new Object[]{ MENU_USE, wpt }, null);
                } catch (IllegalArgumentException e) {
                    Desktop.showWarning("Malformed coordinate", e, null);
                }
            } else {
                Desktop.display.setCurrent(next);
                if (MENU_SAVE.equals(label)) {
                    Waypoint wpt = new Waypoint(location, fieldName.getString(), fieldComment.getString());
                    wpt.setUserObject(imageBytes);
                    callback.invoke(new Object[]{ MENU_SAVE, wpt }, null);
                } else if (MENU_NAVIGATE_TO.equals(label)) {
                    callback.invoke(new Object[]{ MENU_NAVIGATE_TO, null }, null);
                }
            }
        } else {
            Desktop.display.setCurrent(next);
        }
    }

    private QualifiedCoordinates getCoordinates() {
        double lat = QualifiedCoordinates.MinDec.fromDecimalString(fieldLat.getString()).doubleValue();
        double lon = QualifiedCoordinates.MinDec.fromDecimalString(fieldLon.getString()).doubleValue();

        return new QualifiedCoordinates(lat, lon);
    }

    private static String dateToString(long time) {
        CALENDAR.setTimeZone(TimeZone.getDefault());
        CALENDAR.setTime(new Date(time/* + Config.getSafeInstance().getTimeZoneOffset() * 1000*/));
        StringBuffer sb = new StringBuffer();
        sb.append(CALENDAR.get(Calendar.YEAR)).append('-');
        appendTwoDigitStr(sb, CALENDAR.get(Calendar.MONTH) + 1).append('-');
        appendTwoDigitStr(sb, CALENDAR.get(Calendar.DAY_OF_MONTH)).append(' ');
        appendTwoDigitStr(sb, CALENDAR.get(Calendar.HOUR_OF_DAY)).append(':');
        appendTwoDigitStr(sb, CALENDAR.get(Calendar.MINUTE)).append(':');
        appendTwoDigitStr(sb, CALENDAR.get(Calendar.SECOND));

        return sb.toString();
    }

    private static StringBuffer appendTwoDigitStr(StringBuffer sb, int i) {
        if (i < 10) {
            sb.append('0');
        }
        sb.append(i);

        return sb;
    }
}
