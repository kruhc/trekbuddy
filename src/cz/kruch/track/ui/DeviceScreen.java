// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.Resources;
import cz.kruch.track.configuration.Config;

import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Display;
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

    private static final int VROWS  = 15;

    static int BTN_ARC        = 10;
    static final int BTN_COLOR      = 0x00424242; // 0x005b87ce;
    static final int BTN_HICOLOR    = 0x00ffffff; // 0x000a2468;
    static final int BTN_TXTCOLOR   = 0x00ffffff; // 0x00ffffff;

    // main application
    final Desktop delegate;

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

//#ifdef __RIM__
    // rolling flag
    private volatile boolean inRolling;
//#endif

    // touch ops
    private volatile boolean touchMenuActive, cmdExec;

    // menu appearance
    volatile boolean beenPressed;
    private volatile TimerTask delayedRepaint;

//#ifdef __ALL__

    // soft menu
    private volatile boolean softMenuActive, softMenuGone;
    private Command[] commands;
    private int selectedOptionIndex;
    private int colorBackSel, colorBackUnsel, colorForeSel, colorForeUnsel;
    private static final int PADDING_X = 8;
    private static final int PADDING_Y = 2;
    private static final int BORDER = 0;
    private static final int LEFT_SOFTKEY_CODE = -6;
    private static final int RIGHT_SOFTKEY_CODE = -7;

//#endif

    // movement filter
    private int gx, gy, gdiff;
    private boolean inMove;

    // status
    private boolean active;

    DeviceScreen(Desktop delegate, MIDlet midlet) {
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
        splash();
//#ifdef __ALL__
        if (Config.uiNoCommands) {
            this.commands = new Command[10];
            if (Config.safeColors) {
                this.colorBackSel = 0x000a3b76;
                this.colorBackUnsel = 0x00ffffff;
                this.colorForeSel = 0x00ffffff;
                this.colorForeUnsel = 0x0;
            } else {
                this.colorBackSel = Desktop.display.getColor(Display.COLOR_HIGHLIGHTED_BACKGROUND);
                this.colorBackUnsel = Desktop.display.getColor(Display.COLOR_BACKGROUND);
                this.colorForeSel = Desktop.display.getColor(Display.COLOR_HIGHLIGHTED_FOREGROUND);
                this.colorForeUnsel = Desktop.display.getColor(Display.COLOR_FOREGROUND);
            }
        }
//#endif
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

    /** @Override */
    public final void flushGraphics() {
        if (touchMenuActive) {
            drawTouchMenu();
//#ifdef __ALL__
        } else if (softMenuActive) {
            drawSoftMenu();
//#endif            
        }
        super.flushGraphics();
    }

    /** @Override to make <code>Graphics</code> publicly accessible and handle weird states */
    public Graphics getGraphics() {
        if (graphics == null || cz.kruch.track.TrackingMIDlet.s65) {
            graphics = null;
            graphics = super.getGraphics();
        }
        return graphics;
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

    /** @Override for more control */
    public boolean hasPointerEvents() {
//#ifdef __ANDROID__
        return true;
//#else
        return super.hasPointerEvents();
//#endif
    }

    /** @Override */
    public void setCommandListener(CommandListener commandListener) {
        if (!Config.uiNoCommands) {
            super.setCommandListener(commandListener);
        }
    }

    /** @Override */
    public void addCommand(Command command) {
        if (command != null) {
            if (!Config.uiNoCommands) {
                super.addCommand(command);
//#ifdef __ALL__
            } else if (commands != null) {
                commands[command.getPriority()] = command;
//#endif
            }
        }
    }

    /** @Override */
    public void removeCommand(Command command) {
        if (command != null) {
            if (!Config.uiNoCommands) {
                super.removeCommand(command);
//#ifdef __ALL__
            } else if (commands != null) {
                commands[command.getPriority()] = null;
//#endif
            }
        }
    }

//#ifdef __ANDROID__

    /** @Override */
    public void setFullScreenMode(final boolean b) {
        if (b) {
            cz.kruch.track.TrackingMIDlet.getActivity().runOnUiThread(new Runnable() {
				public void run() {
                    cz.kruch.track.TrackingMIDlet.getActivity().getWindow().setFlags(
                            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
				}
			});
        }
    }

//#endif

    void splash() {
        if (Config.guideSpotsMode > 0 || Config.zoomSpotsMode > 0) {
            beenPressed = true;
        }
    }

    void autohide() {
        if (beenPressed && (Config.guideSpotsMode > 1 || Config.zoomSpotsMode > 1)) {
            if (delayedRepaint == null) {
                Desktop.schedule(delayedRepaint = new RepaintTask(), 3000);
            }
        }
    }
    
    boolean iconBarVisible() {
        return Config.guideSpotsMode == 1 || (Config.guideSpotsMode == 2 && beenPressed);
    }

//#ifdef __ANDROID__
    private final float density = Float.parseFloat(System.getProperty("microemu.display.density"));
    private final float xdpi = Float.parseFloat(System.getProperty("microemu.display.xdpi"));
    private final float ydpi = Float.parseFloat(System.getProperty("microemu.display.ydpi"));
//#endif

    boolean isHires() {
//#ifdef __ANDROID__
        return xdpi > 160 || ydpi > 160;
//#else
        return getHeight() > 480 || getWidth() > 480;
//#endif
    }

    boolean isHiresGui() {
//#ifdef __ANDROID__
        return density > 1.0f;
//#else
        return getHeight() > 480 || getWidth() > 480;
//#endif
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

//#ifdef __RIM__
        final boolean isrl = key == Canvas.UP || key == Canvas.DOWN || key == Canvas.LEFT || key == Canvas.RIGHT;
        if (inRolling && !isrl) {
            inRolling = false;
            return;
        }
//#endif
        
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

        // recalc touch threshold
        gdiff = Math.min(w / 15, h / 15);

        // adjust UI
        BTN_ARC = isHiresGui() ? 25 : 10;

        // too early invocation? happens on Belle :-$
        if (delegate == null) {
            return;
        }

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

        // cancel repainter
        if (delayedRepaint != null) {
            delayedRepaint.cancel();
            delayedRepaint = null;
        }

        // menu shown?
        if (touchMenuActive) {

            // ops flags
            touchMenuActive = false;
            cmdExec = true;
            beenPressed = false;

            // update screen anyway
            delegate.update(Desktop.MASK_SCREEN);

            // find simulated command
            final Command cmd = pointerToCmd(x, y);
            
            // run the command
            if (cmd != null) {
                callSerially(new CommandTask(cmd));
            }

        } else { // no, detect action

            // ops flags
            cmdExec = false;
            beenPressed = true;

            // resolve coordinates to keypress
            final int key = pointerToKey(x, y);
            if (key != 0) {

                // invoke emulated event
                keyPressed(key);

                // help repetition
                emulateKeyRepeated(key);
            }

            // show bar on keylock or center hit - update not invoked
            if (keylock || key == Canvas.KEY_NUM5) {

                // repaint screen to show icon bar
                delegate.update(Desktop.MASK_SCREEN);
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

        // stop key checker
        cancelKeyRepeated();

        // delayed repaint for menu autohide
        autohide();

        // ignore the event when menu was on
        if (cmdExec) {
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

                // show the menu
                flushGraphics();

            } else {

                // screenlock warning
                Desktop.showWarning(Resources.getString(Resources.DESKTOP_MSG_KEYS_LOCKED), null, null);

            }

        } else {

            // end of drag?
            if (_getInMove()) {
                _setInMove(false);
                delegate.handleStall(x, y);
            } else {
                // usual handling
                keyReleased(key);
            }
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

        // difference
        final int adx = Math.abs(x - gx);
        final int ady = Math.abs(y - gy);

        // handle gestures
        // TODO

        // usual drag
        if (adx >= gdiff || ady >= gdiff || _getInMove()) {
            cancelKeyRepeated(); // stop key checker
            _setInKey(0);
            _setInMove(true);
            delegate.handleMove(x, y);
        }
    }

    protected void keyPressed(int i) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("keyPressed");
//#endif

//#ifdef __ALL__

        // using soft menu?
        if (commands != null) {

            // handle soft menu action
            if (softMenuActive) {

                // hide menu
                if (i == RIGHT_SOFTKEY_CODE) {

                    // repaint view
                    softMenuActive = false;
                    softMenuGone = true;
                    delegate.update(Desktop.MASK_SCREEN);

                } else {

                    // get game action
                    int action = 0;
                    try {
                        action = getGameAction(i);
                    } catch (IllegalArgumentException e) {
                        // ignore
                    }

                    // handle action
                    switch (action) {
                        case UP: {
                            // go up
                            selectedOptionIndex--;
                            if (selectedOptionIndex < 0) {
                                selectedOptionIndex = getCommandsCount() - 1;
                            }
                            // repaint
                            flushGraphics();
                        } break;
                        case DOWN: {
                            // go down
                            selectedOptionIndex++;
                            if (selectedOptionIndex >= getCommandsCount()) {
                                selectedOptionIndex = 0;
                            }
                            // repaint
                            flushGraphics();
                        } break;
                        case FIRE: {
                            // hide menu
                            softMenuActive = false;
                            softMenuGone = true;
                            // update screen
                            delegate.update(Desktop.MASK_SCREEN);
                            // get selected
                            final Command cmd = indexToCmd();
                            // run the command
                            if (cmd != null) {
                                callSerially(new CommandTask(cmd));
                            }
                        } break;
                    }
                }

                return;

            } else {

                // open soft menu
                if (i == LEFT_SOFTKEY_CODE) {

                    // show the menu
                    selectedOptionIndex = 0;
                    softMenuActive = true;
                    flushGraphics();

                    // no more processing
                    return;
                }
            }
        }

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

        // back key?
        if (isBackKey(i)) {

            // menu shown?
            if (touchMenuActive) {

                // ops flags
                touchMenuActive = false;
                cmdExec = true;

                // repaint screen
                delegate.update(Desktop.MASK_SCREEN);
            }

            // no more processing
            return;
        }

        // save key
        _setInKey(i);

        // help repeat
        keyRepeatedCount = 0;
        checkKeyRepeated(i);

        // keymap
        i = Resources.remap(i);

        // handle specials
        if (Canvas.KEY_STAR == i) {
            return;
        }

        // handle key
        if (!keylock) {
            delegate.handleKeyDown(i, 0);
        }
    }

    protected void keyRepeated(int i) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("keyRepeated");
//#endif

//#ifdef __ALL__

        // fast movement in menu?
        if (softMenuActive) {

            // get game action
            int action = 0;
            try {
                action = getGameAction(i);
            } catch (IllegalArgumentException e) {
                // ignore
            }

            // handle action
            switch (action) {
                case UP: {
                    // go up
                    selectedOptionIndex--;
                    if (selectedOptionIndex < 0) {
                        selectedOptionIndex = getCommandsCount() - 1;
                    }
                    // repaint
                    flushGraphics();
                } break;
                case DOWN: {
                    // go down
                    selectedOptionIndex++;
                    if (selectedOptionIndex >= getCommandsCount()) {
                        selectedOptionIndex = 0;
                    }
                    // repaint
                    flushGraphics();
                } break;
            }

            return;
        }

//#endif

        // back key?
        if (isBackKey(i)) {
            return;
        }

        // increment counter
        keyRepeatedCount++;
        
        // keymap
        i = Resources.remap(i);

        // handle specials
        if (Canvas.KEY_STAR == i) {
            if (keyRepeatedCount == 1) {
                keylock = !keylock;
                delegate.update(Desktop.MASK_OSD);
                Desktop.display.vibrate(100); // bypass power-save check
            }
            return;
        }

        // handle key event is not locked
        if (!keylock) {
            delegate.handleKeyDown(i, keyRepeatedCount);
        }
    }

    protected void keyReleased(int i) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("keyReleased");
//#endif

//#ifdef __RIM__
        /* trackball rolling stopped? */
        if (i == Canvas.UP || i == Canvas.DOWN || i == Canvas.LEFT || i == Canvas.RIGHT) {
            inRolling = true;
            return;
        } else {
            inRolling = false;
        }
//#endif

        // back key?
        if (isBackKey(i)) {
            return;
        }

        // was long press?
        final boolean waslp = keyRepeatedCount > 0;

        // no key pressed anymore
        _setInKey(0);
        keyRepeatedCount = 0;

        // stop key checker
        cancelKeyRepeated();

//#ifdef __ALL__

        // soft keys hack
        if (i == RIGHT_SOFTKEY_CODE) {
            if (softMenuGone) {
                softMenuGone = false;
            } else {
                i = KEY_POUND;
            }
        }

//#endif

        // keymap
        i = Resources.remap(i);

        // handle specials
        if (Canvas.KEY_STAR == i) {
            return;
        }

        // handle key
        if (!keylock) {
            delegate.handleKeyUp(i);
        }
    }

    void callSerially(final Runnable r) {
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
                Desktop.schedule(repeatedKeyCheck = new KeyCheckTimerTask(), 750L);
            }
        }
    }

    void cancelKeyRepeated() {
        synchronized (this) {
            if (repeatedKeyCheck != null) {
                repeatedKeyCheck.cancel();
                repeatedKeyCheck = null;
            }
        }
    }

    void firedKeyRepeated() {
        synchronized (this) {
            repeatedKeyCheck = null;
        }
    }

//#ifdef __ALL__

    private int getCommandsCount() {
        int count = 0;
        final Command[] commands = this.commands;
        for (int N = commands.length, i = 0; i < N; i++) {
            if (commands[i] != null) {
                count++;
            }
        }
        return count;
    }

    private Command indexToCmd() {
        int count = selectedOptionIndex;
        final Command[] commands = this.commands;
        for (int N = commands.length, i = 0; i < N; i++) {
            if (commands[i] != null) {
                if (count == 0) {
                    return commands[i];
                }
                count--;
            }
        }
        throw new IllegalArgumentException("Invalid index: " + selectedOptionIndex);
    }

    private void drawSoftMenu() {
        // get dimensions
        final Font defaultFont = Font.getDefaultFont();
        final Font barFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        final Font barBoldFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
        final Font itemFont = Font.getFont(defaultFont.getFace(), Font.STYLE_PLAIN, Font.SIZE_LARGE);
        final int barHeight = barBoldFont.getHeight() + (PADDING_Y << 1);
        final int fontHeight = itemFont.getHeight();
        final int lineHeight = fontHeight + (PADDING_Y << 1);
        final int width = getWidth();
        final int height = getHeight();

        // draw menu bar
        final Graphics g = getGraphics();
        g.setColor(colorBackUnsel);
        g.fillRect(0, height - barHeight, width, barHeight);
        g.setColor(colorForeUnsel);
        g.setFont(barBoldFont);
        final String selectLabel = Resources.getString(Resources.DESKTOP_CMD_SELECT);
        g.drawString(selectLabel,
                     (width - barBoldFont.stringWidth(selectLabel)) >> 1, height - barHeight + PADDING_Y,
                     Graphics.LEFT | Graphics.TOP);
        g.setFont(barFont);
        g.drawString(Resources.getString(Resources.CMD_CANCEL),
                     width - PADDING_X, height - PADDING_Y,
                     Graphics.RIGHT | Graphics.BOTTOM);

        // local ref
        final Command[] commands = this.commands;

        // check out the max width of a menu (for the specified menu font)
        int menuMaxWidth = 0;
        int menuMaxHeight = 0;

        // we'll simply check each option and find the maximal width
        for (int N = commands.length, i = 0; i < N; i++) {
            if (commands[i] != null) {
                final int currentWidth = itemFont.stringWidth(commands[i].getLabel());
                if (currentWidth > menuMaxWidth) {
                    menuMaxWidth = currentWidth;
                }
                menuMaxHeight += lineHeight;
            }
        }
        menuMaxWidth += PADDING_X << 1;

        // draw menu items
        int active = 0;
        int menuOptionX = BORDER + PADDING_X;
        int menuOptionY = height - menuMaxHeight - barHeight;
        g.setColor(colorBackUnsel);
        g.fillRect(BORDER, menuOptionY, menuMaxWidth, menuMaxHeight);
        g.setFont(itemFont);
        for (int N = commands.length, i = 0; i < N; i++) {
            if (commands[i] != null) {
                final int fgColor;
                if (active != selectedOptionIndex) {
                    fgColor = colorForeUnsel;
                } else {
                    g.setColor(colorBackSel);
                    g.fillRoundRect(BORDER + 2, menuOptionY + 2, menuMaxWidth - 2 - 2, lineHeight - 2, 7, 7);
                    fgColor = colorForeSel;
                }
                g.setColor(fgColor);
                g.drawString(commands[i].getLabel(),
                             menuOptionX, menuOptionY + PADDING_Y,
                             Graphics.LEFT | Graphics.TOP);
                menuOptionY += lineHeight;
                active++;
            }
        }
    }

//#endif    

    private void drawTouchMenu() {
        // calculate spacing and button size
        final int w = getWidth();
        final int h = getHeight();
        final int dx, dy, bh, bw;
        if (w <= h) { // portrait
            dx = Desktop.fontBtns.getHeight();
            dy = h / VROWS;
            bh = dy << 1;
            bw = (w - 3 * dx) >> 1;
        } else { // landscape (= old portrait layout)
            dx = dy = h / VROWS;
            bh = dy << 1;
            bw = (w - 3 * dx) >> 1;
        }
        // paint buttons
        final Graphics g = getGraphics();
        final Desktop delegate = this.delegate;
        final int c = g.getColor();
        g.setFont(Desktop.fontBtns);
        if (delegate.isTracking()) {
            drawButton(g, delegate.cmdStop, dx, dy, bw, bh);
            drawButton(g, Desktop.paused ? delegate.cmdContinue : delegate.cmdPause, dx + bw + dx, dy, bw, bh);
        } else {
            drawButton(g, delegate.cmdRun, dx, dy, bw, bh);
            if (Config.locationProvider == Config.LOCATION_PROVIDER_JSR82 && delegate.cmdRunLast != null) {
                drawButton(g, delegate.cmdRunLast, dx + bw + dx, dy, bw, bh);
            }
        }
//#ifndef __B2B__
        if (api.file.File.isFs()) {
//            drawButton(g, delegate.cmdLoadMap, dx, (dy << 1) + bh, bw, bh);
//            drawButton(g, delegate.cmdLoadAtlas, dx + bw + dx, (dy << 1) + bh, bw, bh);
            drawButton(g, delegate.cmdLoadMaps, dx, (dy << 1) + bh, bw, bh);
//#ifdef __HECL__
            drawButton(g, delegate.cmdLive, dx + bw + dx, (dy << 1) + bh, bw, bh);
//#endif
        }
//#endif        
        drawButton(g, delegate.cmdSettings, dx, 3 * dy + (bh << 1), bw, bh);
        drawButton(g, delegate.cmdInfo, dx + bw + dx, 3 * dy + (bh << 1), bw, bh);
        drawButton(g, delegate.cmdWaypoints, dx, (dy << 2) + 3 * bh, bw, bh);
        drawButton(g, delegate.cmdExit, dx + bw + dx, (dy << 2) + 3 * bh, bw, bh);
        g.setColor(c);
    }

    private void drawButton(final Graphics g, final Command cmd,
                            final int x, final int y,
                            final int bw, final int bh) {
        g.setColor(BTN_COLOR);
        g.fillRoundRect(x, y, bw, bh, BTN_ARC, BTN_ARC);
        g.setColor(BTN_HICOLOR);
        g.drawRoundRect(x, y, bw, bh, BTN_ARC, BTN_ARC);
        final String label = cmd.getLabel();
        final int fh = Desktop.fontBtns.getHeight();
        final int sw = Desktop.fontBtns.stringWidth(label);
        g.setColor(BTN_TXTCOLOR);
        g.drawString(label, x + ((bw - sw) >> 1), y + ((bh - fh) >> 1), Graphics.LEFT | Graphics.TOP);
    }

    private Command pointerToCmd(final int x, final int y) {
        final int h = getHeight();
        final int i = y / (h / VROWS);
        final int w = getWidth();
        final Desktop delegate = this.delegate;

        Command cmd = null;

//        if (w < h) { // portrait
        final boolean xL = x > i && x < (w >> 1) - i;
        final boolean xR = x > (w >> 1) + i && x < w - i;
        switch (i) {
                case 1:
                case 2: {
                    if (delegate.isTracking()) {
                        if (xL) {
                            cmd = delegate.cmdStop;
                        } else if (xR) {
                            cmd = Desktop.paused ? delegate.cmdContinue : delegate.cmdPause;
                        }
                    } else {
                        if (xL) {
                            cmd = delegate.cmdRun;
                        } else if (xR) {
                            cmd = Config.locationProvider == Config.LOCATION_PROVIDER_JSR82 && delegate.cmdRunLast != null ? delegate.cmdRunLast : null;
                        }
                    }
                } break;
                case 4:
                case 5: {
//#ifndef __B2B__
                    if (api.file.File.isFs()) {
                        if (xL) {
//                            cmd = delegate.cmdLoadMap;
                            cmd = delegate.cmdLoadMaps;
                        } else if (xR) {
//                            cmd = delegate.cmdLoadAtlas;
//#ifdef __HECL__
                            cmd = delegate.cmdLive;
//#endif                            
                        }
                    }
//#endif
                } break;
                case 7:
                case 8: {
                    if (xL) {
                        cmd = delegate.cmdSettings;
                    } else if (xR) {
                        cmd = delegate.cmdInfo;
                    }
                } break;
                case 10:
                case 11: {
                    if (xL) {
                        cmd = delegate.cmdWaypoints;
                    } else if (xR) {
                        cmd = delegate.cmdExit;
                    }
                } break;
            }
//        } else { // landscape
//        }

        return cmd;
    }

    private int pointerToKey(final int x, final int y) {
        final int j = x / (getWidth() / 5);
        final int i = y / (getHeight() / 10);
        int key = 0;

        switch (i) {
            case 0: {
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
            } break;
            case 1: {
                switch (j) {
                    case 1:
                    case 2:
                    case 3:
                        key = getKeyCode(Canvas.UP);
                        break;
                }
            } break;
            case 2:
            case 3:
            case 4:
            case 5: {
                switch (j) {
                    case 0:
                        key = getKeyCode(Canvas.LEFT);
                        break;
                    case 1:
                    case 2:
                    case 3:
                        key = Canvas.KEY_NUM5; // getKeyCode(Canvas.FIRE);
                        break;
                    case 4:
                        key = getKeyCode(Canvas.RIGHT);
                        break;
                }
            } break;
            case 6: {
                switch (j) {
                    case 1:
                    case 2:
                    case 3:
                        key = getKeyCode(Canvas.DOWN);
                        break;
                }
            } break;
            case 7: {
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
            } break;
            case 8:
                // space!!!
                break;
            case 9:
            case 10: {
                switch (j) {
                    case 0:
                        key = Canvas.KEY_STAR;
                        break;
                    case 1:
                        if (Config.guideSpotsMode > 0) {
                            key = Canvas.KEY_NUM1;
                        } else {
                            key = Canvas.KEY_NUM0;
                        }
                        break;
                    case 2:
                        key = Canvas.KEY_NUM0;
                        break;
                    case 3:
                        if (Config.guideSpotsMode > 0) {
                            key = Canvas.KEY_NUM3;
                        } else {
                            key = Canvas.KEY_NUM0;
                        }
                        break;
                    case 4:
                        key = Canvas.KEY_POUND;
                        break;
                }
            } break;
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

    private static boolean isBackKey(final int i) {
//#ifdef __ANDROID__
        return  i == +4;
//#elifdef __RIM__
        return false;
//#elifdef __J9__
        return false;
//#else
        return /*(i == -7 && cz.kruch.track.TrackingMIDlet.samsung) ||*/
               (i == -11 && cz.kruch.track.TrackingMIDlet.sonyEricssonEx);
//#endif
    }

    private final class CommandTask implements Runnable {

        private Command cmd;

        public CommandTask(Command cmd) {
            this.cmd = cmd;
        }

        public void run() {
            DeviceScreen.this.delegate.commandAction(cmd, DeviceScreen.this);
        }
    }

    private final class RepaintTask extends TimerTask {

        /* to avoid $1 */
        public RepaintTask() {
        }

        public void run() {
            if (Config.guideSpotsMode > 1) {
                final int dy = (NavigationScreens.guideSize + 2 * 3) / 10;
                try {
                    NavigationScreens.gdOffset = 0;
                    for (int i = 0; i < 9; i++) {
                        NavigationScreens.gdOffset += dy;
                        delegate.update(Desktop.MASK_SCREEN);
                        try {
                            Thread.sleep(25 - i);
                        } catch (InterruptedException e) {
                            // ignore
                        }
                    }
                } finally {
                    NavigationScreens.gdOffset = 0;
                }
            }
            beenPressed = false;
            delegate.update(Desktop.MASK_SCREEN);
        }
    }
}

