// @LICENSE@

package cz.kruch.track.ui;

import java.util.Vector;

/**
 * Eventing engine.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class SmartRunnable extends Thread {
    private static SmartRunnable instance;

    private final Vector runnables;
    private boolean go;

    static int uncaught;

    private SmartRunnable() {
        this.runnables = new Vector(16);
        this.go = true;
    }

    public static SmartRunnable getInstance() {
        if (instance == null) {
            instance = new SmartRunnable();
            instance.setPriority(Thread.MAX_PRIORITY);
            instance.start();
        }
        return instance;
    }

    public void destroy() {
        synchronized (this) {
            go = false;
            notify();
        }
    }

    /*
     * I would guess the best performance (smoothness) could be achieved by:
     *
     * 1. passing instance(s?) of DeviceScreen (key holded check) and Desktop.RenderTask
     *    to display.callSerialy if the screen is shown and midlet state is running
     * 2. queueing instances of Desktop.Event in this task runner
     *
     * This implies synchronization around render lock in Desktop.
     */

    public void callSerially(final Runnable r) {

/*
        if (r instanceof Desktop.Event) {
            r.run();
        } else {
            cz.kruch.track.maps.io.LoaderIO.getInstance().enqueue(r);
        }
*/

        final Vector tasks = this.runnables;

        synchronized (this) {

            // accept task only if running
            if (go) {

                // try task merge
                if (tasks.size() > 0) {

                    final Object last = tasks.lastElement();
                    if (r instanceof DeviceScreen) { // trick #1: avoid duplicates of key-hold checks
                        if (last instanceof DeviceScreen) {
                            return;
                        }
                    } else if (r instanceof Desktop.RenderTask) { // trick #2: merge render tasks
                        if (last instanceof Desktop.RenderTask) {
                            Desktop.RenderTask rt = (Desktop.RenderTask) r;
                            ((Desktop.RenderTask) last).merge(rt);
                            Desktop.releaseRenderTask(rt);
                            return;
                        }
                    }

                }

                // enqueue task
                tasks.addElement(r);

                // wake up
                notify();
            }
        }
    }

    public void run() {

        final Vector tasks = this.runnables;

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
                    tasks.setElementAt(null, 0);
                    tasks.removeElementAt(0);
                }
                if (!go) break;
            }

            try {
                task.run();
            } catch (Throwable t) {
                uncaught++;
//#ifdef __LOG__
                t.printStackTrace();
//#endif
            }
        }
    }
}
