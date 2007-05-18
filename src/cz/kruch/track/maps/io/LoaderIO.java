// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps.io;

public final class LoaderIO extends Thread {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("LoaderIO");
//#endif

    private static LoaderIO instance;

    private Runnable task = null;
    private boolean go = true;
    private boolean ready = true;

    private LoaderIO() {
    }

    public static LoaderIO getInstance() {
        if (instance == null) {
            instance = new LoaderIO();
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

    public void enqueue(Runnable r) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("enqueueing task " + r);
//#endif

        // enqueu task (wait for previous task to finish)
        synchronized (this) {
            while (go && !ready) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            ready = false;
            task = r;
            notify();
        }
    }

    public void run() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("I/O thread starting...");
//#endif

        for (; go ;) {
            // pop task
            Runnable r;
            synchronized (this) {
                while (go && task == null) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
                r = task;
                task = null;
            }

            // good to go?
            if (!go) break;

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("popped task: " + r);
//#endif

            try {
                // run task
                r.run();
            } catch (Throwable t) {
                // ignore
            } finally {
                // signal ready for next task
                synchronized (this) {
                    ready = true;
                    notify();
                }
            }

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("task finished");
//#endif
        }
    }
}
