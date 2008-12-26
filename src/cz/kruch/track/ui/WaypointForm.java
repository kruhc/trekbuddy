// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.Resources;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.event.Callback;
import cz.kruch.track.fun.Camera;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.location.GroundspeakBean;
import cz.kruch.track.util.Mercator;

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
    private long timestamp, tracklogTime;

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
     */
    public WaypointForm(Waypoint wpt, Callback callback,
                        float distance, boolean modifiable) {
        super(Resources.getString(Resources.NAV_TITLE_WPT));
        this.waypoint = wpt;
        this.callback = callback;

        // name
        appendStringItem(Resources.getString(Resources.NAV_FLD_WPT_NAME), wpt.getName());

        // comment
        appendStringItem(Resources.getString(Resources.NAV_FLD_WPT_CMT), wpt.getComment());

        // timestamp
        if (wpt.getTimestamp() != null) {
            appendStringItem(Resources.getString(Resources.NAV_FLD_TIME), dateToString(wpt.getTimestamp().getTime()));
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
        if (modifiable) {
            addCommand(new ActionCommand(Resources.NAV_CMD_EDIT, Desktop.POSITIVE_CMD_TYPE, 5));
            addCommand(new ActionCommand(Resources.NAV_CMD_DELETE, Desktop.POSITIVE_CMD_TYPE, 6));
        }
    }

    /**
     * "Record Current" constructor.
     */
    public WaypointForm(Location location, Callback callback) {
        super(Resources.getString(Resources.NAV_TITLE_WPT));
        this.coordinates = location.getQualifiedCoordinates().clone(); // copy
        this.timestamp = location.getTimestamp();
        this.callback = callback;
        this.previewItemIdx = -1;

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
    public WaypointForm(Callback callback, QualifiedCoordinates pointer) {
        super(Resources.getString(Resources.NAV_TITLE_WPT));
        this.callback = callback;

        // generated name
        final StringBuffer sb = new StringBuffer(32);
        sb.append("WPT");
        NavigationScreens.append(sb, cnt + 1, 3);
/*
        appendWithNewlineAfter(this.fieldName = createTextField(Resources.NAV_FLD_WPT_NAME, sb.toString(), 128));

        // comment
        appendWithNewlineAfter(this.fieldComment = createTextField(Resources.NAV_FLD_WPT_CMT, dateToString(CALENDAR.getTime().getTime()), 256));

        // coordinates
        final String labelX, labelY;
*/
/*
        if (Config.useGridFormat && NavigationScreens.isGrid()) {

            // labels
            labelX = Resources.getString(Resources.NAV_FLD_EASTING);
            labelY = Resources.getString(Resources.NAV_FLD_NORTHING);

            // zone
            final QualifiedCoordinates localQc = Datum.contextDatum.toLocal(pointer);
            final CartesianCoordinates cc = Mercator.LLtoGrid(pointer);
            if (cc.zone != null) {
                appendWithNewlineAfter(this.fieldZone = createTextField(Resources.NAV_FLD_ZONE, new String(cc.zone), 3));
            }
            CartesianCoordinates.releaseInstance(cc);
            QualifiedCoordinates.releaseInstance(localQc);

        } else if (Config.useUTM) {

            // labels
            labelX = Resources.getString(Resources.NAV_FLD_UTM_EASTING);
            labelY = Resources.getString(Resources.NAV_FLD_UTM_NORTHING);

            // zone
            final CartesianCoordinates cc = Mercator.LLtoUTM(pointer);
            appendWithNewlineAfter(this.fieldZone = createTextField(Resources.NAV_FLD_ZONE, new String(cc.zone), 3));
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
*/
/*
        switch (Config.cfmt) {
            case Config.COORDS_MAP_GRID: {
                if (NavigationScreens.isGrid()) {
                    // labels
                    labelX = Resources.getString(Resources.NAV_FLD_EASTING);
                    labelY = Resources.getString(Resources.NAV_FLD_NORTHING);

                    // zone
                    final QualifiedCoordinates localQc = Datum.contextDatum.toLocal(pointer);
                    final CartesianCoordinates cc = Mercator.LLtoGrid(pointer);
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
                final CartesianCoordinates cc = Mercator.LLtoUTM(pointer);
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
        sb.delete(0, sb.length());
        NavigationScreens.printTo(sb, pointer, QualifiedCoordinates.LAT);
        appendWithNewlineAfter(this.fieldLat = new TextField(labelX, sb.toString(), 13, TextField.ANY));

        // lon/northing
        sb.delete(0, sb.length());
        NavigationScreens.printTo(sb, pointer, QualifiedCoordinates.LON);
        appendWithNewlineAfter(this.fieldLon = new TextField(labelY, sb.toString(), 14, TextField.ANY));

        // altitude
        appendWithNewlineAfter(this.fieldAlt = createTextField(Resources.NAV_FLD_ALT, "", 4, TextField.NUMERIC));
*/
        // share
        populateEditableForm(sb.toString(), dateToString(CALENDAR.getTime().getTime()), pointer);

        // commands
        addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Command.BACK, 1));
        addCommand(new ActionCommand(Resources.NAV_CMD_ADD, Desktop.POSITIVE_CMD_TYPE, 1));
    }

    /**
     * Editing constructor.
     */
    public WaypointForm(Callback callback, Waypoint wpt) {
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
    public WaypointForm(Callback callback, String type,
                        QualifiedCoordinates coordinates) {
        super("SMS");
        this.callback = callback;
        this.closure = type;

        // receiver number and message
        append(this.fieldNumber = createTextField(Resources.NAV_FLD_RECIPIENT, null, 16, TextField.PHONENUMBER));
        append(this.fieldMessage = createTextField(Resources.NAV_FLD_MESSAGE, null, 64));

        // coordinates
        final StringBuffer sb = new StringBuffer(32);
        NavigationScreens.printTo(coordinates, sb);
        append(new StringItem(Resources.getString(Resources.NAV_FLD_LOC), sb.toString()));

        // commands
        addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Command.BACK, 1));
        addCommand(new ActionCommand(Resources.NAV_CMD_SEND, Desktop.POSITIVE_CMD_TYPE, 1));
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
/*
        if (Config.useGridFormat && NavigationScreens.isGrid()) {

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

        } else if (Config.useUTM) {

            // labels
            labelX = Resources.getString(Resources.NAV_FLD_UTM_EASTING);
            labelY = Resources.getString(Resources.NAV_FLD_UTM_NORTHING);

            // zone
            final CartesianCoordinates cc = Mercator.LLtoUTM(qc);
            appendWithNewlineAfter(this.fieldZone = createTextField(Resources.NAV_FLD_ZONE, new String(cc.zone), 3));
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
*/
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
        NavigationScreens.printTo(sb, qc, QualifiedCoordinates.LAT);
        appendWithNewlineAfter(this.fieldLat = new TextField(labelX, sb.toString(), 13, TextField.ANY));
        latHash = fieldLat.getString().trim().hashCode();

        // lon/northing
        sb.delete(0, sb.length());
        NavigationScreens.printTo(sb, qc, QualifiedCoordinates.LON);
        appendWithNewlineAfter(this.fieldLon = new TextField(labelY, sb.toString(), 14, TextField.ANY));
        lonHash = fieldLon.getString().trim().hashCode();

        // altitude
        appendWithNewlineAfter(this.fieldAlt = createTextField(Resources.NAV_FLD_ALT,
                                                               Float.isNaN(qc.getAlt()) ? "?" : Integer.toString((int) qc.getAlt()),
                                                               4));
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

    public WaypointForm show() {
        // command handling
        setCommandListener(this);

        // show
        Desktop.display.setCurrent(this);

        return this;
    }

    public void commandAction(Command command, Item item) {
        if (CMD_TAKE.equals(command.getLabel())) {
            try {
                Camera.take(this, this, tracklogTime);
            } catch (Throwable t) {
                Desktop.showError(Resources.getString(Resources.NAV_MSG_CAMERA_FAILED), t, this);
            }
        } else if (CMD_HINT.equals(command.getLabel())) {
            removeCommand(command);
            delete(hintNum);
            hintNum = appendStringItem(Resources.getString(Resources.NAV_FLD_GS_HINT), ((GroundspeakBean) waypoint.getUserObject()).getEncodedHints());
            Desktop.display.setCurrentItem(get(hintNum));
        }
    }

    public void invoke(Object result, Throwable throwable, Object source) {
        if (result instanceof String) { // JSR-234 and new JSR-135 capture snapshot path
            imagePath = (String) result;
            if (previewItemIdx == -1) {
                previewItemIdx = append(Resources.getString(Resources.NAV_MSG_NO_PREVIEW));
            }
            Desktop.showInfo(Resources.getString(Resources.NAV_MSG_DO_NOT_WORRY), this);
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
                        if (touched()) {
                            wpt.setQualifiedCoordinates(parseCoordinates());
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
                    Desktop.showWarning("Unknown wpt action: " + action, null, null);
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
/*
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
*/
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

        // get altitude
        final String altStr = fieldAlt.getString();
        if (altStr != null && altStr.length() > 0 && !"?".equals(altStr)) {
            qc.setAlt(Float.parseFloat(altStr));
        }

        return qc;
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
