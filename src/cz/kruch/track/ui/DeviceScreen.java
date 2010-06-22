// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.Resources;
import cz.kruch.track.configuration.Config;

import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.midlet.MIDlet;

import java.util.TimerTask;

/**
 * Graphic output and user interaction.
 *
 * @author kruhc@seznam.cz
 */
final class DeviceScreen extends GameCanvas implements Runnable {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("DeviceScreen");
//#endif

    static final int BTN_ARC        = 10;
    static final int BTN_COLOR      = 0x005b87ce;
    static final int BTN_HICOLOR    = 0x000a2468;

    // main application
    private final Desktop delegate;

    // behaviour
    private int fullScreenHeight;
    private boolean hasRepeatEvents;

    // graphics
    private Graphics graphics;

    // eventing
    private SmartRunnable eventing;

    // keylock status
    private volatile int keyRepeatedCount;
    private volatile boolean keylock;

    // key repeating simulation support
    private /*volatile*/ TimerTask repeatedKeyCheck;
    private /*volatile*/ int inKey; // using synchronized access helper

    // touch ops
    private volatile boolean touchMenuActive, cmdExec;

    // movement filter
    private int gx, gy;
    private boolean inMove;

    // status
    private boolean active;

    public DeviceScreen(Desktop delegate, MIDlet midlet) {
        super(false);
        this.delegate = delegate;
        this.eventing = new SmartRunnable();
        if (midlet.getAppProperty(cz.kruch.track.TrackingMIDlet.JAD_UI_FULL_SCREEN_HEIGHT) != null) {
            this.fullScreenHeight = Integer.parseInt(midlet.getAppProperty(cz.kruch.track.TrackingMIDlet.JAD_UI_FULL_SCREEN_HEIGHT));
        }
        if (midlet.getAppProperty(cz.kruch.track.TrackingMIDlet.JAD_UI_HAS_REPEAT_EVENTS) != null) {
            this.hasRepeatEvents = "true".equals(midlet.getAppProperty(cz.kruch.track.TrackingMIDlet.JAD_UI_HAS_REPEAT_EVENTS));
        } else {
            this.hasRepeatEvents = super.hasRepeatEvents();
        }
    }

    /** @Override */
    protected void hideNotify() {
        eventing.clear();
        eventing.setActive(active = false);
    }

    /** @Override */
    protected void showNotify() {
        eventing.setActive(active = true);
    }

    /** @Override to make <code>Graphics</code> publicly accessible and handle weird states */
    public Graphics getGraphics() {
        if (graphics == null || cz.kruch.track.TrackingMIDlet.s65) {
            graphics = null;
            graphics = super.getGraphics();
        }
        return graphics;
    }

    /** @Override for touch menu hook */
    public void flushGraphics() {
        if (touchMenuActive) {
            drawTouchMenu();
        }
        super.flushGraphics();
    }

    /** @Override for broken device handling */
    public int getHeight() {
        if (fullScreenHeight == 0 || !Config.fullscreen) {
            return super.getHeight();
        }

        return fullScreenHeight;
    }

    /** @Override for broken device handling */
    public boolean hasRepeatEvents() {
        return hasRepeatEvents;
    }

    /** @Override */
    public void setCommandListener(CommandListener commandListener) {
        if (!Config.uiNoCommands) {
            super.setCommandListener(commandListener);
        }
    }

    /** @Override */
    public void addCommand(Command command) {
        if (!Config.uiNoCommands) {
            if (command != null) {
                super.addCommand(command);
            }
        }
    }

    /** @Override */
    public void removeCommand(Command command) {
        if (!Config.uiNoCommands) {
            if (command != null) {
                super.removeCommand(command);
            }
        }
    }

    /**
     * Used for key repetition emulation
     */
    public void run() {
/*
        int key = 0;

//#ifndef __RIM__
        int keyState = getKeyStates();

        if ((keyState & GameCanvas.LEFT_PRESSED) != 0) {
            key = Canvas.KEY_NUM4;
        } else if ((keyState & GameCanvas.RIGHT_PRESSED) != 0) {
            key = Canvas.KEY_NUM6;
        } else if ((keyState & GameCanvas.UP_PRESSED) != 0) {
            key = Canvas.KEY_NUM2;
        } else if ((keyState & GameCanvas.DOWN_PRESSED) != 0) {
            key = Canvas.KEY_NUM8;
        }
//#endif

        // dumb device without getKeyStates() support?
        if (key == 0) {
            key = _getInKey();
        }
*/
        // shortcut
        final int key = _getInKey();
        
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

    protected void sizeChanged(int w, int h) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("size changed: " + w + "x" + h);
//#endif

        // release current graphics - probably will not work after size change (RIM, ANDROID)
        graphics = null;

        // reset and repaint UI
        if (delegate.resetGui()) {
            delegate.update(Desktop.MASK_ALL);
        } else { // lightweight refresh
            super.flushGraphics();
        }

//#ifdef __LOG__
        if (log.isEnabled()) log.info("~size changed");
//#endif
    }

    protected void pointerPressed(int x, int y) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("pointerPressed");
//#endif

        // happens on android sometimes?!?
        if (cz.kruch.track.TrackingMIDlet.state != 1) {
            return;
        }

        // set helpers
		gx = x;
		gy = y;

		// menu shown?
        if (touchMenuActive) {

            // ops flags
            touchMenuActive = false;
            cmdExec = true;

            // find simulated command
            final Command cmd = pointerToCmd(x, y);
            
            // run the command
            if (cmd != null) {
                delegate.commandAction(cmd, this);
            }

            // update screen anyway
            delegate.update(Desktop.MASK_SCREEN);

        } else { // no, detect action

            // ops flags
            cmdExec = false;

            // resolve coordinates to keypress
            final int key = pointerToKey(x, y);
            if (key != 0) {

                // invoke emulated event
                keyPressed(key);

                // help repetition
                emulateKeyRepeated(key);
            }
        }
    }

    protected void pointerReleased(int x, int y) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("pointerReleased");
//#endif

        // happens on android sometimes?!?
        if (cz.kruch.track.TrackingMIDlet.state != 1) {
            return;
        }

        // clear helpers
		gx = gy = 0;
        
        // ignore the event when menu was on
        if (cmdExec) {
            return;
        }

        // end of drag?
        if (_getInMove()) {
            _setInMove(false);
            delegate.handleStall(x, y);
            return;
        }

        // detect action
        final int key = pointerToKey(x, y);

        // distinguish screenlock and menu "popup"
        if (key == Canvas.KEY_STAR && _getInKey() == Canvas.KEY_STAR && keyRepeatedCount == 0) {

            // render repeated check blind
            _setInKey(0);

            if (!keylock) {

                // set "touch menu on" flag
                touchMenuActive = true;

                // repaint
                flushGraphics();

            } else {

                // screenlock warning
                Desktop.showWarning(Resources.getString(Resources.DESKTOP_MSG_KEYS_LOCKED), null, null);

            }

        } else {

            // usual handling
            keyReleased(key);

        }
    }

    protected void pointerDragged(int x, int y) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("pointerDragged");
//#endif

        // happens on android sometimes?!?
        if (cz.kruch.track.TrackingMIDlet.state != 1) {
            return;
        }

        // ignore the event when menu was on or keylocked
        if (cmdExec || keylock) {
            return;
        }

        final int dx = x - gx;
        final int dy = y - gy;
        final int adx = Math.abs(dx);
        final int ady = Math.abs(dy);
        if (adx >= 15 || ady >= 15 || _getInMove()) {
            _setInKey(0);
            _setInMove(true);
            delegate.handleMove(x, y);
        }
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
                callSerially(this);
            } else {
                _setInKey(0);
            }
            return;
        }
//#endif

        // save key
        _setInKey(i);

        // help repeat
        checkKeyRepeated(i);

        // keymap
        i = Resources.remap(i);

        // handle key
        if (!keylock) {
            delegate.handleKeyDown(i, false);
        }
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
                    Desktop.display.vibrate(500);
                }
            }
            return;
        }

        // handle key event is not locked
        if (!keylock) {
            delegate.handleKeyDown(i, true);
        }
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
        synchronized (this) {
            if (repeatedKeyCheck != null) {
                repeatedKeyCheck.cancel();
                repeatedKeyCheck = null;
            }
        }

        // no key pressed anymore
        _setInKey(0);

        // keymap
        i = Resources.remap(i);

        // handle keylock
        if (Canvas.KEY_STAR == i) {
            if (keyRepeatedCount != 0) {
                keyRepeatedCount = 0;
                Desktop.showConfirmation(keylock ? Resources.getString(Resources.DESKTOP_MSG_KEYS_LOCKED) : Resources.getString(Resources.DESKTOP_MSG_KEYS_UNLOCKED), null);
            }
        }

        // handle key
        if (!keylock) {
            delegate.handleKeyUp(i);
        }
    }

    void callSerially(Runnable r) {
        eventing.callSerially(r);
    }

    boolean isActive() {
        return active;
    }

    boolean isKeylock() {
        return keylock;
    }

    void checkKeyRepeated(final int keyCode) {
        if (!hasRepeatEvents) {
            emulateKeyRepeated(keyCode);
        }
    }

    void emulateKeyRepeated(final int keyCode) {
        synchronized (this) {
            if (repeatedKeyCheck == null) {
                Desktop.timer.schedule(repeatedKeyCheck = new KeyCheckTimerTask(), 750L);
            }
        }
    }

    void firedKeyRepeated() {
        synchronized (this) {
            repeatedKeyCheck = null;
        }
    }

    private void drawTouchMenu() {
        final int dy = getHeight() / 17;
        final int bh = 3 * dy;
        final int bw = (getWidth() - 3 * dy) / 2;
        final Graphics g = getGraphics();
        final int c = g.getColor();
        g.setFont(Desktop.fontBtns);
        if (delegate.isTracking()) {
            drawButton(g, delegate.cmdStop, dy, dy, bw, bh);
            drawButton(g, Desktop.paused ? delegate.cmdContinue : delegate.cmdPause, dy + bw + dy, dy, bw, bh);
        } else {
            drawButton(g, delegate.cmdRun, dy, dy, bw, bh);
            if (Config.locationProvider == Config.LOCATION_PROVIDER_JSR82 && delegate.cmdRunLast != null) {
                drawButton(g, delegate.cmdRunLast, dy + bw + dy, dy, bw, bh);
            }
        }
//#ifndef __B2B__
        if (api.file.File.isFs()) {
            drawButton(g, delegate.cmdLoadMap, dy, 2 * dy + bh, bw, bh);
            drawButton(g, delegate.cmdLoadAtlas, dy + bw + dy, 2 * dy + bh, bw, bh);
        }
//#endif        
        drawButton(g, delegate.cmdSettings, dy, 3 * dy + 2 * bh, bw, bh);
        drawButton(g, delegate.cmdInfo, dy + bw + dy, 3 * dy + 2 * bh, bw, bh);
        drawButton(g, delegate.cmdExit, dy + bw + dy, 4 * dy + 3 * bh, bw, bh);
        g.setColor(c);
    }

    private void drawButton(final Graphics g, final Command cmd,
                            final int x, final int y, final int bw, final int bh) {
        g.setColor(BTN_COLOR);
        g.fillRoundRect(x, y, bw, bh, BTN_ARC, BTN_ARC);
        g.setColor(BTN_HICOLOR);
        g.drawRoundRect(x, y, bw, bh, BTN_ARC, BTN_ARC);
        final String label = cmd.getLabel();
        final int fh = Desktop.fontBtns.getHeight();
        final int sw = Desktop.fontBtns.stringWidth(label);
        g.setColor(0x00ffffff);
        g.drawString(label, x + (bw - sw) / 2, y + (bh - fh) / 2, Graphics.LEFT | Graphics.TOP);
    }

    private Command pointerToCmd(final int x, final int y) {
        final int i = y / (getHeight() / 17);
        final int w = getWidth();

        Command cmd = null;

        switch (i) {
            case 1:
            case 2:
            case 3: {
                if (delegate.isTracking()) {
                    if (x > i && x < w / 2 - i) {
                        cmd = delegate.cmdStop;
                    } else if (x > w / 2 + i && x < w - i) {
                        cmd = Desktop.paused ? delegate.cmdContinue : delegate.cmdPause;
                    }
                } else {
                    if (x > i && x < w / 2 - i) {
                        cmd = delegate.cmdRun;
                    } else if (x > w / 2 + i && x < w - i) {
                        cmd = Config.locationProvider == Config.LOCATION_PROVIDER_JSR82 && delegate.cmdRunLast != null ? delegate.cmdRunLast : null;
                    }
                }
            } break;
            case 5:
            case 6:
            case 7: {
//#ifndef __B2B__
                if (api.file.File.isFs()) {
                    if (x > i && x < w / 2 - i) {
                        cmd = delegate.cmdLoadMap;
                    } else if (x > w / 2 + i && x < w - i) {
                        cmd = delegate.cmdLoadAtlas;
                    }
                }
//#endif                
            } break;
            case 9:
            case 10:
            case 11: {
                if (x > i && x < w / 2 - i) {
                    cmd = delegate.cmdSettings;
                } else if (x > w / 2 + i && x < w - i) {
                    cmd = delegate.cmdInfo;
                }
            } break;
            case 13:
            case 14:
            case 15: {
                if (x > w / 2 + i && x < w - i) {
                    cmd = delegate.cmdExit;
                }
            } break;
        }

        return cmd;
    }

    private int pointerToKey(final int x, final int y) {
        final int j = x / (getWidth() / 5);
        final int i = y / (getHeight() / 10);
        int key = 0;

        switch (i) {
            case 0:
            case 1: {
                switch (j) {
                    case 0:
                        key = Canvas.KEY_NUM1;
                        break;
                    case 1:
                    case 2:
                    case 3:
                        key = getKeyCode(Canvas.UP);
                        break;
                    case 4:
                        key = Canvas.KEY_NUM3;
                        break;
                }
            }
            break;
            case 2:
            case 3:
            case 4:
            case 5:
            case 6: {
                switch (j) {
                    case 0:
                        key = getKeyCode(Canvas.LEFT);
                        break;
                    case 1:
                    case 2:
                    case 3:
                        key = getKeyCode(Canvas.FIRE);
                        break;
                    case 4:
                        key = getKeyCode(Canvas.RIGHT);
                        break;
                }
            }
            break;
            case 7:
            case 8: {
                switch (j) {
                    case 0:
                        key = Canvas.KEY_NUM7;
                        break;
                    case 1:
                    case 2:
                    case 3:
                        key = getKeyCode(Canvas.DOWN);
                        break;
                    case 4:
                        key = Canvas.KEY_NUM9;
                        break;
                }
            }
            break;
            case 9:
            case 10: {
                switch (j) {
                    case 0:
                        key = Canvas.KEY_STAR;
                        break;
                    case 1:
                    case 2:
                    case 3:
                        key = Canvas.KEY_NUM0;
                        break;
                    case 4:
                        key = Canvas.KEY_POUND;
                        break;
                }
            }
            break;
        }

        return key;
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

    private boolean _getInMove() {
        synchronized (this) {
            return inMove;
        }
    }

    private void _setInMove(final boolean b) {
        synchronized (this) {
            inMove = b;
        }
    }
}

