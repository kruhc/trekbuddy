// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.event.Callback;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.location.Navigator;
import cz.kruch.track.location.GpxTracklog;
import cz.kruch.track.maps.io.LoaderIO;
import cz.kruch.track.fun.Friends;
import cz.kruch.track.configuration.Config;
import cz.kruch.j2se.io.BufferedInputStream;

import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Ticker;
import javax.microedition.lcdui.Choice;
import javax.microedition.io.Connector;

import api.location.QualifiedCoordinates;
import api.file.File;

import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;
import java.util.Enumeration;
import java.util.Hashtable;

import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParser;
import org.kxml2.io.KXmlParser;

public final class Waypoints extends List
        implements CommandListener, Callback, Runnable, YesNoDialog.AnswerListener {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Waypoints");
//#endif

    private static final short TYPE_GPX = 100;
    private static final short TYPE_LOC = 200;

    private static final String ITEM_LIST_STORES    = "Waypoints Stores";
    private static final String ITEM_ADD_NEW        = "Record Current";
    private static final String ITEM_ENTER_MANUALY  = "Enter Custom";
    private static final String ITEM_FRIEND_HERE    = "SMS 'I Am Here'";
    private static final String ITEM_FRIEND_THERE   = "SMS 'Meet You There'";
    private static final String ITEM_STOP           = "Stop";

    private static final int FRAME_XML  = 0;
    private static final int FRAME_XMLA = 1;
    private static final int FRAME_MEM  = 2;
    private static final int FRAME_MEMA = 3;

    private static final String USER_CUSTOM_STORE   = "<wmap>";
    private static final String USER_RECORDED_STORE = "<wgps>";
    private static final String USER_FRIENDS_STORE  = "<wsms>";
    private static final String USER_INJAR_STORE    = "<in-jar>";

    private static final String SUFFIX_GPX = ".gpx";
    private static final String SUFFIX_LOC = ".loc";

    private static final String TAG_WPT     = "wpt";
    private static final String TAG_NAME    = "name";
    private static final String TAG_DESC    = "desc";
    private static final String TAG_WAYPOINT = "waypoint";
    private static final String TAG_COORD   = "coord";
    private static final String ATTR_LAT    = "lat";
    private static final String ATTR_LON    = "lon";

    private static boolean initialized;
    private static Hashtable stores;
    private static Hashtable logs;
    private static Vector logNames;
    private static String currentName, inUseName;
    private static Vector currentWpts, inUseWpts;

    private Navigator navigator;
    private short depth;

    private static Command cmdSelect;
    private static Command cmdCancel;
    private static Command cmdClose;

    public Waypoints(Navigator navigator) {
        super("Waypoints", List.IMPLICIT);
        this.navigator = navigator;
        this.setFitPolicy(Choice.TEXT_WRAP_OFF);
        this.initialize();
    }

    public static void shutdown() {
        for (Enumeration e = logs.elements(); e.hasMoreElements(); ) {
            GpxTracklog gpx = (GpxTracklog) e.nextElement();
            try {
                if (gpx.isAlive()) {
                    gpx.destroy();
                    gpx.join();
                }
            } catch (Throwable t) {
                // ignore
            }
        }
    }

    private void initialize() {
        if (!initialized) {
            initialized = true;

            // init menu
            cmdSelect = new Command("Select", Command.ITEM, 1);
            cmdCancel = new Command("Back", Command.BACK, 1);
            cmdClose = new Command("Close", Command.BACK, 1);

            // init collection
            stores = new Hashtable(3);
            logs = new Hashtable(3);
            logNames = new Vector(3);

            // init memory waypoints
            stores.put(USER_CUSTOM_STORE, new Vector());
            stores.put(USER_RECORDED_STORE, new Vector());
            stores.put(USER_FRIENDS_STORE, new Vector());

            // do we have in-jar waypoint(s) resource?
            int type = TYPE_GPX;
            InputStream in = cz.kruch.track.TrackingMIDlet.class.getResourceAsStream("/resources/waypoints.gpx");
            if (in == null) {
                type = TYPE_LOC;
                in = cz.kruch.track.TrackingMIDlet.class.getResourceAsStream("/resources/waypoint.loc");
            }

            // if yes, load it
            if (in != null) {
                try {
                    stores.put(USER_INJAR_STORE, parseWaypoints(in, type));
                } catch (Throwable t) {
                    Desktop.showError("Failed to load in-jar waypoints", t, null);
                } finally {
                    try {
                        in.close();
                    } catch (IOException e) {
                        // ignore
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

        // destroy old commands
        removeCommand(cmdSelect);
        removeCommand(cmdCancel);
        removeCommand(cmdClose);

        switch (depth) {
            case 0: {
                // clear list
                deleteAll();

                // create menu
                append(ITEM_LIST_STORES, null);
                append(ITEM_ADD_NEW, null);
                append(ITEM_ENTER_MANUALY, null);
                if (cz.kruch.track.TrackingMIDlet.jsr120) {
                    if (navigator.isTracking()) {
                        append(ITEM_FRIEND_HERE, null);
                    }
                    append(ITEM_FRIEND_THERE, null);
                }
                if (navigator.getNavigateTo() != null) {
                    append(ITEM_STOP, null);
                }

                // create commands
                addCommand(cmdClose);
                setSelectCommand(cmdSelect);

                // update title
                setTitle("Waypoints");
            } break;
            case 1: {
                // create commands
                addCommand(cmdCancel);
                setSelectCommand(cmdSelect);

                // update title
                setTitle("WptStores");
            } break;
            case 2: {
                // create commands
                addCommand(cmdCancel);
                setSelectCommand(cmdSelect);

                // update title
                setTitle(currentName + " (" + currentWpts.size() + ")");
            } break;
        }
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command.getCommandType() == Command.BACK) {
            if (depth == 0) {
                Desktop.display.setCurrent((Displayable) navigator);
            } else {
                switch (--depth) {
                    case 0: {
                        menu();
                    } break;
                    case 1: {
                        onBackground(null);
                    } break;
                }
            }
        } else {
            // get selected item
            String item = getString(getSelectedIndex());
            // depth-specific action
            switch (depth) {
                case 0: {
                    // menu action
                    if (ITEM_LIST_STORES.equals(item)) {
                        onBackground(null);
                    } else if (ITEM_ADD_NEW.equals(item)) {
                        // only when tracking
                        if (navigator.isTracking()) {
                            // and we have position
                            if (navigator.getLocation() != null) {
                                // notify navigator
                                navigator.saveLocation(navigator.getLocation());
                                // open form
                                (new WaypointForm(this, navigator.getLocation().clone(), this)).show();
                            } else {
                                Desktop.showInfo("No position yet", this);
                            }
                        } else {
                            Desktop.showInfo("Not tracking", this);
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
                              (new FriendForm(this, ITEM_FRIEND_HERE, navigator.getLocation().getQualifiedCoordinates().clone(),
                                              this, ITEM_FRIEND_HERE)).show();
                          }
                    } else if (ITEM_FRIEND_THERE.equals(item)) {
                        QualifiedCoordinates pointer = navigator.getPointer();
                        if (pointer == null) {
                            Desktop.showInfo("No position", this);
                        } else {
                            (new FriendForm(this, ITEM_FRIEND_THERE, pointer, this, ITEM_FRIEND_THERE)).show();
                        }
                    } else if (ITEM_STOP.equals(item)) {
                        // restore navigator
                        Desktop.display.setCurrent((Displayable) navigator);
                        // stop navigation
                        actionStop();
                    }
                } break;
                case 1: { // store action
                    if (Command.ITEM == command.getCommandType()) {
                        onBackground(item);
                    }
                } break;
                case 2: { // wpt action
                    if (Command.ITEM == command.getCommandType()) {
                        (new WaypointForm(this, (Waypoint) currentWpts.elementAt(getSelectedIndex()), this)).show();
                    }
                } break;
            }
        }
    }

    /**
     * External action.
     * @param result array
     * @param throwable problem
     */
    public void invoke(Object result, Throwable throwable, Object source) {
        // handle action
        if (result instanceof Object[]) { // waypoint/friend form closed

            // action type
            Object[] ret = (Object[]) result;
            Object action = ret[0];

            // execute action
            if (WaypointForm.MENU_NAVIGATE_TO == action) { // start navigation

                // restore navigator
                Desktop.display.setCurrent((Displayable) navigator);

                // call navigator
                navigator.setNavigateTo((Waypoint) currentWpts.elementAt(getSelectedIndex()));

                // remember current store
                inUseName = currentName;
                inUseWpts = currentWpts;

            } else if (WaypointForm.MENU_SAVE == action) { // record & enlist current location as waypoint

                // restore navigator
                Desktop.display.setCurrent((Displayable) navigator);

                // add waypoint to memory store
                addToStore(USER_RECORDED_STORE, (Waypoint) ret[1], true);

            } else if (WaypointForm.MENU_USE == action) { // record & enlist manually entered

                _wpt = (Waypoint) ret[1];

                // add waypoint, possibly save
                (new YesNoDialog(this, this)).show("Persist custom waypoint?", null);

            } else if (Friends.TYPE_IAH == action || Friends.TYPE_MYT == action) { // record & enlist received via SMS

                // add waypoint to memory store
                addToStore(USER_FRIENDS_STORE, (Waypoint) ret[1], true);

            } else if (FriendForm.MENU_SEND == action) { // send waypoint by SMS

                // vars
                String type;
                QualifiedCoordinates qc;
                long time;

                // get message type and location
                if (ITEM_FRIEND_HERE == ret[3]) {
                    type = Friends.TYPE_IAH;
                    time = navigator.getLocation().getTimestamp();
                    qc = navigator.getLocation().getQualifiedCoordinates().clone();
                } else if (ITEM_FRIEND_THERE == ret[3]) {
                    type = Friends.TYPE_MYT;
                    time = System.currentTimeMillis();
                    qc = navigator.getPointer();
                } else {
                    throw new IllegalArgumentException("Unknown SMS type");
                }

                // send the message
                Friends.send((String) ret[1], type, (String) ret[2], qc, time);

            }

        } else if (source instanceof GpxTracklog) { // waypoint recording notification

            if (throwable == null) {
                if (result instanceof Integer) {
                    int c = ((Integer) result).intValue();
                    switch (c) {
                        case GpxTracklog.CODE_RECORDING_START:
                            // don't bother
                            break;
                        case GpxTracklog.CODE_RECORDING_STOP:
                            // don't bother
                            break;
                        case GpxTracklog.CODE_WAYPOINT_INSERTED:
                            Desktop.showConfirmation("Waypoint recorded.", null);
                            break;
                    }
                }
            } else {
                Desktop.showWarning(result == null ? "Waypoint recording problem?" : (String) result,
                                    throwable, null);
            }
        } else {
            throw new IllegalStateException("Unknown invocation; result = " + result + "; throwable = " + throwable);
        }
    }


    /**
     * "Do you want to persist custom waypoint?"
     * @param answer answer
     */
    public void response(int answer) {
        // add waypoint to memory store
        addToStore(USER_CUSTOM_STORE, _wpt, YesNoDialog.YES == answer);
        _wpt = null; // gc hint
        if (YesNoDialog.NO == answer) {
            Desktop.showConfirmation("Waypoint enlisted.", this);
        }
    }

    private Waypoint _wpt;

    /**
     * Background I/O operations.
     */
    public void run() {
        if (_storeName == null) {
            actionListStores();
        } else {
            actionListStore();
        }
    }

    private String _storeName;

    /**
     * Background task launcher.
     */
    private void onBackground(String storeName) {
        this._storeName = null; // gc hint
        this._storeName = storeName;
        LoaderIO.getInstance().enqueue(this);
    }

    /**
     * Lists landmark stores.
     */
    private void actionListStores() {
        File dir = null;
        try {
            // open stores directory
            dir = File.open(Connector.open(Config.getFolderWaypoints(), Connector.READ));

            // clear form
            deleteAll();

            // list memory stores
            listKnown(USER_CUSTOM_STORE);
            listKnown(USER_RECORDED_STORE);
            listKnown(USER_FRIENDS_STORE);
            listKnown(USER_INJAR_STORE);

            // list file stores
            if (dir.exists()) {
                for (Enumeration e = dir.list(); e.hasMoreElements(); ) {
                    String name = (String) e.nextElement();
                    int i = name.lastIndexOf('.');
                    if (i > -1) {
                        String ext = name.substring(i).toLowerCase();
                        if (ext.equals(SUFFIX_GPX) || ext.equals(SUFFIX_LOC)) {
                            if (!logNames.contains(name)) {
                                append(name, name.equals(inUseName) ? NavigationScreens.stores[FRAME_XMLA] : NavigationScreens.stores[FRAME_XML]);
                            }
                        }
                    }
                }
            }

            // got anything?
            if (size() == 0) {
                // notify user
                Desktop.showInfo("No landmark stores", this);
                // show std menu
                menu();
            } else {
                // increment depth
                depth = 1;
                // update menu
                menu();
            }
        } catch (Throwable t) {
            Desktop.showError("Failed to list landmark stores", t, this);
        } finally {
            if (dir != null) {
                try {
                    dir.close();
                } catch (IOException e) {
                    // ignore
                }
                dir = null; // gc hint
            }
        }
    }

    /**
     * Loads waypoints from file landmark store.
     */
    private void actionListStore() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("list store: " + _storeName);
//#endif

        // got store in cache?
        Vector wpts = (Vector) stores.get(_storeName);
        if (wpts == null) { // no, load from file
            File file = null;
            try {
                // open file
                file = File.open(Connector.open(Config.getFolderWaypoints() + _storeName, Connector.READ));

                // start ticker
                setTicker(new Ticker("Loading..."));

                // parse new waypoints
                if (_storeName.endsWith(SUFFIX_GPX)) {
                    wpts = parseWaypoints(file, TYPE_GPX);
                } else if (_storeName.endsWith(SUFFIX_LOC)) {
                    wpts = parseWaypoints(file, TYPE_LOC);
                }

                // process result
                if (wpts == null || wpts.size() == 0) {

                    // warn user
                    Desktop.showWarning("No waypoints found in " + _storeName, null, this);

                } else {

                    // notify user
                    Desktop.showConfirmation(wpts.size() + " waypoints loaded", this);

                    // cache store
                    stores.put(_storeName, wpts);
                }
            } catch (Throwable t) {

//#ifdef __LOG__
                t.printStackTrace();
//#endif

                // show error
                Desktop.showError("Failed to list landmark store", t, this);

            } finally {

                // close file
                if (file != null) {
                    try {
                        file.close();
                    } catch (IOException e) {
                        // ignore
                    }
                    file = null; // gc hint
                }

                // remove ticker
                setTicker(null);
            }
        }

        // show result
        if (wpts != null && wpts.size() > 0) {

            // remember store
            currentWpts = wpts;
            currentName = _storeName;

            // show waypoints
            listWaypoints(wpts);
        }

        // update menu
        menu();
    }

    private void actionStop() {
        // stop navigation
        navigator.setNavigateTo(null);

        // update menu
        menu();
    }

    private void listKnown(String storeKey) {
        if (stores.containsKey(storeKey)) {
            if (((Vector) stores.get(storeKey)).size() > 0) {
                append(storeKey, storeKey.equals(inUseName) ? NavigationScreens.stores[FRAME_MEMA] : NavigationScreens.stores[FRAME_MEM]);
            }
        }
    }
    
    private void addToStore(Object storeKey, Waypoint wpt, final boolean save) {
        // add to store
        ((Vector) stores.get(storeKey)).addElement(wpt);

        // save?
        if (!save) {
            return;
        }

        // start GPX log if needed
        GpxTracklog gpx = (GpxTracklog) logs.get(storeKey);
        if (gpx == null) {
            gpx = new GpxTracklog(GpxTracklog.LOG_WPT, this,
                                  navigator.getTracklogCreator(),
                                  navigator.getTracklogTime());
            if (USER_CUSTOM_STORE.equals(storeKey)) {
                gpx.setFilePrefix("wmap-");
            } else if (USER_FRIENDS_STORE.equals(storeKey)) {
                gpx.setFilePrefix("wsms-");
            } else {
                gpx.setFilePrefix("wgps-");
            }
            gpx.start();

            // add to collection
            logs.put(storeKey, gpx);
            logNames.addElement(gpx.getFileName());
        }

        // record waypoint
        gpx.insert(wpt);
    }

    private void listWaypoints(Vector wpts) {
        // inc depth
        depth = 2;

        // clear form
        deleteAll();

        // list wpts
        for (int N = wpts.size(), i = 0; i < N; i++) {
            Waypoint wpt = (Waypoint) wpts.elementAt(i);
            String name = wpt.getName();
            if (name == null || name.length() == 0) {
                append("? <" + wpt.getQualifiedCoordinates().toString() + ">", null);
            } else {
                append(name, null);
            }
        }
    }

    private static Vector parseWaypoints(File file, final int fileType)
            throws IOException, XmlPullParserException {
        InputStream in = null;

        try {
            in = new BufferedInputStream(file.openInputStream(), 512);
            return parseWaypoints(in, fileType);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private static Vector parseWaypoints(InputStream in, final int fileType)
            throws IOException, XmlPullParserException {

        Vector result = new Vector(8, 16);

        // parse XML
        KXmlParser parser = new KXmlParser();
        try {
            parser.setInput(in, null); // null is for encoding autodetection
            if (TYPE_GPX == fileType) { // '==' is ok
                parseGpx(parser, result);
            } else if (TYPE_LOC == fileType) { // '==' is ok
                parseLoc(parser, result);
            }
        } finally {
            try {
                parser.close();
            } catch (IOException e) {
                // ignore
            }
            parser = null; // gc hint
        }

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
                if (TAG_WPT.equals(tag)){
                    // start level
                    wptDepth = 1;
                    // get lat and lon
                    lat = Double.parseDouble(parser.getAttributeValue(null, ATTR_LAT));
                    lon = Double.parseDouble(parser.getAttributeValue(null, ATTR_LON));
                } else if (TAG_NAME.equals(tag) && (wptDepth == 1)) {
                    // get name
                    name = parser.nextText();
                } else if (TAG_DESC.equals(tag) && (wptDepth == 1)) {
                    // get comment
                    comment = parser.nextText();
                } else {
                    // down one level
                    wptDepth++;
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                String tag = parser.getName();
                if (TAG_WPT.equals(tag)){
                    // got wpt
                    v.addElement(new Waypoint(QualifiedCoordinates.newInstance(lat, lon),
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
                if (TAG_WAYPOINT.equals(tag)){
                    // start level
                    wptDepth = 1;
                } else if (TAG_NAME.equals(tag) && (wptDepth == 1)) {
                    // get name and comment
                    name = parser.getAttributeValue(null, "id");
                    comment = parser.nextText();
                } else if (TAG_COORD.equals(tag) && (wptDepth == 1)) {
                    // get lat and lon
                    lat = Double.parseDouble(parser.getAttributeValue(null, ATTR_LAT));
                    lon = Double.parseDouble(parser.getAttributeValue(null, ATTR_LON));
                    // down one level
                    wptDepth++;
                } else {
                    // down one level
                    wptDepth++;
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                String tag = parser.getName();
                if (TAG_WAYPOINT.equals(tag)){
                    // got wpt
                    v.addElement(new Waypoint(QualifiedCoordinates.newInstance(lat, lon),
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
