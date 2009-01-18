// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.Resources;
import cz.kruch.track.configuration.Config;

import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import java.util.TimerTask;

final class DesktopScreen extends GameCanvas implements Runnable {
    //#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Canvas");
//#endif

    static final int BTN_ARC    = 10;
    static final int BTN_COLOR  = 0x002664bf;

    // main application
    private final Desktop delegate;

    // keylock status
    private volatile boolean keylock;
    private volatile int keyRepeatedCount;

    // key repeating simulation support
    private /*volatile*/ TimerTask repeatedKeyCheck;
    private /*volatile*/ int inKey; // using synchronized access helper

    // touch ops
    private volatile boolean inTouch;

    public DesktopScreen(Desktop delegate) {
        super(false);
        this.delegate = delegate;
    }

    public Graphics getGraphics() {
        return super.getGraphics();
    }

    public void flushGraphics() {
        if (inTouch) {
            drawTouchMenu();
        }
        super.flushGraphics();
    }

    public boolean isKeylock() {
        return keylock;
    }

    /**
     * Used for key repetition emulation
     */
    public void run() {
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

    void emulateKeyRepeated(final int keyCode) {
        synchronized (this) {
            if (repeatedKeyCheck == null) {
                Desktop.timer.schedule(repeatedKeyCheck = new KeyCheckTimerTask(), 750L);
            }
        }
    }

    protected void sizeChanged(int w, int h) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("size changed: " + w + "x" + h);
//#endif

        // reset GUI // TODO check for dimensions change EDIT done in resetGui TODO move here
        delegate.resetGui();

//#ifdef __LOG__
        if (log.isEnabled()) log.info("~size changed");
//#endif
    }

    protected void pointerPressed(int x, int y) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("pointerPressed");
//#endif

        if (inTouch) {
            inTouch = false;
            final Command cmd = pointerToCmd(x, y);
            if (cmd != null) {
                delegate.commandAction(cmd, this);
            }
            delegate.update(Desktop.MASK_SCREEN);
        } else {
            final int key = pointerToKey(x, y);
            if (key != 0) {
                keyPressed(key);
                switch (getGameAction(key)) {
                    case Canvas.UP:
                    case Canvas.LEFT:
                    case Canvas.RIGHT:
                    case Canvas.DOWN: {
                        emulateKeyRepeated(key);
                    } break;
                    default: {
                        if (Canvas.KEY_STAR == key) {
                            if (!keylock) {
                                inTouch = true;
                                flushGraphics();
                            }
                        }
                    }
                }
            }
        }
    }

    protected void pointerReleased(int x, int y) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("pointerPressed");
//#endif

        keyReleased(pointerToKey(x, y));
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
                SmartRunnable.getInstance().callSerially(this);
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

        // save key
        _setInKey(i);

        // keymap
        i = Resources.remap(i);

        // only repeated handled for '1' // TODO hacky
        if (Canvas.KEY_NUM1 == i) {
            return;
        }

        // handle key
        delegate.handleKey(i, false);
    }

    protected void keyRepeated(int i) {
//#ifdef __LOG__
        if (log.isEnabled()) log.info("keyRepeated");
//#endif

        // increment counter
        ++keyRepeatedCount;

        // keymap
        i = Resources.remap(i);

        // handle keylock
        if (Canvas.KEY_STAR == i) {
            if (keyRepeatedCount == 1) {
                keylock = !keylock;
                if (!Config.powerSave) {
                    Desktop.display.vibrate(1000);
                }
            }
            return;
        }

        // handle key event is not locked
        if (!keylock) {
            delegate.handleKey(i, true);
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

        // keymap
        i = Resources.remap(i);

        // handle keylock
        if (Canvas.KEY_STAR == i) {
            if (keyRepeatedCount != 0) {
                Desktop.showConfirmation(keylock ? Resources.getString(Resources.DESKTOP_MSG_KEYS_LOCKED) : Resources.getString(Resources.DESKTOP_MSG_KEYS_UNLOCKED), null);
            }
        }

        // handle special key events
        if (!keylock) {
            switch (i) {
                case Canvas.KEY_NUM1: { // hack
                    delegate.handleKey(i, false);
                }
                break;
                case Canvas.KEY_NUM3: { // notify device control
                    cz.kruch.track.ui.nokia.DeviceControl.setBacklight();
                }
                break;
            }
        }

        // no key pressed anymore
        _setInKey(0);

        // reset repeated counter
        keyRepeatedCount = 0;

        // scrolling stops // TODO ugly direct access
        MapView.scrolls = 0;
    }

    private void drawTouchMenu() {
        final int dy = getHeight() / 17;
        final int bh = 3 * dy;
        final int bw = (getWidth() - 3 * dy) / 2;
        final Graphics g = getGraphics();
        final int c = g.getColor();
        g.setFont(Desktop.fontBtns);
        if (delegate.isTracking()) {
            drawButton(g, Desktop.paused ? delegate.cmdContinue : delegate.cmdPause, dy, dy, bw, bh);
            drawButton(g, delegate.cmdStop, dy + bw + dy, dy, bw, bh);
        } else {
            drawButton(g, delegate.cmdRun, dy, dy, bw, bh);
            if (delegate.cmdRunLast != null) {
                drawButton(g, delegate.cmdRunLast, dy + bw + dy, dy, bw, bh);
            }
        }
        drawButton(g, delegate.cmdLoadMap, dy, 2 * dy + bh, bw, bh);
        drawButton(g, delegate.cmdLoadAtlas, dy + bw + dy, 2 * dy + bh, bw, bh);
        drawButton(g, delegate.cmdSettings, dy, 3 * dy + 2 * bh, bw, bh);
        drawButton(g, delegate.cmdInfo, dy + bw + dy, 3 * dy + 2 * bh, bw, bh);
        drawButton(g, delegate.cmdExit, dy + bw + dy, 4 * dy + 3 * bh, bw, bh);
        g.setColor(c);
    }

    private void drawButton(final Graphics g, final Command cmd,
                            final int x, final int y, final int bw, final int bh) {
        g.setColor(BTN_COLOR);
        g.fillRoundRect(x, y, bw, bh, BTN_ARC, BTN_ARC);
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
                if (x > i && x < w / 2 - i) {
                    cmd = delegate.isTracking() ? (Desktop.paused ? delegate.cmdContinue : delegate.cmdPause) : delegate.cmdRun;
                } else if (x > w / 2 + i && x < w - i) {
                    cmd = delegate.isTracking() ? delegate.cmdStop : delegate.cmdRunLast;
                }
            } break;
            case 5:
            case 6:
            case 7: {
                if (x > i && x < w / 2 - i) {
                    cmd = delegate.cmdLoadMap;
                } else if (x > w / 2 + i && x < w - i) {
                    cmd = delegate.cmdLoadAtlas;
                }
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
            case 9: {
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
}

