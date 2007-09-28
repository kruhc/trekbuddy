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
import cz.kruch.track.location.Waypoint;
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

/**
 * Navigation manager.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class Waypoints extends List
        implements CommandListener, Callback, Runnable, YesNoDialog.AnswerListener {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Waypoints");
//#endif

    private static final short TYPE_GPX = 100;
    private static final short TYPE_LOC = 200;

    private static final String ITEM_LIST_STORES    = "Waypoints";
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

    private static final String TAG_RTE     = "rte";
    private static final String TAG_RTEPT   = "rtept";
    private static final String TAG_WPT     = "wpt";
    private static final String TAG_TRKPT   = "trkpt";
    private static final String TAG_NAME    = "name";
    private static final String TAG_CMT     = "cmt";
    private static final String TAG_DESC    = "desc";
    private static final String TAG_ELE     = "ele";
    private static final String TAG_WAYPOINT = "waypoint";
    private static final String TAG_COORD   = "coord";
    private static final String ATTR_LAT    = "lat";
    private static final String ATTR_LON    = "lon";

    private static final String PREFIX_WMAP = "wmap-";
    private static final String PREFIX_WSMS = "wsms-";
    private static final String PREFIX_WGPS = "wgps-";
    
    private static final String[] NAME_CACHE = {
        TAG_WPT, TAG_RTEPT, TAG_TRKPT, TAG_NAME, TAG_CMT, TAG_DESC, ATTR_LAT, ATTR_LON
    };
    
    private Hashtable stores;
    private Hashtable logs;
    private Vector logNames;
    private String currentName, inUseName;
    private Vector currentWpts, inUseWpts;

    private /*Navigator*/Desktop navigator;

    private short depth;
    private List list;
    private int[] idx;

    private Command cmdBack, cmdClose;
    private Command cmdNavigateTo, cmdNavigateAlong, cmdNavigateBack, cmdSetAsCurrent, cmdGoTo;

    private static Waypoints instance;

    public static void initialize(/*Navigator*/Desktop navigator) {
        instance = new Waypoints(navigator);
    }

    public static Waypoints getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Waypoints not initialized");
        }

        return instance;
    }

    public static void shutdown() {
        if (instance != null) {
            instance.stopLogs();
        }
    }

    private Waypoints(/*Navigator*/Desktop navigator) {
        super("Navigation", List.IMPLICIT);
        this.navigator = navigator;
        this.initialize();
        this.setFitPolicy(Choice.TEXT_WRAP_OFF);
        this.setCommandListener(this);
    }

    private void stopLogs() {
        for (Enumeration e = logs.elements(); e.hasMoreElements(); ) {
            GpxTracklog gpx = (GpxTracklog) e.nextElement();
            try {
                if (gpx.isAlive()) {
                    gpx.destroy();
                }
                gpx.join();
            } catch (InterruptedException exc) {
                // ignore - should not happen
            }
        }
    }

    private void initialize() {
        // item index
        idx = new int[3];

        // init commands
        cmdBack = new Command("Back", Command.BACK, 1);
        cmdClose = new Command("Close", Command.BACK, 1);
        cmdNavigateTo = new Command(WaypointForm.MENU_NAVIGATE_TO, Command.ITEM, 2);
        cmdNavigateAlong = new Command(WaypointForm.MENU_NAVIGATE_ALONG, Command.ITEM, 3);
        cmdNavigateBack = new Command(WaypointForm.MENU_NAVIGATE_BACK, Command.ITEM, 4);
        cmdSetAsCurrent = new Command(WaypointForm.MENU_SET_CURRENT, Command.ITEM, 2);
        cmdGoTo = new Command(WaypointForm.MENU_GO_TO, Command.ITEM, 5);

        // update commands
        addCommand(cmdClose);

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

    public void show() {
        // let's start with basic menu
        depth = 0;
        list = this;
        menu();
    }

    public void showCurrent() {
        // show current store, if any...
        if (currentWpts == null) {
            depth = 0;
            list = this;
        } else {
            depth = 2;
            list = listWaypoints(currentWpts);
        }
        menu();
    }

    private void menu() {
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
            } break;
            case 1: {
            } break;
            case 2: {
            } break;
        }

        // set last known choice
        if (idx[depth] < size()) {
            list.setSelectedIndex(idx[depth], true);
        }

        // show list
        Desktop.display.setCurrent(list);
    }

    public void commandAction(Command command, Displayable displayable) {
        if (Command.BACK == command.getCommandType()) {
            if (depth == 0) {
                Desktop.display.setCurrent(navigator);
            } else {
                switch (--depth) {
                    case 0: {
                        list = this;
                        menu();
                    } break;
                    case 1: {
                        onBackground(null);
                    } break;
                }
            }
        } else {
            // get selected item
            idx[depth] = list.getSelectedIndex();
            String item = list.getString(idx[depth]);

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
                                (new WaypointForm(navigator.getLocation().clone(),
                                                  this)).show();
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
                            (new WaypointForm(this, pointer)).show();
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
                        Desktop.display.setCurrent(navigator);
                        // stop navigation
                        actionStop();
                    }
                } break;
                case 1: { // store action
//                    if (Command.ITEM == command.getCommandType()) {
//                        String label = command.getLabel();
//                        if (label.equals(cmdSelect.getLabel())) {
                        if (List.SELECT_COMMAND == command) {
                            onBackground(item);
                        }/* else if (WaypointForm.MENU_NAVIGATE_ALONG.equals(label)) {
                            invoke(new Object[]{ WaypointForm.MENU_NAVIGATE_ALONG, null }, null, this);
                        }*/
//                    }
                } break;
                case 2: { // wpt action
//                    if (Command.ITEM == command.getCommandType()) {
                        String label = command.getLabel();
//                        if (label.equals(cmdSelect.getLabel())) {
                        if (List.SELECT_COMMAND == command) {
                            (new WaypointForm((Waypoint) currentWpts.elementAt(idx[depth]), this)).show();
                        } else if (WaypointForm.MENU_NAVIGATE_TO.equals(label)) {
                            invoke(new Object[]{ WaypointForm.MENU_NAVIGATE_TO, null }, null, this);
                        } else if (WaypointForm.MENU_NAVIGATE_ALONG.equals(label)) {
                            invoke(new Object[]{ WaypointForm.MENU_NAVIGATE_ALONG, null }, null, this);
                        } else if (WaypointForm.MENU_NAVIGATE_BACK.equals(label)) {
                            invoke(new Object[]{ WaypointForm.MENU_NAVIGATE_BACK, null }, null, this);
                        } else if (WaypointForm.MENU_SET_CURRENT.equals(label)) {
                            invoke(new Object[]{ WaypointForm.MENU_SET_CURRENT, null }, null, this);
                        } else if (WaypointForm.MENU_GO_TO.equals(label)) {
                            invoke(new Object[]{ WaypointForm.MENU_GO_TO, null }, null, this);
                        }
//                    }
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
            if (null == action) {

                // restore wtp list
                Desktop.display.setCurrent(list);

            } else if (WaypointForm.MENU_NAVIGATE_ALONG == action) {

                // restore navigator
                Desktop.display.setCurrent(navigator);

                // call navigator
                navigator.setNavigateTo(currentWpts, list.getSelectedIndex(), -1);

                // remember current store
                inUseName = currentName;
                inUseWpts = currentWpts;

            } else if (WaypointForm.MENU_NAVIGATE_BACK == action) {

                // restore navigator
                Desktop.display.setCurrent(navigator);

                // call navigator
                navigator.setNavigateTo(currentWpts, -1, list.getSelectedIndex());

                // remember current store
                inUseName = currentName;
                inUseWpts = currentWpts;

            } else if (WaypointForm.MENU_NAVIGATE_TO == action) { // start navigation

                // restore navigator
                Desktop.display.setCurrent(navigator);

                // call navigator
                navigator.setNavigateTo(currentWpts, list.getSelectedIndex(), list.getSelectedIndex());

                // remember current store
                inUseName = currentName;
                inUseWpts = currentWpts;

            } else if (WaypointForm.MENU_SET_CURRENT == action) {

                // restore navigator
                Desktop.display.setCurrent(navigator);

                // call navigator
                if (Desktop.routeDir == 1) {
                    navigator.setNavigateTo(currentWpts /* == Desktop.wpts */, list.getSelectedIndex(), -1);
                } else {
                    navigator.setNavigateTo(currentWpts /* == Desktop.wpts */, -1, list.getSelectedIndex());
                }

            } else if (WaypointForm.MENU_GO_TO == action) {

                // restore navigator
                Desktop.display.setCurrent(navigator);

                // call navigator
                navigator.goTo((Waypoint) currentWpts.elementAt(list.getSelectedIndex()));

            } else if (WaypointForm.MENU_SAVE == action) { // record & enlist current location as waypoint

                // restore navigator
                Desktop.display.setCurrent(navigator);

                // add waypoint to memory store
                addToStore(USER_RECORDED_STORE, (Waypoint) ret[1], true);

            } else if (WaypointForm.MENU_USE == action) { // record & enlist manually entered

                _wpt = (Waypoint) ret[1];

                // add waypoint, possibly save
                (new YesNoDialog(this, this)).show("Persist custom waypoint?", null);

            } else if (Friends.TYPE_IAH == action || Friends.TYPE_MYT == action) { // record & enlist received via SMS

                // restore navigator
                Desktop.display.setCurrent(navigator);

                // add waypoint to store
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

                // restore navigator
                Desktop.display.setCurrent(navigator);

                // send the message
                Friends.send((String) ret[1], type, (String) ret[2], qc, time);
            }

        } else if (source instanceof GpxTracklog) { // waypoint recording notification

            // no notification for SMS log to avoid conflict with notification in Friends class
            if (!((GpxTracklog) source).getFileName().startsWith(PREFIX_WSMS)) {
                if (throwable == null) {
                    if (result instanceof Integer) {
                        int c = ((Integer) result).intValue();
                        switch (c) {
                            case GpxTracklog.CODE_RECORDING_STOP:
                            case GpxTracklog.CODE_RECORDING_START:
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
        // stores list
        List l = new List("Waypoints", List.IMPLICIT);
        l.addCommand(cmdBack);
        l.setCommandListener(this);

        // list memory stores
        listKnown(l, USER_CUSTOM_STORE);
        listKnown(l, USER_RECORDED_STORE);
        listKnown(l, USER_FRIENDS_STORE);
        listKnown(l, USER_INJAR_STORE);

        // list persistent stores
        if (cz.kruch.track.TrackingMIDlet.isFs()) {

            // may take some time - start ticker
            list.setTicker(new Ticker("Listing..."));

            File dir = null;
            try {
                // open stores directory
                dir = File.open(Connector.open(Config.getFolderWaypoints(), Connector.READ));

                // list file stores
                if (dir.exists()) {
                    for (Enumeration e = dir.list(); e.hasMoreElements(); ) {
                        String name = (String) e.nextElement();
                        int i = name.lastIndexOf('.');
                        if (i > -1) {
                            String ext = name.substring(i).toLowerCase();
                            if (ext.equals(SUFFIX_GPX) || ext.equals(SUFFIX_LOC)) {
                                if (!logNames.contains(name)) {
                                    l.append(name, name.equals(inUseName) ? NavigationScreens.stores[FRAME_XMLA] : NavigationScreens.stores[FRAME_XML]);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                Desktop.showError("Failed to list landmark stores", t, this);
            } finally {
                // close dir
                if (dir != null) {
                    try {
                        dir.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }

                // remove ticker
                list.setTicker(null);
            }
        }

        // got anything?
        if (l.size() == 0) {
            // notify user
            Desktop.showInfo("No landmark stores", this);
        } else {
            // list stores
            depth = 1;
            list = l;
            menu();
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
                list.setTicker(new Ticker("Loading..."));

                // parse new waypoints
                int i = _storeName.lastIndexOf('.');
                if (i > -1) {
                    String ext = _storeName.substring(i).toLowerCase();
                    if (ext.equals(SUFFIX_GPX)) {
                        wpts = parseWaypoints(file, TYPE_GPX);
                    } else if (ext.equals(SUFFIX_LOC)) {
                        wpts = parseWaypoints(file, TYPE_LOC);
                    }
                }

                // process result
                if (wpts == null || wpts.size() == 0) {

                    // warn user
                    Desktop.showWarning("No waypoints found in " + _storeName, null, list);

                } else {

                    // notify user
                    Desktop.showInfo(wpts.size() + " waypoints loaded", this);

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
                list.setTicker(null);
            }
        }

        // show result
        if (wpts != null && wpts.size() > 0) {
            // remember store
            currentWpts = wpts;
            currentName = _storeName;

            // list waypoints
            depth = 2;
            list = listWaypoints(wpts);
            menu();
        }
    }

    private void actionStop() {
        // stop navigation
        navigator.setNavigateTo(null, -1, -1);

        // update menu
        menu();
    }

    private void listKnown(List l, String storeKey) {
        if (stores.containsKey(storeKey)) {
            if (((Vector) stores.get(storeKey)).size() > 0) {
                l.append(storeKey, storeKey.equals(inUseName) ? NavigationScreens.stores[FRAME_MEMA] : NavigationScreens.stores[FRAME_MEM]);
            }
        }
    }
    
    private void addToStore(Object storeKey, Waypoint wpt, final boolean save) {
        // add to store
        ((Vector) stores.get(storeKey)).addElement(wpt);

        // save?
        if (save) {

            // start GPX log if needed
            GpxTracklog gpx = (GpxTracklog) logs.get(storeKey);
            if (gpx == null) {
                gpx = new GpxTracklog(GpxTracklog.LOG_WPT, this,
                                      navigator.getTracklogCreator(),
                                      navigator.getTracklogTime());
                if (USER_CUSTOM_STORE.equals(storeKey)) {
                    gpx.setFilePrefix(PREFIX_WMAP);
                } else if (USER_FRIENDS_STORE.equals(storeKey)) {
                    gpx.setFilePrefix(PREFIX_WSMS);
                } else {
                    gpx.setFilePrefix(PREFIX_WGPS);
                }
                gpx.start();

                // add to collection
                logs.put(storeKey, gpx);
                logNames.addElement(gpx.getFileName());
            }

            // record waypoint
            gpx.insert(wpt);
            
        } else {
            // release user object
            wpt.setUserObject(null);
        }
    }

    private List listWaypoints(Vector wpts) {
        // waypoints list
        List l = new List(currentName + " (" + currentWpts.size() + ")", List.IMPLICIT);
        if (currentWpts == Desktop.wpts && 0 != Desktop.routeDir) {
            l.addCommand(cmdSetAsCurrent);
        } else {
            l.addCommand(cmdNavigateTo);
            l.addCommand(cmdNavigateAlong);
            l.addCommand(cmdNavigateBack);
        }
        l.addCommand(cmdGoTo);
        l.addCommand(cmdBack);
        l.setCommandListener(this);

        // list wpts
        for (int N = wpts.size(), i = 0; i < N; i++) {
            Waypoint wpt = (Waypoint) wpts.elementAt(i);
            String name = wpt.getName();
            if (wpts == Desktop.wpts && i == Desktop.wptIdx) {
                l.append("(*) " + name, null);
            } else {
                l.append(name, null);
            }
        }

        return l;
    }

    private static Vector parseWaypoints(File file, final int fileType)
            throws IOException, XmlPullParserException {
        InputStream in = null;

        try {
            in = new BufferedInputStream(file.openInputStream(), 1024);
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

        Vector result = new Vector(16, 64);

        // parse XML
        KXmlParser parser = new KXmlParser();
        parser.setNameCache(NAME_CACHE);
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
        }

        return result;
    }

    private static void parseGpx(KXmlParser parser, Vector v)
            throws IOException, XmlPullParserException {

        int depth = 0;

        String name = null;
        String comment = null;
        StringBuffer sb = null;
        double lat = -1D;
        double lon = -1D;
        float alt = -1F;

        for (int eventType = parser.next(); eventType != XmlPullParser.END_DOCUMENT; eventType = parser.next()) {
            switch (eventType) {
                case XmlPullParser.START_TAG: {
                    switch (depth) {
                        case 0: {
                            String tag = parser.getName();
                            if (TAG_WPT.equals(tag) || TAG_RTEPT.equals(tag) || TAG_TRKPT.equals(tag)){
                                // start level
                                depth = 1;
                                // get lat and lon
                                lat = Double.parseDouble(parser.getAttributeValue(null, ATTR_LAT));
                                lon = Double.parseDouble(parser.getAttributeValue(null, ATTR_LON));
                            }
                        } break;
                        case 1: {
                            String tag = parser.getName();
                            if (TAG_NAME.equals(tag)) {
                                // get name
                                name = parser.nextText();
                            } else if (TAG_CMT.equals(tag)) {
                                // get comment
                                comment = parser.nextText();
                            } else if (TAG_DESC.equals(tag)) {
                                // get description (replaces existing from <cmt>)
                                comment = parser.nextText();
                            } else if (TAG_ELE.equals(tag)) {
                                // get elevation
                                alt = Float.parseFloat(parser.nextText());
                            } else {
                                // skip
                                parser.skipSubTree();
                            }
                        } break;
                        default:
                            // down one level
                            if (depth > 0) {
                                depth++;
                            }
                    }
                } break;
                case XmlPullParser.END_TAG: {
                    if (depth == 1) {
                        String tag = parser.getName();
                        if (TAG_WPT.equals(tag) || TAG_RTEPT.equals(tag) || TAG_TRKPT.equals(tag)){
                            // got wpt
                            if (name == null || name.length() == 0) {
                                if (sb == null) {
                                    sb = new StringBuffer(16);
                                } else {
                                    sb.delete(0, sb.length());
                                }
                                sb.append('#');
                                NavigationScreens.append(v.size(), 1000, sb);
                                name = sb.toString();
                            }
                            v.addElement(new Waypoint(QualifiedCoordinates.newInstance(lat, lon, alt),
                                                      name, comment));
                            // reset temps
                            lat = lon = -1D;
                            alt = -1F;
                            name = comment = null;
                            // reset depth
                            depth = 0;
                        }
                    } else {
                        // up one level
                        if (depth > 0) {
                            depth--;
                        }
                    }
                } break;
            }
        }
    }

    private static void parseLoc(KXmlParser parser, Vector v)
            throws IOException, XmlPullParserException {

        int depth = 0;

        String name = null;
        String comment = null;
        double lat = -1D;
        double lon = -1D;

        for (int eventType = parser.next(); eventType != XmlPullParser.END_DOCUMENT; eventType = parser.next()) {
            switch (eventType) {
                case XmlPullParser.START_TAG: {
                    switch (depth) {
                        case 0: {
                            String tag = parser.getName();
                            if (TAG_WAYPOINT.equals(tag)) {
                                // start level
                                depth = 1;
                            }
                        } break;
                        case 1: {
                            String tag = parser.getName();
                            if (TAG_NAME.equals(tag)) {
                                // get name and comment
                                name = parser.getAttributeValue(null, "id");
                                comment = parser.nextText();
                            } else if (TAG_COORD.equals(tag)) {
                                // get lat and lon
                                lat = Double.parseDouble(parser.getAttributeValue(null, ATTR_LAT));
                                lon = Double.parseDouble(parser.getAttributeValue(null, ATTR_LON));
                            } else {
                                // skip
                                parser.skipSubTree();
                            }
                        } break;
                        default: {
                            // down one level
                            if (depth > 0) {
                                depth++;
                            }
                        }
                    }
                } break;
                case XmlPullParser.END_TAG: {
                    if (depth == 1) {
                        String tag = parser.getName();
                        if (TAG_WAYPOINT.equals(tag)) {
                            // got wpt
                            v.addElement(new Waypoint(QualifiedCoordinates.newInstance(lat, lon),
                                                      name, comment));
                            // reset temps
                            lat = lon = -1D;
                            name = comment = null;
                            // reset depth
                            depth = 0;
                        }
                    } else {
                        // up one level
                        if (depth > 0) {
                            depth--;
                        }
                    }
                }
            }
        }
    }
}
