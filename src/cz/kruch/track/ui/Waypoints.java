// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.event.Callback;
import cz.kruch.track.util.Logger;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.location.Navigator;
//#ifndef __NO_FS__
import cz.kruch.track.location.GpxTracklog;
//#endif
import cz.kruch.track.maps.io.LoaderIO;
import cz.kruch.track.TrackingMIDlet;

import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Ticker;

import api.location.QualifiedCoordinates;

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

    private Navigator navigator;
    private int depth = 0;

    private Command cmdSelect;
    private Command cmdCancel;

    public Waypoints(Navigator navigator) {
        super("Waypoints (?)", List.IMPLICIT);
        this.navigator = navigator;
        this.setTitle("Waypoints (" + navigator.getPath().length + ")");
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
//#ifndef __NO_FS__
        append(ITEM_ADD_NEW, null);
//#endif
        append(ITEM_SHOW_CURRENT, null);
        if (TrackingMIDlet.isJsr120()) {
            if (navigator.isTracking()) {
                append(ITEM_FRIEND_HERE, null);
            }
            append(ITEM_FRIEND_THERE, null);
        }
        append(ITEM_ENTER_MANUALY, null);
        append(ITEM_CLEAR_ALL, null);
        append(ITEM_LIST_ALL, null);
//#ifndef __NO_FS__
        append(ITEM_LOAD, null);
//#endif

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
                Desktop.display.setCurrent(Desktop.screen);
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
//#ifndef __NO_FS__
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
            } else
//#endif
              if (ITEM_SHOW_CURRENT.equals(item)) {
                if (navigator.getPath().length == 0) {
                    Desktop.showInfo("No waypoints", this);
                } else if (navigator.getNavigateTo() == -1) {
                    Desktop.showInfo("No waypoint selected", this);
                } else {
                    (new WaypointForm(this, navigator.getPath()[navigator.getNavigateTo()])).show();
                }
            } else if (ITEM_ENTER_MANUALY.equals(item)) {
                (new WaypointForm(this, this, navigator.getPointer())).show();
            } else if (ITEM_FRIEND_HERE.equals(item)) {
                  // we have position?
                  if (navigator.getLocation() != null) {
                    (new FriendForm(this, ITEM_FRIEND_HERE, navigator.getLocation().getQualifiedCoordinates(), this, ITEM_FRIEND_HERE)).show();
                  } else {
                      Desktop.showInfo("No position yet", this);
                  }
            } else if (ITEM_FRIEND_THERE.equals(item)) {
                (new FriendForm(this, ITEM_FRIEND_THERE, navigator.getPointer(), this, ITEM_FRIEND_THERE)).show();
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
//#ifndef __NO_FS__
            } else if (ITEM_LOAD.equals(item)) {
                (new FileBrowser("SelectWaypoints", new Callback() {
                    public void invoke(Object result, Throwable throwable) {
                        if (result != null) {
                            actionLoad((String) result);
                        }
                    }
                }, this)).show();
//#endif
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
        // restore desktop
        Desktop.display.setCurrent(Desktop.screen);

        // handle action
        if (result instanceof Object[]) {

            // action type
            Object[] ret = (Object[]) result;

            // execute action
            if (WaypointForm.MENU_NAVIGATE_TO == ret[0]) { // start navigation

                // call navigator
                navigator.setNavigateTo(getSelectedIndex());

//#ifndef __NO_FS__

            } else if (WaypointForm.MENU_SAVE == ret[0]) { // save to GPX tracklog

                // call navigator
                navigator.recordWaypoint((Waypoint) ret[1]);

//#endif

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
                cz.kruch.track.fun.Friends.send((String) ret[1], type, (String) ret[2], qc, time);
            }
        }
    }

    /**
     * Runnable's run() implementation for LoaderIO operation.
     */
    public void run() {

//#ifndef __NO_FS__

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("parse waypoints: " + _urlToRead);
//#endif

        setTicker(new Ticker("Loading..."));
        try {
            // parse new waypoints
            Waypoint[] waypoints = new Waypoint[0];
            if (_urlToRead.endsWith(".gpx")) {
                waypoints = GpxTracklog.parseWaypoints(_urlToRead, "GPX");
            } else if (_urlToRead.endsWith(".loc")) {
                waypoints = GpxTracklog.parseWaypoints(_urlToRead, "LOC");
            }

            // show result
            if (waypoints.length > 0) {
                Desktop.showConfirmation(waypoints.length + " waypoints loaded", this);
            } else {
                Desktop.showWarning("No waypoints found in " + _urlToRead, null, this);
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

            // remove ticker and refreh menu
            setTicker(null);
            menu();

        }

//#endif

    }

//#ifndef __NO_FS__

    String _urlToRead = null;

    /**
     * Load waypoints from file.
     */
    private void actionLoad(String url) {
        _urlToRead = url;
        LoaderIO.getInstance().enqueue(this);
    }

//#endif

    private void actionClearAll() {
        navigator.setPath(new Waypoint[0]);
        navigator.setNavigateTo(-1);
        setTitle("Waypoints (0)");
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
}
