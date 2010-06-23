// @LICENSE@

package cz.kruch.track.ui;

import java.util.Vector;

/**
 * Buffered backend for Display.callserially(Runnable) with task merging support.
 * 
 * 2010-06-22: Added alternative thread-based executor.
 *
 * @author kruhc@seznam.cz
 */
final class SmartRunnable implements Runnable {
    // statistics
    static int uncaught, mergedRT, mergedKT, maxQT;

    // task queue
    private final Vector runnables;

    // task executor
    private final Thread executor;

    // state vars
    private boolean pending, active;

    SmartRunnable() {
        this.runnables = new Vector(16);
        this.active = true;
        if (cz.kruch.track.TrackingMIDlet.hasFlag("thread_task_executor")) {
            this.executor = new Executor();
            this.executor.setPriority(Thread.MAX_PRIORITY);
            this.executor.start();
        } else {
            this.executor = null;
        }
    }

    void setActive(boolean active) {
        // fire flag
        final boolean fire;

        // avoid collision with run()
        synchronized (runnables) {
            this.active = active;
            if (executor == null) {
                if (active && !pending) {
                    fire = pending = true;
                } else {
                    fire = false;
                }
            } else {
                if (active) {
                    runnables.notify();
                }
                return;
            }
        }

        // better enqueue it out of synchronized block
        if (fire) {
            Desktop.display.callSerially(this);
        }
    }

    void callSerially(final Runnable r) {
		// local ref
        final Vector runnables = this.runnables;

        // fire flag
        final boolean fire;

        // avoid collisino with run() or setActive()
        synchronized (runnables) {

            // try task merge first
            if (runnables.size() > 0) {
                final Object last = runnables.lastElement();
                if (r instanceof Desktop.RenderTask) { // trick #1: merge render tasks
                    if (last instanceof Desktop.RenderTask) {
                        ((Desktop.RenderTask) last).merge(((Desktop.RenderTask) r));
                        mergedRT++;
                        return;
                    }
                } else if (r instanceof DeviceScreen) { // trick #2: avoid duplicates of key-hold checks
                    if (last instanceof DeviceScreen) {
                        mergedKT++;
                        return;
                    }
                }
            }

            // no task to merge with, just append
            runnables.addElement(r);

            // debug info
            if (runnables.size() > maxQT) {
                maxQT = runnables.size();
            }

            // fire task if no task is running
            if (executor == null) {
                if (active && !pending) {
                    fire = pending = true;
                } else {
                    fire = false;
                }
            } else {
                if (active) {
                    runnables.notify();
                }
                return;
            }
        }

        // better enqueue it out of synchronized block
        if (fire) {
            Desktop.display.callSerially(this);
        }
    }

    void clear() {
        synchronized (runnables) {
            runnables.removeAllElements();
        }
    }

    public void run() {
        final Vector runnables = this.runnables;
        final Runnable r;

        // pop task
        synchronized (runnables) {
            if (runnables.size() > 0) {
                r = (Runnable) runnables.elementAt(0);
                runnables.setElementAt(null, 0); // helps GC?
                runnables.removeElementAt(0);
            } else { // should never happen
                r = null;
            }
        }

        // execute
        if (r != null) {
            try {
                r.run();
            } catch (Throwable t) {
                uncaught++;
//#ifdef __LOG__
                t.printStackTrace();
//#endif
            }
        }

        // fire flag
        final boolean fire;

        // more work to do?
        synchronized (runnables) {
            if (active && runnables.size() > 0) {
                fire = true;
            } else {
                fire = pending = false;
            }
        }

        // better enqueue it out of synchronized block
        if (fire) {
            Desktop.display.callSerially(this);
        }
    }

    private final class Executor extends Thread {

        public Executor() {
        }

        public void run() {

            while (true) {

                final Vector runnables = SmartRunnable.this.runnables;
                final Runnable r;

                synchronized (runnables) {
                    while (runnables.size() == 0) {
                        try {
                            runnables.wait();
                        } catch (InterruptedException e) {
                            // ignore
                        }
                    }
                    r = (Runnable) runnables.elementAt(0);
                    runnables.setElementAt(null, 0); // helps GC?
                    runnables.removeElementAt(0);
                }

                if (r != null) {
                    try {
                        r.run();
                    } catch (Throwable t) {
                        uncaught++;
                    }
                }
            }
        }
    }
}
