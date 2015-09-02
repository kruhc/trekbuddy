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

    public static void asyncTask(final Runnable task) {
//#ifdef __ANDROID__
        (new Thread(task)).start();
/*
        (new android.os.AsyncTask<Void,Void,Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                task.run();
                return null;
            }
        }).execute();
*/
//#else
        (new Thread(task)).start();
//#endif
    }

    private final NakedVector tasks;
	private boolean go;
//#if __SYMBIAN__ || __CN1__
    private int maxSize;
//#endif

    public Worker(String name) {
        super(name);
        this.tasks = new NakedVector(16);
        this.go = true;
    }

    public synchronized int getQueueSize() {
        return tasks.size();
    }

//#if __SYMBIAN__ || __CN1__
    public synchronized int getMaxQueueSize() {
        return maxSize;
    }
//#endif

    public void destroy() {
        synchronized (this) {
            go = false;
            notify();
        }
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("wakeup sent");
//#endif
        try {
            join();
        } catch (InterruptedException e) {
            // ignore
        }
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("joined");
//#endif
    }

    public void enqueue(final Runnable r) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("enqueueing task " + r);
//#endif

        synchronized (this) {
            if (go) {
                tasks.addElement(r);
//#if __SYMBIAN__ || __CN1__
                if (tasks.size() > maxSize) {
                    maxSize = tasks.size();
                }
//#endif
                notify();
            }
        }
    }

//#ifdef __HECL__

    public Runnable peek() {
        final Vector tasks = this.tasks;
        synchronized (this) {
            if (tasks.size() > 0) {
                return (Runnable) tasks.elementAt(tasks.size() - 1);
            }
        }
        return null;
    }

//#endif

    public void run() {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("thread starting...");
//#endif

        // local ref
        final Vector tasks = this.tasks;
        Runnable task = null;

        // process items until end
        while (true) {

            // pop item
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

            } catch (Throwable t) {
//#ifdef __LOG__
                t.printStackTrace();
                if (log.isEnabled()) log.debug("task failed: " + t);
//#endif
                // ignore
            }

            // gc
            task = null;

//#ifdef __LOG__
            if (log.isEnabled()) log.debug("task finished");
//#endif
        }
    }
}