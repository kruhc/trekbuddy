// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.Resources;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.event.Callback;
import cz.kruch.track.fun.Camera;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.location.GroundspeakBean;
import cz.kruch.track.location.ExtWaypoint;
import cz.kruch.track.location.StampedWaypoint;
import cz.kruch.track.util.Mercator;

import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.Vector;
import java.util.Hashtable;
import java.io.IOException;

import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemCommandListener;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.Screen;
import javax.microedition.lcdui.ItemStateListener;
import javax.microedition.lcdui.Spacer;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.ImageItem;

import api.location.Location;
import api.location.QualifiedCoordinates;
import api.location.CartesianCoordinates;
import api.location.Datum;

/**
 * Form for waypoints.
 *
 * @author kruhc@seznam.cz
 */
final class WaypointForm implements CommandListener, ItemCommandListener, ItemStateListener, Callback {

    private static final Calendar CALENDAR = Calendar.getInstance(TimeZone.getDefault());
    private static final String VALUE_SEE_MORE = " >> ";

    private final Callback callback;
    private QualifiedCoordinates coordinates;
    private Waypoint waypoint;
    private long timestamp, tracklogTime;

    private Form form;
    private List list;
    private TextField fieldName, fieldComment;
    private TextField fieldZone, fieldLat, fieldLon, fieldAlt;
    private TextField fieldCourse, fieldDistance;
    private TextField fieldNumber, fieldMessage;
    private Item viewResult;
    private Object closure;

    private String CMD_TAKE;
    private String CMD_HINT;

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
    public WaypointForm(final Callback callback, final Waypoint wpt, final float distance,
                        final boolean modifiable, final boolean cache) {
        this.callback = callback;
        this.waypoint = wpt;
        this.form = new Form(Resources.getString(Resources.NAV_TITLE_WPT));

        // name
        appendStringItem(Resources.getString(Resources.NAV_FLD_WPT_NAME), wpt.getName());

        // comment
        appendStringItem(Resources.getString(Resources.NAV_FLD_WPT_CMT), wpt.getComment());

        // timestamp
        if (wpt.getTimestamp() != 0) {
            appendStringItem(Resources.getString(Resources.NAV_FLD_TIME), dateToString(wpt.getTimestamp()));
        }

        // lat+lon
        final StringBuffer sb = new StringBuffer(32);
        NavigationScreens.printTo(wpt.getQualifiedCoordinates(), sb);
        appendStringItem(Resources.getString(Resources.NAV_FLD_LOC), sb.toString());

        // altitude
        final float alt = wpt.getQualifiedCoordinates().getAlt();
        if (!Float.isNaN(alt)) {
            sb.delete(0, sb.length());
            NavigationScreens.printAltitude(sb, alt);
            appendStringItem(Resources.getString(Resources.NAV_FLD_ALT), sb.toString());
        }

        // distance
        sb.delete(0, sb.length());
        NavigationScreens.printDistance(sb, distance);
        appendStringItem(Resources.getString(Resources.NAV_FLD_DISTANCE), sb.toString());

        // extra commands
        boolean showLongListing = false, showLogs = false, showHints = false;

        // Groundspeak
        if (wpt.getUserObject() instanceof GroundspeakBean) {
            CMD_HINT = Resources.getString(Resources.NAV_CMD_SHOW);
            final int LINK_TYPE = Desktop.screen.hasPointerEvents() ? Item.BUTTON : Item.HYPERLINK;
            final GroundspeakBean bean = (GroundspeakBean) wpt.getUserObject();
            appendStringItem("GC " + Resources.getString(Resources.NAV_FLD_WPT_NAME), bean.getName());
            final String id = bean.getId();
            if (id != null && id.length() != 0) {
                appendStringItem(Resources.getString(Resources.NAV_FLD_GS_ID), id);
            }
            appendStringItem(Resources.getString(Resources.NAV_FLD_GS_CLASS), bean.classify());
            final String shortListing = bean.getShortListing();
            if (shortListing != null && shortListing.length() != 0) {
                appendStringItem(Resources.getString(Resources.NAV_FLD_GS_LISTING_SHORT),
                                 convertHtmlSnippet(shortListing));
            }
            final String longListing = bean.getLongListing();
            showLongListing = longListing != null && longListing.length() != 0;
            if (showLongListing) {
                final String related = Resources.getString(Resources.NAV_FLD_GS_LISTING_LONG);
                final int idx = appendStringItem(related, VALUE_SEE_MORE, LINK_TYPE);
                if (!Config.uiNoItemCommands) {
                    addHintCommand(form.get(idx), Resources.NAV_FLD_GS_LISTING_LONG);
                }
            }
            final Vector logs = bean.getLogs();
            showLogs = logs != null && logs.size() != 0;
            if (showLogs) {
                final String related = Resources.getString(Resources.NAV_FLD_GS_LOGS);
                final int idx = appendStringItem(related, VALUE_SEE_MORE, LINK_TYPE);
                if (!Config.uiNoItemCommands) {
                    addHintCommand(form.get(idx), Resources.NAV_FLD_GS_LOGS);
                }
            }
            final String encodedHints = bean.getEncodedHints();
            showHints = encodedHints != null && encodedHints.length() != 0;
            if (showHints) {
                final String related = Resources.getString(Resources.NAV_FLD_GS_HINT);
                hintNum = appendStringItem(related, VALUE_SEE_MORE, LINK_TYPE);
                if (!Config.uiNoItemCommands) {
                    addHintCommand(form.get(hintNum), Resources.NAV_FLD_GS_HINT);
                }
            }
        }

        // form command
        final int POSITIVE_CMD_TYPE = Desktop.POSITIVE_CMD_TYPE;
        form.addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Desktop.BACK_CMD_TYPE, 1));
        form.addCommand(new ActionCommand(Resources.NAV_CMD_NAVIGATE_TO, POSITIVE_CMD_TYPE, 1));
        form.addCommand(new ActionCommand(Resources.NAV_CMD_ROUTE_ALONG, POSITIVE_CMD_TYPE, 2));
        form.addCommand(new ActionCommand(Resources.NAV_CMD_ROUTE_BACK, POSITIVE_CMD_TYPE, 3));
        form.addCommand(new ActionCommand(Resources.NAV_CMD_GO_TO, POSITIVE_CMD_TYPE, 4));
        if (modifiable) {
            form.addCommand(new ActionCommand(Resources.NAV_CMD_EDIT, POSITIVE_CMD_TYPE, 5));
            form.addCommand(new ActionCommand(Resources.NAV_CMD_DELETE, POSITIVE_CMD_TYPE, 6));
        }
        if (cache) {
            form.addCommand(new ActionCommand(Resources.NAV_CMD_NEW_NOTE, POSITIVE_CMD_TYPE, 7));
        }
        if (Config.uiNoItemCommands) {
            if (showLongListing) {
                form.addCommand(new ActionCommand(Resources.NAV_FLD_GS_LISTING_LONG, POSITIVE_CMD_TYPE, 8, CMD_HINT));
            }
            if (showLogs) {
                form.addCommand(new ActionCommand(Resources.NAV_FLD_GS_LOGS, POSITIVE_CMD_TYPE, 9, CMD_HINT));
            }
            if (showHints) {
                form.addCommand(new ActionCommand(Resources.NAV_FLD_GS_HINT, POSITIVE_CMD_TYPE, 10, CMD_HINT));
            }
        }
    }

    /**
     * "Record Current" constructor.
     *
     * @param location location
     * @param callback callback
     */
    public WaypointForm(final Callback callback, final Location location) {
        this.callback = callback;
        this.coordinates = location.getQualifiedCoordinates()._clone(); // copy
        this.timestamp = location.getTimestamp();
        this.form = new Form(Resources.getString(Resources.NAV_TITLE_WPT)); // TODO use context NAV_ITEM_RECORD_CURRENT
        this.previewItemIdx = -1;

        // name
        appendWithNewlineAfter(this.fieldName = createTextField(Resources.NAV_FLD_WPT_NAME, null, 128));

        // comment
        appendWithNewlineAfter(this.fieldComment = createTextField(Resources.NAV_FLD_WPT_CMT, null, 256));

        // timestamp
        appendWithNewlineAfter(Resources.getString(Resources.NAV_FLD_TIME), dateToString(location.getTimestamp()));

        // coords
        final StringBuffer sb = new StringBuffer(32);
        NavigationScreens.printTo(location.getQualifiedCoordinates(), sb);
        appendWithNewlineAfter(Resources.getString(Resources.NAV_FLD_LOC), sb.toString());

        // altitude
        sb.delete(0, sb.length());
        NavigationScreens.printAltitude(sb, location.getQualifiedCoordinates().getAlt());
        appendWithNewlineAfter(Resources.getString(Resources.NAV_FLD_ALT), sb.toString());

        // form commands
        if (cz.kruch.track.TrackingMIDlet.supportsVideoCapture()) {
            CMD_TAKE = Resources.getString(Resources.NAV_CMD_TAKE);
            final StringItem snapshot = new StringItem(Resources.getString(Resources.NAV_FLD_SNAPSHOT), CMD_TAKE, Item.BUTTON);
            snapshot.setFont(Desktop.fontStringItems);
            snapshot.setDefaultCommand(new Command(CMD_TAKE, Command.ITEM, 1));
            snapshot.setItemCommandListener(this);
            appendWithNewlineAfter(snapshot);
            form.append(new Spacer(form.getWidth(), 1));
        }
        form.addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Desktop.CANCEL_CMD_TYPE, 1));
        form.addCommand(new ActionCommand(Resources.NAV_ITEM_RECORD_CURRENT, Desktop.POSITIVE_CMD_TYPE, 1, Resources.CMD_OK));
    }

    /**
     * "Enter Custom", "Project New" constructor.
     *
     * @param callback callback
     * @param pointer current position
     */
    public WaypointForm(final Callback callback, final QualifiedCoordinates pointer,
                        final int mode) {
        this.callback = callback;
        this.coordinates = pointer._clone(); // copy
        this.form = new Form(Resources.getString(Resources.NAV_TITLE_WPT)); // TODO use context 'mode'

        // generated name
        final StringBuffer sb = new StringBuffer(16);
        sb.append("WPT");
        NavigationScreens.append(sb, cnt + 1, 3);

        // shared
        if (mode == Resources.NAV_ITEM_ENTER_CUSTOM) {
            populateEditableForm(sb.toString(), "", pointer, true);
        } else if (mode == Resources.NAV_ITEM_PROJECT_NEW) {
            populateEditableForm(sb.toString(), "", pointer, false);
            extendEditableForm();
        }

        // commands
        form.addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Desktop.CANCEL_CMD_TYPE, 1));
        form.addCommand(new ActionCommand(mode, Desktop.POSITIVE_CMD_TYPE, 1, Resources.CMD_OK));
    }

    /**
     * Editing constructor.
     *
     * @param callback callback
     * @param wpt waypoint
     */
    public WaypointForm(final Callback callback, final Waypoint wpt) {
        this.callback = callback;
        this.waypoint = wpt;
        this.form = new Form(Resources.getString(Resources.NAV_TITLE_WPT)); // TODO use context NAV_CMD_EDIT

        // populate form
        populateEditableForm(wpt.getName(), wpt.getComment(), wpt.getQualifiedCoordinates(), true);

        // commands
        form.addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Desktop.CANCEL_CMD_TYPE, 1));
        form.addCommand(new ActionCommand(Resources.NAV_CMD_UPDATE, Desktop.POSITIVE_CMD_TYPE, 1, Resources.CMD_OK));
    }

    /**
     * Send SMS constructor.
     *
     * @param callback callback
     * @param type SMS type
     * @param coordinates coordinates
     */
    public WaypointForm(final Callback callback, final String type,
                        final QualifiedCoordinates coordinates) {
        this.callback = callback;
        this.closure = type;
        this.form = new Form("SMS");

        // receiver number and message
        form.append(this.fieldNumber = createTextField(Resources.NAV_FLD_RECIPIENT, null, 16, TextField.PHONENUMBER));
        form.append(this.fieldMessage = createTextField(Resources.NAV_FLD_MESSAGE, null, 64));

        // coordinates
        final StringBuffer sb = new StringBuffer(32);
        NavigationScreens.printTo(coordinates, sb);
        form.append(new StringItem(Resources.getString(Resources.NAV_FLD_LOC) + ": ", sb.toString()));

        // commands
        form.addCommand(new Command(Resources.getString(Resources.CMD_CANCEL), Desktop.CANCEL_CMD_TYPE, 1));
        form.addCommand(new ActionCommand(Resources.NAV_CMD_SEND, Desktop.POSITIVE_CMD_TYPE, 1));
    }

    public void setTracklogTime(long tracklogTime) {
        this.tracklogTime = tracklogTime;
    }

    private Command addHintCommand(final Item item, final int action) {
        final ActionCommand command = new ActionCommand(action, Command.ITEM, 1, CMD_HINT);
        item.setDefaultCommand(command);
        item.setItemCommandListener(this);
        return command;
    }

    private void populateEditableForm(final String name, final String comment,
                                      final QualifiedCoordinates qc, final boolean standalone) {
        // name
        appendWithNewlineAfter(this.fieldName = createTextField(Resources.NAV_FLD_WPT_NAME, name, 128));

        // comment
        appendWithNewlineAfter(this.fieldComment = createTextField(Resources.NAV_FLD_WPT_CMT, comment, 256));

        // coordinates
        final short labelXidx, labelYidx;
        switch (Config.cfmt) {
            case Config.COORDS_MAP_GRID: {
                if (NavigationScreens.isGrid()) {
                    // labels
                    labelXidx = Resources.NAV_FLD_EASTING;
                    labelYidx = Resources.NAV_FLD_NORTHING;

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
                labelXidx = Resources.NAV_FLD_UTM_EASTING;
                labelYidx = Resources.NAV_FLD_UTM_NORTHING;

                // zone
                final CartesianCoordinates cc = Mercator.LLtoUTM(qc);
                appendWithNewlineAfter(this.fieldZone = createTextField(Resources.NAV_FLD_ZONE, new String(cc.zone), 3));
                CartesianCoordinates.releaseInstance(cc);
            } break;
            case Config.COORDS_GC_LATLON: {
                labelXidx = Resources.NAV_FLD_WGS84LAT;
                labelYidx = Resources.NAV_FLD_WGS84LON;
            } break;
            default: {
                labelXidx = Resources.NAV_FLD_LAT;
                labelYidx = Resources.NAV_FLD_LON;
            }
        }
        final String labelX = Resources.getString(Resources.NAV_FLD_SOURCE) + " " + Resources.getString(labelXidx);
        final String labelY = Resources.getString(Resources.NAV_FLD_SOURCE) + " " + Resources.getString(labelYidx);

        // lat/easting
        final StringBuffer sb = new StringBuffer(32);
        NavigationScreens.printTo(sb, qc, QualifiedCoordinates.LAT, true);
        appendWithNewlineAfter(this.fieldLat = createTextField(labelX, sb.toString(), 13, TextField.ANY));
        latHash = fieldLat.getString().trim().hashCode();

        // lon/northing
        sb.delete(0, sb.length());
        NavigationScreens.printTo(sb, qc, QualifiedCoordinates.LON, true);
        appendWithNewlineAfter(this.fieldLon = createTextField(labelY, sb.toString(), 14, TextField.ANY));
        lonHash = fieldLon.getString().trim().hashCode();

        // no more items
        if (standalone) {

            // altitude
            sb.delete(0, sb.length());
            NavigationScreens.printAltitude(sb, qc.getAlt());
            appendWithNewlineAfter(this.fieldAlt = createTextField(Resources.NAV_FLD_ALT, sb.toString(), 5));

            // spacer
            form.append(new Spacer(Desktop.width >> 1, Desktop.height / 20));
        }
    }

    private void extendEditableForm() {
        // course
        appendWithNewlineAfter(this.fieldCourse = createTextField(Resources.NAV_FLD_COURSE, "", 4, TextField.NUMERIC));

        // distance
        StringBuffer sb = new StringBuffer(16);
        sb.append(Resources.getString(Resources.NAV_FLD_DISTANCE));
        switch (Config.units) {
            case Config.UNITS_METRIC: {
                sb.append(" (m)");
            } break;
            case Config.UNITS_IMPERIAL:
            case Config.UNITS_NAUTICAL: {
                sb.append(" (ft)");
            } break;
        }
        appendWithNewlineAfter(this.fieldDistance = createTextField(sb.toString(), "", 7, TextField.NUMERIC));

        // projected coords
        viewResult = form.get(appendStringItem(Resources.getString(Resources.NAV_FLD_TARGET), ""));

        // spacer
        form.append(new Spacer(Desktop.width >> 1, Desktop.height / 20));
        
        // interactive update
        form.setItemStateListener(this);
    }

    private int appendWithNewlineAfter(final String label, final String text) {
        return appendStringItem(label, text, Item.PLAIN);
    }

    private int appendStringItem(final String label, final String text) {
        if (text != null) {
            return appendStringItem(label, text, Item.PLAIN);
        }
        return -1;
    }

    private int appendStringItem(final String label, final String text, final int appearance) {
        final StringItem item = new StringItem(label + ": ", text, appearance);
        item.setFont(Desktop.fontStringItems);
        return appendWithNewlineAfter(item);
    }

    private int appendWithNewlineAfter(Item item) {
        item.setLayout(Item.LAYOUT_2 | Item.LAYOUT_SHRINK | Item.LAYOUT_NEWLINE_AFTER);
        return form.append(item);
    }

    public void invoke(Object result, Throwable throwable, Object source) {
        if (source instanceof cz.kruch.track.fun.Camera) { // JSR-234 and new JSR-135 capture snapshot path
            if (result instanceof String) {
                imagePath = (String) result;
                final Object thumbnail = Camera.getThumbnail(Config.getFolderURL(Config.FOLDER_WPTS) + imagePath);
                final Item item;
                if (thumbnail instanceof Image) {
                    item = new ImageItem(null, (Image) thumbnail, ImageItem.LAYOUT_DEFAULT, null);
//                } else if (thumbnail instanceof Throwable) {
//                    previewItemIdx = form.append(thumbnail.toString());
                } else {
                    item = new StringItem(null, Resources.getString(Resources.NAV_MSG_NO_PREVIEW));
                }
                if (previewItemIdx == -1) {
                    previewItemIdx = form.append(item);
                } else {
                    form.insert(previewItemIdx, item);
                }
            } else if (throwable != null) {
                Desktop.showError(Resources.getString(Resources.NAV_MSG_SNAPSHOT_FAILED), throwable, form);
            } else {
                Desktop.showError(Resources.getString(Resources.NAV_MSG_NO_SNAPSHOT), null, form);
            }
        } else if (source instanceof FieldNoteForm) {
            callback.invoke(result, throwable, source);
        }
    }

    public WaypointForm show() {
        // command handling
        form.setCommandListener(this);

        // show
        Desktop.display.setCurrent(form);

        return this;
    }

    public void commandAction(Command command, Item item) {
        final String cmd = command.getLabel();
        if (cmd.equals(CMD_TAKE)) {
            try {
                Camera.show(form, this, tracklogTime);
            } catch (Throwable t) {
                Desktop.showError(Resources.getString(Resources.NAV_MSG_CAMERA_FAILED), t, form);
            }
        } else if (command instanceof ActionCommand) {
            final int action = ((ActionCommand) command).getAction();
            final String label = Resources.getString((short) action);
            final GroundspeakBean bean = ((GroundspeakBean) waypoint.getUserObject());
            switch (action) {
                case Resources.NAV_FLD_GS_LISTING_LONG: {
                    final String text = convertHtmlSnippet(bean.getLongListing());
                    final Screen box;
//#ifdef __SYMBIAN__
                    if (cz.kruch.track.TrackingMIDlet.uiq) {
                        box = new javax.microedition.lcdui.TextBox(label, text, text.length(), TextField.UNEDITABLE);
                    } else
//#endif
                    {
                        box = new Form(label);
                        final StringItem content = new StringItem(null, text);
                        content.setFont(Desktop.fontStringItems);
                        ((Form) box).append(content);
                    }
                    box.addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Desktop.BACK_CMD_TYPE, 1));
                    box.setCommandListener(this);
                    Desktop.display.setCurrent(box);
                } break;
                case Resources.NAV_FLD_GS_LOGS: {
                    final Vector logs = bean.getLogs();
                    final List l = list = new List(label, List.IMPLICIT);
                    l.setFitPolicy(Choice.TEXT_WRAP_OFF);
                    for (int N = logs.size(), i = 0; i < N; i++) {
                        final GroundspeakBean.Log entry = (GroundspeakBean.Log) logs.elementAt(i);
                        l.setFont(l.append(entry.toString(), entry.getIcon()), Desktop.fontStringItems);
                    }
                    l.addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Desktop.BACK_CMD_TYPE, 1));
                    l.setCommandListener(this);
                    Desktop.display.setCurrent(l);
                } break;
                case Resources.NAV_FLD_GS_HINT: {
                    delete(hintNum);
                    hintNum = appendStringItem(Resources.getString(Resources.NAV_FLD_GS_HINT), bean.getEncodedHints());
                    Desktop.display.setCurrentItem(form.get(hintNum));
                } break;
            }
        }
    }

    public void commandAction(Command command, Displayable displayable) {
        if (displayable == form) {
            if (Desktop.POSITIVE_CMD_TYPE == command.getCommandType()) {
                final int action = ((ActionCommand) command).getAction();
                final Integer actionObject = new Integer(action);
                switch (action) {
                    case Resources.NAV_CMD_ROUTE_ALONG:
                    case Resources.NAV_CMD_ROUTE_BACK:
                    case Resources.NAV_CMD_NAVIGATE_TO:
                    case Resources.NAV_CMD_GO_TO: {
                        safeCallbackInvoke(callback, new Object[]{ actionObject, null });
                    } break;
                    case Resources.NAV_CMD_SEND: {
                        final Object[] params = new Object[]{
                                actionObject,
                                fieldNumber.getString(),
                                fieldMessage.getString(),
                                closure
                        };
                        safeCallbackInvoke(callback, params);
                    } break;
                    case Resources.NAV_CMD_EDIT: {
                        (new WaypointForm(callback, waypoint)).show();
                    } break;
                    case Resources.NAV_CMD_UPDATE: {
                        try {
                            final Waypoint wpt = waypoint;
                            if (touched()) { // full update
                                wpt.setQualifiedCoordinates(parseCoordinates());
                            } else { // partial update
                                parseAlt(wpt.getQualifiedCoordinates());
                            }
                            wpt.setName(trimToNull(fieldName.getString()));
                            wpt.setComment(trimToNull(fieldComment.getString()));
                            safeCallbackInvoke(callback, new Object[]{ actionObject, wpt });
                        } catch (Exception e) { // usually input data format - parsing error
                            Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_INVALID_INPUT), e, null);
                        }
                    } break;
                    case Resources.NAV_CMD_DELETE: {
                        safeCallbackInvoke(callback, new Object[]{ actionObject, waypoint });
                    } break;
                    case Resources.NAV_CMD_NEW_NOTE: {
                        (new FieldNoteForm(waypoint, displayable, this)).show();
                    } break;
                    case Resources.NAV_ITEM_RECORD_CURRENT: {
                        final ExtWaypoint wpt = new ExtWaypoint(coordinates,
                                                                trimToNull(fieldName.getString()),
                                                                trimToNull(fieldComment.getString()),
                                                                timestamp);
                        if (imagePath != null) {
                            wpt.addLink(imagePath);
                        }
                        safeCallbackInvoke(callback, new Object[]{ actionObject, wpt });
                    } break;
                    case Resources.NAV_ITEM_ENTER_CUSTOM: {
                        try {
                            final Waypoint wpt = new StampedWaypoint(parseCoordinates(),
                                                                     trimToNull(fieldName.getString()),
                                                                     trimToNull(fieldComment.getString()),
                                                                     System.currentTimeMillis());
                            cnt++;
                            safeCallbackInvoke(callback, new Object[]{ actionObject, wpt });
                        } catch (Exception e) { // usually input data format - parsing error
                            Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_INVALID_INPUT), e, null);
                        }
                    } break;
                    case Resources.NAV_ITEM_PROJECT_NEW: {
                        try {
                            final QualifiedCoordinates qc = calcProjected();
                            final Waypoint wpt = new StampedWaypoint(qc,
                                                                     trimToNull(fieldName.getString()),
                                                                     trimToNull(fieldComment.getString()),
                                                                     System.currentTimeMillis());
                            cnt++;
                            safeCallbackInvoke(callback, new Object[]{ actionObject, wpt });
                        } catch (Exception e) { // usually input data format - parsing error
                            Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_INVALID_INPUT), e, null);
                        }
                    } break;
                    case Resources.NAV_FLD_GS_LISTING_LONG:
                    case Resources.NAV_FLD_GS_HINT:
                    case Resources.NAV_FLD_GS_LOGS: {
                        commandAction(command, (Item)null);
                    } break;
                    default:
                        Desktop.showWarning("Internal error", new IllegalStateException("Unknown waypoint action: " + action), null);
                }
            } else {
                // dummy invocation
                callback.invoke(new Object[]{ null, null }, null, this);
            }
        } else if (displayable == list) {
            if (List.SELECT_COMMAND == command) {
                // get selected log
                GroundspeakBean.Log gclog = (GroundspeakBean.Log) ((GroundspeakBean) waypoint.getUserObject()).getLogs().elementAt(((List) displayable).getSelectedIndex());
                // hack: save main form member and misuse appendStringItem method
                final Form main = form;
                // fill form
                final Form box = form = new Form(Resources.getString(Resources.NAV_FLD_GS_LOG));
                appendStringItem(Resources.getString(Resources.NAV_FLD_DATE), gclog.getDate());
                appendStringItem(Resources.getString(Resources.NAV_FLD_TYPE), gclog.getType());
                appendStringItem(Resources.getString(Resources.NAV_FLD_FINDER), gclog.getFinder());
                appendStringItem(Resources.getString(Resources.NAV_FLD_TEXT),
                                 convertHtmlSnippet(gclog.getText()));
                // hack: restore main form member
                form = main;
                // show gc log details
                box.addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Desktop.BACK_CMD_TYPE, 1));
                box.setCommandListener(this);
                Desktop.display.setCurrent(box);
            } else {
                // gc hint
                list = null;
                // restore main (wpt details) form
                Desktop.display.setCurrent(form);
            }
        } else if (list != null) { // log shown, command is BACK
            // restore logs listing
            Desktop.display.setCurrent(list);
        } else { // long listing shown, command is BACK
            // restore main (wpt details) form
            Desktop.display.setCurrent(form);
        }
    }

    public void itemStateChanged(Item item) {
        if (item == fieldCourse || item == fieldDistance || item == fieldLat || item == fieldLon) {
            try {
                final QualifiedCoordinates qc = calcProjected();
                final StringBuffer sb = new StringBuffer(32);
                NavigationScreens.printTo(qc, sb);
                ((StringItem) viewResult).setText(sb.toString());
            } catch (Exception e) {
                ((StringItem) viewResult).setText("<error>");
            }
        }
    }

    private void safeCallbackInvoke(final Callback callback, final Object[] params) {
        try {
            callback.invoke(params, null, this);
        } catch (IllegalArgumentException e) {
            Desktop.showWarning(null, e, null);
        }
    }

    private boolean touched() {
        return fieldLat.getString().trim().hashCode() != latHash || fieldLon.getString().trim().hashCode() != lonHash;
    }

    private void delete(final int idx) {
        if (idx != -1) {
            try {
                form.delete(idx);
            } catch (IndexOutOfBoundsException e) {
                // ignore
            }
        }
    }

    private QualifiedCoordinates calcProjected() {
        final QualifiedCoordinates qc;
        if (touched()) {
            qc = parseCoordinates();
        } else {
            qc = coordinates;
        }
        final int bearing = Integer.parseInt(trimToNull(fieldCourse.getString()));
        float distance = Integer.parseInt(trimToNull(fieldDistance.getString()));
        switch (Config.units) {
            case Config.UNITS_IMPERIAL:
            case Config.UNITS_NAUTICAL: {
                distance *= 0.3048F;
            } break;
        }

        return QualifiedCoordinates.project(qc, bearing, distance);
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
        if (fieldAlt != null) {
            String altStr = fieldAlt.getString().trim();
            if (altStr != null && altStr.length() > 0) {
                final int spaceIdx = altStr.indexOf(' ');
                if (spaceIdx > -1) {
                    altStr = altStr.substring(0, spaceIdx);
                }
                float alt = Float.parseFloat(altStr);
                switch (Config.units) {
                    case Config.UNITS_IMPERIAL:
                    case Config.UNITS_NAUTICAL: {
                        alt *= 0.3048F;
                    } break;
                }
                qc.setAlt(alt);
            }
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

    private static String trimToNull(String s) {
        s = s.trim();
        if (s.length() == 0) {
            s = null;
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
        return createTextField(Resources.getString(label), value, maxLength, type);
    }

    private static TextField createTextField(final String label,
                                             final String value,
                                             final int maxLength,
                                             final int type) {
        final TextField result;
        if (value != null && value.length() > maxLength) {
            result = new TextField(label, value.substring(0, maxLength), maxLength, type);
        } else {
            result = new TextField(label, value, maxLength, type);
        }
        return result;
    }

    public static String convertHtmlSnippet(String escapedHtmlSnippet) {
        //String unescapedSnippet = undoHtmlEscape(escapedHtmlSnippet); - this is unescaped from GPX parser :-)
        return (new HtmlSnippet2StringConvertor(escapedHtmlSnippet)).convert();
    }

    /**
     * Un-escapes &gt;, &lt;, and &amp;. Leaves all other escapes unchanged.
     *
     * @param escapedHtml     not null
     * @return a new string
     */
/*
    public static String undoHtmlEscape(String escapedHtml) {
        final int inLimit = escapedHtml.length();
        char[] inBuffer = new char[escapedHtml.length()];
        escapedHtml.getChars(0, inLimit, inBuffer, 0);
        StringBuffer result = new StringBuffer(inLimit);
        int lastSolved = -1;
        for (int position = 0; position < inLimit; position++) {
            if (inBuffer[position] == '&') {
                result.append(inBuffer, lastSolved + 1, position - lastSolved - 1);
                lastSolved = position - 1;
                if (position + 3 < inLimit) {
                    if (inBuffer[position + 1] == 'g' && inBuffer[position + 2] == 't' && inBuffer[position + 3] == ';') {
                        result.append('>');
                        position += 3;
                        lastSolved = position;
                    } else if (inBuffer[position + 1] == 'l' && inBuffer[position + 2] == 't'
                               && inBuffer[position + 3] == ';') {
                        result.append('<');
                        position += 3;
                        lastSolved = position;
                    }
                } else if (position + 4 < inLimit) {
                    if (inBuffer[position + 1] == 'a' && inBuffer[position + 2] == 'm'
                        && inBuffer[position + 3] == 'p' && inBuffer[position + 4] == ';') {
                        result.append('&');
                        position += 4;
                        lastSolved = position;
                    }
                }
            }
        }
        if (lastSolved + 1 < inLimit) {
            result.append(inBuffer, lastSolved + 1, inLimit - lastSolved - 1);
        }
        return result.toString();
    }
*/

    private static class HtmlSnippet2StringConvertor {

        private final char[] htmlBuffer;
        private final int inLimit;

        private int position;
        private int lastResolved;
        private StringBuffer outBuffer;
        private int bookmark;
        private int listNesting;

        HtmlSnippet2StringConvertor(String htmlSnippet) {
            this.inLimit = htmlSnippet.length();
            char[] input = new char[inLimit];
            htmlSnippet.getChars(0, inLimit, input, 0);
            htmlBuffer = input;
        }

        String convert() {
            position = 0;
            lastResolved = -1;
            outBuffer = new StringBuffer(inLimit);
            listNesting = 0;
            outputNewLine();
            goThroughChars();
            return outBuffer.toString();
        }

        private void goThroughChars() {
            for ( ; position < inLimit; position++) {
                switch (htmlBuffer[position]) {
                    case '\r':
                    case '\n':
                        resolveUpToPosition();
                        lastResolved++;  // convert to whitespace of ignore
                        ensureSpace();
                        break;
                    case '<':
                        processElement();
                        break;
                    case '&':
                        processEntityReference();
                        break;
                }

            }
            resolveUpToPosition();
        }

        private void processElement() {
            resolveUpToPosition();
            boolean endElement = position + 1 < inLimit && htmlBuffer[position + 1] == '/';
            skipToSpaceOrGt();  // could be \t on other whitespace?; check with spec
            int elementNameStart = bookmark + 1 + (endElement ? 1 : 0);
            String elementName = new String(htmlBuffer, elementNameStart, position - elementNameStart);
            processSelectedElemets(elementName, endElement);
            skipToGt();
        }

        private void processSelectedElemets(String elementName, boolean endElement) {
            if (isHeader(elementName)) {
                ensureEmptyLine();
            } else if (elementName.equals("p")) {
                ensureEmptyLine();
            } else if (elementName.equals("br") && !endElement) {
                if (! lastOutputWasEmptyLine()) {
                    outputNewLine();
                }
            } else if (elementName.equals("b") || elementName.equals("strong")) {
                outBuffer.append('*');
            } else if (elementName.equals("i") || elementName.equals("em")) {
                outBuffer.append('_');
            } else if (elementName.equals("img") && !endElement) {
                outputMetaElement(getAttributeValue("alt"), "IMAGE");
            } else if (elementName.equals("a") && !endElement) {
                outputMetaElement(getAttributeValue("href"), "LINK");
            } else if (elementName.equals("i") || elementName.equals("em")) {
                if (endElement) {
                    listNesting--;
                } else {
                    listNesting++;
                }
            } else if (elementName.equals("li") && !endElement) {
                ensureNewLine();
                for (int count = listNesting; count > 0; count--) {
                    outBuffer.append(" ");
                }
                outBuffer.append((char)183);
                outBuffer.append(' ');
            }
        }

        private void outputMetaElement(String imageAlt, String metaName) {
            if (imageAlt == null) {
                outBuffer.append('[');
                outBuffer.append(metaName);
                outBuffer.append(']');
            } else {
                outBuffer.append('[');
                outBuffer.append(metaName);
                outBuffer.append(": ");
                outBuffer.append(imageAlt);
                outBuffer.append("]");
            }
        }

        private boolean isHeader(String elementName) {
            return elementName.length() == 2
                && Character.toLowerCase(elementName.charAt(0)) == 'h'
                && Character.isDigit(elementName.charAt(1));
        }

        private String getAttributeValue(String attribute) {
            while (skipToCharOrChar('=', '>')) {        // we should ignore test inside '' and "" pairs; and whitespace
                if (htmlBuffer[position] == '>') {
                    break;
                }
                if (checkAttributeNameBefore(attribute)) {
                    skipToCharOrChar('\'', '"');
                    if (skipToChar(htmlBuffer[position])) {
                        return new String(htmlBuffer, bookmark + 1, position - bookmark - 1);   // positive search
                    }
                }
            }
            return null;    // not found
        }

        private boolean checkAttributeNameBefore(String attribute) {
            for (int checkPosition = position - 1, namePos = attribute.length() - 1; namePos >= 0; namePos--, checkPosition--) {
                if (checkPosition < 0) {
                    return false;
                }
                if (htmlBuffer[checkPosition] != attribute.charAt(namePos)) {
                    return false;
                }
            }
            return true;
        }

        private void ensureSpace() {
            if (! lastIsWhiteSpace()) {
                outBuffer.append(' ');
            }
        }

        private void ensureNewLine() {
            if (! lastOutputWasNewLine()) {
                outputNewLine();
            }
        }

        private void ensureEmptyLine() {
            if (! lastOutputWasEmptyLine()) {
                ensureNewLine();
                outputNewLine();
            }
        }

        private void outputNewLine() {
            outBuffer.append('\n');
        }

        private boolean lastOutputWasNewLine() {
            return outBuffer.length() == 0 ? false : outBuffer.charAt(outBuffer.length() - 1) == '\n';
        }

        private boolean lastOutputWasEmptyLine() {
            return lastOutputWasNewLine()
                && (outBuffer.length() == 1 || outBuffer.charAt(outBuffer.length() - 2) == '\n');
        }

        private boolean lastIsWhiteSpace() {
            if (outBuffer.length() == 0) {
                return true;        // really
            }
            char charToCheck = outBuffer.charAt(outBuffer.length() - 1);
            return charToCheck == ' ' || charToCheck == '\n' || charToCheck == '\t';
        }

        private void processEntityReference() {
            resolveUpToPosition();
            if (skipToSemicolon() && position - bookmark < MAGIC_LONGEST_KNOWN_ENTITY_NAME) {    // we know there is one ';' char ahead
                char translatedChar;
                if (htmlBuffer[bookmark + 1] == '#') {
                    translatedChar = translateCharacterEscape();
                } else {
                    translatedChar = translateEntity();
                }
                outBuffer.append(translatedChar);
            } else {
                outBuffer.append('&');
				position = bookmark + 1;
                lastResolved = bookmark;
            }
        }

        private void resolveUpToPosition() {
            if (lastResolved + 1 != position) {
                outBuffer.append(htmlBuffer, lastResolved + 1, position - lastResolved - 1);
                lastResolved = position - 1;
            }
        }

        private boolean skipToGt() {
            if (htmlBuffer[position] == '>') {
                return true;
            }
            return skipToChar('>');
        }

        private boolean skipToSemicolon() {
            return skipToWhitespaceOrChar(';');
        }

        private boolean skipToSpaceOrGt() {
            return skipToCharOrChar(' ', '>');
        }

        private boolean skipToCharOrChar(char ch1, char ch2) {
            bookmark = position;
            do {
                position++;
            } while (position < inLimit && htmlBuffer[position] != ch1 && htmlBuffer[position] != ch2);
            lastResolved = position;
            return position < inLimit && (htmlBuffer[position] == ch1 || htmlBuffer[position] == ch2);
        }

        private boolean skipToWhitespaceOrChar(char ch) {
            bookmark = position;
            do {
                position++;
            } while (position < inLimit && htmlBuffer[position] != ch
                     && htmlBuffer[position] != ' ' && htmlBuffer[position] != '\n' && htmlBuffer[position] != '\t');
            lastResolved = position;
            return position < inLimit && htmlBuffer[position] == ch;
        }

        private boolean skipToChar(char ch) {
            bookmark = position;
            do {
                position++;
            } while (position < inLimit && htmlBuffer[position] != ch);
            lastResolved = position;
            return position < inLimit && htmlBuffer[position] == ch;
        }

        private static final int MAGIC_LONGEST_KNOWN_ENTITY_NAME = 10;
        private static final Hashtable ENTITIES = createEntities();

        private static Hashtable createEntities() {
            Hashtable result = new Hashtable(193);
            result.put("lt", new Character('<'));
            result.put("gt", new Character('>'));
            result.put("quot", new Character('"'));
            result.put("amp", new Character('&'));
            result.put("apos", new Character('\''));
            result.put("nbsp", new Character(' '));  // IMHO better for smaller displays than real non-breaking space
            // add others from http://www.theukwebdesigncompany.com/articles/entity-escape-characters.php
            result.put("iexcl", new Character((char)161));
            result.put("cent", new Character((char)162));
            result.put("pound", new Character((char)163));
            result.put("curren", new Character((char)164));
            result.put("yen", new Character((char)165));
            result.put("brvbar", new Character((char)166));
            result.put("sect", new Character((char)167));
            result.put("uml", new Character((char)168));
            result.put("copy", new Character((char)169));
            result.put("ordf", new Character((char)170));
            result.put("not", new Character((char)172));
            result.put("shy", new Character((char)173));
            result.put("reg", new Character((char)174));
            result.put("macr", new Character((char)175));
            result.put("deg", new Character((char)176));
            result.put("plusmn", new Character((char)177));
            result.put("sup2", new Character((char)178));
            result.put("sup3", new Character((char)179));
            result.put("acute", new Character((char)180));
            result.put("micro", new Character((char)181));
            result.put("para", new Character((char)182));
            result.put("middot", new Character((char)183));
            result.put("cedil", new Character((char)184));
			result.put("sup1", new Character((char)185));
            result.put("ordm", new Character((char)186));
            result.put("raquo", new Character((char)187));
            result.put("frac14", new Character((char)188));
            result.put("frac12", new Character((char)189));
            result.put("frac34", new Character((char)190));
            result.put("iquest", new Character((char)191));
            result.put("Agrave", new Character((char)192));
            result.put("Aacute", new Character((char)193));
            result.put("Acirc", new Character((char)194));
            result.put("Atilde", new Character((char)195));
            result.put("Auml", new Character((char)196));
            result.put("Aring", new Character((char)197));
            result.put("AElig", new Character((char)198));
            result.put("Ccedil", new Character((char)199));
            result.put("Egrave", new Character((char)200));
            result.put("Eacute", new Character((char)201));
            result.put("Ecirc", new Character((char)202));
            result.put("Euml", new Character((char)203));
            result.put("Igrave", new Character((char)204));
            result.put("Iacute", new Character((char)205));
            result.put("Icirc", new Character((char)206));
            result.put("Iuml", new Character((char)207));
            result.put("ETH", new Character((char)208));
            result.put("Ntilde", new Character((char)209));
            result.put("Ograve", new Character((char)210));
            result.put("Oacute", new Character((char)211));
            result.put("Ocirc", new Character((char)212));
            result.put("Otilde", new Character((char)213));
            result.put("Ouml", new Character((char)214));
            result.put("times", new Character((char)215));
            result.put("Oslash", new Character((char)216));
            result.put("Ugrave", new Character((char)217));
            result.put("Uacute", new Character((char)218));
            result.put("Ucirc", new Character((char)219));
            result.put("Uuml", new Character((char)220));
            result.put("Yacute", new Character((char)221));
            result.put("THORN", new Character((char)222));
            result.put("szlig", new Character((char)223));
            result.put("agrave", new Character((char)224));
            result.put("aacute", new Character((char)225));
            result.put("acirc", new Character((char)226));
            result.put("atilde", new Character((char)227));
            result.put("auml", new Character((char)228));
            result.put("aring", new Character((char)229));
            result.put("aelig", new Character((char)230));
            result.put("ccedil", new Character((char)231));
            result.put("egrave", new Character((char)232));
            result.put("eacute", new Character((char)233));
            result.put("ecirc", new Character((char)234));
            result.put("euml", new Character((char)235));
            result.put("igrave", new Character((char)236));
            result.put("iacute", new Character((char)237));
            result.put("icirc", new Character((char)238));
            result.put("iuml", new Character((char)239));
            result.put("eth", new Character((char)240));
            result.put("ntilde", new Character((char)241));
            result.put("ograve", new Character((char)242));
            result.put("oacute", new Character((char)243));
            result.put("ocirc", new Character((char)244));
            result.put("otilde", new Character((char)245));
            result.put("ouml", new Character((char)246));
            result.put("divide", new Character((char)247));
            result.put("oslash", new Character((char)248));
            result.put("ugrave", new Character((char)249));
            result.put("uacute", new Character((char)250));
            result.put("ucirc", new Character((char)251));
            result.put("uuml", new Character((char)252));
            result.put("yacute", new Character((char)253));
            result.put("thorn", new Character((char)254));
            return result;
        }

        private char translateEntity() {
            String entityName = new String(htmlBuffer, bookmark + 1, position - bookmark - 1);
            Character resolvedChar = (Character) ENTITIES.get(entityName);
            if (resolvedChar == null) {
                return '?';
            } else {
                return resolvedChar.charValue();
            }
        }

        private char translateCharacterEscape() {
            boolean hexEscape = htmlBuffer[bookmark + 2] == 'x';
            int startIndex = hexEscape ? bookmark + 3 : bookmark + 2;
            String numberStr = new String(htmlBuffer, startIndex, position - startIndex);
            try {
                return (char) Integer.parseInt(numberStr, hexEscape ? 16 : MAGIC_LONGEST_KNOWN_ENTITY_NAME);
            } catch (NumberFormatException ignored) {
                return '?';
            }
        }

    }   // class HtmlSnippet2StringConvertor
}
