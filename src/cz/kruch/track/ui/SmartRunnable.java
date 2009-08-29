// @LICENSE@

package cz.kruch.track.ui;

import java.util.Vector;

/**
 * Buffered backend for Display.callserially(Runnable) with task merging support.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class SmartRunnable implements Runnable {
    static int uncaught, mergedRT, mergedKT;

    private final Vector runnables;
    private boolean go, pending;

    public SmartRunnable() {
        this.runnables = new Vector(16);
        this.go = true;
    }

    public void destroy() {
        synchronized (this) {
            go = false;
        }
    }

    public void callSerially(final Runnable r) {
		// local ref
        final Vector runnables = this.runnables;

        // fire flag
        boolean fire = false;

        // thread-safe
        synchronized (this) {

            // accept task only if running
            if (go) {

                // try task merge
                if (runnables.size() > 0) {

                    final Object last = runnables.lastElement();
                    if (r instanceof DeviceScreen) { // trick #1: avoid duplicates of key-hold checks
                        if (last instanceof DeviceScreen) {
							mergedKT++;
							return;
                        }
                    } else if (r instanceof Desktop.RenderTask) { // trick #2: merge render tasks
                        if (last instanceof Desktop.RenderTask) {
                            ((Desktop.RenderTask) last).merge(((Desktop.RenderTask) r));
							mergedRT++;
							return;
                        }
                    }

                }

                // enqueue task
                runnables.addElement(r);

                // "schedule" a task if no task is currently running
                if (!pending) {
                    fire = pending = true;
                }
            }
        }

        if (fire) {
            Desktop.display.callSerially(this);
        }
    }

    public void run() {
        final Vector runnables = this.runnables;
        Runnable r = null;

        synchronized (this) {
            if (runnables.size() > 0) {
                r = (Runnable) runnables.elementAt(0);
                runnables.setElementAt(null, 0);
                runnables.removeElementAt(0);
            }
        }

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
        boolean fire = false;

        synchronized (this) {
            if (runnables.size() > 0) {
                fire = pending = true;
            } else {
                pending = false;
            }
        }

        if (fire) {
            Desktop.display.callSerially(this);
        }
    }
}
