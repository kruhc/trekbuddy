// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.maps.Map;
import cz.kruch.track.maps.InvalidMapException;
import cz.kruch.track.maps.Atlas;
import cz.kruch.track.maps.io.LoaderIO;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;
import cz.kruch.track.location.GpxTracklog;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.location.Navigator;
//#ifdef __LOG__
import cz.kruch.track.util.Logger;
//#endif
import cz.kruch.track.util.Datum;
import cz.kruch.track.event.Callback;
import cz.kruch.track.AssertionFailedException;
import cz.kruch.track.TrackingMIDlet;
import cz.kruch.track.fun.Friends;
import cz.kruch.j2se.util.StringTokenizer;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.midlet.MIDlet;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Timer;
import java.util.Enumeration;
import java.util.TimerTask;
import java.util.Date;

import api.location.LocationProvider;
import api.location.LocationListener;
import api.location.Location;
import api.location.QualifiedCoordinates;
import api.location.LocationException;

/**
 * Application desktop.
 */
public final class Desktop extends GameCanvas
        implements Runnable, CommandListener, LocationListener,
                   Map.StateListener, Atlas.StateListener,
                   YesNoDialog.AnswerListener, Navigator {
//#ifdef __LOG__
    private static final Logger log = new Logger("Desktop");
//#endif

    // app title, for dialogs etc
    public static final String APP_TITLE = "TrekBuddy";

    // 'no map loaded' message header
    private static final String MSG_NO_MAP = "Map loading failed. ";

    // 'delta' symbol
    private static String DELTA = "d";

    // delta units
    private static final String DIST_STR_M = " m ";
    private static final String DIST_STR_KM = " km ";

    // dialog timeouts
    private static final int INFO_DIALOG_TIMEOUT    = 750;
    private static final int ALARM_DIALOG_TIMEOUT   = 3000;
    private static final int WARN_DIALOG_TIMEOUT    = 1500;

    // musical note
    private static final int NOTE = 91;

    // desktop screen and display
    public static Displayable screen = null;
    public static Display display;
    public static Font font;
    public static boolean partialFlush = true;

    // application
    private MIDlet midlet;

    // desktop components
    private MapViewer mapViewer;
    private OSD osd;
    private Status status;

    // desktop renderer
    private Renderer renderer;
    private Graphics graphics;

    // data components
    private Map map;
    private Atlas atlas;

    // groupware components
    private Friends friends;

    // locator
    private Locator locator;

    // screen mode
    private int mode = 0;

    // LSM/MSK commands
    private Command cmdFocus; // hope for MSK
    private Command cmdRun;
    private Command cmdRunLast;
    private Command cmdLoadMap;
    private Command cmdLoadAtlas;
    private Command cmdSettings;
    private Command cmdInfo;
    private Command cmdExit;
    // RSK commands
    private Command cmdOSD;

    // for faster movement
    private volatile int scrolls = 0;

    // browsing or tracking
    private boolean browsing = true;

    // loading states and last-op message
    private volatile boolean initializingMap = true;
    private volatile boolean loadingSlices = false;
    private volatile String loadingResult = "No default map. Use Options->Load Map/Atlas to load a map/atlas";

    // location provider and its last-op throwable and status
    private volatile LocationProvider provider;
    private volatile Object providerStatus;
    private volatile LocationException providerError;
    private volatile boolean stopRequest;
    private volatile boolean providerRestart;

    // logs
    private boolean tracklog;
    private GpxTracklog gpxTracklog;
    private GpxTracklog gpxWaypointlog;

    // last known valid location
    private Location location = null;

    // navigation
    private volatile Waypoint[] waypoints = new Waypoint[0];
    private volatile int currentWaypoint = -1;

    // focus/navigation
    private boolean navigating = false;

    // repeated event simulation for dumb devices
    private Timer repeatedKeyChecker;
    private TimerTask repeatedKeyCheck;
    private volatile int inAction = -1;

    // start/initialization
    private boolean guiReady = false;
    private boolean postInit = false;

    // relict
    private boolean isS65 = false;

    public Desktop(MIDlet midlet) {
        super(false);
        screen = this;
        display = Display.getDisplay(midlet);
        try {
            DELTA = new String(new byte[]{ (byte) 0xce, (byte) 0x94 }, "UTF-8");
        } catch (UnsupportedEncodingException e) {
        }
        resetFont();
        Datum.use(Config.getSafeInstance().getGeoDatum());
        NavigationScreens.initialize();
        this.midlet = midlet;
        this.repeatedKeyChecker = new Timer();
//#ifdef __S65__
        this.isS65 = TrackingMIDlet.isS65();
//#endif

        // adjust appearance
        this.setFullScreenMode(Config.getSafeInstance().isFullscreen());
        this.setTitle(APP_TITLE);

        // start renderer
        this.renderer = new Renderer();
        this.renderer.setPriority(Thread.MAX_PRIORITY);
        this.renderer.start();

        // create and add commands to the screen
        this.cmdOSD = new Command("OSD", Command.BACK, 1);
        this.cmdFocus = new Command("Track", Command.SCREEN, 1); // hope for MSK
        this.cmdRun = new Command("Start", Command.SCREEN, 2);
        if (Config.getSafeInstance().getBtDeviceName().length() > 0) {
            this.cmdRunLast = new Command("Start (" + Config.getSafeInstance().getBtDeviceName() + ")", Command.SCREEN, 3);
        }
        if (TrackingMIDlet.isFs()) {
            this.cmdLoadMap = new Command("Load Map", Command.SCREEN, 4);
            this.cmdLoadAtlas = new Command("Load Atlas", Command.SCREEN, 5);
        }
        this.cmdSettings = new Command("Settings", Command.SCREEN, 6);
        this.cmdInfo = new Command("Info", Command.SCREEN, 7);
        this.cmdExit = new Command("Exit", Command.SCREEN, 8);
        this.addCommand(cmdOSD);
        this.addCommand(cmdFocus);
        this.addCommand(cmdRun);
        if (Config.getSafeInstance().getBtDeviceName().length() > 0) {
            this.addCommand(cmdRunLast);
        }
        if (TrackingMIDlet.isFs()) {
            this.addCommand(cmdLoadMap);
            this.addCommand(cmdLoadAtlas);
        }
        this.addCommand(cmdSettings);
        this.addCommand(cmdInfo);
        this.addCommand(cmdExit);

        // handle comamnds
        this.setCommandListener(this);
    }

    public static void resetFont() {
        font = Font.getFont(Font.FACE_MONOSPACE,
                            Config.getSafeInstance().isOsdBoldFont() ? Font.STYLE_BOLD : Font.STYLE_PLAIN,
                            Config.getSafeInstance().isOsdMediumFont() ? Font.SIZE_MEDIUM : Font.SIZE_SMALL);
    }

    public void resetGui() {
        int width = getWidth();
        int height = getHeight();

        // clear main area with black
        Graphics g = isS65 ? getGraphics() : (graphics = getGraphics());
        g.setColor(0, 0, 0);
        g.fillRect(0, 0, width, height);
        g.setFont(font);
        g.clipRect(0, 0, width, height);

        // create bg bar
        int color = TrackingMIDlet.numAlphaLevels() > 2 ? (TrackingMIDlet.isSonyEricsson() ? 0xA03f3f3f : 0x807f7f7f) : 0xff7f7f7f;
        int[] shadow = new int[width * font.getHeight()];
        for (int i = shadow.length; --i >= 0; ) {
            shadow[i] = color;
        }
        Image bar = Image.createRGBImage(shadow, width, font.getHeight(), true);

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("GUI reset - step 1");
//#endif

        // create components
        if (osd == null) {
            osd = new OSD(0, 0, width, height, bar);
            _osd = osd.isVisible();
        } else {
            osd.resize(width, height, bar);
        }
        if (status == null) {
            status = new Status(0, 0, width, height, bar);
        } else {
            status.resize(width, height, bar);
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("GUI reset - step 2");
//#endif

        // setup map viewer if map is loaded
        if (map != null) {

            // create
            if (mapViewer == null) {
                mapViewer = new MapViewer(0, 0, width, height);
                mapViewer.setMap(map);
            } else {
                mapViewer.resize(width, height);
            }

            // update OSD
            osd.setInfo(map.transform(mapViewer.getPosition())/*.toString()*/, true);  // TODO listener
        }

        // locator
        if (locator == null) {
            locator = new Locator();
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("GUI reset - final step");
//#endif

        // render screen
        render(MASK_MAP | MASK_OSD);
    }

    public void initDefaultMap() {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("init default map");
//#endif

        try {
            map = Map.defaultMap(this);
            _setInitializingMap(false);
        } catch (Throwable t) {
            _updateLoadingResult(t);
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.info("~init default map");
//#endif
    }

    /* hack - call blocking method to show result in boot console */
    public void initMap() throws InvalidMapException {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("init map");
//#endif

        try {
            String mapPath = Config.getSafeInstance().getMapPath();
            String mapName = null;
            Atlas _atlas = null;

            // load atlas first
            if (mapPath.indexOf('?') > -1) {
                StringTokenizer st = new StringTokenizer(mapPath, "?&=");
                String token = st.nextToken();
                _atlas = new Atlas(token, this);
                Throwable t = _atlas.loadAtlas();
                if (t == null) {
                    st.nextToken(); // layer
                    token = st.nextToken();
                    _atlas.setLayer(token);
                    st.nextToken(); // map
                    mapName = st.nextToken();
                    mapPath = _atlas.getMapURL(mapName);
                } else {
                    throw t;
                }
            }

            // load map now
            Map _map = new Map(mapPath, mapName, this);
            if (_atlas != null) {
                _map.setCalibration(_atlas.getMapCalibration(mapName));
            }
            Throwable t = _map.loadMap();
            if (t == null) {
                _setInitializingMap(false);
                map = _map;
                atlas = _atlas;
                // pre-cache initial map
                if (atlas != null && map != null) {
                    atlas.getMaps().put(map.getPath(), map);
                }
            } else {
                throw t;
            }
        } catch (InvalidMapException e) {
            _updateLoadingResult(e);
            throw e;
        } catch (Throwable t) {
            _updateLoadingResult(t);
            throw new InvalidMapException(t);
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.info("~init map");
//#endif
    }

    private void postInit() {
        // start Friends
        if (TrackingMIDlet.isJsr120() && Config.getSafeInstance().isLocationSharing()) {
//#ifdef __LOG__
             if (log.isEnabled()) log.info("starting SMS listener");
//#endif
            try {
                friends = new Friends(this);
            } catch (Throwable t) {
                showError("Failed to init location sharing", t, this);
            }
        }
    }

    protected void showNotify() {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("show notify");
//#endif

        if (!guiReady) { // first show
            try {
                resetGui();
            } finally {
                guiReady = true;
            }
        }
    }

    protected void sizeChanged(int w, int h) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("size changed: " + w + "x" + h);
//#endif

        resetGui();
    }

    protected void keyPressed(int i) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("keyPressed");
//#endif

        handleKey(i, false);
    }

    protected void keyRepeated(int i) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("keyRepeated");
//#endif

        // handle event
        handleKey(i, true);
    }

    protected void keyReleased(int i) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("keyReleased");
//#endif

        // for dumb devices
        inAction = -1;

        // scrolling stops
        scrolls = 0;

        // prohibit key check upon key release
        if (repeatedKeyCheck != null) {
            repeatedKeyCheck.cancel();
            repeatedKeyCheck = null;
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("repeated key check cancelled");
//#endif
        }
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command == cmdOSD) {
            if (isMap()) {
                // invert OSD visibility
                osd.setVisible(!osd.isVisible());
                _osd = osd.isVisible();
                // update screen
                render(MASK_OSD);
            } else {
                showWarning(_getLoadingResult(), null, this);
            }
        } else if (command == cmdFocus) {
            if (isMap()) {
                // invert function
                navigating = !navigating;
                // focus on last know location
                focus(); // includes screen update if necessary
            } else if (isTracking()) {
                showWarning(_getLoadingResult(), null, this);
            }
        } else if (command == cmdInfo) {
            (new InfoForm()).show(this,
                                  isTracking() ? provider.getException() : providerError,
                                  isTracking() ? provider.getStatus() : providerStatus);
        } else if (command == cmdSettings) {
            (new SettingsForm(new Event(Event.EVENT_CONFIGURATION_CHANGED))).show();
        } else if (command == cmdLoadMap) {
            (new FileBrowser("SelectMap", new Event(Event.EVENT_FILE_BROWSER_FINISHED, "map"), this)).show();
        } else if (command == cmdLoadAtlas) {
            (new FileBrowser("SelectAtlas", new Event(Event.EVENT_FILE_BROWSER_FINISHED, "atlas"), this)).show();
        } else if (command == cmdRun) {
            if ("Start".equals(cmdRun.getLabel())) {
                // start tracking
                stopRequest = providerRestart = false;
                preTracking(false);
            } else {
                // stop tracking
                stopRequest = true;
                stopTracking(false);
            }
            // update OSD
            render(MASK_OSD);
        } else if (command == cmdRunLast) {
            // start tracking with known device
            stopRequest = providerRestart = false;
            preTracking(true);
            // update OSD
            render(MASK_OSD);
        } else if (command == cmdExit) {
            (new YesNoDialog(this, this)).show("Do you want to quit?", "Yes / No");
        }
    }

    public void response(int answer) {
        if (answer == YesNoDialog.YES) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("exit command");
//#endif

            // stop tracking (close GPS connection, close tracklog)
            stopTracking(true);

/* read-only anyway, so let's hope it's ok to skip this
            // close map (fs handles)
            if (map != null) map.close();
*/

            // stop Friends
            if (friends != null) {
                friends.destroy();
            }

            // stop I/O loader
            LoaderIO.destroy();

            // stop device control
            cz.kruch.track.ui.nokia.DeviceControl.destroy();

            // stop renderer
            renderer.destroy();

            // anything else? no, bail out
            midlet.notifyDestroyed();
        }
    }

    public void locationUpdated(LocationProvider provider, Location location) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("location update: " + new Date(location.getTimestamp()) + ";" + location.getQualifiedCoordinates());
//#endif

        callSerially(new Event(Event.EVENT_TRACKING_POSITION_UPDATED,
                     location, null, provider));
    }

    public void providerStateChanged(LocationProvider provider, int newState) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("location provider state changed; " + newState);
//#endif

        callSerially(new Event(Event.EVENT_TRACKING_STATUS_CHANGED,
                     new Integer(newState), null, provider));
    }

    //
    // Navigator contract
    //

    public boolean isTracking() {
        return provider != null;
    }

    public Location getLocation() {
        if (mode == 0) {
            return location;
        } else {
            return locator.getLocation();
        }
    }

    /**
     * Gets current pointer coordinates (transformed to WGS-84).
     * @return current pointer coordinates
     */
    public QualifiedCoordinates getPointer() {
        if (mode == 0) {
            return Datum.current.toWgs84(map.transform(mapViewer.getPosition()));
        } else {
            return locator.getPointer();
        }
    }

    public void recordWaypoint(Waypoint wpt) {

        // start GPX waypoint logging if needed
        if (gpxWaypointlog == null) {
            gpxWaypointlog = new GpxTracklog(GpxTracklog.LOG_WPT, new Event(Event.EVENT_WAYPOINTLOG),
                                             APP_TITLE + " " + midlet.getAppProperty("MIDlet-Version"),
                                             gpxTracklog == null ? System.currentTimeMillis() : gpxTracklog.getTime());
            gpxWaypointlog.start();
        }

        // have trackpoint recorded too, for the waypoint to be "on the track"
        if (gpxTracklog != null) {
            gpxTracklog.insert(wpt);
        }

        // record the waypoint
        gpxWaypointlog.insert(wpt);
    }

    public void setNavigateTo(int pathIdx) {
        // remember
        currentWaypoint = pathIdx;
        navigating = true;

        // set waypoint for showing
        if (currentWaypoint > -1) {
            QualifiedCoordinates qc = Datum.current.toLocal(waypoints[currentWaypoint].getQualifiedCoordinates());
            if (map.isWithin(qc)) {
                mapViewer.setWaypoint(map.transform(qc));
                Desktop.showConfirmation("Waypoint set", this);
            } else {
                mapViewer.setWaypoint(null);
                Desktop.showWarning("Selected waypoint is off current map", null, this);
            }
        } else {
            mapViewer.setWaypoint(null);
        }

        // update extended OSD & pointer
        updateNavigationUI();

        // render
        render(MASK_MAP | MASK_OSD | MASK_CROSSHAIR);
    }

    public int getNavigateTo() {
        return currentWaypoint;
    }

    public Waypoint[] getPath() {
        return waypoints;
    }

    public void setPath(Waypoint[] path) {
        this.waypoints = path;
    }

    public void addWaypoint(Waypoint wpt) {
        Waypoint[] tmp = new Waypoint[waypoints.length + 1];
        System.arraycopy(waypoints, 0, tmp, 0, waypoints.length);
        tmp[waypoints.length] = wpt;
        waypoints = null; // gc hint
        waypoints = tmp;
    }

    //
    // ~Navigator
    //

    public void run() {
        int keyState = getKeyStates();
        int action = -1;

        if ((keyState & LEFT_PRESSED) != 0) {
            action = Canvas.LEFT;
        } else if ((keyState & RIGHT_PRESSED) != 0) {
            action = Canvas.RIGHT;
        } else if ((keyState & UP_PRESSED) != 0) {
            action = Canvas.UP;
        } else if ((keyState & DOWN_PRESSED) != 0) {
            action = Canvas.DOWN;
        }

        // dumb device?
        if (inAction != -1) {
            if (action == -1) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("use inAction value " + inAction);
//#endif
                action = inAction;
            }
/* has to be commented out for Siemens devices :-(
            inAction = -1;
*/
        }

/*
        // update OSD & pointer
        updateNavigationUI();
*/

        // action
        if (action > -1) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("repeated action " + action);
//#endif

            // scroll if possible
            if (!_getInitializingMap() && mapViewer.scroll(action)) {
                scrolls++;

                // no fast scrolling when loading slices
                if (!_getLoadingSlices()) {
                    int steps = 0;
                    if (scrolls >= 15) {
                        steps = 2;
                        if (scrolls >= 25) {
                            steps = 3;
                        }
                        if (scrolls >= 40) {
                            steps = 4;
                        }
                    }
                    while (steps-- > 0) {
                        mapViewer.scroll(action);
                    }
                }

                // update OSD
                osd.setInfo(map.transform(mapViewer.getPosition())/*.toString()*/, true);  // TODO listener

                // update extended OSD & pointer
                updateNavigationUI();

                // render screen
                render(MASK_MAP | MASK_OSD);
            }

            // repeat if not map loading
            if (!_getInitializingMap()) {
/*
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                }
                callSerially(this);
*/
                repeatedKeyCheck = new TimerTask() {
                    public void run() {
                        callSerially(Desktop.this);
                    }
                };
                repeatedKeyChecker.schedule(repeatedKeyCheck, 25);
//#ifdef __LOG__
                if (log.isEnabled()) log.info("key state check scheduled in 25 ms");
//#endif

            } else {
                // scrolling stops
                scrolls = 0;
            }
        } else {
            // scrolling stops
            scrolls = 0;
        }
    }

    private boolean updateNavigationUI() {

        // navigating to a waypoint?
        if ((navigating || browsing) && (currentWaypoint > -1)) {

            // get navigation info
            StringBuffer extInfo = osd._getSb();
            float azimuth = getNavigationInfo(extInfo, getPointer());

            // set course and delta
            mapViewer.setCourse(azimuth);
            osd.setExtendedInfo(extInfo.toString());

            // gc hint
            extInfo = null;

            return true;

        } else if (isTracking()) {

            /*
             * course and 2nd line already updated
             */

            return true;

        } else {

            // no extended info available
            osd.setExtendedInfo(null);
        }

        return false;
    }

    private void focus() {
        // tracking mode
        browsing = false;

        // caught in the middle of something?
        if (_getInitializingMap()) {
            return;
        }

        // update OSD & pointer
        updateNavigationUI();

        // not tracking?
        if (location == null) {
            return;
        }

        // when on map, update position
        Position position = map.transform(Datum.current.toLocal(location.getQualifiedCoordinates()));

        // do we have a real position?
        if (position == null) {
            return;
        }

        // move to given position
        if (mapViewer.move(position.getX(), position.getY())) {
            // render screen completely
            render(MASK_MAP | MASK_OSD | MASK_CROSSHAIR);
        } else {
            // small update
            render(MASK_OSD | MASK_CROSSHAIR);
        }
    }

    private void callSerially(Runnable r) {
        SmartRunnable.callSerially(r);
    }

    private void handleKey(int i, boolean repeated) {
        int action = getGameAction(i);
        switch (action) {
            case Canvas.DOWN:
            case Canvas.UP:
            case Canvas.LEFT:
            case Canvas.RIGHT: {
                if (!isMap()) {
                    showWarning(_getLoadingResult(), null, this);
                } else if (mode != 0) {
                    // handle only non-repeated events
                    if (!repeated) {
                        // redirect to locator
                        locator.handleAction(action);

                        // update desktop
                        render(0);
                    }
                } else {
                    // cursor movement breaks autofocus
                    browsing = true;

                    // update extended OSD & pointer
                    updateNavigationUI();

                    // when repeated and not yet fast-moving, go
                    if (repeated) {
                        if (scrolls == 0) {
                            callSerially(this);
                        }
                    } else { // single step

                        // for dumb devices
                        inAction = action;

                        // scrolled?
                        if (mapViewer.scroll(action)) {

                            // update OSD & navigation UI
                            osd.setInfo(map.transform(mapViewer.getPosition())/*.toString()*/, true);  // TODO listener
                            updateNavigationUI();

                            // render screen
                            render(MASK_MAP | MASK_OSD);

                        } else { // out of current map - find sibling map

                            String url = null;
                            QualifiedCoordinates fakeQc = null;
                            if (atlas != null) {
                                Character neighbour = mapViewer.boundsHit();

//#ifdef __LOG__
                                if (log.isEnabled()) log.debug("bounds hit? sibling is " + neighbour);
//#endif

                                if (neighbour != null) {
                                    QualifiedCoordinates qc = map.transform(mapViewer.getPosition());
                                    QualifiedCoordinates newQc = null;
                                    switch (neighbour.charValue()) {
                                        case 'N':
                                            newQc = new QualifiedCoordinates(qc.getLat() + 0.01D, qc.getLon());
                                            fakeQc = new QualifiedCoordinates(90D, qc.getLon());
                                            break;
                                        case 'S':
                                            newQc = new QualifiedCoordinates(qc.getLat() - 0.01D, qc.getLon());
                                            fakeQc = new QualifiedCoordinates(-90D, qc.getLon());
                                            break;
                                        case 'E':
                                            newQc = new QualifiedCoordinates(qc.getLat(), qc.getLon() + 0.01D);
                                            fakeQc = new QualifiedCoordinates(qc.getLat(), 180D);
                                            break;
                                        case 'W':
                                            newQc = new QualifiedCoordinates(qc.getLat(), qc.getLon() - 0.01D);
                                            fakeQc = new QualifiedCoordinates(qc.getLat(), -180D);
                                            break;
                                    }
                                    url = atlas.getMapURL(newQc);
                                }
                            }
                            if (url != null) {
                                // 'switch' flag
                                _switch = true;
                                // focus on these coords once the new map is loaded
                                _qc = fakeQc;
                                // start loading task
                                startOpenMap(url, null);
                            }
                        }

                        // for dumb phones
                        if (!hasRepeatEvents()) {
//#ifdef __LOG__
                            if (log.isEnabled()) log.debug("does not have repeat events");
//#endif

                            /*
                             * "A key's bit will be 1 if the key is currently down or has
                             * been pressed at least once since the last time this method
                             * was called."
                             *
                             * Therefore the dummy getKeyStates() call before invoking run().
                             */

                            // delayed check to emulate keyRepeated
                            repeatedKeyCheck = new TimerTask() {
                                public void run() {
                                    getKeyStates(); // trick
                                    callSerially(Desktop.this);
                                }
                            };
                            repeatedKeyChecker.schedule(repeatedKeyCheck, 1500);
                        }
                    }
                }
            } break;
            default: {
                if (!repeated) {
                    switch (i) {
                        case KEY_STAR: {
                            if (atlas != null) {
                                Enumeration e = atlas.getLayers();
                                if (e.hasMoreElements()) {
                                    (new ItemSelection(this, "LayerSelection",
                                                       new Event(Event.EVENT_LAYER_SELECTION_FINISHED, "switch"))).show(e);
                                } else {
                                    showInfo("No layers in current atlas.", this);
                                }
                            }
                        } break;
                        case KEY_POUND: {
                            if (atlas != null) {
                                Enumeration e = atlas.getMapNames();
                                if (e.hasMoreElements()) {
                                    (new ItemSelection(this, "MapSelection",
                                                       new Event(Event.EVENT_MAP_SELECTION_FINISHED, "switch"))).show(e);
                                } else {
                                    showInfo("No maps in current layer.", this);
                                }
                            }
                        } break;
                        case KEY_NUM0: {
                            if (isMap()) {
                                // cycle crosshair
                                mapViewer.nextCrosshair();
                                // update desktop
                                render((TrackingMIDlet.isSonyEricsson() ? MASK_MAP : MASK_NONE) | MASK_OSD | MASK_CROSSHAIR);
                            }
                        } break;
                        case KEY_NUM1: {
                            (new Waypoints(this)).show();
                        } break;
                        case KEY_NUM3: {
                            cz.kruch.track.ui.nokia.DeviceControl.setBacklight();
                        } break;
                        case KEY_NUM5: {
                            if (isMap()) {
                                // invert function
                                navigating = !navigating;
                                // focus on last know location
                                focus(); // includes screen update if necessary
                            } else if (isTracking()) {
                                showWarning(_getLoadingResult(), null, this);
                            }
                        } break;
                        case KEY_NUM9: {
                            if (mode == 0) {
                                mode = 1;
                                render(0);
                            } else {
                                mode = 0;
                                render(MASK_ALL);
                            }
                        } break;
                    }
                }
            }
        }
    }

    private void render(int mask) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("render " + Integer.toHexString(mask));
//#endif
        renderer.enqueue(mask | MASK_CROSSHAIR);
    }

    private void _render(int mask) {
        Graphics g = isS65 ? getGraphics() : graphics;

        // common screen params
        g.setFont(font);
        g.setColor(Config.getSafeInstance().isOsdBlackColor() ? 0x00000000 : 0x00ffffff);

        if (_getInitializingMap()) { // mapviewer not ready

            // clear window
            g.setColor(0xff, 0, 0);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(0x00ffffff);

            // draw loaded target
            if (_getLoadingResult() != null) {
                g.drawString(_getLoadingResult(), 0, 0, 0/*Graphics.TOP | Graphics.LEFT*/);
            }

            // draw loading status
            if ((mask & MASK_STATUS) != 0 && status != null) { // initializing map is not clear till EVENT_SLICES_LOADED
                status.render(g);
            }

        } else {

            // make sure mapviewer is (or will soon be) ready
            if ((mask & MASK_MAP) != 0 && !_getLoadingSlices() && isMap()) {
                _setLoadingSlices(mapViewer.ensureSlices());
            }

            // draw map
            if (/*(mask & MASK_MAP) != 0 && */isMap()) {
                if ((mask & MASK_MAP) != 0) {

                    // whole map redraw requested
                    mapViewer.render(g);

                } else {
                    /*
                     * Map areas for 'active' components need to be redrawn,
                     * for transparency not to get screwed.
                     */

                    // draw crosshair area
                    if ((mask & MASK_CROSSHAIR) != 0 && isMap()) {
                        mapViewer.render(g, mapViewer.getClip());
                    }

                    // draw OSD area
                    if ((mask & MASK_OSD) != 0 && osd != null) {
                        mapViewer.render(g, osd.getClip());
                    }

                    // draw status area
                    if ((mask & MASK_STATUS) != 0 && status != null) {
                        mapViewer.render(g, status.getClip());
                    }
                }
            }

            // draw crosshair/course
            if ((mask & MASK_CROSSHAIR) != 0 && isMap()) {
                mapViewer.render2(g);
            }

            // draw OSD
            if ((mask & MASK_OSD) != 0 && osd != null) {
                osd.render(g);
            }

            // draw status
            if ((mask & MASK_STATUS) != 0 && status != null) {
                status.render(g);
            }

            // if whole map was not redrawn, update (ie. flush offscreen buffer) only components
            if (((mask & MASK_MAP) == 0) && (partialFlush)) {

//#ifdef __LOG__
                if (log.isEnabled()) log.debug("partial flush");
//#endif

                if ((mask & MASK_CROSSHAIR) != 0 && isMap()) {
                    _flushClip(mapViewer.getClip());
                }
                if ((mask & MASK_OSD) != 0 && (osd != null)) {
                    _flushClip(osd.getClip());
                }
                if ((mask & MASK_STATUS) != 0 && (status != null)) {
                    _flushClip(status.getClip());
                }

                return;
            }
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("fullscreen flush");
//#endif

        // full flush
        flushGraphics();
    }

    private void _flushClip(int[] clip) {
        if (clip != null) {
            flushGraphics(clip[0], clip[1], clip[2], clip[3]);
        }
    }

    private boolean isMap() {
        return mapViewer != null;
    }

    public static void showConfirmation(String message, Displayable nextDisplayable) {
        showAlert(AlertType.CONFIRMATION, message, INFO_DIALOG_TIMEOUT, nextDisplayable);
    }

    public static void showAlarm(String message, Displayable nextDisplayable) {
        showAlert(AlertType.ALARM, message, ALARM_DIALOG_TIMEOUT, nextDisplayable);
    }

    public static void showInfo(String message, Displayable nextDisplayable) {
        showAlert(AlertType.INFO, message, INFO_DIALOG_TIMEOUT, nextDisplayable);
    }

    public static void showWarning(String message, Throwable t, Displayable nextDisplayable) {
        if (message == null) {
            message = "";
        }
        if (t != null) {
            if (message.length() > 0) message += " ";
            message += t.toString();
        }
        showAlert(AlertType.WARNING, message, WARN_DIALOG_TIMEOUT, nextDisplayable);
    }

    public static void showError(String message, Throwable t, Displayable nextDisplayable) {
        if (message == null) {
            message = "";
        }
        if (t != null) {
            if (message.length() > 0) message += " ";
            message += t.toString();
        }
        showAlert(AlertType.ERROR, message, Alert.FOREVER, nextDisplayable);
    }

    private static void showAlert(AlertType type, String message, int timeout,
                                  Displayable nextDisplayable) {
        Alert alert = new Alert(APP_TITLE, message, null, type);
        alert.setTimeout(timeout);
        if (nextDisplayable == null) {
            display.setCurrent(alert);
        } else {
            display.setCurrent(alert, nextDisplayable);
        }
    }

    private void preTracking(final boolean last) {

        // assertion - should never happen
        if (provider != null) {
            throw new AssertionFailedException("Tracking already started");
        }

        tracklog = false;
        String on = Config.getSafeInstance().getTracklogsOn();

        if (Config.TRACKLOG_NEVER.equals(on)) {
            boolean unused = last ? startTrackingLast() : startTracking();
        } else if (Config.TRACKLOG_ASK.equals(on)) {
            (new YesNoDialog(display.getCurrent(), new YesNoDialog.AnswerListener() {
                public void response(int answer) {
                    if (YesNoDialog.YES == answer) {
                        tracklog = true; // !
                    }
                    boolean unused = last ? startTrackingLast() : startTracking();
                }
            })).show("Start tracklog?", "Yes / No");
        } else if (Config.TRACKLOG_ALWAYS.equals(on)) {
            tracklog = true; // !
            boolean unused = last ? startTrackingLast() : startTracking();
        }
    }

    private boolean startTracking() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("start tracking...");
//#endif

        // which provider?
        String selectedProvider = Config.getSafeInstance().getLocationProvider();
        Class providerClass = null;

        // instantiate provider
        try {
            if (Config.LOCATION_PROVIDER_JSR179.equals(selectedProvider)) {
                providerClass = Class.forName("cz.kruch.track.location.Jsr179LocationProvider");
            } else if (Config.LOCATION_PROVIDER_JSR82.equals(selectedProvider)) {
                providerClass = Class.forName("cz.kruch.track.location.Jsr82LocationProvider");
            } else if (Config.LOCATION_PROVIDER_SIMULATOR.equals(selectedProvider)) {
                providerClass = Class.forName("cz.kruch.track.location.SimulatorLocationProvider");
            }
            provider = (LocationProvider) providerClass.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e.toString());
        }

        // set tracklog flag
        provider.setTracklog(tracklog);

        // register as listener
        provider.setLocationListener(this, -1, -1, -1);

        // start provider
        int state;
        try {
            state = provider.start();
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("provider started; state " + state);
//#endif
        } catch (Throwable t) {
            showError("Failed to start provider " + provider.getName() + ".", t, null);

            // clear member
            provider = null;

            return false;
        }

        // not browsing
        browsing = false;

        // update OSD
        osd.setProviderStatus(state);

        // update menu
        removeCommand(cmdRun);
        removeCommand(cmdRunLast);
        cmdRun = new Command("Stop", Command.SCREEN, 2);
        addCommand(cmdRun);

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("~start tracking");
//#endif

        return true;
    }

    private boolean startTrackingLast() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("start tracking using known device " + Config.getSafeInstance().getBtServiceUrl());
//#endif

        // instantiate BT provider
        try {
            Class providerClass = Class.forName("cz.kruch.track.location.Jsr82LocationProvider");
            provider = (LocationProvider) providerClass.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e.toString());
        }

        // set tracklog flag
        provider.setTracklog(tracklog);

        // register as listener
        provider.setLocationListener(this, -1, -1, -1);

        // update OSD
        osd.setProviderStatus(LocationProvider._STARTING);

        // (re)start BT provider
        (new Thread((Runnable) provider)).start();

        // not browsing
        browsing = false;

        // update menu
        removeCommand(cmdRun);
        removeCommand(cmdRunLast);
        cmdRun = new Command("Stop", Command.SCREEN, 2);
        addCommand(cmdRun);

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("~start tracking");
//#endif

        return true;
    }

    private boolean restartTracking() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("restart tracking");
//#endif

        // be aware
        providerRestart = true;

        // update OSD
        osd.setProviderStatus(LocationProvider._STARTING);

        // (re)start provider
        (new Thread((Runnable) provider)).start();

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("~restart tracking");
//#endif

        return true;
    }

    private boolean stopTracking(boolean exit) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("stop tracking " + provider);
//#endif

        // stop GPX logging
        stopGpxTracklog();
        stopGpxWaypointlog();

        // assertion - should never happen
        if (provider == null) {
//            throw new IllegalStateException("Tracking already stopped");
//#ifdef __LOG__
            if (log.isEnabled()) log.error("tracking already stopped");
//#endif
            return false;
        }

        // record provider status
        providerStatus = provider.getStatus();
        providerError = provider.getException();

        // stop provider
        try {
            provider.setLocationListener(null, -1, -1, -1);
            provider.stop();
        } catch (Throwable t) {
            showError("Failed to stop provider", t, null);
        } finally {
            provider = null;
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("provider stopped");
//#endif

        // when exiting, the bellow is not necessary - we can quit faster
        if (exit) return true;

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("restore UI");
//#endif

        // not tracking
        browsing = true;

        // update OSD & navigation UI
        osd.setProviderStatus(LocationProvider.OUT_OF_SERVICE);
        updateNavigationUI();

        // update menu
        removeCommand(cmdRun);
        removeCommand(cmdRunLast);
        cmdRun = new Command("Start", Command.SCREEN, 2);
        if (Config.getSafeInstance().getBtDeviceName().length() > 0) {
            cmdRunLast = new Command("Start (" + Config.getSafeInstance().getBtDeviceName() + ")", Command.SCREEN, 3);
        }
        addCommand(cmdRun);
        if (Config.getSafeInstance().getBtDeviceName().length() > 0) {
            addCommand(cmdRunLast);
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("~stop tracking");
//#endif

        return true;
    }

    private void startGpxTracklog() {
        // tracklog enabled & is GPX?
        if (provider.isTracklog() && Config.TRACKLOG_FORMAT_GPX.equals(Config.getSafeInstance().getTracklogsFormat())) {

            // restart?
            if (providerRestart) {

                // assertion
                if (gpxTracklog == null) {
                    throw new AssertionFailedException("GPX tracklog not started");
                }

                // break trkseg
                gpxTracklog.insert(Boolean.TRUE);

            } else {

                // assertion
                if (gpxTracklog != null) {
                    throw new AssertionFailedException("GPX tracklog already started");
                }

                // start new gpx
                gpxTracklog = new GpxTracklog(GpxTracklog.LOG_TRK, new Event(Event.EVENT_TRACKLOG),
                                              APP_TITLE + " " + midlet.getAppProperty("MIDlet-Version"),
                                              System.currentTimeMillis());
                gpxTracklog.start();
            }
        }
    }

    private void stopGpxTracklog() {
        if (gpxTracklog != null) {
            try {
                if (gpxTracklog.isAlive()) {
                    gpxTracklog.destroy();
                    gpxTracklog.join();
                }
            } catch (Throwable t) {
            } finally {
                gpxTracklog = null;
            }
        }
    }

    private void stopGpxWaypointlog() {
        if (gpxWaypointlog != null) {
            try {
                if (gpxWaypointlog.isAlive()) {
                    gpxWaypointlog.destroy();
                    gpxWaypointlog.join();
                }
            } catch (Throwable t) {
            } finally {
                gpxWaypointlog = null;
            }
        }
    }

    private float getNavigationInfo(StringBuffer extInfo, QualifiedCoordinates from) {
        /*
         * get distance and azimuth to current waypoint
         */
        Waypoint waypoint = waypoints[currentWaypoint];
        QualifiedCoordinates to = waypoint.getQualifiedCoordinates();
        float c = from.distance(to);
        int azimuth = from.azimuthTo(to, c);
        String uString = DIST_STR_M;
        if (c > 15000D) { // dist > 15 km
            c /= 1000D;
            uString = DIST_STR_KM;
        }

        /*
         * fill extended info
         */
        extInfo.append(DELTA).append('=');
        extInfo.append((int) c);
        extInfo.append(uString).append(azimuth).append(TrackingMIDlet.SIGN);

        return azimuth;
    }

    // temps for altas/map loading
    private Map _map;
    private Atlas _atlas;
    private QualifiedCoordinates _qc;
    private String _layer;
    private boolean _switch;
    private boolean _osd;

    private void startOpenMap(String url, String name) {
        // hide map viewer and OSD
        if (isMap()) {
            mapViewer.hide();
        }
        osd.setVisible(false);

        // message for the screen
        _updateLoadingResult("Loading map " + url);
        _setInitializingMap(true);

        // render screen
        render(MASK_NONE);

        // look for cached map first
        if (atlas != null) {
            _map = (Map) atlas.getMaps().get(url);
            if (_map != null) {
                // use cached map - fire event at once
                mapOpened(null, null);
            }
        }

        // open map (in background)
        if (_map == null) {
            _map = new Map(url, name, this);
            if (atlas != null && name != null) {
                // use cached calibration
                _map.setCalibration(atlas.getMapCalibration(name));
            }
            _map.open();
        }
    }

    private void startOpenAtlas(String url) {
        // hide map viewer and OSD
        if (isMap()) {
            mapViewer.hide();
        }
        osd.setVisible(false);

        // message for the screen
        _updateLoadingResult("Loading atlas " + url);
        _setInitializingMap(true);

        // render screen
        render(MASK_NONE);

        // open atlas (in background)
        _atlas = new Atlas(url, this);
        _atlas.open();
    }

    /*
     * Map.StateListener contract
     */

    public void mapOpened(Object result, Throwable throwable) {
        callSerially(new Event(Event.EVENT_MAP_OPENED, result, throwable, null));
    }

    public void slicesLoaded(Object result, Throwable throwable) {
        callSerially(new Event(Event.EVENT_SLICES_LOADED, result, throwable, null));
    }

    public void loadingChanged(Object result, Throwable throwable) {
        callSerially(new Event(Event.EVENT_LOADING_STATUS_CHANGED, result, throwable, null));
    }

    /*
     * Map.StateListener contract
     */

    public void atlasOpened(Object result, Throwable throwable) {
        callSerially(new Event(Event.EVENT_ATLAS_OPENED, result, throwable, null));
    }

    /* TODO remove
     * thread-safe helpers
     */

    private void _updateLoadingResult(Throwable t) {
        if (t == null) {
            loadingResult = null;
        } else {
            loadingResult = MSG_NO_MAP + t.toString();
        }
    }

    private void _updateLoadingResult(String s) {
        loadingResult = s;
    }

    public String _getLoadingResult() {
        return loadingResult;
    }

    private boolean _getLoadingSlices() {
        return loadingSlices;
    }

    private void _setLoadingSlices(boolean b) {
        loadingSlices = b;
    }

    private boolean _getInitializingMap() {
        return initializingMap;
    }

    private void _setInitializingMap(boolean b) {
        initializingMap = b;
    }

    /*
     * Desktop renderer.
     */

    public static final int MASK_NONE       = 0;
    public static final int MASK_MAP        = 1;
    public static final int MASK_OSD        = 2;
    public static final int MASK_STATUS     = 4;
    public static final int MASK_CROSSHAIR  = 8;
    public static final int MASK_ALL        = MASK_MAP | MASK_OSD | MASK_STATUS | MASK_CROSSHAIR;

    private final class Renderer extends Thread {

        private final Object sync = new Object();

        private volatile boolean go;
        private Integer mask;

        public Renderer() {
            this.go = true;
            this.mask = null;
        }

        public void enqueue(int mask) {
            synchronized (sync) {
                if (this.mask == null) {
                    this.mask = new Integer(mask);
                } else {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("merging " + Integer.toBinaryString(this.mask.intValue()) + " with " + Integer.toBinaryString(mask));
//#endif
                    this.mask = new Integer(this.mask.intValue() | mask);
                }
                sync.notify();
            }
        }

        public void run() {
            for (; go ;) {
                Integer m = null;
                synchronized (sync) {
                    while (mask == null && go) {
                        try {
                            sync.wait();
                        } catch (InterruptedException e) {
                        }
                    }

                    m = mask;
                    mask = null;
                }

                if (!go) break;

                try {
                    if (mode == 0) {
                        Desktop.this._render(m.intValue());
                    } else {
                        Desktop.this.locator.render();
                    }
                } catch (Throwable t) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.error("render failure", t);
                    t.printStackTrace();
//#endif
                    Desktop.showError("_RENDER FAILURE_", t, null);
                } finally {
                    m = null; // gc hint
                }

                if (!postInit && guiReady) {
                    postInit = true;
                    postInit();
                }
            }
        }

        public void destroy() {
            go = false;
            synchronized (sync) {
                sync.notify();
            }
            try {
                join();
            } catch (InterruptedException e) {
            }
        }
    }

    /**
     * Locator.
     */
    private final class Locator {
        private static final int SHORT_HISTORY_DEPTH = 20;
        private static final int LONG_HISTORY_DEPTH = 2 * SHORT_HISTORY_DEPTH;
        private static final String MSG_NO_WAYPOINT = "NO WPT";

        private Location location, location2;
        private Location[] locations;
        private Location[] locations2;
        private int count, count2;
        private QualifiedCoordinates coordinates, coordinates2;
        private float hdopAvg, hdopAvg2;

        private int phase = 0;

        private int width, height;
        /*private int ptSize;*/
        private int dx, dy;
        private int lineLength;

        private int rangeIdx = 3, rangeIdx2 = 2;
        private boolean termSwitch;

        private int fontHeight;
        private int navigationStrWidth;
        private StringBuffer sb;

        public Locator() {
            /*this.ptSize = TrackingMIDlet.point.getWidth();*/
            this.width = getWidth();
            this.height = getHeight();
            this.lineLength = Math.min(width - width / 10, height - height / 10);
            this.dx = (width - lineLength) >> 1;
            this.dy = (height - lineLength) >> 1;
            this.fontHeight = Desktop.font.getHeight();
            this.navigationStrWidth = Math.max(Desktop.font.stringWidth(MSG_NO_WAYPOINT), Desktop.font.stringWidth("14999 m"));
            this.sb = new StringBuffer(32);
            reset();
        }

        public void reset() {
            locations = new Location[SHORT_HISTORY_DEPTH];
            locations2 = new Location[LONG_HISTORY_DEPTH];
            count = count2 = 0;
            hdopAvg = hdopAvg2 = -1F;
        }

        public Location getLocation() {
            return termSwitch ? location2 : location;
        }

        public QualifiedCoordinates getPointer() {
            return termSwitch ? coordinates2 : coordinates;
        }

        public void update(Location l) {
            // update last position
            location = null; // gc hint
            location = l;

            // update short-term array
            append(locations, l);

            // recalc
            recalc();
        }

        public void handleAction(int action) {
            switch (action) {
                case Canvas.LEFT:
                    if (termSwitch) {
                        if (rangeIdx2 > 0) {
                            rangeIdx2--;
                        }
                    } else {
                        if (rangeIdx > 0) {
                            rangeIdx--;
                        }
                    }
                    break;
                case Canvas.RIGHT:
                    if (termSwitch) {
                        if (rangeIdx2 < (TrackingMIDlet.ranges[0].length - 1)) {
                            rangeIdx2++;
                        }
                    } else {
                        if (rangeIdx < (TrackingMIDlet.ranges[0].length - 1)) {
                            rangeIdx++;
                        }
                    }
                    break;
                case Canvas.UP:
                case Canvas.DOWN: {
                    termSwitch = !termSwitch;
                } break;
            }
        }

        private void append(Location[] array, Location l) {
            int idx = -1;
            for (int N = array.length, i = 0; i < N; i++) {
                if (array[i] == null) {
                    array[idx = i] = l;
                    break;
                }
            }
            if (idx == -1) {
                System.arraycopy(array, 1, array, 0, array.length - 1);
                array[array.length - 1] = l;
            }
        }

        private void recalc() {
            // compute short-term avg
            int c = compute(false);

            // is we have some data, compute long-term avg
            if (c > 0) {
                if ((phase++ % 5) == 0) {
                    // create long-term avg location (some values are irrelevant)
                    location2 = null; // gc hint
                    location2 = new Location(coordinates, -1, -1,
                                             location.getSat(), hdopAvg);

                    // update long-term array
                    append(locations2, location2);

                    // compute long-term avg
                    c = compute(true);
                }
            }
        }

        private int compute(boolean longTerm) {
            double latAvg = 0D, lonAvg = 0D;
            float hdopSum = 0F, wSum = 0F;
            int c = 0;

            Location[] array = longTerm ? locations2 : locations;

            // calculate short-term avg lat/lon and hdop
            for (int i = array.length; --i >= 0; ) {
                Location l = array[i];
                if (l != null) {
                    hdopSum += l.getHdop();
                    float w = 1f / l.getHdop();
                    QualifiedCoordinates qc = l.getQualifiedCoordinates();
                    latAvg += qc.getLat() * w;
                    lonAvg += qc.getLon() * w;
                    c++;
                    wSum += w;
                }
            }

            // calculate avg coordinates
            if (c > 0) {
                // calculate avg coordinates
                latAvg /= wSum;
                lonAvg /= wSum;
                if (longTerm) {
                    hdopAvg2 = hdopSum / c;
                    coordinates2 = null; // gc hint
                    coordinates2 = new QualifiedCoordinates(latAvg, lonAvg);
                    coordinates2.setHp(true);
                    count2 = c;
                } else {
                    hdopAvg = hdopSum / c;
                    coordinates = null; // gc hint
                    coordinates = new QualifiedCoordinates(latAvg, lonAvg);
                    coordinates.setHp(true);
                    count = c;
                }
            }

            return c;
        }

        private void render() {
            // local copies for faster access
            int _width = width;
            int _width2 = _width >> 1;
            int _height = height;
            int _height2 = _height >> 1;
/*
            int _ptSize = ptSize;
            int _ptSize2 = _ptSize / 2;
*/

            // term vars
            QualifiedCoordinates _coordinates = termSwitch ? coordinates2 : coordinates;
            Location[] _locations = termSwitch ? locations2 : locations;
            Location _location = termSwitch ? location2 : location;
            /*Image _point = termSwitch ? TrackingMIDlet.point2 : TrackingMIDlet.point;*/
            float _hdopAvg = termSwitch ? hdopAvg2 : hdopAvg;
            int _count = termSwitch ? count2 : count;
            int _rangeIdx = termSwitch ? rangeIdx2 : rangeIdx;
            int _color = termSwitch ? 0x0000ffff : 0x0000ff00;
            double[][] _ranges = TrackingMIDlet.ranges;

            // draw crosshair
            Graphics g = isS65 ? getGraphics() : graphics;
            g.setFont(Desktop.font);
            g.setColor(0x00000000);
            g.fillRect(0,0, _width, _height);
            g.setColor(0x00808080);
            g.drawLine(_width2, dy, _width2, _height - dy);
            g.drawLine(dx, _height2, _width - dx, _height2);

            // draw points
            if (_count > 0) {
                // locals
                double latAvg = _coordinates.getLat();
                double lonAvg = _coordinates.getLon();

                // get scales for given latitude
                double flat = latAvg / 10D;
                int ilat = (int) flat;
                if ((ilat < 8) && (flat - ilat) > 0.75D) {
                    ilat++;
                }
                double xScale = lineLength / (2 * _ranges[ilat][_rangeIdx]);
                double yScale = lineLength / (2 * _ranges[0][_rangeIdx]);

                // points color
                g.setColor(_color);

                // draw points
                for (int i = _locations.length; --i >= 0; ) {
                    Location l = _locations[i];
                    if (l != null) {
                        QualifiedCoordinates qc = l.getQualifiedCoordinates();
                        int x = _width2 + (int) ((qc.getLon() - lonAvg) * xScale);
                        int y = _height2 - (int) ((qc.getLat() - latAvg) * yScale);
//                    System.out.println("x = " + x + ", y = " + y + " [" + width / 2 + ", " + height / 2);
/*
                        g.drawImage(_point, x - _ptSize2, y - _ptSize2, 0);
*/
                        g.drawArc(x - 3, y - 3, 7, 7, 0, 360);
//                    System.out.println(l);
                    }
                }

                // reset sb
                sb.setLength(0);

                // draw calculated (avg) position
                g.setColor(0x00ffffff);
                g.drawString(_coordinates.toStringBuffer(sb).toString(), 0, 0, 0);
                g.drawString(Double.toString(_hdopAvg).substring(0, 3), 0, fontHeight, 0);
                g.drawString(Integer.toString(_location.getSat()), 0, 2 * fontHeight, 0);
/*
                g.drawImage(TrackingMIDlet.pointAvg, _width2 - _ptSize2, _height2 - _ptSize2, 0);
*/
                g.setColor(0x00ffff00);
                g.drawArc(_width2 - 3/*_ptSize2*/, _height2 - 3/*_ptSize2*/, 7, 7, 0, 360);

                // draw waypoint
                g.setColor(0x00ffff00);
                int wptIdx = getNavigateTo();
                if (wptIdx > -1) {
                    QualifiedCoordinates qc = getPath()[wptIdx].getQualifiedCoordinates();
                    int x = _width2 + (int) ((qc.getLon() - lonAvg) * xScale);
                    int y = _height2 - (int) ((qc.getLat() - latAvg) * yScale);
                    float distance = _coordinates.distance(qc);
                    String distanceStr = "???";
                    if (distance < 5f) {
                        distanceStr = Float.toString(distance).substring(0, 3) + " m";
                    } else if (distance < 15000f) {
                        distanceStr = Integer.toString((int) distance) + " m";
                    } else {
                        distanceStr = Integer.toString((int) (distance / 1000)) + " km";
                    }
                    int azimuth = _coordinates.azimuthTo(qc, distance);
                    NavigationScreens.drawWaypoint(g, x, y, 0);
                    NavigationScreens.drawArrow(g, azimuth, _width2, _height2, 0);
                    g.drawString(distanceStr,
                                 width - navigationStrWidth,
                                 height - 2 * fontHeight,
                                 0);
                    g.drawString(Integer.toString(azimuth) + " " + TrackingMIDlet.SIGN,
                                 width - navigationStrWidth,
                                 height - fontHeight,
                                 0);
                } else {
                    g.drawString(MSG_NO_WAYPOINT,
                                 width - navigationStrWidth,
                                 height - fontHeight,
                                 0);
                }
            } else {
                g.setColor(0x00ff0000);
                g.drawString("NO POSITION", 0, 0, 0);
            }

            // draw range
            g.setColor(0x00808080);
            g.drawLine(dx, _height - 3, dx, _height - 1);
            g.drawLine(dx, _height - 2, _width >> 1, _height - 2);
            g.drawLine(_width2, _height - 3, _width2, _height - 1);
            g.drawString(TrackingMIDlet.rangesStr[_rangeIdx], dx + 3, _height - fontHeight - 5, 0);

            // flush
            flushGraphics();
        }
    }

    /**
     * For external events.
     */
    private final class Event implements Runnable, Callback, YesNoDialog.AnswerListener {
        public static final int EVENT_CONFIGURATION_CHANGED         = 0;
        public static final int EVENT_FILE_BROWSER_FINISHED         = 1;
        public static final int EVENT_TRACKLOG                      = 2;
        public static final int EVENT_ATLAS_OPENED                  = 3;
        public static final int EVENT_LAYER_SELECTION_FINISHED      = 4;
        public static final int EVENT_MAP_SELECTION_FINISHED        = 5;
        public static final int EVENT_MAP_OPENED                    = 6;
        public static final int EVENT_SLICES_LOADED                 = 7;
        public static final int EVENT_LOADING_STATUS_CHANGED        = 8;
        public static final int EVENT_TRACKING_STATUS_CHANGED       = 9;
        public static final int EVENT_TRACKING_POSITION_UPDATED     = 10;
        public static final int EVENT_WAYPOINTLOG                   = 11;

        private int code;
        private Object result;
        private Throwable throwable;
        private Object closure;

        private Event(int code) {
            this.code = code;
        }

        private Event(int code, Object result, Throwable throwable, Object closure) {
            this.code = code;
            this.result = result;
            this.throwable = throwable;
            this.closure = closure;

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("private event constructed; " + this.toString());
//#endif
        }

        public Event(int code, Object closure) {
            this.code = code;
            this.closure = closure;
        }

        public void invoke(Object result, Throwable throwable) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("firing event " + this.toString());
//#endif

            this.result = result;
            this.throwable = throwable;

            run();
        }

        /**
         * Confirm using loaded atlas/map as default?
         */
        public void response(int answer) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("yes-no? " + answer);
//#endif

            // update cfg if requested
            if (answer == YesNoDialog.YES) {
                try {
                    Config config = Config.getSafeInstance();
                    if (atlas == null) {
                        config.setMapPath(map.getPath());
                    } else {
                        config.setMapPath(atlas.getURL(map.getName()));
                    }
                    config.update();

                    // let the user know
                    showConfirmation("Configuration updated.", Desktop.screen);

                } catch (ConfigurationException e) {

                    // show user the error
                    showError("Failed to update configuration.", e, Desktop.screen);
                }
            }
        }

        /** fail-safe */
        public void run() {
            try {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("event run; " + this.toString());
//#endif

                _run();

//#ifdef __LOG__
                if (log.isEnabled()) log.debug("~event run; " + this.toString());
//#endif
            } catch (Throwable t) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("event failure", t);
//#endif
                Desktop.showError("_EVENT FAILURE_", t, null);
            }
        }

        public void _run() {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("event " + this.toString());
//#endif

            switch (code) {

                case EVENT_CONFIGURATION_CHANGED: {
//                    if (result == Boolean.TRUE) {
//                        setFullScreenMode(Config.getSafeInstance().isFullscreen());
//                    } else {
                        // temporary solution
                        osd.setInfo(map.transform(mapViewer.getPosition())/*.toString()*/, true);  // TODO listener
                        render(MASK_OSD);
//                    }
                } break;

                case EVENT_FILE_BROWSER_FINISHED: {
                    // had user selected anything?
                    if (result != null) {

                        // user intention to load map or atlas
                        _switch = false;

                        // cast to file connection
                        api.file.File file = (api.file.File) result;
                        String url = file.getURL();

                        // close file connection
                        try {
                            file.close();
                        } catch (IOException e) {
                        }

                        // background task
                        if ("atlas".equals(closure)) {
                            startOpenAtlas(url);
                        } else {
                            startOpenMap(url, null);
                        }
                    }
                } break;

                case EVENT_TRACKLOG: {
                    if (throwable == null) {
                        if (result instanceof Integer) {
                            int c = ((Integer) result).intValue();
                            switch (c) {
                                case GpxTracklog.CODE_RECORDING_START:
                                    osd.setRecording("R");
                                    break;
                                case GpxTracklog.CODE_RECORDING_STOP:
                                    osd.setRecording(null);
                                    break;
                            }
                        }
                    } else {
                        // display warning
                        showWarning(result == null ? "GPX tracklog problem." : (String) result,
                                    throwable, Desktop.screen);

/* deadlocks
                        // stop gpx tracklog
                        stopGpxTracklog();
*/

                        // no more recording
                        osd.setRecording(null);
                    }

                    // update screen
                    render(MASK_OSD);

                } break;

                case EVENT_WAYPOINTLOG: {
                    if (throwable == null) {
                        if (result instanceof Integer) {
                            int c = ((Integer) result).intValue();
                            switch (c) {
                                case GpxTracklog.CODE_RECORDING_START:
                                    // nothing to do
                                    break;
                                case GpxTracklog.CODE_RECORDING_STOP:
                                    showWarning("Waypoints recording stopped", null, Desktop.screen);
                                    break;
                                case GpxTracklog.CODE_WAYPOINT_INSERTED:
                                    showConfirmation("Waypoint recorded", null);
                                    break;
                            }
                        }
                    } else {
                        // display warning
                        showWarning(result == null ? "GPX waypointlog problem." : (String) result,
                                    throwable, Desktop.screen);

/* deadlocks
                        // stop gpx waypointlog
                        stopGpxWaypointlog();
*/
                    }
                } break;

                case EVENT_ATLAS_OPENED: {
                    // if opening ok
                    if (throwable == null) {

                        // force user to select default layer
                        (new ItemSelection(Desktop.screen, "LayerSelection", new Event(Event.EVENT_LAYER_SELECTION_FINISHED))).show(_atlas.getLayers());

                    } else {
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("use old atlas");
//#endif

                        // restore desktop
                        restore();

                        // show a user error
                        showError((String) result, throwable, Desktop.screen);
                    }
                } break;

                case EVENT_LAYER_SELECTION_FINISHED: {
                    // had user selected anything?
                    if (result != null) {

                        // hack
                        _switch = closure == null ? false : true;

                        // from load task
                        if (closure == null) {

                            // layer change?
                            if (!result.toString().equals(_atlas.getLayer())) {

                                // setup atlas
                                _atlas.setLayer(result.toString());

                                // force user to select default map
                                (new ItemSelection(Desktop.screen, "MapSelection", new Event(Event.EVENT_MAP_SELECTION_FINISHED))).show(_atlas.getMapNames());
                            }

                        } else { // layer switch

                            // layer change?
                            if (!result.toString().equals(atlas.getLayer())) {

                                // get current lat/lon
                                QualifiedCoordinates qc0 = map.transform(mapViewer.getPosition());

                                // get map URL from atlas for given coords
                                String mapUrl = atlas.getMapURL(result.toString(), qc0);
                                String mapName = atlas.getMapName(result.toString(), qc0);
                                if (mapUrl == null) {
                                    showWarning("No map for current position in layer '" + result + "'.",
                                                null, Desktop.screen);
                                } else {
                                    _qc = qc0;
                                    _layer = result.toString();

                                    // background task
                                    startOpenMap(mapUrl, mapName);
                                }
                            }
                        }
                    } else { // cancelled

                        // from load task
                        if (closure == null) {

                            // release temps
                            _atlas.close();
                            _atlas = null;

                            // restore desktop
                            restore();
                        }
                    }
                } break;

                case EVENT_MAP_SELECTION_FINISHED: {
                    // had user selected anything?
                    if (result != null) {

                        // map name
                        String name = result.toString();

                        // OSD is already hidden from atlas layer selection

                        // hack
                        _switch = closure == null ? false : true;

                        // load task
                        if (closure == null) {

                            // background task
                            startOpenMap(_atlas.getMapURL(name), name);

                        } else { // change map

                            // background task
                            startOpenMap(atlas.getMapURL(name), name);
                        }
                    } else { // cancelled

                        // from load task
                        if (closure == null) {

                            // release temps
                            _atlas.close();
                            _atlas = null;

                            // restore desktop
                            restore();
                        }
                    }
                } break;

                case EVENT_MAP_OPENED: {
                    // update loading result
                    _updateLoadingResult((Throwable) throwable);

                    // if opening ok
                    if (throwable == null) {
                        try {
                            // temp
                            boolean yesno = _layer == null && _switch == false;
                            boolean wasmap = _atlas == null;

                            /*
                             * same as in initGui...
                             */

                            // release old atlas and use new
                            if (_atlas != null || !_switch) {
                                if (atlas != null) {
                                    atlas.close();
                                    atlas = null;
                                }
                            }
                            if (_atlas != null) {
                                atlas = _atlas;
                                _atlas = null;
                            }

                            // release old map and use new
                            if (map != null) {
                                if (_switch) {
                                    map.dispose();
                                } else {
                                    map.close();
                                }
                                map = null;
                            }
                            if (_map != null) {
                                map = _map;
                                _map = null;
                            }

                            // cache map
                            if (atlas != null && map != null) {
//#ifdef __LOG__
                                if (log.isEnabled()) log.debug("caching map " + map.getPath());
//#endif
                                atlas.getMaps().put(map.getPath(), map);
                            }

                            // update map viewer
                            if (mapViewer == null) {
                                mapViewer = new MapViewer(0, 0, getWidth(), getHeight());
                            }
                            if (mapViewer != null) {
                                // setup map viewer
                                mapViewer.setMap(map);
                                mapViewer.show();

                                // update waypoint navigation
                                setNavigateTo(currentWaypoint);
                            }

                            // clear flag
                            _setInitializingMap(false);

                            // move viewer to known position, if any
                            if (_qc != null) {
                                try {
                                    // handle fake qc when browsing across map boundary
                                    if (_qc.getLat() == 90D) {
                                        _qc = new QualifiedCoordinates(map.getRange()[3].getLat(), _qc.getLon());
                                    } else if (_qc.getLat() == -90D) {
                                        _qc = new QualifiedCoordinates(map.getRange()[0].getLat(), _qc.getLon());
                                    } else if (_qc.getLon() == 180D) {
                                        _qc = new QualifiedCoordinates(_qc.getLat(), map.getRange()[0].getLon());
                                    } else if (_qc.getLon() == -180D) {
                                        _qc = new QualifiedCoordinates(_qc.getLat(), map.getRange()[3].getLon());
                                    }
                                    // transform qc to position, and move to it
                                    Position p = map.transform(_qc); // already local datum
                                    if (p != null) {
                                        mapViewer.move(p.getX(), p.getY());
                                    }
                                } finally {
                                    _qc = null;
                                }
                            }

                            // update OSD & navigation UI
                            osd.setInfo(map.transform(mapViewer.getPosition())/*.toString()*/, true);  // TODO listener
                            updateNavigationUI();

                            // render screen - it will force slices loading
                            render(MASK_MAP | MASK_OSD);

                            // offer use as default?
                            if (yesno) {
                                if (wasmap) {
                                    (new YesNoDialog(Desktop.screen, this)).show("Use as default map?", map.getPath());
                                } else {
                                    (new YesNoDialog(Desktop.screen, this)).show("Use as default atlas?", atlas.getURL());
                                }
                            }
                        } catch (Throwable t) {

                            // restore desktop
                            restore();

                            // show user the error
                            showError("Failed to load map.", t, Desktop.screen);
                        } finally {

                            // clear flag
                            _setInitializingMap(false);
                        }
                    } else {
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("use old map and viewer");
//#endif

                        // restore desktop
                        restore();

                        // show user the error
                        showError((String) result, throwable, Desktop.screen);
                    }
                } break;

                case EVENT_SLICES_LOADED: {
                    // clear flag
                    _setLoadingSlices(false);

                    // if loading was ok
                    if (throwable == null) {

                        // was this layer change?
                        if (_layer != null) {
                            if (atlas != null) {
                                atlas.setLayer(_layer);
                            }
                            _layer = null;
                        }

                        // restore OSD
                        osd.setVisible(_osd);

                        // update screen
                        render(MASK_MAP | MASK_OSD | MASK_STATUS);

/* TODO did this ever work?
                        // check keys, for loading-in-move
                        if ((scrolls > 0) && isShown()) {
                            if (log.isEnabled()) log.debug("load-in-move");
                            display.callSerially(Desktop.this);
                        }
*/
                    } else {

                        // update loading result
                        _updateLoadingResult(throwable);

                        // restore desktop
                        restore();

                        // show user the error
                        showError((String) result, throwable, Desktop.screen);
                    }
                } break;

                case EVENT_LOADING_STATUS_CHANGED: {
                    // update loading result
                    _updateLoadingResult((Throwable) throwable);

                    // loading ok?
                    if (throwable == null) {

                        // update status
                        status.setInfo((String) result, true);

                        // when result is null, we may have another slice image ready
                        if (result == null) {
                            render(MASK_MAP | MASK_OSD | MASK_STATUS);
                        } else {
                            render(MASK_OSD | MASK_STATUS);
                        }

                    } else {

                        // show user the error
                        showError((String) result, throwable, Desktop.screen);
                    }
                } break;

                case EVENT_TRACKING_STATUS_CHANGED: {
                    // grab event data
                    int newState = ((Integer) result).intValue();

                    // update OSD
                    osd.setProviderStatus(newState);

                    // update desktop
                    render(MASK_OSD);

                    // how severe is the change
                    switch (newState) {
                        case LocationProvider._STARTING: {
                            // start gpx tracklog
                            startGpxTracklog();

                            // reset locator upon fresh start
                            if (!providerRestart) {
                                locator.reset();
                            }

                            // clear restart flag
                            providerRestart = false;
                        } break;

                        case LocationProvider.TEMPORARILY_UNAVAILABLE:
                            osd.setExtendedInfo(null);
                            // NO BREAK!
                        case LocationProvider.AVAILABLE: {
                            // beep
                            if (!Config.getSafeInstance().isNoSounds()) {
                                try {
                                    javax.microedition.media.Manager.playTone(NOTE, 250, 100);
                                } catch (Throwable t) {
                                }
                            }
                        } break;

                        case LocationProvider.OUT_OF_SERVICE: {
                            // alarm
                            AlertType.ALARM.playSound(display);

/*
                            // beep
                            if (!Config.getSafeInstance().isNoSounds()) {
                                try {
                                    javax.microedition.media.Manager.playTone(NOTE, 750, 100);
                                } catch (Throwable t) {
                                }
                            }

                            // and vibrate
                            display.vibrate(1500);
*/

                            // stop tracking completely or restart
                            if (stopRequest || !provider.isRestartable()) {
                                stopTracking(false);
                            } else {
                                restartTracking();
                            }
                        } break;

                        case LocationProvider._CANCELLED: {
                            // stop
                            stopTracking(false);
                        } break;
                    }
                } break;

                case EVENT_TRACKING_POSITION_UPDATED: {
                    // grab event data
                    Location l = (Location) result;

                    // update tracklog
                    if (gpxTracklog != null) {
                        try {
                            gpxTracklog.update(l);
                        } catch (Exception e) {
                            showWarning("GPX tracklog update failed.", e, Desktop.screen);
                        }
                    }

                    // if not valid position just quit
                    if (l.getFix() < 1) {
                        return;
                    }

                    // update last know valid location (WGS-84)
                    location = l;
                    locator.update(l);

                    // continue only if in tracking mode and not loading map or slices
                    if (browsing || _getInitializingMap()) {
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("not ready to update position");
//#endif
                        return;
                    }

                    // global & local coordinates
                    QualifiedCoordinates qc = location.getQualifiedCoordinates();
                    QualifiedCoordinates localQc = Datum.current.toLocal(qc);

                    // on map detection
                    boolean onMap = map.isWithin(localQc);

                    // set course (preserve last direction)
                    if (location.getCourse() > -1F) {
                        mapViewer.setCourse(location.getCourse());
                    }

                    // update OSD
                    if (Config.getSafeInstance().isUseGeocachingFormat() || Config.getSafeInstance().isUseUTM()) {
                        osd.setInfo(qc/*.toString()*/, onMap);
                    } else {
                        osd.setInfo(localQc/*.toString()*/, onMap);
                    }

                    // show useful info for geocaching
                    if (navigating && currentWaypoint > -1) {

                        // get navigation info
                        StringBuffer extInfo = osd._getSb();
                        float azimuth = getNavigationInfo(extInfo, qc);

                        // set course & navigation info
                        mapViewer.setCourse(azimuth);
                        osd.setExtendedInfo(extInfo.toString());

                        // gc hint
                        extInfo = null;

                    } else { // or usual GPS stuff

                        // in extended info
                        osd.setExtendedInfo(location.toExtendedInfo(osd._getSb()));
                        osd.setSat(location.getSat());
                    }

                    // are we on map?
                    if (onMap) {

                        // on position
                        focus(); // includes screen update if necessary

                    } else { // off current map

                        // load sibling map, if exists
                        if (atlas != null) {

                            // got map for given coords?
                            String url = atlas.getMapURL(qc);
                            if (url != null) {

                                // 'switch' flag
                                _switch = true;

                                // focus on these coords in new map once it is loaded
                                _qc = qc;

                                // start map loading task
                                startOpenMap(url, null);
                            }
                        }

                        // update screen
                        render(MASK_OSD);
                    }
                } break;
            }
        }

        private void restore() {
            // clear temporary vars
            if (_atlas != null) {
                _atlas.close();
                _atlas = null;
            }
            if (_map != null) {
                _map.close();
                _map = null;
            }

            // clear flags
            _setInitializingMap(false);
            _setLoadingSlices(false);

            // restore OSD
            osd.setVisible(_osd);

            // restore map viewer
            if (isMap()) {
                mapViewer.show();
            }

            // update screen
            render(MASK_MAP | MASK_OSD | MASK_STATUS);
        }

        // debug
        public String toString() {
            return "code " + code + ";result '" + result + "';throwable " + throwable;
        }
        // ~debug
    }
}
