// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.Resources;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.event.Callback;
import cz.kruch.track.fun.Camera;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.location.GroundspeakBean;
import cz.kruch.track.util.Mercator;

import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone;

import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemCommandListener;
import javax.microedition.lcdui.Font;

import api.location.Location;
import api.location.QualifiedCoordinates;
import api.location.CartesianCoordinates;
import api.location.Datum;

/**
 * Form for waypoints.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class WaypointForm implements CommandListener, ItemCommandListener, Callback {

    private static final String CMD_TAKE = Resources.getString(Resources.NAV_CMD_TAKE);
    private static final String CMD_HINT = Resources.getString(Resources.NAV_CMD_SHOW);

    private static final Calendar CALENDAR = Calendar.getInstance(TimeZone.getDefault());

    private final Callback callback;
    private QualifiedCoordinates coordinates;
    private Waypoint waypoint;
    private long timestamp, tracklogTime;

    private Form form;
    private TextField fieldName, fieldComment;
    private TextField fieldZone, fieldLat, fieldLon, fieldAlt;
    private TextField fieldNumber, fieldMessage;
    private Object closure;

    private String imagePath;
    private int previewItemIdx;

    private int latHash, lonHash;

    private int hintNum;

    private static int cnt;

    /**
     * Info view constructor.
     *
     * @param wpt waypoint
     * @param callback callback
     * @param distance distance to the waypoint from current position
     * @param modifiable flag
     */
    public WaypointForm(Waypoint wpt, Callback callback,
                        float distance, boolean modifiable) {
        this.waypoint = wpt;
        this.callback = callback;
        this.form = new Form(Resources.getString(Resources.NAV_TITLE_WPT));

        // name
        appendStringItem(Resources.getString(Resources.NAV_FLD_WPT_NAME), wpt.getName());

        // comment
        appendStringItem(Resources.getString(Resources.NAV_FLD_WPT_CMT), wpt.getComment());

        // timestamp
        if (wpt.getTimestamp() != -1) {
            appendStringItem(Resources.getString(Resources.NAV_FLD_TIME), dateToString(wpt.getTimestamp()));
        }

        // lat+lon
        final StringBuffer sb = new StringBuffer(32);
        NavigationScreens.printTo(wpt.getQualifiedCoordinates(), sb);
        appendStringItem(Resources.getString(Resources.NAV_FLD_LOC), sb.toString());

        // altitude
        sb.delete(0, sb.length());
        NavigationScreens.printAltitude(sb, wpt.getQualifiedCoordinates().getAlt());
        appendStringItem(Resources.getString(Resources.NAV_FLD_ALT), sb.toString());

        // distance
        sb.delete(0, sb.length());
        NavigationScreens.printDistance(sb, distance);
        appendStringItem(Resources.getString(Resources.NAV_FLD_DISTANCE), sb.toString());

        // Groundspeak
        if (wpt.getUserObject() instanceof GroundspeakBean) {
            final GroundspeakBean bean = (GroundspeakBean) wpt.getUserObject();
            appendStringItem("GC " + Resources.getString(Resources.NAV_FLD_WPT_NAME), bean.getName());
            appendStringItem(Resources.getString(Resources.NAV_FLD_GS_ID), bean.getId());
            appendStringItem(Resources.getString(Resources.NAV_FLD_GS_CLASS), bean.classify());
            final String shortListing = bean.getShortListing();
            if (shortListing != null && shortListing.length() != 0) {
                appendStringItem(Resources.getString(Resources.NAV_FLD_GS_LISTING_SHORT),
                                 stripHtml(shortListing));
            }
            final String longListing = bean.getLongListing();
            if (longListing != null && longListing.length() != 0) {
                appendStringItem(Resources.getString(Resources.NAV_FLD_GS_LISTING_LONG),
                                 stripHtml(longListing));
            }
            if (bean.getEncodedHints() != null && bean.getEncodedHints().length() != 0) {
                hintNum = appendStringItem(Resources.getString(Resources.NAV_FLD_GS_HINT), ":-)", Item.BUTTON);
                form.get(hintNum).setDefaultCommand(new Command(CMD_HINT, Command.ITEM, 1));
                form.get(hintNum).setItemCommandListener(this);
            }
        }

        // form command
        form.addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Desktop.BACK_CMD_TYPE, 1));
        form.addCommand(new ActionCommand(Resources.NAV_CMD_NAVIGATE_TO, Desktop.POSITIVE_CMD_TYPE, 1));
        form.addCommand(new ActionCommand(Resources.NAV_CMD_ROUTE_ALONG, Desktop.POSITIVE_CMD_TYPE, 2));
        form.addCommand(new ActionCommand(Resources.NAV_CMD_ROUTE_BACK, Desktop.POSITIVE_CMD_TYPE, 3));
        form.addCommand(new ActionCommand(Resources.NAV_CMD_GO_TO, Desktop.POSITIVE_CMD_TYPE, 4));
        if (modifiable) {
            form.addCommand(new ActionCommand(Resources.NAV_CMD_EDIT, Desktop.POSITIVE_CMD_TYPE, 5));
            form.addCommand(new ActionCommand(Resources.NAV_CMD_DELETE, Desktop.POSITIVE_CMD_TYPE, 6));
        }
    }

    /**
     * "Record Current" constructor.
     *
     * @param location location
     * @param callback callback
     */
    public WaypointForm(Location location, Callback callback) {
        this.coordinates = location.getQualifiedCoordinates().clone(); // copy
        this.timestamp = location.getTimestamp();
        this.callback = callback;
        this.previewItemIdx = -1;
        this.form = new Form(Resources.getString(Resources.NAV_TITLE_WPT));

        // name
        appendWithNewlineAfter(this.fieldName = createTextField(Resources.NAV_FLD_WPT_NAME, null, 128));

        // comment
        appendWithNewlineAfter(this.fieldComment = createTextField(Resources.NAV_FLD_WPT_CMT, null, 256));

        // timestamp
        appendWithNewlineAfter(new StringItem(Resources.getString(Resources.NAV_FLD_TIME), dateToString(location.getTimestamp())));

        // coords
        final StringBuffer sb = new StringBuffer(32);
        NavigationScreens.printTo(location.getQualifiedCoordinates(), sb);
        appendWithNewlineAfter(new StringItem(Resources.getString(Resources.NAV_FLD_LOC), sb.toString()));

        // altitude
        sb.delete(0, sb.length());
        NavigationScreens.printAltitude(sb, location.getQualifiedCoordinates().getAlt());
        appendWithNewlineAfter(new StringItem(Resources.getString(Resources.NAV_FLD_ALT), sb.toString()));

        // form commands
        if (cz.kruch.track.TrackingMIDlet.supportsVideoCapture()) {
            final StringItem snapshot = new StringItem(Resources.getString(Resources.NAV_FLD_SNAPSHOT), CMD_TAKE, Item.BUTTON);
            snapshot.setDefaultCommand(new Command(CMD_TAKE, Command.ITEM, 1));
            snapshot.setItemCommandListener(this);
            appendWithNewlineAfter(snapshot);
        }
        form.addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Desktop.CANCEL_CMD_TYPE, 1));
        form.addCommand(new ActionCommand(Resources.NAV_CMD_SAVE, Desktop.POSITIVE_CMD_TYPE, 1));
    }

    /**
     * "Enter Custom" constructor.
     *
     * @param callback callback
     * @param pointer current position
     */
    public WaypointForm(Callback callback, QualifiedCoordinates pointer) {
        this.callback = callback;
        this.form = new Form(Resources.getString(Resources.NAV_TITLE_WPT));

        // generated name
        final StringBuffer sb = new StringBuffer(32);
        sb.append("WPT");
        NavigationScreens.append(sb, cnt + 1, 3);

        // share
        populateEditableForm(sb.toString(), ""/*dateToString(CALENDAR.getTime().getTime())*/, pointer);

        // commands
        form.addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Desktop.CANCEL_CMD_TYPE, 1));
        form.addCommand(new ActionCommand(Resources.NAV_CMD_ADD, Desktop.POSITIVE_CMD_TYPE, 1));
    }

    /**
     * Editing constructor.
     *
     * @param callback callback
     * @param wpt waypoint
     */
    public WaypointForm(Callback callback, Waypoint wpt) {
        this.callback = callback;
        this.waypoint = wpt;
        this.form = new Form(Resources.getString(Resources.NAV_TITLE_WPT));

        // populate form
        populateEditableForm(wpt.getName(), wpt.getComment(), wpt.getQualifiedCoordinates());

        // commands
        form.addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Desktop.CANCEL_CMD_TYPE, 1));
        form.addCommand(new ActionCommand(Resources.NAV_CMD_UPDATE, Desktop.POSITIVE_CMD_TYPE, 1));
    }

    /**
     * Send SMS constructor.
     *
     * @param callback callback
     * @param type SMS type
     * @param coordinates coordinates
     */
    public WaypointForm(Callback callback, String type,
                        QualifiedCoordinates coordinates) {
        this.callback = callback;
        this.closure = type;
        this.form = new Form("SMS");

        // receiver number and message
        form.append(this.fieldNumber = createTextField(Resources.NAV_FLD_RECIPIENT, null, 16, TextField.PHONENUMBER));
        form.append(this.fieldMessage = createTextField(Resources.NAV_FLD_MESSAGE, null, 64));

        // coordinates
        final StringBuffer sb = new StringBuffer(32);
        NavigationScreens.printTo(coordinates, sb);
        form.append(new StringItem(Resources.getString(Resources.NAV_FLD_LOC), sb.toString()));

        // commands
        form.addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Desktop.CANCEL_CMD_TYPE, 1));
        form.addCommand(new ActionCommand(Resources.NAV_CMD_SEND, Desktop.POSITIVE_CMD_TYPE, 1));
    }

    public void setTracklogTime(long tracklogTime) {
        this.tracklogTime = tracklogTime;
    }

    private void populateEditableForm(final String name, final String comment,
                                      final QualifiedCoordinates qc) {
        // name
        appendWithNewlineAfter(this.fieldName = createTextField(Resources.NAV_FLD_WPT_NAME, name, 128));

        // comment
        appendWithNewlineAfter(this.fieldComment = createTextField(Resources.NAV_FLD_WPT_CMT, comment, 256));

        // coordinates
        final String labelX, labelY;
        switch (Config.cfmt) {
            case Config.COORDS_MAP_GRID: {
                if (NavigationScreens.isGrid()) {
                    // labels
                    labelX = Resources.getString(Resources.NAV_FLD_EASTING);
                    labelY = Resources.getString(Resources.NAV_FLD_NORTHING);

                    // zone
                    final QualifiedCoordinates localQc = Datum.contextDatum.toLocal(qc);
                    final CartesianCoordinates cc = Mercator.LLtoGrid(qc);
                    if (cc.zone != null) {
                        appendWithNewlineAfter(this.fieldZone = createTextField(Resources.NAV_FLD_ZONE, new String(cc.zone), 3));
                    }
                    CartesianCoordinates.releaseInstance(cc);
                    QualifiedCoordinates.releaseInstance(localQc);

                    // break!
                    break;
                }
            } // no break here for not(isGrid) path!
            case Config.COORDS_UTM: {
                // labels
                labelX = Resources.getString(Resources.NAV_FLD_UTM_EASTING);
                labelY = Resources.getString(Resources.NAV_FLD_UTM_NORTHING);

                // zone
                final CartesianCoordinates cc = Mercator.LLtoUTM(qc);
                appendWithNewlineAfter(this.fieldZone = createTextField(Resources.NAV_FLD_ZONE, new String(cc.zone), 3));
                CartesianCoordinates.releaseInstance(cc);
            } break;
            case Config.COORDS_GC_LATLON: {
                labelX = Resources.getString(Resources.NAV_FLD_WGS84LAT);
                labelY = Resources.getString(Resources.NAV_FLD_WGS84LON);
            } break;
            default: {
                labelX = Resources.getString(Resources.NAV_FLD_LAT);
                labelY = Resources.getString(Resources.NAV_FLD_LON);
            }
        }

        // lat/easting
        final StringBuffer sb = new StringBuffer(32);
        NavigationScreens.printTo(sb, qc, QualifiedCoordinates.LAT, true);
        appendWithNewlineAfter(this.fieldLat = new TextField(labelX, sb.toString(), 13, TextField.ANY));
        latHash = fieldLat.getString().trim().hashCode();

        // lon/northing
        sb.delete(0, sb.length());
        NavigationScreens.printTo(sb, qc, QualifiedCoordinates.LON, true);
        appendWithNewlineAfter(this.fieldLon = new TextField(labelY, sb.toString(), 14, TextField.ANY));
        lonHash = fieldLon.getString().trim().hashCode();

        // altitude
        sb.delete(0, sb.length());
        NavigationScreens.printAltitude(sb, qc.getAlt());
        appendWithNewlineAfter(this.fieldAlt = createTextField(Resources.NAV_FLD_ALT, sb.toString(), 5));
    }

    private int appendWithNewlineAfter(Item item) {
        item.setLayout(Item.LAYOUT_2 | Item.LAYOUT_NEWLINE_AFTER);
        return form.append(item);
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

    public WaypointForm show() {
        // command handling
        form.setCommandListener(this);

        // show
        Desktop.display.setCurrent(form);

        return this;
    }

    public void commandAction(Command command, Item item) {
        if (CMD_TAKE.equals(command.getLabel())) {
            try {
                Camera.show(form, this, tracklogTime);
            } catch (Throwable t) {
                Desktop.showError(Resources.getString(Resources.NAV_MSG_CAMERA_FAILED), t, form);
            }
        } else if (CMD_HINT.equals(command.getLabel())) {
            form.removeCommand(command);
            form.delete(hintNum);
            hintNum = appendStringItem(Resources.getString(Resources.NAV_FLD_GS_HINT), ((GroundspeakBean) waypoint.getUserObject()).getEncodedHints());
            Desktop.display.setCurrentItem(form.get(hintNum));
        }
    }

    public void invoke(Object result, Throwable throwable, Object source) {
        if (result instanceof String) { // JSR-234 and new JSR-135 capture snapshot path
            imagePath = (String) result;
            if (previewItemIdx == -1) {
                previewItemIdx = form.append(Resources.getString(Resources.NAV_MSG_NO_PREVIEW));
            }
            Desktop.showInfo(Resources.getString(Resources.NAV_MSG_DO_NOT_WORRY), form);
        } else if (throwable != null) {
            Desktop.showError(Resources.getString(Resources.NAV_MSG_SNAPSHOT_FAILED), throwable, form);
        } else {
            Desktop.showError(Resources.getString(Resources.NAV_MSG_NO_SNAPSHOT), null, form);
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
                    if (imagePath != null) {
                        wpt.addLink(imagePath);
                    }
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
                        if (touched()) { // full update
                            wpt.setQualifiedCoordinates(parseCoordinates());
                        } else { // partial update
                            parseAlt(wpt.getQualifiedCoordinates());
                        }
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
                    Desktop.showWarning("Internal error", new IllegalStateException("Unknown wpt action: " + action), null);
            }
        } else {
            // dummy invocation
            callback.invoke(new Object[]{ null, null }, null, this);
        }
    }

    private boolean touched() {
        return fieldLat.getString().trim().hashCode() != latHash || fieldLon.getString().trim().hashCode() != lonHash;
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
        final String lats = trimToDigit(fieldLat.getString());
        final String lons = trimToDigit(fieldLon.getString());

        // get coords
        switch (Config.cfmt) {
            case Config.COORDS_MAP_GRID: {
                if (NavigationScreens.isGrid()) {
                    // grab grid coords
                    final CartesianCoordinates cc = CartesianCoordinates.newInstance(zone, Integer.parseInt(lats), Integer.parseInt(lons));
                    final QualifiedCoordinates localQc = Mercator.GridtoLL(cc);
                    qc = Datum.contextDatum.toWgs84(localQc);
                    QualifiedCoordinates.releaseInstance(localQc);
                    CartesianCoordinates.releaseInstance(cc);
                    // break!
                    break;
                }
            } // no break for not(isGrid) path!
            case Config.COORDS_UTM: {
                // grab UTM coords
                final CartesianCoordinates cc = CartesianCoordinates.newInstance(zone, Integer.parseInt(lats), Integer.parseInt(lons));
                qc = Mercator.UTMtoLL(cc);
                CartesianCoordinates.releaseInstance(cc);
            } break;
            default: {
                final QualifiedCoordinates _qc = QualifiedCoordinates.newInstance(parseLatOrLon(lats),
                                                                                  parseLatOrLon(lons));
                if (Config.cfmt != Config.COORDS_GC_LATLON) {
                    qc = Datum.contextDatum.toWgs84(_qc);
                    QualifiedCoordinates.releaseInstance(_qc);
                } else {
                    qc = _qc;
                }
            }
        }

        // update altitude
        parseAlt(qc);

        return qc;
    }

    private void parseAlt(final QualifiedCoordinates qc) {
        final String altStr = fieldAlt.getString();
        if (altStr != null && altStr.length() > 0 && altStr.indexOf("?") == -1) {
            float alt = Float.parseFloat(altStr);
            switch (Config.units) {
                case Config.UNITS_IMPERIAL: {
                    alt *= 0.3048F;
                } break;
            }
            qc.setAlt(alt);
        }
    }

    private static double parseLatOrLon(String value) {
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
        final StringBuffer sb = new StringBuffer(32);
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

    private static String trimToDigit(String s) {
        s = s.trim();
        if (!Character.isDigit(s.charAt(s.length() - 1))) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static TextField createTextField(final short label,
                                             final String value,
                                             final int maxLength) {
        return createTextField(label, value, maxLength, TextField.ANY);
    }

    private static TextField createTextField(final short label,
                                             final String value,
                                             final int maxLength,
                                             final int type) {
        final TextField result;
        if (value != null && value.length() > maxLength) {
            result = new TextField(Resources.getString(label),
                                   value.substring(0, maxLength), maxLength, type);
        } else {
            result = new TextField(Resources.getString(label),
                                   value, maxLength, type);
        }
        return result;
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
