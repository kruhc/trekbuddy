// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.Resources;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;
import cz.kruch.track.event.Callback;
import cz.kruch.track.fun.Friends;
import cz.kruch.track.location.GpxTracklog;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.location.TripStatistics;
import cz.kruch.track.maps.Map;
import cz.kruch.track.maps.Atlas;
import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.util.Worker;
import cz.kruch.track.util.NakedVector;

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
import javax.microedition.lcdui.Choice;
import javax.microedition.midlet.MIDlet;
import javax.microedition.io.Connector;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Enumeration;
import java.util.Timer;
import java.util.Vector;
import java.util.TimerTask;

import api.file.File;
import api.io.BufferedOutputStream;
import api.location.LocationProvider;
import api.location.LocationListener;
import api.location.Location;
import api.location.QualifiedCoordinates;
import api.location.LocationException;

//#ifdef __ANDROID__
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import net.trekbuddy.midlet.IRuntime;
//#endif
import net.trekbuddy.midlet.Runtime;

/**
 * Main user interface and event handling.
 *
 * @author kruhc@seznam.cz
 */
public final class Desktop implements CommandListener,
                                      LocationListener,
                                      YesNoDialog.AnswerListener, Runnable {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Desktop");
//#endif
    private static final String TAG = cz.kruch.track.TrackingMIDlet.APP_TITLE;

    // dialog timeouts
    private static final int INFO_DIALOG_TIMEOUT    = 1000;
    private static final int ALARM_DIALOG_TIMEOUT   = 3000;
    private static final int WARN_DIALOG_TIMEOUT    = 2000;

    // views
    private static final int VIEW_MAP               = 0;
    private static final int VIEW_HPS               = 1;
    private static final int VIEW_CMS               = 2;

    public static final String DEFAULT_MAP_NAME     = "Default";

    // UI
    public static int POSITIVE_CMD_TYPE, EXIT_CMD_TYPE, SELECT_CMD_TYPE,
                      BACK_CMD_TYPE, CANCEL_CMD_TYPE, CHOICE_POPUP_TYPE,
                      ITEM_CMD_TYPE;

    // desktop screen and display
    public static DeviceScreen screen;
    public static Display display;
    public static Font font, fontWpt, fontLists, fontStringItems, fontBtns;

    // browsing or tracking
    static volatile boolean browsing;
    static volatile boolean paused;
    static volatile boolean navigating;
    static volatile boolean showall, overlay;
    static volatile boolean synced;

    // all-purpose timer
    private static Timer timer;

    // application
    private MIDlet midlet;

    // desktop mode/screen
    private boolean boot, initialized;

    // common desktop components
    static Image bar, barWpt, barScale;
    static OSD osd; // TODO should move to MapView
    static Status status; // TODO should move to MapView 

    // desktop dimensions
    static int width, height;

    // desktop views
    private View[] views;

    // screen modes
    private int mode;

    // groupware components
    private Friends friends;

    // data components
    private volatile Map map;
    private volatile Atlas atlas;

    // LSM/MSK commands
    /*private */Command cmdRun, cmdRunLast, cmdStop;
    /*private */Command cmdWaypoints;
//#ifdef __HECL__
    /*private */Command cmdLive;
//#endif
    /*private */Command cmdLoadMaps/*, cmdLoadMap, cmdLoadAtlas*/;
//#ifdef __B2B__
    /*private */Command cmdLoadGuide;
//#endif
    /*private */Command cmdSettings;
    /*private */Command cmdInfo;
    /*private */Command cmdExit;
    /*private */Command cmdHide;
    // RSK commands
    /*private */Command cmdPause, cmdContinue;

    // loading states and last-op message
    private /*volatile*/ boolean initializingMap; // using synchronized access helper
    private /*volatile*/ boolean loadingSlices;   // using synchronized access helper
    private final Object[] loadingResult;

    // location provider last-op throwable and status
    private volatile Object providerStatus;
    private volatile Throwable providerError;
    private volatile Throwable tracklogError;
    private /*volatile*/ boolean stopRequest;
    private /*volatile*/ boolean providerRestart;

    // logs
    private GpxTracklog tracklogGpx;
    private File tracklogNmea;
    private OutputStream nmeaOut;
    private long trackstart;
    private boolean tracklog;

    // navigation // TODO move to Waypoints or Runtime
    /*public*/ static volatile Vector wpts;
    /*public*/ static volatile String wptsName;
    /*public*/ static volatile int wptIdx, wptEndIdx, reachedIdx;
    /*public*/ static volatile int routeDir;

    // current waypoint info
    /*private */volatile float wptDistance, wptHeightDiff;
    /*private */volatile int wptAzimuth;
    /*private */volatile int wptsId, wptsSize;

    // doubleclick action
    private long lastKeyTime;

    // sync objects
    private static final Object loadingLock = new Object();
    private static final Object renderLock = new Object();

	// workers // TODO move to Worker? TrackingMIDlet?
	private static Worker diskWorker, eventWorker, liveWorker;

//#ifdef __ANDROID__
    private volatile ServiceConnection svcConn;
    private volatile IRuntime runtime;
//#else
    private volatile Runtime runtime;
//#endif

    /**
     * Desktop constructor.
     * 
     * @param midlet midlet instance
     */
    public Desktop(MIDlet midlet) {
        // UI
        POSITIVE_CMD_TYPE = Command.SCREEN;
        EXIT_CMD_TYPE = POSITIVE_CMD_TYPE;
        SELECT_CMD_TYPE = Command.ITEM;
        BACK_CMD_TYPE = Command.BACK;
        CANCEL_CMD_TYPE = Command.CANCEL;
        CHOICE_POPUP_TYPE = Choice.POPUP;
        ITEM_CMD_TYPE = Command.ITEM;

        // platform-specific hacks
//#ifdef __ALL__
        if (cz.kruch.track.TrackingMIDlet.sonyEricssonEx) {
            CANCEL_CMD_TYPE = Command.BACK;
        }
        if (cz.kruch.track.TrackingMIDlet.symbian) {
            SELECT_CMD_TYPE = Command.SCREEN; 
            ITEM_CMD_TYPE = Command.SCREEN;
        }
        if (cz.kruch.track.TrackingMIDlet.uiq) {
            BACK_CMD_TYPE = Command.EXIT;
        }
        if ("Exit".equals(midlet.getAppProperty(cz.kruch.track.TrackingMIDlet.JAD_UI_RIGHT_KEY))) {
            EXIT_CMD_TYPE = Command.EXIT;
        }
//#elifdef __J9__
        POSITIVE_CMD_TYPE = Command.ITEM;
        EXIT_CMD_TYPE = Command.ITEM;
//#elifdef __RIM__
        EXIT_CMD_TYPE = Command.EXIT;
        SELECT_CMD_TYPE = Command.SCREEN;
        BACK_CMD_TYPE = Command.EXIT;
        CANCEL_CMD_TYPE = Command.EXIT;
//#elifdef __ANDROID__
        CANCEL_CMD_TYPE = Command.BACK;
//#endif

        // init static members
        display = Display.getDisplay(midlet);
        screen = new DeviceScreen(this, midlet);
        browsing = true;

        // HACK reset static members
        jvmReset();

        // init basic members
        this.midlet = midlet;
        this.boot = true;
        this.initializingMap = true;
        this.loadingResult = new Object[]{
            Resources.getString(Resources.DESKTOP_MSG_NO_DEFAULT_MAP), null, null
        };

        // locking objects
//        this.loadingLock = new Object();
//        this.renderLock = new Object();

        // TODO move to Waypoints???
        this.wptAzimuth = -1;
        this.wptDistance = -1F;
        this.wptHeightDiff = Float.NaN;
    }

    public static void schedule(TimerTask task, long delay) {
        getTimer().schedule(task, delay);
    }

    public static void schedule(TimerTask task, long delay, long period) {
        getTimer().schedule(task, delay, period);
    }

    public static void scheduleAtFixedRate(TimerTask task, long delay, long period) {
        getTimer().scheduleAtFixedRate(task, delay, period);
    }

    public static Worker getDiskWorker() { // it is more I/O worker than just disk worker
        if (diskWorker == null) {
            diskWorker = new Worker("TrekBuddy [DiskWorker]");
            diskWorker.start();
        }
        return diskWorker;
    }

    static Worker getEventWorker() {
        if (eventWorker == null) {
            eventWorker = new Worker("TrekBuddy [EventWorker]");
            eventWorker.setPriority(Thread.MAX_PRIORITY);
            eventWorker.start();
        }
        return eventWorker;
    }

//#ifdef __HECL__

    public static Worker getLiveWorker() {
        if (liveWorker == null) {
            liveWorker = new Worker("TrekBuddy [LiveWorker]");
            liveWorker.start();
        }
        return liveWorker;
    }

//#endif    

    public void show() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("show");
//#endif
        // finalize and show screen
        screen.setFullScreenMode(Config.fullscreen);
        screen.setTitle(null);
        Desktop.display.setCurrent(screen);
    }

    public void boot(final int imgcached, final int configured,
                     final int resourced, final boolean update) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("boot");
//#endif
//#ifdef __ANDROID__
        android.util.Log.i(TAG, "[app] starting");
//#endif

//#ifdef __B2B__
        // required for reboot
        this.imgcached = imgcached;
        this.configured = configured;
        this.resourced = resourced;
//#endif

        // get graphics
        final Graphics g = screen.getGraphics();
        final Font f = Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_MEDIUM);
        g.setFont(f);

        // console text start position
        final int consoleLineHeight = f.getHeight();
        int consoleLineY = 0;

        // console init
        final long tStart = System.currentTimeMillis();
        consoleInit(g);

        // show copyright(s)
        consoleShow(g, consoleLineY, "TrekBuddy \u00a9 2013 KrUcH");
        consoleLineY += consoleLineHeight;
        final String lc = Resources.getString(Resources.BOOT_LOCAL_COPY);
        if (lc != null && lc.length() > 0) {
            consoleShow(g, consoleLineY, lc);
            consoleLineY += consoleLineHeight;
        }

        // show version
        consoleShow(g, consoleLineY, (new StringBuffer(32)).append(Resources.getString(Resources.INFO_ITEM_VERSION)).append(' ').append(cz.kruch.track.TrackingMIDlet.version).toString());
        consoleLineY += consoleLineHeight;

        // vertical space
        consoleShow(g, consoleLineY, "");
        consoleLineY += consoleLineHeight;

        // show initial steps results
        consoleShow(g, consoleLineY, Resources.getString(Resources.BOOT_CACHING_IMAGES));
        consoleResult(g, consoleLineY, imgcached);
        consoleLineY += consoleLineHeight;
        consoleShow(g, consoleLineY, Resources.getString(Resources.BOOT_LOADING_CFG));
        consoleResult(g, consoleLineY, configured);
        consoleLineY += consoleLineHeight;

        /*
         * from TrackingMIDlet.run
         */
        
        // load default datums
        cz.kruch.track.configuration.Config.initDefaultDatums();

        // fallback to external configuration in case of trouble
        if (configured == 0) {
            cz.kruch.track.configuration.Config.fallback();
        }

        // load extra (touchscreen) graphics
        if (screen.hasPointerEvents()) {
            try {
                NavigationScreens.initializeForTouch();
            } catch (Throwable t) {
                // ignore
            }
        }

        /*
         * ~from TrackingMIDlet.run
         */

        // additional steps from external resources
        if (cz.kruch.track.configuration.Config.dataDirExists) {

            // get list
            final Vector resources = Config.listResources();

            // user resources
            int localized;
            try {
                localized = Resources.localize(resources);
            } catch (Throwable t) {
                localized = -1;
            }
            if (localized != 0) {
                consoleShow(g, consoleLineY, Resources.getString(Resources.BOOT_L10N));
                consoleResult(g, consoleLineY, localized);
                consoleLineY += consoleLineHeight;
            }

            // UI customization
            int customized;
            try {
                customized = cz.kruch.track.ui.NavigationScreens.customize(resources);
                if (screen.hasPointerEvents()) {
                    customized = cz.kruch.track.ui.NavigationScreens.customizeForTouch(resources);
                }
            } catch (Throwable t) {
                customized = -1;
            }
            if (customized != 0) {
                consoleShow(g, consoleLineY, Resources.getString(Resources.BOOT_CUSTOMIZING));
                consoleResult(g, consoleLineY, customized);
                consoleLineY += consoleLineHeight;
            }

            // user keymap
            int keysmapped;
            try {
                keysmapped = Resources.keymap(resources);
            } catch (Throwable t) {
                keysmapped = -1;
            }
            if (keysmapped != 0) {
                consoleShow(g, consoleLineY, Resources.getString(Resources.BOOT_KEYMAP));
                consoleResult(g, consoleLineY, keysmapped);
                consoleLineY += consoleLineHeight;
            }

            // user datums
            cz.kruch.track.configuration.Config.initUserDatums(resources);
        }

//#ifdef __B2B__

        // b2b res init
        b2b_resInit();

//#endif

        // show load map
        consoleShow(g, consoleLineY, Resources.getString(Resources.BOOT_LOADING_MAP));
        try {
            if (Config.EMPTY_STRING.equals(Config.mapURL)) {
                initDefaultMap();
                consoleResult(g, consoleLineY, 0);
            } else {
                if (!initMap()) {
                    initDefaultMap();
                    consoleResult(g, consoleLineY, -1);
                } else {
                    consoleResult(g, consoleLineY, 1);
                }
            }
        } catch (Throwable t) {
//#ifdef __LOG__
            t.printStackTrace();
//#endif
            // keep clean
            map = null;
            atlas = null;
            // message for map screen
            _updateLoadingResult(Resources.getString(Resources.DESKTOP_MSG_INIT_MAP), t);
            // show result on boot screen
            consoleResult(g, consoleLineY, -1);
        }

//#ifdef __B2B__

        if (Config.vendorChecksumKnown) {
            if (Config.vendorChecksum != cz.kruch.track.io.CrcInputStream.getChecksum()) {
                showAlert(AlertType.ERROR, Resources.getString(Resources.VENDOR_CORRUPTED_MAP), Alert.FOREVER, null);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    // ignore
                }
                midlet.notifyDestroyed();
                // TODO Android: finish/exit
            }
        }

//#endif

//#ifdef __ANDROID__
        // bind to runtime
        android.util.Log.d(TAG, "[app] connect to service");
        svcConn = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder binder) {
                android.util.Log.d(TAG, "[app] service connected");
                runtime = (IRuntime) binder;
//                if (runtime.isRunning()) {
//                    android.util.Log.d(TAG, "[app] runtime already running");
//                } else {
//                    android.util.Log.d(TAG, "[app] runtime not running");
//                }
                cz.kruch.track.TrackingMIDlet.getActivity().startService(
                        new Intent(cz.kruch.track.TrackingMIDlet.getActivity(), Runtime.class));
            }

            public void onServiceDisconnected(ComponentName className) {
                android.util.Log.d(TAG, "[app] service disconnected");
                runtime = null;
            }
        };
        cz.kruch.track.TrackingMIDlet.getActivity().bindService(new Intent(cz.kruch.track.TrackingMIDlet.getActivity(), Runtime.class),
                                                                svcConn, Context.BIND_AUTO_CREATE);
//#else
        runtime = new Runtime();
//#endif

        // show boot progress for a while
        consoleDelay(tStart);

        // setup commands
        configure();

        // booting finished
        boot = false;

        // create default desktop components
        resetGui();

        // set map // TODO move to resetGui???
        ((MapView) views[VIEW_MAP]).setMap(map);

        // update screen
        if (update) {
            update(MASK_SCREEN);
        }

        // last // TODO what about B2B build - should it call this too?
        getDiskWorker().enqueue(this);
    }

    // same as postInit, but invoked from disk worker
    public void run() {

        // finish screen splash
        screen.autohide();

        // check DataDir structure
        if (Config.dataDirAccess) {
            Config.initDataDir();
        } else if (File.isFs()) {
            showError("DataDir [" + Config.getDataDir() + "] not accessible - fix it and restart", null, null);
        } else {
            showWarning("JSR-75 not available", null, null);
        }

        // initialize waypoints
        Waypoints.initialize(this);

        // loads CMS profiles // TODO move to resetGui???
        ((Runnable) views[VIEW_CMS]).run();

//#ifdef __HECL__
        // load plugins
        cz.kruch.track.hecl.PluginManager.getInstance().addFallback((cz.kruch.track.hecl.ControlledInterp.Lookup) views[VIEW_CMS]);
        cz.kruch.track.hecl.PluginManager.getInstance().run();
//#endif

        // initialize groupware
        if (cz.kruch.track.TrackingMIDlet.jsr120 && Config.locationSharing) {
            if (friends == null) {
                try {
                    friends = Friends.createInstance();
                    friends.start();
                } catch (Throwable t) {
                    showError(Resources.getString(Resources.DESKTOP_MSG_FRIENDS_FAILED), t, screen);
                }
            }
        }

        // finally initialized
        initialized = true;

//#ifdef __CN1__
        com.codename1.io.Log.showLog();
//#endif
    }

//#ifdef __ANDROID__

    public void onBackground() {
        android.util.Log.i(TAG, "[app] going background");

        // notify views
        final View[] views = this.views;
        if (views != null) {
            for (int i = views.length; --i >= 0; ) {
                views[i].onBackground();
            }
        }
        views[mode].setVisible(false);

        // force GC
        System.gc(); // unconditional!!!
    }

    public void onForeground() {
        android.util.Log.i(TAG, "[app] going foreground");

        // notify views
        final View[] views = this.views;
        if (views != null) {
            for (int i = views.length; --i >= 0; ) {
                views[i].onForeground();
            }
        }
        views[mode].setVisible(true);

        // repaint
        update(MASK_SCREEN);
    }

//#endif

//#ifdef __B2B__

    private void b2b_resInit() {
        final int idx = Integer.parseInt(Resources.getString(Resources.VENDOR_INITIAL_SCREEN));
        if (idx >= 0 && idx <= 2) {
            Config.startupScreen = mode = idx;
        }
        final String map = Resources.getString(Resources.VENDOR_INITIAL_MAP);
        if (!Integer.toString(Resources.VENDOR_INITIAL_MAP).equals(map)) {
            Config.mapURL = map;
        }
        final String checksum = Resources.getString(Resources.VENDOR_INITIAL_CHECKSUM);
        if (!Integer.toString(Resources.VENDOR_INITIAL_CHECKSUM).equals(checksum)) {
            Config.vendorChecksumKnown = true;
            Config.vendorChecksum = (int) Long.parseLong(checksum, 16);
        }
        final String store = Resources.getString(Resources.VENDOR_INITIAL_NAVI_SOURCE);
        if (!Integer.toString(Resources.VENDOR_INITIAL_NAVI_SOURCE).equals(store)) {
            Config.vendorNaviStore = store;
        }
        final String cmd = Resources.getString(Resources.VENDOR_INITIAL_NAVI_CMD);
        if (!Integer.toString(Resources.VENDOR_INITIAL_NAVI_CMD).equals(cmd)) {
            Config.vendorNaviCmd = cmd;
        }
        final String datadir = Resources.getString(Resources.VENDOR_INITIAL_DATADIR);
        if (!Integer.toString(Resources.VENDOR_INITIAL_DATADIR).equals(datadir)) {
            Config.dataDir = datadir;
        }
        final String cms = Resources.getString(Resources.VENDOR_INITIAL_CMS);
        if (!Integer.toString(Resources.VENDOR_INITIAL_CMS).equals(cms)) {
            Config.cmsProfile = cms;
        }
        final String trailOpts = Resources.getString(Resources.VENDOR_TRAIL_OPTS);
        if (!Integer.toString(Resources.VENDOR_TRAIL_OPTS).equals(trailOpts)) {
            final char[] delims = { ',' };
            final CharArrayTokenizer tokenizer = new CharArrayTokenizer();
            tokenizer.init(trailOpts, delims, false);
            Config.trailOn = "true".equals(tokenizer.next().toString());
            Config.trailThick = tokenizer.nextInt();
            Config.trailColor = tokenizer.nextInt();
        }
        final String routeOpts = Resources.getString(Resources.VENDOR_ROUTE_OPTS);
        if (!Integer.toString(Resources.VENDOR_ROUTE_OPTS).equals(routeOpts)) {
            final char[] delims = { ',' };
            final CharArrayTokenizer tokenizer = new CharArrayTokenizer();
            tokenizer.init(routeOpts, delims, false);
            Config.routeLineStyle = "true".equals(tokenizer.next().toString());
            Config.routePoiMarks = "true".equals(tokenizer.next().toString());
            Config.routeThick = tokenizer.nextInt();
            Config.routeColor = tokenizer.nextInt();
        }
    }

//#endif

    private void configure() {
        // create and attach commands
        if (Config.fullscreen && cz.kruch.track.TrackingMIDlet.brew) {
            screen.addCommand(new Command("", Command.SCREEN, 0));
        }
        if (Config.btDeviceName.length() > 0) {
            String fitName = Config.btDeviceName;
            if (fitName.length() > 6) {
                fitName = fitName.substring(0, 6) + "*";
            }
            this.cmdRunLast = new Command(Resources.getString(Resources.DESKTOP_CMD_START) + " " + fitName, POSITIVE_CMD_TYPE, 1);
            if (Config.locationProvider == Config.LOCATION_PROVIDER_JSR82) {
                screen.addCommand(this.cmdRunLast);
            }
        }
        screen.addCommand(this.cmdRun = new Command(Resources.getString(Resources.DESKTOP_CMD_START), POSITIVE_CMD_TYPE, 2));
        screen.addCommand(this.cmdWaypoints = new Command(Resources.getString(Resources.DESKTOP_CMD_NAVIGATION), POSITIVE_CMD_TYPE, 3));
//#ifdef __B2B__
        screen.addCommand(this.cmdLoadGuide = new Command(Resources.getString(Resources.VENDOR_CMD_LOAD_GUIDE), POSITIVE_CMD_TYPE, 4));
//#else
        if (File.isFs()) {
//            screen.addCommand(this.cmdLoadMap = new Command(Resources.getString(Resources.DESKTOP_CMD_LOAD_MAP), POSITIVE_CMD_TYPE, 5));
//            screen.addCommand(this.cmdLoadAtlas = new Command(Resources.getString(Resources.DESKTOP_CMD_LOAD_ATLAS), POSITIVE_CMD_TYPE, 6));
            screen.addCommand(this.cmdLoadMaps = new Command(Resources.getString(Resources.DESKTOP_CMD_MAPS), POSITIVE_CMD_TYPE, 5));
//#ifdef __HECL__
            screen.addCommand(this.cmdLive = new Command(Resources.getString(Resources.DESKTOP_CMD_LIVE), POSITIVE_CMD_TYPE, 6));
//#endif
        }
//#endif
        screen.addCommand(this.cmdSettings = new Command(Resources.getString(Resources.DESKTOP_CMD_SETTINGS), POSITIVE_CMD_TYPE, 7));
        screen.addCommand(this.cmdInfo = new Command(Resources.getString(Resources.DESKTOP_CMD_INFO), POSITIVE_CMD_TYPE, 8));
        screen.addCommand(this.cmdExit = new Command(Resources.getString(Resources.DESKTOP_CMD_EXIT), EXIT_CMD_TYPE, 9/*1*/));
//#ifndef __ANDROID__
        if (Config.fullscreen && Config.hideBarCmd) {
            screen.addCommand(this.cmdHide = new Command("...", Command.CANCEL, 0));
        }
//#endif
        this.cmdPause = new Command(Resources.getString(Resources.DESKTOP_CMD_PAUSE), Config.fullscreen || cz.kruch.track.TrackingMIDlet.sonyEricsson || cz.kruch.track.TrackingMIDlet.jbed ? POSITIVE_CMD_TYPE : Command.STOP, 1);
        this.cmdContinue = new Command(Resources.getString(Resources.DESKTOP_CMD_CONTINUE), Config.fullscreen || cz.kruch.track.TrackingMIDlet.sonyEricsson || cz.kruch.track.TrackingMIDlet.jbed ? POSITIVE_CMD_TYPE : Command.STOP, 1);
        this.cmdStop = new Command(Resources.getString(Resources.DESKTOP_CMD_STOP), Config.fullscreen || cz.kruch.track.TrackingMIDlet.sonyEricsson || cz.kruch.track.TrackingMIDlet.jbed ? POSITIVE_CMD_TYPE : Command.STOP, 2);

        // handle commands
        screen.setCommandListener(this);
    }

    int getMode() {
        return mode;
    }

    public static boolean isHires() {
        return screen.isHires();
    }

    public static void vibrate(final int ms) {
        if (!Config.powerSave) {
            display.vibrate(ms);
        }
    }

    private static Timer getTimer() {
        if (timer == null) {
//#ifdef __ANDROID__
            timer = new Timer("TrekBuddy [Timer]");
//#else
            timer = new Timer();
//#endif
        }
        return timer;
    }

    private static void resetFont() {

        synchronized (renderLock) { // @threads:?:?

            final Font df = Font.getDefaultFont();
            font = null; // gc hint
            font = Font.getFont(Font.FACE_MONOSPACE,
                                Config.osdBoldFont ? Font.STYLE_BOLD : Font.STYLE_PLAIN,
                                Config.desktopFontSize == 0 ? Font.SIZE_SMALL :
                                        (Config.desktopFontSize == 1 ? Font.SIZE_MEDIUM : Font.SIZE_LARGE));
            fontWpt = null; // gc hint
            fontWpt = Font.getFont(Font.FACE_SYSTEM,
                                   Config.osdBoldFont ? Font.STYLE_BOLD : Font.STYLE_PLAIN,
                                   Font.SIZE_SMALL);
            fontBtns = null; // gc hint
            fontBtns = Font.getFont(df.getFace(), Font.STYLE_PLAIN, Font.SIZE_MEDIUM);
            fontStringItems = null;
            fontStringItems = Font.getFont(df.getFace(), df.getStyle(),
                                           isHires() ? Font.SIZE_MEDIUM : Font.SIZE_SMALL);
            fontLists = null; // gc hint
            try {
                fontLists = Font.getFont((Config.listFont >> 16) & 0x000000ff,
                                         (Config.listFont >> 8) & 0x000000ff,
                                         Config.listFont & 0x000000ff);
            } catch (IllegalArgumentException e) {
                fontLists = df;
            }

        } /* ~synchronized */
    }

    private static void resetBar() {
        // alpha
        int alpha = Config.osdAlpha;
        if (alpha > 0xff) {
            alpha = 0xff;
        }

        synchronized (renderLock) { // @threads:?:?

            // OSD/status bar
            int color = alpha << 24 | (Config.osdBlackColor ? 0x00dfdfdf : 0x007f7f7f);
            int h = font.getHeight();
            int w = screen.getWidth();
            bar = null; // gc hint
            bar = cz.kruch.track.util.ImageUtils.createRGBImage(w, h, color);

            // wpt label bar
            color = alpha << 24 | 0x00ffff00;
            h = cz.kruch.track.TrackingMIDlet.getPlatform().startsWith("Nokia/6230i") ? font.getBaselinePosition() + 2 : font.getHeight();
            barWpt = null; // gc hint
            barWpt = cz.kruch.track.util.ImageUtils.createRGBImage(w, h, color);

            // scale bar
            color = alpha << 24 | 0x00ffffff;
            h = font.getHeight();
            w = font.stringWidth("99999 km") + 4;
            barScale = null; // gc hint
            barScale = cz.kruch.track.util.ImageUtils.createRGBImage(w, h, color);

        } /* ~synchronized */

        if (Config.forcedGc) {
            System.gc(); // conditional
        }
    }

    private static void jvmReset() {
        /* reset everything for proper activity restart */
//#ifdef __ANDROID__
        // for proper resetGUI on next start
        width = height = 0;
        osd = null;
        status = null;
        // thread-based refs
        timer = null;
        eventWorker = diskWorker = liveWorker = null;
        rtCountFree = countFree = 0;
        // navigation refs
        wpts = null;
        wptsName = null;
        routeDir = 0;
        Waypoints.jvmReset();
//#ifdef __HECL__
        // plugin ref
        cz.kruch.track.hecl.PluginManager.jvmReset();
//#endif
        // pools
        java.util.Arrays.fill(rtPool, null);
        java.util.Arrays.fill(pool, null);
//#endif
        // navigation reset
        wptIdx = wptEndIdx = reachedIdx = -1;
    }

    synchronized boolean resetGui() {
        // that's it when booting
        if (boot) {
            return false;
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("GUI reset");
//#endif

        int w = screen.getWidth();
        int h = screen.getHeight();

//#ifdef __ANDROID__
        // TODO move to better place
        cz.kruch.track.TrackingMIDlet.getActivity().config.ignoreVolumeKeys = !Config.easyZoomVolumeKeys;
        cz.kruch.track.TrackingMIDlet.getActivity().config.ignoreTextFieldFocus = !Config.forceTextFieldFocus;
//#endif

        if (w == width && h == height) {
            return false; // no change, just quit
        }

        // remember new size
        width = w;
        height = h;

        // update env setup
        if (w < 176) { // narrow screen on old phones
            NavigationScreens.useCondensed = 2;
        }

        // clear main area with black
        final Graphics g = screen.getGraphics();
        g.setColor(0x0);
        g.fillRect(0, 0, w, h);
        g.clipRect(0, 0, w, h);

        // reset fonts and bars
        resetFont();
        resetBar();

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("GUI reset - step 1");
//#endif

        // create common components
        if (osd == null) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("creating OSD");
//#endif
            osd = new OSD(0, 0, w, h);
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
            v[VIEW_HPS] = new LocatorView(this);
            v[VIEW_CMS] = new ComputerView(this);
            v[mode = Config.startupScreen].setVisible(true);
        }
        v[VIEW_MAP].sizeChanged(w, h);
        v[VIEW_HPS].sizeChanged(w, h);
        v[VIEW_CMS].sizeChanged(w, h);

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("GUI reset - final step");
//#endif

        // UI is ready now
        views = v;

        return true;
    }

    private boolean initDefaultMap() throws Throwable {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("init default map");
//#endif

        // in-jar map
        final Map _map = new Map("trekbuddy.jar", DEFAULT_MAP_NAME, this);
        Throwable t = _map.loadMap();
        if (t != null) {
//            throw t;
            return false;
        }

        // use these
        map = _map;
        
        // we are done
        _setInitializingMap(false);

//#ifdef __LOG__
        if (log.isEnabled()) log.info("~init default map");
//#endif

        return true;
    }

    /* hack - call blocking method to show result in boot console */ // TODO rather ugly
    private boolean initMap() throws Throwable {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("init map");
//#endif

        String mapURL = Config.mapURL;
        String mapName = null;
        Atlas _atlas = null;

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("startup map: " + mapURL);
//#endif

        // load atlas first
        if (mapURL.indexOf('?') > -1) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("loading atlas");
//#endif

            // get atlas index path
            final String[] parts = Atlas.parseURL(mapURL);

            // load atlas
            _atlas = new Atlas(parts[0], null, this);
            final Throwable t = _atlas.loadAtlas();
            if (t != null) {
//                throw t;
                return false;
            }
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("atlas loaded");
//#endif

            // get layer and map name and path
            try {
                _atlas.setLayer(File.decode(parts[1]));
                mapName = File.decode(parts[2]);
                mapURL = _atlas.getFileURL(mapName);
            } catch (NullPointerException e) { // map probably removed by user
                return false;
            }
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("loading map");
//#endif

        // load map now
        final Map _map = new Map(mapURL, mapName, this);
        if (_atlas != null) { // calibration may already be available
            _map.setCalibration(_atlas.getMapCalibration(mapName));
        }
        final Throwable t = _map.loadMap();
        if (t != null) {
//            throw t;
            return false;
        }
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("map loaded");
//#endif

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

        return true;
    }

    public void commandAction(Command command, Displayable displayable) {
        if (screen.isKeylock()) {
            showWarning(Resources.getString(Resources.DESKTOP_MSG_KEYS_LOCKED), null, null);
            return;
        }
        if (!initialized) {
            return;
        }
        if (command == cmdInfo) {
            final InfoForm form = new InfoForm();
            final Object[] extras;
            if (isTracking()) {
                extras = new Object[]{ runtime.getProvider().getStatus(), runtime.getProvider().getThrowable(), tracklogError };
            } else {
                extras = new Object[]{ providerStatus, providerError, tracklogError };
            }
            form.show(this, map, extras);
        } else if (command == cmdSettings) {
            (new SettingsForm(new Event(Event.EVENT_CONFIGURATION_CHANGED))).show();
        } else if (command == cmdWaypoints) {
            Waypoints.getInstance().show(0);
/*
        } else if (command == cmdLoadMap) {
            (new FileBrowser(Resources.getString(Resources.DESKTOP_MSG_SELECT_MAP), new Event(Event.EVENT_FILE_BROWSER_FINISHED, "map"),
                             screen, Config.FOLDER_MAPS,
                             new String[]{ ".map", ".gmi", ".tar", ".xml" })).show();
        } else if (command == cmdLoadAtlas) {
            (new FileBrowser(Resources.getString(Resources.DESKTOP_MSG_SELECT_ATLAS), new Event(Event.EVENT_FILE_BROWSER_FINISHED, "atlas"),
                             screen, Config.FOLDER_MAPS,
                             new String[]{ ".tba", ".idx", ".tar", ".xml" })).show();
*/
        } else if (command == cmdLoadMaps) {
            (new FileBrowser(Resources.getString(Resources.DESKTOP_MSG_SELECT_MAP), new Event(Event.EVENT_FILE_BROWSER_FINISHED, "map/atlas"),
                             screen, Config.FOLDER_MAPS,
                             new String[]{ ".tba", ".idx", ".xml", ".tar", ".map", ".gmi" })).show();
//#ifdef __HECL__
        } else if (command == cmdLive) {
            cz.kruch.track.hecl.PluginManager.getInstance().show();
//#endif
//#ifdef __B2B__
        } else if (command == cmdLoadGuide) {
            (new FileBrowser(Resources.getString(Resources.VENDOR_MSG_SELECT_GUIDE), new Event(Event.EVENT_FILE_BROWSER_FINISHED, "guide"),
                             screen, "../../", null)).show();
//#endif
        } else if (command == cmdRun) {
            // start tracking
            _setStopRequest(false);
            _setProviderRestart(false);
            preTracking(false);
        } else if (command == cmdStop) {
            // stop tracking
            _setStopRequest(true);
            stopTrackingRuntime();
        } else if (command == cmdRunLast) {
            // start tracking with known device
            _setStopRequest(false);
            _setProviderRestart(false);
            preTracking(true);
        } else if (command == cmdExit) {
            if (Config.noQuestions) {
                response(YesNoDialog.YES, this);
            } else {
                (new YesNoDialog(screen, this, this, Resources.getString(Resources.DESKTOP_MSG_WANT_QUIT), null)).show();
            }
        } else if (command == cmdPause) {
            // update flag
            paused = true;
            // update menu
            screen.removeCommand(cmdPause);
            screen.addCommand(cmdContinue);
            // update screen
            update(MASK_SCREEN);
        } else if (command == cmdContinue) {
            // update flag
            paused = false;
            // update menu
            screen.removeCommand(cmdContinue);
            screen.addCommand(cmdPause);
            // update screen
            update(MASK_SCREEN);
        } else { // hide-menubar-command command is used
            // try to hide menubar gently
            screen.setFullScreenMode(false);
            screen.setFullScreenMode(true);
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
//#ifdef __ANDROID__
                android.util.Log.i(TAG, "[app] exiting");
//#endif

                // stop device control
                cz.kruch.track.ui.nokia.DeviceControl.destroy();

                // no more UI events
                screen.hideNotify();

                // stop timer
                if (timer != null) {
                    timer.cancel();
                }

                // stop tracklog and tracking
                stopTrackingRuntime();
                stopTracklog();

//#ifdef __ANDROID__
                // stop service
                cz.kruch.track.TrackingMIDlet.getActivity().stopService(
                        new Intent(cz.kruch.track.TrackingMIDlet.getActivity(), Runtime.class));
                // unbind runtime
                cz.kruch.track.TrackingMIDlet.getActivity().unbindService(svcConn);
//#endif

/* probably not necessary
                // stop Friends
                if (friends != null) {
                    friends.destroy();
                }
*/

/* probably not necessary
                // close atlas/map
                if (atlas != null) {
                    atlas.close();
                }
                if (map != null) {
                    map.close();
                }
*/

                // close views
                final View[] views = this.views;
                for (int i = views.length; --i >= 0; ) {
                    views[i].setVisible(false);
                    views[i].close();
                }

                // backup runtime vars
                try {
                    Config.update(Config.VARS_090);
                } catch (ConfigurationException e) {
                    // ignore
                }

//#ifdef __ANDROID__

                // clear JVM
                jvmReset();

//#endif

                // bail out
                midlet.notifyDestroyed();

//#ifdef __ANDROID__
                cz.kruch.track.TrackingMIDlet.getActivity().finish();
//                System.runFinalizersOnExit(true);
                System.exit(0); // see lines 466-467 in microemulator\microemu-javase\src\main\java\org\microemu\app\Common.java
//#endif
            }

        } else if (closure instanceof Boolean) { // START TRACKING

            // set flag
            if (YesNoDialog.YES == answer) {
                tracklog = true; // !
            }

            // start runtime/tracking
            startTrackingRuntime(((Boolean) closure).booleanValue());

            // update OSD
            update(MASK_OSD);
        }
    }
    
    public void locationUpdated(LocationProvider provider, Location location) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("location update: " + new Date(location.getTimestamp()) + ";" + location.getQualifiedCoordinates() + "; course = " + location.getCourse());
//#endif
//        eventing.callSerially(newEvent(Event.EVENT_TRACKING_POSITION_UPDATED,
//                              location, null, provider));
        getEventWorker().enqueue(newEvent(Event.EVENT_TRACKING_POSITION_UPDATED,
                                          location, null, provider));
    }

    public void providerStateChanged(LocationProvider provider, int newState) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("location provider state changed; " + newState);
//#endif

//        eventing.callSerially(newEvent(Event.EVENT_TRACKING_STATUS_CHANGED,
//                              new Integer(newState), null, provider));
        getEventWorker().enqueue(newEvent(Event.EVENT_TRACKING_STATUS_CHANGED,
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
/* not needed when thread is used instead of callSerially?
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
*/
//#endif
    }

    public void tracklogStateChanged(LocationProvider provider, boolean isRecording) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("tracklog state changed; " + isRecording);
//#endif
//        eventing.callSerially(newEvent(Event.EVENT_TRACKLOG,
//                              new Integer(isRecording ? GpxTracklog.CODE_RECORDING_START : GpxTracklog.CODE_RECORDING_STOP),
//                              null, provider));
        getEventWorker().enqueue(newEvent(Event.EVENT_TRACKLOG,
                                          new Integer(isRecording ? GpxTracklog.CODE_RECORDING_START : GpxTracklog.CODE_RECORDING_STOP),
                                          null, provider));
    }

    public void orientationChanged(LocationProvider provider, int heading) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("orientation changed; " + heading);
//#endif

//        eventing.callSerially(newEvent(Event.EVENT_ORIENTATION_CHANGED,
//                              new Integer(heading), null, provider));
        getEventWorker().enqueue(newEvent(Event.EVENT_ORIENTATION_CHANGED,
                                          new Integer(heading), null, null));
    }

    //
    // Navigator contract
    //

    boolean isTracking() {
        return this.runtime != null && this.runtime.getProvider() != null;
    }

    boolean isLocation() {
        return ((MapView) views[VIEW_MAP]).isLocation(); // TODO navigator should now better?
    }

    boolean isAtlas() {
        return this.atlas != null;
    }

    Atlas getAtlas() {
        return this.atlas;
    }

    Map getMap() {
        return this.map;
    }

    Friends getFriends() {
        if (cz.kruch.track.TrackingMIDlet.jsr120) {
            if (friends == null) {
                try {
                    friends = Friends.createInstance();
                } catch (Throwable t) {
                    showError(Resources.getString(Resources.DESKTOP_MSG_FRIENDS_FAILED), t, screen);
                }
            }
        }
        return friends;
    }

    void updateNavigation(final QualifiedCoordinates from) {
        // got active wpt?
        if (wpts != null && wptIdx > -1) {

            // wpt location
            final Waypoint wpt = ((Waypoint) wpts.elementAt(wptIdx));
            final QualifiedCoordinates qc = wpt.getQualifiedCoordinates();

            // calculate distance, azimuth and height diff
            wptDistance = from.distance(qc);
            wptAzimuth = (int) from.azimuthTo(qc);
            if (!Float.isNaN(qc.getAlt()) && !Float.isNaN(from.getAlt())) {
                wptHeightDiff = qc.getAlt() - from.getAlt();
            } else {
                wptHeightDiff = Float.NaN;
            }
        }
    }

    /* called from synchronized block in Event.execTrackingPositionUpdated */
    private void updateRouting(final QualifiedCoordinates from) {
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
/* this makes mess on S40, SonyEricsson, ... where else??
                    cz.kruch.track.ui.nokia.DeviceControl.flashScreen();
*/

                    // play sound if set
                    if (Config.wptAlertSound) {
                        final String userLink = ((Waypoint) wpts.elementAt(wptIdx)).getLink(Waypoint.LINK_GENERIC_SOUND);
                        cz.kruch.track.fun.Playback.play(Config.FOLDER_SOUNDS,
                                                         userLink, Config.defaultWptSound,
                                                         AlertType.ALARM);
                    }

                    // vibrate if set - overrides powerSave
                    if (Config.wptAlertVibr) {
                        display.vibrate(500);
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
                    final View[] views = this.views;
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
     *
     * @return last known position from GPS
     */
//#ifdef __ANDROID__
    /* public for picture geotagging, see AndroidCamera */
    public
//#endif
    Location getLocation() {
        return views[VIEW_MAP].getLocation();
    }

    /**
     * Gets current pointer coordinates (WGS-84).
     * Called by {@link Waypoints} only.
     *
     * @return current pointer coordinates
     */
    QualifiedCoordinates getPointer() {
        if (getMap() != null) {
            return views[VIEW_MAP].getPointer();
        }
        return null;
    }

    /**
     * Gets current coordinates (WGS-84).
     * Called by {@link Waypoints} only.
     *
     * @return current coordinates
     */
    QualifiedCoordinates getRelQc() {
        if (!browsing && isTracking() && isLocation()) {
            return getLocation().getQualifiedCoordinates();
        }
        return getPointer();
    }

    /**
     * @deprecated redesign
     */
    void saveLocation(Location l) {
        if (tracklogGpx != null) {
            tracklogGpx.insert(l);
        }
    }

    long getTracklogTime() {
        if (trackstart == 0) {
            return System.currentTimeMillis();
        }
        return trackstart;
    }

    String getTracklogCreator() {
        return cz.kruch.track.TrackingMIDlet.APP_TITLE + " " + cz.kruch.track.TrackingMIDlet.version;
    }

    void goTo(Waypoint wpt) {
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
            if (startAlternateMap(atlas.getLayer(), qc)) {
                // set browsing mode // TODO why?
                browsing = true;
            } else {
                // warn user
                showWarning(Resources.getString(Resources.DESKTOP_MSG_WPT_OFF_LAYER), null, Desktop.screen);
            }

        } else {

            // warn user
            Desktop.showWarning(Resources.getString(Resources.DESKTOP_MSG_WPT_OFF_MAP), null, screen);

        }
    }

    /*
     * Should be called only if not navigating yet; and
     * - no set is shown, or
     * - different set shown
     */
    void showWaypoints(final NakedVector wpts, final String wptsName, final int mode) {

        // show?
        final boolean show = wpts != null && wptsName != null;
        if (show) {

            // not navigating yet or different set
            if (Desktop.wpts == null || Desktop.wptIdx == -1) {

                // use wpts
                Desktop.wpts = wpts;
                Desktop.wptsName = wptsName;
                wptsId = wpts.hashCode();

                // notify map view
                views[VIEW_MAP].routeChanged(wpts, mode);

            } else if (Desktop.wpts == wpts && Desktop.showall == false) {

                // this is ok state - navigation to one, then user selected ShowAll

            } else {

                throw new IllegalStateException("Wrong navigation state");

            }

        } else if (Desktop.wptIdx == -1) { /* 2013-04-21: cannot release route when navigating */

/* 2009-01-07: do nothing, showall flag is enough */
/* 2010-04-08: and what about releasing memory?? and map viewer state?? */

            // no more any wpts
            Desktop.wpts = null;
            wptsId = 0;

            // notify map view
            views[VIEW_MAP].routeChanged(null, mode);
        }

        // set flag
        Desktop.showall = show;

        // update screen
        update(MASK_ALL);
    }

    void routeExpanded(final Waypoint wpt) {
        int mask = MASK_OSD;

        // notify views
        final View[] views = this.views;
        for (int i = views.length; --i >= 0; ) {
            mask |= views[i].routeExpanded(Desktop.wpts);
        }

        // update screen
        update(mask);

    }

    Waypoint getNavigateTo() {
        return wpts == null || wptIdx == -1 ? null : ((Waypoint) wpts.elementAt(wptIdx));
    }

    void setNavigateTo(final Vector wpts, final String wptsName,
                       final int fromIndex, final int toIndex) {
        // 'route changed' flag
        boolean rchange = false;

        // start navigation?
        if (wpts != null) {

            // update state vars
            Desktop.navigating = true;
            Desktop.wpts = null; // gc hint
            Desktop.wpts = wpts;
            Desktop.wptsName = wptsName;

            if (toIndex < 0) { // forward routing
                Desktop.wptIdx = fromIndex;
                Desktop.wptEndIdx = toIndex;
                Desktop.routeDir = 1;
                Desktop.showall = false;
            } else if (fromIndex < 0) { // backward routing
                Desktop.wptIdx = toIndex;
                Desktop.wptEndIdx = fromIndex;
                Desktop.routeDir = -1;
                Desktop.showall = false;
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
            Desktop.wptIdx = Desktop.reachedIdx = Desktop.wptEndIdx = -1;

            // reset local navigation info
            wptsId = 0;
            wptAzimuth = -1;
            wptDistance = -1F;
            wptHeightDiff = Float.NaN;

            // set 'route changed' flag
            rchange = true;

            // hack this does not solve different routes for navigation and and showall
            if (showall) {
                rchange = false;
            } else {
                Desktop.wpts = null;
            }
        }

        int mask = MASK_OSD;

        // notify views
        final View[] views = this.views;
        for (int i = views.length; --i >= 0; ) {
            if (rchange) {
                views[i].routeChanged(wpts, 1);
            }
            mask |= views[i].navigationChanged(wpts, wptIdx, false);
        }

        // update screen
        update(mask);
    }

    Waypoint previousWpt() {
        if (wpts != null) {

            // not at the first one yet?
            if (wptIdx > 0) {
                --wptIdx;
            }

            // update navinfo // TODO copy&pasted from setNavigateTo
            if (isTracking() && isLocation()) {
                updateNavigation(getLocation().getQualifiedCoordinates());
            } else {
                updateNavigation(getPointer());
            }

            return (Waypoint) wpts.elementAt(wptIdx);
        }

        return null;
    }

    Waypoint nextWpt() {
        if (wpts != null) {

            // not at the last one yet?
            if (wptIdx < wpts.size() - 1) {
                ++wptIdx;
            }

            // update navinfo
            if (isTracking() && isLocation()) {
                updateNavigation(getLocation().getQualifiedCoordinates());
            } else {
                updateNavigation(getPointer());
            }

            return (Waypoint) wpts.elementAt(wptIdx);
        }
        return null;
    }

    int getWptAzimuth() {
        return wptAzimuth;
    }

    float getWptDistance() {
        return wptDistance;
    }

    float getWptAltDiff() {
        return wptHeightDiff;
    }

    Waypoint getWpt() {
        if (wpts == null || wptIdx == -1) {
            return null;
        }
        return ((Waypoint) wpts.elementAt(wptIdx));
    }

    //
    // ~Navigator
    //

    /*
     * synchronization of vars of primitive type
     * why? callback access mixed with event access?
     */

    boolean _getLoadingSlices() {
        synchronized (loadingLock) {
            return loadingSlices;
        }
    }

    private void _setLoadingSlices(final boolean b) {
        synchronized (loadingLock) {
            loadingSlices = b;
        }
    }

    boolean _getInitializingMap() {
        synchronized (loadingLock) {
            return initializingMap;
        }
    }

    private void _setInitializingMap(final boolean b) {
        synchronized (loadingLock) {
            initializingMap = b;
        }
    }

    private boolean _isStopRequest() {
        synchronized (this) {
            return stopRequest;
        }
    }

    private void _setStopRequest(final boolean stopRequest) {
        synchronized (this) {
            this.stopRequest = stopRequest;
        }
    }

    private boolean _isProviderRestart() {
        synchronized (this) {
            return providerRestart;
        }
    }

    private void _setProviderRestart(final boolean providerRestart) {
        synchronized (this) {
            this.providerRestart = providerRestart;
        }
    }

    /*
    * ~end
    */

    void handleKeyDown(final int i, final int c) {
        if (!initialized) {
            return;
        }

        int mask = MASK_NONE;
        int action = 0;

        try {
            action = screen.getGameAction(i);
        } catch (IllegalArgumentException e) {
            // ignore
        }

        // hacks
        switch (i) {
//#ifdef __SYMBIAN__
            case Canvas.KEY_NUM0:
                if (cz.kruch.track.TrackingMIDlet.uiq) {
                    action = 0;
                }
            break;
//#endif
            case Canvas.KEY_NUM5:
                if (mode == VIEW_MAP) {
                    action = 0;
                }
            break;
        }

        final View[] views = this.views;
        if (views == null) return; // too early invocation
        final boolean repeated = c != 0;

        switch (action) {

            case Canvas.UP:
            case Canvas.LEFT:
            case Canvas.RIGHT:
            case Canvas.DOWN: {

                // handle action
                mask = views[mode].handleAction(action, repeated);

                // repetition
                if (repeated && mode == VIEW_MAP) {
                    screen.callSerially(Desktop.screen);
                } else {
                    screen.checkKeyRepeated(i);
                }

            } break;

            case Canvas.FIRE: {

                // handle action
                if (c == 0 || c == 1) {
                    mask = views[mode].handleAction(action, repeated);
                }

            } break;

            default: { // no game action

                switch (i) {

                    case Canvas.KEY_NUM0: { // day/night switch
                        if (c == 0) {
                            if (mode != VIEW_MAP) {
                                Config.dayNight++;
                                if (Config.dayNight == 2) {
                                    Config.dayNight = 0;
                                }
                                for (int j = views.length; --j >= 0; ) {
                                    final int m = views[j].changeDayNight(Config.dayNight);
                                    if (j == mode) { // current view
                                        mask |= m;
                                    }
                                }
                            }
                        } else if (c == 1) {
                            mask = views[mode].handleKey(i, repeated);
                        }
                    } break;

                    case Canvas.KEY_NUM1: { // navigation
                        if (c == 1) {
                            Waypoints.getInstance().show(1);
                        }
                    } break;

                    case Canvas.KEY_NUM3: { // notify device control
                        if (c == 1) {
                            cz.kruch.track.ui.nokia.DeviceControl.setBacklight();
                            mask = MASK_ALL;
                        }
                    } break;

                    default: {
                        if (c == 1) { // only first repeated passed along
                            mask = views[mode].handleKey(i, repeated);
                        }
                    }
                }
            }
        }

        // update
        update(mask);
    }

    void handleKeyUp(final int i) {
        if (!initialized) {
            return;
        }

        int mask = MASK_NONE;
        int action = 0;

        try {
            action = screen.getGameAction(i);
        } catch (IllegalArgumentException e) {
            // ignore
        }

        // hacks
        switch (i) {
//#ifdef __SYMBIAN__
            case Canvas.KEY_NUM0:
                if (cz.kruch.track.TrackingMIDlet.uiq) {
                    action = 0;
                }
            break;
//#endif
            case Canvas.KEY_NUM5:
                if (mode == VIEW_MAP) {
                    action = 0;
                }
            break;
        }

        // intercept MOB
        if (Config.mobEnabled) {
            if (i == Canvas.KEY_NUM5 || action == Canvas.FIRE) {
                final long now = System.currentTimeMillis();
                final long tdiff = now - lastKeyTime;
                lastKeyTime = now;
                if (tdiff < 350 && isTracking() && isLocation()) {
                    // record wpt and start navigation
                    Waypoints.getInstance().mob();
                    // done
                    return;
                }
            }
        }

        final View[] views = this.views;
        if (views == null) return; // too early invocation

        switch (action) {

            case Canvas.UP:
            case Canvas.LEFT:
            case Canvas.RIGHT:
            case Canvas.DOWN:
            case Canvas.FIRE:
                break;

            default: { // handle events common to all screens

                switch (i) {

                    case Canvas.KEY_POUND: { // change screen
                        views[mode++].setVisible(false);
                        if (mode >= views.length) {
                            mode = 0;
                        }
                        views[mode].setVisible(true);
                        mask = MASK_ALL;
                    } break;

                    case Canvas.KEY_NUM1: { // navigation
                        Waypoints.getInstance().showCurrent();
                    } break;

                    case Canvas.KEY_NUM3: { // notify user
                        cz.kruch.track.ui.nokia.DeviceControl.getBacklight();
                        mask = MASK_ALL;
                    } break;

//#ifdef __ANDROID__
                    case -27: { // camera key :)
                        Waypoints.getInstance().show(2);
                    } break;
//#endif

                    default: {
                        mask = views[mode].handleKey(i, false);
                    }
                }
            }
        }

        // update
        update(mask);
    }

    // TODO hacky!!!!
    void handleMove(int x, int y) {
        if (views == null) return; // too early invocation
        if (mode == VIEW_MAP) {
            Desktop.browsing = true;
            update(((MapView) views[mode]).moveTo(x, y));
        }
    }

    // TODO hacky!!!!
    void handleStall(int x, int y) {
        if (views == null) return; // too early invocation
        if (mode == VIEW_MAP) {
            update(((MapView) views[mode]).moveTo(-1, -1));
        }
    }

    void update(final int mask) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("update " + Integer.toBinaryString(mask) + "; state: " + cz.kruch.track.TrackingMIDlet.state);
//#endif

//#ifdef __ANDROID__
        if (cz.kruch.track.TrackingMIDlet.state != 1) {
            return;
        }
//#endif

        // anything to update?
        if (mask != MASK_NONE) {

            // notify map view that render event is about to happen...
            // so that it can start loading tiles asap...
            // TODO MapView specific and very ugly
            if ((mask & Desktop.MASK_MAP) != 0 && mode == VIEW_MAP) {
                synchronized (loadingLock) {
                    if (!initializingMap && !loadingSlices) {
                        try {
                            if (((MapView) views[VIEW_MAP]).prerender()) { // loading will start soon
                                return; 
                            }
                        } catch (Throwable t) { // should never happen
                            showError(null, t, null);
                            return;
                        }
                    }
                }
            }

            // call render task
            screen.callSerially(newRenderTask(mask));

        }
//#ifdef __LOG__
          else {
            if (log.isEnabled()) log.debug("update with mask 0");
        }
//#endif
    }

    static void restore(final Displayable displayable) {
        showNext(displayable, screen);
    }

    static void showNext(final Displayable displayable, final Displayable next) {
        displayable.setCommandListener(null);
        display.setCurrent(next);
    }

    public static void showWaitScreen(final String title, final String message) {
        final Form form = new Form(title);
        form.append(message);
        display.setCurrent(form);
    }

    public static void showConfirmation(final String message, final Displayable nextDisplayable) {
        showAlert(AlertType.CONFIRMATION, message, INFO_DIALOG_TIMEOUT, nextDisplayable);
    }

    public static void showAlarm(final String message, final Displayable nextDisplayable,
                                 final boolean forever) {
        vibrate(500);
        showAlert(AlertType.ALARM, message, forever ? Alert.FOREVER : ALARM_DIALOG_TIMEOUT, nextDisplayable);
    }

    public static void showInfo(final String message, final Displayable nextDisplayable) {
        showAlert(AlertType.INFO, message, INFO_DIALOG_TIMEOUT, nextDisplayable);
    }

    public static void showWarning(String message, final Throwable t, final Displayable nextDisplayable) {
        if (message == null) {
            message = "";
        }
        if (t != null) {
            final StringBuffer sb = new StringBuffer(message);
            if (sb.length() > 0) sb.append(' ');
            sb.append(t.toString());
            message = sb.toString();
        }
        showAlert(AlertType.WARNING, message, WARN_DIALOG_TIMEOUT, nextDisplayable);
    }

    public static void showError(String message, final Throwable t, final Displayable nextDisplayable) {
        if (message == null) {
            message = "";
        }
        if (t != null) {
//#ifdef __ANDROID__
            android.util.Log.e(TAG, message, t);
//#elifdef __SYMBIAN__
            System.err.println("[TrekBuddy] " + message);
            t.printStackTrace();
//#endif
            final StringBuffer sb = new StringBuffer(message);
            if (sb.length() > 0) sb.append(": ");
            sb.append(t.toString());
            if (t instanceof api.lang.RuntimeException) {
                if (((api.lang.RuntimeException) t).getCause() != null)
                sb.append(" Caused by: ").append(((api.lang.RuntimeException) t).getCause().toString());
            }
            message = sb.toString();
        }
        showAlert(AlertType.ERROR, message, Alert.FOREVER, nextDisplayable);
    }

    private static void showAlert(final AlertType type, final String message, final int timeout,
                                  final Displayable nextDisplayable) {
        Alert alert = new Alert(cz.kruch.track.TrackingMIDlet.APP_TITLE,
                                message, null, Config.noSounds ? null : type);
        alert.setTimeout(timeout);
        if (nextDisplayable == null) {
            display.setCurrent(alert);
        } else {
            if (display.getCurrent() instanceof Alert) {
                display.setCurrent(nextDisplayable); // this should cancel alert 
            }
            display.setCurrent(alert, nextDisplayable);
        }
    }

    private void preTracking(final boolean last) {

        // assertion - should never happen
        if (runtime.getProvider() != null) {
            throw new IllegalStateException("Tracking already started");
        }

        // by default
        tracklog = false;

        // start tracklog?
        switch (Config.tracklog) {
            case Config.TRACKLOG_NEVER: {
                startTrackingRuntime(last);
            } break;
            case Config.TRACKLOG_ASK: {
                (new YesNoDialog(screen, this, new Boolean(last), Resources.getString(Resources.DESKTOP_MSG_START_TRACKLOG), null)).show();
            } break;
            case Config.TRACKLOG_ALWAYS: {
                tracklog = true; // !
                startTrackingRuntime(last);
            } break;
        }

        // update OSD
        update(MASK_OSD);
    }

    private boolean startTracking() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("start tracking...");
//#endif

        // provider
        String providerName = null;
        LocationProvider provider;

        // instantiate provider
        try {
            Class providerClass = null;
            switch (Config.locationProvider) {
                case Config.LOCATION_PROVIDER_JSR82:
//#ifdef __ANDROID__
                    providerClass = Class.forName("cz.kruch.track.location.AndroidBluetoothLocationProvider");
//#else
                    providerClass = Class.forName("cz.kruch.track.location.Jsr82LocationProvider");
//#endif
                    providerName = "Bluetooth";
                break;
                case Config.LOCATION_PROVIDER_JSR179:
//#ifdef __ANDROID__
                    providerClass = Class.forName("cz.kruch.track.location.AndroidLocationProvider");
//#else
                    providerClass = Class.forName("cz.kruch.track.location.Jsr179LocationProvider");
//#endif
                    providerName = "Internal";
                break;
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
                case Config.LOCATION_PROVIDER_O2GERMANY:
                    providerClass = Class.forName("cz.kruch.track.location.O2GermanyLocationProvider");
                    providerName = "O2 Germany";
                break;
                case Config.LOCATION_PROVIDER_HGE100:
                    providerClass = Class.forName("cz.kruch.track.location.SerialLocationProvider");
                    providerName = "HGE-100";
                break;
//#endif
            }
            provider = (LocationProvider) providerClass.newInstance();
        } catch (Throwable t) {
            showError(Resources.getString(Resources.DESKTOP_MSG_CREATE_PROV_FAILED) + " [" + providerName + "]", t, screen);

            return false;
        }

        // register as listener
        provider.setLocationListener(this);

        // start provider
        final int state;
        try {
            state = runtime.startTracking(provider);
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("provider started; state " + state);
//#endif
        } catch (LocationException e) {

            // notify user
            showError(e.getMessage(), null, null);

			return false;

        } catch (Throwable t) {

            // notify user
            showError(Resources.getString(Resources.DESKTOP_MSG_START_PROV_FAILED) + " [" + provider.getName() + "]", t, null);

			return false;
        }

        // not browsing
        browsing = false;

        // update OSD
        osd.setProviderStatus(state);

        // update menu
        screen.removeCommand(cmdRun);
        screen.removeCommand(cmdRunLast);
        screen.addCommand(cmdStop);
        screen.addCommand(cmdPause);

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
        LocationProvider provider;
        try {
//#ifdef __ANDROID__
            provider = (LocationProvider) Class.forName("cz.kruch.track.location.AndroidBluetoothLocationProvider").newInstance();
//#else
            provider = (LocationProvider) Class.forName("cz.kruch.track.location.Jsr82LocationProvider").newInstance();
//#endif
        } catch (Throwable t) {
            showError(Resources.getString(Resources.DESKTOP_MSG_CREATE_PROV_FAILED) + " [Bluetooth]", t, screen);
            return false;
        }

        // update OSD
        osd.setProviderStatus(LocationProvider._STARTING);

        // register as listener
        provider.setLocationListener(this);

        // (re)start BT provider
        runtime.quickstartTracking(provider);

        // not browsing
        browsing = false;

        // update menu
        screen.removeCommand(cmdRun);
        screen.removeCommand(cmdRunLast);
        screen.addCommand(cmdStop);
        screen.addCommand(cmdPause);

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
        _setProviderRestart(true);

        // update OSD
        osd.setProviderStatus(LocationProvider._STARTING);

        // (re)start provider
        runtime.restartTracking();

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("~restart tracking");
//#endif

        return true;
    }

    private void afterTracking() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("after tracking");
//#endif

        // record provider status
        providerStatus = runtime.getProvider().getStatus();
        providerError = runtime.getProvider().getThrowable();

        // cleanup
        runtime.afterTracking();

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

        // notify navigation manager
        Waypoints.getInstance().trackingStopped();

        // notify views (if start was propagated too - this is hack)
        if (trackstart > 0) {
            final View[] views = this.views;
            for (int i = views.length; --i >= 0; ) {
                views[i].trackingStopped();
            }
//#ifdef __HECL__
            cz.kruch.track.hecl.PluginManager.getInstance().trackingStopped();
//#endif
        } else {
//#ifdef __LOG__
            log.warn("false stop tracking");
//#endif
        }

        // forget track start
        trackstart = 0;

        // request screen update
        update(MASK_OSD | MASK_CROSSHAIR);

        // update menu
        screen.removeCommand(cmdStop);
        screen.removeCommand(cmdPause);
        screen.removeCommand(cmdContinue);
        screen.addCommand(cmdRun);
        if (Config.locationProvider == Config.LOCATION_PROVIDER_JSR82) {
            screen.addCommand(cmdRunLast);
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("~after tracking");
//#endif
    }

    private boolean stopTracking() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("stop tracking");
//#endif

        // assertion - should never happen
        if (runtime.getProvider() == null) {
//            throw new IllegalStateException("Tracking already stopped");
//#ifdef __LOG__
            if (log.isEnabled()) log.error("tracking already stopped");
//#endif
            return false;
        }

        // already stopping?
        if (!runtime.getProvider().isGo()) {
//#ifdef __LOG__
            if (log.isEnabled()) log.error("tracking already stopping");
//#endif
            return false;
        }

        // stop provider
        try {
            runtime.stopTracking();
        } catch (Throwable t) {
            showError(Resources.getString(Resources.DESKTOP_MSG_STOP_PROV_FAILED), t, null);
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
            if (_isProviderRestart()) {
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

                    try {
                        // create file
                        tracklogNmea = File.open(Config.getFileURL(Config.FOLDER_NMEA, GpxTracklog.dateToFileDate(trackstart) + ".nmea"), Connector.READ_WRITE);
                        if (tracklogNmea.exists()) {
                            tracklogNmea.delete();
                        }
                        tracklogNmea.create();

                        // create output
                        nmeaOut = new BufferedOutputStream(tracklogNmea.openOutputStream(), 4096, true);

                        // inject observer
                        runtime.getProvider().setObserver(nmeaOut);

                        // notify itself ;-)
                        (new Event(Event.EVENT_TRACKLOG)).invoke(new Integer(GpxTracklog.CODE_RECORDING_START), null, this);

                    } catch (Exception e) {

                        // notify itself ;-)
                        (new Event(Event.EVENT_TRACKLOG)).invoke(null, e, this);

                    }
                }
            }
        }
    }

    private boolean startTrackingRuntime(boolean last) {
        final boolean started;
        if (last) {
            started = startTrackingLast();
        } else {
            started = startTracking();
        }
        if (started) {
//#ifdef __ANDROID__
//            cz.kruch.track.TrackingMIDlet.getActivity().startService(
//                    new Intent(cz.kruch.track.TrackingMIDlet.getActivity(), Runtime.class));
//#endif
        }
        return started;
    }

    private boolean stopTrackingRuntime() {
//#ifdef __ANDROID__
//        cz.kruch.track.TrackingMIDlet.getActivity().stopService(
//                new Intent(cz.kruch.track.TrackingMIDlet.getActivity(), Runtime.class));
//#endif
        return stopTracking();
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
        } else if (tracklogNmea != null) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("stopping NMEA tracklog");
//#endif
            File.closeQuietly(nmeaOut);
            nmeaOut = null;
            File.closeQuietly(tracklogNmea);
            tracklogNmea = null;
        }
    }

    static void drawPause(final Graphics g, final Displayable target, final String text) {
        final Font f = Font.getDefaultFont();
        final int sw = f.stringWidth(text);
        final int sh = f.getHeight();
        final int w = target.getWidth() * 6 / 8;
        final int h = sh << 1;
        final int x = (target.getWidth() - w) >> 1;
        final int y = (target.getHeight() - h) * 4 / 5;
        g.setColor(DeviceScreen.BTN_COLOR);
        g.fillRoundRect(x, y, w, h, DeviceScreen.BTN_ARC, DeviceScreen.BTN_ARC);
        g.setColor(DeviceScreen.BTN_HICOLOR);
        g.drawRoundRect(x, y, w, h, DeviceScreen.BTN_ARC, DeviceScreen.BTN_ARC);
        g.setColor(0x00ffffff);
        g.setFont(f);
        g.drawString(text, x + ((w - sw) >> 1), y + ((h - sh) >> 1), Graphics.TOP | Graphics.LEFT);
    }

    /*
     * Map.StateListener contract
     */

    public void mapOpened(final Object result, final Throwable throwable) {
//        eventing.callSerially(newEvent(Event.EVENT_MAP_OPENED,
//                                       result, throwable, null));
        getEventWorker().enqueue(newEvent(Event.EVENT_MAP_OPENED,
                                          result, throwable, null));
    }

    public void slicesLoading(final Object result, final Throwable throwable) {
        _setLoadingSlices(true);
        /* nothing else to do */
    }

    public void slicesLoaded(final Object result, final Throwable throwable) {
        _setLoadingSlices(false);
//        eventing.callSerially(newEvent(Event.EVENT_SLICES_LOADED,
//                                       result, throwable, null));
        getEventWorker().enqueue(newEvent(Event.EVENT_SLICES_LOADED,
                                          result, throwable, null));
    }

    public void loadingChanged(final Object result, final Throwable throwable) {
//        eventing.callSerially(newEvent(Event.EVENT_LOADING_STATUS_CHANGED,
//                                       result, throwable, null));
        getEventWorker().enqueue(newEvent(Event.EVENT_LOADING_STATUS_CHANGED,
                                          result, throwable, null));
    }

    /*
    * Map.StateListener contract
    */

    public void atlasOpened(final Object result, final Throwable throwable) {
//        eventing.callSerially(newEvent(Event.EVENT_ATLAS_OPENED,
//                                        result, throwable, null));
        getEventWorker().enqueue(newEvent(Event.EVENT_ATLAS_OPENED,
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

    private static final RenderTask[] rtPool = new RenderTask[16];
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

    private void releaseRenderTask(final RenderTask task) {
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
    public static final int MASK_ALL        = MASK_MAP | MASK_OSD | MASK_STATUS | MASK_CROSSHAIR;
    public static final int MASK_SCREEN     = MASK_ALL;

    static long skips;

    final class RenderTask implements Runnable {
        private int mask;

        public RenderTask(final int m) {
            this.mask = m;
        }

        public void merge(final RenderTask r) {
            this.mask |= r.mask;
            releaseRenderTask(r);
        }
        
        public void run() {
            // render
            try {
                // get graphics
                final Graphics g = Desktop.screen.getGraphics();

                // render current view
                synchronized (Desktop.renderLock) {
                    Desktop.this.views[mode].render(g, font, mask);
                }

                // paused?
                if (Desktop.paused) {
                    Desktop.drawPause(g, screen, Resources.getString(Resources.DESKTOP_MSG_PAUSED));
                }

                // flush offscreen buffer
                Desktop.screen.flushGraphics();
                
            } catch (Throwable t) {
//#ifdef __LOG__
                t.printStackTrace();
                if (Desktop.log.isEnabled()) Desktop.log.error("render failure", t);
//#endif
//#ifdef __ANDROID__
                android.util.Log.e(TAG, "render failure", t);
//#endif
                Desktop.showError("_RENDER FAILURE_", t, null);

            } finally {

                // release task
                releaseRenderTask(this);
            }

        }
    }

    // temps for atlas/map loading
    private volatile String _target;
    private volatile Map _map;
    private volatile Atlas _atlas;
    private volatile QualifiedCoordinates _qc; // WGS84
    private volatile boolean _switch;

    void changeLayer() {
        if (atlas != null) {
            final Enumeration e = atlas.getLayers();
            if (e.hasMoreElements()) {
                (new ItemSelection(screen, Resources.getString(Resources.DESKTOP_MSG_SELECT_LAYER),
                                   new Event(Event.EVENT_LAYER_SELECTION_FINISHED, "switch"))).show(e, atlas.getLayer());
            } else {
                showInfo(Resources.getString(Resources.DESKTOP_MSG_NO_LAYERS), screen);
            }
        }
    }

    void changeMap() {
        if (atlas != null) {
            final Enumeration e = atlas.getMapNames();
            if (e.hasMoreElements()) {
                (new ItemSelection(screen, Resources.getString(Resources.DESKTOP_MSG_SELECT_MAP),
                                   new Event(Event.EVENT_MAP_SELECTION_FINISHED, "switch"))).show(e, map.getName());
            } else {
                showInfo(Resources.getString(Resources.DESKTOP_MSG_NO_MAPS), screen);
            }
        }
    }

    void zoom(int direction) {
        if (atlas != null) {
            final Enumeration e = atlas.getLayers();
            if (e.hasMoreElements()) {
                final String[] layers = FileBrowser.sort2array(e, null, null);
                final String layer = atlas.getLayer();
                for (int N = layers.length, i = 0; i < N; i++) {
                    if (layer.equals(layers[i])) {
                        final QualifiedCoordinates qc = getPointer();
                        String nextLayer = null;
                        if (direction == 1 && (i + 1) < N) {
                            for (i = i + 1; i < N; i++) {
                                if (atlas.getFileURL(layers[i], qc) != null) {
                                    nextLayer = layers[i];
                                    break;
                                }
                            }
                        } else if (direction == -1 && i > 0) {
                            for (i = i - 1; i >= 0; i--) {
                                if (atlas.getFileURL(layers[i], qc) != null) {
                                    nextLayer = layers[i];
                                    break;
                                }
                            }
                        }
                        if (nextLayer != null) {
                            (new Event(Event.EVENT_LAYER_SELECTION_FINISHED, "switch")).invoke(nextLayer, null, this);
                        }
                        break;
                    }
                }
                // give feedback
                vibrate(50);
            }
        }
    }

    boolean startAlternateMap(final String layerName, final QualifiedCoordinates qc) {

        synchronized (loadingLock) {

            // already in progress check
            if (initializingMap) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("some alternate map being loaded");
//#endif
                return true;
            }

            // find map for given coords
            final String mapUrl = atlas.getFileURL(layerName, qc);
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
            }

            return mapUrl != null;

        } // ~synchronized
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
        if (Config.verboseLoading) {
            update(MASK_SCREEN);
        }

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

    private void startOpenAtlas(final String url, final String name) {
        // flag on
        _setInitializingMap(true);

        // message for the screen
        _updateLoadingResult(Resources.getString(Resources.DESKTOP_MSG_LOADING_ATLAS), url);

        // hide OSD
        osd.setVisible(false);

        // render screen
        update(MASK_SCREEN);

        // open atlas (in background)
        _atlas = new Atlas(url, name, this);
        _atlas.open();
    }

//#ifdef __B2B__

    private int imgcached, configured, resourced;

    private void b2b_startOpenGuide(final String url) {
        // set datadir
        Config.dataDir = url;
        // reset
        screen.removeCommand(cmdRun); cmdRun = null;
        screen.removeCommand(cmdRunLast); cmdRunLast = null;
        screen.removeCommand(cmdPause); cmdPause = null;
        screen.removeCommand(cmdContinue); cmdContinue = null;
        screen.removeCommand(cmdStop); cmdStop = null;
        screen.removeCommand(cmdWaypoints); cmdWaypoints = null;
        screen.removeCommand(cmdLoadGuide); cmdLoadGuide = null;
        screen.removeCommand(cmdSettings); cmdSettings = null;
        screen.removeCommand(cmdInfo); cmdInfo = null;
        screen.removeCommand(cmdExit); cmdExit = null;
//#ifndef __ANDROID__
        if (cmdHide != null) {
            screen.removeCommand(cmdHide); cmdHide = null;
        }
//#endif        
        // views reset
        ((ComputerView) views[VIEW_CMS]).b2b_reset(); // TODO ugly
        ((MapView) views[VIEW_MAP]).setMap(null);
        // release maps
        if (atlas != null) {
            atlas.close();
            atlas = null;
        }
        if (map != null) {
            map.close();
            map = null;
        }
        // reset checksum
        cz.kruch.track.io.CrcInputStream.doReset();
        // reboot
        boot(imgcached, configured, resourced, false);
        // common views setup
        View.b2b_init(); // TODO ugly
        // show view
        views[VIEW_MAP].setVisible(false);
        views[VIEW_HPS].setVisible(false);
        views[VIEW_CMS].setVisible(false);
        views[mode].setVisible(true);
        update(MASK_SCREEN);
    }

//#endif

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

    final class Event implements Runnable, Callback, YesNoDialog.AnswerListener {
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
        public static final int EVENT_ORIENTATION_CHANGED           = 11;

        private int code;
        private Object result;
        private Throwable throwable;
        private Object closure;

        private boolean release;

        public Event(final int code) {
            this.code = code;
            this.release = true;
        }

        public Event(final int code, final Object closure) {
            this(code);
            this.closure = closure;
        }

        public Event(final int code, final Object result,
                     final Throwable throwable, final Object closure) {
            this(code, closure);
            this.result = result;
            this.throwable = throwable;
        }

        public void invoke(final Object result, final Throwable throwable, final Object source) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("firing event " + this.toString());
//#endif
            // fill with results
            this.result = result;
            this.throwable = throwable;

            // direct invocation, do not release this instance
            this.release = false;

            // enqueu for execution
            Desktop.getEventWorker().enqueue(this);
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
                    if (Desktop.this.atlas == null) {
                        Config.mapURL = Desktop.this.map.getPath();
                    } else {
                        Config.mapURL = Desktop.this.atlas.getMapURL(Desktop.this.map.getPath(),
                                                                     Desktop.this.map.getName());
                    }
                    Config.update(Config.CONFIG_090);

                    // let the user know
                    showConfirmation(Resources.getString(Resources.DESKTOP_MSG_CFG_UPDATED), Desktop.screen);

                } catch (ConfigurationException e) {

                    // show user the error
                    showError(Resources.getString(Resources.DESKTOP_MSG_CFG_UPDATED), e, Desktop.screen);
                }
            }

            // update anyway
            update(MASK_SCREEN);
        }

        /** fail-safe */
        public void run() {
//#ifdef __LOG__
            if (throwable != null) {
                cz.kruch.track.util.Logger.out("*event throwable*");
                cz.kruch.track.util.Logger.printStackTrace(throwable);
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
                    case EVENT_ORIENTATION_CHANGED: {
                        execOrientationChanged();
                    } break;
                    default:
                        throw new IllegalArgumentException("Unknown event " + code);
                }

//#ifdef __LOG__
                if (log.isEnabled()) log.debug("~event run; " + this);
//#endif
            } catch (Throwable t) {
//#ifdef __LOG__
                t.printStackTrace();
                if (log.isEnabled()) log.debug("event failure", t);
//#endif
//#ifdef __ANDROID__
                android.util.Log.e(TAG, "event failure", t);
//#endif                
                Desktop.showError("_EVENT FAILURE_ (" + this + ")", t, Desktop.screen);

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
            synchronized (Desktop.renderLock) {
                resetFont();
                resetBar();
            }

//#ifdef __ANDROID__
            cz.kruch.track.TrackingMIDlet.getActivity().config.ignoreVolumeKeys = !Config.easyZoomVolumeKeys;
            cz.kruch.track.TrackingMIDlet.getActivity().config.ignoreTextFieldFocus = !Config.forceTextFieldFocus;
//#endif

            // runtime ops
            if (Desktop.this.friends != null) {
                Desktop.this.friends.reconfigure(Desktop.screen);
            }

            // smart menu
            if (Desktop.this.cmdRunLast != null && !isTracking()) {
                if (Config.locationProvider == Config.LOCATION_PROVIDER_JSR82) {
                    Desktop.screen.addCommand(Desktop.this.cmdRunLast);
                } else {
                    Desktop.screen.removeCommand(Desktop.this.cmdRunLast);
                }
            }

            // notify views
            final View[] views = Desktop.this.views;
            synchronized (Desktop.renderLock) {
                for (int i = views.length; --i >= 0; ) {
                    try {
                        views[i].configChanged();
                    } catch (Exception e) {
//#ifdef __LOG__
                        e.printStackTrace();
//#endif
                        throw new api.lang.RuntimeException("Error in [config changed] in view #" + i, e);
                    }
                }
            } // ~synchronized

            // update screen
            Desktop.this.update(MASK_ALL);

        }

        private void execFileBrowserFinished() {

            // had user selected anything?
            if (result != null) {

                // user intention to load map or atlas
                Desktop.this._switch = false;

                // cast to file connection
                final api.file.File file = (api.file.File) result;
                final String name = file.getName();
                final String url = file.getURL();

                // close file connection
                File.closeQuietly(file);

                // to recover position when new map loaded
                if (Desktop.this.map != null) {
                    Desktop.this._qc = getPointer();
                }

                synchronized (Desktop.renderLock) {
                    // hide map viewer and OSD // TODO hackish, and conflicts with _qc just above
                    ((MapView) views[VIEW_MAP]).setMap(null);
                } // ~synchronized

                // release current data
                if (Desktop.this.atlas != null) {
                    Desktop.this.atlas.close();
                    Desktop.this.atlas = null;
                }
                if (Desktop.this.map != null) {
                    Desktop.this.map.close();
                    Desktop.this.map = null;
                }

//#ifdef __BUILDER__
                // reset checksum
                cz.kruch.track.io.CrcInputStream.doReset();
//#endif
                
                // load map or atlas
//                if ("atlas".equals(closure)) {
//                    Desktop.this._target = "atlas";
//                    Desktop.this.startOpenAtlas(url, name);
//                } else if ("map".equals(closure)) {
//                    Desktop.this._target = "map";
//                    Desktop.this.startOpenMap(url, name);
                if ("map/atlas".equals(closure)) {
                    final String nameLc = name.toLowerCase();
                    if (nameLc.endsWith(".tba")
                            || nameLc.endsWith(".tar")
                            || nameLc.endsWith(".idx")
                            || nameLc.endsWith(".xml")) {
                        Desktop.this._target = "atlas";
                        Desktop.this.startOpenAtlas(url, name);
                    } else {
                        Desktop.this._target = "map";
                        Desktop.this.startOpenMap(url, name);
                    }
                }
//#ifdef __B2B__
                  else if ("guide".equals(closure)) {
                    Desktop.this._target = "guide";
                    Desktop.this.b2b_startOpenGuide(url);
                }
//#endif
            } else if (throwable != null) {
                Desktop.showError("[1]", throwable, Desktop.screen);
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
                Desktop.this.tracklogError = throwable;

                // display warning
                Desktop.showWarning(result == null ? Resources.getString(Resources.DESKTOP_MSG_TRACKLOG_ERROR) : result.toString(),
                                    throwable, Desktop.screen);

                // no more recording
                Desktop.osd.setRecording(false);
            }

            // update screen
            Desktop.this.update(MASK_OSD);

        }

        private void execAtlasOpened() {

            // if opening ok
            if (throwable == null) {

                // use new atlas
                Desktop.this.atlas = Desktop.this._atlas;
                Desktop.this._atlas = null;

                // force user to select layer
                (new ItemSelection(Desktop.screen, Resources.getString(Resources.DESKTOP_MSG_SELECT_LAYER),
                                   new Event(Event.EVENT_LAYER_SELECTION_FINISHED))).show(Desktop.this.atlas.getLayers(), null);

            } else {

                // HACK
                if (throwable instanceof cz.kruch.track.maps.InvalidMapException
                        && "ATLAS->MAP".equals(throwable.getMessage())) {
                    final String url = Desktop.this._atlas.getURL();
                    final String name = Desktop.this._atlas.getName();
                    Desktop.this._atlas.close();
                    Desktop.this._atlas = null;
                    Desktop.this._target = "map";
                    Desktop.this.startOpenMap(url, name);
                    return;
                }

                // show a user error
                Desktop.showError(nullToString("[3] ", result), throwable, Desktop.screen);

                // cleanup
                cleanup(throwable);
            }

        }

        private void execLayerSelectionFinished() {

            // layer switch with '7'
            Desktop.this._switch = "switch".equals(closure);

            // had user selected anything?
            if (result != null) {

                // layer name
                final String layerName = (String) result;

                // has layer changed?
                if (!layerName.equals(Desktop.this.atlas.getLayer())) {

                    // from load task
                    if (!Desktop.this._switch) {

                        // setup atlas
                        Desktop.this.atlas.setLayer(layerName);

                        // force user to select default map
                        (new ItemSelection(Desktop.screen, Resources.getString(Resources.DESKTOP_MSG_SELECT_MAP),
                                           new Event(Event.EVENT_MAP_SELECTION_FINISHED))).show(Desktop.this.atlas.getMapNames(), null);

                    } else { // layer switch

                        // switch match
                        if (!Desktop.this.startAlternateMap(layerName, getPointer())) {
                            // let user to select any map
                            final Displayable next = (new ItemSelection(Desktop.screen, Resources.getString(Resources.DESKTOP_MSG_SELECT_MAP),
                                                                        new Event(Event.EVENT_MAP_SELECTION_FINISHED, layerName))).show(Desktop.this.atlas.getMapNames(layerName), null);
                            // warn user (in overlay dialog??)
                            showWarning(Resources.getString(Resources.DESKTOP_MSG_NO_MAP_FOR_POS) + " '" + layerName + "'.",
                                        null, next);                            
                        }
                    }
                }
            } else { // cancelled

                // cleanup 
                cleanup(null);

            }

        }

        private void execMapSelectionFinished() {

            // map switch with '9'
            Desktop.this._switch = "switch".equals(closure);

            // had user selected anything?
            if (result != null) {

                // trick - focus on these coords once the new map is loaded
                if (Desktop.this.map != null) {
                    Desktop.this._qc = getPointer();
                }

                // map name
                final String name = (String) result;

                // phantom layer
                if (!Desktop.this._switch && closure != null && Desktop.this.atlas != null) {
                    Desktop.this.atlas.setLayer((String) closure);
                    Desktop.this._switch = true;
                }

                // background task
                Desktop.this.startOpenMap(Desktop.this.atlas.getFileURL(name), name);

            } else { // cancelled

                // cleanup
                cleanup(null);

            }

        }

        private void execMapOpened() {

            // opening was ok
            if (throwable == null) {

                try {
                    // destroy existing map definitely if it is standalone
                    if (Desktop.this.atlas == null && Desktop.this.map != null) {
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("definitely destroy map " + Desktop.this.map.getPath());
//#endif
                        Desktop.this.map.close();
                        Desktop.this.map = null; // gc hint
                    }

                    // use new map
                    Desktop.this.map = Desktop.this._map;
                    Desktop.this._map = null;

                    // cache map
                    if (Desktop.this.atlas != null && Desktop.this.map != null) {
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("caching map " + Desktop.this.map.getPath());
//#endif
                        Desktop.this.atlas.getMaps().put(Desktop.this.map.getPath(), Desktop.this.map);
                    }

                    // setup map viewer
                    final MapView mapView = ((MapView) Desktop.this.views[VIEW_MAP]);
                    synchronized (Desktop.renderLock) {
                        mapView.setMap(Desktop.this.map);
                    }

                    // enable OSD
                    osd.setVisible(true);

                    // move viewer to known position, if any
                    if (Desktop.this._qc != null) {
                        try {
                            QualifiedCoordinates _qc = Desktop.this._qc;
                            Map map = Desktop.this.map;
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
                                synchronized (Desktop.renderLock) {
                                    mapView.setPosition(map.transform(_qc));
                                }
                            }

                        } finally {
                            Desktop.this._qc = null;
                        }
                    }

                    // TODO ugly code begins ---

                    // update OSD & navigation UI
                    synchronized (Desktop.renderLock) {
                        QualifiedCoordinates qc = Desktop.this.map.transform(mapView.getPosition());
                        MapView.setBasicOSD(qc, true);
                        Desktop.this.updateNavigation(qc);
                        QualifiedCoordinates.releaseInstance(qc);
                        qc = null; // gc hint
                        mapView.updateNavigationInfo(); // TODO ugly
                    }

                    // TODO -- ugly code ends

                    // map is ready
                    Desktop.this._setInitializingMap(false);

                    // render screen - it will force slices loading
                    Desktop.this.update(MASK_SCREEN);

                    // offer use as default?
                    if (!Desktop.this._switch && !Config.noQuestions) {
                        final YesNoDialog dialog;
                        if ("atlas".equals(Desktop.this._target)) {
                            dialog = new YesNoDialog(Desktop.screen, this, null, Resources.getString(Resources.DESKTOP_MSG_USE_AS_DEFAULT_ATLAS),
                                                     Desktop.this.atlas.getURL());
                        } else {
                            dialog = new YesNoDialog(Desktop.screen, this, null, Resources.getString(Resources.DESKTOP_MSG_USE_AS_DEFAULT_MAP),
                                                     Desktop.this.map.getPath());
                        }
                        dialog.show();
                    }
                } catch (Throwable t) {
//#ifdef __LOG__
                    t.printStackTrace();
//#endif

                    // show user the error
                    Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_USE_MAP_FAILED), t, Desktop.screen);

                    // cleanup
                    cleanup(t);

                }
                
            } else {

                // update loading result
                Desktop.this._updateLoadingResult(Resources.getString(Resources.DESKTOP_MSG_LOAD_MAP_FAILED), throwable);

                // show user the error
                Desktop.showError(nullToString("[6] ", result), throwable, Desktop.screen);

                // cleanup
                cleanup(throwable);

            }

        }

        // TODO this is quite wasting event
        private void execSlicesLoaded() {

            // update loading result
            Desktop.this._updateLoadingResult(Resources.getString(Resources.DESKTOP_MSG_SLICES_LOADED), throwable);

            // if loading was ok
            if (throwable == null) {

                // update screen
                Desktop.this.update(MASK_ALL);

            } else {

                // show user the error
                Desktop.showError(nullToString("[7] ", result), throwable, Desktop.screen);
            }

        }

        private void execLoadingStatusChanged() {

            // update loading result
            Desktop.this._updateLoadingResult(Resources.getString(Resources.DESKTOP_MSG_LOADING_STATUS), throwable);

            // loading ok?
            if (throwable == null) {

                // update status
                Desktop.status.setStatus((String) result);

                // status update
                Desktop.this.update(MASK_ALL);

            } else {

                // show user the error
                Desktop.showError(nullToString("[8] ", result), throwable, Desktop.screen);
            }

        }

        private void execTrackingStatusChanged() {

            // grab event data
            final int newState = ((Integer) result).intValue();

            // TODO keep state somewhere else
            Desktop.osd.setProviderStatus(newState);

            // how severe is the change
            switch (newState) {

                case LocationProvider._STARTING: {

					// remember track start
					Desktop.this.trackstart = System.currentTimeMillis();

                    // reset trip stats
                    TripStatistics.reset();

                    // start tracklog
                    Desktop.this.startTracklog();

                    // reset views on fresh start
                    if (!Desktop.this._isProviderRestart()) {
                        final View[] views = Desktop.this.views;
                        synchronized (Desktop.renderLock) {
                            for (int i = views.length; --i >= 0; ) {
                                views[i].trackingStarted();
                            }
                        }
//#ifdef __HECL__
                        cz.kruch.track.hecl.PluginManager.getInstance().trackingStarted();
//#endif
                    }

                    // clear restart flag
                    Desktop.this._setProviderRestart(false);

                } break;

                case LocationProvider.AVAILABLE: {

                    // beep
                    if (!Config.noSounds) {
                        AlertType.INFO.playSound(Desktop.display);
                    }

                } break;

                case LocationProvider.TEMPORARILY_UNAVAILABLE: {

                    // beep
                    if (!Config.noSounds) {
                        AlertType.WARNING.playSound(Desktop.display);
                    }

                } break;

                case LocationProvider.OUT_OF_SERVICE: {

                    // alarm
                    if (!Config.noSounds) {
                        AlertType.ALARM.playSound(Desktop.display);
                    }

                    // stop tracking completely or restart
                    if (Desktop.this._isStopRequest() || Desktop.this.runtime.getProvider() == null) {
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("to do: after tracking");
//#endif
                        Desktop.this.afterTracking();
                    } else if (Desktop.this.runtime.getProvider().isRestartable()) {
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("to do: restart tracking");
//#endif
                        Desktop.this.restartTracking();
                    } else {
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("to do: stop tracking");
//#endif
                        Desktop.this.stopTrackingRuntime();
                        Desktop.this.afterTracking();
                    }

                } break;

                case LocationProvider._STALLED: {

                    // beep
                    if (!Config.noSounds) {
                        AlertType.WARNING.playSound(Desktop.display);
                    }

                    // stop provider - if it is restartable, it will be restarted (see above case)
                    try {
                        Desktop.this.stopTrackingRuntime();
                    } catch (Exception e) {
//#ifdef __LOG__
                        e.printStackTrace();
//#endif
                        // ignore - never happens?
                    }

                } break;

                case LocationProvider._CANCELLED: {

                    // stop and resume
                    Desktop.this.stopTrackingRuntime();
                    Desktop.this.afterTracking();

                } break;
            }

            // update screen
            Desktop.this.update(MASK_MAP | MASK_OSD);

        }

        private void execTrackingPositionUpdated() {

            // paused?
            if (Desktop.paused) {
                return;
            }

            // grab event data
            final Location l = (Location) result;
            if (l == null) {
                throw new IllegalStateException("Location is null");
            }

            // extra validation
            l.validateEx();

            // update tracklog
            if (Desktop.this.tracklogGpx != null) {
                Desktop.this.tracklogGpx.locationUpdated(l);
            }

            // update mask
            int mask = MASK_NONE;

            // if valid position do updates
            if (l.getFix() > 0) {

                // update trip stats
                TripStatistics.locationUpdated(l);

                // TODO ugly big lock
                synchronized (Desktop.renderLock) {

                    // update wpt navigation
                    try {
                        Desktop.this.updateNavigation(l.getQualifiedCoordinates());
                    } catch (Exception e) {
//#ifdef __LOG__
                        e.printStackTrace();
//#endif
                        throw new api.lang.RuntimeException("Error in [navigation update]", e);
                    }

                    // update route navigation
                    try {
                        Desktop.this.updateRouting(l.getQualifiedCoordinates());
                    } catch (Exception e) {
//#ifdef __LOG__
                        e.printStackTrace();
//#endif
                        throw new api.lang.RuntimeException("Error in [routing update]", e);
                    }
                }

                // notify views
                final View[] views = Desktop.this.views;
                for (int i = views.length; --i >= 0; ) {
                    try {
                        final int m = views[i].locationUpdated(l);
                        if (i == mode) { // current view
                            mask |= m;
                        }
                    } catch (Exception e) {
//#ifdef __LOG__
                        e.printStackTrace();
//#endif
                        throw new api.lang.RuntimeException("Error in [location updated] in view #" + i, e);
                    }
                }
            } // ~synchronized

//#ifdef __HECL__
            cz.kruch.track.hecl.PluginManager.getInstance().locationUpdated(l);
//#endif
            
            // release instance
            Location.releaseInstance(l);

            // update screen
            Desktop.this.update(mask);
        }

        private void execOrientationChanged() {

            // grab event data
            final int heading = ((Integer) result).intValue();
            
            // notify views
            int mask = MASK_NONE;
            final View[] views = Desktop.this.views;
            synchronized (Desktop.renderLock) {
                for (int i = views.length; --i >= 0; ) {
                    try {
                        final int m = views[i].orientationChanged(heading);
                        if (i == mode) { // current view
                            mask |= m;
                        }
                    } catch (Exception e) {
    //#ifdef __LOG__
                        e.printStackTrace();
    //#endif
                        throw new api.lang.RuntimeException("Error in [orientation changed] in view #" + i, e);
                    }
                }
            } // ~synchronized

            // update screen
            Desktop.this.update(mask);
        }

        private void cleanup(final Throwable unused) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("cleanup; " + unused);
//#endif

            // update loading result
            final String msg = Resources.getString(Resources.DESKTOP_MSG_EVENT_CLENAUP);
            final int i = msg.indexOf('\n');
            if (i > /* -1 */ 0) {
                Desktop.this._updateLoadingResult(msg.substring(0, i), msg.substring(i + 1));
            } else {
                Desktop.this._updateLoadingResult(msg, (String) null);
            }

            // clear temporary vars
            if (Desktop.this._atlas != null) {
                Desktop.this._atlas.close();
                Desktop.this._atlas = null;
            }
            if (Desktop.this._map != null) {
                Desktop.this._map.close();
                Desktop.this._map = null;
            }

            // show hint
            Desktop.this.update(MASK_SCREEN);
        }

        // debug
        public String toString() {
            return "code " + code + ";result '" + result + "';throwable " + throwable;
        }
        // ~debug

        private String nullToString(final String title, final Object var) {
            return var == null ? title : title + var;
        }
    }

    /*
     * Boot console // TODO make it a View... ?!? move to DeviceScreen... ?!?
     */

    // vars
    private static short consoleErrors, consoleSkips;
    private static boolean consoleLogo;

    private static void consoleInit(final Graphics g) {
        g.setColor(0x0);
        g.fillRect(0, 0, screen.getWidth(), screen.getHeight());
        if (cz.kruch.track.configuration.Config.dataDirExists) {
            try {
                final Image logo = NavigationScreens.loadImage(Config.FOLDER_RESOURCES, "logo.png");
                if (logo != null) {
                    consoleLogo = true;
                    final int x = (screen.getWidth() - logo.getWidth()) >> 1;
                    final int y = (screen.getHeight() - logo.getHeight()) >> 1;
                    g.drawImage(logo, x, y, Graphics.TOP | Graphics.LEFT);
                }
            } catch (Throwable t) {
                // ignore
            }
        }
        screen.flushGraphics();
    }

    private static void consoleShow(final Graphics g, final int y, final String text) {
        if (!consoleLogo) {
            if (text != null) {
                g.setColor(0x00FFFFFF);
                g.drawString(text, 2, y, Graphics.TOP | Graphics.LEFT);
                screen.flushGraphics();
            }
        } 
    }

    private static void consoleResult(final Graphics g, final int y, final int code) {
        if (!consoleLogo) {
            final int x = screen.getWidth() - 2 - g.getFont().charWidth('*');
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
            screen.flushGraphics();
        }
    }

    private static void consoleDelay(final long tStart) {
        final long delay;
        if (!consoleLogo) {
            delay = consoleErrors > 0 ? 750 : (consoleSkips > 0 ? 250 : 0);
        } else {
            delay = 3000 - (System.currentTimeMillis() - tStart);
        }
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    /// ~ CONSOLE

}
