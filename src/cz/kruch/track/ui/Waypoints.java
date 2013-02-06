// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.Resources;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.event.Callback;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.location.GpxTracklog;
import cz.kruch.track.location.GroundspeakBean;
import cz.kruch.track.location.ExtWaypoint;
import cz.kruch.track.location.StampedWaypoint;
import cz.kruch.track.util.NakedVector;
import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.util.ImageUtils;
import cz.kruch.track.util.GpxVector;

import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Choice;
import javax.microedition.io.Connector;

import api.file.File;
import api.io.BufferedInputStream;
import api.io.BufferedOutputStream;
import api.location.QualifiedCoordinates;
import api.location.Location;
import api.util.Comparator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Vector;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone;

import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParser;
import org.kxml2.io.HXmlParser;

/**
 * Navigation manager.
 *
 * @author kruhc@seznam.cz
 */
public final class Waypoints implements CommandListener, Runnable, Callback,
                                        Comparator, YesNoDialog.AnswerListener {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Waypoints");
//#endif

    private static final int TYPE_GPX = 0;
    private static final int TYPE_LOC = 1;

    private static final int SORT_BYORDER   = 0;
    private static final int SORT_BYNAME    = 1;
    private static final int SORT_BYDIST    = 2;

    private static final int SUFFIX_LENGTH  = 4;

    private static final String STORE_USER      = "<user>";
    private static final String STORE_FRIENDS   = "<sms>";
    private /*static*/ final String NEW_FILE_STORE;

    private static final String SPECIAL_STORE_HEADING = "<";

    private /*static*/ final String PREFIX_USER;
    private static final String PREFIX_FRIENDS  = "sms-";

    private static final String SUFFIX_GPX      = ".gpx";
    private static final String SUFFIX_LOC      = ".loc";
//#ifdef __RIM__
    private static final String SUFFIX_GPX_REM  = ".gpx.rem";
//#endif

    private static final int TAG_TRK            = 0x0001c1ad; // trk
    private static final int TAG_TRKSEG         = 0xcc6aff88; // trkseg
    private static final int TAG_RTEPT          = 0x067cbba7; // "rtept"
    private static final int TAG_WPT            = 0x0001ccbb; // "wpt"
    private static final int TAG_TRKPT          = 0x06981871; // "trkpt"
    private static final int TAG_NAME           = 0x00337a8b; // "name"
    private static final int TAG_TIME           = 0x003652cd; // "time"
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
    private static final int TAG_GS_LOGS        = 0x0032c5af; // "logs"
    private static final int TAG_GS_LOG         = 0x0001a344; // "log"
    private static final int TAG_GS_DATE        = 0x002eefae; // "date"
    private static final int TAG_GS_FINDER      = 0xb4097826; // "finder"
    private static final int TAG_GS_TEXT        = 0x0036452d; // "text"

    private static final String ATTR_GS_ID      = "id";

    private static final int TAG_AU_CACHE       = 0x6d790ad1; // "geocache"
    private static final int TAG_AU_SUMMARY     = 0x9146a7a6; // "summary"
    private static final int TAG_AU_DESC        = 0x993583fc; // "description"
    private static final int TAG_AU_HINTS       = 0x05eaf2cc; // "hints"
    private static final int TAG_AU_FINDER      = 0x41a84fc1; // "geocacher"

    private static final int INITIAL_LIST_SIZE = 128;
    private static final int INCREMENT_LIST_SIZE = 32;

    private final /*Navigator*/ Desktop navigator;
    private final Hashtable stores, backends;

    private GpxVector currentWpts, inUseWpts;
    private String currentName, inUseName, sortedName;

    private volatile ExtList pane;
    private volatile List notes;
    private volatile UiList list;

    private volatile boolean tickerInUse; // FIXME
    private volatile boolean quickAction;

    private NakedVector sortedWpts, fieldNotes;
    private String folder, subfolder;
    private String notesFilename;
    private final Object[] idx;
    private int entry, depth, sort;

    private volatile String session;

    private String itemWptsStores, itemTracksStores,
                   itemRecordCurrent, itemEnterCustom, itemProjectNew,
                   itemFriendHere, itemFriendThere, itemStop, itemFieldNotes, itemEndSession;
    private String actionListWpts, actionListTracks, actionListTargets,
                   actionAddFieldNote;
//#ifdef __B2B__
    private String actionResourceNavi;
//#endif

    private Command cmdBack, cmdCancel, cmdClose, cmdOpen;
    private Command cmdActionNavigateTo, cmdActionNavigateAlong, cmdActionNavigateBack,
                    cmdActionSetAsCurrent, cmdActionGoTo, cmdActionAddFieldNote,
                    cmdActionShowAll, cmdActionHideAll, /*cmdActionOverlay,*/
                    cmdActionSortByOrder, cmdActionSortByName, cmdActionSortByDist;

    public javax.microedition.lcdui.Image
                   iconWptsStores, iconTracksStores,
                   iconRecordCurrent, iconEnterCustom, iconProjectNew,
                   iconFriendHere, iconFriendThere, iconStop, iconFieldNotes;

    private static boolean useNativeList;

    private static Waypoints instance;

    static void initialize(/*Navigator*/final Desktop navigator) {
//#ifdef __B2B__
        instance = null; // gc hint
//#endif
        instance = new Waypoints(navigator);
//#ifdef __B2B__
        if (Config.vendorNaviStore != null && Config.vendorNaviStore.length() > 0) {
            instance.folder = Config.FOLDER_WPTS;
            instance.onBackground(Config.vendorNaviStore, instance.actionResourceNavi);
        }
//#endif
        // prefer native lists on phones w/ touchscreen, except WM
        useNativeList = Desktop.screen.hasPointerEvents() && !cz.kruch.track.TrackingMIDlet.wm;
    }

//#ifdef __ANDROID__
    static void jvmReset() {
        instance = null;
    }
//#endif

    public static Waypoints getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Waypoints not initialized");
        }

        return instance;
    }

    private Waypoints(/*Navigator*/final Desktop navigator) {
        this.navigator = navigator;
        this.backends = new Hashtable(4);
        this.stores = new Hashtable(4);
        this.fieldNotes = new NakedVector(0, 4);
        this.idx = new Object[3];
//        this.sort = Config.sort;
        this.notesFilename = "fieldnotes-" + GpxTracklog.dateToFileDate(System.currentTimeMillis()) + ".txt";

        this.NEW_FILE_STORE = Resources.getString(Resources.NAV_MSG_NEW_FILE_ITEM);
        this.PREFIX_USER = Resources.getString(Resources.NAV_MSG_USER_FILE_PREFIX);

        this.itemWptsStores = Resources.getString(Resources.NAV_ITEM_WAYPOINTS);
        this.itemTracksStores = Resources.getString(Resources.NAV_ITEM_TRACKS);
        this.itemRecordCurrent = Resources.getString(Resources.NAV_ITEM_RECORD_CURRENT);
        this.itemEnterCustom = Resources.getString(Resources.NAV_ITEM_ENTER_CUSTOM);
        this.itemProjectNew = Resources.getString(Resources.NAV_ITEM_PROJECT_NEW);
        this.itemFriendHere = Resources.getString(Resources.NAV_ITEM_SMS_IAH);
        this.itemFriendThere = Resources.getString(Resources.NAV_ITEM_SMS_MYT);
        this.itemStop = Resources.getString(Resources.NAV_ITEM_STOP);
        this.itemFieldNotes = Resources.getString(Resources.NAV_ITEM_FIELD_NOTES);
        this.itemEndSession = "End Session";//Resources.getString(Resources.NAV_ITEM_FIELD_NOTES);

        this.actionListWpts = Resources.getString(Resources.NAV_ITEM_WAYPOINTS);
        this.actionListTracks = Resources.getString(Resources.NAV_ITEM_TRACKS);
        this.actionListTargets = Resources.getString(Resources.NAV_MSG_SELECT_STORE);
        this.actionAddFieldNote = Resources.getString(Resources.NAV_CMD_NEW_NOTE);
//#ifdef __B2B__
        this.actionResourceNavi = new String(Resources.getString(Resources.NAV_ITEM_WAYPOINTS));
//#endif

        this.cmdBack = new Command(Resources.getString(Resources.CMD_BACK), Desktop.BACK_CMD_TYPE, 1);
        this.cmdCancel = new Command(Resources.getString(Resources.CMD_CANCEL), Desktop.CANCEL_CMD_TYPE, 1);
        this.cmdClose = new Command(Resources.getString(Resources.CMD_CLOSE), Desktop.BACK_CMD_TYPE, 1);
        this.cmdOpen = new Command(Resources.getString(Resources.DESKTOP_CMD_OPEN), Desktop.SELECT_CMD_TYPE, 0);
        this.cmdActionNavigateTo = new ActionCommand(Resources.NAV_CMD_NAVIGATE_TO, Desktop.ITEM_CMD_TYPE, 2);
        this.cmdActionNavigateAlong = new ActionCommand(Resources.NAV_CMD_ROUTE_ALONG, Desktop.ITEM_CMD_TYPE, 3);
        this.cmdActionNavigateBack = new ActionCommand(Resources.NAV_CMD_ROUTE_BACK, Desktop.ITEM_CMD_TYPE, 4);
        this.cmdActionSetAsCurrent = new ActionCommand(Resources.NAV_CMD_SET_AS_ACTIVE, Desktop.ITEM_CMD_TYPE, 2);
        this.cmdActionGoTo = new ActionCommand(Resources.NAV_CMD_GO_TO, Desktop.ITEM_CMD_TYPE, 5);
        this.cmdActionAddFieldNote = new ActionCommand(Resources.NAV_CMD_NEW_NOTE, Desktop.ITEM_CMD_TYPE, 6);
        this.cmdActionShowAll = new ActionCommand(Resources.NAV_CMD_SHOW_ALL, Desktop.ITEM_CMD_TYPE, 7);
        this.cmdActionHideAll = new ActionCommand(Resources.NAV_CMD_HIDE_ALL, Desktop.ITEM_CMD_TYPE, 7);
//        this.cmdActionOverlay = new ActionCommand(Resources.NAV_CMD_OVERLAY, Desktop.ITEM_CMD_TYPE, 8);
        this.cmdActionSortByOrder = new ActionCommand(Resources.NAV_CMD_SORT_BYORDER, Desktop.ITEM_CMD_TYPE, 9);
        this.cmdActionSortByName = new ActionCommand(Resources.NAV_CMD_SORT_BYNAME, Desktop.ITEM_CMD_TYPE, 10);
        this.cmdActionSortByDist = new ActionCommand(Resources.NAV_CMD_SORT_BYDIST, Desktop.ITEM_CMD_TYPE, 11);

        try {
            this.iconWptsStores = ImageUtils.getGoodIcon("/resources/nav.wpts.png");
            this.iconTracksStores = ImageUtils.getGoodIcon("/resources/nav.tracks.png");
            this.iconRecordCurrent = ImageUtils.getGoodIcon("/resources/nav.wpt-gps.png");
            this.iconEnterCustom = ImageUtils.getGoodIcon("/resources/nav.wpt-cust.png");
            this.iconProjectNew = ImageUtils.getGoodIcon("/resources/nav.wpt-proj.png");
            this.iconStop = ImageUtils.getGoodIcon("/resources/nav.stop.png");
            if (cz.kruch.track.TrackingMIDlet.jsr120) {
                this.iconFriendHere = ImageUtils.getGoodIcon("/resources/nav.sms-him.png");
                this.iconFriendThere = ImageUtils.getGoodIcon("/resources/nav.sms-myt.png");
            }
        } catch (Throwable t) {
            // ignore
        }
    }

    public void show(final int hint) {

        // reset state
        entry = depth = 0;
        subfolder = null;
        quickAction = false;

        // start with default quick action on longpress when tracking and when no wpts are loaded
        switch (hint) {
            case 0: { // main menu command
                use(pane());
                menu(0);
            } break;
            case 1: { // long press
                if (navigator.isTracking() && STORE_USER.equals(session) && inUseWpts == null && currentWpts == null) {
                    quickAction = true;
                    mainMenuCommandAction(itemRecordCurrent);
                } else { // just go with main menu
                    use(pane());
                    menu(0);
                }
            } break;
            case 2: { // camera key
                if (navigator.isTracking()) {
                    quickAction = true;
                    mainMenuCommandAction(itemRecordCurrent);
                }
            } break;
        }
    }

    public void showCurrent() {
        // reset state
        quickAction = false;
        // show current store, if any...
        if (inUseWpts != null) {
            entry = depth = 2;
            use(listWaypoints(inUseName, inUseWpts, false, false));
        } else if (currentWpts != null) {
            entry = depth = 2;
            use(listWaypoints(currentName, currentWpts, false, false));
        } else {
            entry = depth = 0;
            subfolder = null;
            use(pane());
        }
        // update menu and show list
        menu(depth);
    }

    private ExtList pane() {
        pane = new ExtList(Resources.getString(Resources.DESKTOP_CMD_NAVIGATION), List.IMPLICIT);
        pane.setFitPolicy(Choice.TEXT_WRAP_OFF);
        pane.setCommandListener(this);
        pane.addCommand(cmdClose);
        return pane;
    }

    private void menu(final int depth) {
        // setup list
        switch (this.depth = depth) {
            case 0: {
                // clear list
                pane.deleteAll();
                // create menu
                pane.append(itemWptsStores, iconWptsStores);
                pane.append(itemTracksStores, iconTracksStores);
                if (fieldNotes.size() > 0) {
                    pane.append(itemFieldNotes, null);
                }
                pane.append(itemRecordCurrent, iconRecordCurrent);
                pane.append(itemEnterCustom, iconEnterCustom);
                pane.append(itemProjectNew, iconProjectNew);
                if (cz.kruch.track.TrackingMIDlet.jsr120) {
                    if (navigator.isTracking()) {
                        pane.append(itemFriendHere, iconFriendHere);
                    }
                    pane.append(itemFriendThere, iconFriendThere);
                }
                if (navigator.getNavigateTo() != null) {
                    pane.append(itemStop, iconStop);
                }
                if (session != null) {
                    pane.append(itemEndSession, iconStop);
                }
                pane.setSelectedIndex(0, true);
            } break;
            case 1: {
                // set last known choice
                if (idx[depth] != null) {
                    list.setSelectedItem(idx[depth], false);
                }
            } break;
            case 2: {
                // set last known choice
                if (inUseName == null || inUseName.equals(currentName)) {
                    if (idx[depth] != null) {
                        list.setSelectedItem(idx[depth], true);
                    }
                }
                // set marked, if any
                if (Desktop.wpts != null && Desktop.wpts == inUseWpts && Desktop.wptIdx > -1) {
                    list.setMarked(list.indexOf(inUseWpts.elementAt(Desktop.wptIdx)));
//                    list.setSelectedIndex(Desktop.wptIdx, true);
                }
            } break;
        }
        // show list
        Desktop.display.setCurrent(list.getUI());
    }

    public void commandAction(final Command command, final Displayable displayable) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("commandAction; command: " + command.getLabel() + "; displayable: " + displayable.getTitle());
//#endif
        // command source
        boolean fn = false;
        if (displayable instanceof List) {
            fn = itemFieldNotes.equals(displayable.getTitle());
//#ifndef __ANDROID__
        } else if (displayable instanceof javax.microedition.lcdui.Alert) {
            return; // S40 "ticker" activity indicator (dialog with dismiss button)
//#endif            
        }

        // field notes action?
        if (fn) {

            if (List.SELECT_COMMAND == command || cmdOpen == command) {
                // open field note
                (new FieldNoteForm((String[]) fieldNotes.elementAt(((List) displayable).getSelectedIndex()),
                                   displayable, this)).show();
            } else {
                // restore main menu
                notes = null; // gc hint
                Desktop.display.setCurrent(list.getUI());
            }

        } else { // menu or list action

            final int type = command.getCommandType();
            if (Desktop.BACK_CMD_TYPE == type || Desktop.CANCEL_CMD_TYPE == type) {
                if (depth == 0) {
                    close();
                } else {
                    if (depth == 1 && subfolder != null) { // same as SELECTing ".."
                        subfolder = null;
                        onBackground(null, null, Resources.NAV_MSG_TICKER_LISTING);
                    } else {
                        switch (--depth) {
                            case 0: {
                                if (quickAction) {
                                    close();
                                } else {
                                    use(pane);
                                    menu(0);
                                }
                            } break;
                            case 1: {
                                if (entry == 2) {
                                    depth = 0;
                                    close();
                                } else {
                                    onBackground(null, null, Resources.NAV_MSG_TICKER_LISTING);
                                }
                            } break;
                        }
                    }
                }
            } else {
                // depth-specific action
                switch (depth) {
                    case 0: { // main menu
//#ifdef __ANDROID__
                        try {
//#endif
                        // get command item
                        final String item = (String) list.getSelectedItem();
                        idx[depth] = item;

                        // exec
                        mainMenuCommandAction(item);
//#ifdef __ANDROID__
                        } catch (ArrayIndexOutOfBoundsException e) {
                            // no item selected - ignore
                        }
//#endif
                    } break;
                    case 1: { // store action
//#ifdef __ANDROID__
                        try {
//#endif

                        // get store name
                        final String item = (String) list.getSelectedItem();
                        idx[depth] = item;

                        // store action
                        if (List.SELECT_COMMAND == command || cmdOpen == command) {
                            if (item.endsWith(File.PATH_SEPARATOR)) {
                                subfolder = item;
                                onBackground(null, null, Resources.NAV_MSG_TICKER_LISTING);
                            } else if (item.equals(File.PARENT_DIR)) {
                                subfolder = null;
                                onBackground(null, null, Resources.NAV_MSG_TICKER_LISTING);
                            } else {
                                if (_listingTitle != actionListTargets) { // != is OK
                                    onBackground(item, null, Resources.NAV_MSG_TICKER_LOADING);
                                } else { // "Operation in progress" or "Enter landmarks filename:" will be shown
                                    onBackground(item, null);
                                }
                            }
                        }
//#ifdef __ANDROID__
                        } catch (ArrayIndexOutOfBoundsException e) {
                            // no item selected - ignore
                        }
//#endif
                    } break;
                    case 2: { // wpt action
                        // selected item
                        final Waypoint item = (Waypoint) list.getSelectedItem();
                        // exec command
                        if (List.SELECT_COMMAND == command || cmdOpen == command) {
                            // save current depth list position
                            if (inUseName == null || inUseName.equals(currentName)) {
                                idx[depth] = item;
                            }
                            // calculate distance
//#ifdef __ANDROID__
                            try {
//#endif
                                _distance = item.getQualifiedCoordinates().distance(navigator.getRelQc());
//#ifdef __ANDROID__
                            } catch (NullPointerException e) { // relQC - how come?!?
                                _distance = -1F;
                            }
//#endif
                            // check parsing status
                            if (item.getUserObject() instanceof GroundspeakBean && !((GroundspeakBean) item.getUserObject()).isParsed()) {
                                // fully parse bean and show wpt details
                                _parseWpt = item;
                                exec();
                            } else {
                                // open waypoint form
                                (new WaypointForm(this, item, _distance,
                                                  isModifiable(), isCache())).show();
                            }
                        } else { // execute action
                            // act accordingly
                            final int action = ((ActionCommand) command).getAction();
                            switch (action) {
                                case Resources.NAV_CMD_ROUTE_ALONG: {
                                    // same as action in waypoint form
                                    invoke(new Object[]{new Integer(Resources.NAV_CMD_ROUTE_ALONG), null}, null, this);
                                } break;
                                case Resources.NAV_CMD_ROUTE_BACK: {
                                    // same as action in waypoint form
                                    invoke(new Object[]{new Integer(Resources.NAV_CMD_ROUTE_BACK), null}, null, this);
                                } break;
                                case Resources.NAV_CMD_NAVIGATE_TO: {
                                    // same as action in waypoint form
                                    invoke(new Object[]{new Integer(Resources.NAV_CMD_NAVIGATE_TO), null}, null, this);
                                } break;
                                case Resources.NAV_CMD_SET_AS_ACTIVE: {
                                    // same as action in waypoint form
                                    invoke(new Object[]{new Integer(Resources.NAV_CMD_SET_AS_ACTIVE), null}, null, this);
                                } break;
                                case Resources.NAV_CMD_GO_TO: {
                                    // same as action in waypoint form
                                    invoke(new Object[]{new Integer(Resources.NAV_CMD_GO_TO), null}, null, this);
                                } break;
                                case Resources.NAV_CMD_SHOW_ALL: {
                                    // close nav UI
                                    close();
                                    // call navigator
                                    navigator.showWaypoints(currentWpts, currentName, 0);
                                } break;
                                case Resources.NAV_CMD_HIDE_ALL: {
                                    // close nav UI
                                    close();
                                    // call navigator
                                    navigator.showWaypoints(null, null, -1);
                                } break;
                                case Resources.NAV_CMD_SORT_BYORDER: {
                                    // sort
                                    sortWaypoints(list, SORT_BYORDER, false, currentWpts, false);
                                } break;
                                case Resources.NAV_CMD_SORT_BYNAME: {
                                    // sort
                                    sortWaypoints(list, SORT_BYNAME, false, currentWpts, false);
                                } break;
                                case Resources.NAV_CMD_SORT_BYDIST: {
                                    // sort
                                    sortWaypoints(list, SORT_BYDIST, false, currentWpts, false);
                                } break;
                                case Resources.NAV_CMD_NEW_NOTE: {
                                    // open New Note form
                                    final Waypoint wpt = (Waypoint) inUseWpts.elementAt(Desktop.wptIdx);
                                    (new FieldNoteForm(wpt, displayable, this)).show();
                                } break;
                            }
                        }
                    } break;
                }
            }
        }
//#ifdef __LOG__
    if (log.isEnabled()) log.debug("~commandAction");
//#endif
    }

    private void mainMenuCommandAction(final String item) {
        // menu action
        if (itemWptsStores.equals(item)) {
            // use "wpts/" folder
            folder = Config.FOLDER_WPTS;
            // list in thread
            onBackground(null, actionListWpts, Resources.NAV_MSG_TICKER_LISTING);
        } else if (itemTracksStores.equals(item)) {
            // use "tracks-gpx/" folder
            folder = Config.FOLDER_TRACKS;
            // list in thread
            onBackground(null, actionListTracks, Resources.NAV_MSG_TICKER_LISTING);
        } else if (itemFieldNotes.equals(item)) {
            // show notes
            if (fieldNotes.size() > 0) {
                showFieldNotes();
            } else {
                Desktop.showInfo(Resources.getString(Resources.NAV_MSG_NO_NOTES_YET), null);
            }
        } else if (itemRecordCurrent.equals(item)) {
            // only when tracking
            if (navigator.isTracking()) {
                // got location?
                final Location location = navigator.getLocation();
                if (location != null) {
                    // force location to be gpx-logged
                    navigator.saveLocation(location);
                    // open form with current location
                    (new WaypointForm(this, location, session)).show().setTracklogTime(navigator.getTracklogTime());
                } else {
                    Desktop.showInfo(Resources.getString(Resources.NAV_MSG_NO_POS_YET), pane.getUI());
                }
            } else {
                Desktop.showInfo(Resources.getString(Resources.NAV_MSG_NOT_TRACKING), pane.getUI());
            }
        } else if (itemEnterCustom.equals(item)) {
            // got position?
            final QualifiedCoordinates pointer = navigator.getPointer();
            if (pointer == null) {
                Desktop.showInfo(Resources.getString(Resources.NAV_MSG_NO_POS_YET), pane.getUI());
            } else {
                (new WaypointForm(this, pointer, Resources.NAV_ITEM_ENTER_CUSTOM)).show();
            }
        } else if (itemProjectNew.equals(item)) {
            // got position?
            final QualifiedCoordinates qc = navigator.getRelQc();
            if (qc == null) {
                Desktop.showInfo(Resources.getString(Resources.NAV_MSG_NO_POS_YET), pane.getUI());
            } else {
                (new WaypointForm(this, qc, Resources.NAV_ITEM_PROJECT_NEW)).show();
            }
//#ifndef __ANDROID__
        } else if (itemFriendHere.equals(item)) {
            // do we have position?
            final Location location = navigator.getLocation();
            if (location == null) {
                Desktop.showInfo(Resources.getString(Resources.NAV_MSG_NO_POS_YET), pane.getUI());
            } else {
                (new WaypointForm(this, cz.kruch.track.fun.Friends.TYPE_IAH,
                        location.getQualifiedCoordinates()._clone())).show();
            }
        } else if (itemFriendThere.equals(item)) {
            // got position?
            final QualifiedCoordinates pointer = navigator.getPointer();
            if (pointer == null) {
                Desktop.showInfo(Resources.getString(Resources.NAV_MSG_NO_POS_YET), pane.getUI());
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
            // remove in-use store from cache IF IT IS NOT current
            if (inUseName != null && !inUseName.equals(currentName)) {
                stores.remove(inUseName);
            }
            // no in-use store
            inUseName = null;
            inUseWpts = null;
        } else if (itemEndSession.equals(item)) {
            // close nav UI
            close();
            // clear session flag
            session = null;
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
        if (source instanceof FieldNoteForm) { // new or updated field note
            
            // update or append to notes
            if (fieldNotes.contains(result)) {
                notes.set(fieldNotes.indexOf(result),
                          FieldNoteForm.format((String[]) result, new StringBuffer(128)), 
                          null);
            } else { // add new note
                fieldNotes.addElement(result);
            }

            // persist changes
            _storeName = notesFilename;
            _listingTitle = actionAddFieldNote;
            exec();

//#ifndef __ANDROID__
        } else if (source instanceof cz.kruch.track.fun.Friends) { // SMS received

            // get result
            final Waypoint wpt = (Waypoint) result;

            // notify user
            Desktop.showAlarm(Resources.getString(Resources.DESKTOP_MSG_SMS_RECEIVED) + wpt.getName(),
                              /*list.getUI()*/null , !Config.autohideNotification);

            /*
             * this is safe, no need to use worker, it is called from a thread, see Friends.execPop()
             */

            // add waypoint to store
            final GpxTracklog backend = getBackend(STORE_FRIENDS, null);
            if (backend.getFileName() == null) {
                backend.setFileName(backend.getDefaultFileName());
            }
            addToStore(STORE_FRIENDS, backend.getFileName(), wpt);
//#endif
        } else if (result instanceof Object[]) { // list or waypoint form origin

            // action type
            final Object[] ret = (Object[]) result;
            final Object action = ret[0];

            // execute action
            if (null == action) {

                // custom list mode
                if (Config.extListMode == Config.LISTMODE_CUSTOM) {
                    if (source instanceof WaypointForm && list instanceof ExtList) {
                        list.setSelectedItem(list.getSelectedItem(), true);
                    }
                }
                
                // restore list (if any)
                Desktop.display.setCurrent(quickAction ? Desktop.screen : list.getUI());

            } else if (action instanceof Integer) { // wpt action

                // action level
                if (pane == list) { // top level command

                    // action
                    switch (((Integer) action).intValue()) {
//#ifndef __ANDROID__
                        case Resources.NAV_CMD_SEND: {
                            // close nav UI
                            close();

                            // vars
                            final String type = (String) ret[3];
                            final QualifiedCoordinates qc;
                            final long time;

                            // get message type and location
                            if (cz.kruch.track.fun.Friends.TYPE_IAH == type) { // == is OK
                                time = navigator.getLocation().getTimestamp();
                                qc = navigator.getLocation().getQualifiedCoordinates()._clone();
                            } else { // Friends.TYPE_MYT
                                time = System.currentTimeMillis();
                                qc = navigator.getPointer();
                            }

                            // send the message
                            navigator.getFriends().send((String) ret[1], type, (String) ret[2], qc, time);
                        } break;
//#endif
                        case Resources.NAV_ITEM_ENTER_CUSTOM:
                        case Resources.NAV_ITEM_RECORD_CURRENT:
                        case Resources.NAV_ITEM_PROJECT_NEW: {
                            // add waypoint to memory store
                            addToPrefferedStore(STORE_USER, (Waypoint) ret[1], (String) ret[2]);
                        } break;

                        default:
                            throw new IllegalArgumentException("Unknown wpt action: " + action);
                    }

                } else { // selected wpt action

                    // get selected wpt
                    final Waypoint wpt = (Waypoint) list.getSelectedItem();
                    if (wpt == null) {

                        // warn user
                        Desktop.showWarning(Resources.getString(Resources.NAV_MSG_NO_WPT_SELECTED), null, null);
                    
                    } else {

                        // get selected wpt index
                        final int idxSelected = wptIdx(currentWpts, wpt);

                        // action
                        switch (((Integer) action).intValue()) {

                            case Resources.NAV_CMD_ROUTE_ALONG: {
                                // remember idx
                                idx[depth] = wpt;

                                // close nav UI
                                close();

                                // use current store
                                useCurrent();

                                // call navigator
                                navigator.setNavigateTo(currentWpts, currentName, idxSelected, -1);
                            } break;

                            case Resources.NAV_CMD_ROUTE_BACK: {
                                // remember idx
                                idx[depth] = wpt;

                                // close nav UI
                                close();

                                // use current store
                                useCurrent();

                                // call navigator
                                navigator.setNavigateTo(currentWpts, currentName, -1, idxSelected);
                            } break;

                            case Resources.NAV_CMD_NAVIGATE_TO: {
                                // remember idx
                                idx[depth] = wpt;

                                // close nav UI
                                close();

                                // use current store
                                useCurrent();

                                // call navigator
                                navigator.setNavigateTo(currentWpts, currentName, idxSelected, idxSelected);
                            } break;

                            case Resources.NAV_CMD_SET_AS_ACTIVE: {
                                // remember idx
                                idx[depth] = wpt;

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
                                // remember idx
                                idx[depth] = wpt;

                                // close nav UI
                                close();

                                // call navigator
                                navigator.goTo((Waypoint) currentWpts.elementAt(idxSelected));
                            } break;

                            case Resources.NAV_CMD_UPDATE: {
                                // update current store
                                updateStore(currentName, currentWpts);
                            } break;

                            case Resources.NAV_CMD_DELETE: {
                                // remove selected wpt
                                currentWpts.removeElementAt(idxSelected);
                                int nextSelected = list.getSelectedIndex();
                                sortedWpts.removeElementAt(nextSelected);
                                list.delete(nextSelected);
                                if (idxSelected >= sortedWpts.size()) {
                                    nextSelected--;
                                }
                                list.setSelectedIndex(nextSelected, true);

                                // update current store
                                updateStore(currentName, currentWpts);
                            } break;
                            default:
                                throw new IllegalArgumentException("Unknown wpt action: " + action);
                        }
                    }
                }
            } else {
                throw new IllegalArgumentException("Unknown waypoint form invocation; result = " + result + "; throwable = " + throwable);
            }
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
//                final QualifiedCoordinates qc = _pointer;
//                if (qc != null) {
//                    final float d1 = qc.distance(wpt1.getQualifiedCoordinates());
//                    final float d2 = qc.distance(wpt2.getQualifiedCoordinates());
                if (_pointer != null) {
                    final float d1 = getDistanceHack(wpt1);
                    final float d2 = getDistanceHack(wpt2);
                    if (d1 < d2) {
                        cmp = -1;
                    } else if (d1 > d2) {
                        cmp = 1;
                    } else {
                        cmp = 0;
                    }
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
                if (_parseWpt == null) {
                    actionListStores(_listingTitle);
                } else {
                    actionParseWpt(_parseWpt);
                    _parseWpt = null;
                }
            } else {
                if (_listingTitle == actionListWpts || _listingTitle == actionListTracks) { // == is OK
                    actionListStore(_storeName, 2, Config.lazyGpxParsing);
//#ifdef __B2B__
                } else if (_listingTitle == actionResourceNavi) { // == is OK
                    actionListStore(_storeName, 1, Config.lazyGpxParsing);
                    b2b_Action();
//#endif
                } else if (_listingTitle == actionListTargets) { // == is OK
                    actionUpdateTarget(_storeName, _addWptStoreKey, _addWptSelf);
                } else if (_listingTitle == actionAddFieldNote) {
                    _listingTitle = actionListWpts; // hack
                    actionUpdateNotes();
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
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("~run");
//#endif
    }

    private volatile Waypoint _addWptSelf;
    private volatile Object _addWptStoreKey;
    private volatile QualifiedCoordinates _pointer;
    private volatile Hashtable _distances;
    private volatile String _storeName, _updateName, _listingTitle;
    private volatile GpxTracklog _storeUpdate;
    private volatile Waypoint _parseWpt;
    private volatile Vector _updateWpts;
    private volatile boolean _updateRevision;
    private volatile float _distance;

    /*
     * Background task launcher.
     */
    private void onBackground(final String storeName, final String listingTitle) {
        onBackground(storeName, listingTitle, -1);
    }

    /*
     * Background task launcher.
     */
    private void onBackground(final String storeName, final String listingTitle, final int resText) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("onBackground; " + storeName + "," + listingTitle);
//#endif

        this._storeName = storeName;
        if (listingTitle != null) {
            this._listingTitle = listingTitle;
        }
//-#ifndef __ANDROID__
        if (resText > -1 && list != null) {
            final String busyText = Resources.getString((short) resText);
            final String busyTitle = listingTitle == null ? storeName : listingTitle;
//#ifndef __ANDROID__
            if (Config.s40ticker) {
                final javax.microedition.lcdui.Alert wait = new javax.microedition.lcdui.Alert(busyTitle, busyText,
                                                                                               null, null);
                wait.setTimeout(javax.microedition.lcdui.Alert.FOREVER);
                wait.setIndicator(new javax.microedition.lcdui.Gauge(null, false,
                                                                     javax.microedition.lcdui.Gauge.INDEFINITE,
                                                                     javax.microedition.lcdui.Gauge.CONTINUOUS_RUNNING));
                wait.setCommandListener(this);
                Desktop.display.setCurrent(wait);
            } else {
//#endif
                cz.kruch.track.ui.nokia.DeviceControl.setTicker(list.getUI(), busyText);
                tickerInUse = true;
//#ifndef __ANDROID__
            }
//#endif
        }
//-#endif
        exec();
    }

    /*
     * Executes in background.
     */
    private void exec() {
        Desktop.getDiskWorker().enqueue(this);
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

        final String uiTitle = subfolder == null ? title :
                (new StringBuffer(32)).append(title).append(" [").append(subfolder).append("]").toString();
        final NakedVector v = new NakedVector(INITIAL_LIST_SIZE, INCREMENT_LIST_SIZE);

        // offer new file when selecting target
        if (title == actionListTargets) { // == is OK

            // offer new file
            v.addElement(NEW_FILE_STORE);

            // "wpts/" folder is only supported
            folder = Config.FOLDER_WPTS;

            // re-read folder content
            /*cachedDisk = null;*/ // see note N20100318
        }

        // list special stores first only when listing "wpts/" folder
        if (folder == Config.FOLDER_WPTS && subfolder == null) { // == is OK

            // add prefered stores
            listKnown(v, title == actionListTargets);

//        } else if (subfolder != null) {
//
//            // back
//            v.addElement(File.PARENT_DIR);

        }

        final int left = v.size();
        final String storesFolder = getStoresFolder();
        NakedVector cachedDisk = null;
//#ifdef __ANDROID__
//        boolean tickerInUse = false;
//#endif        

            // list persistent stores
            if (Config.dataDirExists) {

                try {

                    // may take some time - start ticker
//#ifdef __ANDROID__
//                    cz.kruch.track.ui.nokia.DeviceControl.setTicker(list.getUI(), Resources.getString(Resources.NAV_MSG_TICKER_LISTING) + storesFolder);
//                    tickerInUse = true;
//#endif

                    // list file stores
                    listWptFiles(cachedDisk = new NakedVector(INITIAL_LIST_SIZE, INCREMENT_LIST_SIZE));

                } catch (Throwable t) {

                    // show error
                    Desktop.showError(Resources.getString(Resources.NAV_MSG_LIST_STORES_FAILED), t, null);

                }
            }

        // now append (cached) folder content
        /**
         * Note N20100318: the condition bellow will allow the user to
         * select only new or parsed file. For some reason I thought
         * it would be dangerous, but it seems ok (2010-03-30).
         */
//        if (title != actionListTargets) { // '==' is OK
            appendCached(v, cachedDisk, title == actionListTargets);
//        }

        // got anything?
        if (v.size() == 0) {

            // stay at root
            subfolder = null;

            // remove ticker
//-#ifdef __ANDROID__
//            if (tickerInUse) {
//                cz.kruch.track.ui.nokia.DeviceControl.setTicker(list.getUI(), null);
//            }
//-#else
            clearTicker();
//-#endif

            // notify user
            Desktop.showInfo(Resources.getString(Resources.NAV_MSG_NO_STORES), null);

        } else {

            try {

                // sort file stores (leaves memory stores at the beginning)
                FileBrowser.sort(v.getData(), left, v.size() - 1);

                // create UI list
                use(listStores(v, uiTitle), tickerInUse);

                // list stores
                menu(1);

            } catch (Throwable t) {
//#ifdef __ANDROID__
                android.util.Log.e(cz.kruch.track.TrackingMIDlet.APP_TITLE, "Failed to list " + storesFolder, t);
//#endif
//#ifdef __LOG__
                t.printStackTrace();
//#endif
                Desktop.showError("Failed to list landmark files", t, list.getUI());
            }
        }
    }

    /**
     * Loads waypoints from file landmark store.
     */
    private Vector actionListStore(final String fileName, final int uiAction,
                                   final boolean lazyGs) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("list store: " + fileName);
//#endif

        // store name
        final String storeName = subfolder == null ? fileName : subfolder + fileName;

        // local
        GpxVector wpts = null;
        Throwable parseException = null;
//#ifdef __ANDROID__
//        boolean tickerInUse = false;
//#endif        

        // got store in cache?
        final GpxVector wptsCached = (GpxVector) stores.get(storeName);
        if (wptsCached == null) { // no, load from file

            // only foreground activity
            if (uiAction == 2) {

                // may take some time - start ticker
//#ifdef __ANDROID__
//                cz.kruch.track.ui.nokia.DeviceControl.setTicker(list.getUI(), Resources.getString(Resources.NAV_MSG_TICKER_LOADING));
//                tickerInUse = true;
//#endif

                // remove current store from cache IF IT IS NOT in use
                if (currentName != null && !currentName.equals(inUseName)) {
                    stores.remove(currentName);
                }

                // no current store
                currentWpts = null; // gc hint
                currentName = null; // gc hint
            }

            // parse XML-based store
            File file = null;

            try {

                // open file
                file = File.open(Config.getFileURL(folder, storeName));
                if (file.exists()) {

                    // parse new waypoints
                    final int i = storeName.lastIndexOf('.');
                    if (i > -1) {
                        final String lcname = storeName.toLowerCase();
                        if (lcname.endsWith(SUFFIX_GPX)) {
                            wpts = parseWaypoints(file, TYPE_GPX, lazyGs);
                        } else if (lcname.endsWith(SUFFIX_LOC)) {
                            wpts = parseWaypoints(file, TYPE_LOC, lazyGs);
                        }
                    }

                }

            } catch (Throwable t) {
//#ifdef __ANDROID__
                android.util.Log.e(cz.kruch.track.TrackingMIDlet.APP_TITLE, "Failed to parse " + storeName, t);
//#endif
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
            }

        } else {

            // used cached
            wpts = wptsCached;

/* without parsing everything should be fast
            // only foreground activity
            if (uiAction == 2) {

                // many wpts may take some time - start ticker
                if (wpts.size() >= 64) {
                    cz.kruch.track.ui.nokia.DeviceControl.setTicker(list.getUI(), Resources.getString(Resources.NAV_MSG_TICKER_LISTING));
                    tickerInUse = true;
                }
            }
*/

        }

        // return intermediate
        if (uiAction == 0) {
            return wpts;
        }

        // process result
        if (wpts == null || wpts.size() == 0) {

            // notify
            if (uiAction == 2) {

                // kill ticker
//-#ifdef __ANDROID__
//                if (tickerInUse) {
//                    cz.kruch.track.ui.nokia.DeviceControl.setTicker(list.getUI(), null);
//                }
//-#else
                clearTicker();
//-#endif

                // notify on errors
                if (parseException == null) {
                    Desktop.showWarning(Resources.getString(Resources.NAV_MSG_NO_WPTS_FOUND_IN) + " " + storeName, null, list.getUI());
                } else {
                    Desktop.showError("Failed to parse landmarks", parseException, list.getUI());
                }
            }

        } else {

            try {
                // make sure we have backend ready // TODO why???
                if (folder == Config.FOLDER_WPTS) {
                    getBackend(storeName, storeName);
                }
                
                // remember current store
                currentWpts = wpts;
                currentName = storeName;

                // cache current store IF IT IS NOT in-use (it is already cached then)
                if (currentName != null && !currentName.equals(inUseName)) {
                    stores.put(currentName, currentWpts);
                }

                // UI interaction allowed?
                if (uiAction == 2) {

                    // create list
                    use(listWaypoints(storeName, wpts, true, tickerInUse), tickerInUse);

                    // update menu and show list
                    menu(2);

                    // notify user (only if freshly loaded)
                    if (wptsCached == null) {
                        Desktop.showInfo(wpts.size() + " " + Resources.getString(Resources.NAV_MSG_WPTS_LOADED), list.getUI());
                    }
                }

            } catch (Throwable t) {
//#ifdef __ANDROID__
                android.util.Log.e(cz.kruch.track.TrackingMIDlet.APP_TITLE, "Failed to list " + storeName, t);
//#endif
//#ifdef __LOG__
                t.printStackTrace();
//#endif
                if (uiAction == 2) { // UI interaction allowed?
                    Desktop.showError("Failed to list landmarks", t, list.getUI());
                }
            }
        }

        return null;
    }

    private void actionUpdateStore(final String name, final Vector wpts) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("action update store '" + name + "'");
//#endif
/*
        // interactive?
        final boolean onBackround = STORE_FRIENDS.equals(_addWptStoreKey);
*/

        // execution status
        Throwable status = null;
        String url = null;

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
                    f = File.open(Config.getFileURL(Config.FOLDER_WPTS, name), Connector.READ_WRITE);
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
/*
                    } catch (Exception e) {
                        status = e;
//#ifdef __LOG__
                        if (log.isEnabled()) log.error("error writting wpt", e);
//#endif
*/
                    } finally {
                        _storeUpdate.close(); // safe operation
                    }
                } else {
                    url = _storeUpdate.getURL();
                }
            }
        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
//#ifdef __ANDROID__
            android.util.Log.e(cz.kruch.track.TrackingMIDlet.APP_TITLE, "Failed to update " + name, t);
//#endif

            // report
            status = t;

        } finally {

            // cleanup
            _storeUpdate = null; // gc hint

            // what next
            final boolean onBackround = STORE_FRIENDS.equals(_addWptStoreKey);
            if (!onBackround) {
                final Displayable next;
                if (depth > 1) {
                    use(list);
                    next = list.getUI();
                    depth = 2;
                } else {
                    use(null);
                    next = Desktop.screen;
                    pane = null;
                    depth = 0;
                }

                // notify about result
                if (status == null) {
                    Desktop.showConfirmation(Resources.getString(Resources.NAV_MSG_STORE_UPDATED),
                                             next);
                } else {
                    String message = Resources.getString(Resources.NAV_MSG_STORE_UPDATE_FAILED);
                    if (url != null) {
                        message += " [" + url + "]";
                    }
                    Desktop.showError(message, status, next);
                }
            }
        }
    }

    private void actionUpdateTarget(final String name, final Object storeKey,
                                    final Waypoint wpt) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("action update target '" + name + "'");
//#endif

        Object useKey = storeKey;

        if (name.equals(NEW_FILE_STORE)) { // new store
            stores.remove(storeKey);
            backends.remove(storeKey);
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
            (new YesNoDialog(this, new Object[]{useKey, sb, wpt},
                             Resources.getString(Resources.NAV_MSG_ENTER_STORE_FILENAME),
                             sb)).show();
        } else {
            if ("".equals(session)) {
                session = (String)useKey;
            }
            addToStore(useKey, name, wpt);
        }
    }

    private void actionParseWpt(final Waypoint wpt) {
        // parse XML-based store
        File file = null;

        try {

            // may take some time - start ticker
            cz.kruch.track.ui.nokia.DeviceControl.setTicker(list.getUI(), Resources.getString(Resources.NAV_MSG_TICKER_LOADING));

            // open file
            file = File.open(Config.getFileURL(folder, currentName));

            // input stream and parser
            InputStream in = null;
            HXmlParser parser = null;

            try {
                // open input
                in = new BufferedInputStream(file.openInputStream(), 8192);

                // create parser
                parser = new HXmlParser();
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);

                // get bean
                final GroundspeakBean bean = (GroundspeakBean) wpt.getUserObject();

                // init parser
                parser.setInput(in, null); // null is for encoding autodetection
                parser.skip(bean.getFileOffset());

                // parse
                parseBean(parser, bean, 1, false);

                // open waypoint form
                (new WaypointForm(this, wpt, _distance, isModifiable(), isCache())).show();

            } finally {
                try {
                    parser.close();
                } catch (Exception e) { // NPE or IOE
                    // ignore
                }
                try {
                    in.close();
                } catch (Exception e) { // NPE or IOE
                    // ignore
                }
            }

        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
            Desktop.showError("Parse error", t, null);

        } finally {

            // close file
            try {
                file.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }

            // remove ticker
            cz.kruch.track.ui.nokia.DeviceControl.setTicker(list.getUI(), null);
        }
    }

    private void actionUpdateNotes() {
        File file = null;

        try {

            // create file
            file = File.open(Config.getFileURL(Config.FOLDER_GC, notesFilename), Connector.READ_WRITE);
            if (file.exists()) {
                file.delete();
            }
            file.create();

            // output
            OutputStream out = null;

            try {
                // open output
                out = new BufferedOutputStream(file.openOutputStream(), 512, false);

                // write BOM
                out.write((byte) 0xEF);
                out.write((byte) 0xBB);
                out.write((byte) 0xBF);

                // write notes
                final Vector fieldNotes = this.fieldNotes;
                final StringBuffer sb = new StringBuffer(128);
                for (int N = fieldNotes.size(), i = 0; i < N; i++) {
                    sb.delete(0, sb.length());
                    FieldNoteForm.format((String[]) fieldNotes.elementAt(i), sb);
                    sb.append("\r\n");
                    out.write(sb.toString().getBytes("UTF-8"));
                }

                // notify user
                Desktop.showConfirmation(Resources.getString(Resources.NAV_MSG_NOTE_ADDED), null);

            } finally {
                try {
                    out.close();
                } catch (Exception e) { // NPE or IOE
                    // ignore
                }
            }

        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
            Desktop.showError("Failed to save field notes", t, null);

        } finally {

            // close file
            try {
                file.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
        }
    }

    private void listKnown(final Vector v, final boolean omitUnparsed) {

        // enumerate all known backends
        for (final Enumeration e = backends.keys(); e.hasMoreElements();) {

            // get backend name (ie. filename)
            final String storeKey = (String) e.nextElement();
            final String backendName = ((GpxTracklog) backends.get(storeKey)).getFileName();

            // avoid duplicity
            if (backendName != null && !v.contains(backendName)) {

                // check for lazy-parsed waypoints
                boolean rw = true;
                if (omitUnparsed) {
                    rw = isWriteable(storeKey);
                }

                // list known store only if is "read-write"
                if (rw) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("list known backend file " + backendName);
//#endif
                    v.addElement(backendName);
                }
            }
        }
    }

    private void showFieldNotes() {
        final StringBuffer sb = new StringBuffer(128);
        final Vector data = fieldNotes;
        final List l = notes = new List(itemFieldNotes, List.IMPLICIT);
        l.setFitPolicy(Choice.TEXT_WRAP_OFF);
        for (int N = data.size(), i = 0; i < N; i++) {
            sb.delete(0, sb.length());
            l.append(FieldNoteForm.format((String[]) data.elementAt(i), sb), null);
        }
        for (int N = l.size(), i = 0; i < N; i++) {
            l.setFont(i, Desktop.fontStringItems);
        }
        l.setSelectCommand(cmdOpen);
        l.addCommand(cmdClose);
        l.setCommandListener(this);
        Desktop.display.setCurrent(l);
    }

    private boolean isWriteable(final String storeKey) {
        final NakedVector wpts = (NakedVector) stores.get(storeKey);
        if (wpts != null) {
            final Object[] raw = wpts.getData();
            for (int i = wpts.size(); --i >= 0; ) {
                final Waypoint wpt = (Waypoint) raw[i];
                if (wpt.getUserObject() instanceof GroundspeakBean && !((GroundspeakBean) wpt.getUserObject()).isParsed()) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isModifiable() { // '==' is OK
        return (folder == Config.FOLDER_WPTS && currentWpts != Desktop.wpts) && isWriteable(currentName);
    }

    private boolean isCache() {
        return (folder == Config.FOLDER_WPTS); // '==' is OK
    }

    private void addToPrefferedStore(final Object storeKey, final Waypoint wpt,
                                     final String sessionKey) {
        if (sessionKey == null || sessionKey.length() == 0) {

            // update session
            session = sessionKey;

            // set temps
            _addWptStoreKey = storeKey;
            _addWptSelf = wpt;

            // use "wpts/" folder
            folder = Config.FOLDER_WPTS;

            // list targets in thread
            onBackground(null, actionListTargets);

        } else { // session mode

            // add to current user store
            addToStore(sessionKey, null, wpt);

        }
    }

    private boolean hideStore(final String storeName) {
        for (final Enumeration e = backends.elements(); e.hasMoreElements();) {
            final GpxTracklog backendName = ((GpxTracklog) e.nextElement());
            if (storeName.equals((backendName.getFileName()))) {
//#ifdef __LOG__
                if (log.isEnabled())
                    log.debug("hide file " + storeName + " as backend file " + backendName.getFileName());
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
            } else if (STORE_FRIENDS.equals(storeKey)) {
                gpx.setFilePrefix(PREFIX_FRIENDS);
            } else {
                // assertion
                if (storeName == null) {
                    throw new NullPointerException("storeName");
                }
                // remove previous file backend
                for (final Enumeration e = backends.keys(); e.hasMoreElements();) {
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

            // add to backends
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
            if (storeName != null) { // store not loaded yet
                wpts = actionListStore(storeName, 0, false);
            }
        }
        if (wpts == null) {
            stores.put(storeKey, wpts = new NakedVector(16, 16));
        }
        wpts.addElement(wpt);

        // update navigator if we update store being used
        if (Desktop.wpts != null) {
            if (Desktop.wptsName.equals(storeName)) {
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

        // updated store key
        _addWptStoreKey = name;

        // flag update revision
        _updateRevision = name.startsWith(SPECIAL_STORE_HEADING) ? false : Config.makeRevisions;

        // fallback action
        _listingTitle = actionListWpts;

        // wait screen // TODO move to addToStore but need to handle updateStore invocation from waypoint form
        final boolean onBackround = STORE_FRIENDS.equals(_addWptStoreKey);
        if (!onBackround) {
//#ifndef __ANDROID__
            Desktop.showWaitScreen(Resources.getString(Resources.DESKTOP_CMD_NAVIGATION),
                                   Resources.getString(Resources.DESKTOP_MSG_IN_PROGRESS));
//#else
            if (list != null) { // this is quickAction // TODO would be nice to have progess dialog too
                cz.kruch.track.ui.nokia.DeviceControl.setTicker(list.getUI(), Resources.getString(Resources.DESKTOP_MSG_IN_PROGRESS));
                tickerInUse = true; // so that use() will hide the progress dialog (Android)
            }
//#endif
        }

        // do the rest on background
        exec();
    }

    private void listWptFiles(final Vector v) throws IOException {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("list " + getStoresFolder());
//#endif

        File dir = null;
        try {
            // open directory
            dir = File.open(Config.getFolderURL(getStoresFolder()));

            // list file stores in the directory
            if (dir.exists()) {

                // iterate over directory
                for (final Enumeration e = dir.list(); e.hasMoreElements();) {

                    // has suffix?
                    final String filename = (String) e.nextElement();
                    final int i = filename.lastIndexOf('.');
                    if (i > -1) {
                        final String candidate = filename.toLowerCase();
                        if (File.isOfType(candidate, SUFFIX_GPX) || File.isOfType(candidate, SUFFIX_LOC)) {
                            v.addElement(File.idenFix(filename));
                        }
//#ifdef __RIM__
                          else if (candidate.endsWith(SUFFIX_GPX_REM)) {
                            v.addElement(File.resolveEncrypted(filename));
                        }
//#endif
                    } else if (subfolder == null && /* isDir: */filename.endsWith(File.PATH_SEPARATOR) && !filename.startsWith("images-")) {
                        v.addElement(filename);
                    }
                }
            }

        } catch (Throwable t) {

            // show error
            Desktop.showError(Resources.getString(Resources.NAV_MSG_LIST_STORES_FAILED), t, null);

        } finally {

            // close dir
            try {
                dir.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
        }
    }

    private UiList listStores(final NakedVector stores, final String title) {
        // create UI list
        UiList l = null;
        if (useNativeList) {
            try {
                l = new ExtList(title, List.IMPLICIT, names2strings(stores), null);
                l.setFitPolicy(Choice.TEXT_WRAP_OFF);
            } catch (Throwable t) {
                // ignore - can be IllegalArgumentException due to 255 limit etc
            }
        }
        if (l == null) {
            l = new SmartList(title);
        }
        l.setData(stores);

        // add commands
        l.setSelectCommand(cmdOpen);
        if (title == actionListWpts || title == actionListTracks || title.startsWith(actionListWpts)) { // == is OK
            l.addCommand(cmdBack);
        } else if (title == actionListTargets) { // == is OK
            l.addCommand(cmdCancel);
        }
        l.setCommandListener(this);

        return l;
    }

    private UiList listWaypoints(final String store, final Vector wpts,
                                 final boolean forceSort, final boolean tickerInUse) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("list waypoint from " + store);
//#endif

        // prepare list content
        if (forceSort || sortedWpts == null || !store.equals(sortedName)) {

            // use preferred sort method
            sort = Config.sort;

            // create new vector
            sortedWpts = null; // gc hint
            sortedWpts = new NakedVector((NakedVector) wpts);
            sortedName = store;

            // pre-sort
            sortWaypoints(list, wpts, tickerInUse);

        } else if (sort == SORT_BYDIST) { // has to be re-sorted everytime, but we can reuse existing vector

            // HACK to fight quicksort inefficiency when array is already sorted
            FileBrowser.shuffle(sortedWpts.getData());

            // re-sort
            sortWaypoints(list, sortedWpts, tickerInUse);

        }
        
        // create UI list
        final String title = (new StringBuffer(32)).append(store).append(" [").append(wpts.size()).append(']').toString();
        UiList l = null;
        if (useNativeList) {
            try {
                l = new ExtList(title, List.IMPLICIT, wpts2strings(sortedWpts), null);
                l.setFitPolicy(Choice.TEXT_WRAP_OFF);
            } catch (Throwable t) {
                // ignore - can be IllegalArgumentException due to 255 limit etc
            }
        }
        if (l == null) {
            l = new SmartList(title);
        }
        l.setData(sortedWpts);

        // selected first
        l.setSelectedIndex(0, true);

        // add commands
        l.setSelectCommand(cmdOpen);
        if (Desktop.wpts == wpts) {
            if (Desktop.routeDir != 0) {
                l.addCommand(cmdActionSetAsCurrent);
            } else {
                l.addCommand(cmdActionNavigateTo);
                if (sort == SORT_BYORDER) {
                    l.addCommand(cmdActionNavigateAlong);
                    l.addCommand(cmdActionNavigateBack);
                }
            }
            l.addCommand(cmdActionGoTo);
            if (Desktop.wptIdx > -1 && isCache()) {
                l.addCommand(cmdActionAddFieldNote);
            }
            if (Desktop.showall) {
                l.addCommand(cmdActionHideAll);
            } else if (Desktop.routeDir == 0) {
                l.addCommand(cmdActionShowAll);
            }
//            l.addCommand(cmdActionOverlay);
        } else if (Desktop.wpts == null || Desktop.wptIdx == -1) {
            l.addCommand(cmdActionNavigateTo);
            if (sort == SORT_BYORDER) {
                l.addCommand(cmdActionNavigateAlong);
                l.addCommand(cmdActionNavigateBack);
            }
            l.addCommand(cmdActionGoTo);
            l.addCommand(cmdActionShowAll);
//            l.addCommand(cmdActionOverlay);
        }
        l.addCommand(cmdActionSortByOrder);
        l.addCommand(cmdActionSortByName);
        l.addCommand(cmdActionSortByDist);
        l.addCommand(cmdBack);
        l.setCommandListener(this);

        return l;
    }

    private void close() {
        // gc hints
        use(null);
        // no more needed
        pane = null;
        // restore navigator
        Desktop.display.setCurrent(Desktop.screen);
    }

    private void appendCached(final Vector v, final NakedVector cached, final boolean omitFolders) {
        if (cached != null) {
            final Object[] names = cached.getData();
            for (int N = cached.size(), i = 0; i < N; i++) {
                final String name = (String) names[i];
                if (!hideStore(name) && !(omitFolders && File.isDir(name))) {
                    v.addElement(name);
                }
            }
        }
    }

    private String getStoresFolder() {
        if (subfolder == null) {
            return folder;
        }
        return folder + subfolder;
    }

    private float getDistanceHack(final Waypoint wpt) {
        Float fo = (Float) _distances.get(wpt);
        if (fo == null) {
            fo = new Float(_pointer.distance(wpt.getQualifiedCoordinates()));
            _distances.put(wpt, fo);
        }
        return fo.floatValue();
    }

    private void use(final UiList l) {
        // remove ticker
//-#ifndef __ANDROID__
        clearTicker();
//-#endif
        // use new
        list = l;
    }

    private void use(final UiList l, final boolean tickerInUse) {
        // remove ticker
//#ifdef __ANDROID__
//        if (tickerInUse) {
//            cz.kruch.track.ui.nokia.DeviceControl.setTicker(list.getUI(), null);
//        }
//#endif        
        // use use
        use(l);
    }

    private void clearTicker() {
        if (list != null && tickerInUse) {
//#ifdef __ANDROID__
            // no harm if there is no ticker
            cz.kruch.track.ui.nokia.DeviceControl.setTicker(list.getUI(), null);
//#else
            // better check for ticker existence, for dumb implementations may act crazily
            if (list.getTicker() != null) { 
                cz.kruch.track.ui.nokia.DeviceControl.setTicker(list.getUI(), null);
            }
//#endif
        }
        tickerInUse = false;
    }

    private void sortWaypoints(final UiList list, final Vector wpts, final boolean tickerInUse) {
        // do sort
        switch (sort) {

            case SORT_BYORDER: {

                // copy original list as is
                wpts.copyInto(sortedWpts.getData());

            } break;

            case SORT_BYNAME: {
                // sort using this comparer
                FileBrowser.sort(sortedWpts.getData(), this, 0, sortedWpts.size() - 1);

            } break;

            case SORT_BYDIST: {

                // may take some time - start ticker
                final boolean showTicker = list != null && list.isShown() && wpts.size() > 1024 && !tickerInUse;
                try {
                    if (showTicker) {
                        cz.kruch.track.ui.nokia.DeviceControl.setTicker(list.getUI(), Resources.getString(Resources.NAV_MSG_TICKER_LISTING));
                    }

                    // grab map position (used in by-dist sorting)
                    _pointer = navigator.getRelQc();

                    // create cache for distances
                    _distances = new Hashtable(sortedWpts.size());

                    // sort using this comparer
                    FileBrowser.sort(sortedWpts.getData(), this, 0, sortedWpts.size() - 1);

                } finally {

                    // gc hints
                    _pointer = null;
                    _distances = null;

                    // stop ticker
                    if (showTicker) {
                        cz.kruch.track.ui.nokia.DeviceControl.setTicker(list.getUI(), null);
                    }
                }
            } break;
        }
    }

    private void sortWaypoints(final UiList list, final int by, final boolean force,
                               final Vector wpts, final boolean tickerInUse) {
        // sorting criterium changed or sorting forced
        if (sort != by || force) {

            // remember criterium
            sort = by;

            // sort
            sortWaypoints(list, wpts, tickerInUse);

            // update list
            if (list instanceof ExtList) { // FIXME
                ((ExtList) list).setAll(wpts2strings(sortedWpts));
            }

            // sort
            switch (sort) {
                case SORT_BYORDER: {
                    // route navigation avail
                    list.addCommand(cmdActionNavigateAlong);
                    list.addCommand(cmdActionNavigateBack);
                } break;
                case SORT_BYNAME:
                case SORT_BYDIST: {
                    // route navigation NOT avail
                    list.removeCommand(cmdActionNavigateAlong);
                    list.removeCommand(cmdActionNavigateBack);
                } break;
            }

/*
            // OK, we know that the list is using sortedWpts as backend...
            ((SmartList) list).setSelectedItem(wpt);
*/          // no, after sort, focus on 1st item
            list.setSelectedIndex(0, true);

            // set marked item, if any
            if (Desktop.wpts == wpts && Desktop.wptIdx > -1) {
                list.setMarked(list.indexOf(wpts.elementAt(Desktop.wptIdx)));
            }

            // redraw list
            list.repaint();
        }
    }

    private void useCurrent() {
        if (inUseName != null && !inUseName.equals(currentName)) {
            stores.remove(inUseName);
        }
        inUseName = currentName;
        inUseWpts = null; // gc hint
        inUseWpts = currentWpts;
    }

//#ifdef __B2B__

    private void b2b_Action() {
        if (currentWpts != null && currentWpts.size() > 0 && Config.vendorNaviCmd != null && Config.vendorNaviCmd.length() > 0) {
            final String cmd = Config.vendorNaviCmd;
            if (cmd.equals(cmdActionNavigateTo.getLabel())) {
                // remember idx
                idx[depth] = currentWpts.elementAt(0);
                // call navigator
                navigator.setNavigateTo(inUseWpts = currentWpts, inUseName = currentName, 0, 0);
            } else if (cmd.equals(cmdActionNavigateAlong.getLabel())) {
                // remember idx
                idx[depth] = idx[depth] = currentWpts.elementAt(0);
                // call navigator
                navigator.setNavigateTo(inUseWpts = currentWpts, inUseName = currentName, 0, -1);
            } else if (cmd.equals(cmdActionShowAll.getLabel())) {
                // call navigator
                navigator.showWaypoints(currentWpts, currentName, 0);
            }
        }
    }

//#endif

    private static String[] names2strings(final NakedVector names) {
        final String[] strings = new String[names.size()];
        names.copyInto(strings);
        return strings;
    }

    private static String[] wpts2strings(final NakedVector wpts) {
        final int size = wpts.size();
        final Object[] source = wpts.getData();
        final String[] strings = new String[size];
        for (int i = 0; i < size; i++) {
            strings[i] = source[i].toString();
        }
        return strings;
    }

    private int wptIdx(final Vector items, final Waypoint item) {
        final Object[] elements = ((NakedVector) items).getData();
        for (int i = items.size(); --i >= 0;) {
            if (item.equals(elements[i])) {
                return i;
            }
        }
        return -1;
    }

//#if __SYMBIAN__ || __RIM__ || __ANDROID__
    private static final int BUFFERSIZE = 16384; // more memory available
//#else
    private static final int BUFFERSIZE = 8192; // conservative; also backward compatible
//#endif

    private static GpxVector parseWaypoints(final File file, final int fileType,
                                            final boolean lazyGs)
            throws IOException, XmlPullParserException {
        // result
        final GpxVector result = new GpxVector(256, 256);

        // input stream and parser
        InputStream in = null;
        HXmlParser parser = null;

        try {
            // open input
            in = new BufferedInputStream(file.openInputStream(), BUFFERSIZE);

            // create parser
            parser = new HXmlParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);

            // init parser
            parser.setInput(in, null); // null is for encoding autodetection

            // parse
            switch (fileType) {
                case TYPE_GPX: {
                    parseGpx(parser, result, lazyGs);
                } break;
                case TYPE_LOC: {
                    parseLoc(parser, result);
                }
                break;
            }
            
            return result;

        } finally {
            try {
                parser.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
            try {
                in.close();
            } catch (Exception e) { // NPE or IOE
                // ignore
            }
        }
    }

    private static void parseGpx(final HXmlParser parser, final GpxVector v,
                                 final boolean lazyGs)
            throws IOException, XmlPullParserException {

        GroundspeakBean gsbean = null;

        char[] name = null, cmt = null, sym = null;
        double lat = -1D, lon = -1D;
        float alt = Float.NaN;
        long timestamp = 0;

        final NakedVector links = new NakedVector(2, 2);
        final StringBuffer sb = new StringBuffer(16);
        final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        final CharArrayTokenizer tokenizer = new CharArrayTokenizer();
        final char[] timeDelims = new char[]{'+', '-', ':', 'T', 'Z'};

        for (int depth = 0, eventType = parser.next(); eventType != XmlPullParser.END_DOCUMENT; eventType = parser.next()) {
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
                                case TAG_TRKSEG: {
                                    v.startSegment();
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
                                case TAG_LINK: {
                                    // get link
                                    links.addElement(parser.getAttributeValue(null, ATTR_HREF));
                                } break;
                                case TAG_NAME: {
                                    // get name
                                    name = parser.nextChars();
                                } break;
                                case TAG_TIME: {
                                    // get time
                                    final char[] chars = parser.nextChars();
                                    try {
                                        tokenizer.init(chars, chars.length, timeDelims, false);
                                        int step = 0;
                                        while (tokenizer.hasMoreTokens() && step <= 5) {
                                            switch (step++) {
                                                case 0: {
                                                    calendar.set(Calendar.YEAR, tokenizer.nextInt());
                                                } break;
                                                case 1: {
                                                    calendar.set(Calendar.MONTH, tokenizer.nextInt() - 1);
                                                } break;
                                                case 2: {
                                                    calendar.set(Calendar.DATE, tokenizer.nextInt());
                                                } break;
                                                case 3: {
                                                    calendar.set(Calendar.HOUR_OF_DAY, tokenizer.nextInt());
                                                } break;
                                                case 4: {
                                                    calendar.set(Calendar.MINUTE, tokenizer.nextInt());
                                                } break;
                                                case 5: {
                                                    final double sss = tokenizer.nextDouble();
                                                    calendar.set(Calendar.SECOND, (int) sss);
                                                    calendar.set(Calendar.MILLISECOND, (int) ((sss - (int) sss) * 1000));
                                                } break;
                                            }
                                        }
                                        if (step >= 5) {
                                            timestamp = calendar.getTime().getTime();
                                        }
                                    } catch (Throwable t) {
                                        // ignore
                                    }
                                } break;
                                case TAG_GS_CACHE: {
                                    // create bean
                                    gsbean = new GroundspeakBean(GpxTracklog.GS_1_0_PREFIX,
                                                                 parser.getAttributeValue(null, ATTR_GS_ID));
                                    parseBean(parser, gsbean, 2, lazyGs);
                                } break;
                                case TAG_AU_CACHE: {
                                    // create bean
                                    gsbean = new GroundspeakBean(GpxTracklog.AU_1_0_PREFIX, null);
                                    parseBean(parser, gsbean, 2, lazyGs);
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
                        default:
                            // down one level
                            if (depth > 0) {
                                depth++;
                            }
                    }
                } break;
                case XmlPullParser.END_TAG: {
                    switch (depth) {
                        case 0: {
                            final int tag = parser.getHash();
                            switch (tag) {
                                case TAG_TRKSEG: {
                                    v.endSegment();
                                } break;
                            }
                        } break;
                        case 1: {
                            final int tag = parser.getHash();
                            switch (tag) {
                                case TAG_WPT:
                                case TAG_RTEPT:
                                case TAG_TRKPT: {
                                    // anonymous wpt, trkpt or rtept
                                    if (name == null || name.length == 0) {
                                        sb.delete(0, sb.length());
                                        NavigationScreens.append(sb, v.size() + 1, 10000);
                                        name = sb.toString().toCharArray();
                                    }

                                    // create wpt
                                    final Waypoint wpt;
                                    final QualifiedCoordinates qc = QualifiedCoordinates.newInstance(lat, lon, alt);
                                    if (gsbean != null || links.size() != 0) {
                                        wpt = new ExtWaypoint(qc, name, cmt, sym, timestamp, gsbean, links);
                                        gsbean = null;
                                        links.removeAllElements();
                                    } else if (timestamp != 0) {
                                        wpt = new StampedWaypoint(qc, name, cmt, sym, timestamp);
                                        timestamp = 0;
                                    } else {
                                        wpt = new Waypoint(qc, name, cmt, sym);
                                    }

                                    // add wpt to list
                                    v.addElement(wpt);

                                    // reset depth
                                    depth = 0;

                                    // reset temps
                                    alt = Float.NaN;
                                    lat = lon = -1D;
                                    name = cmt = sym = null;

                                } break;
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

    private static void parseBean(final HXmlParser parser, final GroundspeakBean gsbean,
                                  final int start, final boolean lazy) throws IOException, XmlPullParserException {
        if (start == 1 || lazy == false) {
            gsbean.ctor();
        } else {
            gsbean.setFileOffset(parser.elementOffset);
        }

        GroundspeakBean.Log gslog = null;

        for (int depth = start, eventType = parser.next(); ; eventType = parser.next()) {
            switch (eventType) {
                case XmlPullParser.START_TAG: {
                    switch (depth) {
                        case 1: { // standalone <cache> parsing
                            final int tag = parser.getHash();
                            if (TAG_GS_CACHE == tag || TAG_AU_CACHE == tag) {
                                depth = 2;
                            } else {
                                throw new IllegalStateException("Unexpected start tag: " + parser.getName());
                            }
                        } break;
                        case 2: {
                            final int tag = parser.getHash();
                            switch (tag) {
                                case TAG_NAME: {
                                    // get GS name
                                    gsbean.setName(parser.nextText().trim());
                                    // partial or full parsing?
                                    if (lazy) {
                                        return;
                                    }
                                } break;
                                case TAG_GS_TYPE: {
                                    // get GS type
                                    gsbean.setType(parser.nextText(GroundspeakBean.valuesCache));
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
                                    gsbean.setCountry(parser.nextText(GroundspeakBean.valuesCache));
                                } break;
                                case TAG_GS_DIFF: {
                                    // get GS difficulty
                                    gsbean.setDifficulty(parser.nextText(GroundspeakBean.valuesCache));
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
                                    gsbean.setTerrain(parser.nextText(GroundspeakBean.valuesCache));
                                } break;
                                case TAG_GS_CONTAINER: {
                                    // get GS container
                                    gsbean.setContainer(parser.nextText(GroundspeakBean.valuesCache));
                                } break;
                                case TAG_GS_SHORTL: {
                                    // get GS short listing
                                    gsbean.setShortListing(parser.nextText());
                                } break;
                                // TODO
                                case TAG_GS_LOGS: {
                                    // depth
                                    depth = 3;
                                } break;
                                default: {
                                    // skip
                                    parser.skipSubTree();
                                }
                            }
                        } break;
                        case 3: {
                            final int tag = parser.getHash();
                            switch (tag) {
                                case TAG_GS_LOG: {
                                    // depth
                                    depth = 4;
                                    // log instance
                                    gslog = new GroundspeakBean.Log(parser.getAttributeValue(null, ATTR_GS_ID));
                                } break;
                                default: {
                                    // skip
                                    parser.skipSubTree();
                                }
                            }
                        } break;
                        case 4: {
                            final int tag = parser.getHash();
                            switch (tag) {
                                case TAG_TIME: // AU timestamp
                                case TAG_GS_DATE: { // GS timestamp
                                    // date
                                    gslog.setDate(parser.nextText());
                                } break;
                                case TAG_GS_TYPE: {
                                    // date
                                    gslog.setType(parser.nextText());
                                } break;
                                case TAG_AU_FINDER:
                                case TAG_GS_FINDER: {
                                    // date
                                    gslog.setFinder(parser.nextChars());
                                } break;
                                case TAG_GS_TEXT: {
                                    // date
                                    gslog.setText(parser.nextText());
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
                        case 2: {
                            final int tag = parser.getHash();
                            if (TAG_GS_CACHE == tag || TAG_AU_CACHE == tag) {
                                return;
                            } else {
                                throw new IllegalStateException("Unexpected end tag: " + parser.getName());
                            }
                        } // no break here - unreachable
                        case 4: {
                            final int tag = parser.getHash();
                            if (TAG_GS_LOG == tag) {
                                // add log
                                gsbean.addLog(gslog);
                                // gc hint
                                gslog = null;
                            }
                        } // no break here intentionally
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

    private static void parseLoc(final HXmlParser parser, final NakedVector v)
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
