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
//#ifdef __LOG__
import cz.kruch.track.util.Logger;
//#endif
import cz.kruch.track.event.Callback;
import cz.kruch.track.AssertionFailedException;
import cz.kruch.track.TrackingMIDlet;
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
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.midlet.MIDlet;
import java.io.IOException;
import java.util.Timer;
import java.util.Enumeration;
import java.util.TimerTask;
import java.util.Date;
import java.util.Vector;

import api.location.LocationProvider;
import api.location.LocationListener;
import api.location.Location;
import api.location.LocationException;
import api.location.QualifiedCoordinates;

/**
 * Application desktop.
 */
public final class Desktop extends GameCanvas
        implements Runnable, CommandListener, LocationListener,
                   Map.StateListener, Atlas.StateListener,
                   YesNoDialog.AnswerListener {

//#ifdef __LOG__
    private static final Logger log = new Logger("Desktop");
//#endif

    // app title, for dialogs etc
    public static final String APP_TITLE = "TrekBuddy";

    // 'no map loaded' message header
    private static final String MSG_NO_MAP = "Map loading failure. ";

    // dialog timeouts
    private static final int INFO_DIALOG_TIMEOUT = 750;
    private static final int WARN_DIALOG_TIMEOUT = 1500;

    // musical note
    private static final int NOTE = 91;

    // desktop screen and display
    public static Displayable screen = null;
    public static Display display;
    public static Font font;

    // application
    private MIDlet midlet;

    // desktop components
    private MapViewer mapViewer;
    private OSD osd;
    private Status status;

    // desktop renderer
    private Renderer renderer;

    // data components
    private Map map;
    private Atlas atlas;

    // LSM/MSK commands
    private Command cmdFocus; // hope for MSK
    private Command cmdRun;
    private Command cmdLoadMap;
    private Command cmdLoadAtlas;
    private Command cmdSettings;
    private Command cmdInfo;
    private Command cmdExit;
    // RSK commands
    private Command cmdOSD;

    // for faster movement
    private int scrolls = 0;

    // browsing or tracking
    private boolean browsing = true;

    // loading states and last-op message
    private volatile boolean initializingMap = true;
    private volatile boolean loadingSlices = false;
    private volatile String loadingResult = "No default map. Use Options->Load Map/Atlas to load a map/atlas";

    // location provider and its last-op throwable
    private LocationProvider provider;
    private LocationException providerResult = null;

    // GPX tracklog
    private GpxTracklog gpxTracklog;

    // last known valid location
    private Location location = null;

    // repeated event simulation for dumb devices
    private Timer repeatedKeyChecker;
    private int inAction = -1;

    public Desktop(MIDlet midlet) {
        super(false);
        screen = this;
        display = Display.getDisplay(midlet);
        font = Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        this.midlet = midlet;

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("hasRepeatEvents? " + hasRepeatEvents());
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
        this.cmdFocus = new Command("Focus", Command.SCREEN, 1); // hope for MSK
        this.cmdRun = new Command("Start", Command.SCREEN, 2);
        if (TrackingMIDlet.isFs()) {
            this.cmdLoadMap = new Command("Load Map", Command.SCREEN, 3);
            this.cmdLoadAtlas = new Command("Load Atlas", Command.SCREEN, 4);
        }
        this.cmdSettings = new Command("Settings", Command.SCREEN, 5);
        this.cmdInfo = new Command("Info", Command.SCREEN, 6);
        this.cmdExit = new Command("Exit", Command.SCREEN, 7);
        this.addCommand(cmdOSD);
        this.addCommand(cmdFocus);
        this.addCommand(cmdRun);
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

    public void resetGui() throws IOException {
        int width = getWidth();
        int height = getHeight();

        // clear main area with black
        Graphics g = getGraphics();
        g.setColor(0, 0, 0);
        g.fillRect(0, 0, width, height);
        g.setFont(font);

        // create components
        if (osd == null) {
            osd = new OSD(0, 0, width, height);
        } else {
            osd.resize(width, height);
        }
        if (status == null) {
            status = new Status(0, 0, width, height);
        } else {
            status.resize(width, height);
        }

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
            osd.setInfo(map.transform(mapViewer.getPosition()).toString(), true);  // TODO listener
        }

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
            String mapPath = Config.getInstance().getMapPath();
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
                map = _map;
                atlas = _atlas;
                _setInitializingMap(false);
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

    private boolean guiReady = false;

    protected void showNotify() {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("show notify");
//#endif

        if (!guiReady) { // first show
            try {
                resetGui();
                guiReady = true;
            } catch (IOException e) {
                // should not happen
            }
        }
    }

    protected void sizeChanged(int w, int h) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("size changed: " + w + "x" + h);
//#endif

        try {
            resetGui();
        } catch (IOException e) {
            // should not happen
        }
    }

    protected void keyPressed(int i) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("keyPressed");
//#endif

        // handle event
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

        // prohibit key check upon key release
        if (repeatedKeyChecker != null) {
            repeatedKeyChecker.cancel();
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
                // update screen
                render(MASK_OSD);
            } else {
                showWarning(_getLoadingResult(), null, this);
            }
        } else if (command == cmdFocus) {
            if (isMap()) {
                // focus on last know location
                focus(); // includes screen update if necessary
            } else if (isTracking()) {
                showWarning(_getLoadingResult(), null, this);
            }
        } else if (command == cmdInfo) {
            (new InfoForm()).show(provider == null ? providerResult : provider.getException());
        } else if (command == cmdSettings) {
            (new SettingsForm(new Event(Event.EVENT_CONFIGURATION_CHANGED))).show();
        } else if (command == cmdLoadMap) {
            (new FileBrowser("SelectMap", new Event(Event.EVENT_FILE_BROWSER_FINISHED, "map"))).show();
        } else if (command == cmdLoadAtlas) {
            (new FileBrowser("SelectAtlas", new Event(Event.EVENT_FILE_BROWSER_FINISHED, "atlas"))).show();
        } else if (command == cmdRun) {
            if ("Start".equals(cmdRun.getLabel())) {
                // start tracking
                startTracking();
                // update screen
                render(MASK_OSD);
            } else {
                // stop tracking
                stopTracking(false);
                // update screen
                render(MASK_OSD);
            }
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

            // stop I/O loader
            LoaderIO.destroy();

            // stop renderer
            renderer.stop();

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
        if ((action == -1) && (inAction != -1)) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("use inAction value " + inAction);
//#endif
            action = inAction;
        }

        // no extended OSD while browsing
        osd.setExtendedInfo(null);

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
                osd.setInfo(map.transform(mapViewer.getPosition()).toString(), true);  // TODO listener

                // render screen
                render(MASK_MAP | MASK_OSD);
            }

            // repeat if not map loading
            if (!_getInitializingMap()) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                }
                callSerially(this);
            } else {
                // scrolling stops
                scrolls = 0;
            }
        } else {
            // scrolling stops
            scrolls = 0;
        }
    }

    private void focus() {
        // tracking mode
        browsing = false;

        // caught in the middle of something?
        if (_getInitializingMap() || location == null) {
            return;
        }

        // when on map, update position
        Position position = map.transform(location.getQualifiedCoordinates());

        // do we have a real position?
        if (position == null) {
            return;
        }

        // set course
        mapViewer.setCourse(new Float(location.getCourse()));

        // move to given position
        if (mapViewer.move(position.getX(), position.getY())) {
            // render screen completely
            render(MASK_MAP | MASK_OSD);
        } else {
            // small update
            render(MASK_OSD);
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
                } else {
                    // cursor movement breaks autofocus
                    browsing = true;

                    // and also course showing and extended OSD
                    mapViewer.setCourse(null);
                    osd.setExtendedInfo(null);

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

                            // update OSD
                            osd.setInfo(map.transform(mapViewer.getPosition()).toString(), true);  // TODO listener
                            osd.setExtendedInfo(null);

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
                            repeatedKeyChecker = new Timer();
                            repeatedKeyChecker.schedule(new TimerTask() {
                                public void run() {
                                    getKeyStates();
                                    callSerially(Desktop.this);
                                }
                            }, 1500);
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
                            if (mapViewer != null) {
                                // cycle crosshair
                                mapViewer.nextCrosshair();
                                // update desktop
                                render(MASK_CROSSHAIR);
                            }
                        } break;
                        case KEY_NUM1: {
                            if (provider != null && location != null) {
                                (new WaypointForm(this, new Event(Event.EVENT_TRACKLOG), location)).show();
                            }
                        } break;
                        case KEY_NUM5: {
                            if (isMap()) {
                                // focus on last know location
                                focus(); // includes screen update if necessary
                            } else if (isTracking()) {
                                showWarning(_getLoadingResult(), null, this);
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
/*
        final int m = mask | MASK_CROSSHAIR;
        callSerially(new Runnable() {
            public void run() {
                Desktop.this._render(m);
            }
        });
*/
    }

    private void _render(int mask) {
        Graphics g = getGraphics();

        // common screen params
        g.setFont(font);
        g.setColor(255, 255, 255);

        if (_getInitializingMap()) { // mapviewer not ready

            // clear window
            g.setColor(0, 0, 0);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(255, 255, 255);

            // draw loaded target
            if (_getLoadingResult() != null) {
                g.drawString(_getLoadingResult(), 0, 0, Graphics.TOP | Graphics.LEFT);
            }

            // draw loading status
            if ((mask & MASK_STATUS) != 0 && status != null) { // initializing map is not clear till EVENT_SLICES_LOADED
                status.render(g);
            }

        } else {

            // make sure mapviewer is (or will soon be) ready
            if ((mask & MASK_MAP) != 0 && !_getLoadingSlices()) {
                _setLoadingSlices(mapViewer.ensureSlices());
            }

            // draw map
            if (/*(mask & MASK_MAP) != 0 && */mapViewer != null) {
                if ((mask & MASK_MAP) != 0) {

                    // whole map redraw requested
                    mapViewer.render(g);

                } else {
                    /*
                     * Map areas for 'active' components need to be redrawn,
                     * for transparency not to get screwed.
                     */

                    // draw crosshair area
                    if ((mask & MASK_CROSSHAIR) != 0 && mapViewer != null) {
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

            // draw crosshair
            if ((mask & MASK_CROSSHAIR) != 0 && mapViewer != null) {
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
            if ((mask & MASK_MAP) == 0) {

//#ifdef __LOG__
                if (log.isEnabled()) log.debug("partial flush");
//#endif

                if ((mask & MASK_CROSSHAIR) != 0 && mapViewer != null) {
                    _flushClip(mapViewer.getClip());
                }
                if ((mask & MASK_OSD) != 0 && osd != null) {
                    _flushClip(osd.getClip());
                }
                if ((mask & MASK_STATUS) != 0 && status != null) {
                    _flushClip(status.getClip());
                }

                return;
            }
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("fullscreen flush");
//#endif

        flushGraphics();
    }

    private void _flushClip(int[] clip) {
        if (clip != null) {
            flushGraphics(clip[0], clip[1], clip[2], clip[3]);
        }
    }

    private boolean isMap() {
        return mapViewer == null ? false : true;
    }

    private boolean isTracking() {
        return provider == null ? false : true;
    }

    public static void showConfirmation(String message, Displayable nextDisplayable) {
        showAlert(AlertType.CONFIRMATION, message, INFO_DIALOG_TIMEOUT, nextDisplayable);
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

    private boolean startTracking() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("start tracking " + provider);
//#endif

        // assertion - should never happen
        if (provider != null) {
            throw new IllegalStateException("Tracking already started");
        }

        // which provider?
        String selectedProvider = Config.getSafeInstance().getLocationProvider();

        // instantiate provider
        if (Config.LOCATION_PROVIDER_SIMULATOR.equals(selectedProvider)) {
            provider = new cz.kruch.track.location.SimulatorLocationProvider();
        } else if (Config.LOCATION_PROVIDER_JSR179.equals(selectedProvider)) {
            provider = new cz.kruch.track.location.Jsr179LocationProvider();
        } else if (Config.LOCATION_PROVIDER_JSR82.equals(selectedProvider)) {
            provider = new cz.kruch.track.location.Jsr82LocationProvider(new Event(Event.EVENT_TRACKLOG));
        }

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
        cmdRun = new Command("Stop", Command.SCREEN, 2);
        addCommand(cmdRun);

        return true;
    }

    private boolean stopTracking(boolean exit) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("stop tracking " + provider);
//#endif

        // stop gpx
        stopGpx();

        // assertion - should never happen
        if (provider == null) {
//            throw new IllegalStateException("Tracking already stopped");
            return false;
        }

        // stop provider
        try {
            provider.setLocationListener(null, -1, -1, -1);
            provider.stop();
        } catch (Throwable t) {
            showError("Failed to stop provider.", t, null);
        } finally {
            provider = null;
        }

        // when exiting, the bellow is not necessary - we can quit faster
        if (exit) return true;

        // not tracking
        browsing = true;

        // update OSD
        osd.setProviderStatus(LocationProvider.OUT_OF_SERVICE);

        // update menu
        removeCommand(cmdRun);
        cmdRun = new Command("Start", Command.SCREEN, 2);
        addCommand(cmdRun);

        return true;
    }

    private void startGpx() {
        // assert
        if (gpxTracklog != null) {
            throw new AssertionFailedException("GPX already started");
        }

        if (Config.getSafeInstance().isTracklogsOn() && Config.TRACKLOG_FORMAT_GPX.equals(Config.getSafeInstance().getTracklogsFormat())) {
            gpxTracklog = new GpxTracklog(new Event(Event.EVENT_TRACKLOG),
                                          APP_TITLE + " " + midlet.getAppProperty("MIDlet-Version"));
            gpxTracklog.start();
        }
    }

    private void stopGpx() {
        if (gpxTracklog != null) {
            gpxTracklog.destroy();
            try {
                gpxTracklog.join();
            } catch (InterruptedException e) {
            } finally {
                gpxTracklog = null;
            }
        }
    }

    // temp map and/or atlas
    private Map _map;
    private Atlas _atlas;
    private QualifiedCoordinates _qc;
    private String _layer;
    private boolean _switch;

    private void startOpenMap(String url, String name) {
        // hide map viewer and OSD
        mapViewer.hide();
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
        mapViewer.hide();
        osd.setVisible(false);

        // message for the screen
        _updateLoadingResult("Loading atlas " + url);
        _setInitializingMap(true);

        // render screen
        render(MASK_NONE);

        // open atlas (in background)
        _atlas = new Atlas(url, this);
        _atlas.prepareAtlas();
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
        return new String(loadingResult);
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
     * Queueable runnable for callSerially(...).
     */

    private static final class SmartRunnable implements Runnable {
        private static Vector runnables = new Vector();
        private static SmartRunnable current = null;

        private Runnable runnable;

        private SmartRunnable(Runnable runnable) {
            this.runnable = runnable;
        }

        public static synchronized void callSerially(Runnable r) {
            runnables.addElement(new SmartRunnable(r));
            callNext();
        }

        private static synchronized void callNext() {
            if (current == null) {
                if (runnables.size() > 0) {
                    current = (SmartRunnable) runnables.elementAt(0);
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("got next runnable to call serially: " + current.runnable);
//#endif
                    display.callSerially(current);
                } else {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("no runnable to call serially");
//#endif
                }
            }
        }

        private static synchronized void removeCurrent() {
            runnables.removeElementAt(0);
            current = null;
        }

        public void run() {
            try {
                runnable.run();
            } finally {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("call serially finished");
//#endif
                removeCurrent();
                callNext();
            }
        }
    }

    /*
     * Desktop renderer.
     */

    public static final int MASK_NONE       = 0;
    public static final int MASK_MAP        = 1;
    public static final int MASK_OSD        = 2;
    public static final int MASK_STATUS     = 4;
    public static final int MASK_CROSSHAIR  = 8;

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
                    Desktop.this._render(m.intValue());
                } catch (Throwable t) {
                    Desktop.showError("_RENDER FAILURE_", t, null);
                }
            }
        }

        public void stop() {
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

        public void response(int answer) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("yes-no? " + answer);
//#endif

            // update cfg if requested
            if (answer == YesNoDialog.YES) {
                try {
                    Config config = Config.getInstance();
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
                        render(MASK_NONE);
//                    }
                } break;

                case EVENT_FILE_BROWSER_FINISHED: {
                    // had user selected anything?
                    if (result != null) {

                        // user intention to load map or atlas
                        _switch = false;

                        // background task
                        if ("atlas".equals(closure)) {
                            startOpenAtlas((String) result);
                        } else {
                            startOpenMap((String) result, null);
                        }
                    }
                } break;

                case EVENT_TRACKLOG: {
                    if (throwable == null) {
                        if (result instanceof Integer) {
                            int c = ((Integer) result).intValue();
                            switch (c) {
                                case GpxTracklog.CODE_RECORDING_STOP:
                                    osd.setRecording(null);
                                    break;
                                case GpxTracklog.CODE_RECORDING_START:
                                    osd.setRecording("R");
                                    break;
                                case GpxTracklog.CODE_WAYPOINT_INSERTED:
                                    showConfirmation("Waypoint inserted", Desktop.screen);
                                    break;

                            }
                        } else if (result instanceof Waypoint) {
                            if (gpxTracklog != null) {
                                gpxTracklog.insert((Waypoint) result);
                            }
                        }
                    } else {
                        // display warning
                        showWarning(result == null ? "GPX tracklog warning." : (String) result,
                                    throwable, Desktop.screen);

                        // stop gpx
                        stopGpx();

                        // no more recording
                        osd.setRecording(null);
                    }

                    // update screen
                    render(MASK_OSD);

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
                                map.dispose();
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
                            if (mapViewer != null) {
                                mapViewer.setMap(map);
                                mapViewer.show();
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
                                    Position p = map.transform(_qc);
                                    if (p != null && mapViewer != null) {
                                        mapViewer.move(p.getX(), p.getY());
                                    }
                                } finally {
                                    _qc = null;
                                }
                            }

                            // update OSD
                            if (mapViewer != null) {
                                osd.setInfo(map.transform(mapViewer.getPosition()).toString(), true);  // TODO listener
                            }
                            osd.setExtendedInfo(null);

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
                        osd.setVisible(true);

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
                    LocationProvider provider = (LocationProvider) closure;

                    // provider last-op message
                    providerResult = provider.getException();

                    // how severe is the change
                    switch (newState) {
                        case LocationProvider._STARTING: {
                            // start gpx
                            startGpx();
                            // update desktop
                            render(MASK_OSD);
                        } break;

                        case LocationProvider.AVAILABLE:
                        case LocationProvider.TEMPORARILY_UNAVAILABLE: {
                            // beep
                            if (!Config.getSafeInstance().isNoSounds()) {
                                try {
                                    javax.microedition.media.Manager.playTone(NOTE, 250, 100);
                                } catch (Throwable t) {
                                }
                            }
                            // update OSD
                            osd.setProviderStatus(newState);
                            // update desktop
                            render(MASK_OSD);
                        } break;

                        case LocationProvider.OUT_OF_SERVICE: {
                            // beep
                            if (!Config.getSafeInstance().isNoSounds()) {
                                try {
                                    javax.microedition.media.Manager.playTone(NOTE, 750, 100);
                                } catch (Throwable t) {
                                }
                            }
                            // stop tracking completely
                            stopTracking(false);
                            // update desktop
                            render(MASK_OSD);
                        } break;
                    }
                } break;

                case EVENT_TRACKING_POSITION_UPDATED: {
                    // grab event data
                    Location location = (Location) result;

                    // update tracklog
                    if (gpxTracklog != null) {
                        try {
                            gpxTracklog.update(location);
                        } catch (Exception e) {
                            showWarning("GPX tracklog update failed.", e, Desktop.screen);
                        }
                    }

                    // if not valid position just quit
                    if (location.getFix() < 1) {
                        return;
                    }

                    // update last know valid location
                    Desktop.this.location = location;

                    // continue only if in tracking mode and not loading map or slices
                    if (browsing || _getInitializingMap()) {
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("not ready to update position");
//#endif
                        return;
                    }

                    // on map detection
                    boolean onMap = map.isWithin(location.getQualifiedCoordinates());

                    // update OSD
                    osd.setInfo(location.toInfo(), onMap);
                    if (Config.getSafeInstance().isOsdExtended()) {
                        osd.setExtendedInfo(location.toExtendedInfo(Config.getSafeInstance().getTimeZone()));
                    }

                    // are we on map?
                    if (onMap) {

                        // on position
                        focus(); // includes screen update if necessary

                    } else { // off current map

                        // load sibling map, if exists
                        if (atlas != null) {
                            // got map for given coords?
                            String url = atlas.getMapURL(location.getQualifiedCoordinates());
                            if (url != null) {
                                // 'switch' flag
                                _switch = true;
                                // focus on these coords in new map once it is loaded
                                _qc = location.getQualifiedCoordinates();
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
                _map.dispose();
                _map = null;
            }

            // clear flags
            _setInitializingMap(false);
            _setLoadingSlices(false);

            // restore OSD
            osd.setVisible(true);

            // restore map viewer
            if (mapViewer != null) {
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
