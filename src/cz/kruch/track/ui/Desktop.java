// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.maps.Map;
import cz.kruch.track.configuration.Config;
import cz.kruch.track.configuration.ConfigurationException;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.game.GameCanvas;
import java.io.IOException;

/**
 * Application desktop.
 */
public class Desktop extends GameCanvas implements Runnable, CommandListener {
    // component name for logging
    private static final String COMPONENT_NAME = "Desktop";

    // app title, for dialogs etc
    public static final String APP_TITLE = "TrekBuddy";

    // 'no map loaded' message header
    private static final String MSG_NO_MAP = "No map loaded. ";

    // 1 sec timeout
    private static final int INFO_DIALOG_TIMEOUT = 1000;

    // display
    private Display display;

    // desktop components
    private MapViewer mapViewer;
    private Bar osd;
    private Bar status;

    // data components
    private Map map;

    // LSM/MSK commands
    private Command cmdInfo;
    private Command cmdSettings;
    private Command cmdLoadBook;
//    private Command cmdLoadWaypoints;
    private Command cmdFocus;
    // RSK commands
    private Command cmdOSD;

    // OSD on/off
    private boolean osdVisible = true;

    // for faster movement
    private int scrolls = 0;

    // map loading state and result of last operation
    private boolean mapLoading = false;
    private String mapLoadingResult;

    public Desktop(Display display) {
        super(false);
        this.display = display;

        // event setup
        Event.desktop = this;

        // adjust appearance
        this.setFullScreenMode(Config.getSafeInstance().isFullscreen());
        this.setTitle(APP_TITLE);

        // create and add commands to the screen
        this.cmdFocus = new Command("Focus", Command.SCREEN, 1);
        this.cmdOSD = new Command("OSD", Command.BACK, 1);
        this.cmdInfo = new Command("Info", Command.SCREEN, 2);
        this.cmdSettings = new Command("Settings", Command.SCREEN, 2);
        this.cmdLoadBook = new Command("Load Maps", Command.SCREEN, 2);
//        this.cmdLoadWaypoints = new Command("Load Waypoints", Command.SCREEN, 2);
        this.addCommand(cmdFocus);
        this.addCommand(cmdOSD);
        this.addCommand(cmdInfo);
        this.addCommand(cmdSettings);
        this.addCommand(cmdLoadBook);
//        this.addCommand(cmdLoadWaypoints);

        // handle comamnds
        this.setCommandListener(this);
    }

    public void initGui() throws ConfigurationException, IOException {
        // clear map area with black
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
            mapViewer.ensureSlices();
        }
    }

    public void initMap() throws ConfigurationException, IOException {
        try {
            map = new Map(Config.getInstance().getMapPath());
        } catch (IOException e) {
            updateBookLoadinResult(e);
            throw e;
        } catch (ConfigurationException e) {
            updateBookLoadinResult(e);
            throw e;
        }
    }

    /**
     * Destroys desktop.
     */
    public void destroy() {
        // log
        System.out.println(COMPONENT_NAME + " [info] destroy");

        // close map
        map.close();
    }

    protected void keyPressed(int i) {
        handleKey(i, false);
    }

    protected void keyRepeated(int i) {
        handleKey(i, true);
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command == cmdOSD) {
            if (!isMap()) {
                showWarning(display, mapLoadingResult);
            } else {
                osdVisible = !osdVisible;
                renderScreen(true, true);
            }
        } else if (command == cmdFocus) {
            if (!isMap()) {
                showWarning(display, mapLoadingResult);
            }
        } else if (command == cmdInfo) {
            (new InfoForm(display)).show();
        } else if (command == cmdSettings) {
            (new SettingsForm()).show();
        } else if (command == cmdLoadBook) {
            (new FileBrowser()).browse();
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
            if (!mapLoading && mapViewer.scroll(action)) {
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
                mapLoading = mapViewer.ensureSlices();
                if (!mapLoading) {
                    renderScreen(true, true);
                }
            }

            // repeat
            display.callSerially(this);

        } else {
            // scrolling stop
            scrolls = 0;
        }
    }

    private void handleKey(int i, boolean repeated) {
        int action = getGameAction(i);
        switch (action) {
            case Canvas.DOWN:
            case Canvas.UP:
            case Canvas.LEFT:
            case Canvas.RIGHT:
//                System.out.println("handled action " + action + ", repeated? " + repeated);
                if (mapViewer == null) {
                    showWarning(display, mapLoadingResult);
                } else {
//                    if (repeated) {
                        display.callSerially(this);
//                    } else if (mapViewer.scroll(action)) {
//                        mapViewer.ensureSlices();
//                        renderScreen(true, true);
//                    }
                }
                break;
            default:
                System.out.println(COMPONENT_NAME + " [debug] unhandled key " + getKeyName(i));
        }
    }

    // TODO:
    // improve
    // get rid of params (is possible)
    // optimize flush graphics (clip only) when only OSD or status has changed
    private void renderScreen(boolean deep, boolean flush) {
        if (isMap()) {
            Graphics g = getGraphics();
            if (deep) {
                mapViewer.render(g);
            }
            if (osd != null) {
                if (osdVisible) {
                    osd.setInfo(map.getCalibration().transform(mapViewer.getPosition()).toString());  // TODO listener
                    osd.render(g);
                }
            }
            if (status != null) {
                status.render(g);
            }
        }

        if (flush) {
            flushGraphics();
        }
    }

    private boolean isMap() {
        return mapViewer == null ? false : true;
    }

    public static void showInfo(Display display, String message) {
        Alert alert = new Alert(APP_TITLE, message + ".", null, AlertType.INFO);
        alert.setTimeout(INFO_DIALOG_TIMEOUT);
        display.setCurrent(alert);
    }

    public static void showWarning(Display display, String message) {
        Alert alert = new Alert(APP_TITLE, message + ".", null, AlertType.WARNING);
        alert.setTimeout(Alert.FOREVER);
        display.setCurrent(alert);
    }

    public static void showError(Display display, String message, Throwable t) {
        Alert alert = new Alert(APP_TITLE, message + ". " + t.toString(), null, AlertType.ERROR);
        alert.setTimeout(Alert.FOREVER);
        display.setCurrent(alert);
    }

    /**
     * For external events.
     */
    public static class Event implements Runnable {
        public static final int EVENT_CONFIGURATION_CHANGED = 0;
        public static final int EVENT_FILE_BROWSER_FINISHED = 1;
        public static final int EVENT_BOOK_STATUS_CHANGED   = 2;
        public static final int EVENT_BOOK_LOADED   = 3;

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
            desktop.display.callSerially(this);
        }

        public static Display getDisplay() {
            return desktop.display;
        }

        public void run() {
            System.out.println(COMPONENT_NAME + " [debug] event " + code + ";result '" + result + "';throwable " + throwable);

            switch (code) {
                case EVENT_CONFIGURATION_CHANGED: {
                    // TODO
                } break;

                case EVENT_FILE_BROWSER_FINISHED: {
                    // restore desktop
                    desktop.display.setCurrent(desktop);
                    // TODO
                } break;

                case EVENT_BOOK_STATUS_CHANGED: {
                    // update status
                    desktop.status.setInfo((String) result);

                    // render if shown
                    if (desktop.isShown()) {
                        // when we are loading map or map status in null, do not do deep render
                        desktop.renderScreen(desktop.mapLoading ? false : result == null, true);
                        desktop.serviceRepaints();
                    }
                } break;

                case EVENT_BOOK_LOADED: {
                    // clear flag and save result
                    desktop.mapLoading = false;
                    desktop.updateBookLoadinResult(throwable);

                    // update screen
                    if (throwable == null) {
                        desktop.renderScreen(true, true);
                    }
                } break;
            }
        }
    }

    private void updateBookLoadinResult(Throwable t) {
        if (t == null) {
            mapLoadingResult = null;
        } else {
            mapLoadingResult = MSG_NO_MAP + t.toString();
        }
    }
}
