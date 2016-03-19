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

    static int BTN_ARC              = 10;
    
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
    private volatile int pointersCount;

    // menu appearance
    volatile boolean beenPressed; // TODO review visibility
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
            this.commands = new Command[11];
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
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("hideNotify");
//#endif
        eventing.setActive(active = false);
        pointersCount = 0;
    }

    /** @Override */
    protected void showNotify() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("showNotify");
//#endif
        eventing.setActive(active = true);
        pointersCount = 0;
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
        if (graphics == null /*|| cz.kruch.track.TrackingMIDlet.s65*/) {
            /*graphics = null;*/
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

//#ifdef __ANDROID__
    
    /** @Override for more control */
    public boolean hasPointerEvents() {
        return true;
    }

//#endif

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
        if (Config.guideSpotsMode > 1 || Config.zoomSpotsMode > 1) {
            beenPressed = true;
        }
    }

    void autohide() {
        if (beenPressed && (Config.guideSpotsMode > 1 || Config.zoomSpotsMode > 1)) {
            if (delayedRepaint == null) {
                Desktop.schedule(delayedRepaint = new AnyTask(AnyTask.TASK_REPAINT), 3000);
            }
        }
    }
    
    boolean iconBarVisible() {
        return Config.guideSpotsMode == 1 || (Config.guideSpotsMode == 2 && beenPressed);
    }

//#ifdef __ANDROID__
    static final float density = Float.parseFloat(System.getProperty("microemu.display.density"));
    static final float densityDpi = Float.parseFloat(System.getProperty("microemu.display.densityDpi"));
    static final float xdpi = Float.parseFloat(System.getProperty("microemu.display.xdpi"));
    static final float ydpi = Float.parseFloat(System.getProperty("microemu.display.ydpi"));
//#else
    static final float density = 1.0f;
//#endif

    int getHiresLevel() {
        int level = 0;
//#ifdef __ANDROID__
        if (xdpi > 320 || ydpi > 320) {
            level = 3;
        } else if (xdpi > 240 || ydpi > 240) {
            level = 2;
        } else if (xdpi > 160 || ydpi > 160) {
            level = 1;
        }
//#else
        if (getHeight() > 480 || getWidth() > 480) {
            if (getHeight() > 720 || getWidth() > 720) {
                level = 2;
            } else {
                level = 1;
            }
        }
//#endif
        return level;
    }

    boolean isHiresGui() {
//#ifdef __ANDROID__
        return density >= 1.5f;
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
        if (log.isEnabled()) log.debug("size changed: " + w + "x" + h);
//#endif
        // CN1 only?
        super.sizeChanged(w, h);

        // current graphics probably no longer valid (RIM, ANDROID)
        graphics = null;

        // recalc touch threshold
        gdiff = Math.min(w / 15, h / 15);

        // adjust UI
        BTN_ARC = isHiresGui() ? 15 : 10;

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
        if (log.isEnabled()) log.debug("~size changed");
//#endif
    }

//#ifdef __CN1__

    public void buttonPressed(int x, int y) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("buttonPressed; " + x + "-" + y + "; count = " + pointersCount);
//#endif

        // should happen only when touch menu is on
        if (touchMenuActive) {

            // as if pressed
            pointerPressed(x, y);

            // clear fake press
            pointersCount = 0;

        } else {
//#ifdef __LOG__
            log.error("button pressed when touch menu is not active");
//#endif
        }
    }

//#endif // __CN1__

    protected void pointerPressed(int x, int y) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("pointerPressed; " + x + "-" + y + "; count = " + pointersCount);
//#endif

        // happens on android sometimes?!?
        if (cz.kruch.track.TrackingMIDlet.state != 1) {
            return;
        }

        // avoid multitouch
        if (pointersCount++ > 0) {
            cancelKeyRepeated(); // stop repeated key action (such as scrolling or magnification)
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

            // update screen anyway
            delegate.update(Desktop.MASK_SCREEN);

            // find simulated command
            final Command cmd = pointerToCmd(x, y);
            
            // run the command
            if (cmd != null) {
                callSerially(new AnyTask(AnyTask.TASK_COMMAND, cmd));
            }

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
        if (log.isEnabled()) log.debug("pointerReleased; " + x + "-" + y + "; count = " + pointersCount);
//#endif

        // happens on android sometimes?!?
        if (cz.kruch.track.TrackingMIDlet.state != 1) {
            return;
        }

        // avoid multitouch
        if (--pointersCount > 0) {
            return;
        }

//#if __ANDROID__ || __CN1__

        // end of pinch no matter what
        if (!Float.isNaN(scale)) {
            scale = Float.NaN;
            return;
        }

//#endif

        // clear helpers
		gx = gy = 0;

        // stop key checker
        cancelKeyRepeated();

        // ignore the event when menu was on
        if (cmdExec) {
            return;
        }

        // detect action
        final int key = pointerToKey(x, y);
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("pointerReleased; key = " + key);
//#endif

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

            // show action icons (if autohiding)
            splash();
            if (beenPressed) {
                delegate.update(Desktop.MASK_SCREEN);
            }
            
            // end of drag?
            if (_getInMove()) {
                _setInMove(false);
                delegate.handleStall(x, y);
            } else { // usual handling
                keyReleased(key);
            }

            // trigger icon spots autohide
            autohide();
        }
    }

    protected void pointerDragged(int x, int y) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("pointerDragged; " + x + "-" + y + "; count = " + pointersCount);
//#endif

        // happens on android sometimes?!?
        if (cz.kruch.track.TrackingMIDlet.state != 1) {
            return;
        }

        // ignore the event when menu was on or keylocked
        if (cmdExec || keylock) {
            return;
        }

        // avoid multitouch
        if (pointersCount > 1) {
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
            beenPressed = false; // to hide action icons when dragging
        }
    }

//#if __ANDROID__ || __CN1__

    private float scale = Float.NaN;

    protected void pointerScaled(int x, int y) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("pointerScaled; " + x + "-" + y);
//#endif

        // happens on android sometimes?!?
        if (cz.kruch.track.TrackingMIDlet.state != 1) {
            return;
        }

        // ignore the event when menu was on or keylocked
        if (cmdExec || keylock) {
            return;
        }

        // detect change
        final float cs = (float)x / 1000;
        if (Float.isNaN(scale)) {

            // start of pinch
            scale = cs;

        } else {

            // calculate percentual change
            final float ds = scale / cs;

            // check difference
            int mag = 0;
            if (ds < 0.75) {
                mag = 1;
            } else if (ds > 1.25) {
                mag = -1;
            }

            // is magnification
            if (mag != 0) {
                // use as new base
                scale = cs;
                // handle magnification
                delegate.handleMagnify(mag);
            }
        }
    }

//#endif

    protected void keyPressed(int i) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("keyPressed; " + i);
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
                                callSerially(new AnyTask(AnyTask.TASK_COMMAND, cmd));
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

//#ifdef __LOG__
        if (log.isEnabled()) log.debug("key is " + i);
//#endif

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
        if (log.isEnabled()) log.debug("keyRepeated");
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
        if (log.isEnabled()) log.debug("keyReleased");
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
                Desktop.schedule(repeatedKeyCheck = new AnyTask(AnyTask.TASK_KEYCHECK), 750L);
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
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("drawTouchMenu");
//#endif

        // calculate spacing and button size
        final int w = getWidth();
        final int h = getHeight();
        final int dx, dy, bh, bw;
        final int mode;
        if (w <= h) { // portrait
            mode = 0;
            dx = Desktop.fontBtns.getHeight();
            dy = h / VROWS;
            bh = dy << 1;
            bw = (w - 3 * dx) >> 1;
        } else { // landscape (= old portrait layout)
            mode = 1;
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
            drawButton(g, mode, delegate.cmdStop, dx, dy, bw, bh);
            drawButton(g, mode, Desktop.paused ? delegate.cmdContinue : delegate.cmdPause, dx + bw + dx, dy, bw, bh);
        } else {
            drawButton(g, mode, delegate.cmdRun, dx, dy, bw, bh);
            if (Config.locationProvider == Config.LOCATION_PROVIDER_JSR82 && delegate.cmdRunLast != null) {
                drawButton(g, mode, delegate.cmdRunLast, dx + bw + dx, dy, bw, bh);
            }
        }
//#ifndef __B2B__
        drawButton(g, mode, delegate.cmdLoadMaps, dx, (dy << 1) + bh, bw, bh);
        drawButton(g, mode, delegate.cmdLive, dx + bw + dx, (dy << 1) + bh, bw, bh);
//#endif
        drawButton(g, mode, delegate.cmdSettings, dx, 3 * dy + (bh << 1), bw, bh);
        drawButton(g, mode, delegate.cmdInfo, dx + bw + dx, 3 * dy + (bh << 1), bw, bh);
        drawButton(g, mode, delegate.cmdWaypoints, dx, (dy << 2) + 3 * bh, bw, bh);
        if (delegate.isTracking()) {
            drawButton(g, mode, delegate.cmdTracklog, dx + bw + dx, (dy << 2) + 3 * bh, bw, bh);
        } else {
            drawButton(g, mode, delegate.cmdExit, dx + bw + dx, (dy << 2) + 3 * bh, bw, bh);
        }
        g.setColor(c);
    }

    private void drawButton(final Graphics g, final int mode, final Command cmd,
                            final int x, final int y,
                            final int bw, final int bh) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("drawButton; " + g + " " + cmd);
//#endif
//#ifndef __CN1__
        g.setColor(BTN_COLOR);
        g.fillRoundRect(x, y, bw, bh, BTN_ARC, BTN_ARC);
        g.setColor(BTN_HICOLOR);
        g.drawRoundRect(x, y, bw, bh, BTN_ARC, BTN_ARC);
        final String label = cmd.getLabel();
        final int wordIdx = label.indexOf(' ');
        final int fh = Desktop.fontBtns.getHeight();
        final int lspace = (bh - (fh << 1)) / 3;
        g.setColor(BTN_TXTCOLOR);
        if (mode == 1 || wordIdx == -1 || lspace < 0) { // landscape or single-word or not enough space for 2 lines
            final int sw = Desktop.fontBtns.stringWidth(label);
            g.drawString(label, x + ((bw - sw) >> 1), y + ((bh - fh) >> 1), Graphics.LEFT | Graphics.TOP);
        } else { // portrait and 2-word label
            final String label0 = label.substring(0, wordIdx);
            final String label1 = label.substring(wordIdx);
            final int sw0 = Desktop.fontBtns.stringWidth(label0);
            final int sw1 = Desktop.fontBtns.stringWidth(label1);
            g.drawString(label0, x + ((bw - sw0) >> 1), y + lspace, Graphics.LEFT | Graphics.TOP);
            g.drawString(label1, x + ((bw - sw1) >> 1), y + (lspace << 1) + fh, Graphics.LEFT | Graphics.TOP);
        }
//#else
        com.codename1.ui.FriendlyAccess.execute("draw-button", new Object[]{
                com.codename1.ui.FriendlyAccess.getNativeGraphics(offscreen.getNativeImage()),
                new Integer(x), new Integer(y), new Integer(bw), new Integer(bh), cmd.getLabel()
        });
//#endif
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
                    if (xL) {
                        cmd = delegate.cmdLoadMaps;
                    } else if (xR) {
                        cmd = delegate.cmdLive;
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
                        if (delegate.isTracking()) {
                            cmd = delegate.cmdTracklog;
                        } else {
                            cmd = delegate.cmdExit;
                        }
                    }
                } break;
            }
//        } else { // landscape
//        }

        return cmd;
    }

    private int pointerToKey(final int x, final int y) {
        final int j = (x * 5) / getWidth(); // x / (getWidth() / 5);
        final int i = (y * 10) / getHeight(); // y / (getHeight() / 10);
        int key = 0;
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("pointerToKey; i = " + i + "; j = " + j);
//#endif

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
//                    case 1:
//                    case 2:
//                    case 3:
//                        key = getKeyCode(Canvas.UP);
//                        break;
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
//                switch (j) {
//                    case 1:
//                    case 2:
//                    case 3:
//                        key = getKeyCode(Canvas.DOWN);
//                        break;
//                }
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

    private final class AnyTask extends TimerTask {
        private static final int TASK_COMMAND   = 0;
        private static final int TASK_REPAINT   = 1;
        private static final int TASK_KEYCHECK  = 2;

        private int type;
        private Object arg;

        public AnyTask(int type) {
            this.type = type;
        }

        public AnyTask(int type, Object arg) {
            this.type = type;
            this.arg = arg;
        }

        public void run() {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("anyTask [" + type + "] run");
//#endif
            switch (type) {
                case TASK_COMMAND: {
                    delegate.commandAction((Command) arg, DeviceScreen.this);
                } break;
                case TASK_REPAINT: {
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
                } break;
                case TASK_KEYCHECK: {
                    callSerially(DeviceScreen.this);
                    firedKeyRepeated();
                } break;
            }
        }
    }
    
//#ifdef __CN1__
    public static double scaleFactor;
//#endif
}
