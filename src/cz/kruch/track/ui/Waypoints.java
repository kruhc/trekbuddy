// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.event.Callback;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.location.GpxTracklog;
import cz.kruch.track.location.GroundspeakBean;
import cz.kruch.track.maps.io.LoaderIO;
import cz.kruch.track.fun.Friends;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.Resources;
import cz.kruch.track.util.NakedVector;
import cz.kruch.j2se.io.BufferedInputStream;

import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Ticker;
import javax.microedition.lcdui.Choice;
import javax.microedition.io.Connector;

import api.location.QualifiedCoordinates;
import api.location.Location;
import api.file.File;

import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Date;

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

    private static final int TYPE_GPX = 100;
    private static final int TYPE_LOC = 101;

    private static final int SUFFIX_LENGTH = 4;

/*
    private static final int FRAME_XML  = 0;
    private static final int FRAME_XMLA = 1;
    private static final int FRAME_MEM  = 2;
    private static final int FRAME_MEMA = 3;
*/

    private static final String USER_CUSTOM_STORE   = "<wmap>";
    private static final String USER_RECORDED_STORE = "<wgps>";
    private static final String USER_FRIENDS_STORE  = "<wsms>";
/*
    private static final String USER_INJAR_STORE    = "<in-jar>";
*/

    private static final String SUFFIX_GPX = ".gpx";
    private static final String SUFFIX_LOC = ".loc";

    private static final String TAG_RTEPT   = "rtept";
    private static final String TAG_WPT     = "wpt";
    private static final String TAG_TRKPT   = "trkpt";
    private static final String TAG_NAME    = "name";
    private static final String TAG_CMT     = "cmt";
    private static final String TAG_DESC    = "desc";
    private static final String TAG_ELE     = "ele";
    private static final String TAG_LINK    = "link";
    private static final String TAG_WAYPOINT = "waypoint";
    private static final String TAG_COORD   = "coord";
    private static final String TAG_SYM     = "sym";
    private static final String ATTR_LAT    = "lat";
    private static final String ATTR_LON    = "lon";
    private static final String ATTR_HREF   = "href";

    private static final String TAG_GS_CACHE        = "cache";
    private static final String TAG_GS_TYPE         = "type";
    private static final String TAG_GS_CONTAINER    = "container";
    private static final String TAG_GS_DIFF         = "difficulty";
    private static final String TAG_GS_TERRAIN      = "terrain";
    private static final String TAG_GS_SHORTL       = "short_description";
    private static final String TAG_GS_LONGL        = "long_description";
    private static final String TAG_GS_COUNTRY      = "country";
    private static final String TAG_GS_HINTS        = "encoded_hints";
    private static final String ATTR_GS_ID          = "id";

    private static final String PREFIX_WMAP = "wmap-";
    private static final String PREFIX_WSMS = "wsms-";
    private static final String PREFIX_WGPS = "wgps-";
    
    private static final String itemListStores  = Resources.getString(Resources.NAV_ITEM_WAYPOINTS);
    private static final String itemAddNew      = Resources.getString(Resources.NAV_ITEM_RECORD);
    private static final String itemEnterCustom = Resources.getString(Resources.NAV_ITEM_ENTER);
    private static final String itemFriendHere  = Resources.getString(Resources.NAV_ITEM_SMS_IAH);
    private static final String itemFriendThere = Resources.getString(Resources.NAV_ITEM_SMS_MYT);
    private static final String itemStop        = Resources.getString(Resources.NAV_ITEM_STOP);

//    /*private */static final String CMD_SET_CURRENT = Resources.getString(Resources.NAV_CMD_SET_AS_ACTIVE);
//    /*private */static final String CMD_SHOW_ALL = Resources.getString(Resources.NAV_CMD_SHOW_ALL);
//    /*private */static final String CMD_HIDE_ALL = Resources.getString(Resources.NAV_CMD_HIDE_ALL);
//    /*private */static final String CMD_NAVIGATE_ALONG = Resources.getString(Resources.NAV_CMD_ROUTE_ALONG);
//    /*private */static final String CMD_NAVIGATE_BACK = Resources.getString(Resources.NAV_CMD_ROUTE_BACK);
//    /*private */static final String CMD_NAVIGATE_TO = Resources.getString(Resources.NAV_CMD_NAVIGATE_TO);
//    /*private */static final String CMD_GO_TO = Resources.getString(Resources.NAV_CMD_GO_TO);
//    /*private */static final String CMD_EDIT = Resources.getString(Resources.NAV_CMD_EDIT);
//    /*private */static final String CMD_ENTER = Resources.getString(Resources.NAV_CMD_ADD);
//    /*private */static final String CMD_RECORD = Resources.getString(Resources.NAV_CMD_SAVE);
//    /*private */static final String CMD_UPDATE = Resources.getString(Resources.NAV_CMD_UPDATE);
//    /*private */static final String CMD_DELETE = Resources.getString(Resources.NAV_CMD_DELETE);

    private final /*Navigator*/Desktop navigator;

    private Hashtable stores;
    private Hashtable memoryStores;
    private Vector memoryFilenames;
    private String currentName, inUseName;
    private Vector currentWpts, inUseWpts;

    private Displayable list;
    private final int[] idx;
    private int depth;

    private Command cmdBack;
    private Command cmdOpen, cmdNavigateTo, cmdNavigateAlong, cmdNavigateBack, cmdSetAsCurrent,
                    cmdGoTo, cmdShowAll, cmdHideAll;

    private static Waypoints instance;

    public static void initialize(/*Navigator*/final Desktop navigator) {
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
        super(Resources.getString(Resources.DESKTOP_CMD_NAVIGATION), List.IMPLICIT);
        this.navigator = navigator;
        this.idx = new int[3];
        this.cmdBack = new Command(Resources.getString(Resources.CMD_BACK), Command.BACK, 1);
        this.cmdOpen = new Command(Resources.getString(Resources.DESKTOP_CMD_SELECT), Command.ITEM, 1);
        this.cmdNavigateTo = new ActionCommand(Resources.NAV_CMD_NAVIGATE_TO, Command.ITEM, 2);
        this.cmdNavigateAlong = new ActionCommand(Resources.NAV_CMD_ROUTE_ALONG, Command.ITEM, 3);
        this.cmdNavigateBack = new ActionCommand(Resources.NAV_CMD_ROUTE_BACK, Command.ITEM, 4);
        this.cmdSetAsCurrent = new ActionCommand(Resources.NAV_CMD_SET_AS_ACTIVE, Command.ITEM, 2);
        this.cmdShowAll = new ActionCommand(Resources.NAV_CMD_SHOW_ALL, Command.ITEM, 5);
        this.cmdHideAll = new ActionCommand(Resources.NAV_CMD_HIDE_ALL, Command.ITEM, 5);
        this.cmdGoTo = new ActionCommand(Resources.NAV_CMD_GO_TO, Command.ITEM, 6);
        this.setFitPolicy(Choice.TEXT_WRAP_OFF);
        this.setCommandListener(this);
        this.addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Command.BACK, 1));
        this.prepare();
    }

    private void stopLogs() {
        for (final Enumeration e = memoryStores.elements(); e.hasMoreElements(); ) {
            final GpxTracklog gpx = (GpxTracklog) e.nextElement();
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("shutdown " + gpx.getFileName() + "; alive? " + gpx.isAlive());
//#endif
            try {
                if (gpx.isAlive()) {
                    gpx.shutdown();
                }
                gpx.join();
            } catch (InterruptedException exc) {
                // ignore - should not happen
            }
        }
    }

    private void prepare() {
        // init collection
        stores = new Hashtable(3);
        memoryStores = new Hashtable(3);
        memoryFilenames = new Vector(3);

        // init memory waypoints
        stores.put(USER_CUSTOM_STORE, new NakedVector(16, 16));
        stores.put(USER_RECORDED_STORE, new NakedVector(16, 16));
        stores.put(USER_FRIENDS_STORE, new NakedVector(16, 16));

/*
        // do we have in-jar waypoint(s) resource?
        int type = TYPE_GPX;
        InputStream in = Waypoints.class.getResourceAsStream("/resources/waypoints.gpx");
        if (in == null) {
            type = TYPE_LOC;
            in = Waypoints.class.getResourceAsStream("/resources/waypoint.loc");
        }

        // if yes, load it
        if (in != null) {
            try {
                stores.put(USER_INJAR_STORE, parseWaypoints(in, type));
            } catch (Throwable t) {
                Desktop.showError(Resources.getString(Resources.NAV_MSG_LOAD_INJAR_FAILED), t, null);
            } finally {
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
*/
    }

    public void show() {
        // let's start with basic menu
        depth = 0;
        list = this;
        menu(0);
    }

    public void showCurrent() {
        // show current store, if any...
        if (inUseWpts != null) {
            depth = 2;
            list = listWaypoints(inUseName, inUseWpts);
        } else if (currentWpts != null) {
            depth = 2;
            list = listWaypoints(currentName, currentWpts);
        } else {
            depth = 0;
            list = this;
        }
        menu(depth);
    }

    private void menu(final int depth) {
        switch (this.depth = depth) {
            case 0: {
                // clear list
                deleteAll();
                // create menu
                append(itemListStores, null);
                append(itemAddNew, null);
                append(itemEnterCustom, null);
                if (cz.kruch.track.TrackingMIDlet.jsr120) {
                    if (navigator.isTracking()) {
                        append(itemFriendHere, null);
                    }
                    append(itemFriendThere, null);
                }
                if (navigator.getNavigateTo() != null) {
                    append(itemStop, null);
                }
            } break;
            case 1: {
                // set last known choice
                if (idx[depth] < ((SmartList) list).size()) {
                    ((SmartList) list).setSelectedIndex(idx[depth], true);
                }
            } break;
            case 2: {
                // set last known choice
                if (currentName.equals(inUseName)) {
                    if (idx[depth] < ((SmartList) list).size()) {
                        ((SmartList) list).setSelectedIndex(idx[depth], true);
                    }
                }
            }
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
                        list = null; // gc hint
                        list = this;
                        menu(depth);
                    } break;
                    case 1: {
                        onBackground(null);
                    } break;
                }
            }
        } else {
            // depth-specific action
            switch (depth) {
                case 0: { // main menu
                    // get command item
                    final String item = ((List) list).getString(idx[depth] = ((List) list).getSelectedIndex());
                    // exec
                    mainMenuCommandAction(item);
                } break;
                case 1: { // store action
                    // get store name
                    final String item = ((SmartList) list).getString(idx[depth] = ((SmartList) list).getSelectedIndex());
                    // list store
                    if (List.SELECT_COMMAND == command || cmdOpen == command) {
                        onBackground(item);
                    }
                } break;
                case 2: { // wpt action
                    // selected wpt index
                    final int i = ((SmartList) list).getSelectedIndex();
                    // exec command
                    if (List.SELECT_COMMAND == command || cmdOpen == command) {
                        // remember idx
                        if (currentName.equals(inUseName)) {
                            idx[depth] = i;
                        }
                        // open waypoint form
                        (new WaypointForm((Waypoint) currentWpts.elementAt(i), this)).show();
                    } else if (cmdNavigateTo == command) {
                        // remember idx
                        idx[depth] = i;
                        // same as action in waypoint form
                        invoke(new Object[]{ new Integer(Resources.NAV_CMD_NAVIGATE_TO), null }, null, this);
                    } else if (cmdNavigateAlong == command) {
                        // remember idx
                        idx[depth] = i;
                        // same as action in waypoint form
                        invoke(new Object[]{ new Integer(Resources.NAV_CMD_ROUTE_ALONG), null }, null, this);
                    } else if (cmdNavigateBack == command) {
                        // remember idx
                        idx[depth] = i;
                        // same as action in waypoint form
                        invoke(new Object[]{ new Integer(Resources.NAV_CMD_ROUTE_BACK), null }, null, this);
                    } else if (cmdSetAsCurrent == command) {
                        // remember idx
                        idx[depth] = i;
                        // same as action in waypoint form
                        invoke(new Object[]{ new Integer(Resources.NAV_CMD_SET_AS_ACTIVE), null }, null, this);
                    } else if (cmdGoTo == command) {
                        // same as action in waypoint form
                        invoke(new Object[]{ new Integer(Resources.NAV_CMD_GO_TO), null }, null, this);
                    } else if (cmdShowAll == command) {
                        // restore navigator
                        Desktop.display.setCurrent(navigator);
                        // call navigator
                        navigator.setVisible(currentWpts, true);
                    } else if (cmdHideAll == command) {
                        // restore navigator
                        Desktop.display.setCurrent(navigator);
                        // call navigator
                        navigator.setVisible(currentWpts, false);
                    }
                } break;
            }
        }
    }

    private void mainMenuCommandAction(final String item) {
        // menu action
        if (itemListStores.equals(item)) {
            onBackground(null);
        } else if (itemAddNew.equals(item)) {
            // only when tracking
            if (navigator.isTracking()) {
                // got location?
                Location location = navigator.getLocation();
                if (location != null) {
                    // force location to be gpx-logged
                    navigator.saveLocation(location);
                    // open form with current location
                    (new WaypointForm(location, this)).show();
                } else {
                    Desktop.showInfo(Resources.getString(Resources.NAV_MSG_NO_POS_YET), this);
                }
            } else {
                Desktop.showInfo(Resources.getString(Resources.NAV_MSG_NOT_TRACKING), this);
            }
        } else if (itemEnterCustom.equals(item)) {
            // got position?
            QualifiedCoordinates pointer = navigator.getPointer();
            if (pointer == null) {
                Desktop.showInfo(Resources.getString(Resources.NAV_MSG_NO_POS_YET), this);
            } else {
                (new WaypointForm(this, pointer)).show();
            }
        } else if (itemFriendHere.equals(item)) {
            // do we have position?
            Location location = navigator.getLocation();
            if (location == null) {
                Desktop.showInfo(Resources.getString(Resources.NAV_MSG_NO_POS_YET), this);
            } else {
                (new WaypointForm(this, Friends.TYPE_IAH, location.getQualifiedCoordinates().clone())).show();
            }
        } else if (itemFriendThere.equals(item)) {
            // got position?
            QualifiedCoordinates pointer = navigator.getPointer();
            if (pointer == null) {
                Desktop.showInfo(Resources.getString(Resources.NAV_MSG_NO_POS_YET), this);
            } else {
                (new WaypointForm(this, Friends.TYPE_MYT, pointer)).show();
            }
        } else if (itemStop.equals(item)) {
            // restore navigator
            Desktop.display.setCurrent(navigator);
            // stop navigation
            navigator.setNavigateTo(null, -1, -1);
            // no in-use store
            inUseName = null;
            inUseWpts = null;
        }
    }

    /**
     * External action.
     * 
     * @param result array
     * @param throwable problem
     */
    public void invoke(final Object result, final Throwable throwable, final Object source) {
        // handle action
        if (result instanceof Object[]) { // list or waypoint form origin

            // action type
            final Object[] ret = (Object[]) result;
            final Object action = ret[0];

            // local vars
            final int idxSelected = this == list ? -1 : ((SmartList) list).getSelectedIndex();

            // execute action
            if (null == action) {

                // restore list
                Desktop.display.setCurrent(list);
                
            } else if (action instanceof Integer) { // wpt action

                switch (((Integer) action).intValue()) {

                    case Resources.NAV_CMD_ROUTE_ALONG: {
                        // restore navigator
                        Desktop.display.setCurrent(navigator);

                        // call navigator
                        navigator.setNavigateTo(currentWpts, idxSelected, -1);

                        // remember current store
                        inUseName = currentName;
                        inUseWpts = currentWpts;
                    } break;

                    case Resources.NAV_CMD_ROUTE_BACK: {
                        // restore navigator
                        Desktop.display.setCurrent(navigator);

                        // call navigator
                        navigator.setNavigateTo(currentWpts, -1, idxSelected);

                        // remember current store
                        inUseName = currentName;
                        inUseWpts = currentWpts;
                    } break;

                    case Resources.NAV_CMD_NAVIGATE_TO: {
                        // restore navigator
                        Desktop.display.setCurrent(navigator);

                        // call navigator
                        navigator.setNavigateTo(currentWpts, idxSelected, idxSelected);

                        // remember current store
                        inUseName = currentName;
                        inUseWpts = currentWpts;
                    } break;

                    case Resources.NAV_CMD_SET_AS_ACTIVE: {
                        // restore navigator
                        Desktop.display.setCurrent(navigator);

                        // call navigator
                        if (Desktop.routeDir == 1) {
                            navigator.setNavigateTo(currentWpts /* == Desktop.wpts */, idxSelected, -1);
                        } else {
                            navigator.setNavigateTo(currentWpts /* == Desktop.wpts */, -1, idxSelected);
                        }
                    } break;

                    case Resources.NAV_CMD_GO_TO: {
                        // restore navigator
                        Desktop.display.setCurrent(navigator);

                        // call navigator
                        navigator.goTo((Waypoint) currentWpts.elementAt(idxSelected));
                    } break;

                    case Resources.NAV_CMD_ADD: {
                        // add waypoint, possibly save
                        (new YesNoDialog(this, this, ret[1], Resources.getString(Resources.NAV_MSG_PERSIST_WPT), null)).show();
                    } break;

                    case Resources.NAV_CMD_SAVE: {
                        // restore navigator
                        Desktop.display.setCurrent(navigator);

                        // add waypoint to memory store
                        addToStore(USER_RECORDED_STORE, (Waypoint) ret[1], true);
                    } break;

                    case Resources.NAV_CMD_UPDATE: {
                        // update current store
                        updateCurrentStore();
                    } break;

                    case Resources.NAV_CMD_DELETE: {
                        // remove selected wpt
                        currentWpts.removeElementAt(idxSelected);
                        if (idxSelected > 0) {
                            ((SmartList) list).setSelectedIndex(idxSelected - 1, true);
                        }

                        // update current store
                        updateCurrentStore();
                    } break;

                    case Resources.NAV_CMD_SEND: {
                        // restore navigator
                        Desktop.display.setCurrent(navigator);

                        // vars
                        final String type = (String) ret[3];
                        final QualifiedCoordinates qc;
                        final long time;

                        // get message type and location
                        if (Friends.TYPE_IAH == type) { // '==' is OK
                            time = navigator.getLocation().getTimestamp();
                            qc = navigator.getLocation().getQualifiedCoordinates().clone();
                        } else { // Friends.TYPE_MYT
                            time = System.currentTimeMillis();
                            qc = navigator.getPointer();
                        }

                        // send the message
                        Friends.send((String) ret[1], type, (String) ret[2], qc, time);
                    } break;
                    
                    default:
                        throw new IllegalArgumentException("Unknown wpt action: " + action);
                }

            } else {
                throw new IllegalArgumentException("Unknown waypoint form invocation; result = " + result + "; throwable = " + throwable);
            }

        } else if (source instanceof Friends) { // SMS received

            // add waypoint to store
            addToStore(USER_FRIENDS_STORE, (Waypoint) result, true);

        } else if (source instanceof GpxTracklog) { // waypoint recording notification

            // handle GPX event for store
            try {
                if (throwable == null) {
                    switch (((Integer) result).intValue()) {
                        case GpxTracklog.CODE_RECORDING_STOP:
                        case GpxTracklog.CODE_RECORDING_START:
                            // don't bother
                            break;
                        case GpxTracklog.CODE_WAYPOINT_INSERTED:
                            Desktop.showConfirmation(Resources.getString(Resources.NAV_MSG_WPT_RECORDED), null);
                            break;
                    }
                } else {
                    Desktop.showWarning(result == null ? Resources.getString(Resources.NAV_MSG_WPT_RECORD_ERROR) : (String) result,
                                        throwable, null);
                }
            } catch (IllegalArgumentException e) {
                // current displayable is an alert :-(((
                // conflicts:
                // 1. notification in Friends when SMS is received
                // 2. yes/no dialog "persist waypoint?"
            }
        } else {
            throw new IllegalArgumentException("Unknown invocation; result = " + result + "; throwable = " + throwable);
        }
    }


    /**
     * "Do you want to persist custom waypoint?"
     * @param answer answer
     */
    public void response(final int answer, final Object closure) {
        // add waypoint to store
        addToStore(USER_CUSTOM_STORE, (Waypoint) closure, YesNoDialog.YES == answer);

        // confirm adding to store; persistent wpt is confirmed elsewhere
        if (YesNoDialog.NO == answer) {
            Desktop.showConfirmation(Resources.getString(Resources.NAV_MSG_WPT_ENLISTED), list);
        }
    }

    /**
     * Background I/O operations.
     */
    public void run() {
        if (_storeUpdate == null) {
            if (_storeName == null) {
                actionListStores();
            } else {
                actionListStore();
            }
        } else {
            actionUpdateStore(currentName, currentWpts);
        }
    }

    private String _storeName;
    private GpxTracklog _storeUpdate;

    /**
     * Background task launcher.
     */
    private void onBackground(final String storeName) {
        this._storeName = null; // gc hint
        this._storeName = storeName;
        LoaderIO.getInstance().enqueue(this);
    }

    /**
     * Lists landmark stores.
     */
    private void actionListStores() {
        NakedVector v = new NakedVector(64, 64);

        // add memory stores
        listKnown(v, USER_RECORDED_STORE);
        listKnown(v, USER_CUSTOM_STORE);
        listKnown(v, USER_FRIENDS_STORE);
/*
        listKnown(v, USER_INJAR_STORE);
*/
        final int left = v.size();

        // list persistent stores
        if (Config.dataDirExists/* && File.isFs()*/) {

            // may take some time - start ticker
            list.setTicker(new Ticker(Resources.getString(Resources.NAV_MSG_TICKER_LISTING)));

            try {

                // list file stores
                listWptFiles("", v, true);

            } catch (Throwable t) {

                // show error
                Desktop.showError(Resources.getString(Resources.NAV_MSG_LIST_STORES_FAILED), t, null);

            } finally {

                // remove ticker
                list.setTicker(null);
            }
        }

        // got anything?
        if (v.size() == 0) {

            // notify user
            Desktop.showInfo(Resources.getString(Resources.NAV_MSG_NO_STORES), this);

        } else {

            // sort file stores (leaves memory stores at the beginning)
            FileBrowser.quicksort(v.getData(), left, v.size() - 1);

            // setup icons for stores
/* // TODO support icons in SmartList
            for (int i = strs.length; --i >= 0; ) {
                final String store = strs[i];
                if (i >= left) {
                    imgs[i] = store.equals(inUseName) ? NavigationScreens.stores[FRAME_XMLA] : NavigationScreens.stores[FRAME_XML];
                } else {
                    imgs[i] = store.equals(inUseName) ? NavigationScreens.stores[FRAME_MEMA] : NavigationScreens.stores[FRAME_MEM];
                }
            }

*/
            // create UI list
            list = listStores(v);

            // list stores
            menu(1);
        }
    }

    /**
     * Loads waypoints from file landmark store.
     */
    private void actionListStore() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("list store: " + _storeName);
//#endif

        // local
        Vector wpts = null;
        Throwable parseException = null;

        // got store in cache?
        final Vector wptsCached = (Vector) stores.get(_storeName);
        if (wptsCached == null) { // no, load from file

            // parse XML-based store
            File file = null;

            // start ticker
            list.setTicker(new Ticker(Resources.getString(Resources.NAV_MSG_TICKER_LOADING)));

            try {

                // open file
                file = File.open(Config.getFolderURL(Config.FOLDER_WPTS) + _storeName);

                // parse new waypoints
                final int i = _storeName.lastIndexOf('.');
                if (i > -1) {
                    final String lcname = _storeName.toLowerCase();
                    if (lcname.endsWith(SUFFIX_GPX)) {
                        wpts = parseWaypoints(file, TYPE_GPX);
                    } else if (lcname.endsWith(SUFFIX_LOC)) {
                        wpts = parseWaypoints(file, TYPE_LOC);
                    }
                }

                // cache non-empty store
                if (wpts != null && wpts.size() > 0) {
                    stores.put(_storeName, wpts);
                }

             } catch (Throwable t) {
//#ifdef __LOG__
                t.printStackTrace();
//#endif
                // save for later
                parseException = t;

            } finally {

                // close file
                try {
                    file.close();
                } catch (Exception e) { // NPE or IOE
                    // ignore
                }

                // remove ticker
                list.setTicker(null);
            }

        } else {

            // used cached
            wpts = wptsCached;

        }

        // process result
        if (wpts == null || wpts.size() == 0) {

            // notify
            if (parseException == null) {
                Desktop.showWarning(Resources.getString(Resources.NAV_MSG_NO_WPTS_FOUND_IN) + " " + _storeName, null, null);
            } else {
                Desktop.showError(Resources.getString(Resources.NAV_MSG_LIST_STORE_FAILED), parseException, null);
            }

        } else {

            try {
                // create list
                list = listWaypoints(_storeName, wpts);

                // remember current store
                currentWpts = wpts;
                currentName = _storeName;

                // notify user (if just loaded)
                if (wptsCached == null) {
                    Desktop.showInfo(wpts.size() + " " + Resources.getString(Resources.NAV_MSG_WPTS_LOADED), list);
                }

                // update menu
                menu(2);

            } catch (Throwable t) {
                Desktop.showError(Resources.getString(Resources.NAV_MSG_LIST_STORE_FAILED), t, null);
            }
        }
    }

    private void actionUpdateStore(final String name, final Vector wpts) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("action update store");
//#endif

        // wait screen
        Desktop.showWaitScreen(Resources.getString(Resources.DESKTOP_CMD_NAVIGATION),
                               Resources.getString(Resources.DESKTOP_MSG_IN_PROGRESS));

        // rename does not work for subfolders
        final int ipath = name.indexOf(File.PATH_SEPCHAR);

        // construct revision name
        final StringBuffer sb = new StringBuffer(32);
        sb.append(name.substring(ipath + 1, name.length() - SUFFIX_LENGTH));
        sb.append(".rev_").append(GpxTracklog.dateToFileDate(new Date().getTime()));
        sb.append(name.substring(name.length() - SUFFIX_LENGTH));
        final String newName = sb.toString();

        // execution status
        Throwable status = null;

        // create file revision
        if (Config.makeRevisions) {
            File f = null;
            try {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("creating revision " + newName);
//#endif
                f = File.open(Config.getFolderURL(Config.FOLDER_WPTS) + name, Connector.READ_WRITE);
                f.rename(newName);
            } catch (Exception e) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("failed: " + e);
//#endif
                status = e;
            } finally {
                if (f != null) {
                    try {
                        f.close();
                    } catch (IOException e) {
                        // ignore
                    }
                    f = null;
                }
            }
        }

        // revision created? go on...
        if (status == null) {

            // set filename
            if (name.endsWith(SUFFIX_LOC)) { // only GPX serialization is supported
                _storeUpdate.setFileName(name.substring(0, name.length() - SUFFIX_LENGTH) + SUFFIX_GPX);
            } else {
                _storeUpdate.setFileName(name);
            }

            // serialize store to GPX
            status = _storeUpdate.open();
            if (status == null) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("store opened for writing");
//#endif

                // get array
                final Object[] elements = ((NakedVector) wpts).getData();
                try {
                    // save all wpts
                    for (int N = wpts.size(), i = 0; i < N; i++) {
                        _storeUpdate.writeWpt((Waypoint) elements[i]);
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("wpt written " + elements[i]);
//#endif
                    }
                } catch (Exception e) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.error("error writting wpt: " + e);
//#endif
                    // flash status
                    status = e;
                } finally {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("close GPX");
//#endif
                    // safe operation
                    _storeUpdate.close();
                }
            }
        }

        // notify about result
        if (status == null) {
            Desktop.showConfirmation(Resources.getString(Resources.NAV_MSG_STORE_UPDATED),
                                     list);
        } else {
            Desktop.showError(Resources.getString(Resources.NAV_MSG_STORE_UPDATE_FAILED),
                              status, list);
        }

        // cleanup
        _storeUpdate = null; // gc hint
    }

    private void listKnown(final Vector v, final String key) {
        if (stores.containsKey(key)) {
            if (((Vector) stores.get(key)).size() > 0) {
                v.addElement(key);
            }
        }
    }

    private void addToStore(final Object storeKey, final Waypoint wpt,
                            final boolean save) {
        // add to store
        ((Vector) stores.get(storeKey)).addElement(wpt);

        // save?
        if (save) {

            // start GPX log if needed
            GpxTracklog gpx = (GpxTracklog) memoryStores.get(storeKey);
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
                memoryStores.put(storeKey, gpx);
                memoryFilenames.addElement(gpx.getFileName());
            }

            // record waypoint
            gpx.insert(wpt);
            
        } else {

            // release user object
            wpt.setUserObject(null);

        }
    }

    private void updateCurrentStore() {
        // check if it is file store
        if (!currentName.startsWith("<w")) {
            
            // instantiate GPX
            _storeUpdate = new GpxTracklog(GpxTracklog.LOG_WPT, this,
                                           navigator.getTracklogCreator(),
                                           navigator.getTracklogTime());
            // do the rest on background
            LoaderIO.getInstance().enqueue(this);

        } else {

            // restore mgmt UI
            Desktop.display.setCurrent(list);
        }
    }

    private void listWptFiles(final String path, final Vector v, final boolean recursive) throws IOException {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("list " + path);
//#endif

        File dir = null;
        try {
            // open directory
            dir = File.open(Config.getFolderURL(Config.FOLDER_WPTS) + path);

            // list file stores in the directory
            if (dir.exists()) {

                // local ref
                final Vector hidden = memoryFilenames;

                // iterate over directory
                for (final Enumeration e = dir.list(); e.hasMoreElements(); ) {

                    // has suffix?
                    final String name = (String) e.nextElement();
                    final int i = name.lastIndexOf('.');
                    if (i > -1) {
                        final String lcname = name.toLowerCase();
                        if (lcname.endsWith(SUFFIX_GPX) || lcname.endsWith(SUFFIX_LOC)) {
                            if (recursive) {
                                if (!hidden.contains(name)) { // filter memory stores 'backends'
                                    v.addElement(name);
                                }
                            } else {
                                v.addElement(path + name);
                            }
                        }
                    } else if (recursive) { // is subfolder?
                        if (/* isDir: */name.endsWith(File.PATH_SEPARATOR) && !name.startsWith("images-")) {
                            listWptFiles(name, v, false);
                        }
                    }
                }
            }

        } catch (Throwable t) {

            // show error
            Desktop.showError(Resources.getString(Resources.NAV_MSG_LIST_STORES_FAILED), t, null);

        } finally {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("~list " + path);
//#endif

            // close dir
            try {
                dir.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
        }
    }

    private Displayable listStores(final Vector stores) {
        // create UI list
        final SmartList l = new SmartList(Resources.getString(Resources.NAV_STORES), this);
        l.setData(stores);

        // add commands
        l.addCommand(cmdOpen);
        l.addCommand(cmdBack);
        l.setCommandListener(this);

        return l;
    }

    private Displayable listWaypoints(final String store, final Vector wpts) {
        // create list
        final SmartList l = new SmartList((new StringBuffer(32)).append(store).append(" [").append(wpts.size()).append(']').toString(), this);
        l.setData(wpts);

        // set selected
        if (Desktop.wpts == wpts && Desktop.wptIdx > -1) {
            l.setSelectedIndex(Desktop.wptIdx, true);
            l.setMarked(Desktop.wptIdx);
        }

        // add commands
        l.addCommand(cmdOpen);
        if (Desktop.wpts == wpts) {
            if (Desktop.routeDir != 0) {
                l.addCommand(cmdSetAsCurrent);
            } else {
                l.addCommand(cmdNavigateTo);
                l.addCommand(cmdNavigateAlong);
                l.addCommand(cmdNavigateBack);
            }
            if (Desktop.showall) {
                l.addCommand(cmdHideAll);
            } else if (Desktop.routeDir == 0) {
                l.addCommand(cmdShowAll);
            }
        } else if (Desktop.wpts == null || Desktop.wptIdx == -1)/*if (Desktop.wpts == null)*/ {
            l.addCommand(cmdNavigateTo);
            l.addCommand(cmdNavigateAlong);
            l.addCommand(cmdNavigateBack);
            l.addCommand(cmdShowAll);
        }

/*
        if (Desktop.wpts == wpts && Desktop.showall) {
            l.addCommand(cmdHideAll);
        } else if (Desktop.wpts == null || (Desktop.wpts == wpts && Desktop.routeDir == 0)) {
            l.addCommand(cmdShowAll);
        }
*/

        l.addCommand(cmdGoTo);
        l.addCommand(cmdBack);
        l.setCommandListener(this);
        
        return l;
    }

    private static Vector parseWaypoints(final File file, final int fileType)
            throws IOException, XmlPullParserException {
        InputStream in = null;

        try {
            return parseWaypoints(in = new BufferedInputStream(file.openInputStream(), 4096), fileType);
        } finally {
            try {
                in.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
        }
    }

    private static Vector parseWaypoints(final InputStream in, final int fileType)
            throws IOException, XmlPullParserException {

        // result
        final Vector result = new NakedVector(16, 64);

        // name cache
        final String[] NAME_CACHE = {
            TAG_WPT, TAG_RTEPT, TAG_TRKPT, TAG_NAME, TAG_CMT, TAG_DESC, TAG_SYM, ATTR_LAT, ATTR_LON,
            TAG_GS_CACHE, TAG_GS_TYPE, TAG_GS_CONTAINER, TAG_GS_DIFF, TAG_GS_TERRAIN,
            TAG_GS_SHORTL, TAG_GS_LONGL, TAG_GS_COUNTRY, TAG_GS_HINTS, ATTR_GS_ID
        };

        // parse XML
        final KXmlParser parser = new KXmlParser(NAME_CACHE);
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        try {
            parser.setInput(in, null); // null is for encoding autodetection
            if (TYPE_GPX == fileType) {
                parseGpx(parser, result);
            } else if (TYPE_LOC == fileType) {
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

    private static void parseGpx(final KXmlParser parser, final Vector v)
            throws IOException, XmlPullParserException {

        int depth = 0;
        float alt = Float.NaN;
        double lat = -1D, lon = -1D;
        String name = null, cmt = null, sym = null, link = null;
        GroundspeakBean gsbean = null;

        for (int eventType = parser.next(); eventType != XmlPullParser.END_DOCUMENT; eventType = parser.next()) {
            switch (eventType) {
                case XmlPullParser.START_TAG: {
                    switch (depth) {
                        case 0: {
                            final String tag = parser.getName();
                            if (TAG_WPT.equals(tag) || TAG_RTEPT.equals(tag) || TAG_TRKPT.equals(tag)){
                                // start level
                                depth = 1;
                                // get lat and lon
                                lat = Double.parseDouble(parser.getAttributeValue(null, ATTR_LAT));
                                lon = Double.parseDouble(parser.getAttributeValue(null, ATTR_LON));
                            }
                        } break;
                        case 1: {
                            final String tag = parser.getName();
                            if (TAG_NAME.equals(tag)) {
                                // get name
                                name = parser.nextText();
                            } else if (TAG_CMT.equals(tag)) {
                                // get comment
                                cmt = parser.nextText();
                            } else if (TAG_DESC.equals(tag) && cmt == null) {
                                // get description
                                cmt = parser.nextText();
                            } else if (TAG_ELE.equals(tag)) {
                                // get elevation
                                alt = Float.parseFloat(parser.nextText());
                            } else if (TAG_SYM.equals(tag)) {
                                // get sym
                                sym = parser.nextText();
                            } else if (TAG_LINK.equals(tag)) {
                                // get link
                                link = parser.getAttributeValue(null, ATTR_HREF);
                            } else if (TAG_GS_CACHE.equals(tag)) {
                                // groundspeak
                                depth = 2;
                                // create bean
                                gsbean = new GroundspeakBean(parser.getAttributeValue(null, ATTR_GS_ID));
                            } else {
                                // skip
                                parser.skipSubTree();
                            }
                        } break;
                        case 2: {
                            final String tag = parser.getName();
                            if (TAG_NAME.equals(tag)) {
                                // get GS name
                                gsbean.name = parser.nextText();
                            } else if (TAG_GS_TYPE.equals(tag)) {
                                // get GS type
                                gsbean.type = parser.nextText();
                            } else if (TAG_GS_CONTAINER.equals(tag)) {
                                // get GS container
                                gsbean.container = parser.nextText();
                            } else if (TAG_GS_DIFF.equals(tag)) {
                                // get GS difficulty
                                gsbean.difficulty = parser.nextText();
                            } else if (TAG_GS_TERRAIN.equals(tag)) {
                                // get GS terrain
                                gsbean.terrain = parser.nextText();
                            } else if (TAG_GS_COUNTRY.equals(tag)) {
                                // get GS terrain
                                gsbean.country = parser.nextText();
                            } else if (TAG_GS_SHORTL.equals(tag)) {
                                // get GS short listing
                                gsbean.shortListing = parser.nextText();
                            } else if (TAG_GS_LONGL.equals(tag)) {
                                // get GS long listing
                                gsbean.longListing = parser.nextText();
                            } else if (TAG_GS_HINTS.equals(tag)) {
                                // get GS long listing
                                gsbean.encodedHints = parser.nextText().trim();
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
                    switch (depth) {
                        case 1: {
                            final String tag = parser.getName();
                            if (TAG_WPT.equals(tag) || TAG_RTEPT.equals(tag) || TAG_TRKPT.equals(tag)) {

                                // got anonymous wpt?
                                if (name == null || name.length() == 0) {
                                    final StringBuffer sb = new StringBuffer(32);
                                    sb.append('#');
                                    NavigationScreens.append(sb, v.size(), 1000);
                                    name = sb.toString();
                                }

                                // add to list
                                final Waypoint wpt = new Waypoint(QualifiedCoordinates.newInstance(lat, lon, alt), name, cmt, sym);
                                wpt.setLinkPath(link);
                                wpt.setUserObject(gsbean);
                                v.addElement(wpt);

                                // reset depth
                                depth = 0;

                                // reset temps
                                alt = Float.NaN;
                                lat = lon = -1D;
                                name = cmt = link = null;
                            }
                        } break;
                        case 2: {
                            final String tag = parser.getName();
                            if (TAG_GS_CACHE.equals(tag)) {
                                // back to <wpt> level
                                depth = 1;
                            }
                        } break;
                        default:
                            // up one level
                            if (depth > 0) {
                                depth--;
                            }
                    }
                } break;
            }
        }
    }

    private static void parseLoc(final KXmlParser parser, final Vector v)
            throws IOException, XmlPullParserException {

        int depth = 0;
        double lat, lon;
        String name, comment;

        name = comment = null;
        lat = lon = -1D;

        for (int eventType = parser.next(); eventType != XmlPullParser.END_DOCUMENT; eventType = parser.next()) {
            switch (eventType) {
                case XmlPullParser.START_TAG: {
                    switch (depth) {
                        case 0: {
                            final String tag = parser.getName();
                            if (TAG_WAYPOINT.equals(tag)) {
                                // start level
                                depth = 1;
                            }
                        } break;
                        case 1: {
                            final String tag = parser.getName();
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
                        final String tag = parser.getName();
                        if (TAG_WAYPOINT.equals(tag)) {
                            // got wpt
                            v.addElement(new Waypoint(QualifiedCoordinates.newInstance(lat, lon),
                                                      name, comment, null));
                            // reset depth
                            depth = 0;
                            // reset temps
                            lat = lon = -1D;
                            name = comment = null;
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
