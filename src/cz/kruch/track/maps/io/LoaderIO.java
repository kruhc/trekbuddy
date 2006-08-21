// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps.io;

//#ifdef __LOG__
import cz.kruch.track.util.Logger;
//#endif

public final class LoaderIO extends Thread {
//#ifdef __LOG__
    private static final Logger log = new Logger("LoaderIO");
//#endif

    private static LoaderIO instance;

    private Runnable task = null;
    private volatile boolean go = true;
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

    public static void destroy() {
        if (instance != null) {
            instance.go = false;
            synchronized (instance) {
                instance.notify();
            }
            try {
                instance.join();
            } catch (InterruptedException e) {
            }
        }
    }

    public void enqueue(Runnable r) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("enqueueing task " + r);
//#endif

        // enqueu task (wait for previous task to finish)
        synchronized (this) {
            while (!ready) {
                try {
                    wait();
                } catch (InterruptedException e) {
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

        for (;;) {
            // pop task
            Runnable r = null;
            synchronized (this) {
                while (go && task == null) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
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

            // run task
            r.run();

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("task finished");
//#endif

            // signal ready for next task
            synchronized (this) {
                ready = true;
                notify();
            }
        }
    }
}
