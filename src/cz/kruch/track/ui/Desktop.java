// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.TrackingMIDlet;
import cz.kruch.track.Resources;
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
import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.j2se.io.BufferedOutputStream;

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
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.midlet.MIDlet;
import javax.microedition.io.Connector;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import api.location.LocationProvider;
import api.location.LocationListener;
import api.location.Location;
import api.location.QualifiedCoordinates;
import api.location.LocationException;
import api.file.File;

/**
 * Application desktop. Navigator. :-)
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class Desktop extends GameCanvas
        implements Runnable, CommandListener, LocationListener,
                   /* Map.StateListener, Atlas.StateListener, */
                   YesNoDialog.AnswerListener/*, Navigator*/ {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Desktop");
//#endif

    // dialog timeouts
    private static final int INFO_DIALOG_TIMEOUT    = 1000;
    private static final int ALARM_DIALOG_TIMEOUT   = 3000;
    private static final int WARN_DIALOG_TIMEOUT    = 2000;

    // views
    private static final int VIEW_MAP       = 0;
    private static final int VIEW_HPS       = 1;
    private static final int VIEW_CMS       = 2;

    // UI
    static int POSITIVE_CMD_TYPE;

    // desktop screen and display
    public static Displayable screen;
    public static Display display;
    public static Font font, fontWpt, fontLists, fontStringItems;

    // behaviour flags
    public static int fullScreenHeight = -1;
    public static boolean partialFlush;
    public static boolean hasRepeatEvents;

    // application
    private MIDlet midlet;

    // desktop mode/screen
    private boolean boot = true;

    // common desktop components
    static Image bar, barWpt, barScale;
    static OSD osd; // TODO should move to MapView
    static Status status; // TODO should move to MapView 

    // desktop dimensions
    static int width, height;

    // desktop views
    private View[] views;

    // screen modes
    /*private */int mode;

    // desktop renderer
    private Graphics graphics;

    // data components
    private Map map;
    private Atlas atlas;

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

    // browsing or tracking
    static volatile boolean browsing = true;
    static volatile boolean paused;
    static volatile boolean navigating;
    static volatile boolean showall;

    // keylock status
    private volatile boolean keylock;

    // loading states and last-op message
    private /*volatile*/ boolean initializingMap = true; // using synchronized access helper
    private /*volatile*/ boolean loadingSlices;          // using synchronized access helper
    private final Object[] loadingResult = {
        Resources.getString(Resources.DESKTOP_MSG_NO_DEFAULT_MAP), null 
    };

    // location provider and its last-op throwable and status
    private volatile LocationProvider provider;
    private volatile Object providerStatus;
    private volatile Throwable providerError;
    private volatile Throwable tracklogError;
    private volatile boolean stopRequest;
    private volatile boolean providerRestart;

    // all-purpose timer
    public static Timer timer;

    // logs
    private boolean tracklog;
    private long trackstart;
    private GpxTracklog tracklogGpx;
    private OutputStream trackLogNmea;

    // navigation // TODO move to Waypoints
    /*public*/ static volatile Vector wpts;
    /*public*/ static volatile int wptIdx, wptEndIdx, reachedIdx;
    /*public*/ static volatile int routeDir;

    // current waypoint info
    /*private */volatile float wptDistance, wptHeightDiff;
    /*private */volatile int wptAzimuth;
    /*private */volatile int wptsId, wptsSize;

    // key repeating simulation support
    private volatile int keyRepeatedCount;

    // repeated event simulation for dumb devices
    private volatile TimerTask repeatedKeyCheck;
    private /*volatile*/ int inKey; // using synchronized access helper

    // eventing
    /*private */final SmartRunnable eventing; // TODO fix visibility

    /**
     * Desktop constructor.
     * 
     * @param midlet midlet instance
     */
    public Desktop(MIDlet midlet) {
        super(false);

        // UI
        POSITIVE_CMD_TYPE = TrackingMIDlet.j9 ? Command.ITEM : Command.SCREEN;

        // init static members
        screen = this;
        display = Display.getDisplay(midlet);
        timer = new Timer();
        hasRepeatEvents = hasRepeatEvents();

        // init basic members
        this.midlet = midlet;
        this.eventing = SmartRunnable.getInstance();

        // TODO move to Waypoints???
        Desktop.wptIdx = Desktop.wptEndIdx = Desktop.reachedIdx = -1;
        this.wptAzimuth = -1;
        this.wptDistance = -1F;
        this.wptHeightDiff = Float.NaN;
    }

    public void boot(final int imgcached, final int configured,
                     final int customized, final int localized,
                     final int keysmapped) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("boot");
//#endif

        // get graphics
        final Graphics g = graphics = getGraphics();
        g.setFont(Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_SMALL));

        // console text position
        short lineY = 0;
        short lineHeight = (short) g.getFont().getHeight();

        // prepare console
        consoleInit(g);

        // boot sequence
        consoleShow(g, lineY, "TrekBuddy \u00a9 2008 KrUcH");
        lineY += lineHeight;
        String lc = Resources.getString(Resources.BOOT_LOCAL_COPY);
        if (lc.length() > 0) {
            consoleShow(g, lineY, lc);
            lineY += lineHeight;
        }
        consoleShow(g, lineY, "");
        lineY += lineHeight;
        consoleShow(g, lineY, Resources.getString(Resources.BOOT_CACHING_IMAGES));
        consoleResult(g, lineY, imgcached);
        lineY += lineHeight;
        consoleShow(g, lineY, Resources.getString(Resources.BOOT_LOADING_CFG));
        consoleResult(g, lineY, configured);
        lineY += lineHeight;
        if (customized != 0) {
            consoleShow(g, lineY, Resources.getString(Resources.BOOT_CUSTOMIZING));
            consoleResult(g, lineY, customized);
            lineY += lineHeight;
        }
        if (localized != 0) {
            consoleShow(g, lineY, Resources.getString(Resources.BOOT_L10N));
            consoleResult(g, lineY, localized);
            lineY += lineHeight;
        }
        if (keysmapped != 0) {
            consoleShow(g, lineY, Resources.getString(Resources.BOOT_KEYMAP));
            consoleResult(g, lineY, keysmapped);
            lineY += lineHeight;
        }
        consoleShow(g, lineY, Resources.getString(Resources.BOOT_CREATING_UI));
        try {
            configure();
            consoleResult(g, lineY, 1);
        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
            consoleResult(g, lineY, -1);
        }
        lineY += lineHeight;
        consoleShow(g, lineY, Resources.getString(Resources.BOOT_LOADING_MAP));
        try {
            if (Config.EMPTY_STRING.equals(Config.mapPath)) {
                initDefaultMap();
                consoleResult(g, lineY, 0);
            } else {
                initMap();
                consoleResult(g, lineY, 1);
            }
        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
            _updateLoadingResult(Resources.getString(Resources.DESKTOP_MSG_INIT_MAP), t);
            consoleResult(g, lineY, -1);
        }
        consoleDelay();

        // booting finished
        boot = false;

        // create default desktop components
        resetGui();

        // last
        postInit();
    }

    private void configure() {
        // create and add commands to the screen
        if (Config.fullscreen && cz.kruch.track.TrackingMIDlet.brew) {
            this.addCommand(new Command("", Command.SCREEN, 0));
        }
        if (Config.btDeviceName.length() > 0) {
            this.addCommand(this.cmdRunLast = new Command(Resources.getString(Resources.DESKTOP_CMD_START) + " " + Config.btDeviceName, POSITIVE_CMD_TYPE, 1));
            this.addCommand(this.cmdRun = new Command(Resources.getString(Resources.DESKTOP_CMD_START), POSITIVE_CMD_TYPE, 2));
        } else {
            this.addCommand(this.cmdRun = new Command(Resources.getString(Resources.DESKTOP_CMD_START), POSITIVE_CMD_TYPE, 1));
        }
        if (cz.kruch.track.TrackingMIDlet.getPlatform().startsWith("NokiaE61")) {
            this.addCommand(this.cmdWaypoints = new Command(Resources.getString(Resources.DESKTOP_CMD_NAVIGATION), POSITIVE_CMD_TYPE, 3));
        }
        if (File.isFs()) {
            this.addCommand(this.cmdLoadMap = new Command(Resources.getString(Resources.DESKTOP_CMD_LOAD_MAP), POSITIVE_CMD_TYPE, 4));
            this.addCommand(this.cmdLoadAtlas = new Command(Resources.getString(Resources.DESKTOP_CMD_LOAD_ATLAS), POSITIVE_CMD_TYPE, 5));
        }
        this.addCommand(this.cmdSettings = new Command(Resources.getString(Resources.DESKTOP_CMD_SETTINGS), POSITIVE_CMD_TYPE, 6));
        this.addCommand(this.cmdInfo = new Command(Resources.getString(Resources.DESKTOP_CMD_INFO), POSITIVE_CMD_TYPE, 7));
        this.addCommand(this.cmdExit = new Command(Resources.getString(Resources.DESKTOP_CMD_EXIT), POSITIVE_CMD_TYPE/*EXIT*/, 8/*1*/));
        this.cmdPause = new Command(Resources.getString(Resources.DESKTOP_CMD_PAUSE), Config.fullscreen || cz.kruch.track.TrackingMIDlet.sonyEricsson || cz.kruch.track.TrackingMIDlet.jbed ? POSITIVE_CMD_TYPE : Command.STOP, 1);
        this.cmdContinue = new Command(Resources.getString(Resources.DESKTOP_CMD_CONTINUE), Config.fullscreen || cz.kruch.track.TrackingMIDlet.sonyEricsson || cz.kruch.track.TrackingMIDlet.jbed ? POSITIVE_CMD_TYPE : Command.STOP, 1);
        this.cmdStop = new Command(Resources.getString(Resources.DESKTOP_CMD_STOP), Config.fullscreen || cz.kruch.track.TrackingMIDlet.sonyEricsson || cz.kruch.track.TrackingMIDlet.jbed ? POSITIVE_CMD_TYPE : Command.STOP, 2);

        // handle commands
        this.setCommandListener(this);
        
        // start I/O loader
        LoaderIO.getInstance();
    }

    public int getHeight() {
        if (fullScreenHeight > -1) {
            return fullScreenHeight;
        }

        return super.getHeight();
    }

    private static void resetFont() {
        font = null; // gc hint
        font = Font.getFont(Font.FACE_MONOSPACE,
                            Config.osdBoldFont ? Font.STYLE_BOLD : Font.STYLE_PLAIN,
                            Config.osdMediumFont ? Font.SIZE_MEDIUM : Font.SIZE_SMALL);
        fontWpt = null; // gc hint
        fontWpt = Font.getFont(Font.FACE_SYSTEM,
                               Config.osdBoldFont ? Font.STYLE_BOLD : Font.STYLE_PLAIN,
                               Font.SIZE_SMALL);
        fontLists = null; // gc hint
        fontLists = Font.getFont(Font.getDefaultFont().getFace(),
                                 Font.STYLE_BOLD/*Font.getDefaultFont().getStyle()*/,
                                 Font.SIZE_SMALL);
    }

    private static void resetBar() {
        // alpha
        int alpha = Config.osdAlpha;
        if (alpha > 0xff) {
            alpha = 0xff;
        }

        // OSD/status bar
        int color = alpha << 24 | (Config.osdBlackColor ? 0x00dfdfdf : 0x007f7f7f);
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
        color = alpha << 24 | 0x00ffff00;
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
        color = alpha << 24 | 0x00ffffff;
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

        final int w = getWidth();
        final int h = getHeight();

        boolean sizeChanged = w != width || h != height;
        if (!sizeChanged) {
            return; // no change, just quit
        }

        // update env setup
        if (w < 176) { // narrow screen
            NavigationScreens.useCondensed = 2;
        }

        // remember new size
        width = w;
        height = h;

        // clear main area with black
        Graphics g = graphics;
//#ifdef __ALL__
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
        View[] v = views;
        if (v == null) {
            v = new View[3];
            v[VIEW_MAP] = new MapView(this);
            v[VIEW_MAP].setCanvas(this);
            v[VIEW_HPS] = new LocatorView(this);
            v[VIEW_HPS].setCanvas(this);
            v[VIEW_CMS] = new ComputerView(this);
            v[VIEW_CMS].setCanvas(this);
            v[0].setVisible(true);
/*
            sizeChanged = true; // enforce sizeChanged notification
*/
            // TODO hack
            LoaderIO.getInstance().enqueue((Runnable) v[VIEW_CMS]);
        }
/*
        if (sizeChanged) {
*/
            v[VIEW_MAP].sizeChanged(w, h);
            v[VIEW_HPS].sizeChanged(w, h);
            v[VIEW_CMS].sizeChanged(w, h);
/*
        }
*/

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("GUI reset - final step");
//#endif

        // UI is ready now
        views = v;

        // render screen
        update(MASK_ALL);
    }

    private void initDefaultMap() throws Throwable {
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
    private void initMap() throws Throwable {
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
            final CharArrayTokenizer tokenizer = new CharArrayTokenizer();
            tokenizer.init(mapPath, new char[]{ '?', '&','=' }, false);
            String token = tokenizer.next().toString();

            // load atlas
            _atlas = new Atlas(token, this);
            final Throwable t = _atlas.loadAtlas();
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
        }

        // load map now
        final Map _map = new Map(mapPath, mapName, this);
        if (_atlas != null) { // calibration may already be available
            _map.setCalibration(_atlas.getMapCalibration(mapName));
        }
        final Throwable t = _map.loadMap();
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
                showError(Resources.getString(Resources.DESKTOP_MSG_FRIENDS_FAILED), t, this);
            }
        }

        // check DataDir structure
        if (File.isFs()) {
            Config.initDataDir();
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

        final int key = pointerToKey(x, y);

        if (key != 0) {
            keyPressed(key);
            switch (getGameAction(key)) {
                case Canvas.UP:
                case Canvas.LEFT:
                case Canvas.RIGHT:
                case Canvas.DOWN: {
                    if (repeatedKeyCheck == null) {
                        timer.schedule(repeatedKeyCheck = new KeyCheckTimerTask(this), 750L);
                    }
                } break;
            }
        }
    }

    protected void pointerReleased(int x, int y) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("pointerPressed");
//#endif

        keyReleased(pointerToKey(x, y));
    }

    private int pointerToKey(final int x, final int y) {
        final int j = x / (width / 3);
        final int i = y / (height / 10);

        int key = 0;

        switch (i) {
            case 0:
            case 1:
            case 2: {
                switch (j) {
                    case 0:
                        key = Canvas.KEY_NUM1;
                    break;
                    case 1:
                        key = getKeyCode(Canvas.UP);
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
                        key = getKeyCode(Canvas.LEFT);
                    break;
                    case 1:
                        key = getKeyCode(Canvas.FIRE);
                    break;
                    case 2:
                        key = getKeyCode(Canvas.RIGHT);
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
                        key = getKeyCode(Canvas.DOWN);
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

        return key;
    }

    protected void keyPressed(int i) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("keyPressed");
//#endif

//#ifdef __RIM__
        /* trackball rolling? */
        if (i == Canvas.UP || i == Canvas.DOWN || i == Canvas.LEFT || i == Canvas.RIGHT) {
            final int now = _getInKey();
            _setInKey(i);
            if (now == 0 || now == i) {
                eventing.callSerially(this);
            } else {
                _setInKey(0);
            }
            return;
        }
//#endif

        // keylock notification
        if (keylock) {
            return;
        }

        // counter
        keyRepeatedCount = 0;

        // keymap
        i = Resources.remap(i);

        // only repeated handled for '1'
        if (Canvas.KEY_NUM1 == i) {
            return;
        }

        // handle key
        handleKey(i, false);
    }

    protected void keyRepeated(int i) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("keyRepeated");
//#endif

        // keymap
        i = Resources.remap(i);

        // handle keylock
        if (Canvas.KEY_STAR == i) {
            if (++keyRepeatedCount == 1) {
                keylock = !keylock;
                if (!Config.powerSave) {
                    display.vibrate(1000);
                }
            }
            return;
        } 

        // keylock check
        if (keylock) {
            return;
        }
        
        // handle event
        handleKey(i, true);
    }

    protected void keyReleased(int i) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("keyReleased");
//#endif

//#ifdef __RIM__
        /* trackball rolling stopped? */
        if (i == Canvas.UP || i == Canvas.DOWN || i == Canvas.LEFT || i == Canvas.RIGHT) {
            return;
        }
//#endif

        // stop key checker
        if (repeatedKeyCheck != null) {
            repeatedKeyCheck.cancel();
            repeatedKeyCheck = null;
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("repeated key check cancelled");
//#endif
        }

        // keymap
        i = Resources.remap(i);

        // handle keylock
        if (Canvas.KEY_STAR == i) {
            if (keyRepeatedCount != 0) {
                keyRepeatedCount = 0;
                showConfirmation(keylock ? Resources.getString(Resources.DESKTOP_MSG_KEYS_LOCKED) : Resources.getString(Resources.DESKTOP_MSG_KEYS_UNLOCKED), null);
            }
        }

        // special keys
        if (!keylock) {
            switch (i) {
                case Canvas.KEY_NUM1: { // hack
                    handleKey(i, false);
                } break;
                case Canvas.KEY_NUM3: { // notify device control
                    cz.kruch.track.ui.nokia.DeviceControl.setBacklight();
                } break;
            }
        }

        // no key pressed anymore
        _setInKey(0);

        // scrolling stops // TODO ugly direct access
        MapView.scrolls = 0;

/* why????
        // update
        update(MASK_ALL);
*/
    }

    public void commandAction(Command command, Displayable displayable) {
        if (keylock) {
            showWarning(Resources.getString(Resources.DESKTOP_MSG_KEYS_LOCKED), null, null);
            return;
        }
        if (command == cmdInfo) {
            (new InfoForm()).show(this,
                                  isTracking() ? provider.getThrowable() : providerError,
                                  tracklogError, isTracking() ? provider.getStatus() : providerStatus,
                                  map);
        } else if (command == cmdSettings) {
            (new SettingsForm(new Event(Event.EVENT_CONFIGURATION_CHANGED))).show();
        } else if (command == cmdWaypoints) {
            Waypoints.getInstance().show();
        } else if (command == cmdLoadMap) {
            (new FileBrowser(Resources.getString(Resources.DESKTOP_MSG_SELECT_MAP), new Event(Event.EVENT_FILE_BROWSER_FINISHED, "map"), this)).show();
        } else if (command == cmdLoadAtlas) {
            (new FileBrowser(Resources.getString(Resources.DESKTOP_MSG_SELECT_ATLAS), new Event(Event.EVENT_FILE_BROWSER_FINISHED, "atlas"), this)).show();
        } else if (command == cmdRun) {
            // start tracking
            stopRequest = providerRestart = false;
            preTracking(false);
        } else if (command == cmdStop) {
            // stop tracking
            stopRequest = true;
            stopTracking();
        } else if (command == cmdRunLast) {
            // start tracking with known device
            stopRequest = providerRestart = false;
            preTracking(true);
        } else if (command == cmdExit) {
            (new YesNoDialog(this, this, this, Resources.getString(Resources.DESKTOP_MSG_WANT_QUIT), null)).show();
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

    public void response(final int answer, final Object closure) {
        if (closure == this) { // EXIT
            if (answer == YesNoDialog.YES) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("exit command");
//#endif

                // close wpts files
                Waypoints.shutdown();

                // stop tracklog and tracking
                stopTracking();
                stopTracklog();

                // stop timer
                timer.cancel();

                // stop eventing
                eventing.destroy();

                // stop device control
                cz.kruch.track.ui.nokia.DeviceControl.destroy();

                // stop Friends
                if (friends != null) {
                    friends.destroy();
                }

/*
                // close atlas/map
                if (atlas != null) {
                    atlas.close();
                }
                if (map != null) {
                    map.close();
                }
*/

                // close views
                for (int i = views.length; --i >= 0; ) {
                    views[i].close();
                }

                // backup runtime vars
                try {
                    Config.update(Config.VARS_090);
                } catch (ConfigurationException e) {
                    // ignore
                }

/*
                // stop I/O loader
                LoaderIO.getInstance().destroy();
*/

                // bail out
                midlet.notifyDestroyed();
            }

        } else if (closure instanceof Boolean) { // START TRACKING

            // set flag
            if (YesNoDialog.YES == answer) {
                tracklog = true; // !
            }

            // start tracking
            if (((Boolean) closure).booleanValue()) startTrackingLast(); else startTracking();

            // update OSD
            update(MASK_OSD);
        }
    }
    
    public void locationUpdated(LocationProvider provider, Location location) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("location update: " + new Date(location.getTimestamp()) + ";" + location.getQualifiedCoordinates() + "; course = " + location.getCourse());
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

        /*
         * hack is needed to ... "kick" the event pump??? (SonyEricsson JP-7, Blackberry)
         * shortly displayed dialog helps, but not on S60 :-(
         * 2008-06-10: stop() of SerialLocationProvider changed; only BB seems to
         * have problems now
         */

/*
        switch (newState) {
            // on Blackberry after Permission prompt
            case LocationProvider._STARTING:
                if (api.location.LocationProvider.restarts == 1) { // only first time
                    if (cz.kruch.track.TrackingMIDlet.symbian) {
                        Thread.yield();
                    } else {
                        showAlert(null, Resources.getString(Resources.DESKTOP_MSG_PROV_STARTING), 25, this);
                    }
                }
            break;
            // on JP-7 after Stop command
            // on Blackberry after Permission prompt
            case LocationProvider.OUT_OF_SERVICE:
                if (stopRequest) { // only on stop request
                    if (cz.kruch.track.TrackingMIDlet.symbian) {
                        Thread.yield();
                    } else {
                        showAlert(null, Resources.getString(Resources.DESKTOP_MSG_PROV_OUT_OF_SERVICE), 25, this);
                    }
                }
            break;
        }
*/
//#ifdef __RIM__
        if (cz.kruch.track.TrackingMIDlet.rim) {
            switch (newState) {
                case LocationProvider._STARTING:
                    if (api.location.LocationProvider.restarts == 1) { // only first time
                        showAlert(null, Resources.getString(Resources.DESKTOP_MSG_PROV_STARTING), 25, this);
                    }
                break;
                case LocationProvider.OUT_OF_SERVICE:
                    if (stopRequest) { // only on stop request
                        showAlert(null, Resources.getString(Resources.DESKTOP_MSG_PROV_OUT_OF_SERVICE), 25, this);
                    }
                break;
            }
        }
//#endif
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
        return this.provider != null;
    }

    public boolean isLocation() {
        return ((MapView)views[VIEW_MAP]).isLocation();
    }

    public Atlas getAtlas() {
        return this.atlas;
    }

    public Map getMap() {
        return this.map;
    }

    public void updateNavigation(final QualifiedCoordinates from) {
        // got active wpt?
        if (wpts != null && wptIdx > -1) {

            // wpt location
            final Waypoint wpt = ((Waypoint) wpts.elementAt(wptIdx));
            final QualifiedCoordinates qc = wpt.getQualifiedCoordinates();

            // calculate distance, azimuth and height diff
            wptDistance = from.distance(qc);
            wptAzimuth = (int) from.azimuthTo(qc, wptDistance);
            if (!Float.isNaN(qc.getAlt()) && !Float.isNaN(from.getAlt())) {
                wptHeightDiff = qc.getAlt() - from.getAlt();
            } else {
                wptHeightDiff = Float.NaN;
            }
        }
    }

    public void updateRouting(final QualifiedCoordinates from) {
        // route navigation?
        if (wpts != null && wptIdx > -1) {

            // current wpt reached?
            if (wptDistance > -1F && wptDistance <= Config.wptProximity) {

                // notify // TODO ugly!!!
                ((MapView) views[VIEW_MAP]).mapViewer.setPoiStatus(wptIdx, MapViewer.WPT_STATUS_REACHED);

                // wpt reached - fanfar!
                if (reachedIdx != wptIdx) {

                    // remember
                    reachedIdx = wptIdx;

                    // flash screen
                    cz.kruch.track.ui.nokia.DeviceControl.flash();

                    // play sound and vibrate
                    if (Config.noSounds) {
                        if (!Config.powerSave) {
                            display.vibrate(1000);
                        }
                    } else {
                        boolean notified = false;
                        final String linkPath = ((Waypoint) wpts.elementAt(wptIdx)).getLinkPath();
                        if (linkPath != null && linkPath.endsWith(".amr")) {
                            notified = Camera.play(linkPath);
                        }
                        if (!notified) {
                            notified = Camera.play("wpt.amr");
                        }
                        if (notified) {
                            if (!Config.powerSave) {
                                display.vibrate(1000);
                            }
                        } else { // fallback to system alarm
                            AlertType.ALARM.playSound(display);
                        }
                    }
                }

                // find next wpt
                boolean changed = false;
                switch (routeDir) {
                    case-1: {
                        if (wptIdx > 0) {
                            wptIdx--;
                            changed = true;
                        }
                        break;
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

                    // update navinfo
                    updateNavigation(from);

                    // notify views
                    for (int i = views.length; --i >= 0;) {
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

    /**
     * @deprecated redesign
     */
    public void saveLocation(Location l) {
        if (tracklogGpx != null) {
            tracklogGpx.insert(l);
        }
    }

    public long getTracklogTime() {
        if (trackstart == 0) {
            return System.currentTimeMillis();
        }
        return trackstart;
    }

    public String getTracklogCreator() {
        return cz.kruch.track.TrackingMIDlet.APP_TITLE + " " + midlet.getAppProperty("MIDlet-Version");
    }

    public void goTo(Waypoint wpt) {
        final QualifiedCoordinates qc = wpt.getQualifiedCoordinates();

        if (map.isWithin(qc)) {

            // set browsing mode
            browsing = true;
            ((MapView) views[VIEW_MAP]).browsingOn(false);

            // scroll to position and sync OSD
            ((MapView) views[VIEW_MAP]).setPosition(map.transform(qc));
            ((MapView) views[VIEW_MAP]).syncOSD();

            // update screen
            update(MASK_ALL);

        } else if (atlas != null) {

            // try to find alternate map
            if (startAlternateMap(atlas.getLayer(), qc,
                                  Resources.getString(Resources.DESKTOP_MSG_WPT_OFF_LAYER))) {
                // also set browsing mode
                browsing = true;
            }

        } else {

            // warn user
            Desktop.showWarning(Resources.getString(Resources.DESKTOP_MSG_WPT_OFF_MAP), null, Desktop.this);

        }
    }

    /*
     * Should be called only if not navigating yet; and
     * - no set is shown, or
     * - different set shown
     */
    public void setVisible(Vector wpts, boolean visible) {
        // set flag
        Desktop.showall = visible;

        // show?
        if (visible) {

            // not navigating yet or different set
            if (Desktop.wpts == null || Desktop.wptIdx == -1) {

                // use wpts
                Desktop.wpts = wpts;
                wptsId = wpts.hashCode();

                // notify map view // TODO this is ugly
                views[VIEW_MAP].routeChanged(wpts);
                ((MapView) views[VIEW_MAP]).mapViewer.starTick();
                ((MapView) views[VIEW_MAP]).mapViewer.nextCrosshair();
                ((MapView) views[VIEW_MAP]).mapViewer.starTick();

            } else {

                throw new IllegalStateException("Wrong navigation state");

            }

        } else {

            // notify map view // TODO this is ugly
            views[VIEW_MAP].routeChanged(null);

        }

        // update screen
        update(MASK_ALL);
    }

    /**
     * @deprecated should be?
     */
    public Waypoint getNavigateTo() {
        return wpts == null || wptIdx == -1 ? null : ((Waypoint) wpts.elementAt(wptIdx));
    }

    public void setNavigateTo(Vector wpts, int fromIndex, int toIndex) {
        // 'route changed' flag
        boolean rchange = false;

        // gc hint
        Desktop.wpts = null;

        // start navigation?
        if (wpts != null) {

            // update state vars
            Desktop.navigating = true;
            Desktop.wpts = wpts;

            if (toIndex < 0) { // forward routing
                Desktop.wptIdx = fromIndex;
                Desktop.wptEndIdx = toIndex;
                Desktop.routeDir = 1;
            } else if (fromIndex < 0) { // backward routing
                Desktop.wptIdx = toIndex;
                Desktop.wptEndIdx = fromIndex;
                Desktop.routeDir = -1;
            } else { // single wpt navigation
                Desktop.wptIdx = toIndex;
                Desktop.wptEndIdx = fromIndex;
                Desktop.routeDir = 0;
            }

            // update navinfo
            if (isTracking() && isLocation()) {
                updateNavigation(getLocation().getQualifiedCoordinates());
            } else {
                updateNavigation(getPointer());
            }

            // detect route change
            if (wpts.hashCode() != wptsId || wpts.size() != wptsSize) {
                // remember new route params
                wptsId = wpts.hashCode();
                wptsSize = wpts.size();
                // set flag
                rchange = true;
            }
        } else { /* no, navigation stoppped */

            // reset global navigation info
            Desktop.navigating = false;
            Desktop.routeDir = 0;
            Desktop.wptIdx = -1;

            // reset local navigation info
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

    public float getWptAlt() {
        if (wpts == null || wptIdx == -1) {
            return Float.NaN;
        }
        return ((Waypoint) wpts.elementAt(wptIdx)).getQualifiedCoordinates().getAlt();
    }

    public QualifiedCoordinates getWptCoords() {
        if (wpts == null || wptIdx == -1) {
            return null;
        }
        return ((Waypoint) wpts.elementAt(wptIdx)).getQualifiedCoordinates();
    }

    public String getWptName() {
        if (wpts == null || wptIdx == -1) {
            return null;
        }
        return ((Waypoint) wpts.elementAt(wptIdx)).getName();
    }

    public String getWptCmt() {
        if (wpts == null || wptIdx == -1) {
            return null;
        }
        return ((Waypoint) wpts.elementAt(wptIdx)).getComment();
    }

    public String getWptSym() {
        if (wpts == null || wptIdx == -1) {
            return null;
        }
        return ((Waypoint) wpts.elementAt(wptIdx)).getSym();
    }

    //
    // ~Navigator
    //

    /** Used for key repetition emulation */
    public void run() {
        int key = 0;

//#ifndef __RIM__
        int keyState = getKeyStates();

        if ((keyState & LEFT_PRESSED) != 0) {
            key = Canvas.KEY_NUM4;
        } else if ((keyState & RIGHT_PRESSED) != 0) {
            key = Canvas.KEY_NUM6;
        } else if ((keyState & UP_PRESSED) != 0) {
            key = Canvas.KEY_NUM2;
        } else if ((keyState & DOWN_PRESSED) != 0) {
            key = Canvas.KEY_NUM8;
        }
//#endif        

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

    /*
     * synchronization of vars of primitive type
     * why? callback access mixed with event access?
     */

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

    boolean _getLoadingSlices() {
        synchronized (this) {
            return loadingSlices;
        }
    }

    void _setLoadingSlices(final boolean b) {
        synchronized (this) {
            loadingSlices = b;
        }
    }

    boolean _getInitializingMap() {
        synchronized (this) {
            return initializingMap;
        }
    }

    private void _setInitializingMap(final boolean b) {
        synchronized (this) {
            initializingMap = b;
        }
    }

    /*
     * ~end
     */

    private void handleKey(final int i, final boolean repeated) {
        final View[] views = this.views;

        if (views == null || paused) {
            return;
        }

        int mask = MASK_NONE;
        int action = 0;

        try {
            action = getGameAction(i);
        } catch (IllegalArgumentException e) {
            // ignore
        }

        // hacks
        if (cz.kruch.track.TrackingMIDlet.uiq) {
            switch (i) {
                case KEY_NUM0:
                    action = 0;
                break;
            }
        }

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
                    if (repeatedKeyCheck == null) {
                        timer.schedule(repeatedKeyCheck = new KeyCheckTimerTask(this), 1500L);
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

                    default: {
                        if (!repeated) {
                            mask = views[mode].handleKey(i, repeated);
                        }
                    }
                }
            }
        }

        // update
        update(mask);
    }

    public void update(int mask) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("update " + Integer.toBinaryString(mask));
//#endif

        // anything to update?
        if (mask != MASK_NONE) {

            // prepare slices
            if ((mask & MASK_MAP) != 0) {
                ((MapView) views[VIEW_MAP]).ensureSlices();
            }

            // enqueu render request
            eventing.callSerially(newRenderTask(mask));
        }
//#ifdef __LOG__
          else {
            if (log.isEnabled()) log.debug("update 0!");
        }
//#endif
    }

    public static void showWaitScreen(String title, String message) {
        final Form form = new Form(title);
        form.append(message);
        display.setCurrent(form);
    }

    public static void showConfirmation(String message, Displayable nextDisplayable) {
        showAlert(AlertType.CONFIRMATION, message, INFO_DIALOG_TIMEOUT, nextDisplayable);
    }

    public static void showAlarm(String message, Displayable nextDisplayable,
                                 boolean forever) {
        if (Config.noSounds) {
            Desktop.display.vibrate(1000);
        }
        showAlert(AlertType.ALARM, message, forever ? Alert.FOREVER : ALARM_DIALOG_TIMEOUT, nextDisplayable);
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
                                message, null, Config.noSounds ? null : type);
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
            throw new IllegalStateException("Tracking already started");
        }

        // by default
        tracklog = false;

        // start tracklog?
        switch (Config.tracklog) {
            case Config.TRACKLOG_NEVER: {
                if (last) startTrackingLast(); else startTracking();
            } break;
            case Config.TRACKLOG_ASK: {
                (new YesNoDialog(display.getCurrent(), this, new Boolean(last), Resources.getString(Resources.DESKTOP_MSG_START_TRACKLOG), null)).show();
            } break;
            case Config.TRACKLOG_ALWAYS: {
                tracklog = true; // !
                if (last) startTrackingLast(); else startTracking();
            } break;
        }

        // update OSD
        update(MASK_OSD);
    }

    private boolean startTracking() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("start tracking...");
//#endif

        // which provider?
        String providerName = null;

        // instantiate provider
        try {
            Class providerClass = null;
            switch (Config.locationProvider) {
                case Config.LOCATION_PROVIDER_JSR179:
                    providerClass = Class.forName("cz.kruch.track.location.Jsr179LocationProvider");
                    providerName = "Internal";
                break;
                case Config.LOCATION_PROVIDER_JSR82:
                    providerClass = Class.forName("cz.kruch.track.location.Jsr82LocationProvider");
                    providerName = "Bluetooth";
                break;
//#ifdef __ALL__
                case Config.LOCATION_PROVIDER_HGE100:
//#endif
                case Config.LOCATION_PROVIDER_SERIAL:
                    providerClass = Class.forName("cz.kruch.track.location.SerialLocationProvider");
                    providerName = "Serial";
                break;
                case Config.LOCATION_PROVIDER_SIMULATOR:
                    providerClass = Class.forName("cz.kruch.track.location.SimulatorLocationProvider");
                    providerName = "Simulator";
                break;
//#ifdef __ALL__
                case Config.LOCATION_PROVIDER_MOTOROLA:
                    providerClass = Class.forName("cz.kruch.track.location.MotorolaLocationProvider");
                    providerName = "Motorola";
                break;
//#endif
                case Config.LOCATION_PROVIDER_O2GERMANY:
                    providerClass = Class.forName("cz.kruch.track.location.O2GermanyLocationProvider");
                    providerName = "O2 Germany";
                break;
            }
            provider = (LocationProvider) providerClass.newInstance();
        } catch (Throwable t) {
            showError(Resources.getString(Resources.DESKTOP_MSG_CREATE_PROV_FAILED) + " [" + providerName + "]", t, this);

            return false;
        }

        // register as listener
        provider.setLocationListener(this);

        // start provider
        final int state;
        try {
            state = provider.start();
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("provider started; state " + state);
//#endif
        } catch (Throwable t) {
            showError(Resources.getString(Resources.DESKTOP_MSG_START_PROV_FAILED) + " [" + provider.getName() + "]", t, null);

            // clear member
            provider = null;

            return false;
        }

        // remember track start
        trackstart = System.currentTimeMillis();

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
            provider = (LocationProvider) Class.forName("cz.kruch.track.location.Jsr82LocationProvider").newInstance();
        } catch (Throwable t) {
            showError(Resources.getString(Resources.DESKTOP_MSG_CREATE_PROV_FAILED) + " [Bluetooth]", t, this);
            return false;
        }

        // update OSD
        osd.setProviderStatus(LocationProvider._STARTING);

        // register as listener
        provider.setLocationListener(this);

        // (re)start BT provider
        (new Thread((Runnable) provider)).start();

        // remember track start
        trackstart = System.currentTimeMillis();

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

    private void afterTracking() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("after tracking " + provider);
//#endif

        // stop tracklog
        stopTracklog();

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
        // hack
        ((MapView) views[VIEW_MAP]).browsingOn(true);
        // update
        update(MASK_OSD | MASK_CROSSHAIR);

        // update menu
        removeCommand(cmdStop);
        removeCommand(cmdPause);
        removeCommand(cmdContinue);
        addCommand(cmdRun);
        if (cmdRunLast != null) {
            addCommand(cmdRunLast);
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("~after tracking");
//#endif
    }

    private boolean stopTracking() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("stop tracking " + provider);
//#endif

        // no time
        trackstart = 0;

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

/*
        // unregister as listener
        provider.setLocationListener(null);
*/

        // stop provider
        try {
            provider.stop();
        } catch (Throwable t) {
            showError(Resources.getString(Resources.DESKTOP_MSG_STOP_PROV_FAILED), t, null);
        } finally {
            provider = null;
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("provider being stopped");
//#endif

        return true;
    }

    private void startTracklog() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("start tracklog?");
//#endif

        // tracklog enabled
        if (tracklog) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("yes do start it");
//#endif
            // GPX tracklog?
            final boolean isGpx = Config.TRACKLOG_FORMAT_GPX.equals(Config.tracklogFormat);

            // restart?
            if (providerRestart) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("already running");
//#endif
                // break segment for GPX
                if (isGpx) {

                    // assertion
                    if (tracklogGpx == null) {
                        throw new IllegalStateException("GPX tracklog not started");
                    }
                    // break trkseg
                    tracklogGpx.insert(Boolean.TRUE);
                }

            } else { // fresh new start

                // clear error
                tracklogError = null;

                // GPX
                if (isGpx) {

                    // assertion
                    if (tracklogGpx != null) {
                        throw new IllegalStateException("GPX tracklog already started");
                    }

                    // start new tracklog
                    tracklogGpx = new GpxTracklog(GpxTracklog.LOG_TRK,
                                          new Event(Event.EVENT_TRACKLOG),
                                          getTracklogCreator(),
                                          trackstart);
                    tracklogGpx.setFilePrefix(null);
                    tracklogGpx.start();
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("starting tracklog " + tracklogGpx.getFileName());
//#endif

                } else {

                    // output file
                    File file = null;

                    try {
                        // create file
                        file = File.open(Config.getFolderURL(Config.FOLDER_NMEA) + GpxTracklog.dateToFileDate(trackstart) + ".nmea", Connector.READ_WRITE);
                        if (!file.exists()) {
                            file.create();
                        }

                        // create output
                        trackLogNmea = new BufferedOutputStream(file.openOutputStream(), 4096);

                        // inject provider
                        provider.setObserver(trackLogNmea);

                        // notify itself ;-)
                        (new Event(Event.EVENT_TRACKLOG)).invoke(new Integer(GpxTracklog.CODE_RECORDING_START), null, this);

                    } catch (IOException e) {

                        // notify itself ;-)
                        (new Event(Event.EVENT_TRACKLOG)).invoke(null, e, this);

                    } finally {

                        // close file
                        if (file != null) {
                            try {
                                file.close();
                            } catch (IOException e) {
                                // ignore
                            }
                        }
                    }
                }
            }
        }
    }

    private void stopTracklog() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("stop tracklog");
//#endif
        if (tracklogGpx != null) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("stopping GPX tracklog");
//#endif
            try {
                if (tracklogGpx.isAlive()) {
                    tracklogGpx.shutdown();
                }
                tracklogGpx.join();
            } catch (InterruptedException e) {
                // ignore - should not happen
            }
            tracklogGpx = null; // GC hint
        } else if (trackLogNmea != null) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("stopping NMEA tracklog");
//#endif
            try {
                trackLogNmea.close();
            } catch (Exception e) {
                // ignore
            }
            trackLogNmea = null;
        }
    }

    private void drawPause(final Graphics g) {
        final Font f = Font.getDefaultFont();
        final String s = Resources.getString(Resources.DESKTOP_MSG_PAUSED);
        final int sw = f.stringWidth(s);
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
        g.drawString(s, x + (w - sw) / 2, y + (h - sh) / 2, Graphics.TOP | Graphics.LEFT);
        flushGraphics();
    }

    /*
     * Map.StateListener contract
     */

    public void mapOpened(final Object result, final Throwable throwable) {
        eventing.callSerially(new Event(Event.EVENT_MAP_OPENED,
                                        result, throwable, null));
    }

    public void slicesLoading(final Object result, final Throwable throwable) {
        _setLoadingSlices(true);
    }

    public void slicesLoaded(final Object result, final Throwable throwable) {
        _setLoadingSlices(false);
        eventing.callSerially(new Event(Event.EVENT_SLICES_LOADED,
                                        result, throwable, null));
    }

    public void loadingChanged(final Object result, final Throwable throwable) {
        eventing.callSerially(new Event(Event.EVENT_LOADING_STATUS_CHANGED,
                                        result, throwable, null));
        // hack for "UI smoothness"
        Thread.yield();
    }

    /*
    * Map.StateListener contract
    */

    public void atlasOpened(final Object result, final Throwable throwable) {
        eventing.callSerially(new Event(Event.EVENT_ATLAS_OPENED,
                                        result, throwable, null));
    }

    /* TODO remove
     * thread-safe helpers... hehe, 'thread-safe' :-)
     */

    private void _updateLoadingResult(final String label, final Throwable t) {
        if (t == null) {
            loadingResult[0] = null;
            loadingResult[1] = null;
        } else {
            loadingResult[0] = label;
            loadingResult[1] = t;
        }
    }

    private void _updateLoadingResult(final String label, final String value) {
        loadingResult[0] = label;
        loadingResult[1] = value;
    }

    Object[] _getLoadingResult() {
        return loadingResult;
    }

    String _getLoadingResultText() {
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

    private RenderTask newRenderTask(final int m) {
        final RenderTask result;

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

    private static void releaseRenderTask(final RenderTask task) {
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

        public RenderTask(final int m) {
            this.mask = m;
        }

        public void merge(RenderTask r) {
            this.mask |= r.mask;
        }
        
        public void run() {
            // render
            try {
                // get graphics
                Graphics g = graphics;
//#ifdef __ALL__
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
/*

            if (!postInit && guiReady) {
                postInit = true;
                postInit();
            }
*/
        }
    }

    // temps for atlas/map loading
    private volatile String _target;
    private volatile Map _map;
    private volatile Atlas _atlas;
    private volatile QualifiedCoordinates _qc; // WGS84
    private volatile boolean _switch;
    private volatile boolean _osd;

    void changeLayer() {
        if (atlas != null) {
            final Enumeration e = atlas.getLayers();
            if (e.hasMoreElements()) {
                (new ItemSelection(this, Resources.getString(Resources.DESKTOP_MSG_SELECT_LAYER),
                                   new Event(Event.EVENT_LAYER_SELECTION_FINISHED, "switch"))).show(e, atlas.getLayer());
            } else {
                showInfo(Resources.getString(Resources.DESKTOP_MSG_NO_LAYERS), this);
            }
        }
    }

    void changeMap() {
        if (atlas != null) {
            final Enumeration e = atlas.getMapNames();
            if (e.hasMoreElements()) {
                (new ItemSelection(this, Resources.getString(Resources.DESKTOP_MSG_SELECT_MAP),
                                   new Event(Event.EVENT_MAP_SELECTION_FINISHED, "switch"))).show(e, map.getName());
            } else {
                showInfo(Resources.getString(Resources.DESKTOP_MSG_NO_MAPS), this);
            }
        }
    }

    boolean startAlternateMap(final String layerName, final QualifiedCoordinates qc,
                              final String notFoundMsg) {
        // find map for given coords
        final String mapUrl = atlas.getMapURL(layerName, qc);
        final String mapName = atlas.getMapName(layerName, qc);

        // got map for given coordinates?
        if (mapUrl != null) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("loading alternate map " + mapUrl);
//#endif
            // 'switch' flag
            _switch = true;

            // focus on these coords once the new map is loaded
            _qc = qc;

            // change atlas layer
            atlas.setLayer(layerName);

            // start loading task
            startOpenMap(mapUrl, mapName);

        } else if (notFoundMsg != null) {
            showWarning(notFoundMsg, null, Desktop.screen);
        }

        return mapUrl != null;
    }

    private void startOpenMap(final String url, final String name) {
        // flag on
        _setInitializingMap(true);

        // message for the screen
        _updateLoadingResult(Resources.getString(Resources.DESKTOP_MSG_LOADING_MAP), url);

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

    private void startOpenAtlas(final String url) {
        // flag on
        _setInitializingMap(true);

        // message for the screen
        _updateLoadingResult(Resources.getString(Resources.DESKTOP_MSG_LOADING_ATLAS), url);

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

    private Event newEvent(final int code, final Object result,
                           final Throwable throwable, final Object closure) {
        final Event event;

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

    private void releaseEvent(final Event event) {
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

        public Event(int code) {
            this.code = code;
        }

        public Event(int code, Object closure) {
            this.code = code;
            this.closure = closure;
        }

        public Event(int code, Object result, Throwable throwable, Object closure) {
            this.code = code;
            this.result = result;
            this.throwable = throwable;
            this.closure = closure;
        }

        public void invoke(final Object result, final Throwable throwable, final Object source) {
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
        public void response(final int answer, final Object closure) {
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
                    showConfirmation(Resources.getString(Resources.DESKTOP_MSG_CFG_UPDATED), Desktop.screen);

                } catch (ConfigurationException e) {

                    // show user the error
                    showError(Resources.getString(Resources.DESKTOP_MSG_CFG_UPDATED), e, Desktop.screen);
                }
            }
        }

        /** fail-safe */
        public void run() {
//#ifdef __LOG__
            if (throwable != null) {
                System.out.println("*event throwable*");
                throwable.printStackTrace();
            }
            if (log.isEnabled()) log.debug("event " + this.toString());
//#endif

            try {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("event run; " + this);
//#endif

                switch (code) {
                    case EVENT_CONFIGURATION_CHANGED: {
                        execConfigChanged();
                    } break;
                    case EVENT_FILE_BROWSER_FINISHED: {
                        execFileBrowserFinished();
                    } break;
                    case EVENT_TRACKLOG: {
                        execTracklog();
                    } break;
                    case EVENT_ATLAS_OPENED: {
                        execAtlasOpened();
                    } break;
                    case EVENT_LAYER_SELECTION_FINISHED: {
                        execLayerSelectionFinished();
                    } break;
                    case EVENT_MAP_SELECTION_FINISHED: {
                        execMapSelectionFinished();
                    } break;
                    case EVENT_MAP_OPENED: {
                        execMapOpened();
                    } break;
                    case EVENT_SLICES_LOADED: {
                        execSlicesLoaded();
                    } break;
                    case EVENT_LOADING_STATUS_CHANGED: {
                        execLoadingStatusChanged();
                    } break;
                    case EVENT_TRACKING_STATUS_CHANGED: {
                        execTrackingStatusChanged();
                    } break;
                    case EVENT_TRACKING_POSITION_UPDATED: {
                        execTrackingPositionUpdated();
                    } break;
                }

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

        private void execConfigChanged() {

            // force changes
            Config.useDatum(Config.geoDatum);
            resetFont();
            resetBar();

            // notify views
            for (int i = views.length; --i >= 0; ) {
                try {
                    views[i].configChanged();
                } catch (NullPointerException e) {
//#ifdef __LOG__
                    e.printStackTrace();
//#endif
                    throw new IllegalStateException("NPE in view #" + i);
                }
            }

            // update screen
            update(MASK_ALL);

        }

        private void execFileBrowserFinished() {

            // had user selected anything?
            if (result != null) {

                // user intention to load map or atlas
                _switch = false;

                // cast to file connection
                final api.file.File file = (api.file.File) result;
                final String url = file.getURL();

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

        }

        private void execTracklog() {

            // everything ok?
            if (throwable == null) {
                if (result instanceof Integer) {
                    final int c = ((Integer) result).intValue();
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

                // store error
                tracklogError = throwable;

                // display warning
                showWarning(result == null ? Resources.getString(Resources.DESKTOP_MSG_TRACKLOG_ERROR) : result.toString(),
                            throwable, Desktop.screen);

                // no more recording
                osd.setRecording(false);
            }

            // update screen
            update(MASK_OSD);

        }

        private void execAtlasOpened() {

            // if opening ok
            if (throwable == null) {

                // use new atlas
                atlas = _atlas;
                _atlas = null;

                // force user to select layer
                (new ItemSelection(Desktop.screen, Resources.getString(Resources.DESKTOP_MSG_SELECT_LAYER), new Event(Event.EVENT_LAYER_SELECTION_FINISHED))).show(atlas.getLayers(), null);

            } else {

                // show a user error
                showError("[3] " + result, throwable, Desktop.screen);

                // cleanup
                cleanup(throwable);
            }

        }

        private void execLayerSelectionFinished() {

            // layer switch with '7'
            _switch = "switch".equals(closure);

            // had user selected anything?
            if (result != null) {

                // layer name
                final String layerName = (String) result;

                // has layer changed?
                if (!layerName.equals(atlas.getLayer())) {

                    // from load task
                    if (closure == null) {

                        // setup atlas
                        atlas.setLayer(layerName);

                        // force user to select default map
                        (new ItemSelection(Desktop.screen, Resources.getString(Resources.DESKTOP_MSG_SELECT_MAP),
                                           new Event(Event.EVENT_MAP_SELECTION_FINISHED))).show(atlas.getMapNames(), null);

                    } else { // layer switch

                        // switch match
                        if (!startAlternateMap(layerName, getPointer(),
                                               Resources.getString(Resources.DESKTOP_MSG_NO_MAP_FOR_POS) + " '" + layerName + "'.")) {
                            // let user to select any map
                            (new ItemSelection(Desktop.screen, Resources.getString(Resources.DESKTOP_MSG_SELECT_MAP),
                                               new Event(Event.EVENT_MAP_SELECTION_FINISHED, layerName))).show(atlas.getMapNames(layerName), null);
                        }
                    }
                }
            } else { // cancelled

                // from load task
                if (closure == null) {

                    // restore desktop
                    cleanup(null);
                }
            }

        }

        private void execMapSelectionFinished() {

            // map switch with '9'
            _switch = "switch".equals(closure);

            // had user selected anything?
            if (result != null) {

                // trick - focus on these coords once the new map is loaded
                if (map != null) {
                    _qc = getPointer();
                }

                // map name
                final String name = (String) result;

                // phantom layer
                if (!_switch && closure != null && atlas != null) {
                    atlas.setLayer((String) closure);
                    _switch = true;
                }

                // background task
                startOpenMap(atlas.getMapURL(name), name);

            } else { // cancelled

                // from load task
                if (closure == null) {

                    // cleanup
                    cleanup(null);
                }
            }

        }

        private void execMapOpened() {

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
                                _qc = null; // gc hint
                                _qc = QualifiedCoordinates.newInstance(map.getRange(2), _qc.getLon());
                            } else if (_qc.getLat() == -90D) {
                                _qc = null; // gc hint
                                _qc = QualifiedCoordinates.newInstance(map.getRange(0), _qc.getLon());
                            } else if (_qc.getLon() == 180D) {
                                _qc = null; // gc hint
                                _qc = QualifiedCoordinates.newInstance(_qc.getLat(), map.getRange(1));
                            } else if (_qc.getLon() == -180D) {
                                _qc = null; // gc hint
                                _qc = QualifiedCoordinates.newInstance(_qc.getLat(), map.getRange(3));
                            }

                            // move to position
                            if (map.isWithin(_qc)) {
                                ((MapView) views[VIEW_MAP]).setPosition(map.transform(_qc));
                            }

                        } finally {
                            _qc = null;
                        }
                    }

                    // map is ready
                    _setInitializingMap(false);

                    // update OSD & navigation UI // TODO ugly code begins ---
                    QualifiedCoordinates qc = map.transform(((MapView) views[VIEW_MAP]).getPosition());
                    MapView.setBasicOSD(qc, true);
                    updateNavigation(qc);
                    QualifiedCoordinates.releaseInstance(qc);
                    qc = null; // gc hint
                    ((MapView) views[VIEW_MAP]).updateNavigationInfo(); // TODO ugly
                    // TODO -- ugly code ends

                    // render screen - it will force slices loading
                    update(MASK_MAP | MASK_OSD);

                    // offer use as default?
                    if (!_switch) {
                        if ("atlas".equals(_target)) {
                            (new YesNoDialog(Desktop.screen, this, null, Resources.getString(Resources.DESKTOP_MSG_USE_AS_DEFAULT_ATLAS), atlas.getURL())).show();
                        } else {
                            (new YesNoDialog(Desktop.screen, this, null, Resources.getString(Resources.DESKTOP_MSG_USE_AS_DEFAULT_MAP), map.getPath())).show();
                        }
                    }
                } catch (Throwable t) {
//#ifdef __LOG__
                    t.printStackTrace();
//#endif

                    // show user the error
                    showError(Resources.getString(Resources.DESKTOP_MSG_USE_MAP_FAILED), t, Desktop.screen);

                    // cleanup
                    cleanup(t);

                }
            } else {

                // update loading result
                _updateLoadingResult(Resources.getString(Resources.DESKTOP_MSG_LOAD_MAP_FAILED), throwable);

                // show user the error
                showError("[6] " + result, throwable, Desktop.screen);

                // cleanup
                cleanup(throwable);

            }

        }

        private void execSlicesLoaded() {

            // update loading result
            _updateLoadingResult(Resources.getString(Resources.DESKTOP_MSG_SLICES_LOADED), throwable);

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

        }

        private void execLoadingStatusChanged() {

            // update loading result
            _updateLoadingResult(Resources.getString(Resources.DESKTOP_MSG_LOADING_STATUS), throwable);

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

        }

        private void execTrackingStatusChanged() {

            // grab event data
            final int newState = ((Integer) result).intValue();

            // TODO keep state somewhere else
            osd.setProviderStatus(newState);

            // how severe is the change
            switch (newState) {

                case LocationProvider._STARTING: {

                    // start tracklog
                    startTracklog();

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
                    if (stopRequest || provider == null) {
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("to do: after tracking");
//#endif
                        afterTracking();
                    } else if (provider.isRestartable()) {
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("to do: restart tracking");
//#endif
                        restartTracking();
                    } else {
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("to do: stop tracking");
//#endif
                        stopTracking();
                        afterTracking();
                    }

                } break;

                case LocationProvider._STALLED: {

                    // beep
                    if (!Config.noSounds) {
                        AlertType.WARNING.playSound(display);
                    }

                    // stop provider - if it is restartable, it will be restarted (see above case)
                    try {
                        provider.stop();
                    } catch (Exception e) {
                        // ignore - never happens?
                    }

                } break;

                case LocationProvider._CANCELLED: {

                    // stop and resume
                    stopTracking();
                    afterTracking();

                } break;
            }

            // update screen
            update(MASK_MAP | MASK_OSD);

        }

        private void execTrackingPositionUpdated() {

            // paused?
            if (paused) {
                return;
            }

            // grab event data
            final Location l = (Location) result;
            if (l == null) {
                throw new IllegalStateException("Location is null");
            }

            // update tracklog
            if (tracklogGpx != null) {
                tracklogGpx.locationUpdated(l);
            }

            // if valid position do updates
            if (l.getFix() > 0) {

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
//#ifdef __LOG__
                    e.printStackTrace();
//#endif
                    throw new IllegalStateException("NPE in view #" + i);
                }
            }

            // release instance
            Location.releaseInstance(l);

            // update screen
            update(mask);

        }

        private void cleanup(final Throwable t) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("cleanup");
//#endif
            // update loading result
            {
                final String msg = Resources.getString(Resources.DESKTOP_MSG_EVENT_CLENAUP);
                final int i = msg.indexOf('\n');
                if (i > /* -1 */ 0) {
                    _updateLoadingResult(msg.substring(0, i), msg.substring(i + 1));
                } else {
                    _updateLoadingResult(msg, (String) null);
                }
            }

            // clear temporary vars
            if (_atlas != null) {
                _atlas.close();
                _atlas = null;
            }
            if (_map != null) {
                _map.close();
                _map = null;
            }

            // show hint
            update(MASK_SCREEN);
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
    private short consoleErrors, consoleSkips;

    private void consoleInit(final Graphics g) {
        g.setColor(0x0);
        g.fillRect(0, 0, getWidth(), getHeight());
        flushGraphics();
    }

    private void consoleShow(final Graphics g, final int y, final String text) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("console show - " + text);
//#endif

        if (text == null) {
            return;
        }
        g.setColor(0x00FFFFFF);
        g.drawString(text, 2, y, Graphics.TOP | Graphics.LEFT);
        flushGraphics();
    }

    private void consoleResult(final Graphics g, final int y, final int code) {
        final int x = getWidth() - 2 - g.getFont().charWidth('*');
        switch (code) {
            case -1:
                g.setColor(0x00FF0000);
                consoleErrors++;
            break;
            case 0:
                g.setColor(0x00FFB900);
                consoleSkips++;
            break;
            default:
                g.setColor(0x0000FF00);
        }
        g.drawChar('*', x, y, Graphics.TOP | Graphics.LEFT);
        flushGraphics();
    }

    private void consoleDelay() {
        final long delay = consoleErrors > 0 ? 750 : (consoleSkips > 0 ? 250 : 0);
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
            }
        }
    }

    /// ~ CONSOLE

}
