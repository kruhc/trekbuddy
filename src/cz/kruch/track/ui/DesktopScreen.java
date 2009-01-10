// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.Resources;
import cz.kruch.track.configuration.Config;

import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Canvas;
import java.util.TimerTask;

final class DesktopScreen extends GameCanvas implements Runnable {
    //#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Canvas");
//#endif

    // main application
    private Desktop delegate;

    // keylock status
    private volatile boolean keylock;

    // key repeating simulation support
    private volatile TimerTask repeatedKeyCheck;
    private volatile int keyRepeatedCount;
    private /*volatile*/ int inKey; // using synchronized access helper

    public DesktopScreen(Desktop delegate) {
        super(false);
        this.delegate = delegate;
    }

    public Graphics getGraphics() {
        return super.getGraphics();
    }

    public void flushGraphics() {
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
        _setInKey(keyCode); // remember key - some devices do not support getKeyStates()
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

        final int key = pointerToKey(x, y);

        if (key != 0) {
            keyPressed(key);
            switch (getGameAction(key)) {
                case Canvas.UP:
                case Canvas.LEFT:
                case Canvas.RIGHT:
                case Canvas.DOWN: {
                    emulateKeyRepeated(key);
                }
                break;
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

        // keymap
        i = Resources.remap(i);

        // only repeated handled for '1'
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

        // keymap
        i = Resources.remap(i);

        // handle keylock
        if (Canvas.KEY_STAR == i) {
            if (++keyRepeatedCount == 1) {
                keylock = !keylock;
                if (!Config.powerSave) {
                    Desktop.display.vibrate(1000);
                }
            }
            return;
        }

        // keylock check
        if (keylock) {
            return;
        }

        // handle event
        delegate.handleKey(i, true);
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
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("repeated key check cancelled");
//#endif
            }
        }

        // keymap
        i = Resources.remap(i);

        // handle keylock
        if (Canvas.KEY_STAR == i) {
            if (keyRepeatedCount != 0) {
                keyRepeatedCount = 0;
                Desktop.showConfirmation(keylock ? Resources.getString(Resources.DESKTOP_MSG_KEYS_LOCKED) : Resources.getString(Resources.DESKTOP_MSG_KEYS_UNLOCKED), null);
            }
        }

        // special keys
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

        // scrolling stops // TODO ugly direct access
        MapView.scrolls = 0;
    }

    private int pointerToKey(final int x, final int y) {
        final int j = x / (getWidth() / 3);
        final int i = y / (getHeight() / 10);

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
            }
            break;
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
            }
            break;
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
            }
            break;
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

