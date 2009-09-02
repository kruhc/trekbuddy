// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.Resources;
import cz.kruch.track.event.Callback;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.location.GpxTracklog;
import cz.kruch.track.location.GroundspeakBean;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.util.NakedVector;

import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Ticker;
import javax.microedition.lcdui.Choice;
import javax.microedition.io.Connector;

import api.io.BufferedInputStream;
import api.location.QualifiedCoordinates;
import api.location.Location;
import api.file.File;
import api.util.Comparator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Date;

import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParser;
import org.kxml2.io.HXmlParser;

/**
 * Navigation manager.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class Waypoints implements CommandListener,
                                        Runnable,
                                        Callback,
                                        Comparator,
                                        YesNoDialog.AnswerListener {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Waypoints");
//#endif

    private static final int TYPE_GPX       = 0;
    private static final int TYPE_LOC       = 1;

    private static final int SORT_BYORDER   = 0;
    private static final int SORT_BYNAME    = 1;
    private static final int SORT_BYDIST    = 2;

    private static final int SUFFIX_LENGTH  = 4;

/*
    private static final int FRAME_XML  = 0;
    private static final int FRAME_XMLA = 1;
    private static final int FRAME_MEM  = 2;
    private static final int FRAME_MEMA = 3;
*/

//    private static final String USER_CUSTOM_STORE   = "<custom>";
//    private static final String USER_RECORDED_STORE = "<recorded>";
//    private static final String USER_FRIENDS_STORE  = "<received>";
    private static final String STORE_USER      = "<user>";
    private static final String STORE_FRIENDS   = "<sms>";
    private static final String NEW_FILE_STORE  = "<new file>";

    private static final String SPECIAL_STORE_HEADING = "<";

//    private static final String PREFIX_WMAP     = "wmap-";
//    private static final String PREFIX_WSMS     = "wsms-";
//    private static final String PREFIX_WGPS     = "wgps-";
    private static final String PREFIX_USER     = "user-";
    private static final String PREFIX_FRIENDS  = "sms-";

    private static final String SUFFIX_GPX      = ".gpx";
    private static final String SUFFIX_LOC      = ".loc";

    private static final int TAG_RTEPT          = 0x067cbba7; // "rtept"
    private static final int TAG_WPT            = 0x0001ccbb; // "wpt"
    private static final int TAG_TRKPT          = 0x06981871; // "trkpt"
    private static final int TAG_NAME           = 0x00337a8b; // "name"
    private static final int TAG_CMT            = 0x0001814a; // "cmt"
    private static final int TAG_DESC           = 0x002efe91; // "desc"
    private static final int TAG_ELE            = 0x0001889e; // "ele"
    private static final int TAG_LINK           = 0x0032affa; // "link"
    private static final int TAG_WAYPOINT       = 0x29c10801; // "waypoint"
    private static final int TAG_COORD          = 0x05a73af5; // "coord"
    private static final int TAG_SYM            = 0x0001bec7; // "sym"
    private static final int TAG_EXTENSIONS     = 0x94266c14; // "extensions"

    private static final String ATTR_LAT        = "lat";
    private static final String ATTR_LON        = "lon";
    private static final String ATTR_HREF       = "href";

    private static final int TAG_GS_CACHE       = 0x05a0af82; // "cache"
    private static final int TAG_GS_TYPE        = 0x00368f3a; // "type"
    private static final int TAG_GS_CONTAINER   = 0xe7814c81; // "container"
    private static final int TAG_GS_DIFF        = 0x6d0bf7bb; // "difficulty"
    private static final int TAG_GS_TERRAIN     = 0xab281335; // "terrain"
    private static final int TAG_GS_SHORTL      = 0xf1f88cb9; // "short_description"
    private static final int TAG_GS_LONGL       = 0x97d2ceb9; // "long_description"
    private static final int TAG_GS_COUNTRY     = 0x39175796; // "country"
    private static final int TAG_GS_HINTS       = 0x20d8585b; // "encoded_hints"

    private static final String ATTR_GS_ID      = "id";

    private static final int TAG_AU_CACHE       = 0x6d790ad1; // "geocache"
    private static final int TAG_AU_SUMMARY     = 0x9146a7a6; // "summary"
    private static final int TAG_AU_DESC        = 0x993583fc; // "description"
    private static final int TAG_AU_HINTS       = 0x05eaf2cc; // "hints"

    private static final String itemWptsStores   = Resources.getString(Resources.NAV_ITEM_WAYPOINTS);
    private static final String itemTracksStores = Resources.getString(Resources.NAV_ITEM_TRACKS);
    private static final String itemAddNew       = Resources.getString(Resources.NAV_ITEM_RECORD);
    private static final String itemEnterCustom  = Resources.getString(Resources.NAV_ITEM_ENTER);
    private static final String itemFriendHere   = Resources.getString(Resources.NAV_ITEM_SMS_IAH);
    private static final String itemFriendThere  = Resources.getString(Resources.NAV_ITEM_SMS_MYT);
    private static final String itemStop         = Resources.getString(Resources.NAV_ITEM_STOP);

    private static final String actionListWpts   = Resources.getString(Resources.NAV_ITEM_WAYPOINTS);
    private static final String actionListTracks = Resources.getString(Resources.NAV_ITEM_TRACKS);
    private static final String actionListTargets = Resources.getString(Resources.NAV_MSG_SELECT_STORE);

    private static final int INITIAL_LIST_SIZE      = 128;
    private static final int INCREMENT_LIST_SIZE    = 32;

    private final /*Navigator*/Desktop navigator;
    private final Hashtable stores, backends;

    private Vector currentWpts, inUseWpts;
    private String currentName, inUseName;

    private List pane;
    private Displayable list;
    private NakedVector sortedWpts, cachedDisk;
    private String folder;
    private final Object[] idx;
    private int depth, sort, cacheDiskHint;

    private Command cmdBack, cmdCancel;
    private Command cmdOpen, cmdNavigateTo, cmdNavigateAlong, cmdNavigateBack,
                    cmdSetAsCurrent, cmdGoTo, cmdShowAll, cmdHideAll,
                    cmdSortByOrder, cmdSortByName, cmdSortByDist;

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

    private Waypoints(/*Navigator*/Desktop navigator) {
        this.navigator = navigator;
        this.backends = new Hashtable(4);
        this.stores = new Hashtable(4);
        this.idx = new Object[3];
        this.sort = Config.sort;
        this.cacheDiskHint = INITIAL_LIST_SIZE;
        this.cmdBack = new Command(Resources.getString(Resources.CMD_BACK), Desktop.BACK_CMD_TYPE, 1);
        this.cmdCancel = new Command(Resources.getString(Resources.CMD_CANCEL), Desktop.CANCEL_CMD_TYPE, 1);
        this.cmdOpen = new Command(Resources.getString(Resources.DESKTOP_CMD_SELECT), Desktop.SELECT_CMD_TYPE, 0);
        this.cmdNavigateTo = new ActionCommand(Resources.NAV_CMD_NAVIGATE_TO, Command.ITEM, 2);
        this.cmdNavigateAlong = new ActionCommand(Resources.NAV_CMD_ROUTE_ALONG, Command.ITEM, 3);
        this.cmdNavigateBack = new ActionCommand(Resources.NAV_CMD_ROUTE_BACK, Command.ITEM, 4);
        this.cmdSetAsCurrent = new ActionCommand(Resources.NAV_CMD_SET_AS_ACTIVE, Command.ITEM, 2);
        this.cmdShowAll = new ActionCommand(Resources.NAV_CMD_SHOW_ALL, Command.ITEM, 5);
        this.cmdHideAll = new ActionCommand(Resources.NAV_CMD_HIDE_ALL, Command.ITEM, 5);
        this.cmdGoTo = new ActionCommand(Resources.NAV_CMD_GO_TO, Command.ITEM, 6);
        this.cmdSortByOrder = new ActionCommand(Resources.NAV_CMD_SORT_BYORDER, Command.ITEM, 7);
        this.cmdSortByName = new ActionCommand(Resources.NAV_CMD_SORT_BYNAME, Command.ITEM, 8);
        this.cmdSortByDist = new ActionCommand(Resources.NAV_CMD_SORT_BYDIST, Command.ITEM, 9);
        this.pane = new List(Resources.getString(Resources.DESKTOP_CMD_NAVIGATION), List.IMPLICIT);
        this.pane.setFitPolicy(Choice.TEXT_WRAP_OFF);
        this.pane.setCommandListener(this);
        this.pane.addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Desktop.BACK_CMD_TYPE, 1));
    }

    public void show() {
        // let's start with basic menu
        depth = 0;
        use(pane);
        menu(0);
    }

    public void showCurrent() {
        // show current store, if any...
        if (inUseWpts != null) {
            depth = 2;
            use(listWaypoints(inUseName, inUseWpts, true));
        } else if (currentWpts != null) {
            depth = 2;
            use(listWaypoints(currentName, currentWpts, true));
        } else {
            depth = 0;
            use(pane);
        }
        menu(depth);
    }

    private void menu(final int depth) {
        switch (this.depth = depth) {
            case 0: {
                // clear list
                pane.deleteAll();
                // create menu
                pane.append(itemWptsStores, null);
                pane.append(itemTracksStores, null);
                pane.append(itemAddNew, null);
                pane.append(itemEnterCustom, null);
                if (cz.kruch.track.TrackingMIDlet.jsr120) {
                    if (navigator.isTracking()) {
                        pane.append(itemFriendHere, null);
                    }
                    pane.append(itemFriendThere, null);
                }
                if (navigator.getNavigateTo() != null) {
                    pane.append(itemStop, null);
                }
            } break;
            case 1: {
                // set last known choice
                if (idx[depth] != null) {
                    if (list instanceof SmartList) {
                        ((SmartList) list).setSelectedItem(idx[depth]);
                    } else {
                        ((List) list).setSelectedIndex(((Integer) idx[depth]).intValue(), true);
                    }
                }
            } break;
            case 2: {
                // set last known choice
                if (inUseName == null || currentName.equals(inUseName)) {
                    if (idx[depth] != null) {
                        ((SmartList) list).setSelectedItem(idx[depth]);
                    }
                }
            }
        }
        // show list
        Desktop.display.setCurrent(list);
    }

    public void commandAction(Command command, Displayable displayable) {
        final int type = command.getCommandType();
        if (Desktop.BACK_CMD_TYPE == type || Desktop.CANCEL_CMD_TYPE == type) {
            if (depth == 0) {
                close();
            } else {
                switch (--depth) {
                    case 0: {
                        use(pane);
                        menu(depth);
                    } break;
                    case 1: {
                        actionListStores(_listingTitle);
                    } break;
                }
            }
        } else {
            // depth-specific action
            switch (depth) {
                case 0: { // main menu
                    // get command item
                    final String item = ((List) list).getString(((List) list).getSelectedIndex());
                    idx[depth] = item;
                    // exec
                    mainMenuCommandAction(item);
                } break;
                case 1: { // store action
                    // get store name
                    final String item;
                    if (list instanceof SmartList) {
                        item = (String) ((SmartList) list).getSelectedItem();
                        idx[depth] = item;
                    } else {
                        item = ((List) list).getString(((List) list).getSelectedIndex());
                        idx[depth] = new Integer(((List) list).getSelectedIndex());
                    }
                    // store action
                    if (List.SELECT_COMMAND == command || cmdOpen == command) {
                        onBackground(item, null);
                    }
                } break;
                case 2: { // wpt action
                    // selected wpt index
                    final Waypoint item = (Waypoint) ((SmartList) list).getSelectedItem();
					// exec command
                    if (List.SELECT_COMMAND == command || cmdOpen == command) {
                        // save current depth list position
                        if (/*inUseName == null || */currentName.equals(inUseName)) {
                            idx[depth] = item;
                        }
                        // calculate distance
                        QualifiedCoordinates qc = navigator.getPointer();
                        final float distance;
                        if (qc != null) {
                            distance = qc.distance(item.getQualifiedCoordinates());
                        } else {
                            distance = Float.NaN;
                        }
                        // open waypoint form
                        (new WaypointForm(item, this, distance,
                                          folder == Config.FOLDER_WPTS && currentWpts != Desktop.wpts)).show();
                    } else if (cmdNavigateTo == command) {
                        // remember idx
                        idx[depth] = item;
                        // same as action in waypoint form
                        invoke(new Object[]{ new Integer(Resources.NAV_CMD_NAVIGATE_TO), null }, null, this);
                    } else if (cmdNavigateAlong == command) {
                        // remember idx
                        idx[depth] = item;
                        // same as action in waypoint form
                        invoke(new Object[]{ new Integer(Resources.NAV_CMD_ROUTE_ALONG), null }, null, this);
                    } else if (cmdNavigateBack == command) {
                        // remember idx
                        idx[depth] = item;
                        // same as action in waypoint form
                        invoke(new Object[]{ new Integer(Resources.NAV_CMD_ROUTE_BACK), null }, null, this);
                    } else if (cmdSetAsCurrent == command) {
                        // remember idx
                        idx[depth] = item;
                        // same as action in waypoint form
                        invoke(new Object[]{ new Integer(Resources.NAV_CMD_SET_AS_ACTIVE), null }, null, this);
                    } else if (cmdGoTo == command) {
                        // same as action in waypoint form
                        invoke(new Object[]{ new Integer(Resources.NAV_CMD_GO_TO), null }, null, this);
                    } else if (cmdShowAll == command) {
                        // close nav UI
                        close();
                        // call navigator
                        navigator.showWaypoints(currentWpts, currentName, true);
                    } else if (cmdHideAll == command) {
                        // close nav UI
                        close();
                        // call navigator
                        navigator.showWaypoints(currentWpts, currentName, false);
                    } else if (cmdSortByOrder == command) {
                        // sort
                        sortWaypoints((SmartList) list, SORT_BYORDER, false, currentWpts);
                    } else if (cmdSortByName == command) {
                        // sort
                        sortWaypoints((SmartList) list, SORT_BYNAME, false, currentWpts);
                    } else if (cmdSortByDist == command) {
                        // sort
                        sortWaypoints((SmartList) list, SORT_BYDIST, false, currentWpts);
                    }
                } break;
            }
        }
    }

    private void mainMenuCommandAction(final String item) {
        // menu action
        if (itemWptsStores.equals(item)) {
            // use "wpts/" folder
            folder = Config.FOLDER_WPTS;
            cachedDisk = null;
            // list in thread
            onBackground(null, actionListWpts);
        } else if (itemTracksStores.equals(item)) {
            // use "tracks-gpx/" folder
            folder = Config.FOLDER_TRACKS;
            cachedDisk = null;
            // list in thread
            onBackground(null, actionListTracks);
        } else if (itemAddNew.equals(item)) {
            // only when tracking
            if (navigator.isTracking()) {
                // got location?
                final Location location = navigator.getLocation();
                if (location != null) {
                    // force location to be gpx-logged
                    navigator.saveLocation(location);
                    // open form with current location
                    (new WaypointForm(location, this)).show().setTracklogTime(navigator.getTracklogTime());
                } else {
                    Desktop.showInfo(Resources.getString(Resources.NAV_MSG_NO_POS_YET), pane);
                }
            } else {
                Desktop.showInfo(Resources.getString(Resources.NAV_MSG_NOT_TRACKING), pane);
            }
        } else if (itemEnterCustom.equals(item)) {
            // got position?
            final QualifiedCoordinates pointer = navigator.getPointer();
            if (pointer == null) {
                Desktop.showInfo(Resources.getString(Resources.NAV_MSG_NO_POS_YET), pane);
            } else {
                (new WaypointForm(this, pointer)).show();
            }
//#ifndef __ANDROID__
        } else if (itemFriendHere.equals(item)) {
            // do we have position?
            final Location location = navigator.getLocation();
            if (location == null) {
                Desktop.showInfo(Resources.getString(Resources.NAV_MSG_NO_POS_YET), pane);
            } else {
                (new WaypointForm(this, cz.kruch.track.fun.Friends.TYPE_IAH,
                                  location.getQualifiedCoordinates().clone())).show();
            }
        } else if (itemFriendThere.equals(item)) {
            // got position?
            final QualifiedCoordinates pointer = navigator.getPointer();
            if (pointer == null) {
                Desktop.showInfo(Resources.getString(Resources.NAV_MSG_NO_POS_YET), pane);
            } else {
                (new WaypointForm(this, cz.kruch.track.fun.Friends.TYPE_MYT,
                                  pointer)).show();
            }
//#endif
        } else if (itemStop.equals(item)) {
            // close nav UI
            close();
            // stop navigation
            navigator.setNavigateTo(null, null, -1, -1);
            // remove in-use store from cache
            if (inUseName != null && !inUseName.equals(currentName)) {
                stores.remove(inUseName);
            }
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

            // execute action
            if (null == action) {

                // restore list
                Desktop.display.setCurrent(list);
                
            } else if (action instanceof Integer) { // wpt action

                // wpt index (in currentWpts collection)
                final int idxSelected = pane == list ? -1 : itemIdx(currentWpts, ((SmartList) list).getSelectedItem());
                
                switch (((Integer) action).intValue()) {

                    case Resources.NAV_CMD_ROUTE_ALONG: {
                        // close nav UI
                        close();

                        // call navigator
                        navigator.setNavigateTo(currentWpts, currentName, idxSelected, -1);

                        // remember current store
                        inUseName = currentName;
                        inUseWpts = currentWpts;
                    } break;

                    case Resources.NAV_CMD_ROUTE_BACK: {
                        // close nav UI
                        close();

                        // call navigator
                        navigator.setNavigateTo(currentWpts, currentName, -1, idxSelected);

                        // remember current store
                        inUseName = currentName;
                        inUseWpts = currentWpts;
                    } break;

                    case Resources.NAV_CMD_NAVIGATE_TO: {
                        // close nav UI
                        close();

                        // call navigator
                        navigator.setNavigateTo(currentWpts, currentName, idxSelected, idxSelected);

                        // remember current store
                        inUseName = currentName;
                        inUseWpts = currentWpts;
                    } break;

                    case Resources.NAV_CMD_SET_AS_ACTIVE: {
                        // close nav UI
                        close();

                        // call navigator
                        if (Desktop.routeDir == 1) {
                            navigator.setNavigateTo(currentWpts /* == Desktop.wpts */, currentName, idxSelected, -1);
                        } else {
                            navigator.setNavigateTo(currentWpts /* == Desktop.wpts */, currentName, -1, idxSelected);
                        }
                    } break;

                    case Resources.NAV_CMD_GO_TO: {
                        // close nav UI
                        close();

                        // call navigator
                        navigator.goTo((Waypoint) currentWpts.elementAt(idxSelected));
                    } break;

                    case Resources.NAV_CMD_ADD: {
                        // add waypoint, possibly save
                        addToPrefferedStore(/*USER_CUSTOM_STORE*/STORE_USER, (Waypoint) ret[1]);
                    } break;

                    case Resources.NAV_CMD_SAVE: {
                        // add waypoint to memory store
                        addToPrefferedStore(/*USER_RECORDED_STORE*/STORE_USER, (Waypoint) ret[1]);
                    } break;

                    case Resources.NAV_CMD_UPDATE: {
                        // update current store
                        updateStore(currentName, currentWpts);
                    } break;

                    case Resources.NAV_CMD_DELETE: {
                        // remove selected wpt
                        currentWpts.removeElementAt(idxSelected);
                        sortedWpts.removeElementAt(idxSelected);
                        if (idxSelected > 0) {
                            ((SmartList) list).setSelectedIndex(idxSelected - 1, true);
                        }

                        // update current store
                        updateStore(currentName, currentWpts);
                    } break;
//#ifndef __ANDROID__
                    case Resources.NAV_CMD_SEND: {
                        // close nav UI
                        close();

                        // vars
                        final String type = (String) ret[3];
                        final QualifiedCoordinates qc;
                        final long time;

                        // get message type and location
                        if (cz.kruch.track.fun.Friends.TYPE_IAH == type) { // '==' is OK
                            time = navigator.getLocation().getTimestamp();
                            qc = navigator.getLocation().getQualifiedCoordinates().clone();
                        } else { // Friends.TYPE_MYT
                            time = System.currentTimeMillis();
                            qc = navigator.getPointer();
                        }

                        // send the message
                        cz.kruch.track.fun.Friends.send((String) ret[1], type, (String) ret[2], qc, time);
                    } break;
//#endif
                    default:
                        throw new IllegalArgumentException("Unknown wpt action: " + action);
                }

            } else {
                throw new IllegalArgumentException("Unknown waypoint form invocation; result = " + result + "; throwable = " + throwable);
            }
//#ifndef __ANDROID__
        } else if (source instanceof cz.kruch.track.fun.Friends) { // SMS received

            // get result
            final Waypoint wpt = (Waypoint) result;

            // notify user
            Desktop.showAlarm(Resources.getString(Resources.DESKTOP_MSG_SMS_RECEIVED) + wpt.getName(),
                              list, !Config.autohideNotification);
            Thread.yield(); // this is safe, it is called from a thread, see Friends.execPop()

            // add waypoint to store
            addToStore(STORE_FRIENDS, null, wpt);
//#endif
        } else {
            throw new IllegalArgumentException("Unknown invocation; result = " + result + "; throwable = " + throwable);
        }
    }

    public int compare(Object o1, Object o2) {
        final Waypoint wpt1 = (Waypoint) o1;
        final Waypoint wpt2 = (Waypoint) o2;

        int cmp = 0;
        switch (sort) {
            case SORT_BYNAME: {
                cmp = wpt1.toString().compareTo(wpt2.toString());
            } break;
            case SORT_BYDIST: {
                final QualifiedCoordinates qc = _pointer;
                if (qc != null) {
                    final float d1 = qc.distance(wpt1.getQualifiedCoordinates());
                    final float d2 = qc.distance(wpt2.getQualifiedCoordinates());
                    cmp = d1 < d2 ? -1 : 1;
                }
            } break;
            default:
                throw new IllegalStateException("Illegal sorting");
        }

        return cmp;
    }

    public void response(int answer, Object closure) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("response; action = '" + _listingTitle + "'");
//#endif

        final Object[] data = (Object[]) closure;
        if (answer == YesNoDialog.YES) {
            if (data[1] instanceof StringBuffer) {
                final StringBuffer sb = (StringBuffer) data[1];
                if (!sb.toString().endsWith(SUFFIX_GPX)) {
                    sb.append(SUFFIX_GPX);
                }
                final String storeName = data[1].toString();
                getBackend(data[0], null).setFileName(storeName);
                onBackground(storeName, _listingTitle);
			} else {
                throw new IllegalStateException("Unknown source: " + data[1]);
            }
        } else {
            throw new IllegalStateException("Unexpected response: " + answer);
        }
    }

    /**
     * Background I/O operations.
     */
    public void run() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("run; " + _storeUpdate + ";" + _storeName + ";" + _listingTitle);
//#endif

        if (_storeUpdate == null) {
            if (_storeName == null) {
                actionListStores(_listingTitle);
            } else {
                if (_listingTitle == actionListWpts || _listingTitle == actionListTracks) { // '==' is OK
                    actionListStore(_storeName, false);
                } else if (_listingTitle == actionListTargets) { // '==' is OK
                    actionUpdateTarget(_storeName, _addWptStoreKey, _addWptSelf);
                }
                _storeName = null;
            }
        } else {
            actionUpdateStore(_updateName, _updateWpts);
            _storeUpdate = null;
            _updateName = null;
            _updateWpts = null;
            _addWptStoreKey = null;
            _addWptSelf = null;
        }
    }

    private volatile Waypoint _addWptSelf;
    private volatile Object _addWptStoreKey;
    private volatile QualifiedCoordinates _pointer;
    private volatile String _storeName, _updateName, _listingTitle;
    private volatile GpxTracklog _storeUpdate;
    private volatile Vector _updateWpts;
    private volatile boolean _updateRevision;

    /*
     * Background task launcher.
     */
    private void onBackground(final String storeName, final String listingTitle) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("onBackground; " + storeName + "," + listingTitle);
//#endif

        this._storeName = storeName;
        if (listingTitle != null) {
            this._listingTitle = listingTitle;
        }
        navigator.getDiskWorker().enqueue(this);
    }

    /**
     * Lists landmark stores.
     *
     * @param title list title
     */
    private void actionListStores(final String title) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("list stores as " + title);
//#endif

        final NakedVector v = new NakedVector(cacheDiskHint + 16, INCREMENT_LIST_SIZE);
        final boolean recursive;

        // offer new file when selecting target
        if (title == actionListTargets) { // '==' is OK
            v.addElement(NEW_FILE_STORE);
        }

        // list special stores first only when listing "wpts/" folder
        if (folder == Config.FOLDER_WPTS) { // '==' is OK

            // add prefered stores
            listKnown(v);

			// list "wpts/" recursively
            recursive = true;

        } else {

            // no recursion
            recursive = false;
        }

        final int left = v.size();

        // no cached disk files?
        if (cachedDisk == null) {

            // list persistent stores
            if (Config.dataDirExists) {

                try {

                    // may take some time - start ticker
                    list.setTicker(new Ticker(Resources.getString(Resources.NAV_MSG_TICKER_LISTING)));

                    // list file stores
                    listWptFiles("", cachedDisk = new NakedVector(cacheDiskHint + 16, INCREMENT_LIST_SIZE), recursive);
                    cacheDiskHint = cachedDisk.size();

                } catch (Throwable t) {

                    // show error
                    Desktop.showError(Resources.getString(Resources.NAV_MSG_LIST_STORES_FAILED), t, null);

                } finally {

                    // remove ticker
                    list.setTicker(null);
                }

            }

        }

        // now use cached
        appendCached(v);

        // got anything?
        if (v.size() == 0) {

            // notify user
            Desktop.showInfo(Resources.getString(Resources.NAV_MSG_NO_STORES), pane);

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
            use(listStores(v, title));

            // list stores
            menu(1);
        }
    }

    /**
     * Loads waypoints from file landmark store.
     */
    private Vector actionListStore(final String storeName, final boolean onBackground) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("list store: " + storeName);
//#endif

        // local
        Vector wpts = null;
        Throwable parseException = null;

        // got store in cache?
        final Vector wptsCached = (Vector) stores.get(storeName);
        if (wptsCached == null) { // no, load from file

            // parse XML-based store
            File file = null;

            try {

                // may take some time - start ticker
                if (!onBackground) {
                    list.setTicker(new Ticker(Resources.getString(Resources.NAV_MSG_TICKER_LOADING)));
                }

                // open file
                file = File.open(Config.getFolderURL(folder) + storeName);
                if (file.exists()) {

                    // parse new waypoints
                    final int i = storeName.lastIndexOf('.');
                    if (i > -1) {
                        final String lcname = storeName.toLowerCase();
                        if (lcname.endsWith(SUFFIX_GPX)) {
                            wpts = parseWaypoints(file, TYPE_GPX);
                        } else if (lcname.endsWith(SUFFIX_LOC)) {
                            wpts = parseWaypoints(file, TYPE_LOC);
                        }
                    }

                }

/* 2008-12-03: cache only in-use and current
                // cache non-empty store
                if (wpts != null && wpts.size() > 0) {
                    stores.put(_storeName, wpts);
                }
*/

             } catch (Throwable t) {
//#ifdef __LOG__
                t.printStackTrace();
//#endif
                // save for later
                parseException = t;

            } finally {

                // remove ticker
                if (!onBackground) {
                    list.setTicker(null);
                }

                // close file
                try {
                    file.close();
                } catch (Exception e) { // NPE or IOE
                    // ignore
                }
            }

        } else {

            // used cached
            wpts = wptsCached;

        }

        // return intermediate
        if (onBackground) {
            return wpts;
        }

        // process result
        if (wpts == null || wpts.size() == 0) {

            // notify
            if (parseException == null) {
                Desktop.showWarning(Resources.getString(Resources.NAV_MSG_NO_WPTS_FOUND_IN) + " " + storeName, null, list);
            } else {
                Desktop.showError(Resources.getString(Resources.NAV_MSG_LIST_STORE_FAILED), parseException, list);
            }

        } else {

            try {
                // create list
                use(listWaypoints(storeName, wpts, true));

                // remove current store from cache IF IT IS NOT in-use
                if (currentName != null && !currentName.equals(inUseName)) {
                    stores.remove(currentName);
                }

                // remember current store
                currentWpts = null; // gc hint
                currentWpts = wpts;
                currentName = null; // gc hint
                currentName = storeName;

                // cache current store IF IT IS NOT in-use (already cached)
                if (currentName != null && !currentName.equals(inUseName)) {
                    stores.put(currentName, currentWpts);
                }

                // notify user (if just loaded)
                if (wptsCached == null) {
                    Desktop.showInfo(wpts.size() + " " + Resources.getString(Resources.NAV_MSG_WPTS_LOADED), list);
                }

                // update menu
                menu(2);

            } catch (Throwable t) {
//#ifdef __LOG__
                t.printStackTrace();
//#endif
                Desktop.showError(Resources.getString(Resources.NAV_MSG_LIST_STORE_FAILED), t, list);
            }
        }

        return null;
    }

    private void actionUpdateStore(final String name, final Vector wpts) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("action update store '" + name + "'");
//#endif

        // execution status
        Throwable status = null;

		// wait screen
        Desktop.showWaitScreen(Resources.getString(Resources.DESKTOP_CMD_NAVIGATION),
                               Resources.getString(Resources.DESKTOP_MSG_IN_PROGRESS));

		try {
            
            // create file revision
            if (_updateRevision) {

                // rename does not work for subfolders
                final int ipath = name.indexOf(File.PATH_SEPCHAR);

                // construct revision name
                final StringBuffer sb = new StringBuffer(32);
                sb.append(name.substring(ipath + 1, name.length() - SUFFIX_LENGTH));
                sb.append(".rev_").append(GpxTracklog.dateToFileDate(new Date().getTime()));
                sb.append(name.substring(name.length() - SUFFIX_LENGTH));
                final String newName = sb.toString();

//#ifdef __LOG__
                if (log.isEnabled()) log.debug("creating revision " + newName);
//#endif
                // do rename
                File f = null;
                try {
                    f = File.open(Config.getFolderURL(Config.FOLDER_WPTS) + name, Connector.READ_WRITE);
                    f.rename(newName);
                } catch (Exception e) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("rename failed", e);
//#endif
                    status = e;
                } finally {
                    try {
                        f.close();
                    } catch (Exception e) { // NPE or IOE
                        // ignore
                    }
                }
            }

            // revision created or skipped? go on...
            if (status == null) {

                // set filename
                if (name.endsWith(SUFFIX_LOC)) { // only GPX serialization is supported
                    _storeUpdate.setFileName(name.substring(0, name.length() - SUFFIX_LENGTH) + SUFFIX_GPX);
                } else {
                    _storeUpdate.setFileName(name);
                }

                // serialize store to file
                status = _storeUpdate.open();
                if (status == null) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("store opened for writing: " + _storeUpdate.getFileName());
//#endif
                    // save all wpts
                    final Object[] elements = ((NakedVector) wpts).getData();
                    try {
                        for (int N = wpts.size(), i = 0; i < N; i++) {
                            _storeUpdate.writeWpt((Waypoint) elements[i]);
//#ifdef __LOG__
                            if (log.isEnabled()) log.debug("wpt written " + elements[i]);
//#endif
                        }
                    } catch (Exception e) {
//#ifdef __LOG__
                        if (log.isEnabled()) log.error("error writting wpt", e);
//#endif
                        status = e;
                    } finally {
                        _storeUpdate.close(); // safe operation
                    }
                }
            }
        } catch (Throwable t) {

            // report
            status = t;

        } finally {

            // cleanup
            _storeUpdate = null; // gc hint

            // what next
            final Displayable next;
            if (depth > 1) {
                use(next = list);
                depth = 2;
            } else {
                use(next = pane);
                depth = 0;
            }

            // notify about result
            if (status == null) {
                Desktop.showConfirmation(Resources.getString(Resources.NAV_MSG_STORE_UPDATED),
                                         next);
            } else {
                Desktop.showError(Resources.getString(Resources.NAV_MSG_STORE_UPDATE_FAILED),
                                  status, next);
            }
        }
    }

    private void actionUpdateTarget(final String name, final Object storeKey,
                                    final Waypoint wpt) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("action update target '" + name + "'");
//#endif

        Object useKey = storeKey;

        if (NEW_FILE_STORE.equals(name)) { // new store
            stores.remove(storeKey);
            backends.remove(storeKey);
            cachedDisk = null; // force refresh of file list
        } else if (name.equals(currentName)) { // current store
            useKey = name;
		} else if (!name.equals(getBackend(storeKey, null).getFileName())) { // store other than last
            stores.remove(storeKey);
            backends.remove(storeKey);
            getBackend(storeKey, null).setFileName(name); // set required filename
        }

        final GpxTracklog backend = getBackend(useKey, name);
        if (backend.getFileName() == null) {
            final String fileName = backend.getDefaultFileName();
            final StringBuffer sb = new StringBuffer(fileName);
            if (fileName.endsWith(SUFFIX_GPX)) {
                sb.delete(sb.length() - 4, sb.length());
            }
            (new YesNoDialog(null, this, new Object[]{ useKey, sb, wpt },
                             Resources.getString(Resources.NAV_MSG_ENTER_STORE_FILENAME),
                             sb)).show();
        } else {
            addToStore(useKey, name, wpt);
        }
    }

    private void listKnown(final Vector v) {
		for (final Enumeration e = backends.elements(); e.hasMoreElements(); ) {
            final String backendName = ((GpxTracklog) e.nextElement()).getFileName();
            if (backendName != null && !v.contains(backendName)) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("list known backend file " + backendName);
//#endif
                v.addElement(backendName);
            }
        }
	}

    private void addToPrefferedStore(final Object storeKey, final Waypoint wpt) {
        // set temps
        _addWptStoreKey = storeKey;
        _addWptSelf = wpt;

        // use "wpts/" folder
        folder = Config.FOLDER_WPTS;

        // list targets in thread
        onBackground(null, actionListTargets);
    }

    private boolean hideStore(final String storeName) {
		for (final Enumeration e = backends.elements(); e.hasMoreElements(); ) {
			final GpxTracklog backendName = ((GpxTracklog) e.nextElement());
            if (storeName.equals((backendName.getFileName()))) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("hide file " + storeName + " as backend file " + backendName.getFileName());
//#endif
			    return true;
			}
		}
        return false;
    }

    private GpxTracklog getBackend(final Object storeKey, final String storeName) {
        // get backend for key
        GpxTracklog gpx = (GpxTracklog) backends.get(storeKey);
        if (gpx == null) {
            gpx = new GpxTracklog(GpxTracklog.LOG_WPT, null/*this*/,
                                  navigator.getTracklogCreator(),
                                  navigator.getTracklogTime());
            if (STORE_USER.equals(storeKey)) {
                gpx.setFilePrefix(PREFIX_USER);
//            } else if (USER_RECORDED_STORE.equals(storeKey)) {
//                gpx.setFilePrefix(PREFIX_WGPS);
            } else if (STORE_FRIENDS.equals(storeKey)) {
                gpx.setFilePrefix(PREFIX_FRIENDS);
            } else {
                // assertion
                if (storeName == null) {
                    throw new NullPointerException("storeName");
                }
                // remove previous file backend
                for (Enumeration e = backends.keys(); e.hasMoreElements(); ) {
                    final String key = (String) e.nextElement();
                    if (!key.startsWith(SPECIAL_STORE_HEADING)) {
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("removing previous file backend " + key);
//#endif
                        backends.remove(key);
                        break;
                    }
                }
                // set requested filename
                gpx.setFileName(storeName);
            }

            // add to collection
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("put to backends: " + storeKey + "/" + gpx + "(" + gpx.getFileName() + ")");
//#endif
            backends.put(storeKey, gpx);
        }

        return gpx;
    }

    private void addToStore(final Object storeKey, final String storeName,
                            final Waypoint wpt) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("add " + wpt + " to store " + storeKey + "/" + storeName);
//#endif

        // add wpt to store
        Vector wpts = (Vector) stores.get(storeKey);
        if (wpts == null) {
            wpts = actionListStore(storeName, true);
        }
        if (wpts == null) {
            stores.put(storeKey, wpts = new NakedVector(16, 16));
        }
        wpts.addElement(wpt);

		// update navigator if we update store being used
        if (Desktop.wpts != null) {
            if (storeName.equals(Desktop.wptsName)) {
                navigator.routeExpanded(wpt);
            }
        }

        // update via backend
        updateStore((String) storeKey, wpts);
    }

    private void updateStore(final String name, final Vector wpts) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("update store " + name);
//#endif

        // get backend
        _storeUpdate = (GpxTracklog) backends.get(name);

        // resolve update name
        _updateName = _storeUpdate.getFileName();

        // updated collection
        _updateWpts = wpts;

        // flag update revision
        _updateRevision = name.startsWith(SPECIAL_STORE_HEADING) ? false : Config.makeRevisions;

        // fallback action
        _listingTitle = actionListWpts;

        // do the rest on background
        navigator.getDiskWorker().enqueue(this);
    }

    private void listWptFiles(final String path, final Vector v,
                              final boolean recursive) throws IOException {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("list " + path);
//#endif

        File dir = null;
        try {
            // open directory
            dir = File.open(Config.getFolderURL(folder) + path);

            // list file stores in the directory
            if (dir.exists()) {

                // iterate over directory
                for (final Enumeration e = dir.list(); e.hasMoreElements(); ) {

                    // has suffix?
                    final String name = (String) e.nextElement();
                    final int i = name.lastIndexOf('.');
                    if (i > -1) {
                        final String lcname = name.toLowerCase();
                        if (lcname.endsWith(SUFFIX_GPX) || lcname.endsWith(SUFFIX_LOC)) {
                            if (recursive) {
								v.addElement(name);
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

    private Displayable listStores(final Vector stores, final String title) {
        // create UI list
        Displayable l = null;
//#ifndef __ANDROID__
        l = new SmartList(title);
        ((SmartList) l).setData(stores);
//#else
        if (cz.kruch.track.TrackingMIDlet.android) { // always true
            final String[] strings = new String[stores.size()];
            stores.copyInto(strings);
            l = new List(title, List.IMPLICIT, strings, null);
        }
//#endif

        // add commands
        l.addCommand(cmdOpen);
        if (title == actionListWpts || title == actionListTracks) { // '==' is OK
            l.addCommand(cmdBack);
        } else if (title == actionListTargets) {
            l.addCommand(cmdCancel);
        }
        l.setCommandListener(this);

        return l;
    }

    private Displayable listWaypoints(final String store, final Vector wpts,
                                      final boolean forceSort) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("list waypoint from " + store);
//#endif

        // make sure we have backend ready
        getBackend(store, store);

        // create list
        final SmartList l = new SmartList((new StringBuffer(32)).append(store).append(" [").append(wpts.size()).append(']').toString());
        sortedWpts = null; // gc hint
        l.setData(sortedWpts = new NakedVector((NakedVector) wpts));

        // pre-sort
        sortWaypoints(l, sort, forceSort, wpts);

        // set selected
        if (Desktop.wpts == wpts && Desktop.wptIdx > -1) {
/* before sorting code
            l.setSelectedIndex(Desktop.wptIdx, true);
            l.setMarked(Desktop.wptIdx);
*/
            l.setMarked(l.setSelectedItem(wpts.elementAt(Desktop.wptIdx)));
        }

        // add commands
        l.addCommand(cmdOpen);
        if (Desktop.wpts == wpts) {
            if (Desktop.routeDir != 0) {
                l.addCommand(cmdSetAsCurrent);
            } else {
                l.addCommand(cmdNavigateTo);
                if (sort == SORT_BYORDER) {
                    l.addCommand(cmdNavigateAlong);
                    l.addCommand(cmdNavigateBack);
                }
            }
            if (Desktop.showall) {
                l.addCommand(cmdHideAll);
            } else if (Desktop.routeDir == 0) {
                l.addCommand(cmdShowAll);
            }
        } else if (Desktop.wpts == null || Desktop.wptIdx == -1) /*if (Desktop.wpts == null)*/ {
            l.addCommand(cmdNavigateTo);
            if (sort == SORT_BYORDER) {
                l.addCommand(cmdNavigateAlong);
                l.addCommand(cmdNavigateBack);
            }
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
        l.addCommand(cmdSortByOrder);
        l.addCommand(cmdSortByName);
        l.addCommand(cmdSortByDist);
        l.addCommand(cmdBack);
        l.setCommandListener(this);

        return l;
    }

    private void close() {
        // gc hints
        list = null;
        sortedWpts = null;
        // restore navigator
        Desktop.display.setCurrent(Desktop.screen);
    }

    private void appendCached(final Vector v) {
        if (cachedDisk != null) {
            final Object[] names = cachedDisk.getData();
            for (int N = cachedDisk.size(), i = 0; i < N; i++) {
				final String name = (String) names[i];
				if (!hideStore(name)) {
					v.addElement(name);
				}
			}
        }
    }

    private void use(final Displayable l) {
        // gc hint
        list = null;
        // use new
        list = l;
    }

    private void sortWaypoints(final SmartList list, final int by,
                               final boolean force, final Vector wpts) {
        // sorting criterium changed or sorting forced
        if (sort != by || force) {

            // remember criterium
            sort = by;

            //sort
            switch (sort) {
                case SORT_BYORDER: {
                    // copy refs from store
                    wpts.copyInto(sortedWpts.getData());
                    // route navigation avail
                    list.addCommand(cmdNavigateAlong);
                    list.addCommand(cmdNavigateBack);
                } break;
                case SORT_BYNAME:
                case SORT_BYDIST: {
                    // grab map position (used in by-dist sorting)
                    _pointer = navigator.getPointer();
                    // sort
                    FileBrowser.quicksort(sortedWpts.getData(), this, 0, sortedWpts.size() - 1);
                    // gc hint
                    _pointer = null;
                    // route navigation NOT avail
                    list.removeCommand(cmdNavigateAlong);
                    list.removeCommand(cmdNavigateBack);
                } break;
            }

/*
            // OK, we know that the list is using sortedWpts as backend...
            ((SmartList) list).setSelectedItem(wpt);
*/          // no, after sort, focus on 1st item
            list.setSelectedIndex(0, true);
            
            // set marked item, if any
            if (Desktop.wpts == wpts && Desktop.wptIdx > -1) {
                list.setMarked(list.getIndex(wpts.elementAt(Desktop.wptIdx)));
            }
            
            // redraw list
            list.repaint();
        }
    }

    private static int itemIdx(final Vector items, final Object item) {
        final Object[] elements = ((NakedVector) items).getData();
        for (int i = items.size(); --i >= 0;) {
            if (item.equals(elements[i])) {
                return i;
            }
        }
        return -1;
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
        final Vector result = new NakedVector(512, 512);

        // parse XML
        final HXmlParser parser = new HXmlParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        try {
            parser.setInput(in, null); // null is for encoding autodetection
            switch (fileType) {
                case TYPE_GPX: {
                    parseGpx(parser, result);
                } break;
                case TYPE_LOC: {
                    parseLoc(parser, result);
                } break;
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

    private static void parseGpx(final HXmlParser parser, final Vector v)
            throws IOException, XmlPullParserException {
        int depth = 0;
        float alt = Float.NaN;
        double lat = -1D, lon = -1D;
        char[] name = null, cmt = null, sym = null;
        final StringBuffer sb = new StringBuffer(16);
        NakedVector links = null;
        GroundspeakBean gsbean = null;

        for (int eventType = parser.next(); eventType != XmlPullParser.END_DOCUMENT; eventType = parser.next()) {
            switch (eventType) {
                case XmlPullParser.START_TAG: {
                    switch (depth) {
                        case 0: {
                            final int tag = parser.getHash();
                            switch (tag) {
                                case TAG_WPT:
                                case TAG_RTEPT:
                                case TAG_TRKPT: {
                                    // start level
                                    depth = 1;
                                    // get lat and lon
                                    lat = Double.parseDouble(parser.getAttributeValue(null, ATTR_LAT));
                                    lon = Double.parseDouble(parser.getAttributeValue(null, ATTR_LON));
                                } break;
                            }
                        } break;
                        case 1: {
                            final int tag = parser.getHash();
                            switch (tag) {
                                case TAG_CMT: {
                                    // get comment
                                    cmt = parser.nextChars();
                                } break;
                                case TAG_ELE: {
                                    // get elevation
                                    alt = Float.parseFloat(parser.nextText());
                                } break;
                                case TAG_SYM: {
                                    // get sym
                                    sym = parser.nextChars();
                                } break;
/*
                                case TAG_DESC: {
                                    // get description
                                    cmt = parser.nextText();
                                } break;
*/
                                case TAG_LINK: {
                                    // get link
                                    if (links == null) {
                                        links = new NakedVector(4, 4);
                                    }
                                    links.addElement(parser.getAttributeValue(null, ATTR_HREF));
                                } break;
                                case TAG_NAME: {
                                    // get name
                                    name = parser.nextChars();
                                } break;
                                case TAG_GS_CACHE: {
                                    // groundspeak
                                    depth = 2;
                                    // create bean
                                    gsbean = new GroundspeakBean(GpxTracklog.GS_1_0_PREFIX,
                                                                 parser.getAttributeValue(null, ATTR_GS_ID));
                                } break;
                                case TAG_AU_CACHE: {
                                    // groundspeak
                                    depth = 2;
                                    // create bean
                                    gsbean = new GroundspeakBean(GpxTracklog.AU_1_0_PREFIX, null);
                                } break;
                                case TAG_EXTENSIONS: {
                                    // groundspeak in GPX 1.1
                                } break;
                                default: {
                                    // skip
                                    parser.skipSubTree();
                                }
                            }
                        } break;
                        case 2: {
                            final int tag = parser.getHash();
                            switch (tag) {
                                case TAG_NAME: {
                                    // get GS name
                                    gsbean.setName(parser.nextText().trim());
                                } break;
                                case TAG_GS_TYPE: {
                                    // get GS type
                                    gsbean.setType(parser.nextText());
                                } break;
                                case TAG_AU_HINTS: {
                                    // get GS long listing
                                    gsbean.setEncodedHints(parser.nextText());
                                } break;
                                case TAG_GS_HINTS: {
                                    // get GS long listing
                                    gsbean.setEncodedHints(parser.nextText());
                                } break;
                                case TAG_GS_COUNTRY: {
                                    // get GS terrain
                                    gsbean.setCountry(parser.nextText());
                                } break;
                                case TAG_GS_DIFF: {
                                    // get GS difficulty
                                    gsbean.setDifficulty(parser.nextText());
                                } break;
                                case TAG_AU_SUMMARY: {
                                    // get AU summary used as GS long listing as
                                    gsbean.setShortListing(parser.nextText());
                                } break;
                                case TAG_GS_LONGL: {
                                    // get GS long listing
                                    gsbean.setLongListing(parser.nextText());
                                } break;
                                case TAG_AU_DESC: {
                                    // get AU description
                                    gsbean.setLongListing(parser.nextText());
                                } break;
                                case TAG_GS_TERRAIN: {
                                    // get GS terrain
                                    gsbean.setTerrain(parser.nextText());
                                } break;
                                case TAG_GS_CONTAINER: {
                                    // get GS container
                                    gsbean.setContainer(parser.nextText());
                                } break;
                                case TAG_GS_SHORTL: {
                                    // get GS short listing
                                    gsbean.setShortListing(parser.nextText());
                                } break;
                                default: {
                                    // skip
                                    parser.skipSubTree();
                                }
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
                            final int tag = parser.getHash();
                            switch (tag) {
                                case TAG_WPT:
                                case TAG_RTEPT:
                                case TAG_TRKPT: {
                                    // got anonymous wpt?
                                    if (name == null || name.length == 0) {
                                        sb.delete(0, sb.length());
                                        NavigationScreens.append(sb, v.size(), 1000);
                                        name = sb.toString().toCharArray();
                                    }

                                    // add to list
                                    final Waypoint wpt = new Waypoint(QualifiedCoordinates.newInstance(lat, lon, alt), name, cmt, sym);
                                    wpt.setLinks(links);
                                    wpt.setUserObject(gsbean);
                                    v.addElement(wpt);

                                    // reset depth
                                    depth = 0;

                                    // reset temps
                                    alt = Float.NaN;
                                    lat = lon = -1D;
                                    name = cmt = null;
                                    links = null;
                                } break;
                            }
                        } break;
                        case 2: {
                            final int tag = parser.getHash();
                            if (TAG_GS_CACHE == tag || TAG_AU_CACHE == tag) {
                                // back to <wpt> level
                                depth = 1;
                            } else if (TAG_EXTENSIONS == tag) {
                                // nothing to do
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

    private static void parseLoc(final HXmlParser parser, final Vector v)
            throws IOException, XmlPullParserException {

        int depth = 0;
        double lat, lon;
        char[] name, comment;

        name = comment = null;
        lat = lon = -1D;

        for (int eventType = parser.next(); eventType != XmlPullParser.END_DOCUMENT; eventType = parser.next()) {
            switch (eventType) {
                case XmlPullParser.START_TAG: {
                    switch (depth) {
                        case 0: {
                            final int tag = parser.getHash();
                            if (TAG_WAYPOINT == tag) {
                                // start level
                                depth = 1;
                            }
                        } break;
                        case 1: {
                            switch (parser.getHash()) {
                                case TAG_NAME: {
                                    // get name and comment
                                    name = parser.getAttributeValue(null, "id").toCharArray();
                                    comment = parser.nextChars();
                                } break;
                                case TAG_COORD: {
                                    // get lat and lon
                                    lat = Double.parseDouble(parser.getAttributeValue(null, ATTR_LAT));
                                    lon = Double.parseDouble(parser.getAttributeValue(null, ATTR_LON));
                                } break;
                                default: {
                                    // skip
                                    parser.skipSubTree();
                                }
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
                        final int tag = parser.getHash();
                        if (TAG_WAYPOINT == tag) {
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
