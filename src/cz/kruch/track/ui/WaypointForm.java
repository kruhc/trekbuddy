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
import cz.kruch.track.fun.Camera;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.Resources;

import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemCommandListener;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.ImageItem;

import api.location.Location;
import api.location.QualifiedCoordinates;

import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Form for waypoints.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class WaypointForm extends Form
        implements CommandListener, ItemCommandListener, Callback {

    /*private */static final String CMD_NAVIGATE_ALONG = Resources.getString(Resources.NAV_CMD_ROUTE_ALONG);
    /*private */static final String CMD_NAVIGATE_BACK = Resources.getString(Resources.NAV_CMD_ROUTE_BACK);
    /*private */static final String CMD_NAVIGATE_TO = Resources.getString(Resources.NAV_CMD_NAVIGATE_TO);
    /*private */static final String CMD_SET_CURRENT = Resources.getString(Resources.NAV_CMD_SET_AS_ACTIVE);
    /*private */static final String CMD_GO_TO = Resources.getString(Resources.NAV_CMD_GO_TO);
    /*private */static final String CMD_SHOW_ALL = Resources.getString(Resources.NAV_CMD_SHOW_ALL);
    /*private */static final String CMD_HIDE_ALL = Resources.getString(Resources.NAV_CMD_HIDE_ALL);
    /*private */static final String CMD_USE = Resources.getString(Resources.NAV_CMD_ADD);
    /*private */static final String CMD_SAVE = Resources.getString(Resources.NAV_CMD_SAVE);
    private static final String CMD_TAKE = Resources.getString(Resources.NAV_CMD_TAKE);

    private static final Calendar CALENDAR = Calendar.getInstance(TimeZone.getDefault());

    private final Callback callback;
    private QualifiedCoordinates coordinates;
    private long timestamp;

    private TextField fieldName, fieldComment;
    private TextField fieldLat, fieldLon, fieldAlt;

    private byte[] imageBytes;
    private int imageNum = -1;

    private static int cnt;

    /**
     * Info view constructor.
     */
    public WaypointForm(Waypoint wpt, Callback callback) {
        super(Resources.getString(Resources.NAV_TITLE_WPT));
        this.coordinates = wpt.getQualifiedCoordinates();
        this.callback = callback;
        // name
        appendWithNewlineAfter(new StringItem(Resources.getString(Resources.NAV_FLD_WPT_NAME), wpt.getName()));
        // comment
        appendWithNewlineAfter(new StringItem(Resources.getString(Resources.NAV_FLD_WPT_CMT), wpt.getComment()));
        // timestamp
        if (wpt.getTimestamp() > 0) {
            appendWithNewlineAfter(new StringItem(Resources.getString(Resources.NAV_FLD_TIME), dateToString(wpt.getTimestamp())));
        }
        // lat+lon
        StringBuffer sb = new StringBuffer(32);
        NavigationScreens.toStringBuffer(wpt.getQualifiedCoordinates(), sb);
        append(new StringItem(Resources.getString(Resources.NAV_FLD_LOC), sb.toString()));
        // altitude
        sb.delete(0, sb.length());
        final float alt = wpt.getQualifiedCoordinates().getAlt();
        if (Float.isNaN(alt)) {
            sb.append('?');
        } else {
            NavigationScreens.append(sb, (int) alt);
        }
        sb.append(' ').append('m');
        append(new StringItem(Resources.getString(Resources.NAV_FLD_ALT), sb.toString()));
        sb = null; // gc hint
        // form command
        addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Command.BACK, 1));
        addCommand(new Command(CMD_NAVIGATE_TO, Desktop.POSITIVE_CMD_TYPE, 1));
        addCommand(new Command(CMD_NAVIGATE_ALONG, Desktop.POSITIVE_CMD_TYPE, 2));
        addCommand(new Command(CMD_NAVIGATE_BACK, Desktop.POSITIVE_CMD_TYPE, 3));
        addCommand(new Command(CMD_GO_TO, Desktop.POSITIVE_CMD_TYPE, 4));
    }

    /**
     * "Record Current" constructor.
     */
    public WaypointForm(Location location, Callback callback) {
        super(Resources.getString(Resources.NAV_TITLE_WPT));
        this.coordinates = location.getQualifiedCoordinates().clone(); // copy
        this.timestamp = location.getTimestamp();
        this.callback = callback;
        // name
        appendWithNewlineAfter(this.fieldName = new TextField(Resources.getString(Resources.NAV_FLD_WPT_NAME), null, 16, TextField.ANY));
        // comment
        appendWithNewlineAfter(this.fieldComment = new TextField(Resources.getString(Resources.NAV_FLD_WPT_CMT), null, 256, TextField.ANY));
        // timestamp
        appendWithNewlineAfter(new StringItem(Resources.getString(Resources.NAV_FLD_TIME), dateToString(location.getTimestamp())));
        // coords
        StringBuffer sb = new StringBuffer(32);
        NavigationScreens.toStringBuffer(location.getQualifiedCoordinates(), sb);
        appendWithNewlineAfter(new StringItem(Resources.getString(Resources.NAV_FLD_LOC), sb.toString()));
        // altitude
        sb.delete(0, sb.length());
        final float alt = location.getQualifiedCoordinates().getAlt();
        if (Float.isNaN(alt)) {
            sb.append('?');
        } else {
            NavigationScreens.append(sb, (int) alt);
        }
        sb.append(' ').append('m');
        appendWithNewlineAfter(new StringItem(Resources.getString(Resources.NAV_FLD_ALT), sb.toString()));
        sb = null; // gc hint
        // form commands
        if (cz.kruch.track.TrackingMIDlet.supportsVideoCapture()) {
            StringItem snapshot = new StringItem(Resources.getString(Resources.NAV_FLD_SNAPSHOT), CMD_TAKE, Item.BUTTON);
            snapshot.setDefaultCommand(new Command(CMD_TAKE, Command.ITEM, 1));
            snapshot.setItemCommandListener(this);
            appendWithNewlineAfter(snapshot);
        }
        addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Command.BACK, 1));
        addCommand(new Command(CMD_SAVE, Desktop.POSITIVE_CMD_TYPE, 1));
    }

    /**
     * "Enter Custom" constructor.
     */
    public WaypointForm(Callback callback, QualifiedCoordinates pointer) {
        super(Resources.getString(Resources.NAV_TITLE_WPT));
        this.callback = callback;
        // generated name
        StringBuffer sb = new StringBuffer(32);
        sb.append("WPT");
        NavigationScreens.append(sb, cnt + 1, 3);
        appendWithNewlineAfter(this.fieldName = new TextField(Resources.getString(Resources.NAV_FLD_WPT_NAME), sb.toString(), 16, TextField.ANY));
        // comment
        appendWithNewlineAfter(this.fieldComment = new TextField(Resources.getString(Resources.NAV_FLD_WPT_CMT), dateToString(CALENDAR.getTime().getTime()), 64, TextField.ANY));
        // lat
        sb.delete(0, sb.length());
        sb.append(pointer.getLat() > 0D ? 'N' : 'S').append(' ');
        NavigationScreens.append(sb, QualifiedCoordinates.LAT, pointer.getLat(), true);
        appendWithNewlineAfter(this.fieldLat = new TextField(Resources.getString(Resources.NAV_FLD_WGS84LAT), sb.toString(), 13, TextField.ANY));
        // lon
        sb.delete(0, sb.length());
        sb.append(pointer.getLon() > 0D ? 'E' : 'W').append(' ');
        NavigationScreens.append(sb, QualifiedCoordinates.LON, pointer.getLon(), true);
        appendWithNewlineAfter(this.fieldLon = new TextField(Resources.getString(Resources.NAV_FLD_WGS84LON), sb.toString(), 14, TextField.ANY));
        appendWithNewlineAfter(this.fieldAlt = new TextField(Resources.getString(Resources.NAV_FLD_ALT), "", 14, TextField.NUMERIC));
        sb = null; // gc hint
        // commands
        addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Command.BACK, 1));
        addCommand(new Command(CMD_USE, Desktop.POSITIVE_CMD_TYPE, 1));
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
        if (CMD_TAKE.equals(command.getLabel())) {
            try {
                (new Camera(this, this)).show();
            } catch (Throwable t) {
                Desktop.showError(Resources.getString(Resources.NAV_MSG_CAMERA_FAILED), t, this);
            }
        }
    }

    public void invoke(Object result, Throwable throwable, Object source) {
        if (result instanceof byte[]) {
            imageBytes = null; // gc hint
            imageBytes = (byte[]) result;
            try {
                // release previous preview
                if (imageNum > -1) {
                    /* is this really necessary??? */
                    Item old = get(imageNum);
                    if (old instanceof ImageItem) {
                        ((ImageItem) old).setImage(null);
                    }
                    // remove item
                    delete(imageNum);
                }

                // create preview
                int[] thumbnail = Camera.getThumbnail(imageBytes);
                if (thumbnail == null) {
                    imageNum = append(Resources.getString(Resources.NAV_MSG_NO_PREVIEW));
                    Desktop.showInfo(Resources.getString(Resources.NAV_MSG_DO_NOT_WORRY), this);
                } else {
                    imageNum = append(Image.createImage(imageBytes, thumbnail[0], thumbnail[1] - thumbnail[0]));
                }
            } catch (Throwable t) {
                Desktop.showError(Resources.getString(Resources.NAV_MSG_DO_NOT_WORRY), t, this);
            }
        } else if (throwable != null) {
            Desktop.showError(Resources.getString(Resources.NAV_MSG_SNAPSHOT_FAILED), throwable, this);
        } else {
            Desktop.showError(Resources.getString(Resources.NAV_MSG_NO_SNAPSHOT), null, this);
        }
    }

    public void commandAction(Command command, Displayable displayable) {
        if (Desktop.POSITIVE_CMD_TYPE == command.getCommandType()) {
            String label = command.getLabel();
            if (CMD_USE.equals(label)) {
                try {
                    Waypoint wpt = new Waypoint(getCoordinates(),
                                                fieldName.getString(),
                                                fieldComment.getString(),
                                                null,
                                                System.currentTimeMillis());
                    callback.invoke(new Object[]{CMD_USE, wpt }, null, this);
                    cnt++;
                } catch (IllegalArgumentException e) {
                    Desktop.showWarning(null, e, null);
                }
            } else if (CMD_SAVE.equals(label)) {
                Waypoint wpt = new Waypoint(coordinates,
                                            fieldName.getString(),
                                            fieldComment.getString(),
                                            null,
                                            timestamp);
                wpt.setUserObject(imageBytes);
                callback.invoke(new Object[]{CMD_SAVE, wpt }, null, this);
            } else if (CMD_NAVIGATE_TO.equals(label)) {
                callback.invoke(new Object[]{CMD_NAVIGATE_TO, null }, null, this);
            } else if (CMD_NAVIGATE_ALONG.equals(label)) {
                callback.invoke(new Object[]{CMD_NAVIGATE_ALONG, null }, null, this);
            } else if (CMD_NAVIGATE_BACK.equals(label)) {
                callback.invoke(new Object[]{CMD_NAVIGATE_BACK, null }, null, this);
            } else if (CMD_GO_TO.equals(label)) {
                callback.invoke(new Object[]{CMD_GO_TO, null }, null, this);
            }
        } else {
            // gc hint
            imageBytes = null;
            // dummy invocation
            callback.invoke(new Object[]{ null, null }, null, this);
        }
    }

    private QualifiedCoordinates getCoordinates() {
        final double lat = stringToLatOrLon(fieldLat.getString());
        final double lon = stringToLatOrLon(fieldLon.getString());
        String altStr = fieldAlt.getString();
        float alt = Float.NaN;
        if (altStr != null && altStr.length() > 0) {
            alt = Float.parseFloat(altStr);
        }
        return QualifiedCoordinates.newInstance(lat, lon, alt);
    }

    private static double stringToLatOrLon(String value) {
        // trim whitespaces
        value = value.trim();

        // valid coord is at least 4 chars: <letter><space><degree><sign>
        if (value.length() < 4) {
            throw new IllegalArgumentException(Resources.getString(Resources.NAV_MSG_MALFORMED_COORD) + " " + value);
        }

        // cut last non-digit off
        if (!Character.isDigit(value.charAt(value.length() - 1))) {
            value = value.substring(0, value.length() - 1);
        }

        // sign
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
                throw new IllegalArgumentException(Resources.getString(Resources.NAV_MSG_MALFORMED_COORD) + " " + value);
        }

        final int idxSign = value.indexOf(NavigationScreens.SIGN);
        final int idxApo = value.indexOf("\'", idxSign);

        double result;
        if (idxSign == -1) { // N 3°
            result = Double.parseDouble(value.substring(1).trim());
        } else { // N 3°xxx
            result = Integer.parseInt(value.substring(1, idxSign).trim());
            if (idxApo == -1) { // N 3°6'
                result += Double.parseDouble(value.substring(idxSign + 1).trim()) / 60D;
            } else { // N 3°6'12.4"
                result += Integer.parseInt(value.substring(idxSign + 1, idxApo).trim()) / 60D;
                result += Double.parseDouble(value.substring(idxApo + 1).trim()) / 3600D;
            }
        }

        return result * sign;
    }

    private static String dateToString(final long time) {
        CALENDAR.setTime(new Date(time));
        StringBuffer sb = new StringBuffer(32);
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
