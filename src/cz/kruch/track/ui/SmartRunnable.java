// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import java.util.Vector;

public final class SmartRunnable extends Thread {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("SmartRunnable");
//#endif

    /*
     * POOL
     */

    private static final class QueuedRunnable implements Runnable {
//#ifdef __LOG__
        private /*static*/ final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("QueuedRunnable");
//#endif

        private Runnable runnable;

        public QueuedRunnable(Runnable runnable) {
            this.runnable = runnable;
        }

        public void run() {
            try {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("invoking runnable: " + runnable);
//#endif
                runnable.run();
            } finally {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("~invoking runnable: " + runnable);
//#endif
                runnable = null;
                releaseInstance(this); // TODO release by "parent" (ie. introduce 'current')
                instance.tick();
            }
        }
    }

    private static final QueuedRunnable[] pool = new QueuedRunnable[8];
    private static int countFree;

    private synchronized static QueuedRunnable newInstance(Runnable r) {
        QueuedRunnable result;

        if (countFree == 0) {
            result = new QueuedRunnable(r);
        } else {
            result = pool[--countFree];
            result.runnable = r;
        }

        return result;
    }

    private synchronized static void releaseInstance(QueuedRunnable sr) {
        if (countFree < pool.length) {
            pool[countFree++] = sr;
        }
    }

    /*
     * ~POOL
     */

    private static SmartRunnable instance;

    private Vector runnables;
    private boolean go;
    private boolean check;
    private boolean running;

    private SmartRunnable() {
        this.runnables = new Vector(8);
        this.go = true;
    }

    public synchronized static SmartRunnable getInstance() {
        if (instance == null) {
            instance = new SmartRunnable();
            instance.start();
        }

        return instance;
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

    public void callSerially(Runnable r) {
        synchronized (this) {
            runnables.addElement(newInstance(r));
            check = true;
            notify();
        }
    }

    private void tick() {
        synchronized (this) {
            running = false;
            check = true;
            notify();
        }
    }

    public void run() {
        for (; go ; ) {
            // pop task
            QueuedRunnable r = null;
            synchronized (this) {
                while (go && !check) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
                if (!running) {
                    if (runnables.size() > 0) {
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("got next runnable to call serially: " + r);
//#endif
                        r = (QueuedRunnable) runnables.elementAt(0);
                        runnables.setElementAt(null, 0);
                        runnables.removeElementAt(0);
                        running = r != null;
                    }
//#ifdef __LOG__
                    else {
                        if (log.isEnabled()) log.debug("no next runnable to call serially");
                    }
//#endif
                }
//#ifdef __LOG__
                else {
                    if (log.isEnabled()) log.debug("already running");
                }
//#endif

                // clear flag
                check = false;
            }

            // good to go?
            if (!go) break;

            // run task, if any
            if (r != null) {
                Desktop.display.callSerially(r);
            }
        }
    }
}
