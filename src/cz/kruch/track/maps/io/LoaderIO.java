// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.maps.io;

import cz.kruch.track.util.Logger;

public final class LoaderIO extends Thread {
    private static final Logger log = new Logger("LoaderIO");

    private static LoaderIO instance;

    private Runnable task = null;
    private boolean go = true;
    private boolean ready = true;

    private LoaderIO() {
    }

    public static LoaderIO getInstance() {
        if (instance == null) {
            instance = new LoaderIO();
            instance.setPriority(Thread.MAX_PRIORITY);
            instance.start();
        }

        return instance;
    }

    public static void destroy() {
        if (instance != null) {
            synchronized (instance) {
                instance.go = false;
                instance.notify();
            }
            try {
                instance.join();
            } catch (InterruptedException e) {
            }
        }
    }

    public void enqueue(Runnable r) {
        if (log.isEnabled()) log.debug("enqueueing task " + r);

        // wait for previous task to finish
        synchronized (this) {
            while (!ready) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
            ready = false;
        }

        if (log.isEnabled()) log.debug("I/O thread ready");

        // enqueu task
        synchronized (this) {
            this.task = r;
            notify();
        }

        if (log.isEnabled()) log.debug("enqueued");
    }

    public void run() {
        if (log.isEnabled()) log.debug("I/O thread starting...");

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

            if (log.isEnabled()) log.debug("popped task: " + r);

            // run task
            r.run();

            if (log.isEnabled()) log.debug("task finished");

            // signal ready for next task
            synchronized (this) {
                ready = true;
                notify();
            }
        }
    }
}
