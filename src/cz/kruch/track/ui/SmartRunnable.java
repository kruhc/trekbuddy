// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.util.NakedVector;

import java.util.Vector;

//#ifdef __CN1__
//-#define __EXECUTOR__
//#endif

/**
 * Buffered backend for Display.callserially(Runnable) with task merging support.
 * 
 * 2010-06-22: Added alternative thread-based executor.
 * 2010-10-29: Removed.
 * 2013-06-24: Added again for CN1.
 *
 * @author kruhc@seznam.cz
 */
final class SmartRunnable implements Runnable {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("SmartRunnable");
//#endif

    // statistics
    static int uncaught, mergedRT, mergedKT, maxQT;

    // task queue
    private final NakedVector runnables;

    // state vars
    private boolean pending, active;

    SmartRunnable() {
        this.runnables = new NakedVector(16);
        this.active = true;
//#ifdef __EXECUTOR__
        final Thread executor = new Thread("TrekBuddy [CallSerially]") {
            public void run() {
                while (true) {
                    synchronized (SmartRunnable.this) {
                        while (runnables.size() == 0) {
                            try {
                                SmartRunnable.this.wait();
                            } catch (InterruptedException e) {
                                // ignore
                            }
                        }
                    }
                    SmartRunnable.this.run();
                }
            }
        };
        executor.setPriority(Thread.MAX_PRIORITY);
        executor.start();
//#endif
    }

    void setActive(boolean active) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("set active: " + active + "; pending: " + pending + "; queue: " + runnables.size());
//#endif

        // fire flag
        boolean fire = false;

        // avoid collision with run()
        synchronized (this) {
            this.active = active;
/*
            if (active && !pending && runnables.size() > 0) {
                fire = pending = true;
            } else {
                fire = false;
            }
*/
            if (active) {
                if (!pending && runnables.size() > 0) {
                    fire = pending = true;
                }
            } else {
                runnables.removeAllElements();
            }
        }

        // better enqueue it out of synchronized block
        if (fire) {
            execute();
        }
    }

    void callSerially(final Runnable r) {
        // local ref
        final Vector runnables = this.runnables;

        // fire flag
        final boolean fire;

        // avoid collision with run() or setActive()
        synchronized (this) {

            // current tasks count
            int count = runnables.size();

            // try task merge first
            if (count > 0) {
                final Object last = runnables.lastElement();
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("merge? new: " + r + "; stack: " + last);
//#endif
                if (r instanceof Desktop.RenderTask) { // trick #1: merge render tasks
                    if (last instanceof Desktop.RenderTask) {
                        ((Desktop.RenderTask) last).merge(((Desktop.RenderTask) r));
                        mergedRT++;
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("render task merged");
//#endif
                        return;
                    }
                } else if (r instanceof DeviceScreen) { // trick #2: avoid duplicates of key-hold checks
                    if (last instanceof DeviceScreen) {
                        mergedKT++;
//#ifdef __LOG__
                        if (log.isEnabled()) log.debug("devicescreen task merged");
//#endif
                        return;
                    }
                }
            }

            // no task to merge with, just append
            runnables.addElement(r);
            count++;
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("task " + r + " queued; count = " + count);
//#endif

            // debug info
            if (count > maxQT) {
                maxQT = count;
            }

            // fire task if no task is running
            if (active && !pending) {
                fire = pending = true;
            } else {
                fire = false;
            }
        }

        // better enqueue it out of synchronized block
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("fire task? " + fire);
//#endif
        if (fire) {
            execute();
        }
    }

    public void run() {
        final NakedVector runnables = this.runnables;
        final Runnable r;

        // pop task
        synchronized (this) {
            if (runnables.size() > 0) {
                r = (Runnable) runnables.popAt(0);
            } else { // should never happen
                r = null;
            }
        }

        // execute
        if (r != null) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("running task " + r);
//#endif
            try {
                r.run();
            } catch (Throwable t) {
                uncaught++;
//#ifdef __LOG__
                t.printStackTrace();
//#endif
            }
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("task " + r + " finished");
//#endif
        }

        // fire flag
        final boolean fire;

        // more work to do?
        synchronized (this) {
            if (active && runnables.size() > 0) {
                fire = pending = true;
            } else {
                fire = pending = false;
            }
        }

        // better enqueue it out of synchronized block
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("run another? " + fire);
//#endif
        if (fire) {
            execute();
        }
    }

    private void execute() {
//#ifdef __EXECUTOR__
        synchronized (this) {
            this.notify();
        }
        Thread.yield();
//#else
        Desktop.display.callSerially(this);
//#endif
    }
}
