// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.event.Callback;
import cz.kruch.track.util.Logger;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.location.Navigator;
import cz.kruch.track.maps.io.LoaderIO;
import cz.kruch.track.fun.Friends;
import cz.kruch.j2se.io.BufferedInputStream;

import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Ticker;

import api.location.QualifiedCoordinates;

import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;

import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParser;
import org.kxml2.io.KXmlParser;

public final class Waypoints extends List
        implements CommandListener, Callback, Runnable {
//#ifdef __LOG__
    private static final Logger log = new Logger("Waypoints");
//#endif

    private static final String ITEM_BACK   = "Back";
    private static final String ITEM_SELECT = "Select";
    private static final String ITEM_CLOSE  = "Close";

    private static final String ITEM_ADD_NEW        = "Record Current";
    private static final String ITEM_SHOW_CURRENT   = "Show Destination";
    private static final String ITEM_ENTER_MANUALY  = "Enter Manually";
    private static final String ITEM_FRIEND_HERE    = "SMS 'I Am Here'";
    private static final String ITEM_FRIEND_THERE   = "SMS 'Meet You There'";
    private static final String ITEM_CLEAR_ALL      = "Clear All";
    private static final String ITEM_LIST_ALL       = "List";
    private static final String ITEM_LOAD           = "Load";

    private static final String ITEM_VIEW_DETAIL = "View";

    private static boolean initialized = false;

    private Navigator navigator;
    private int depth;

    private Command cmdSelect;
    private Command cmdCancel;

    public Waypoints(Navigator navigator) {
        super("Waypoints (?)", List.IMPLICIT);
        this.navigator = navigator;
        this.depth = 0;
        this.initialize();
        this.setTitle("Waypoints (" + navigator.getPath().length + ")");
    }

    /**
     * Loads in-jar waypoint(s).
     */
    private void initialize() {
        if (!initialized) {
            initialized = true;

            // do we have in-jar waypoint(s) resource?
            InputStream in = null;
            String type = "GPX";
            in = cz.kruch.track.TrackingMIDlet.class.getResourceAsStream("/resources/waypoints.gpx");
            if (in == null) {
                in = cz.kruch.track.TrackingMIDlet.class.getResourceAsStream("/resources/waypoint.loc");
                type = "LOC";
            }

            // if yes, load it
            if (in != null) {
                try {
                    navigator.setPath(parseWaypoints(in, type));
                } catch (Throwable t) {
                    Desktop.showError("Failed to load in-jar waypoint(s)", t, null);
                } finally {
                    try {
                        in.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
    }

    public void show() {
        // command handling
        setCommandListener(this);

        // let's start with basic menu
        menu();

        // show
        Desktop.display.setCurrent(this);
    }

    private void menu() {
        // clear list
        deleteAll();

        // create menu
        append(ITEM_ADD_NEW, null);
        append(ITEM_SHOW_CURRENT, null);
        if (cz.kruch.track.TrackingMIDlet.isJsr120()) {
            if (navigator.isTracking()) {
                append(ITEM_FRIEND_HERE, null);
            }
            append(ITEM_FRIEND_THERE, null);
        }
        append(ITEM_ENTER_MANUALY, null);
        append(ITEM_CLEAR_ALL, null);
        append(ITEM_LIST_ALL, null);
        append(ITEM_LOAD, null);

        // create commands
        removeCommand(cmdSelect);
        removeCommand(cmdCancel);
        cmdCancel = new Command(ITEM_CLOSE, Command.BACK, 1);
        cmdSelect = new Command(ITEM_SELECT, Command.SCREEN, 1);
        addCommand(cmdCancel);
        setSelectCommand(cmdSelect);
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command.getCommandType() == Command.BACK) {
            if (depth == 0) {
                Desktop.display.setCurrent((Displayable) navigator);
            } else {
                depth--;
                if (depth == 0) {
                    menu();
                }
            }
        } else if (depth == 0) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("item: " + getString(getSelectedIndex()));
//#endif
            // menu action
            String item = getString(getSelectedIndex());
            if (ITEM_ADD_NEW.equals(item)) {
                // only when tracking
                if (navigator.isTracking()) {
                    // and we have position
                    if (navigator.getLocation() != null) {
                        // open form
                        (new WaypointForm(this, navigator.getLocation(), this)).show();
                    } else {
                        Desktop.showInfo("No position yet", this);
                    }
                } else {
                    Desktop.showInfo("Not tracking", this);
                }
            } else if (ITEM_SHOW_CURRENT.equals(item)) {
                if (navigator.getPath().length == 0) {
                    Desktop.showInfo("No waypoints", this);
                } else if (navigator.getNavigateTo() == -1) {
                    Desktop.showInfo("No waypoint selected", this);
                } else {
                    (new WaypointForm(this, navigator.getPath()[navigator.getNavigateTo()])).show();
                }
            } else if (ITEM_ENTER_MANUALY.equals(item)) {
                QualifiedCoordinates pointer = navigator.getPointer();
                if (pointer == null) {
                    Desktop.showInfo("No position", this);
                } else {
                    (new WaypointForm(this, this, pointer)).show();
                }
            } else if (ITEM_FRIEND_HERE.equals(item)) {
                  // do we have position?
                  if (navigator.getLocation() == null) {
                      Desktop.showInfo("No position yet", this);
                  } else {
                      (new FriendForm(this, ITEM_FRIEND_HERE, navigator.getLocation().getQualifiedCoordinates(), this, ITEM_FRIEND_HERE)).show();
                  }
            } else if (ITEM_FRIEND_THERE.equals(item)) {
                QualifiedCoordinates pointer = navigator.getPointer();
                if (pointer == null) {
                    Desktop.showInfo("No position", this);
                } else {
                    (new FriendForm(this, ITEM_FRIEND_THERE, pointer, this, ITEM_FRIEND_THERE)).show();
                }
            } else if (ITEM_CLEAR_ALL.equals(item)) {
                (new YesNoDialog(this, new YesNoDialog.AnswerListener() {
                    public void response(int answer) {
                        if (answer == YesNoDialog.YES) {
                            actionClearAll();
                            Desktop.showConfirmation("All waypoints removed", Waypoints.this);
                        }
                    }
                })).show("Clear waypoint list?", "(" + navigator.getPath().length + " waypoints)");
            } else if (ITEM_LIST_ALL.equals(item)) {
                actionListAll();
            } else if (ITEM_LOAD.equals(item)) {
                (new FileBrowser("SelectWaypoints", this, this)).show();
            }
        } else if (depth == 1) { // wpt action
            /*
             * in wpt listing...
             */
            // show wpt details
            (new WaypointForm(this, navigator.getPath()[getSelectedIndex()], this)).show();
        }
    }

    /**
     * WaypointForm action.
     * @param result array
     * @param throwable problem
     */
    public void invoke(Object result, Throwable throwable) {
        // restore navigator
        Desktop.display.setCurrent((Displayable) navigator);

        // handle action
        if (result instanceof Object[]) {

            // action type
            Object[] ret = (Object[]) result;

            // execute action
            if (WaypointForm.MENU_NAVIGATE_TO == ret[0]) { // start navigation

                // call navigator
                navigator.setNavigateTo(getSelectedIndex());

            } else if (WaypointForm.MENU_SAVE == ret[0]) { // save to GPX tracklog

                // call navigator
                navigator.recordWaypoint((Waypoint) ret[1]);

            } else if (WaypointForm.MENU_USE == ret[0]) { // enlist manually entered

                // add waypoint
                navigator.addWaypoint((Waypoint) ret[1]);

                // update title
                setTitle("Waypoints (" + navigator.getPath().length + ")");

                // show confirmation
                Desktop.showConfirmation("Waypoint enlisted", this);

            } else if (FriendForm.MENU_SEND == ret[0]) { // send waypoint by SMS

                // get message type and location
                String type = "???";
                QualifiedCoordinates qc = null;
                long time = 0;
                if (ITEM_FRIEND_HERE == ret[3]) {
                    type = cz.kruch.track.fun.Friends.TYPE_IAH;
                    qc = navigator.getLocation().getQualifiedCoordinates();
                    time = navigator.getLocation().getTimestamp();
                } else if (ITEM_FRIEND_THERE == ret[3]) {
                    type = cz.kruch.track.fun.Friends.TYPE_MYT;
                    qc = navigator.getPointer();
                    time = System.currentTimeMillis();
                }

                // send the message
                Friends.send((String) ret[1], type, (String) ret[2], qc, time);
            }
        } else if (result instanceof api.file.File) {
            // load waypoints from a file
            actionLoad((api.file.File) result);
        }
    }

    /**
     * Runnable's run() implementation for LoaderIO operation.
     */
    public void run() {

        String url = _file.getURL();

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("parse waypoints: " + url);
//#endif

        setTicker(new Ticker("Loading..."));
        try {
            // parse new waypoints
            Waypoint[] waypoints = new Waypoint[0];
            if (url.endsWith(".gpx")) {
                waypoints = parseWaypoints(_file, "GPX");
            } else if (url.endsWith(".loc")) {
                waypoints = parseWaypoints(_file, "LOC");
            }

            // show result
            if (waypoints.length > 0) {
                Desktop.showConfirmation(waypoints.length + " waypoints loaded", this);
            } else {
                Desktop.showWarning("No waypoints found in " + url, null, this);
            }

            // update title
            setTitle("Waypoints (" + waypoints.length + ")");

            // update navigator
            navigator.setPath(waypoints);
            navigator.setNavigateTo(-1);

        } catch (Throwable t) {

            // too bad
            Desktop.showError("Failed to parse waypoints", t, null);

        } finally {

            // close file connection
            try {
                _file.close();
            } catch (IOException e) {
            }

            // remove ticker and refreh menu
            setTicker(null);
            menu();
        }
    }

    api.file.File _file = null;

    /**
     * Load waypoints from file.
     */
    private void actionLoad(api.file.File file) {
        _file = file;
        LoaderIO.getInstance().enqueue(this);
    }

    private void actionClearAll() {
        navigator.setPath(new Waypoint[0]);
        navigator.setNavigateTo(-1);
        setTitle("Waypoints (0)");
        WaypointForm.cnt = 0;
    }

    private void actionListAll() {
        // get waypoints
        Waypoint[] path = navigator.getPath();

        if (path.length == 0) {
            Desktop.showInfo("No waypoints", this);
        } else {
            // clear form
            deleteAll();

            // create commands
            removeCommand(cmdSelect);
            removeCommand(cmdCancel);
            cmdCancel = new Command(ITEM_BACK, Command.BACK, 1);
            cmdSelect = new Command(ITEM_VIEW_DETAIL, Command.SCREEN, 1);
            addCommand(cmdCancel);
            setSelectCommand(cmdSelect);

            // list wpts
            for (int N = path.length, i = 0; i < N; i++) {
                Waypoint wpt = path[i];
                if (wpt.getName() == null || wpt.getName().length() == 0) {
                    append("? <" + wpt.getQualifiedCoordinates().toString() + ">", null);
                } else {
                    append(wpt.getName(), null);
                }
            }

            // increment depth
            depth++;
        }
    }

    private static Waypoint[] parseWaypoints(api.file.File file, String fileType)
            throws IOException, XmlPullParserException {
        Waypoint[] result = new Waypoint[0];
        InputStream in = null;

        try {
            in = new BufferedInputStream(file.openInputStream(), 512);
            result = parseWaypoints(in, fileType);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
            }
//            if (file != null) {
//                try {
//                    file.close();
//                } catch (IOException e) {
//                    // ignore
//                }
//            }
        }

        return result;
    }

    private static Waypoint[] parseWaypoints(InputStream in, String fileType)
            throws IOException, XmlPullParserException {

        Vector waypoints = new Vector(0);

        // parse XML
        KXmlParser parser = new KXmlParser();
        parser.setInput(in, null); // null is for encoding autodetection
        if ("GPX".equals(fileType)) {
            parseGpx(parser, waypoints);
        } else if ("LOC".equals(fileType)) {
            parseLoc(parser, waypoints);
        }
        // gc hint
        parser = null;

        // create result
        Waypoint[] result = new Waypoint[waypoints.size()];
        waypoints.copyInto(result);

        return result;
    }

    private static void parseGpx(KXmlParser parser, Vector v)
            throws IOException, XmlPullParserException {

        int eventType = XmlPullParser.START_TAG;
        int wptDepth = 0;
        String name = null;
        String comment = null;
        double lat = -1D, lon = -1D;
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                String tag = parser.getName();
                if ("wpt".equals(tag)){
                    // start level
                    wptDepth = 1;
                    // get lat and lon
                    lat = Double.parseDouble(parser.getAttributeValue(null, "lat"));
                    lon = Double.parseDouble(parser.getAttributeValue(null, "lon"));
                } else if ("name".equals(tag) && (wptDepth == 1)) {
                    // get name
                    name = parser.nextText();
                } else if ("desc".equals(tag) && (wptDepth == 1)) {
                    // get comment
                    comment = parser.nextText();
                } else {
                    // down one level
                    wptDepth++;
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                String tag = parser.getName();
                if ("wpt".equals(tag)){
                    // got wpt
                    v.addElement(new Waypoint(new QualifiedCoordinates(lat, lon),
                                              name, comment));

                    // reset temps
                    lat = lon = -1D;
                    name = comment = null;

                    // reset depth
                    wptDepth = 0;
                } else {
                    // up one level
                    wptDepth--;
                }
            }
            // next event
            eventType = parser.next();
        }
    }

    private static void parseLoc(KXmlParser parser, Vector v)
            throws IOException, XmlPullParserException {

        int eventType = XmlPullParser.START_TAG;
        int wptDepth = 0;
        String name = null;
        String comment = null;
        double lat = -1D, lon = -1D;
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                String tag = parser.getName();
                if ("waypoint".equals(tag)){
                    // start level
                    wptDepth = 1;
                } else if ("name".equals(tag) && (wptDepth == 1)) {
                    // get name and comment
                    name = parser.getAttributeValue(null, "id");
                    comment = parser.nextText();
                } else if ("coord".equals(tag) && (wptDepth == 1)) {
                    // get lat and lon
                    lat = Double.parseDouble(parser.getAttributeValue(null, "lat"));
                    lon = Double.parseDouble(parser.getAttributeValue(null, "lon"));
                    // down one level
                    wptDepth++;
                } else {
                    // down one level
                    wptDepth++;
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                String tag = parser.getName();
                if ("waypoint".equals(tag)){
                    // got wpt
                    v.addElement(new Waypoint(new QualifiedCoordinates(lat, lon),
                                              name, comment));

                    // only 1 waypoint for LOC files
                    break;
                } else {
                    // up one level
                    wptDepth--;
                }
            }

            // next event
            eventType = parser.next();
        }
    }
}
