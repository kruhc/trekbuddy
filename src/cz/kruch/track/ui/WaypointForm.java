// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.event.Callback;
import cz.kruch.track.fun.Camera;
import cz.kruch.track.location.Waypoint;

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
import api.location.MinDec;

import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone;

public final class WaypointForm extends Form
        implements CommandListener, ItemCommandListener, Callback {

    static final String MENU_NAVIGATE_ALONG = "Route Along";
    static final String MENU_NAVIGATE_BACK  = "Route Back";
    static final String MENU_NAVIGATE_TO    = "Navigate To";
    static final String MENU_SET_CURRENT    = "Set As Active";
    static final String MENU_GO_TO          = "Go To";
    static final String MENU_SAVE           = "Save";
    static final String MENU_USE            = "Add";
    static final String MENU_CLOSE          = "Close";
    static final String MENU_CANCEL         = "Cancel";

    private static final Calendar CALENDAR = Calendar.getInstance(TimeZone.getDefault());

    private static final String FIELD_NAME      = "Name";
    private static final String FIELD_COMMENT   = "Comment";
    private static final String FIELD_LAT       = "Lat";
    private static final String FIELD_LON       = "Lon";
    private static final String FIELD_TIME      = "Time";
    private static final String FIELD_LOCATION  = "Location";
    private static final String TITLE           = "Waypoint";

    private Displayable next;
    private QualifiedCoordinates coordinates;
    private Callback callback;

    private TextField fieldName;
    private TextField fieldComment;

/*
    private QualifiedCoordinates coordinates;
*/
    private TextField fieldLat;
    private TextField fieldLon;

    private byte[] imageBytes;
    private int imageNum = -1;

/*
    private ChoiceGroup editorSign;
    private TextField editorDeg;
    private TextField editorMin;
*/

    static int cnt = 0;

    public WaypointForm(Displayable next, Location location, Callback callback) {
        super(TITLE);
        this.next = next;
        this.coordinates = location.getQualifiedCoordinates();
        this.callback = callback;
        appendWithNewlineAfter(this.fieldName = new TextField(FIELD_NAME, null, 16, TextField.ANY));
        appendWithNewlineAfter(this.fieldComment = new TextField(FIELD_COMMENT, null, 256, TextField.ANY));
        appendWithNewlineAfter(new StringItem(FIELD_TIME, dateToString(location.getTimestamp())));
        appendWithNewlineAfter(new StringItem(FIELD_LOCATION, location.getQualifiedCoordinates().toString()));
        if (cz.kruch.track.TrackingMIDlet.supportsVideoCapture()) {
            StringItem snapshot = new StringItem("Snapshot", "Take", Item.BUTTON);
            snapshot.setDefaultCommand(new Command("Take", Command.ITEM, 1));
            snapshot.setItemCommandListener(this);
            appendWithNewlineAfter(snapshot);
        }
        addCommand(new Command(MENU_CANCEL, Command.BACK, 1));
        addCommand(new Command(MENU_SAVE, Command.SCREEN, 1));
    }

    public WaypointForm(Displayable next, Waypoint wpt, Callback callback) {
        super(TITLE);
        this.next = next;
        this.coordinates = wpt.getQualifiedCoordinates();
        this.callback = callback;
        appendWithNewlineAfter(new StringItem(FIELD_NAME, wpt.getName()));
        appendWithNewlineAfter(new StringItem(FIELD_COMMENT, wpt.getComment()));
        long timestamp = wpt.getTimestamp();
        if (timestamp > 0) {
            appendWithNewlineAfter(new StringItem(FIELD_TIME, dateToString(timestamp)));
        }
        append(new StringItem(FIELD_LOCATION, wpt.getQualifiedCoordinates().toString()));
        addCommand(new Command(MENU_CLOSE, Command.BACK, 1));
        addCommand(new Command(MENU_NAVIGATE_TO, Command.SCREEN, 1));
        addCommand(new Command(MENU_NAVIGATE_ALONG, Command.SCREEN, 2));
        addCommand(new Command(MENU_NAVIGATE_BACK, Command.SCREEN, 3));
/* TODO in 0.9.58
        addCommand(new Command(MENU_GO_TO, Command.SCREEN, 3));
*/
    }

    public WaypointForm(Displayable next, Callback callback,
                        QualifiedCoordinates pointer) {
        super(TITLE);
        this.next = next;
        this.callback = callback;
/*
        this.coordinates = pointer;
*/
        int c = cnt + 1;
        String name = c < 10 ? "WPT00" + c : (c < 100 ? "WPT0" + c : "WPT" + Integer.toString(c));
        appendWithNewlineAfter(this.fieldName = new TextField(FIELD_NAME, name, 16, TextField.ANY));
        appendWithNewlineAfter(this.fieldComment = new TextField(FIELD_COMMENT, CALENDAR.getTime().toString(), 64, TextField.ANY));
        appendWithNewlineAfter(this.fieldLat = new TextField(FIELD_LAT, (new MinDec(QualifiedCoordinates.LAT, pointer.getLat())).toString(),
                                                             13, TextField.ANY));
/*
        Command editCmd = new Command("Edit", Command.ITEM, 1);
        this.fieldLat.setDefaultCommand(editCmd);
        this.fieldLat.setItemCommandListener(this);
*/
        appendWithNewlineAfter(this.fieldLon = new TextField(FIELD_LON, (new MinDec(QualifiedCoordinates.LON, pointer.getLon())).toString(),
                                                             14, TextField.ANY));
/*
        this.fieldLat.setDefaultCommand(editCmd);
        this.fieldLat.setItemCommandListener(this);
*/
        addCommand(new Command(MENU_CLOSE, Command.BACK, 1));
        addCommand(new Command(MENU_USE, Command.SCREEN, 1));
    }

    private int appendWithNewlineAfter(Item item) {
        item.setLayout(Item.LAYOUT_2 | Item.LAYOUT_NEWLINE_AFTER);
        return append(item);
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
        if ("Take".equals(command.getLabel())) {
            try {
                (new Camera(this, this)).show();
            } catch (Throwable t) {
                Desktop.showError("Camera failed", t, this);
            }
        }
    }

    public void invoke(Object result, Throwable throwable, Object source) {
        if (result instanceof byte[]) {
            imageBytes = (byte[]) result;
            try {
                // release previous preview
                if (imageNum > -1) {
                    delete(imageNum);
                }

                // create preview
                byte[] thumbnail = Camera.getThumbnail(imageBytes);
                if (thumbnail == null) {
                    imageNum = append("<no preview>");
                } else {
                    imageNum = append(Image.createImage(thumbnail, 0, thumbnail.length));
                    thumbnail = null; // gc hint
                    System.gc();
                }
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
        if (Command.SCREEN == command.getCommandType()) {
            String label = command.getLabel();
            if (MENU_USE.equals(label)) {
                try {
                    Waypoint wpt = new Waypoint(getCoordinates(),
                                                fieldName.getString(),
                                                fieldComment.getString(),
                                                System.currentTimeMillis());
                    Desktop.display.setCurrent(next);
                    callback.invoke(new Object[]{ MENU_USE, wpt }, null, this);
                    cnt++;
                } catch (IllegalArgumentException e) {
                    Desktop.showWarning("Malformed coordinate", e, null);
                }
            } else if (MENU_SAVE.equals(label)) {
                Waypoint wpt = new Waypoint(coordinates,
                                            fieldName.getString(),
                                            fieldComment.getString());
                wpt.setUserObject(imageBytes);
                Desktop.display.setCurrent(next);
                callback.invoke(new Object[]{ MENU_SAVE, wpt }, null, this);
            } else if (MENU_NAVIGATE_TO.equals(label)) {
                Desktop.display.setCurrent(next);
                callback.invoke(new Object[]{ MENU_NAVIGATE_TO, null }, null, this);
            } else if (MENU_NAVIGATE_ALONG.equals(label)) {
                Desktop.display.setCurrent(next);
                callback.invoke(new Object[]{ MENU_NAVIGATE_ALONG, null }, null, this);
            } else if (MENU_NAVIGATE_BACK.equals(label)) {
                Desktop.display.setCurrent(next);
                callback.invoke(new Object[]{ MENU_NAVIGATE_BACK, null }, null, this);
            } else if (MENU_GO_TO.equals(label)) {
                Desktop.display.setCurrent(next);
                callback.invoke(new Object[]{ MENU_GO_TO, null }, null, this);
            }
        } else {
            Desktop.display.setCurrent(next);
        }
    }

    private QualifiedCoordinates getCoordinates() {
        double lat = stringToLatOrLon(fieldLat.getString());
        double lon = stringToLatOrLon(fieldLon.getString());

        return QualifiedCoordinates.newInstance(lat, lon);
    }

    private static double stringToLatOrLon(String value) {
        value = value.trim();
        if (value.length() < 5) {
            throw new IllegalArgumentException("Malformed coordinate: " + value);
        }

        final int sign;
        switch (value.charAt(0)) {
            case 'N': {
                sign = 1;
            } break;
            case 'S': {
                sign = -1;
            } break;
            case 'E': {
                sign = 1;
            } break;
            case 'W': {
                sign = -1;
            } break;
            default:
                throw new IllegalArgumentException("Malformed coordinate: " + value);
        }

        final int idxSign = value.indexOf(NavigationScreens.SIGN);
        if (idxSign < 3) {
            throw new IllegalArgumentException("Malformed coordinate: " + value);
        }
        final int idxApo = value.indexOf("\'", idxSign);

        double result = Integer.parseInt(value.substring(1, idxSign).trim());
        if (idxApo == -1) {
            result += Double.parseDouble(value.substring(idxSign + 1).trim()) / 60D;
        } else {
            result += Integer.parseInt(value.substring(idxSign + 1, idxApo).trim()) / 60D;
            result += Double.parseDouble(value.substring(idxApo + 1).trim()) / 3600D;
        }

        return result * sign;
    }

    private static String dateToString(final long time) {
        CALENDAR.setTime(new Date(time));
        StringBuffer sb = new StringBuffer();
        NavigationScreens.append(sb, CALENDAR.get(Calendar.YEAR)).append('-');
        appendTwoDigitStr(sb, CALENDAR.get(Calendar.MONTH) + 1).append('-');
        appendTwoDigitStr(sb, CALENDAR.get(Calendar.DAY_OF_MONTH)).append(' ');
        appendTwoDigitStr(sb, CALENDAR.get(Calendar.HOUR_OF_DAY)).append(':');
        appendTwoDigitStr(sb, CALENDAR.get(Calendar.MINUTE)).append(':');
        appendTwoDigitStr(sb, CALENDAR.get(Calendar.SECOND));

        return sb.toString();
    }

    private static StringBuffer appendTwoDigitStr(StringBuffer sb, final int i) {
        if (i < 10) {
            sb.append('0');
        }
        NavigationScreens.append(sb, i);

        return sb;
    }
}
