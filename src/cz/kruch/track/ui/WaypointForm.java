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
import cz.kruch.track.location.GroundspeakBean;
import cz.kruch.track.Resources;
import cz.kruch.track.util.Mercator;
import cz.kruch.track.configuration.Config;

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
import javax.microedition.lcdui.Font;

import api.location.Location;
import api.location.QualifiedCoordinates;
import api.location.CartesianCoordinates;
import api.location.Datum;

import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Form for waypoints.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class WaypointForm extends Form
        implements CommandListener, ItemCommandListener, Callback {

    private static final String CMD_TAKE = Resources.getString(Resources.NAV_CMD_TAKE);
    private static final String CMD_HINT = Resources.getString(Resources.NAV_CMD_SHOW);

    private static final Calendar CALENDAR = Calendar.getInstance(TimeZone.getDefault());

    private final Callback callback;
    private QualifiedCoordinates coordinates;
    private Waypoint waypoint;
    private long timestamp;

    private TextField fieldName, fieldComment;
    private TextField fieldZone, fieldLat, fieldLon, fieldAlt;

    private TextField fieldNumber, fieldMessage;
    private Object closure;

    private byte[] imageBytes;
    private int imageNum = -1;

    private int hintNum;

    private static int cnt;

    /**
     * Info view constructor.
     */
    public WaypointForm(final Waypoint wpt, final Callback callback,
                        final float distance, final boolean isReal) {
        super(Resources.getString(Resources.NAV_TITLE_WPT));
        this.waypoint = wpt;
        this.callback = callback;

        // name
        appendStringItem(Resources.getString(Resources.NAV_FLD_WPT_NAME), wpt.getName());

        // comment
        appendStringItem(Resources.getString(Resources.NAV_FLD_WPT_CMT), wpt.getComment());

        // timestamp
        if (wpt.getTimestamp() > 0) {
            appendStringItem(Resources.getString(Resources.NAV_FLD_TIME), dateToString(wpt.getTimestamp()));
        }

        // lat+lon
        final StringBuffer sb = new StringBuffer(32);
        NavigationScreens.printTo(wpt.getQualifiedCoordinates(), sb);
        appendStringItem(Resources.getString(Resources.NAV_FLD_LOC), sb.toString());

        // altitude
        sb.delete(0, sb.length());
        fillAltitudeInfo(wpt.getQualifiedCoordinates().getAlt(), sb);
        appendStringItem(Resources.getString(Resources.NAV_FLD_ALT), sb.toString());

        // distance
        sb.delete(0, sb.length());
        NavigationScreens.printDistance(sb, distance);
        appendStringItem(Resources.getString(Resources.NAV_FLD_DISTANCE), sb.toString());

        // Groundspeak
        if (wpt.getUserObject() instanceof GroundspeakBean) {
            final GroundspeakBean bean = (GroundspeakBean) wpt.getUserObject();
            appendStringItem(Resources.getString(Resources.NAV_FLD_GS_ID), bean.id);
            appendStringItem(Resources.getString(Resources.NAV_FLD_GS_CLASS), bean.classify());
            if (bean.shortListing != null && bean.shortListing.length() != 0) {
                appendStringItem(Resources.getString(Resources.NAV_FLD_GS_LISTING_SHORT),
                                 stripHtml(bean.shortListing));
            }
            if (bean.longListing != null && bean.longListing.length() != 0) {
                appendStringItem(Resources.getString(Resources.NAV_FLD_GS_LISTING_LONG),
                                 stripHtml(bean.longListing));
            }
            if (bean.encodedHints != null && bean.encodedHints.length() != 0) {
                hintNum = appendStringItem(Resources.getString(Resources.NAV_FLD_GS_HINT), ":-)", Item.BUTTON);
                get(hintNum).setDefaultCommand(new Command(CMD_HINT, Command.ITEM, 1));
                get(hintNum).setItemCommandListener(this);
            }
        }

        // form command
        addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Command.BACK, 1));
        addCommand(new ActionCommand(Resources.NAV_CMD_NAVIGATE_TO, Desktop.POSITIVE_CMD_TYPE, 1));
        addCommand(new ActionCommand(Resources.NAV_CMD_ROUTE_ALONG, Desktop.POSITIVE_CMD_TYPE, 2));
        addCommand(new ActionCommand(Resources.NAV_CMD_ROUTE_BACK, Desktop.POSITIVE_CMD_TYPE, 3));
        addCommand(new ActionCommand(Resources.NAV_CMD_GO_TO, Desktop.POSITIVE_CMD_TYPE, 4));
        if (isReal) {
            addCommand(new ActionCommand(Resources.NAV_CMD_EDIT, Desktop.POSITIVE_CMD_TYPE, 5));
            addCommand(new ActionCommand(Resources.NAV_CMD_DELETE, Desktop.POSITIVE_CMD_TYPE, 6));
        }
    }

    /**
     * "Record Current" constructor.
     */
    public WaypointForm(final Location location, final Callback callback) {
        super(Resources.getString(Resources.NAV_TITLE_WPT));
        this.coordinates = location.getQualifiedCoordinates().clone(); // copy
        this.timestamp = location.getTimestamp();
        this.callback = callback;

        // name
        appendWithNewlineAfter(this.fieldName = new TextField(Resources.getString(Resources.NAV_FLD_WPT_NAME), null, 32, TextField.ANY));

        // comment
        appendWithNewlineAfter(this.fieldComment = new TextField(Resources.getString(Resources.NAV_FLD_WPT_CMT), null, 256, TextField.ANY));

        // timestamp
        appendWithNewlineAfter(new StringItem(Resources.getString(Resources.NAV_FLD_TIME), dateToString(location.getTimestamp())));

        // coords
        final StringBuffer sb = new StringBuffer(32);
        NavigationScreens.printTo(location.getQualifiedCoordinates(), sb);
        appendWithNewlineAfter(new StringItem(Resources.getString(Resources.NAV_FLD_LOC), sb.toString()));

        // altitude
        sb.delete(0, sb.length());
        fillAltitudeInfo(location.getQualifiedCoordinates().getAlt(), sb);
        appendWithNewlineAfter(new StringItem(Resources.getString(Resources.NAV_FLD_ALT), sb.toString()));

        // form commands
        if (cz.kruch.track.TrackingMIDlet.supportsVideoCapture()) {
            final StringItem snapshot = new StringItem(Resources.getString(Resources.NAV_FLD_SNAPSHOT), CMD_TAKE, Item.BUTTON);
            snapshot.setDefaultCommand(new Command(CMD_TAKE, Command.ITEM, 1));
            snapshot.setItemCommandListener(this);
            appendWithNewlineAfter(snapshot);
        }
        addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Command.BACK, 1));
        addCommand(new ActionCommand(Resources.NAV_CMD_SAVE, Desktop.POSITIVE_CMD_TYPE, 1));
    }

    /**
     * "Enter Custom" constructor.
     */
    public WaypointForm(final Callback callback, final QualifiedCoordinates pointer) {
        super(Resources.getString(Resources.NAV_TITLE_WPT));
        this.callback = callback;

        // generated name
        final StringBuffer sb = new StringBuffer(32);
        sb.append("WPT");
        NavigationScreens.append(sb, cnt + 1, 3);
        appendWithNewlineAfter(this.fieldName = new TextField(Resources.getString(Resources.NAV_FLD_WPT_NAME), sb.toString(), 32, TextField.ANY));

        // comment
        appendWithNewlineAfter(this.fieldComment = new TextField(Resources.getString(Resources.NAV_FLD_WPT_CMT), dateToString(CALENDAR.getTime().getTime()), 256, TextField.ANY));

        // coordinates
        final String labelX, labelY;
        if (Config.useGridFormat && NavigationScreens.isGrid()) {

            // labels
            labelX = Resources.getString(Resources.NAV_FLD_EASTING);
            labelY = Resources.getString(Resources.NAV_FLD_NORTHING);

            // zone
            final QualifiedCoordinates localQc = Datum.contextDatum.toLocal(pointer);
            final CartesianCoordinates cc = Mercator.LLtoGrid(pointer);
            if (cc.zone != null) {
                appendWithNewlineAfter(this.fieldZone = new TextField(Resources.getString(Resources.NAV_FLD_ZONE), new String(cc.zone), 3, TextField.ANY));
            }
            CartesianCoordinates.releaseInstance(cc);
            QualifiedCoordinates.releaseInstance(localQc);

        } else if (Config.useUTM) {

            // labels
            labelX = Resources.getString(Resources.NAV_FLD_UTM_EASTING);
            labelY = Resources.getString(Resources.NAV_FLD_UTM_NORTHING);

            // zone
            final CartesianCoordinates cc = Mercator.LLtoUTM(pointer);
            appendWithNewlineAfter(this.fieldZone = new TextField(Resources.getString(Resources.NAV_FLD_ZONE), new String(cc.zone), 3, TextField.ANY));
            CartesianCoordinates.releaseInstance(cc);

        } else {

            // labels
            if (Config.useGeocachingFormat) {
                labelX = Resources.getString(Resources.NAV_FLD_WGS84LAT);
                labelY = Resources.getString(Resources.NAV_FLD_WGS84LON);
            } else {
                labelX = Resources.getString(Resources.NAV_FLD_LAT);
                labelY = Resources.getString(Resources.NAV_FLD_LON);
            }

        }

        // lat/easting
        sb.delete(0, sb.length());
        NavigationScreens.printTo(sb, pointer, QualifiedCoordinates.LAT);
        appendWithNewlineAfter(this.fieldLat = new TextField(labelX, sb.toString(), 13, TextField.ANY));

        // lon/northing
        sb.delete(0, sb.length());
        NavigationScreens.printTo(sb, pointer, QualifiedCoordinates.LON);
        appendWithNewlineAfter(this.fieldLon = new TextField(labelY, sb.toString(), 14, TextField.ANY));

        // altitude
        appendWithNewlineAfter(this.fieldAlt = new TextField(Resources.getString(Resources.NAV_FLD_ALT), "", 4, TextField.NUMERIC));

        // commands
        addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Command.BACK, 1));
        addCommand(new ActionCommand(Resources.NAV_CMD_ADD, Desktop.POSITIVE_CMD_TYPE, 1));
    }

    /**
     * Editing constructor.
     */
    public WaypointForm(final Callback callback, final Waypoint wpt) {
        super(Resources.getString(Resources.NAV_TITLE_WPT));
        this.callback = callback;
        this.waypoint = wpt;

        // populate form
        populateEditableForm(wpt.getName(), wpt.getComment(), wpt.getQualifiedCoordinates());

        // commands
        addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Command.BACK, 1));
        addCommand(new ActionCommand(Resources.NAV_CMD_UPDATE, Desktop.POSITIVE_CMD_TYPE, 1));
    }

    /**
     * Send SMS constructor.
     */
    public WaypointForm(final Callback callback, final String type,
                        final QualifiedCoordinates coordinates) {
        super("SMS");
        this.callback = callback;
        this.closure = type;

        // receiver number and message
        append(this.fieldNumber = new TextField(Resources.getString(Resources.NAV_FLD_RECIPIENT), null, 16, TextField.PHONENUMBER));
        append(this.fieldMessage = new TextField(Resources.getString(Resources.NAV_FLD_MESSAGE), null, 64, TextField.ANY));

        // coordinates
        final StringBuffer sb = new StringBuffer(32);
        NavigationScreens.printTo(coordinates, sb);
        append(new StringItem(Resources.getString(Resources.NAV_FLD_LOC), sb.toString()));

        // commands
        addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Command.BACK, 1));
        addCommand(new ActionCommand(Resources.NAV_CMD_SEND, Desktop.POSITIVE_CMD_TYPE, 1));
    }

    private void populateEditableForm(final String name, final String comment,
                                      final QualifiedCoordinates qc) {
        final StringBuffer sb = new StringBuffer(32);

        // name
        appendWithNewlineAfter(this.fieldName = new TextField(Resources.getString(Resources.NAV_FLD_WPT_NAME), name, 32, TextField.ANY));

        // comment
        appendWithNewlineAfter(this.fieldComment = new TextField(Resources.getString(Resources.NAV_FLD_WPT_CMT), comment, 256, TextField.ANY));

        // coordinates
        final String labelX, labelY;
        if (Config.useGridFormat && NavigationScreens.isGrid()) {

            // labels
            labelX = Resources.getString(Resources.NAV_FLD_EASTING);
            labelY = Resources.getString(Resources.NAV_FLD_NORTHING);

            // zone
            final QualifiedCoordinates localQc = Datum.contextDatum.toLocal(qc);
            final CartesianCoordinates cc = Mercator.LLtoGrid(qc);
            if (cc.zone != null) {
                appendWithNewlineAfter(this.fieldZone = new TextField(Resources.getString(Resources.NAV_FLD_ZONE), new String(cc.zone), 3, TextField.ANY));
            }
            CartesianCoordinates.releaseInstance(cc);
            QualifiedCoordinates.releaseInstance(localQc);

        } else if (Config.useUTM) {

            // labels
            labelX = Resources.getString(Resources.NAV_FLD_UTM_EASTING);
            labelY = Resources.getString(Resources.NAV_FLD_UTM_NORTHING);

            // zone
            final CartesianCoordinates cc = Mercator.LLtoUTM(qc);
            appendWithNewlineAfter(this.fieldZone = new TextField(Resources.getString(Resources.NAV_FLD_ZONE), new String(cc.zone), 3, TextField.ANY));
            CartesianCoordinates.releaseInstance(cc);

        } else {

            // labels
            if (Config.useGeocachingFormat) {
                labelX = Resources.getString(Resources.NAV_FLD_WGS84LAT);
                labelY = Resources.getString(Resources.NAV_FLD_WGS84LON);
            } else {
                labelX = Resources.getString(Resources.NAV_FLD_LAT);
                labelY = Resources.getString(Resources.NAV_FLD_LON);
            }

        }

        // lat/easting
        sb.delete(0, sb.length());
        NavigationScreens.printTo(sb, qc, QualifiedCoordinates.LAT);
        appendWithNewlineAfter(this.fieldLat = new TextField(labelX, sb.toString(), 13, TextField.ANY));

        // lon/northing
        sb.delete(0, sb.length());
        NavigationScreens.printTo(sb, qc, QualifiedCoordinates.LON);
        appendWithNewlineAfter(this.fieldLon = new TextField(labelY, sb.toString(), 14, TextField.ANY));

        // altitude
        appendWithNewlineAfter(this.fieldAlt = new TextField(Resources.getString(Resources.NAV_FLD_ALT),
                                                             Float.isNaN(qc.getAlt()) ? "?" : Integer.toString((int) qc.getAlt()),
                                                             4, TextField.ANY));
    }

    private StringBuffer fillAltitudeInfo(final float alt, final StringBuffer sb) {
        if (Float.isNaN(alt)) {
            sb.append('?');
        } else {
            NavigationScreens.append(sb, (int) alt);
        }
        sb.append(' ').append('m');
        return sb;
    }

    private int appendWithNewlineAfter(Item item) {
        item.setLayout(Item.LAYOUT_2 | Item.LAYOUT_NEWLINE_AFTER);
        return append(item);
    }

    private int appendStringItem(final String label, final String text) {
        if (text != null) {
            return appendStringItem(label, text, Item.PLAIN);
        }
        return -1;
    }

    private int appendStringItem(final String label, final String text, final int appearance) {
        final StringItem item = new StringItem(label, text, appearance);
        if (Desktop.fontStringItems == null) {
            final Font f = item.getFont();
            Desktop.fontStringItems = Font.getFont(f.getFace(), f.getStyle(), Font.SIZE_SMALL);
        }
        item.setFont(Desktop.fontStringItems);
        return appendWithNewlineAfter(item);
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
        } else if (CMD_HINT.equals(command.getLabel())) {
            removeCommand(command);
            delete(hintNum);
            hintNum = appendStringItem(Resources.getString(Resources.NAV_FLD_GS_HINT), ((GroundspeakBean) waypoint.getUserObject()).encodedHints);
            Desktop.display.setCurrentItem(get(hintNum));
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
                    // remove existing item
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
            final int action = ((ActionCommand) command).getAction();
            final Integer actionObject = new Integer(action);
            switch (action) {
                case Resources.NAV_CMD_ROUTE_ALONG:
                case Resources.NAV_CMD_ROUTE_BACK:
                case Resources.NAV_CMD_NAVIGATE_TO:
                case Resources.NAV_CMD_GO_TO: {
                    callback.invoke(new Object[]{ actionObject, null }, null, this);
                } break;
                case Resources.NAV_CMD_ADD: {
                    try {
                        final Waypoint wpt = new Waypoint(parseCoordinates(),
                                                          fieldName.getString(),
                                                          fieldComment.getString(),
                                                          System.currentTimeMillis());
                        callback.invoke(new Object[]{ actionObject, wpt }, null, this);
                        cnt++;
                    } catch (IllegalArgumentException e) {
                        Desktop.showWarning(null, e, null);
                    }
                } break;
                case Resources.NAV_CMD_SAVE: {
                    final Waypoint wpt = new Waypoint(coordinates,
                                                      fieldName.getString(),
                                                      fieldComment.getString(),
                                                      timestamp);
                    wpt.setUserObject(imageBytes);
                    callback.invoke(new Object[]{ actionObject, wpt }, null, this);
                } break;
                case Resources.NAV_CMD_SEND: {
                    callback.invoke(new Object[]{ actionObject,
                                                  fieldNumber.getString(),
                                                  fieldMessage.getString(),
                                                  closure
                                                }, null, this);

                } break;
                case Resources.NAV_CMD_EDIT: {
                    (new WaypointForm(callback, waypoint)).show();
                } break;
                case Resources.NAV_CMD_UPDATE: {
                    final Waypoint wpt = waypoint;
                    try {
                        wpt.setQualifiedCoordinates(parseCoordinates());
                        wpt.setName(fieldName.getString());
                        wpt.setComment(fieldComment.getString());
                        callback.invoke(new Object[]{ actionObject, wpt }, null, this);
                    } catch (IllegalArgumentException e) {
                        Desktop.showWarning(null, e, null);
                    }
                } break;
                case Resources.NAV_CMD_DELETE: {
                    callback.invoke(new Object[]{ actionObject, waypoint }, null, this);
                } break;
                default:
                    Desktop.showWarning("Unknown wpt action: " + action, null, null);
            }
        } else {
            // dummy invocation
            callback.invoke(new Object[]{ null, null }, null, this);
        }
        // gc hint
        imageBytes = null;
    }

    private QualifiedCoordinates parseCoordinates() {
        QualifiedCoordinates qc;
        char[] zone = null;

        // get zone
        final String zoneStr = fieldZone == null ? null : fieldZone.getString();
        if (zoneStr != null && zoneStr.length() > 0) {
            zone = zoneStr.toCharArray();
        }

        // get x/y
        String lats = trim(fieldLat.getString());
        String lons = trim(fieldLon.getString());

        // get coords
        if (Config.useGridFormat && NavigationScreens.isGrid()) {
            final CartesianCoordinates cc = CartesianCoordinates.newInstance(zone, Integer.parseInt(lats), Integer.parseInt(lons));
            final QualifiedCoordinates localQc = Mercator.GridtoLL(cc);
            qc = Datum.contextDatum.toWgs84(localQc);
            QualifiedCoordinates.releaseInstance(localQc);
            CartesianCoordinates.releaseInstance(cc);
        } else if (Config.useUTM) {
            final CartesianCoordinates cc = CartesianCoordinates.newInstance(zone, Integer.parseInt(lats), Integer.parseInt(lons));
            qc = Mercator.UTMtoLL(cc);
            CartesianCoordinates.releaseInstance(cc);
        } else {
            final QualifiedCoordinates _qc = QualifiedCoordinates.newInstance(parseLatOrLon(lats),
                                                                              parseLatOrLon(lons));
            if (Config.useGeocachingFormat) {
                qc = _qc;
            } else {
                qc = Datum.contextDatum.toWgs84(_qc);
                QualifiedCoordinates.releaseInstance(_qc);
            }
        }

        // get altitude
        final String altStr = fieldAlt.getString();
        if (altStr != null && altStr.length() > 0 && !"?".equals(altStr)) {
            qc.setAlt(Float.parseFloat(altStr));
        }

        return qc;
    }

    private String trim(String s) {
        // trim spaces
        s = s.trim();

        // cut last non-digit off
        if (!Character.isDigit(s.charAt(s.length() - 1))) {
            s = s.substring(0, s.length() - 1);
        }

        return s;
    }

    private static double parseLatOrLon(final String value) {
        // valid coord is at least 4 chars: <letter><space><degree><sign>
        if (value.length() < 4) {
            throw new IllegalArgumentException(Resources.getString(Resources.NAV_MSG_MALFORMED_COORD) + " " + value);
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

    private static String stripHtml(final String value) {
        try {
            final char[] raw = value.toCharArray();
            int end = raw.length;
            int i = 0, i0 = -1, i1 = -1;
            while (i < end) {
                if (i0 == -1) {
                    if (raw[i] == '<') {
                        i0 = i;
                    }
                } else {
                    if (raw[i] == '>') {
                        final int tagLen = i - i0 + 1;
                        int less = 0;
                        if (i0 == i1 + 1) {
                            less = 0;
                        } else {
                            int is = i0 + 1;
                            while (is < i && is == ' ') is++;
                            switch (raw[is]) {
                                case 'b':
                                case 'p': { /* probably <p> or <br> */
                                    raw[i0] = '\n';
                                    less = 1;
                                } break;
                                case '/': {
                                } break;
                                default: {
                                    raw[i0] = 0xb7;
                                    raw[i0 + 1] = ' ';
                                    less = 2;
                                }
                            }
                        }
                        System.arraycopy(raw, i + 1, raw, i0 + less, end - i - 1);
                        end -= tagLen - less;
                        i = i0 + (less - 1);
                        i1 = i;
                        i0 = -1;
                    }
                }
                i++;
            }
            return new String(raw, 0, end);
        } catch (Exception e) {
            return value;
        }
    }
}
