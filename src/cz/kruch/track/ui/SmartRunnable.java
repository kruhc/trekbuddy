// @LICENSE@

package cz.kruch.track.ui;

import java.util.Vector;

/**
 * Buffered backend for Display.callserially(Runnable) with task merging support.
 * 
 * 2010-06-22: Added alternative thread-based executor.
 * 2010-10-29: Removed.
 *
 * @author kruhc@seznam.cz
 */
final class SmartRunnable implements Runnable {
    // statistics
    static int uncaught, mergedRT, mergedKT, maxQT;

    // task queue
    private final Vector runnables;

    // state vars
    private boolean pending, active;

    SmartRunnable() {
        this.runnables = new Vector(16);
        this.active = true;
    }

    void setActive(boolean active) {

        // fire flag
        final boolean fire;

        // avoid collision with run()
        synchronized (this) {
            this.active = active;
            if (active && !pending && runnables.size() > 0) {
                fire = pending = true;
            } else {
                fire = false;
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
            count++;

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
        if (fire) {
            execute();
        }
    }

    void clear() {
        synchronized (this) {
            runnables.removeAllElements();
        }
    }

    public void run() {
        final Vector runnables = this.runnables;
        final Runnable r;

        // pop task
        synchronized (this) {
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
        synchronized (this) {
            if (active && runnables.size() > 0) {
                fire = true;
            } else {
                fire = pending = false;
            }
        }

        // better enqueue it out of synchronized block
        if (fire) {
            execute();
        }
    }

    private void execute() {
        Desktop.display.callSerially(this);
    }
}
