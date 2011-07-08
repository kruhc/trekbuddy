// @LICENSE@

package cz.kruch.track.util;

import java.util.Vector;

/**
 * Worker that executes tasks in a thread.
 *
 * @author kruhc@seznam.cz
 */
public final class Worker extends Thread {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Worker");
//#endif

    private final Vector tasks;
	private boolean go;

    public Worker(String name) {
        super(name);
        this.tasks = new Vector(16);
        this.go = true;
    }

    public int getQueueSize() {
        return tasks.size();
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

        synchronized (this) {
            if (go) {
                tasks.addElement(r);
                notify();
            }
        }
    }

    public void run() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("thread starting...");
//#endif

        final Vector tasks = this.tasks;

        while (true) {
            Runnable task = null;
            synchronized (this) {
                while (go && tasks.size() == 0) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
                if (tasks.size() > 0) {
                    task = (Runnable) tasks.elementAt(0);
                    tasks.setElementAt(null, 0); // helps GC?
                    tasks.removeElementAt(0);
                }
                if (!go)
                    break;
            }

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
                t.printStackTrace();
                if (log.isEnabled()) log.debug("task failed: " + t);
//#endif
                // ignore
            }

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("task finished");
//#endif
        }
    }
}