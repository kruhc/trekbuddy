// @LICENSE@

package cz.kruch.track.maps.io;

/**
 * File loading helper. It is a task runner actually :-)
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class LoaderIO extends Thread {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("LoaderIO");
//#endif

    private static LoaderIO instance;

    private volatile Runnable task;
    private volatile boolean go;

    private LoaderIO() {
        this.go = true;
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

    public void enqueue(final Runnable r) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("enqueueing task " + r);
//#endif

        // enqueu task (wait for previous task to finish)
        synchronized (this) {
            while (go && task != null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            task = r;
            notify();
        }
    }

    public void run() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("I/O thread starting...");
//#endif

        while (go) {

            // wait for task
            synchronized (this) {
                while (go && task == null) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
            }

            // good to go?
            if (!go) break;

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("popped task: " + task);
//#endif

            try {
                // run task
                task.run();
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("task finished successfully");
//#endif
            } catch (Throwable t) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("task failed: " + t);
//#endif
                // ignore
            } finally {
                // signal readiness for next task
                synchronized (this) {
                    task = null;
                    notify();
                }
            }

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("task finished");
//#endif
        }
    }
}
