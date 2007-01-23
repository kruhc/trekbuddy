// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import java.util.Vector;

public final class SmartRunnable implements Runnable {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("SmartRunnable");
//#endif

    private static Vector runnables = new Vector();
    private static SmartRunnable current = null;

    private Runnable runnable;

    private SmartRunnable(Runnable runnable) {
        this.runnable = runnable;
    }

    public static synchronized void callSerially(Runnable r) {
        runnables.addElement(new SmartRunnable(r));
        callNext();
    }

    private static synchronized void callNext() {
        if (current == null) {
            if (runnables.size() > 0) {
                current = (SmartRunnable) runnables.elementAt(0);
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("got next runnable to call serially: " + current.runnable);
//#endif
                Desktop.display.callSerially(current);
            } else {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("no runnable to call serially");
//#endif
            }
        }
    }

    private static synchronized void removeCurrent() {
        runnables.setElementAt(null, 0);
        runnables.removeElementAt(0);
        current = null;
    }

    public void run() {
        try {
            runnable.run();
        } finally {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("call serially finished");
//#endif
            runnable = null; // gc hint
            removeCurrent();
            callNext();
        }
    }
}
