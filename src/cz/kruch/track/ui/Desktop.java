// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.maps.Map;
import cz.kruch.track.maps.InvalidMapException;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;
import cz.kruch.track.location.SimulatorLocationProvider;
import cz.kruch.track.location.Jsr179LocationProvider;
import cz.kruch.track.location.Jsr82LocationProvider;
import cz.kruch.track.location.GpxTracklog;
import cz.kruch.track.util.Logger;
import cz.kruch.track.event.Callback;

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

import api.location.LocationProvider;
import api.location.LocationListener;
import api.location.Location;
import api.location.QualifiedCoordinates;
import api.location.LocationException;

/**
 * Application desktop.
 */
public class Desktop extends GameCanvas implements Runnable, CommandListener, LocationListener, Map.StateListener, YesNoDialog.AnswerListener {
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
    private static final int NOTE = 77;

    // application and display
    private MIDlet midlet;
    private Display display;

    // desktop components
    private MapViewer mapViewer;
    private OSD osd;
    private Status status;

    // data components
    private Map map;

    // LSM/MSK commands
    private Command cmdFocus; // hope for MSK
    private Command cmdRun;
    private Command cmdLoadMap;
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
    private String loadingResult = "No default map. Use Options->Load Map to load a map";

    // location provider and its last-op throwable
    private LocationProvider provider;
    private LocationException providerResult = null;

    // GPX tracklog
    private GpxTracklog gpxTracklog;

    // last known valid X-Y and L-L position
    private Position position = null;
    private QualifiedCoordinates coordinates = null;

    // event lock
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
        this.cmdSettings = new Command("Settings", Command.SCREEN, 4);
        this.cmdInfo = new Command("Info", Command.SCREEN, 5);
        this.cmdExit = new Command("Exit", Command.SCREEN, 6);
        this.addCommand(cmdOSD);
        this.addCommand(cmdFocus);
        this.addCommand(cmdRun);
        this.addCommand(cmdLoadMap);
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
            osd.setInfo(map.getCalibration().transform(mapViewer.getPosition()).toString(), true);  // TODO listener

            // ensure slices are being loaded for current view
            _setLoadingSlices(mapViewer.ensureSlices());

            // if slices are ready, show map
            if (!_getLoadingSlices()) {
                renderScreen(true, true);
            }
        }
    }

    public void initDefaultMap() {
        try {
            map = Map.defaultMap(this);
        } catch (IOException e) {
            // should never happen
        }
    }

    /*
     * hack - call blocking method to show result in boot console
     */
    public void initMap() throws ConfigurationException, IOException {
        try {
            Map _map = new Map(Config.getInstance().getMapPath(), this);
            Throwable t = _map.loadMap();
            if (t == null) {
                map = _map;
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
                osd.setVisible(!osd.isVisible());
                renderScreen(true, true);
            }
        } else if (command == cmdFocus) {
            if (!isMap()) {
                showWarning(display, _getLoadingResult(), null, null);
            } else {
                browsing = false;
                if (coordinates != null) {
                    osd.setInfo(coordinates.toString(), map.isWithin(coordinates));
                }
                focus();
            }
        } else if (command == cmdInfo) {
            (new InfoForm(display)).show(providerResult);
        } else if (command == cmdSettings) {
            (new SettingsForm(display, new DesktopEvent(DesktopEvent.EVENT_CONFIGURATION_CHANGED))).show();
        } else if (command == cmdLoadMap) {
            (new FileBrowser("SelectMap", display, new DesktopEvent(DesktopEvent.EVENT_FILE_BROWSER_FINISHED))).show();
        } else if (command == cmdRun) {
            if ("Start".equals(cmdRun.getLabel())) {
                startTracking();
            } else {
                stopTracking();
            }
        } else if (command == cmdExit) {
            (new YesNoDialog(display, this)).show("Do you want to quit?", "Yes / No");
        }
    }

    public void response(int answer) {
        if (answer == YesNoDialog.YES) {
            // stop tracking (GPS connection, tracklog fs handles)
            stopTracking();

            // close map (fs handles)
            if (map != null) map.close();

            // anything else? bail out
            midlet.notifyDestroyed();
        }
    }

    public void locationUpdated(LocationProvider provider, Location location) {
        if (log.isEnabled()) log.debug("location update: " + new Date(location.getTimestamp()) + ";" + location.getQualifiedCoordinates());

        // update tracklog
        if (gpxTracklog != null) {
            gpxTracklog.update(location);
        }

        // if not valid position just quit
        if (location.getFix() < 1) {
            return;
        }

        // update last know valid L-L
        coordinates = location.getQualifiedCoordinates();

        // are we on current map?
        boolean onMap = map.isWithin(coordinates);

        // update
        if (onMap) {
            position = map.getCalibration().transform(coordinates);
        }
        osd.setInfo(coordinates.toString(), onMap);

        // when not browsing and having all slices
        if (!browsing && !_getLoadingSlices()) {

            // are we on map?
            if (onMap) {

                // on position
                focus(); // includes screen update if necessary

            } else {
                // log
                if (log.isEnabled()) log.warn("position off current map");

                // update screen // TODO only top area is necessary to update
                renderScreen(true, true);
            }
        }
    }

    public void providerStateChanged(LocationProvider provider, int newState) {
        if (log.isEnabled()) log.info("location provider state changed; " + newState);

        switch (newState) {
            case LocationProvider.AVAILABLE:
                try {
                    javax.microedition.media.Manager.playTone(NOTE, 250, 100);
                } catch (Throwable t) {
                }
                break;
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                try {
                    javax.microedition.media.Manager.playTone(NOTE, 250, 100);
                } catch (Throwable t) {
                }
                break;
            case LocationProvider.OUT_OF_SERVICE:
                try {
                    javax.microedition.media.Manager.playTone(NOTE, 1000, 100);
                } catch (Throwable t) {
                }
                break;
        }

        // provider last-op message
        providerResult = provider.getException();

        // how severe is the change
        if (newState == LocationProvider.OUT_OF_SERVICE) {
            // stop tracking completely (also updates OSD and render)
            stopTracking();
        } else {
            // update OSD
            osd.setProviderStatus(newState);
            // update desktop
            renderScreen(true, true);
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
                osd.setInfo(map.getCalibration().transform(mapViewer.getPosition()).toString(), true);  // TODO listener

                // move made, ensure map viewer has slices
                _setLoadingSlices(mapViewer.ensureSlices());
                if (!_getLoadingSlices()) {
                    renderScreen(true, true);
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
        // do we have a real position?
        if (position == null) {
            return;
        }

        // move to given position
        if (mapViewer.move(position.getX(), position.getY())) {

            // move made, ensure map viewer has slices
            _setLoadingSlices(mapViewer.ensureSlices());
            if (!_getLoadingSlices()) {
                renderScreen(true, true);
            }
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
                            osd.setInfo(map.getCalibration().transform(mapViewer.getPosition()).toString(), true);  // TODO listener

                            // ensure viewer has proper slices
                            _setLoadingSlices(mapViewer.ensureSlices());
                            if (!_getLoadingSlices()) {
                                renderScreen(true, true);
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
                if (log.isEnabled()) log.debug("unhandled key " + getKeyName(i));
        }
    }

    // TODO:
    // improve
    // get rid of params (is possible)
    // optimize flush graphics (clip only) when only OSD or status has changed
    private void renderScreen(boolean deep, boolean flush) {
        if (isMap()) {
            Graphics g = getGraphics();
            if (_getInitializingMap()) {
                if (_getLoadingResult() != null) {
                    g.setFont(Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_SMALL));
                    g.setColor(255, 255, 255);
                    g.drawString(_getLoadingResult(), 0, 0, Graphics.TOP | Graphics.LEFT);
                }
            } else {
                if (deep) {
                    mapViewer.render(g);
                }
                if (osd != null) {
                    osd.render(g);
                }
                if (status != null) {
                    status.render(g);
                }
            }
        } else {
            if (log.isEnabled()) log.debug("no map viewer");
        }

        if (flush) {
            flushGraphics();
        }
    }

    private boolean isMap() {
        return mapViewer == null ? false : true;
    }

    public static void showConfirmation(Display display, String message, Displayable nextDisplayable) {
        Alert alert = new Alert(APP_TITLE, message + ".", null, AlertType.CONFIRMATION);
        alert.setTimeout(INFO_DIALOG_TIMEOUT);
        if (nextDisplayable == null)
            display.setCurrent(alert);
        else
            display.setCurrent(alert, nextDisplayable);
    }

    public static void showInfo(Display display, String message, Displayable nextDisplayable) {
        Alert alert = new Alert(APP_TITLE, message + ".", null, AlertType.INFO);
        alert.setTimeout(INFO_DIALOG_TIMEOUT);
        if (nextDisplayable == null)
            display.setCurrent(alert);
        else
            display.setCurrent(alert, nextDisplayable);
    }

    public static void showWarning(Display display, String message, Throwable t, Displayable nextDisplayable) {
        Alert alert = new Alert(APP_TITLE, message + (t == null ? "." : ". " + t.toString()), null, AlertType.WARNING);
        alert.setTimeout(WARN_DIALOG_TIMEOUT);
        if (nextDisplayable == null)
            display.setCurrent(alert);
        else
            display.setCurrent(alert, nextDisplayable);
    }

    public static void showError(Display display, String message, Throwable t, Displayable nextDisplayable) {
        Alert alert = new Alert(APP_TITLE, message + (t == null ? "." : (". " + t.toString())), null, AlertType.ERROR);
        alert.setTimeout(Alert.FOREVER);
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
            provider = new Jsr82LocationProvider(display);
        }

        // register as listener
        provider.setLocationListener(this, -1, -1, -1);

        // start provider
        int state;
        try {
            state = provider.start();
            if (log.isEnabled()) log.debug("provider started; state " + state);
        } catch (LocationException e) {
            showError(display, "Failed to start provider " + provider.getName(), e, null);

            // gc hint
            provider = null;

            return false;
        }

        // start gpx
        startGpx();

        // update OSD
        osd.setProviderStatus(state);

        // update screen
        renderScreen(true, true);

        // update menu
        removeCommand(cmdRun);
        cmdRun = new Command("Stop", Command.SCREEN, 2);
        addCommand(cmdRun);

        // not browsing
        browsing = false;

        return true;
    }

    private boolean stopTracking() {
        if (log.isEnabled()) log.debug("stop tracking " + provider);

        // // assertion - should never happen
        if (provider == null) {
            return false;
        }

        // stop gpx
        stopGpx();

        // stop provider
        try {
            provider.setLocationListener(null, -1, -1, -1);
            provider.stop();
        } catch (LocationException e) {
            showError(display, "Failed to stop provider", e, null);
        } finally {
            provider = null;
        }

        // update OSD
        osd.setProviderStatus(LocationProvider.OUT_OF_SERVICE);

        // update screen
        renderScreen(true, true);

        // update menu
        removeCommand(cmdRun);
        cmdRun = new Command("Start", Command.SCREEN, 2);
        addCommand(cmdRun);

        // not tracking
        browsing = true;

        return true;
    }

    private void startGpx() {
        if (Config.getSafeInstance().isTracklogsOn()) {
            if ((provider instanceof Jsr179LocationProvider) || ((provider instanceof Jsr82LocationProvider) && (Config.TRACKLOG_FORMAT_GPX.equals(Config.getSafeInstance().getTracklogsFormat())))) {
                gpxTracklog = new GpxTracklog(new DesktopEvent(DesktopEvent.EVENT_TRACKLOG),
                                              APP_TITLE + " " + midlet.getAppProperty("MIDlet-Version"));
                gpxTracklog.start();
                osd.setGpxRecording("R");
            }
        }
    }

    private void stopGpx() {
        if (gpxTracklog != null) {
            gpxTracklog.destroy();
            gpxTracklog = null;
            osd.setGpxRecording(null);
        }
    }

    // temp map
    private Map _map;

    private void startOpenMap(String url) {
        // hide map viewer
        if (mapViewer != null) {
            mapViewer.hide();
        }

        // message for the screen
        _updateLoadingResult("Opening map " + url);

        // emulate console...
        Graphics g = getGraphics();
        g.setColor(0, 0, 0);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setFont(Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_SMALL));
        g.setColor(255, 255, 255);
        g.drawString(_getLoadingResult(), 0, 0, Graphics.TOP | Graphics.LEFT);

        // open map (in background)
        _map = new Map(url, this);
        _setInitializingMap(_map.prepareMap());
    }

    /*
     * Map.StateListener contract
     */

    public void mapOpened(Object result, Throwable throwable) {
        (new DesktopEvent(DesktopEvent.EVENT_MAP_OPENED)).invoke(result, throwable);
    }

    public void slicesLoaded(Object result, Throwable throwable) {
        (new DesktopEvent(DesktopEvent.EVENT_SLICES_LOADED)).invoke(result, throwable);
    }

    public void loadingChanged(Object result, Throwable throwable) {
        (new DesktopEvent(DesktopEvent.EVENT_LOADING_STATUS_CHANGED)).invoke(result, throwable);
    }

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
    private class DesktopEvent implements Callback, YesNoDialog.AnswerListener {
        public static final int EVENT_CONFIGURATION_CHANGED         = 0;
        public static final int EVENT_FILE_BROWSER_FINISHED         = 1;
        public static final int EVENT_LOADING_STATUS_CHANGED        = 2;
        public static final int EVENT_SLICES_LOADED                 = 3;
        public static final int EVENT_MAP_OPENED                    = 4;
        public static final int EVENT_TRACKLOG                      = 5;

        private int code;
        private Object result;
        private Throwable throwable;

        private DesktopEvent(int code) {
            this.code = code;
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
                    config.setMapPath(map.getPath());
                    config.update();
                    showConfirmation(display, "Configuration updated", Desktop.this);
                } catch (ConfigurationException e) {
                    showError(display, "Failed to update configuration", e, Desktop.this);
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

                        // hide OSD
                        osd.setVisible(false);

                        // background task
                        startOpenMap((String) result);
                    }
                } break;

                case EVENT_LOADING_STATUS_CHANGED: {
                    // update status
                    status.setInfo((String) result, true);

                    // do not deep render until loading is not finished
                    renderScreen(false, true);

                    // repaint if shown
                    if (isShown()) {
                        serviceRepaints();
                    }
                } break;

                case EVENT_SLICES_LOADED: {
                    // temp var
                    boolean yesno = _getInitializingMap();

                    // clear flags and save result
                    _setLoadingSlices(false);
                    _setInitializingMap(false);
                    _updateLoadingResult(throwable);

                    // upon map init finished, restore OSD
                    if (yesno) {
                        // restore OSD
                        osd.setVisible(true);
                    }

                    // if loading was ok
                    if (throwable == null) {

                        // update screen
                        renderScreen(true, true);

                        // repaint if shown
                        if (isShown()) {
                            serviceRepaints();
                        }

                        // offer setting as default map
                        if (yesno) {

                            // use as default map?
                            (new YesNoDialog(display, this)).show("Use as default map", map.getPath());
                        }

                        // check keys, for loading-in-move
                        if ((scrolls > 0) && isShown()) {
                            if (log.isEnabled()) log.debug("load-in-move");
                            display.callSerially(Desktop.this);
                        }

                    } else {
                        showError(display, (String) result, throwable, null);
                    }
                } break;

                case EVENT_MAP_OPENED: {
                    // if opening ok
                    if (throwable == null) {
                        try {

                            /*
                             * same as in initGui...
                             */

                            // release old map
                            if (map != null) {
                                map.close();
                            }

                            // use new map
                            map = _map;

                            // create new map viewer
                            if (mapViewer != null) {
                                mapViewer.reset();
                            } else {
                                mapViewer = new MapViewer(0, 0, getWidth(), getHeight());
                            }

                            // setup map viewer
                            mapViewer.setMap(map);

                            // update OSD
                            osd.setInfo(map.getCalibration().transform(mapViewer.getPosition()).toString(), true);  // TODO listener

                            // ensure initial slice(s) are being loaded
                            _setLoadingSlices(mapViewer.ensureSlices());

                        } catch (IOException e) {
                            _updateLoadingResult(e);
                            showError(display, "Failed to load map", e, null);
                        }

                    } else {
                        if (log.isEnabled()) log.debug("use old map and viewer");

                        // clear flag
                        _setInitializingMap(false);

                        // restore OSD
                        osd.setVisible(true);

                        // restore map viewer
                        if (mapViewer != null) {
                            mapViewer.show();
                        }

                        // update screen
                        renderScreen(true, true);

                        // repaint if shown
                        if (isShown()) {
                            serviceRepaints();
                        }

                        // show a user error
                        showError(display, (String) result, throwable, null);
                    }
                } break;

                case EVENT_TRACKLOG: {
                    if (throwable != null) {
                        showWarning(display, result == null? "GPX tracklog event" : (String) result, throwable, null);
                    }

                    // event from GPX tracklog always means something wrong
                    stopGpx();
                } break;
            }
        }

        // debug
        public String toString() {
            return "code " + code + ";result '" + result + "';throwable " + throwable;
        }
        // ~debug
    }
}
