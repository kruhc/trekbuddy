// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.maps.Map;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;
import cz.kruch.track.location.SimulatorLocationProvider;
import cz.kruch.track.location.Jsr179LocationProvider;
import cz.kruch.track.location.Jsr82LocationProvider;
import cz.kruch.track.util.Logger;

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
import java.io.IOException;
import java.util.Date;

import api.location.LocationProvider;
import api.location.LocationListener;
import api.location.Location;
import api.location.QualifiedCoordinates;
import api.location.LocationException;

/**
 * Application desktop.
 */
public class Desktop extends GameCanvas implements Runnable, CommandListener, LocationListener {
    // log
    private static final Logger log = new Logger("Desktop");

    // app title, for dialogs etc
    public static final String APP_TITLE = "TrekBuddy";

    // 'no map loaded' message header
    private static final String MSG_NO_MAP = "No map loaded. ";

    // dialog timeouts
    private static final int INFO_DIALOG_TIMEOUT = 750;
    private static final int WARN_DIALOG_TIMEOUT = 1500;

    // display
    private Display display;

    // desktop components
    private MapViewer mapViewer;
    private OSD osd;
    private Status status;

    // data components
    private Map map;

    // LSM/MSK commands
    private Command cmdFocus; // hope for MSK
    private Command cmdLoadMap;
    private Command cmdSettings;
    private Command cmdInfo;
    private Command cmdRun;
    // RSK commands
    private Command cmdOSD;

    // for faster movement
    private int scrolls = 0;

    // browsing or tracking
    private boolean browsing = true;

    // loading states and last-op message
    private volatile boolean initializingMap = false;
    private volatile boolean loadingSlices = false;
    private volatile String loadingResult = "No default map. Use Options->Load Map to load a map";

    // location provider
    private LocationProvider provider;

    // last known X-Y and L-L position
    private Position position = new Position(0, 0);
    private QualifiedCoordinates coordinates = new QualifiedCoordinates(0D, 0D);
    private long timestamp = 0;

    public Desktop(Display display) {
        super(false);
        this.display = display;

        // event setup
        Event.desktop = this;

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
        this.addCommand(cmdOSD);
        this.addCommand(cmdFocus);
        this.addCommand(cmdRun);
        this.addCommand(cmdLoadMap);
        this.addCommand(cmdSettings);
        this.addCommand(cmdInfo);

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

            // ensure slices are being loaded for current view
            loadingSlices = mapViewer.ensureSlices();
        }
    }

    /*
     * hack - call blocking method to show result in boot console
     */
    public void initMap() throws ConfigurationException, IOException {
        try {
            map = new Map(Config.getInstance().getMapPath());
            Throwable t = map.loadMap();
            if (t instanceof Error) {
                throw (Error)t;
            } else if (t instanceof RuntimeException) {
                throw (RuntimeException)t;
            } else if (t instanceof Exception) {
                throw new RuntimeException(t.toString());
            }
        } catch (ConfigurationException e) {
            updateLoadingResult(e);
            throw e;
        } catch (RuntimeException e) {
            updateLoadingResult(e);
            throw e;
        } catch (OutOfMemoryError e) {
            updateLoadingResult(e);
            throw e;
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
        if (log.isEnabled()) log.debug("key pressed");

        // handle event
        handleKey(i, false);
    }

    protected void keyRepeated(int i) {
        // log
        if (log.isEnabled()) log.debug("key repeated");

        // handle event
        handleKey(i, true);
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command == cmdOSD) {
            if (!isMap()) {
                showWarning(display, loadingResult, null);
            } else {
                osd.setVisible(!osd.isVisible());
                renderScreen(true, true);
            }
        } else if (command == cmdFocus) {
            if (!isMap()) {
                showWarning(display, loadingResult, null);
            } else if (isProviderRunning()) {
                browsing = false;
                focus();
            }
        } else if (command == cmdInfo) {
            (new InfoForm(display)).show();
        } else if (command == cmdSettings) {
            (new SettingsForm(display)).show();
        } else if (command == cmdLoadMap) {
            (new FileBrowser(display)).show();
        } else if (command == cmdRun) {
            if ("Start".equals(cmdRun.getLabel())) {
                startTracking();
            } else {
                stopTracking();
            }
        }
    }

    public void locationUpdated(LocationProvider provider, Location location) {
        if (log.isEnabled()) log.debug("location update: " + new Date(location.getTimestamp()) + ";" + location.getQualifiedCoordinates());

        // update timestamp
        timestamp = System.currentTimeMillis();

        // update last know L-L
        coordinates = location.getQualifiedCoordinates();

        // are we on map?
        if (!browsing && !loadingSlices) {
            if (map.isWithin(coordinates)) {
                // update OSD
                osd.setInfo(coordinates.toString(), true);

                // update position
                position = map.getCalibration().transform(coordinates);
                focus(); // includes screen update
            } else {
                // log
                if (log.isEnabled()) log.warn("position off current map");

                // update OSD
                osd.setInfo(coordinates.toString(), false);

                // update screen
                renderScreen(true, true);
            }
        }
    }

    public void providerStateChanged(LocationProvider provider, int newState) {
        if (log.isEnabled()) log.info("location provider state changed; " + newState);

        switch (newState) {
            case LocationProvider.AVAILABLE:
                showInfo(display, "Provider " + provider.getName() + " available", null);
                break;
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                showWarning(display, "Provider " + provider.getName() + " temporatily unavailable", null);
                break;
            case LocationProvider.OUT_OF_SERVICE:
                showWarning(display, "Provider " + provider.getName() + " out of service", provider.getException());
                break;
        }

        // how severe is the change
        if (newState == LocationProvider.OUT_OF_SERVICE) {
            // stop tracking completely
            stopTracking();
        } else {
            // update desktop
            osd.setProviderStatus(newState);
            renderScreen(false, true);
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

        if (action > -1) {

            // scroll if possible
            if (!loadingSlices && mapViewer.scroll(action)) {
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

                // move made, ensure map viewer has slices
                loadingSlices = mapViewer.ensureSlices();
                if (!loadingSlices) {
                    renderScreen(true, true);
                }
            }

            // repeat if not map loading
            if (!loadingSlices) {
                display.callSerially(this);
            }

        } else {
            // scrolling stop
            scrolls = 0;
        }
    }

    private void focus() {
        // move to given position
        if (mapViewer.move(position.getX(), position.getY())) {

            // move made, ensure map viewer has slices
            loadingSlices = mapViewer.ensureSlices();

            if (!loadingSlices) {
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
                    showWarning(display, loadingResult, null);
                } else {
                    browsing = true;
                    if (repeated) {
                        if (scrolls == 0) {
                            display.callSerially(Desktop.this);
                        }
                    } else {
                        if (mapViewer.scroll(action)) {
                            loadingSlices = mapViewer.ensureSlices();
                            if (!loadingSlices) {
                                renderScreen(true, true);
                            }
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
            if (initializingMap) {
                if (loadingResult != null) {
                    g.setFont(Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_SMALL));
                    g.setColor(255, 255, 255);
                    g.drawString(loadingResult, 0, 0, Graphics.TOP | Graphics.LEFT);
                }
            } else {
                if (deep) {
                    mapViewer.render(g);
                }
                if (osd != null) {
                    if (osd.isVisible()) {
                        if (browsing) {
                            osd.setInfo(map.getCalibration().transform(mapViewer.getPosition()).toString(), true);  // TODO listener
                        } else {
                            osd.setInfo(coordinates.toString(), true);
                        }
                        osd.render(g);
                    }
                }
                if (status != null) {
                    status.render(g);
                }
            }
        }

        if (flush) {
            flushGraphics();
        }
    }

    private boolean isMap() {
        return mapViewer == null ? false : true;
    }

    private boolean isProviderRunning() {
        return provider == null ? false : true;
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

    public static void showWarning(Display display, String message, Throwable t) {
        Alert alert = new Alert(APP_TITLE, message + (t == null ? "." : ". " + t.toString()), null, AlertType.WARNING);
        alert.setTimeout(WARN_DIALOG_TIMEOUT);
        display.setCurrent(alert);
    }

    public static void showError(Display display, String message, Throwable t) {
        Alert alert = new Alert(APP_TITLE, message + ". " + t.toString(), null, AlertType.ERROR);
        alert.setTimeout(Alert.FOREVER);
        display.setCurrent(alert);
    }

    public static void showError(Display display, String message, Throwable t, Displayable nextDisplayable) {
        Alert alert = new Alert(APP_TITLE, message + ". " + t.toString(), null, AlertType.ERROR);
        alert.setTimeout(Alert.FOREVER);
        if (nextDisplayable == null)
            display.setCurrent(alert);
        else
            display.setCurrent(alert, nextDisplayable);
    }

    private void updateLoadingResult(Throwable t) {
        if (t == null) {
            loadingResult = null;
        } else {
            loadingResult = MSG_NO_MAP + t.toString();
        }
    }

    private boolean startTracking() {
        // which provider?
        String selectedProvider = Config.getSafeInstance().getLocationProvider();

        // instantiat provider
        if (Config.LOCATION_PROVIDER_SIMULATOR.equals(selectedProvider)) {
            provider = new SimulatorLocationProvider(Config.getSafeInstance().getSimulatorPath(),
                                                     Config.getSafeInstance().getSimulatorDelay());
        } else if (Config.LOCATION_PROVIDER_JSR179.equals(selectedProvider)) {
            provider = new Jsr179LocationProvider();
        } else if (Config.LOCATION_PROVIDER_JSR82.equals(selectedProvider)) {
            provider = new Jsr82LocationProvider(display);
        }

        // register as listener
        provider.setLocationListener(this, -1, -1, -1);

        // start provider
        try {
            provider.start();
        } catch (LocationException e) {
            showError(display, "Failed to start provider " + provider.getName(), e);

            // gc hint
            provider = null;

            return false;
        }

        // update menu
        removeCommand(cmdRun);
        cmdRun = new Command("Stop", Command.SCREEN, 2);
        addCommand(cmdRun);

        // tracking
        browsing = false;

        return true;
    }

    private boolean stopTracking() {

        // stop provider
        try {
            provider.setLocationListener(null, -1, -1, -1);
            provider.stop();
        } catch (LocationException e) {
            showError(display, "Failed to stop provider", e);
        } finally {
            provider = null;
        }

        // update desktop
        osd.setProviderStatus(LocationProvider.OUT_OF_SERVICE);
        renderScreen(false, true);

        // update menu
        removeCommand(cmdRun);
        cmdRun = new Command("Start", Command.SCREEN, 2);
        addCommand(cmdRun);

        // tracking
        browsing = true;

        return true;
    }

    private void bgTaskOpenMap(String url) {
        if (map != null) {
            map.close();
            map = null;
        }

        // release current viewer
        mapViewer = null;

        // message for the screen
        loadingResult = "Opening map " + url;

        // emulate console...
        Graphics g = getGraphics();
        g.setColor(0, 0, 0);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setFont(Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_SMALL));
        g.setColor(255, 255, 255);
        g.drawString(loadingResult, 0, 0, Graphics.TOP | Graphics.LEFT);

        // open map (in background)
        map = new Map(url);
        initializingMap = map.prepare();
    }

    /**
     * For external events.
     */
    public static class Event implements Runnable {
        public static final int EVENT_CONFIGURATION_CHANGED         = 0;
        public static final int EVENT_FILE_BROWSER_FINISHED         = 1;
        public static final int EVENT_LOADING_STATUS_CHANGED        = 2;
        public static final int EVENT_SLICES_LOADED                 = 3;
        public static final int EVENT_MAP_OPENED                    = 4;

        private static Desktop desktop; // only if the class is static!

        private int code;
        private Object result;
        private Throwable throwable;

        public Event(int code, Object result, Throwable t) {
            this.code = code;
            this.result = result;
            this.throwable = t;
        }

        public void fire() {
            if (log.isEnabled()) log.debug("firing event " + this.toString());
            desktop.display.callSerially(Desktop.Event.this);
        }

        public static Display getDisplay() {
            return desktop.display;
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
                        desktop.osd.setVisible(false);

                        // background task
                        desktop.bgTaskOpenMap((String) result);
                    }
                } break;

                case EVENT_LOADING_STATUS_CHANGED: {
                    // update status
                    desktop.status.setInfo((String) result, true);

                    // render if shown
                    if (desktop.isShown()) {

                        // do not deep render until loading is not finished
                        desktop.renderScreen(false, true);
                        desktop.serviceRepaints();
                    }
                } break;

                case EVENT_SLICES_LOADED: {
                    // temp var
                    boolean yesno = desktop.initializingMap;

                    // clear flags and save result
                    desktop.loadingSlices = false;
                    desktop.initializingMap = false;
                    desktop.updateLoadingResult(throwable);

                    // if loaded was ok
                    if (throwable == null) {

                        // offer setting as default map
                        if (yesno) {

                            // default map
                            (new YesNoDialog(desktop.display, new YesNoDialog.AnswerListener(){
                                public void response(int answer) {
                                    if (log.isEnabled()) log.debug("yes-no? " + answer);

                                    // update cfg if requested
                                    if (answer == YesNoDialog.AnswerListener.YES) {
                                        try {
                                            Config config = Config.getInstance();
                                            config.setMapPath(desktop.map.getPath());
                                            config.update();
                                            showConfirmation(desktop.display, "Configuration updated", desktop);
                                        } catch (ConfigurationException e) {
                                            showError(desktop.display, "Failed to update configuration", e, desktop);
                                        }
                                    }
                                }
                            })).show("Use as default map", desktop.map.getPath());
                        }

                        // update screen
                        desktop.renderScreen(true, true);
                        desktop.serviceRepaints();

                        // check keys, for loading-in-move
                        if (desktop.scrolls > 0) {
                            desktop.display.callSerially(desktop);
                        }
                    } else {
                        showError(desktop.display, (String) result, throwable);
                    }
                } break;

                case EVENT_MAP_OPENED: {
                    // update loading result
/*
                    desktop.updateLoadingResult(throwable);
*/ // do not clear it yet, for the user to see what's happing

                    // show OSD (only on map reload)
                    desktop.osd.setVisible(true);

                    // if opening ok
                    if (throwable == null) {
                        try {

                            /*
                             * same as in initGui...
                             */

                            // create map viewer
                            desktop.mapViewer = new MapViewer(0, 0, desktop.getWidth(), desktop.getHeight());

                            // setup map viewer
                            desktop.mapViewer.setMap(desktop.map);

                            // ensure initial slice(s) are being loaded
                            desktop.loadingSlices = desktop.mapViewer.ensureSlices();

                        } catch (IOException e) {
                            desktop.updateLoadingResult(e);
                            showError(desktop.display, "Failed to load map", e, desktop);
                        }
                    } else {
                        // update message
                        desktop.updateLoadingResult(throwable);

                        // clear desktop
                        Graphics g = desktop.getGraphics();
                        g.setColor(0, 0, 0);
                        g.fillRect(0, 0, desktop.getWidth(), desktop.getHeight());

                        showError(desktop.display, (String) result, throwable);
                    }
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
