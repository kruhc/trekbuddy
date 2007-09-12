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

import cz.kruch.track.AssertionFailedException;
import cz.kruch.track.TrackingMIDlet;
import cz.kruch.track.event.Callback;
import cz.kruch.track.fun.Friends;
import cz.kruch.track.fun.Camera;
import cz.kruch.track.maps.Map;
import cz.kruch.track.maps.Atlas;
import cz.kruch.track.maps.io.LoaderIO;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;
import cz.kruch.track.location.GpxTracklog;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.location.Navigator;
import cz.kruch.track.util.CharArrayTokenizer;

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
import java.util.Date;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import api.location.LocationProvider;
import api.location.LocationListener;
import api.location.Location;
import api.location.QualifiedCoordinates;

/**
 * Application desktop.
 * TODO rip {@link MapView} out.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class Desktop extends GameCanvas
        implements Runnable, CommandListener, LocationListener,
//#ifdef __RIM__
                   net.rim.device.api.system.TrackwheelListener,
//#endif
                   /* Map.StateListener, Atlas.StateListener, */
                   YesNoDialog.AnswerListener, Navigator {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Desktop");
//#endif

    // messages
    private static final String MSG_NO_DEFAULT_MAP = "No startup map. Use Options->Load Map/Atlas. ";
    private static final String MSG_PAUSED = "Paused";

    // delta units
    public static final char[] DIST_STR_M      = { ' ', 'm', ' ' };
    public static final char[] DIST_STR_KM     = { ' ', 'k', 'm', ' ' };
    public static final char[] DIST_STR_NMI    = { ' ', 'M', ' ' };

    // dialog timeouts
    private static final int INFO_DIALOG_TIMEOUT    = 1000;
    private static final int ALARM_DIALOG_TIMEOUT   = 3000;
    private static final int WARN_DIALOG_TIMEOUT    = 2000;

    // views
    private static final int VIEW_MAP       = 0;
    private static final int VIEW_HPS       = 1;
    private static final int VIEW_CMS       = 2;

    // desktop screen and display
    public static Displayable screen;
    public static Display display;
    public static Font font, fontWpt;

    // behaviour flags
    public static int fullScreenHeight = -1;
    public static boolean partialFlush = true;
    public static boolean hasRepeatEvents;

    // application
    private MIDlet midlet;

    // desktop mode/screen
    private boolean boot = true;

    // common desktop components
    static Image bar, barWpt, barScale;
    static OSD osd;
    private Status status; // TODO map viewer specific?

    // desktop dimensions
    public static int width, height;

    // desktop views
    private View[] views;

    // screen modes
    private int mode;

    // desktop renderer
//    private Renderer renderer;
    private Graphics graphics;

    // data components
    private Map map;
    private Atlas atlas;

    // navigator components
    private Location location;

    // groupware components
    private Friends friends;

    // LSM/MSK commands
    private Command cmdRun, cmdRunLast, cmdStop;
    private Command cmdWaypoints;
    private Command cmdLoadMap, cmdLoadAtlas;
    private Command cmdSettings;
    private Command cmdInfo;
    private Command cmdExit;
    // RSK commands
    private Command cmdPause, cmdContinue;

    // for faster movement
    private volatile int scrolls;

    // browsing or tracking
    static volatile boolean browsing = true;
    private volatile boolean paused;
    private volatile boolean navigating;
    private volatile boolean keylock;

    // loading states and last-op message
    private /*volatile */ boolean initializingMap = true;
    private /*volatile */ boolean loadingSlices;
    private final Object[] loadingResult = {
        MSG_NO_DEFAULT_MAP, null 
    };

    // location provider and its last-op throwable and status
    private volatile LocationProvider provider;
    private volatile Object providerStatus;
    private volatile Throwable providerError;
    private volatile boolean stopRequest;
    private volatile boolean providerRestart;

    // all-purpose timer
    public static Timer timer;

    // logs
    private boolean tracklog;
    private GpxTracklog gpxTracklog;

    // navigation // TODO move to Waypoints??? access modifiers???
    /*public*/ static volatile Vector wpts;
    /*public*/ static volatile int wptIdx, wptEndIdx;
    /*public*/ static volatile int routeDir;
    private volatile float wptDistance, wptHeightDiff;
    private volatile int wptAzimuth;
    private volatile int wptsId;

    // repeated event simulation for dumb devices
    private volatile TimerTask repeatedKeyCheck;
    private /*volatile*/ int inKey;

    // start/initialization
    private volatile boolean guiReady;
    private volatile boolean postInit;

    // eventing
    private final SmartRunnable eventing;

    /**
     * Desktop constructor.
     * 
     * @param midlet midlet instance
     */
    public Desktop(MIDlet midlet) {
        super(false);

        // init static members
        screen = this;
        display = Display.getDisplay(midlet);
        timer = new Timer();
        hasRepeatEvents = hasRepeatEvents();

        // init basic members
        this.midlet = midlet;
        this.eventing = SmartRunnable.getInstance();

        // TODO move to Waypoints???
        Desktop.wptIdx = Desktop.wptEndIdx = -1;
        this.wptAzimuth = -1;
        this.wptDistance = -1F;
        this.wptHeightDiff = Float.NaN;
    }

    public void boot(boolean imagesLoaded, boolean configLoaded,
                     int customized, int localized) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("boot");
//#endif

        // get graphics
        Graphics g = graphics = getGraphics();
        g.setFont(Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_SMALL));

        // prepare console
        consoleInit(g);

        // boot sequence
        consoleShow(g, "TrekBuddy \u00a9 2007 KrUcH");
        consoleShow(g, "www.trekbuddy.net");
        consoleShow(g, "");
        consoleShow(g, "caching images...");
        if (imagesLoaded) {
            consoleResult(g, 0);
        } else {
            consoleResult(g, -1);
        }
        consoleShow(g, "loading config...");
        if (configLoaded) {
            consoleResult(g, 0);
        } else {
            consoleResult(g, -1);
        }
        consoleShow(g, "customizing ui...");
        switch (customized) {
            case -1:
                consoleResult(g, -1);
            break;
            case 0:
                consoleResult(g, 1);
            break;
            default:
                consoleResult(g, 0);
        }
        consoleShow(g, "localizing...");
        switch (localized) {
            case -1:
                consoleResult(g, -1);
            break;
            case 0:
                consoleResult(g, 1);
            break;
            default:
                consoleResult(g, 0);
        }
        consoleShow(g, "creating desktop...");
        try {
            configure();
            consoleResult(g, 0);
        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
            consoleResult(g, -1);
        }
        consoleShow(g, "loading map...");
        try {
            if ("".equals(Config.mapPath)) {
                initDefaultMap();
                consoleResult(g, 1);
            } else {
                initMap();
                consoleResult(g, 0);
            }
        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
            _updateLoadingResult("Map init", t);
            consoleResult(g, -1);
        }
        consoleDelay();

        // booting finished
        boot = false;

        // create default desktop components
        resetGui();
    }

    private void configure() {
        final int positiveCmdType = TrackingMIDlet.wm ? Command.ITEM : Command.SCREEN;

        // create and add commands to the screen
        if (Config.fullscreen && cz.kruch.track.TrackingMIDlet.sxg75) {
            this.addCommand(new Command("", Command.SCREEN, 0));
        }
        if (Config.btDeviceName.length() > 0) {
            this.addCommand(this.cmdRunLast = new Command(TrackingMIDlet.resolve(TrackingMIDlet.MENU_START) + " " + Config.btDeviceName, positiveCmdType, 1));
            this.addCommand(this.cmdRun = new Command(TrackingMIDlet.resolve(TrackingMIDlet.MENU_START), positiveCmdType, 2));
        } else {
            this.addCommand(this.cmdRun = new Command(TrackingMIDlet.resolve(TrackingMIDlet.MENU_START), positiveCmdType, 1));
        }
        if (cz.kruch.track.TrackingMIDlet.getPlatform().startsWith("NokiaE61")) {
            this.addCommand(this.cmdWaypoints = new Command("Waypoints", positiveCmdType, 3));
        }
        if (cz.kruch.track.TrackingMIDlet.isFs()) {
            this.addCommand(this.cmdLoadMap = new Command(TrackingMIDlet.resolve(TrackingMIDlet.MENU_LOADMAP), positiveCmdType, 4));
            this.addCommand(this.cmdLoadAtlas = new Command(TrackingMIDlet.resolve(TrackingMIDlet.MENU_LOADATLAS), positiveCmdType, 5));
        }
        this.addCommand(this.cmdSettings = new Command(TrackingMIDlet.resolve(TrackingMIDlet.MENU_SETTINGS), positiveCmdType, 6));
        this.addCommand(this.cmdInfo = new Command(TrackingMIDlet.resolve(TrackingMIDlet.MENU_INFO), positiveCmdType, 7));
        this.addCommand(this.cmdExit = new Command(TrackingMIDlet.resolve(TrackingMIDlet.MENU_EXIT), positiveCmdType/*EXIT*/, 8/*1*/));
        this.cmdPause = new Command(TrackingMIDlet.resolve(TrackingMIDlet.MENU_PAUSE), Config.fullscreen || cz.kruch.track.TrackingMIDlet.sonyEricsson ? positiveCmdType : Command.STOP, 1);
        this.cmdContinue = new Command(TrackingMIDlet.resolve(TrackingMIDlet.MENU_CONTINUE), Config.fullscreen || cz.kruch.track.TrackingMIDlet.sonyEricsson ? positiveCmdType : (Command.STOP), 1);
        this.cmdStop = new Command(TrackingMIDlet.resolve(TrackingMIDlet.MENU_STOP), Config.fullscreen || cz.kruch.track.TrackingMIDlet.sonyEricsson ? positiveCmdType : (Command.STOP), 2);

        // handle commands
        this.setCommandListener(this);
//#ifdef __RIM__
        if (TrackingMIDlet.rim) {
            _rimDesktopScreen = net.rim.device.api.ui.UiApplication.getUiApplication().getActiveScreen();
            net.rim.device.api.ui.UiApplication.getUiApplication().addTrackwheelListener(this);
        }
//#endif
        
        // start I/O loader
        LoaderIO.getInstance();

        // start renderer
//        this.renderer = new Renderer();
//        this.renderer.setPriority(Thread.MAX_PRIORITY);
//        this.renderer.start();
    }

    public int getHeight() {
        if (fullScreenHeight > -1) {
            return fullScreenHeight;
        }

        return super.getHeight();
    }

    public static void resetFont() {
        font = null; // gc hint
        font = Font.getFont(Font.FACE_MONOSPACE,
                            Config.osdBoldFont ? Font.STYLE_BOLD : Font.STYLE_PLAIN,
                            Config.osdMediumFont ? Font.SIZE_MEDIUM : Font.SIZE_SMALL);
        fontWpt = null; // gc hint
        fontWpt = Font.getFont(Font.FACE_SYSTEM,
                               Config.osdBoldFont ? Font.STYLE_BOLD : Font.STYLE_PLAIN,
                               Font.SIZE_SMALL);
    }

    public static void resetBar() {
        // OSD/status bar
        int color = display.numAlphaLevels() > 2 ? (cz.kruch.track.TrackingMIDlet.sonyEricsson ? 0xA03f3f3f : 0x807f7f7f) : 0xff7f7f7f;
        int h = font.getHeight();
        int w = screen.getWidth();
        int[] shadow = new int[w * h];
        for (int i = shadow.length; --i >= 0; ) {
            shadow[i] = color;
        }
        bar = null; // gc hint
        bar = Image.createRGBImage(shadow, w, h, true);
        shadow = null; // gc hint
        if (Config.forcedGc) {
            System.gc();
        }

        // wpt label bar
        color = display.numAlphaLevels() > 2 ? (cz.kruch.track.TrackingMIDlet.sonyEricsson ? 0xA0ffff00 : 0x80ffff00) : 0xffffff00;
        h = cz.kruch.track.TrackingMIDlet.getPlatform().startsWith("Nokia/6230i") ? font.getBaselinePosition() + 2 : font.getHeight();
        shadow = new int[w * h];
        for (int i = shadow.length; --i >= 0; ) {
            shadow[i] = color;
        }
        barWpt = null; // gc hint
        barWpt = Image.createRGBImage(shadow, w, h, true);
        shadow = null; // gc hint
        if (Config.forcedGc) {
            System.gc();
        }

        // scale bar
        color = cz.kruch.track.TrackingMIDlet.sonyEricsson ? 0x80ffffff : 0x80ffffff;
        h = font.getHeight();
        w = font.stringWidth("99999 km") + 4;
        shadow = new int[w * h];
        for (int i = shadow.length; --i >= 0; ) {
            shadow[i] = color;
        }
        barScale = null; // gc hint
        barScale = Image.createRGBImage(shadow, w, h, true);
        shadow = null; // gc hint
        if (Config.forcedGc) {
            System.gc();
        }
    }

    private synchronized void resetGui() {
        // that's it when booting
        if (boot) {
            return;
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("GUI reset");
//#endif

        int w = getWidth();
        int h = getHeight();
        boolean sizeChanged = w != width || h != height;

        if (!sizeChanged) {
            return; // no change, just quit
        }

        // remember new size
        width = w;
        height = h;

        // clear main area with black
        Graphics g = graphics;
//#ifdef __S65__
        if (cz.kruch.track.TrackingMIDlet.s65) {
            g = getGraphics();
        }
//#endif
        g.setColor(0x0);
        g.fillRect(0, 0, w, h);
        g.clipRect(0, 0, w, h);

        // create bg bar and font
        if (font == null) {
            resetFont();
        }
        if (bar == null) {
            resetBar();
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("GUI reset - step 1");
//#endif

        // create common components
        if (osd == null) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("creating OSD");
//#endif
            osd = new OSD(0, 0, w, h);
            _osd = osd.isVisible();
        } else /*if (sizeChanged)*/ {
            osd.resize(w, h);
        }
        if (status == null) {
            status = new Status(0, 0, w, h);
        } else /*if (sizeChanged)*/ {
            status.resize(w, h);
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("GUI reset - step 2");
//#endif

        /*
         * create views
         */

        // screen views list
        if (views == null) {
            views = new View[3];
            views[VIEW_MAP] = new MapView(this);
            views[VIEW_MAP].setCanvas(this);
            views[VIEW_HPS] = new LocatorView(this);
            views[VIEW_HPS].setCanvas(this);
            views[VIEW_CMS] = new ComputerView(this);
            views[VIEW_CMS].setCanvas(this);
            views[0].setVisible(true);
/*
            sizeChanged = true; // enforce sizeChanged notification
*/
        }
/*
        if (sizeChanged) {
*/
            views[VIEW_MAP].sizeChanged(w, h);
            views[VIEW_HPS].sizeChanged(w, h);
            views[VIEW_CMS].sizeChanged(w, h);
/*
        }
*/

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("GUI reset - final step");
//#endif

        // UI is ready now
        guiReady = true;

        // render screen
        update(MASK_ALL);
    }

    public void initDefaultMap() throws Throwable {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("init default map");
//#endif

        // in-jar map
        map = Map.defaultMap(this);

        // we are done
        _setInitializingMap(false);

//#ifdef __LOG__
        if (log.isEnabled()) log.info("~init default map");
//#endif
    }

    /* hack - call blocking method to show result in boot console */
    public void initMap() throws Throwable {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("init map");
//#endif

        String mapPath = Config.mapPath;
        String mapName = null;
        Atlas _atlas = null;

//#ifdef __LOG__
        if (log.isEnabled()) log.info("startup map: " + mapPath);
//#endif

        // load atlas first
        if (mapPath.indexOf('?') > -1) {

            // get atlas index path
            CharArrayTokenizer tokenizer = new CharArrayTokenizer();
            tokenizer.init(mapPath, new char[]{ '?', '&','=' }, false);
            String token = tokenizer.next().toString();

            // load atlas
            _atlas = new Atlas(token, this);
            Throwable t = _atlas.loadAtlas();
            if (t != null) {
                throw t;
            }

            // get layer and map name
            tokenizer.next(); // layer
            token = tokenizer.next().toString();
            _atlas.setLayer(token);
            tokenizer.next(); // map
            mapName = tokenizer.next().toString();
            mapPath = _atlas.getMapURL(mapName);

            // cleanup
            tokenizer.dispose();
        }

        // load map now
        Map _map = new Map(mapPath, mapName, this);
        if (_atlas != null) { // calibration may already be available
            _map.setCalibration(_atlas.getMapCalibration(mapName));
        }
        Throwable t = _map.loadMap();
        if (t != null) {
            throw t;
        }

        // use these
        map = _map;
        atlas = _atlas;
        if (atlas != null && map != null) { // pre-cache initial map
            atlas.getMaps().put(map.getPath(), map);
        }

        // we are done
        _setInitializingMap(false);

//#ifdef __LOG__
        if (log.isEnabled()) log.info("~init map");
//#endif
    }

    private void postInit() {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("post init");
//#endif

        // start Friends
        if (cz.kruch.track.TrackingMIDlet.jsr120 && Config.locationSharing) {
//#ifdef __LOG__
             if (log.isEnabled()) log.info("starting SMS listener");
//#endif
            try {
                friends = new Friends();
            } catch (Throwable t) {
                showError("Failed to init location sharing", t, this);
            }
        }
    }

    protected void sizeChanged(int w, int h) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("size changed: " + w + "x" + h);
//#endif

        // reset GUI // TODO check for dimensions change EDIT done in resetGui TODO move here
        resetGui();

//#ifdef __LOG__
        if (log.isEnabled()) log.info("~size changed");
//#endif
    }

    protected void pointerPressed(int x, int y) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("pointerPressed");
//#endif

        int key = 0;
        boolean repeated = false;

        int j = x / (getWidth() / 3);
        int i = y / (getHeight() / 10);

        switch (i) {
            case 0:
            case 1:
            case 2: {
                switch (j) {
                    case 0:
                        key = Canvas.KEY_NUM1;
                    break;
                    case 1:
                        _setInKey(key = getKeyCode(Canvas.UP));
                        repeated = true;
                    break;
                    case 2:
                        key = Canvas.KEY_NUM3;
                    break;
                }
            } break;
            case 3:
            case 4:
            case 5: {
                switch (j) {
                    case 0:
                        _setInKey(key = getKeyCode(Canvas.LEFT));
                        repeated = true;
                    break;
                    case 1:
                        key = getKeyCode(Canvas.FIRE);
                    break;
                    case 2:
                        _setInKey(key = getKeyCode(Canvas.RIGHT));
                        repeated = true;
                    break;
                }
            } break;
            case 6:
            case 7:
            case 8: {
                switch (j) {
                    case 0:
                        key = Canvas.KEY_NUM7;
                    break;
                    case 1:
                        _setInKey(key = getKeyCode(Canvas.DOWN));
                        repeated = true;
                    break;
                    case 2:
                        key = Canvas.KEY_NUM9;
                    break;
                }
            } break;
            case 9: {
                switch (j) {
                    case 0:
                        key = Canvas.KEY_STAR;
                    break;
                    case 1:
                        key = Canvas.KEY_NUM0;
                    break;
                    case 2:
                        key = Canvas.KEY_POUND;
                    break;
                }
            } break;
        }

        if (key != 0) {
            handleKey(key, repeated);
        }
    }

    protected void pointerReleased(int x, int y) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("pointerPressed");
//#endif

        keyReleased(0); // TODO unknown key?
    }

    protected void keyPressed(int i) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("keyPressed");
//#endif

        if (i == Canvas.KEY_NUM1) {
            return;
        }

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

        if (i == Canvas.KEY_NUM1) {
            handleKey(i, false);
        }

        // no key pressed anymore
        _setInKey(0);

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

        // notify device control
        cz.kruch.track.ui.nokia.DeviceControl.keyReleased();
    }

//#ifdef __RIM__

    private net.rim.device.api.ui.Screen _rimDesktopScreen;

    public boolean trackwheelClick(int status, int time) {
        return false;
    }

    public boolean trackwheelUnclick(int status, int time) {
        return false;
    }

    public boolean trackwheelRoll(int amount, int status, int time) {
        if (net.rim.device.api.ui.UiApplication.getUiApplication().getActiveScreen() == _rimDesktopScreen) {
            int key = 0;
            boolean alt = (status & net.rim.device.api.system.KeypadListener.STATUS_ALT) != 0;
            if (amount < 0) {
                key = alt ? Canvas.KEY_NUM4 : Canvas.KEY_NUM2;
            } else if (amount > 0) {
                key = alt ? Canvas.KEY_NUM6 : Canvas.KEY_NUM8;
            }
            int now = _getInKey();
            _setInKey(key);
            if (now == 0 || now == key) {
/*
                keyPressed(key);
*/
                eventing.callSerially(this);
            } else {
                _setInKey(0);
            }

            return true;
        }

        return false;
    }

//#endif

    public void commandAction(Command command, Displayable displayable) {
        if (command == cmdInfo) {
            (new InfoForm()).show(this, isTracking() ? provider.getThrowable() : providerError,
                                  isTracking() ? provider.getStatus() : providerStatus, map);
        } else if (command == cmdSettings) {
            (new SettingsForm(new Event(Event.EVENT_CONFIGURATION_CHANGED))).show();
        } else if (command == cmdWaypoints) {
            Waypoints.getInstance().show();
        } else if (command == cmdLoadMap) {
            (new FileBrowser("SelectMap", new Event(Event.EVENT_FILE_BROWSER_FINISHED, "map"), this)).show();
        } else if (command == cmdLoadAtlas) {
            (new FileBrowser("SelectAtlas", new Event(Event.EVENT_FILE_BROWSER_FINISHED, "atlas"), this)).show();
        } else if (command == cmdRun) {
            // start tracking
            stopRequest = providerRestart = false;
            preTracking(false);
            // update OSD
            update(MASK_OSD);
        } else if (command == cmdStop) {
            // stop tracking
            stopRequest = true;
            stopTracking(false);
            // update OSD
            update(MASK_OSD);
        } else if (command == cmdRunLast) {
            // start tracking with known device
            stopRequest = providerRestart = false;
            preTracking(true);
            // update OSD
            update(MASK_OSD);
        } else if (command == cmdExit) {
            (new YesNoDialog(this, this)).show("Do you want to quit?", null);
        } else if (command == cmdPause) {
            // update flag
            paused = true;
            // update menu
            removeCommand(cmdPause);
            addCommand(cmdContinue);
            // update screen
            update(MASK_SCREEN);
        } else if (command == cmdContinue) {
            // update flag
            paused = false;
            // update menu
            removeCommand(cmdContinue);
            addCommand(cmdPause);
            // update screen
            update(MASK_SCREEN);
        }
    }

    public void response(int answer) {
        if (answer == YesNoDialog.YES) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("exit command");
//#endif

            // stop eventing
            eventing.destroy();

            // stop tracking (closes GPS connection, closes tracklog)
            try {
                stopTracking(true);
            } catch (Throwable t) {
                // ignore
            }

            // stop waypoints
            try {
                Waypoints.getInstance().shutdown();
            } catch (Throwable t) {
                // ignore
            }

            // stop renderer
//            renderer.destroy();

            // stop device control
            cz.kruch.track.ui.nokia.DeviceControl.destroy();

            // stop timer
            if (timer != null) {
                timer.cancel();
            }

            // close atlas/map
            if (atlas != null) {
                atlas.close();
            }
            if (map != null) {
                map.close();
            }

            // close views
            for (int i = views.length; --i >= 0; ) {
                views[i].close();
            }

            // stop I/O loader
            LoaderIO.getInstance().destroy();

            // stop Friends
            if (friends != null) {
                friends.destroy();
            }

            // bail out
            midlet.notifyDestroyed();
        }
    }
    
    public void locationUpdated(LocationProvider provider, Location location) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("location update: " + new Date(location.getTimestamp()) + ";" + location.getQualifiedCoordinates());
//#endif

        eventing.callSerially(newEvent(Event.EVENT_TRACKING_POSITION_UPDATED,
                              location, null, provider));
    }

    public void providerStateChanged(LocationProvider provider, int newState) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("location provider state changed; " + newState);
//#endif

        eventing.callSerially(newEvent(Event.EVENT_TRACKING_STATUS_CHANGED,
                              new Integer(newState), null, provider));
    }

    public void tracklogStateChanged(LocationProvider provider, boolean isRecording) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("tracklog state changed; " + isRecording);
//#endif

        eventing.callSerially(newEvent(Event.EVENT_TRACKLOG,
                              new Integer(isRecording ? GpxTracklog.CODE_RECORDING_START : GpxTracklog.CODE_RECORDING_STOP),
                              null, provider));
    }

    //
    // Navigator contract
    //

    public boolean isTracking() {
        return provider != null;
    }

    public boolean isLocation() {
        return location != null;
    }

    public void updateNavigation(QualifiedCoordinates from) {
        // got active wpt?
        if (wpts != null) {

            // wpt location
            Waypoint wpt = ((Waypoint) wpts.elementAt(wptIdx));
            QualifiedCoordinates qc = wpt.getQualifiedCoordinates();

            // calculate distance, azimuth and height diff
            wptDistance = from.distance(qc);
            wptAzimuth = (int) from.azimuthTo(qc, wptDistance);
            if (qc.getAlt() != Float.NaN && from.getAlt() != Float.NaN) {
                wptHeightDiff = qc.getAlt() - from.getAlt();
            } else {
                wptHeightDiff = Float.NaN;
            }
        }
    }

    public void updateRouting(QualifiedCoordinates from) {
        // route navigation?
        if (wpts != null) {

            // current wpt reached?
            if (wptDistance > -1F && wptDistance <= Config.wptProximity) {

                // mark wpt as 'reached' when routing // TODO ugly code
                if (((MapView)views[VIEW_MAP]).route != null) {
                    ((MapView)views[VIEW_MAP]).mapViewer.setPoiStatus(wptIdx, MapViewer.WPT_STATUS_REACHED);
                }

                // find next wpt
                boolean changed = false;
                switch (routeDir) {
                    case -1: {
                        if (wptIdx > 0) {
                            wptIdx--;
                            changed = true;
                        } break;
                    }
                    case 1: {
                        if (wptIdx < wpts.size() - 1) {
                            wptIdx++;
                            changed = true;
                        }
                    }
                }

                // notify views
                if (changed) {

                    // flash screen
                    cz.kruch.track.ui.nokia.DeviceControl.flash();

                    // play sound and vibrate
                    if (Camera.play("wpt.amr")) {
                        display.vibrate(1000);
                    } else { // fallback to system alarm
                        AlertType.ALARM.playSound(display);
                    } 

                    // update navinfo
                    updateNavigation(from);

                    // notify views
                    for (int i = views.length; --i >= 0; ) {
                        views[i].navigationChanged(wpts, wptIdx, true);
                    }
                }
            }
        }
    }

    /**
     * Gets last known position from GPS (WGS-84).
     * Called by {@link Waypoints} only.
     * @return last known position from GPS
     */
    public Location getLocation() {
        return views[VIEW_MAP].getLocation();
    }

    /**
     * Gets current pointer coordinates (WGS-84).
     * Called by {@link Waypoints} only.
     * @return current pointer coordinates
     */
    public QualifiedCoordinates getPointer() {
        return views[VIEW_MAP].getPointer();
    }

    public void saveLocation(Location l) {
        if (gpxTracklog != null) {
            gpxTracklog.insert(l);
        }
    }

    public long getTracklogTime() {
        if (gpxTracklog == null) {
            return System.currentTimeMillis();
        }

        return gpxTracklog.getTime();
    }

    public String getTracklogCreator() {
        return cz.kruch.track.TrackingMIDlet.APP_TITLE + " " + midlet.getAppProperty("MIDlet-Version");
    }

    /**
     * @deprecated should be?
     * @return current waypoint
     */
    public Waypoint getNavigateTo() {
        return wpts == null ? null : ((Waypoint) wpts.elementAt(wptIdx));
    }

    public void setNavigateTo(Vector wpts, int fromIndex, int toIndex) {
        // gc hint
        /*this.*/Desktop.wpts = null;

        // 'route changed' flag
        boolean rchange = false;

        // start navigation?
        if (wpts != null) {
            // update state vars
            navigating = true;
            /*this.*/Desktop.wpts = wpts;
            if (toIndex < 0) { // forward routing
                /*this.*/Desktop.wptIdx = fromIndex;
                /*this.*/Desktop.wptEndIdx = toIndex;
                /*this.*/Desktop.routeDir = 1;
            } else if (fromIndex < 0) { // backward routing
                /*this.*/Desktop.wptIdx = toIndex;
                /*this.*/Desktop.wptEndIdx = fromIndex;
                /*this.*/Desktop.routeDir = -1;
            } else { // single wpt navigation
                /*this.*/Desktop.wptIdx = toIndex;
                /*this.*/Desktop.wptEndIdx = fromIndex;
                /*this.*/Desktop.routeDir = 0;
            }
            // update navinfo
            if (isTracking() && isLocation()) {
                updateNavigation(getLocation().getQualifiedCoordinates());
            } else {
                updateNavigation(getPointer());
            }
            // is this route navigation
            if (routeDir != 0) {
                if (wpts.hashCode() != wptsId) {
                    // remember new route hash
                    wptsId = wpts.hashCode();
                    // set flag
                    rchange = true;
                }
            }
        } else { /* no, navigation stoppped */
            // clear navigation info
            navigating = false;
            wptsId = 0;
            wptAzimuth = -1;
            wptDistance = -1F;
            wptHeightDiff = Float.NaN;
            // set 'route changed' flag
            rchange = true;
        }

        int mask = MASK_OSD;

        // notify views
        for (int i = views.length; --i >= 0; ) {
            if (rchange) {
                views[i].routeChanged(wpts);
            }
            mask |= views[i].navigationChanged(wpts, wptIdx, false);
        }

        // update screen
        update(mask);
    }

    public int getWptAzimuth() {
        return wptAzimuth;
    }

    public float getWptDistance() {
        return wptDistance;
    }

    public Map getMap() {
        return map;
    }

    //
    // ~Navigator
    //

    /** Used for key repetition emulation */
    public void run() {
        int keyState = getKeyStates();
        int key = 0;

        if ((keyState & LEFT_PRESSED) != 0) {
            key = Canvas.KEY_NUM4;
        } else if ((keyState & RIGHT_PRESSED) != 0) {
            key = Canvas.KEY_NUM6;
        } else if ((keyState & UP_PRESSED) != 0) {
            key = Canvas.KEY_NUM2;
        } else if ((keyState & DOWN_PRESSED) != 0) {
            key = Canvas.KEY_NUM8;
        }

        // dumb device without getKeyStates() support?
        if (key == 0) {
            key = _getInKey();
        }

        // action
        if (key != 0) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("repeated key " + key);
//#endif
            // notify
            keyRepeated(key);

        } else {
            // notify
            keyReleased(0); // TODO unknown key?
        }
    }

    private int _getInKey() {
        synchronized (this) {
            return inKey;
        }
    }

    private void _setInKey(final int i) {
        synchronized (this) {
            inKey = i;
        }
    }

    private boolean _getLoadingSlices() {
        synchronized (this) {
            return loadingSlices;
        }
    }

    private void _setLoadingSlices(final boolean b) {
        synchronized (this) {
            loadingSlices = b;
        }
    }

    private boolean _getInitializingMap() {
        synchronized (this) {
            return initializingMap;
        }
    }

    private void _setInitializingMap(final boolean b) {
        synchronized (this) {
            initializingMap = b;
        }
    }

    private void handleKey(int i, final boolean repeated) {
        if (paused || !postInit) {
            return;
        }

        if (Canvas.KEY_STAR == i) {
            if (repeated) {
                keylock = !keylock;
                showInfo(keylock ? "Keys locked." : "Keys unlocked.", null);
            }
        }

        if (keylock) {
            return;
        }

        int mask = MASK_NONE;
        int action = getGameAction(i);

        switch (action) {
            
            case Canvas.UP:
            case Canvas.LEFT:
            case Canvas.RIGHT:
            case Canvas.DOWN: {

                // remember key - some devices do not support getKeyStates()
                if (!repeated) { _setInKey(i); }

                // handle action
                mask = views[mode].handleAction(action, repeated);

                // dumb phones check for repetition
                if (!repeated && !hasRepeatEvents) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("does not have repeat events");
//#endif

                    // schedule delayed check to emulate key repeated event
                    if (repeatedKeyCheck == null) {
                        repeatedKeyCheck = new KeyCheckTimerTask();
                        timer.schedule(repeatedKeyCheck, 1500L);
                    }
                }

            } break;

            case Canvas.FIRE: {

                // handle action (repeated is ignored)
                if (!repeated) {
                    mask = views[mode].handleAction(action, repeated);
                }

            } break;

            default: { // no game action

                switch (i) {
                    
                    case Canvas.KEY_POUND: { // change screen
                        if (!repeated) {
                            views[mode++].setVisible(false);
                            if (mode >= views.length) {
                                mode = 0;
                            }
                            views[mode].setVisible(true);
                            mask = MASK_ALL;
                        }
                    } break;

                    case Canvas.KEY_NUM0: { // day/night switch
                        if (!repeated) {
                            if (mode == VIEW_MAP) {
                                mask |= views[VIEW_MAP].handleKey(i, false);
                            } else {
                                Config.dayNight++;
                                if (Config.dayNight == 2) {
                                    Config.dayNight = 0;
                                }
                                for (int j = views.length; --j >= 0; ) {
                                    mask |= views[j].changeDayNight(Config.dayNight);
                                }
                            }
                        }
                    } break;

                    case Canvas.KEY_NUM1: { // navigation
                        if (!repeated) {
                            Waypoints.getInstance().show();
                        } else {
                            Waypoints.getInstance().showCurrent();
                        }
                    } break;

                    case Canvas.KEY_NUM3: { // backlight control
                        if (!repeated) {
                            cz.kruch.track.ui.nokia.DeviceControl.setBacklight();
                        }
                    } break;

                    default: {
                        if (!repeated) {
                            mask = views[mode].handleKey(i, repeated);
                        }
                    }
                }
            }
        }

        // update necessary?
        if (mask > 0) {
            update(mask);
        }
    }

    private void update(int mask) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("update " + Integer.toBinaryString(mask));
//#endif

        // prepare slices
        if ((mask & MASK_MAP) != 0) {
            ((MapView) views[VIEW_MAP]).ensureSlices();
        }

        // enqueu render request
//        renderer.enqueue(mask);
        eventing.callSerially(newRenderTask(mask));
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
            if (message.length() > 0) message += ": ";
            message += t.toString();
        }
        showAlert(AlertType.ERROR, message, Alert.FOREVER, nextDisplayable);
    }

    private static void showAlert(AlertType type, String message, int timeout,
                                  Displayable nextDisplayable) {
        Alert alert = new Alert(cz.kruch.track.TrackingMIDlet.APP_TITLE,
                                message, null, type);
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

        // by default
        tracklog = false;

        // start tracklog?
        String on = Config.tracklog;
        if (Config.TRACKLOG_NEVER.equals(on)) {
            if (last) startTrackingLast(); else startTracking();
        } else if (Config.TRACKLOG_ASK.equals(on)) {
            (new YesNoDialog(display.getCurrent(), new YesNoDialog.AnswerListener() {
                public void response(int answer) {
                    if (YesNoDialog.YES == answer) {
                        tracklog = true; // !
                    }
                    if (last) startTrackingLast(); else startTracking();
                }
            })).show("Start tracklog?", null);
        } else if (Config.TRACKLOG_ALWAYS.equals(on)) {
            tracklog = true; // !
            if (last) startTrackingLast(); else startTracking();
        }
    }

    private boolean startTracking() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("start tracking...");
//#endif

        // which provider?
        String selectedProvider = Config.locationProvider;
        Class providerClass = null;

        // instantiate provider
        try {
            if (Config.LOCATION_PROVIDER_JSR179.equals(selectedProvider)) {
                providerClass = Class.forName("cz.kruch.track.location.Jsr179LocationProvider");
            } else if (Config.LOCATION_PROVIDER_JSR82.equals(selectedProvider)) {
                providerClass = Class.forName("cz.kruch.track.location.Jsr82LocationProvider");
            } else if (Config.LOCATION_PROVIDER_SERIAL.equals(selectedProvider)) {
                providerClass = Class.forName("cz.kruch.track.location.SerialLocationProvider");
            } else if (Config.LOCATION_PROVIDER_SIMULATOR.equals(selectedProvider)) {
                providerClass = Class.forName("cz.kruch.track.location.SimulatorLocationProvider");
            } 
//#ifdef __A1000__
              else if (Config.LOCATION_PROVIDER_MOTOROLA.equals(selectedProvider)) {
                providerClass = Class.forName("cz.kruch.track.location.MotorolaLocationProvider");
            }
//#endif
            provider = (LocationProvider) providerClass.newInstance();
        } catch (Throwable t) {
            showError("Could not create provider [" + selectedProvider + "] (" + providerClass + ")", t, this);

            return false;
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
            showError("Failed to start provider [" + provider.getName() + "]", t, null);

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
        addCommand(cmdStop);
        addCommand(cmdPause);

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("~start tracking");
//#endif

        return true;
    }

    private boolean startTrackingLast() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("start tracking using known device " + Config.btServiceUrl);
//#endif

        // instantiate BT provider
        try {
            Class providerClass = Class.forName("cz.kruch.track.location.Jsr82LocationProvider");
            provider = (LocationProvider) providerClass.newInstance();
        } catch (Throwable t) {
            showError("Could not create provider [Bluetooth]", t, this);
            return false;
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
        addCommand(cmdStop);
        addCommand(cmdPause);

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
        providerError = provider.getThrowable();

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
        paused = false;

        // update OSD & navigation UI
        osd.setProviderStatus(LocationProvider.OUT_OF_SERVICE);
        osd.resetExtendedInfo();
        osd.setRecording(false);
        update(MASK_OSD);

        // update menu
        removeCommand(cmdStop);
        removeCommand(cmdPause);
        removeCommand(cmdContinue);
        addCommand(cmdRun);
        if (cmdRunLast != null) {
            addCommand(cmdRunLast);
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("~stop tracking");
//#endif

        return true;
    }

    private void startGpxTracklog() {
        // tracklog enabled & is GPX?
        if (provider.isTracklog() && Config.TRACKLOG_FORMAT_GPX.equals(Config.tracklogFormat)) {

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

                // start new tracklog
                gpxTracklog = new GpxTracklog(GpxTracklog.LOG_TRK,
                                              new Event(Event.EVENT_TRACKLOG),
                                              getTracklogCreator(),
                                              System.currentTimeMillis());
                gpxTracklog.setFilePrefix(null);
                gpxTracklog.start();
            }
        }
    }

    private void stopGpxTracklog() {
        if (gpxTracklog != null) {
            try {
                if (gpxTracklog.isAlive()) {
                    gpxTracklog.destroy();
                }
                gpxTracklog.join();
            } catch (InterruptedException e) {
                // ignore - should not happen
            } finally {
                gpxTracklog = null; // GC hint
            }
        }
    }

    private void drawPause(Graphics g) {
        Font f = Font.getDefaultFont();
        final int sw = f.stringWidth(MSG_PAUSED);
        final int sh = f.getHeight();
        final int w = getWidth() * 7 / 8;
        final int h = sh << 1;
        final int x = (getWidth() - w) / 2;
        final int y = (getHeight() - h);
        g.setColor(0x00D2E9FF);
        g.fillRoundRect(x, y, w, h, 5, 5);
        g.setColor(0x0);
        g.drawRoundRect(x, y, w - 1, h - 1, 5, 5);
        g.setFont(f);
        g.drawString(MSG_PAUSED, x + (w - sw) / 2, y + (h - sh) / 2, Graphics.TOP | Graphics.LEFT);
        flushGraphics();
    }

    /*
     * Map.StateListener contract
     */

    public void mapOpened(Object result, Throwable throwable) {
        eventing.callSerially(new Event(Event.EVENT_MAP_OPENED,
                                        result, throwable, null));
    }

    public void slicesLoading(Object result, Throwable throwable) {
        _setLoadingSlices(true);
    }

    public void slicesLoaded(Object result, Throwable throwable) {
        _setLoadingSlices(false);
        eventing.callSerially(new Event(Event.EVENT_SLICES_LOADED,
                                        result, throwable, null));
    }

    public void loadingChanged(Object result, Throwable throwable) {
        eventing.callSerially(new Event(Event.EVENT_LOADING_STATUS_CHANGED,
                                        result, throwable, null));
    }

    /*
    * Map.StateListener contract
    */

    public void atlasOpened(Object result, Throwable throwable) {
        eventing.callSerially(new Event(Event.EVENT_ATLAS_OPENED,
                                        result, throwable, null));
    }

    /* TODO remove
     * thread-safe helpers... hehe, 'thread-safe' :-)
     */

    private void _updateLoadingResult(String label, Throwable t) {
        if (t == null) {
            loadingResult[0] = null;
            loadingResult[1] = null;
        } else {
            loadingResult[0] = label;
            loadingResult[1] = t;
        }
    }

    private void _updateLoadingResult(String label, String value) {
        loadingResult[0] = label;
        loadingResult[1] = value;
    }

    private Object[] _getLoadingResult() {
        return loadingResult;
    }

    private String _getLoadingResultText() {
        return loadingResult[0] + " " + loadingResult[1];
    }

    /*
     * Desktop renderer.
     */

    /*
     * POOL
     */

    private static final RenderTask[] rtPool = new RenderTask[32];
    private static int rtCountFree;

    public synchronized RenderTask newRenderTask(final int m) {
        RenderTask result;

        synchronized (rtPool) {
            if (rtCountFree == 0) {
                result = new RenderTask(m);
            } else {
                result = rtPool[--rtCountFree];
                rtPool[rtCountFree] = null;
                result.mask = m;
            }
        }

        return result;
    }

    public void releaseRenderTask(RenderTask task) {
        synchronized (rtPool) {
            if (rtCountFree < rtPool.length) {
                rtPool[rtCountFree++] = task;
            }
        }
    }

    /*
     * ~POOL
     */

    public static final int MASK_NONE       = 0;
    public static final int MASK_MAP        = 1;
    public static final int MASK_OSD        = 2;
    public static final int MASK_STATUS     = 4;
    public static final int MASK_CROSSHAIR  = 8;
    public static final int MASK_SCREEN     = 256;
    public static final int MASK_ALL        = MASK_MAP | MASK_OSD | MASK_STATUS | MASK_CROSSHAIR;

    final class RenderTask implements Runnable {
//#ifdef __LOG__
        private /*static*/ final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("RenderTask");
//#endif

        private int mask;

        public RenderTask(int m) {
            this.mask = m;
        }

        public int getMask() {
            return mask;
        }

        public void merge(int m) {
            this.mask |= m;
        }
        
        public void run() {
            // render
            try {
                // get graphics
                Graphics g = graphics;
//#ifdef __S65__
                if (cz.kruch.track.TrackingMIDlet.s65) {
                    g = getGraphics();
                }
//#endif

                // render current view
                views[mode].render(g, font, mask);

                // paused?
                if (paused) {
                    drawPause(g);
                }

            } catch (Throwable t) {
//#ifdef __LOG__
                t.printStackTrace();
                if (log.isEnabled()) log.error("render failure", t);
//#endif
                Desktop.showError("_RENDER FAILURE_", t, null);

            } finally {

                // release task
                releaseRenderTask(this);
            }

            if (!postInit && guiReady) {
                postInit = true;
                postInit();
            }
        }
    }

    private final class Renderer extends Thread {
//#ifdef __LOG__
        private /*static*/ final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Renderer");
//#endif

        private boolean go;
        private int mask;

        public Renderer() {
            this.go = true;
            this.mask = 0;
        }

        public void enqueue(int m) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("merging " + Integer.toBinaryString(this.mask) + " with " + Integer.toBinaryString(m));
//#endif
            synchronized (this) {
                mask |= m;
                notify();
            }
        }

        public void run() {
            // pop mask
            for (; go ;) {
                int m;
                synchronized (this) {
                    while (go && mask == 0) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                        }
                    }
                    m = mask;
                    mask = 0;
                }

                if (!go) break;

//#ifdef __LOG__
                if (log.isEnabled()) log.error("render mask = " + Integer.toBinaryString(m));
//#endif

                // render
                try {
                    // get graphics
                    Graphics g = graphics;
//#ifdef __S65__
                    if (cz.kruch.track.TrackingMIDlet.s65) {
                        g = getGraphics();
                    }
//#endif

                    // render current view
                    views[mode].render(g, font, m);

                    // paused?
                    if (paused) {
                        drawPause(g);
                    }

                } catch (Throwable t) {
//#ifdef __LOG__
                    t.printStackTrace();
                    if (log.isEnabled()) log.error("render failure", t);
//#endif
                    Desktop.showError("_RENDER FAILURE_", t, null);
                }

                if (!postInit && guiReady) {
                    postInit = true;
                    postInit();
                }
            }
        }

        public void destroy() {
            synchronized (this) {
                go = false;
                notify();
            }
            try {
                join();
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    /**
     * TODO make this top-level class like LocatorView
     */
    private final class MapView extends View {
//#ifdef __LOG__
        private /*static*/ final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("MapView");
//#endif

        private final MapViewer mapViewer;

        private Position[] route;
        private Position waypoint;

//        private volatile long lastRepeat;
        
        public MapView(Navigator navigator) {
            super(navigator);

            // create map viewer
            this.mapViewer = new MapViewer(/*0, 0, */);
        }

        /** @deprecated hack */
        public void ensureSlices() {
            if (!_getInitializingMap() && mapViewer.hasMap()) {
                synchronized (Desktop.this/*loadingSlicesLock*/) {
                    if (!loadingSlices) {
                        loadingSlices = mapViewer.ensureSlices();
                    }
                }
            }
        }

        /** @deprecated hack */
        public void setMap(Map map) {
            mapViewer.setMap(map);
            clearRoute();
            clearWaypoint();
            if (map != null && wpts != null) {
                if (route != null) {
                    prepareRoute(wpts);
                    mapViewer.setRoute(route, true);
                }
                prepareWaypoint(wpts, wptIdx, true);
                mapViewer.setWaypoint(waypoint);
            }
        }

        /** @deprecated hack */
        public Position getPosition() {
            return mapViewer.getPosition();
        }

        /** @deprecated hack */
        public void setPosition(Position position) {
            mapViewer.setPosition(position);
        }

        /** @deprecated remove */
        private boolean isAtlas() {
            return atlas != null;
        }

        /** @deprecated remove */
        private boolean isMap() {
            return map != null;
        }

        public void close() {
            mapViewer.setMap(null); // saves crosshair position
        }

        void setVisible(boolean b) {
            super.setVisible(b);
            if (b) { /* trick */
                if (location != null) {
                    locationUpdated(location);
                }
            }
        }

        public int routeChanged(Vector wpts) {
            // release old
            clearRoute();
            this.route = null; // gc hint

            // routing starts
            if (wpts != null) {

                // allocate new array
                route = new Position[wpts.size()];
                
                // prepare route
                prepareRoute(wpts);

            }

            // set route on map
            mapViewer.setRoute(route, false);

            return super.routeChanged(wpts);
        }

        public int navigationChanged(Vector wpts, int idx, boolean silent) {
            // release old
            clearWaypoint();
            waypoint = null; // gc hint

            // navigation started?
            if (wpts != null) {

                // prepare waypoint
                prepareWaypoint(wpts, idx, silent);

                // update UI
                updateNavigationInfo();

            } else { // no, navigation stopped

                // also hide arrow and delta info when browsing
                if (browsing) {
                    mapViewer.setCourse(-1F);
                    osd.resetExtendedInfo();
                }

                // notify user
                if (isVisible) {
                    Desktop.showConfirmation("Navigation stopped", Desktop.this);
                }
            }

            // set wpt on map
            mapViewer.setWaypoint(waypoint);

            return super.navigationChanged(wpts, idx, silent);
        }

        private void clearRoute() {
            if (this.route != null) {
                Position[] route = this.route;
                for (int i = route.length; --i >= 0; ) {
                    Position.releaseInstance(route[i]);
                    route[i] = null;
                }
            }
        }

        private void prepareRoute(Vector wpts) {
            // create
            for (int N = wpts.size(), c = 0, i = 0; i < N; i++) {

                // get coordinates local to map
                Waypoint wpt = (Waypoint) wpts.elementAt(i);
                QualifiedCoordinates localQc = map.getDatum().toLocal(wpt.getQualifiedCoordinates());
                Position position = map.transform(localQc);

                // add to route or release
//                if (map.isWithin(position)) {
                    route[c++] = position.clone();
//                } else {
//                    c++;
//                }

                // release
                QualifiedCoordinates.releaseInstance(localQc);
            }
        }

        private void clearWaypoint() {
            Position.releaseInstance(this.waypoint);
        }

        private void prepareWaypoint(Vector wpts, final int idx, final boolean silent) {
            // get coordinates local to map
            Waypoint wpt = (Waypoint) wpts.elementAt(idx);
            QualifiedCoordinates localQc = map.getDatum().toLocal(wpt.getQualifiedCoordinates());

            // is wpt on current map
            if (map.isWithin(localQc)) {

                // get marker position on current map
                waypoint = map.transform(localQc).clone();

                // notify user
                if (isVisible && !silent) {
                    Desktop.showInfo("Waypoint set on map", Desktop.this);
                }

            } else { // no, warn

                // warn user
                if (isVisible && !silent) {
                    Desktop.showWarning("Selected waypoint is off current map", null, Desktop.this);
                }
            }

            // release
            QualifiedCoordinates.releaseInstance(localQc);
        }

        public int handleAction(int action, boolean repeated) {
            int mask = MASK_NONE;

            // only if map viewer is usable
            if (mapViewer.hasMap()) {

                // sync or navigate
                if (action == Canvas.FIRE) {

                    // mode flags
                    browsing = false;
                    navigating = !navigating;

                    // trick
                    if (isTracking() && isLocation()) {
                        if (location != null) {
                            mask |= locationUpdated(location);
                        }
                    }
                } else { // move left-right-up-down

                    // cursor movement breaks real-time tracking
                    browsing = true;

                    // calculate number of scrolls
                    int steps = 1;
                    if (_getLoadingSlices() || _getInitializingMap()) {
                        steps = 0;
                    } else if (repeated) {
//                        long t = System.currentTimeMillis();
//                        if (t < lastRepeat/* + 5 * Config.scrollingDelay*/) {
//                            steps = 0;
//                        } else {
                            steps = getScrolls();
//                            lastRepeat = t;
//                        }
                    }

//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("handleAction - steps to scroll = " + steps);
//#endif

                    // scroll the maps
                    boolean scrolled = false;
//                    for (int i = steps; i-- > 0; ) {
//                        scrolled = mapViewer.scroll(action) || scrolled;
//                    }
                    scrolled = mapViewer.scroll(action, steps);

                    // has map been scrolled?
                    if (scrolled) {

                        // update basic OSD
                        QualifiedCoordinates qc = map.transform(mapViewer.getPosition());
                        osd.setInfo(qc, true);

                        // update navigation info
                        QualifiedCoordinates from = map.getDatum().toWgs84(qc);
                        updateNavigation(from);

                        // release to pool
                        QualifiedCoordinates.releaseInstance(from);
                        QualifiedCoordinates.releaseInstance(qc);

                        // update extended OSD (and navigation, if any)
                        if (!updateNavigationInfo()) {
                            osd.resetExtendedInfo();
                        }

                        // update mask
                        mask = MASK_MAP | MASK_OSD;

                    } else if (steps > 0) { // no scroll? out of current map? find sibling map

                        // find sibling in atlas
                        if (isAtlas() && !_getInitializingMap() && !_getLoadingSlices()) {

                            // bounds hit?
                            char neighbour = mapViewer.boundsHit();
//#ifdef __LOG__
                            if (log.isEnabled()) log.debug("bounds hit? sibling is " + neighbour);
//#endif
                            // got sibling?
                            if (neighbour != ' ') {
                                QualifiedCoordinates newQc = null;
                                QualifiedCoordinates qc = map.transform(mapViewer.getPosition());
                                double lat = qc.getLat();
                                double lon = qc.getLon();

                                // calculate coords that lies in the sibling map
                                switch (neighbour) {
                                    case 'N':
                                        newQc = QualifiedCoordinates.newInstance(lat + 5 * map.getStep(neighbour), lon);
                                        break;
                                    case 'S':
                                        newQc = QualifiedCoordinates.newInstance(lat + 5 * map.getStep(neighbour), lon);
                                        break;
                                    case 'E':
                                        newQc = QualifiedCoordinates.newInstance(lat, lon + 5 * map.getStep(neighbour));
                                        break;
                                    case 'W':
                                        newQc = QualifiedCoordinates.newInstance(lat, lon + 5 * map.getStep(neighbour));
                                        break;
                                }

                                // switch alternate map
                                startAlternateMap(atlas.getLayer(), newQc, null);
                            }
                        }
                    }

                    // fast check again
                    if (repeated) {
                        eventing.callSerially(Desktop.this);
                    }
                }
            }

            return mask;
        }

        public int handleKey(int keycode, boolean repeated) {
            int mask = 0;

            switch (keycode) {
                case KEY_NUM0: {
                    if (mapViewer.hasMap()) {

                        // cycle crosshair
                        mask = mapViewer.nextCrosshair();
                    }
                } break;

                case KEY_NUM5: {
                    if (mapViewer.hasMap()) {

                        // mode flags
                        browsing = false;
                        navigating = !navigating;

                        // trick
                        if (isTracking() && isLocation()) {
                            if (location != null) {
                                mask |= locationUpdated(location);
                            }
                        }

                    } else if (isTracking()) {
                        showWarning(_getLoadingResultText(), null, Desktop.this);
                    }
                } break;

                case KEY_NUM7: { // atlas switch
                    if (isAtlas()) {
                        Enumeration e = atlas.getLayers();
                        if (e.hasMoreElements()) {
                            (new ItemSelection(Desktop.this, "LayerSelection",
                                               new Event(Event.EVENT_LAYER_SELECTION_FINISHED, "switch"))).show(e);
                        } else {
                            showInfo("No layers in current atlas.", Desktop.this);
                        }
                    }
                } break;

                case KEY_NUM9: { // map switch
                    if (isAtlas()) {
                        Enumeration e = atlas.getMapNames();
                        if (e.hasMoreElements()) {
                            (new ItemSelection(Desktop.this, "MapSelection",
                                               new Event(Event.EVENT_MAP_SELECTION_FINISHED, "switch"))).show(e);
                        } else {
                            showInfo("No maps in current layer.", Desktop.this);
                        }
                    }
                } break;

                case KEY_STAR: {
                    mapViewer.starTick();
                } break;
            }

            return mask;
        }

        public void sizeChanged(int w, int h) {
            // map ready but not set yet (situation after start)
            if (isMap() && !mapViewer.hasMap()) {

                // set map
                mapViewer.setMap(map);

                // update basic OSD
                QualifiedCoordinates qc = map.transform(mapViewer.getPosition());
                osd.setInfo(qc, true);
                QualifiedCoordinates.releaseInstance(qc);
            }

            // propagate further
            mapViewer.sizeChanged(w, h);
        }

        public int locationUpdated(Location l) {
            if (!isVisible) {
                return MASK_NONE;
            }

            int mask = MASK_NONE;

            // tracking?
            if (!browsing && !_getInitializingMap()) {

                // minimum UI update
                mask = MASK_OSD;

                // move on map if we get fix
                if (l.getFix() > 0) {

                    // more UI updates
                    mask |= MASK_CROSSHAIR;

                    // get wgs84 and local coordinates
                    QualifiedCoordinates qc = l.getQualifiedCoordinates();
                    QualifiedCoordinates localQc = map.getDatum().toLocal(qc);

                    // on map detection
                    boolean onMap = map.isWithin(localQc);

                    // OSD basic
                    if (Config.useGeocachingFormat || Config.useUTM) {
                        osd.setInfo(qc, onMap);
                    } else {
                        osd.setInfo(localQc, onMap);
                    }
                    osd.setSat(l.getSat());

                    // OSD extended and course arrow - navigating?
                    if (navigating && wpts != null) {

                        // get navigation info
                        StringBuffer extInfo = osd._getSb();
                        float azimuth = getNavigationInfo(extInfo);

                        // set course & navigation info
                        mapViewer.setCourse(azimuth);
                        osd.setExtendedInfo(extInfo);

                    } else { // no, tracking info

                        // set course
                        if (l.getCourse() > -1F) {
                            mapViewer.setCourse(l.getCourse());
                        }

                        // in extended info
                        osd.setExtendedInfo(l.toStringBuffer(osd._getSb()));
                    }

                    // are we on map?
                    if (onMap) {

                        // sync position
                        if (syncPosition()) {
                            mask |= MASK_MAP;
                        }

                    } else { // off current map

                        // load sibling map, if exists
                        if (isAtlas() && !_getInitializingMap() && !_getLoadingSlices()) {

                            // switch alternate map
                            startAlternateMap(atlas.getLayer(), localQc, null);
                        }
                    }

                    // release local coordinates
                    QualifiedCoordinates.releaseInstance(localQc);

                } else {

                    // if not navigating, display extended tracking info (ie. time :-) )
                    if (!navigating || wpts == null) {
                        osd.setSat(0);
                        osd.setExtendedInfo(l.toStringBuffer(osd._getSb()));
                    }

                }
            }

            return mask;
        }

        public Location getLocation() {
            return location;
        }

        public QualifiedCoordinates getPointer() {
            return map.getDatum().toWgs84(map.transform(mapViewer.getPosition()));
        }

        public void render(Graphics g, Font f, int mask) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("render");
//#endif

            // common screen params
            g.setFont(f);

            // is map(viewer) ready?
            if (!mapViewer.hasMap()) {

                // clear window
                g.setColor(0x0);
                g.fillRect(0, 0, width, height);
                g.setColor(0x00FFFFFF);

                // draw loaded target
                Object[] result = _getLoadingResult();
                if (result[0] != null) {
                    g.drawString(result[0].toString(), 0, 0, Graphics.TOP | Graphics.LEFT);
                }
                if (result[1] != null) {
                    if (result[1] instanceof Throwable) {
                        Throwable t = (Throwable) result[1];
                        g.drawString(t.getClass().toString().substring(6) + ":", 0, font.getHeight(), Graphics.TOP | Graphics.LEFT);
                        if (t.getMessage() != null) {
                            g.drawString(t.getMessage(), 0, 2 * font.getHeight(), Graphics.TOP | Graphics.LEFT);
                        }
                    } else {
                        g.drawString(result[1].toString(), 0, font.getHeight(), Graphics.TOP | Graphics.LEFT);
                    }
                }

            } else {

                // draw map
/* always redraw 'background'
                if ((mask & MASK_MAP) != 0) {
*/
                    // whole map redraw requested
                    mapViewer.render(g);
/*
                }
*/

                // draw OSD
                if ((mask & MASK_OSD) != 0) {

                    // set text color
                    g.setColor(Config.osdBlackColor ? 0x00000000 : 0x00FFFFFF);

                    // render
                    osd.render(g);
                }
            }

            // draw status
            if ((mask & MASK_STATUS) != 0) {

                // set text color
                g.setColor(Config.osdBlackColor ? 0x00000000 : 0x00FFFFFF);

                // render
                status.render(g);
            }

            // flush
            if ((mask & MASK_MAP) != 0 || (mask & MASK_SCREEN) != 0 || !partialFlush) {
                flushGraphics();
            } else {
                if ((mask & MASK_OSD) != 0) {
                    flushGraphics(osd.getClip());
                }
                if ((mask & MASK_STATUS) != 0) {
                    flushGraphics(status.getClip());
                }
                if ((mask & MASK_CROSSHAIR) != 0) {
                    flushGraphics(mapViewer.getClip());
                }
            }
        }

        private float getNavigationInfo(StringBuffer extInfo) {
            final float distance = wptDistance;
            final int azimuth = wptAzimuth;

            extInfo.append(NavigationScreens.DELTA_D).append('=');
            if (Config.unitsNautical) {
                NavigationScreens.append(extInfo, distance / 1852F, 0).append(DIST_STR_NMI);
            } else if (Config.unitsImperial) {
                NavigationScreens.append(extInfo, distance / 1609F, 0).append(DIST_STR_NMI);
            } else {
                if (distance >= 10000F) { // dist > 10 km
                    NavigationScreens.append(extInfo, distance / 1000F, 1).append(DIST_STR_KM);
                } else {
                    NavigationScreens.append(extInfo, (int) distance).append(DIST_STR_M);
                }
            }
            NavigationScreens.append(extInfo, azimuth).append(NavigationScreens.SIGN);
            if (wptHeightDiff != Float.NaN) {
                extInfo.append(' ').append(NavigationScreens.DELTA_d).append('=');
                NavigationScreens.append(extInfo, (int) wptHeightDiff);
            }

            return azimuth;
        }

        private boolean syncPosition() {
            boolean moved = false;

            if (location != null) {
                QualifiedCoordinates localQc = map.getDatum().toLocal(location.getQualifiedCoordinates());
                if (map.isWithin(localQc)) {
                    moved = mapViewer.setPosition(map.transform(localQc));
                }
                QualifiedCoordinates.releaseInstance(localQc);
            }

            return moved;
        }

        private int getScrolls() {
            int steps = 1;
            if (scrolls++ >= 15) {
                steps = 2;
                if (scrolls >= 30) {
                    steps = 3;
                }
                if (scrolls >= 40) {
                    steps = 4;
                }
            }

            return steps;
        }

        private boolean updateNavigationInfo() {
            // navigating?
            if (wpts != null) {

                // get navigation info
                StringBuffer extInfo = osd._getSb();
                float azimuth = getNavigationInfo(extInfo);

                // set course and delta
                mapViewer.setCourse(azimuth);
                osd.setExtendedInfo(extInfo);

                return true;
            }

            return false;
        }

/*
        private void flushClip(int[] clip) {
            if (clip != null) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("flush clip " + clip[0] + "-" + clip[1] + "x" + clip[2] + "-" + clip[3]);
//#endif
                flushGraphics(clip[0], clip[1], clip[2], clip[3]);
            }
        }
*/
    }

    // temps for atlas/map loading
    private volatile String _target;
    private volatile Map _map;
    private volatile Atlas _atlas;
    private volatile QualifiedCoordinates _qc; // WGS84
    private volatile boolean _switch;
    private volatile boolean _osd;

    private void startAlternateMap(String layerName, QualifiedCoordinates qc,
                                   String notFoundMsg) {
        // find map for given coords
        String mapUrl = atlas.getMapURL(layerName, qc);
        String mapName = atlas.getMapName(layerName, qc);

        // got map for given coordinates?
        if (mapUrl != null) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("loading alternate map " + mapUrl);
//#endif
            // 'switch' flag
            _switch = true;

            // focus on these coords once the new map is loaded
            _qc = map.getDatum().toWgs84(qc);

            // change atlas layer
            atlas.setLayer(layerName);

            // start loading task
            startOpenMap(mapUrl, mapName);

        } else if (notFoundMsg != null) {
            showWarning(notFoundMsg, null, Desktop.screen);
        }
    }

    private void startOpenMap(String url, String name) {
        // flag on
        _setInitializingMap(true);

        // message for the screen
        _updateLoadingResult("Loading map", url);

        // hide map viewer and OSD // TODO hackish
        ((MapView) views[VIEW_MAP]).setMap(null);

        // hide OSD
        osd.setVisible(false);

        // render screen
        update(MASK_SCREEN);

        // dispose current map
        if (map != null) {
            map.dispose();
        }

        // look for cached map first in atlas
        if (atlas != null) {
            // get from cache
            _map = (Map) atlas.getMaps().get(url);
        }

        // create new map if it does not exist yet
        if (_map == null) {
            // create new map
            _map = new Map(url, name, this);
            // try to reuse cached calibration
            if (atlas != null && name != null) {
                _map.setCalibration(atlas.getMapCalibration(name));
            }
        }

        // open the map
        _map.open();
    }

    private void startOpenAtlas(String url) {
        // flag on
        _setInitializingMap(true);

        // message for the screen
        _updateLoadingResult("Loading atlas", url);

        // hide map viewer and OSD // TODO hackish
        ((MapView) views[VIEW_MAP]).setMap(null);

        // hide OSD
        osd.setVisible(false);

        // render screen
        update(MASK_SCREEN);

        // open atlas (in background)
        _atlas = new Atlas(url, this);
        _atlas.open();
    }

    /*
     * For external events.
     */

    /*
     * POOL
     */

    private static final Event[] pool = new Event[8];
    private static int countFree;

    public Event newEvent(int code, Object result,
                          Throwable throwable, Object closure) {
        Event event;

        synchronized (pool) {
            if (countFree == 0) {
                event = new Event(code, result, throwable, closure);
            } else {
                event = pool[--countFree];
                pool[countFree] = null;
                event.code = code;
                event.result = result;
                event.throwable = throwable;
                event.closure = closure;
            }
        }

        return event;
    }

    public void releaseEvent(Event event) {
        synchronized (pool) {
            if (countFree < pool.length) {
                pool[countFree++] = event;
            }
        }
    }

    /*
     * ~POOL
     */

    private final class Event implements Runnable, Callback, YesNoDialog.AnswerListener {
//#ifdef __LOG__
        private /*static*/ final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Event");
//#endif

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

        private boolean release = true;

        private Event(int code) {
            this.code = code;
        }

        private Event(int code, Object closure) {
            this.code = code;
            this.closure = closure;
        }

        private Event(int code, Object result, Throwable throwable, Object closure) {
            this.code = code;
            this.result = result;
            this.throwable = throwable;
            this.closure = closure;
        }

        public void invoke(Object result, Throwable throwable, Object source) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("firing event " + this.toString());
//#endif

            this.result = result;
            this.throwable = throwable;
            this.release = false; // direct invocation, do not release

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
                    if (atlas == null) {
                        Config.mapPath = map.getPath();
                    } else {
                        Config.mapPath = atlas.getURL(map.getName());
                    }
                    Config.defaultMapPath = Config.mapPath;
                    Config.update(Config.CONFIG_090);

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
                if (log.isEnabled()) log.debug("event run; " + this);
//#endif

                _run();

//#ifdef __LOG__
                if (log.isEnabled()) log.debug("~event run; " + this);
//#endif
            } catch (Throwable t) {
//#ifdef __LOG__
                t.printStackTrace();
                if (log.isEnabled()) log.debug("event failure", t);
//#endif
                Desktop.showError("_EVENT FAILURE_ (" + this + ")", t, null);

            } finally {

                // gc hints
                result = null;
                throwable = null;
                closure = null;

                // release event
                if (release) {
                    releaseEvent(this);
                }
            }
        }

        public void _run() {
//#ifdef __LOG__
            if (throwable != null) {
                System.out.println("*event throwable*");
                throwable.printStackTrace();
            }
            if (log.isEnabled()) log.debug("event " + this.toString());
//#endif

            switch (code) {

                case EVENT_CONFIGURATION_CHANGED: {

                    // update screen
                    update(MASK_ALL);

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
                            // ignore
                        }

                        // to recover position when new map loaded
                        if (map != null) {
                            _qc = getPointer();
                        }
                        
                        // release current data
                        if (atlas != null) {
                            atlas.close();
                            atlas = null;
                        }
                        if (map != null) {
                            map.close();
                            map = null;
                        }

                        // background task
                        if ("atlas".equals(closure)) {
                            _target = "atlas";
                            startOpenAtlas(url);
                        } else {
                            _target = "map";
                            startOpenMap(url, null);
                        }

                    } else if (throwable != null) {
                        showError("[1]", throwable, Desktop.screen);
                    }

                } break;

                case EVENT_TRACKLOG: {

                    if (throwable == null) {
                        if (result instanceof Integer) {
                            int c = ((Integer) result).intValue();
                            switch (c) {
                                case GpxTracklog.CODE_RECORDING_START:
                                    osd.setRecording(true);
                                    break;
                                case GpxTracklog.CODE_RECORDING_STOP:
                                    osd.setRecording(false);
                                    break;
                            }
                        }
                    } else {
                        // display warning
                        showWarning(result == null ? "Tracklog problem." : result.toString(),
                                    throwable, Desktop.screen);

                        // no more recording
                        osd.setRecording(false);
                    }

                    // update screen
                    update(MASK_OSD);

                } break;

                case EVENT_ATLAS_OPENED: {

                    // if opening ok
                    if (throwable == null) {

                        // use new atlas
                        atlas = _atlas;
                        _atlas = null;

                        // force user to select layer
                        (new ItemSelection(Desktop.screen, "LayerSelection", new Event(Event.EVENT_LAYER_SELECTION_FINISHED))).show(atlas.getLayers());

                    } else {

                        // show a user error
                        showError("[3] " + result, throwable, Desktop.screen);

                        // cleanup
                        cleanup(throwable);
                    }

                } break;

                case EVENT_LAYER_SELECTION_FINISHED: {

                    // layer switch with '7'
                    _switch = "switch".equals(closure);

                    // had user selected anything?
                    if (result != null) {

                        // layer name
                        String layerName = (String) result;

                        // has layer changed?
                        if (!layerName.equals(atlas.getLayer())) {

                            // from load task
                            if (closure == null) {

                                // setup atlas
                                atlas.setLayer(layerName);

                                // force user to select default map
                                (new ItemSelection(Desktop.screen, "MapSelection", new Event(Event.EVENT_MAP_SELECTION_FINISHED))).show(atlas.getMapNames());

                            } else { // layer switch

                                // get current lat/lon
                                QualifiedCoordinates qc = map.transform(((MapView) views[VIEW_MAP]).getPosition());

                                // switch match
                                startAlternateMap(layerName, qc, "No map for current position in layer '" + layerName + "'.");
                            }
                        }
                    } else { // cancelled

                        // from load task
                        if (closure == null) {

                            // restore desktop
                            cleanup(null);
                        }
                    }

                } break;

                case EVENT_MAP_SELECTION_FINISHED: {

                    // map switch with '9'
                    _switch = "switch".equals(closure);

                    // had user selected anything?
                    if (result != null) {

                        // map name
                        String name = (String) result;

                        // background task
                        startOpenMap(atlas.getMapURL(name), name);

                    } else { // cancelled

                        // from load task
                        if (closure == null) {

                            // cleanup
                            cleanup(null);
                        }
                    }

                } break;

                case EVENT_MAP_OPENED: {

                    // opening was ok
                    if (throwable == null) {
                        try {
                            // destroy existing map definitely if it is standalone
                            if (atlas == null && map != null) {
//#ifdef __LOG__
                                if (log.isEnabled()) log.debug("definitely destroy map " + map.getPath());
//#endif
                                map.close();
                                map = null; // gc hint
                            }

                            // use new map
                            map = _map;
                            _map = null;

                            // cache map
                            if (atlas != null && map != null) {
//#ifdef __LOG__
                                if (log.isEnabled()) log.debug("caching map " + map.getPath());
//#endif
                                atlas.getMaps().put(map.getPath(), map);
                            }

                            // setup map viewer
                            ((MapView) views[VIEW_MAP]).setMap(map);

                            // move viewer to known position, if any
                            if (_qc != null) {
                                try {
                                    // handle fake qc when browsing across map boundary
                                    if (_qc.getLat() == 90D) {
                                        _qc = QualifiedCoordinates.newInstance(map.getRange()[3].getLat(), _qc.getLon());
                                    } else if (_qc.getLat() == -90D) {
                                        _qc = QualifiedCoordinates.newInstance(map.getRange()[0].getLat(), _qc.getLon());
                                    } else if (_qc.getLon() == 180D) {
                                        _qc = QualifiedCoordinates.newInstance(_qc.getLat(), map.getRange()[0].getLon());
                                    } else if (_qc.getLon() == -180D) {
                                        _qc = QualifiedCoordinates.newInstance(_qc.getLat(), map.getRange()[3].getLon());
                                    }
                                    // transform qc (already local datum) to position, and move to it
                                    if (map.isWithin(_qc)) {
                                        ((MapView) views[VIEW_MAP]).setPosition(map.transform(_qc));
                                    }
                                } finally {
                                    _qc = null;
                                }
                            }

                            // map is ready
                            _setInitializingMap(false);

                            // update OSD & navigation UI
                            QualifiedCoordinates qc = map.transform(((MapView) views[VIEW_MAP]).getPosition());
                            osd.setInfo(qc, true);  // TODO listener
                            updateNavigation(qc);
                            ((MapView) views[VIEW_MAP]).updateNavigationInfo(); // TODO ugly
                            QualifiedCoordinates.releaseInstance(qc);

                            // render screen - it will force slices loading
                            update(MASK_MAP | MASK_OSD);

                            // offer use as default?
                            if (!_switch) {
                                if ("atlas".equals(_target)) {
                                    (new YesNoDialog(Desktop.screen, this)).show("Use as default atlas?", atlas.getURL());
                                } else {
                                    (new YesNoDialog(Desktop.screen, this)).show("Use as default map?", map.getPath());
                                }
                            }
                        } catch (Throwable t) {
//#ifdef __LOG__
                            t.printStackTrace();
//#endif

                            // show user the error
                            showError("Failed to use map.", t, Desktop.screen);

                            // cleanup
                            cleanup(t);

                        }
                    } else {

                        // update loading result
                        _updateLoadingResult("Map loading failed", throwable);

                        // show user the error
                        showError("[6] " + result, throwable, Desktop.screen);

                        // cleanup
                        cleanup(throwable);

                    }

                } break;

                case EVENT_SLICES_LOADED: {

                    // update loading result
                    _updateLoadingResult("Slices loaded", throwable);

                    // if loading was ok
                    if (throwable == null) {

                        // restore OSD
                        osd.setVisible(_osd);

                        // update screen
                        update(MASK_MAP | MASK_OSD);

                    } else {

                        // show user the error
                        showError("[7] " + result, throwable, Desktop.screen);
                    }

                } break;

                case EVENT_LOADING_STATUS_CHANGED: {

                    // update loading result
                    _updateLoadingResult("Loading status", throwable);

                    // loading ok?
                    if (throwable == null) {

                        // update status
                        status.setStatus((String) result);

                        // status update
                        if (result == null) {
                            update(MASK_STATUS /* | MASK_MAP */);
                        } else {
                            update(MASK_STATUS);
                        }
                    } else {

                        // show user the error
                        showError("[8] " + result, throwable, Desktop.screen);
                    }

                } break;

                case EVENT_TRACKING_STATUS_CHANGED: {

                    // grab event data
                    int newState = ((Integer) result).intValue();

                    // TODO keep state somewhere else
                    osd.setProviderStatus(newState);

                    // how severe is the change
                    switch (newState) {

                        case LocationProvider._STARTING: {

                            // start gpx tracklog
                            startGpxTracklog();

                            // reset views on fresh start
                            if (!providerRestart) {
                                for (int i = views.length; --i >= 0; ) {
                                    views[i].reset();
                                }
                            }

                            // clear restart flag
                            providerRestart = false;

                        } break;

                        case LocationProvider.AVAILABLE: {

                            // beep
                            if (!Config.noSounds) {
                                AlertType.INFO.playSound(display);
                            }

                        } break;

                        case LocationProvider.TEMPORARILY_UNAVAILABLE: {

                            // beep
                            if (!Config.noSounds) {
                                AlertType.WARNING.playSound(display);
                            }

                        } break;

                        case LocationProvider.OUT_OF_SERVICE: {

                            // alarm
                            if (!Config.noSounds) {
                                AlertType.ALARM.playSound(display);
                            }

                            // stop tracking completely or restart
                            if (stopRequest || !provider.isRestartable()) {
                                stopTracking(false);
                            } else {
                                restartTracking();
                            }

                        } break;

                        case LocationProvider._STALLED: {

                            // beep
                            if (!Config.noSounds) {
                                AlertType.WARNING.playSound(display);
                            }

                            // no time/speed/alt/etc
                            osd.resetExtendedInfo();

                        } break;

                        case LocationProvider._CANCELLED: {

                            // stop
                            stopTracking(false);

                        } break;
                    }

                    // update screen
                    update(MASK_MAP | MASK_OSD);

                } break;

                case EVENT_TRACKING_POSITION_UPDATED: {

                    // paused?
                    if (paused) {
                        return;
                    }

                    // grab event data
                    Location l = (Location) result;
                    if (l == null) {
                        throw new AssertionFailedException("Location is null");
                    }

                    // update tracklog
                    if (gpxTracklog != null) {
                        gpxTracklog.locationUpdated(l);
                    }

                    // if valid position do updates
                    if (l.getFix() > 0) {

                        // update last know valid location (WGS-84)
                        Location.releaseInstance(location);
                        location = null;
                        location = l;

                        // update wpt navigation
                        try {
                            updateNavigation(l.getQualifiedCoordinates());
                        } catch (NullPointerException e) {
                            throw new IllegalStateException("NPE in navigation update");
                        }

                        // update route navigation
                        try {
                            updateRouting(l.getQualifiedCoordinates());
                        } catch (NullPointerException e) {
                            throw new IllegalStateException("NPE in routing update");
                        }
                    }

                    // notify views
                    int mask = MASK_NONE;
                    for (int i = views.length; --i >= 0; ) {
                        try {
                            mask |= views[i].locationUpdated(l);
                        } catch (NullPointerException e) {
                            throw new IllegalStateException("NPE in view #" + i);
                        }
                    }

                    // update screen
                    update(mask);

                } break;
            }
        }

        private void cleanup(Throwable t) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("cleanup");
//#endif
            // update loading result
            _updateLoadingResult("Event cleanup", t);

            // clear temporary vars
            if (_atlas != null) {
                _atlas.close();
                _atlas = null;
            }
            if (_map != null) {
                _map.close();
                _map = null;
            }
        }

        // debug
        public String toString() {
            return "code " + code + ";result '" + result + "';throwable " + throwable;
        }
        // ~debug
    }

    /*
     * Boot console // TODO make it a View... ?!?
     */

    // vars
    private short consoleY, consoleH;
    private short consoleErrors;
    private short consoleSkips;

    private void consoleInit(Graphics g) {
        consoleY = -1;
        consoleErrors = consoleSkips = 0;
        consoleH = (short) g.getFont().getHeight();
        g.setColor(0x0);
        g.fillRect(0, 0, getWidth(), getHeight());
        flushGraphics();
    }

    private void consoleShow(Graphics g, String text) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("console show - " + text);
//#endif

        if (text == null) {
            return;
        }
        consoleY++;
        g.setColor(0x00FFFFFF);
        g.drawString(text, 2, consoleY * consoleH, Graphics.TOP | Graphics.LEFT);
        flushGraphics();
    }

    private void consoleResult(Graphics g, int code) {
        int x = getWidth() - 2 - g.getFont().charWidth('*');
        if (code == 0) {
            g.setColor(0x0000FF00);
        } else if (code == -1) {
            g.setColor(0x00FF0000);
            consoleErrors++;
        } else {
            g.setColor(0x00FFB900);
            consoleSkips++;
        }
        g.drawChar('*', x, consoleY * consoleH, Graphics.TOP | Graphics.LEFT);
        flushGraphics();
    }

    private void consoleDelay() {
        long delay = consoleErrors > 0 ? 750 : (consoleSkips > 0 ? 250 : 0);
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
            }
        }
    }

    /// ~ CONSOLE

    /*
     * "A key's bit will be 1 if the key is currently down or has
     * been pressed at least once since the last time this method
     * was called."
     *
     * Therefore the dummy getKeyStates() call before invoking run().
     */
    
    private final class KeyCheckTimerTask extends TimerTask {
        public void run() {
            getKeyStates(); // trick
            eventing.callSerially(Desktop.this);
        }
    }
}
