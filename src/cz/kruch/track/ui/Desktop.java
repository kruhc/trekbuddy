// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.maps.Map;
import cz.kruch.track.maps.InvalidMapException;
import cz.kruch.track.maps.Atlas;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;
import cz.kruch.track.location.SimulatorLocationProvider;
import cz.kruch.track.location.Jsr179LocationProvider;
import cz.kruch.track.location.Jsr82LocationProvider;
import cz.kruch.track.location.GpxTracklog;
import cz.kruch.track.util.Logger;
import cz.kruch.track.event.Callback;
import cz.kruch.track.AssertionFailedException;
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
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Enumeration;

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

    // log
    private static final Logger log = new Logger("Desktop");

    // app title, for dialogs etc
    public static final String APP_TITLE = "TrekBuddy";

    // 'no map loaded' message header
    private static final String MSG_NO_MAP = "No map loaded. ";

    // dialog timeouts
    private static final int INFO_DIALOG_TIMEOUT = 750;
    private static final int WARN_DIALOG_TIMEOUT = 1500;

    // musical note
    private static final int NOTE = 91;

    // application and display
    private MIDlet midlet;
    private Display display;

    // desktop components
    private MapViewer mapViewer;
    private OSD osd;
    private Status status;

    // data components
    private Map map;
    private Atlas atlas;
    private Object syncMap = new Object();

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
    private boolean initializingMap = false;
    private boolean loadingSlices = false;
    private String loadingResult = "No default map. Use Options->Load Map/Atlas to load a map/atlas";

    // location provider and its last-op throwable
    private LocationProvider provider;
    private LocationException providerResult = null;

    // GPX tracklog
    private GpxTracklog gpxTracklog;

    // last known valid location
    private Location location = null;

    // map event lock
    private Object lock = new Object();

    // repeated event simulation
    private Timer repeatedKeyChecker;
    private int inAction = -1;

    public Desktop(MIDlet midlet) {
        super(false);
        this.midlet = midlet;
        this.display = Display.getDisplay(midlet);

        // debug for defective impl
        if (log.isEnabled()) log.debug("hasRepeatEvents? " + hasRepeatEvents());

        // adjust appearance
        this.setFullScreenMode(Config.getSafeInstance().isFullscreen());
        this.setTitle(APP_TITLE);

        // create and add commands to the screen
        this.cmdOSD = new Command("OSD", Command.BACK, 1);
        this.cmdFocus = new Command("Focus", Command.SCREEN, 1); // hope for MSK
        this.cmdRun = new Command("Start", Command.SCREEN, 2);
        this.cmdLoadMap = new Command("Load Map", Command.SCREEN, 3);
        this.cmdLoadAtlas = new Command("Load Atlas", Command.SCREEN, 4);
        this.cmdSettings = new Command("Settings", Command.SCREEN, 5);
        this.cmdInfo = new Command("Info", Command.SCREEN, 6);
        this.cmdExit = new Command("Exit", Command.SCREEN, 7);
        this.addCommand(cmdOSD);
        this.addCommand(cmdFocus);
        this.addCommand(cmdRun);
        this.addCommand(cmdLoadMap);
        this.addCommand(cmdLoadAtlas);
        this.addCommand(cmdSettings);
        this.addCommand(cmdInfo);
        this.addCommand(cmdExit);

        // handle comamnds
        this.setCommandListener(this);
    }

    public void initGui() throws ConfigurationException, IOException {
        // clear main area with black
        Graphics g = getGraphics();
        g.setColor(0, 0, 0);
        g.fillRect(0, 0, getWidth(), getHeight());

        // create components
        osd = new OSD(0, 0, getWidth(), getHeight());
        status = new Status(0, 0, getWidth(), getHeight());

        // init map viewer if map is loaded
        if (map != null) {

            // create
            mapViewer = new MapViewer(0, 0, getWidth(), getHeight());

            // setup map viewer
            mapViewer.setMap(map);

            // update OSD
            osd.setInfo(map.transform(mapViewer.getPosition()).toString(), true);  // TODO listener

            // ensure slices are being loaded for current view
            _setLoadingSlices(mapViewer.ensureSlices());

            // if slices are ready, show map
            if (!_getLoadingSlices()) {
                render(true);
            }
        }
    }

    public void initDefaultMap() {
        try {
            map = Map.defaultMap(this);
        } catch (IOException e) {
            _updateLoadingResult(e);
        }
    }

    /*
     * hack - call blocking method to show result in boot console
     */
    public void initMap() throws ConfigurationException, IOException {
        try {
            String mapPath = Config.getInstance().getMapPath();
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
                    token = st.nextToken();
                    mapPath = _atlas.getMapURL(token);
                } else {
                    throw t;
                }
            }

            // load map now
            Map _map = new Map(mapPath, this);
            Throwable t = _map.loadMap();
            if (t == null) {
                map = _map;
                atlas = _atlas;
            } else {
                throw t;
            }
        } catch (OutOfMemoryError e) {
            _updateLoadingResult(e);
            throw e;
        } catch (ConfigurationException e) {
            _updateLoadingResult(e);
            throw e;
        } catch (IOException e) {
            _updateLoadingResult(e);
            throw e;
        } catch (RuntimeException e) {
            _updateLoadingResult(e);
            throw e;
        } catch (Error e) {
            _updateLoadingResult(e);
            throw e;
        } catch (Throwable t) {
            _updateLoadingResult(t);
            throw new InvalidMapException((Exception) t);
        }
    }

    /**
     * Destroys desktop.
     */
    public void destroy() {
        // log
        if (log.isEnabled()) log.info("destroy");

        // close map
        map.close();
    }

    protected void keyPressed(int i) {
        // log
        if (log.isEnabled()) log.info("keyPressed");

        // handle event
        handleKey(i, false);
    }

    protected void keyRepeated(int i) {
        // log
        if (log.isEnabled()) log.info("keyRepeated");

        // handle event
        handleKey(i, true);
    }

    protected void keyReleased(int i) {
        // log
        if (log.isEnabled()) log.info("keyReleased");

        // for dumb devices
        inAction = -1;

        // prohibit key check upon key release
        if (repeatedKeyChecker != null) {
            repeatedKeyChecker.cancel();
            if (log.isEnabled()) log.debug("repeated key check cancelled");
        }
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command == cmdOSD) {
            if (!isMap()) {
                showWarning(display, _getLoadingResult(), null, null);
            } else {
                // invert OSD visibility
                osd.setVisible(!osd.isVisible());
                // update screen
                render(false);
            }
        } else if (command == cmdFocus) {
            if (!isMap()) {
                showWarning(display, _getLoadingResult(), null, null);
            } else if (isTracking()) {
                // tracking mode
                browsing = false;
                // focus on last know location
                focus(); // includes screen update if necessary
            }
        } else if (command == cmdInfo) {
            (new InfoForm(display)).show(provider == null ? providerResult : provider.getException());
        } else if (command == cmdSettings) {
            (new SettingsForm(display, new DesktopEvent(DesktopEvent.EVENT_CONFIGURATION_CHANGED))).show();
        } else if (command == cmdLoadMap) {
            (new FileBrowser("SelectMap", display, new DesktopEvent(DesktopEvent.EVENT_FILE_BROWSER_FINISHED, "map"))).show();
        } else if (command == cmdLoadAtlas) {
            (new FileBrowser("SelectAtlas", display, new DesktopEvent(DesktopEvent.EVENT_FILE_BROWSER_FINISHED, "atlas"))).show();
        } else if (command == cmdRun) {
            if ("Start".equals(cmdRun.getLabel())) {
                startTracking();
            } else {
                stopTracking(false);
            }
        } else if (command == cmdExit) {
            (new YesNoDialog(display, this)).show("Do you want to quit?", "Yes / No");
        }
    }

    public void response(int answer) {
        if (answer == YesNoDialog.YES) {
            if (log.isEnabled()) log.debug("exit command");

            // stop tracking (close GPS connection, close tracklog)
            stopTracking(true);

/* read-only anyway, so let's hope it's ok to skip this
            // close map (fs handles)
            if (map != null) map.close();
*/

            // TODO stop I/O thread (LoaderIO)???

            // anything else? no, bail out
            midlet.notifyDestroyed();
        }
    }

    public void locationUpdated(LocationProvider provider, Location location) {
        if (log.isEnabled()) log.debug("location update: " + new Date(location.getTimestamp()) + ";" + location.getQualifiedCoordinates());

        // update tracklog
        if (gpxTracklog != null) {
            try {
                gpxTracklog.update(location);
            } catch (Exception e) {
                showWarning(display, "GPX tracklog update failed.", e, null);
            }
        }

        // if not valid position just quit
        if (location.getFix() < 1) {
            return;
        }

        // update last know valid location
        this.location = location;

        // continue only if in tracking mode and not loading map or slices
        if (browsing || _getLoadingSlices() || _getInitializingMap()) {
            if (log.isEnabled()) log.debug("not ready to update position");
            return;
        }

        // on map detection
        boolean onMap;
        synchronized (syncMap) {
            onMap = map.isWithin(location.getQualifiedCoordinates());
        } // synchronized

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

            // update screen
            render(false);

            // load sibling map, if possible
            if (atlas != null) {
                String url = atlas.getMapURL(location.getQualifiedCoordinates());
                if (url != null) {
                    _switch = true;
                    _qc = location.getQualifiedCoordinates();
                    startOpenMap(url, null);
                }
            }
        }
    }

    public void providerStateChanged(LocationProvider provider, int newState) {
        if (log.isEnabled()) log.info("location provider state changed; " + newState);

        // provider last-op message
        providerResult = provider.getException();

        // how severe is the change
        switch (newState) {
            case LocationProvider._STARTING: {
                // start gpx
                startGpx();
                // update desktop
                render(true);
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
                render(true);
            } break;

            case LocationProvider.OUT_OF_SERVICE: {
                // beep
                if (!Config.getSafeInstance().isNoSounds()) {
                    try {
                        javax.microedition.media.Manager.playTone(NOTE, 750, 100);
                    } catch (Throwable t) {
                    }
                }
                // stop tracking completely (also updates OSD and render)
                stopTracking(false);
            } break;
        }
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

        if ((action == -1) && (inAction != -1)) {
            if (log.isEnabled()) log.debug("use inAction value " + inAction);
            action = inAction;
        }

        if (action > -1) {
            if (log.isEnabled()) log.debug("repeated action " + action);

            // scroll if possible
            if (!_getLoadingSlices() && mapViewer.scroll(action)) {
                scrolls++;
                if (scrolls >= 15) {
                    int steps = 2;
                    if (scrolls >= 25) {
                        steps = 3;
                    }
                    if (scrolls >= 40) {
                        steps = 4;
                    }
                    while (steps-- > 0) {
                        mapViewer.scroll(action);
                    }
                }

                // update OSD
                osd.setInfo(map.transform(mapViewer.getPosition()).toString(), true);  // TODO listener
                osd.setExtendedInfo(null);

                // move made, ensure map viewer has slices
                _setLoadingSlices(mapViewer.ensureSlices());
                if (!_getLoadingSlices()) {
                    render(true);
                }
            }

            // repeat if not map loading
            if (!_getLoadingSlices()) {
                display.callSerially(this);
            }

        } else {
            if (log.isEnabled()) log.debug("stop scrolling");

            // scrolling stop
            scrolls = 0;
        }
    }

    private void focus() {
        // caught in the middle of something?
        if (location == null || _getInitializingMap() || _getLoadingSlices()) {
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

            // move made, ensure map viewer has slices
            _setLoadingSlices(mapViewer.ensureSlices());

            // if not loading, render
            if (!_getLoadingSlices()) {
                render(true);
            }
        } else {
            // update
            render(false);
        }
    }

    private void handleKey(int i, boolean repeated) {
        int action = getGameAction(i);
        switch (action) {
            case Canvas.DOWN:
            case Canvas.UP:
            case Canvas.LEFT:
            case Canvas.RIGHT: {
                if (mapViewer == null) {
                    showWarning(display, _getLoadingResult(), null, null);
                } else {

                    // cursor movement breaks autofocus
                    browsing = true;

                    // and also course showing and extended OSD
                    mapViewer.setCourse(null);
                    osd.setExtendedInfo(null);

                    // when repeated and not yet fast-moving, go
                    if (repeated) {
                        if (scrolls == 0) {
                            display.callSerially(this);
                        }
                    } else { // single step

                        // for dumb devices
                        inAction = action;

                        // scrolled?
                        if (mapViewer.scroll(action)) {

                            // update OSD
                            osd.setInfo(map.transform(mapViewer.getPosition()).toString(), true);  // TODO listener
                            osd.setExtendedInfo(null);

                            // ensure viewer has proper slices
                            _setLoadingSlices(mapViewer.ensureSlices());
                            if (!_getLoadingSlices()) {
                                render(true);
                            }
                        } else { // out of current map
                            String url = null;
                            QualifiedCoordinates fakeQc = null;
                            if (atlas != null) {
                                Character neighbour = mapViewer.boundsHit();
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
                                _switch = true;
                                _qc = fakeQc;
                                startOpenMap(url, null);
                            }
                        }

                        // for dumb phones
                        if (!hasRepeatEvents()) {
                            if (log.isEnabled()) log.debug("does not have repeat events");

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
                                    display.callSerially(Desktop.this);
                                }
                            }, 1000);
                        }
                    }
                }
            } break;
            default:
                switch (i) {
                    case KEY_STAR: {
                        if (atlas != null) {
                            Enumeration e = atlas.getLayers();
                            if (e.hasMoreElements()) {
                                (new ItemSelection(display, this, "LayerSelection",
                                                   new DesktopEvent(DesktopEvent.EVENT_LAYER_SELECTION_FINISHED, "switch"))).show(e);
                            } else {
                                showInfo(display, "No layers in current atlas.", null);
                            }
                        }
                    } break;
                    case KEY_POUND: {
                        if (atlas != null) {
                            Enumeration e = atlas.getMapNames();
                            if (e.hasMoreElements()) {
                                (new ItemSelection(display, this, "MapSelection",
                                                   new DesktopEvent(DesktopEvent.EVENT_MAP_SELECTION_FINISHED, "switch"))).show(e);
                            } else {
                                showInfo(display, "No maps in current layer.", null);
                            }
                        }
                    } break;
                }
        }
    }

    private void render(boolean deep) {
        Graphics g = getGraphics();
        if (_getInitializingMap()) {
            if (_getLoadingResult() != null) {
                g.setFont(Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_SMALL));
                g.setColor(255, 255, 255);
                g.drawString(_getLoadingResult(), 0, 0, Graphics.TOP | Graphics.LEFT);
            }
            if (status != null) { // initializing map is not clear till EVENT_SLICES_LOADED
                status.render(g);
            }
            flushGraphics();
        } else {
            if (mapViewer != null) {
                if (!_getLoadingSlices()) {
                    mapViewer.render(g);
                }
            }
            if (osd != null) {
                osd.render(g);
            }
            if (status != null) {
                status.render(g);
            }
            if (deep) {
                flushGraphics();
            } else {
                if (osd != null) {
                    int[] clip = osd.getClip();
                    flushGraphics(clip[0], clip[1], clip[2], clip[3]);
                }
                if (status != null) {
                    int[] clip = status.getClip();
                    flushGraphics(clip[0], clip[1], clip[2], clip[3]);
                }
            }
        }
    }

    private boolean isMap() {
        return mapViewer == null ? false : true;
    }

    private boolean isTracking() {
        return provider == null ? false : true;
    }

    public static void showConfirmation(Display display, String message, Displayable nextDisplayable) {
        showAlert(display, AlertType.CONFIRMATION, message, INFO_DIALOG_TIMEOUT, nextDisplayable);
    }

    public static void showInfo(Display display, String message, Displayable nextDisplayable) {
        showAlert(display, AlertType.INFO, message, INFO_DIALOG_TIMEOUT, nextDisplayable);
    }

    public static void showWarning(Display display, String message, Throwable t, Displayable nextDisplayable) {
        if (message == null) {
            message = "";
        }
        if (t != null) {
            if (message.length() > 0) message += " ";
            message += t.toString();
        }
        showAlert(display, AlertType.WARNING, message, WARN_DIALOG_TIMEOUT, nextDisplayable);
    }

    public static void showError(Display display, String message, Throwable t, Displayable nextDisplayable) {
        if (message == null) {
            message = "";
        }
        if (t != null) {
            if (message.length() > 0) message += " ";
            message += t.toString();
        }
        showAlert(display, AlertType.ERROR, message, Alert.FOREVER, nextDisplayable);
    }

    private static void showAlert(Display display, AlertType type,
                                  String message, int timeout,
                                  Displayable nextDisplayable) {
        Alert alert = new Alert(APP_TITLE, message, null, type);
        alert.setTimeout(timeout);
        if (nextDisplayable == null)
            display.setCurrent(alert);
        else
            display.setCurrent(alert, nextDisplayable);
    }

    private void _updateLoadingResult(Throwable t) {
        synchronized (lock) {
            if (t == null) {
                loadingResult = null;
            } else {
                loadingResult = MSG_NO_MAP + t.toString();
            }
        }
    }

    private void _updateLoadingResult(String s) {
        synchronized (lock) {
            loadingResult = s;
        }
    }

    public String _getLoadingResult() {
        synchronized (lock) {
            return loadingResult;
        }
    }

    private boolean startTracking() {
        if (log.isEnabled()) log.debug("start tracking " + provider);

        // assertion - should never happen
        if (provider != null) {
//            return false;
            throw new IllegalStateException("Tracking already started");
        }

        // which provider?
        String selectedProvider = Config.getSafeInstance().getLocationProvider();

        // instantiat provider
        if (Config.LOCATION_PROVIDER_SIMULATOR.equals(selectedProvider)) {
            provider = new SimulatorLocationProvider(display);
        } else if (Config.LOCATION_PROVIDER_JSR179.equals(selectedProvider)) {
            provider = new Jsr179LocationProvider();
        } else if (Config.LOCATION_PROVIDER_JSR82.equals(selectedProvider)) {
            provider = new Jsr82LocationProvider(display, new DesktopEvent(DesktopEvent.EVENT_TRACKLOG));
        }

        // register as listener
        provider.setLocationListener(this, -1, -1, -1);

        // start provider
        int state;
        try {
            state = provider.start();
            if (log.isEnabled()) log.debug("provider started; state " + state);
        } catch (LocationException e) {
            showError(display, "Failed to start provider " + provider.getName() + ".", e, null);

            // gc hint
            provider = null;

            return false;
        }

        // update OSD
        osd.setProviderStatus(state);

        // update screen
        render(false);

        // update menu
        removeCommand(cmdRun);
        cmdRun = new Command("Stop", Command.SCREEN, 2);
        addCommand(cmdRun);

        // not browsing
        browsing = false;

        return true;
    }

    private boolean stopTracking(boolean exit) {
        if (log.isEnabled()) log.debug("stop tracking " + provider);

        // stop gpx
        stopGpx();

        // assertion - should never happen
        if (provider == null) {
            return false;
        }

        // stop provider
        try {
            provider.setLocationListener(null, -1, -1, -1);
            provider.stop();
        } catch (LocationException e) {
            showError(display, "Failed to stop provider.", e, null);
        } finally {
            provider = null;
        }

        // when exiting, the bellow is not necessary
        if (exit) return true;

        // update OSD
        osd.setProviderStatus(LocationProvider.OUT_OF_SERVICE);

        // update screen
        render(false);

        // update menu
        removeCommand(cmdRun);
        cmdRun = new Command("Start", Command.SCREEN, 2);
        addCommand(cmdRun);

        // not tracking
        browsing = true;

        return true;
    }

    private void startGpx() {
        // assert
        if (gpxTracklog != null) {
            throw new AssertionFailedException("GPX already started");
        }

        if (Config.getSafeInstance().isTracklogsOn() && Config.TRACKLOG_FORMAT_GPX.equals(Config.getSafeInstance().getTracklogsFormat())) {
            gpxTracklog = new GpxTracklog(new DesktopEvent(DesktopEvent.EVENT_TRACKLOG),
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
        // message for the screen
        _updateLoadingResult("Opening map " + url);

        // show loading console
        toConsole();

        // open map (in background)
        _map = new Map(url, name, this);
        _setInitializingMap(_map.open());
    }

    private void startOpenAtlas(String url) {
        // message for the screen
        _updateLoadingResult("Opening atlas " + url);

        // show loading console
        toConsole();

        // open atlas (in background)
        _atlas = new Atlas(url, this);
        _setInitializingMap(_atlas.prepareAtlas());
    }

    private void toConsole() {
        // hide map viewer
        if (mapViewer != null) {
            mapViewer.hide();
        }

        // emulate console...
        Graphics g = getGraphics();
        g.setColor(0, 0, 0);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setFont(Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_SMALL));
        g.setColor(255, 255, 255);
        g.drawString(_getLoadingResult(), 0, 0, Graphics.TOP | Graphics.LEFT);
    }

    /*
     * Map.StateListener contract
     */

    public void mapOpened(Object result, Throwable throwable) {
/*
        (new DesktopEvent(DesktopEvent.EVENT_MAP_OPENED)).invoke(result, throwable);
*/
        display.callSerially(new DesktopEvent(DesktopEvent.EVENT_MAP_OPENED, result, throwable, null));
    }

    public void slicesLoaded(Object result, Throwable throwable) {
/*
        (new DesktopEvent(DesktopEvent.EVENT_SLICES_LOADED)).invoke(result, throwable);
*/
        display.callSerially(new DesktopEvent(DesktopEvent.EVENT_SLICES_LOADED, result, throwable, null));
    }

    public void loadingChanged(Object result, Throwable throwable) {
/*
        (new DesktopEvent(DesktopEvent.EVENT_LOADING_STATUS_CHANGED)).invoke(result, throwable);
*/
        display.callSerially(new DesktopEvent(DesktopEvent.EVENT_LOADING_STATUS_CHANGED, result, throwable, null));
    }

    /*
     * Map.StateListener contract
     */

    public void atlasOpened(Object result, Throwable throwable) {
/*
        (new DesktopEvent(DesktopEvent.EVENT_ATLAS_OPENED)).invoke(result, throwable);
*/
        display.callSerially(new DesktopEvent(DesktopEvent.EVENT_ATLAS_OPENED, result, throwable, null));
    }

    /*
    * thread-safe helpers
    */

    private boolean _getLoadingSlices() {
        synchronized (lock) {
            return loadingSlices;
        }
    }

    private void _setLoadingSlices(boolean b) {
        synchronized (lock) {
            loadingSlices = b;
        }
    }

    private boolean _getInitializingMap() {
        synchronized (lock) {
            return initializingMap;
        }
    }

    private void _setInitializingMap(boolean b) {
        synchronized (lock) {
            initializingMap = b;
        }
    }

    /**
     * For external events.
     */
    private class DesktopEvent implements Runnable, Callback, YesNoDialog.AnswerListener {
        public static final int EVENT_CONFIGURATION_CHANGED         = 0;
        public static final int EVENT_FILE_BROWSER_FINISHED         = 1;
        public static final int EVENT_TRACKLOG                      = 2;
        public static final int EVENT_ATLAS_OPENED                  = 3;
        public static final int EVENT_LAYER_SELECTION_FINISHED      = 4;
        public static final int EVENT_MAP_SELECTION_FINISHED        = 5;
        public static final int EVENT_MAP_OPENED                    = 6;
        public static final int EVENT_SLICES_LOADED                 = 7;
        public static final int EVENT_LOADING_STATUS_CHANGED        = 8;

        private int code;
        private Object result;
        private Throwable throwable;
        private Object closure;

        private DesktopEvent(int code) {
            this.code = code;
        }

        private DesktopEvent(int code, Object result, Throwable throwable, Object closure) {
            this.code = code;
            this.result = result;
            this.throwable = throwable;
            this.closure = closure;
            if (log.isEnabled()) log.debug("private event constructed; " + this.toString());
        }

        public DesktopEvent(int code, Object closure) {
            this.code = code;
            this.closure = closure;
        }

        public void invoke(Object result, Throwable throwable) {
            if (log.isEnabled()) log.debug("firing event " + this.toString());

            this.result = result;
            this.throwable = throwable;

            run();
        }

        public void response(int answer) {
            if (log.isEnabled()) log.debug("yes-no? " + answer);

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
                    showConfirmation(display, "Configuration updated.", Desktop.this);

                } catch (ConfigurationException e) {

                    // show user the error
                    showError(display, "Failed to update configuration.", e, Desktop.this);
                }
            }
        }

        public void run() {
            if (log.isEnabled()) log.debug("event " + this.toString());

            switch (code) {

                case EVENT_CONFIGURATION_CHANGED: {
                    // TODO
                } break;

                case EVENT_FILE_BROWSER_FINISHED: {
                    // had user selected anything?
                    if (result != null) {

                        // user intention to load map or atlas
                        _switch = false;

                        // hide OSD
                        osd.setVisible(false);

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
                            if (((Integer) result).intValue() == 0) {
                                osd.setRecording(null);
                            } else {
                                osd.setRecording("R");
                            }
                        }
                    } else {
                        // display warning
                        showWarning(display, result == null ? "GPX tracklog warning." : (String) result, throwable, null);

                        // stop gpx
                        stopGpx();

                        // no more recording
                        osd.setRecording(null);
                    }

                    // update screen
                    render(false);

                } break;

                case EVENT_ATLAS_OPENED: {
                    // if opening ok
                    if (throwable == null) {

                        // force user to select default layer
                        (new ItemSelection(display, Desktop.this, "LayerSelection", new DesktopEvent(DesktopEvent.EVENT_LAYER_SELECTION_FINISHED))).show(_atlas.getLayers());

                    } else {
                        if (log.isEnabled()) log.debug("use old atlas");

                        // restore desktop
                        restore();

                        // show a user error
                        showError(display, (String) result, throwable, null);
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
                                (new ItemSelection(display, Desktop.this, "MapSelection", new DesktopEvent(DesktopEvent.EVENT_MAP_SELECTION_FINISHED))).show(_atlas.getMapNames());
                            }

                        } else { // layer switch

                            // layer change?
                            if (!result.toString().equals(atlas.getLayer())) {

                                // get current lat/lon
                                QualifiedCoordinates qc0 = map.transform(mapViewer.getPosition());

                                // get map URL from atlas for given coords
                                String url = atlas.getMapURL(result.toString(), qc0);
                                if (url == null) {
                                    showWarning(display, "No map for current position in layer '" + result + "'.", null, Desktop.this);
                                } else {
                                    _qc = qc0;
                                    _layer = result.toString();

                                    // background task
                                    startOpenMap(url, null);
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
                    // if opening ok
                    if (throwable == null) {
                        try {
                            // temp
                            boolean yesno = _layer == null && _switch == false;
                            boolean wasmap = _atlas == null;

                            /*
                             * same as in initGui...
                             */

                            synchronized (syncMap) {
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
                                    map.close();
                                    map = null;
                                }
                                if (_map != null) {
                                    map = _map;
                                    _map = null;
                                }

                                // init map viewer
                                if (mapViewer != null) {
                                    mapViewer.reset();
                                } else {
                                    mapViewer = new MapViewer(0, 0, getWidth(), getHeight());
                                }
                                mapViewer.setMap(map);

                            } // synchronized

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
                                    if (p != null) {
                                        mapViewer.move(p.getX(), p.getY());
                                    }
                                } finally {
                                    _qc = null;
                                }
                            }

                            // update OSD (it is still hidden, though)
                            osd.setInfo(map.transform(mapViewer.getPosition()).toString(), true);  // TODO listener
                            osd.setExtendedInfo(null);

                            // ensure initial slice(s) are being loaded
                            _setLoadingSlices(mapViewer.ensureSlices());

                            // offer use as default?
                            if (yesno) {
                                if (wasmap) {
                                    (new YesNoDialog(display, this)).show("Use as default map?", map.getPath());
                                } else {
                                    (new YesNoDialog(display, this)).show("Use as default atlas?", atlas.getURL());
                                }
                            }
                        } catch (Throwable t) {

                            // clear flags
                            _updateLoadingResult(t);
                            _atlas = null;
                            _map = null;

                            // show user the error
                            showError(display, "Failed to load map.", t, null);
                        }
                    } else {
                        if (log.isEnabled()) log.debug("use old map and viewer");

                        // clear temps
                        if (_atlas != null) {
                            _atlas.close();
                            _atlas = null;
                        }
                        if (_map != null) {
                            _map.close();
                            _map = null;
                        }

                        // restore desktop
                        restore();

                        // show user the error
                        showError(display, (String) result, throwable, null);
                    }
                } break;

                case EVENT_SLICES_LOADED: {
                    // clear flags and save result
                    _setLoadingSlices(false);
                    _setInitializingMap(false);
                    _updateLoadingResult(throwable);

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
                        render(true);

/*
                        // repaint if shown
                        repaint();
*/

                        // check keys, for loading-in-move
                        if ((scrolls > 0) && isShown()) {
                            if (log.isEnabled()) log.debug("load-in-move");
                            display.callSerially(Desktop.this);
                        }
                    } else {
                        // show user the error
                        showError(display, (String) result, throwable, Desktop.this);
                    }
                } break;

                case EVENT_LOADING_STATUS_CHANGED: {
                    // update status
                    status.setInfo((String) result, true);

                    // do not deep render until loading is not finished
                    render(false);

/*
                    // repaint if shown
                    int[] clip = status.getClip();
                    repaint(clip[0], clip[1], clip[2], clip[3]);
*/
                } break;
            }
        }

        private void restore() {

            // clear flag
            _setInitializingMap(false);

            // restore OSD
            osd.setVisible(true);

            // restore map viewer
            if (mapViewer != null) {
                mapViewer.show();
            }

            // update screen
            render(true);

/*
            // repaint if shown
            repaint();
*/
        }

        // debug
        public String toString() {
            return "code " + code + ";result '" + result + "';throwable " + throwable;
        }
        // ~debug
    }
}
