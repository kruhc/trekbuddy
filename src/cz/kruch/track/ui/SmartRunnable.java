/*
 * Copyright 2006-2007 Ales Pour <kruhc@seznam.cz>.
 * All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package cz.kruch.track.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import java.util.Vector;

/**
 * Eventing engine.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class SmartRunnable implements Runnable {
    private final static SmartRunnable instance = new SmartRunnable();

    private final Vector runnables;
    private boolean go, pending;

    private SmartRunnable() {
        this.runnables = new Vector(16);
        this.go = true;
    }

    public static SmartRunnable getInstance() {
        return instance;
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

        synchronized (this) {
            if (go) {

                // trick #1: avoid duplicates of key-hold checks
                if (r instanceof Desktop) { //
                    if (runnables.size() > 0) {
                        if (runnables.lastElement() instanceof Desktop) {
                            return;
                        }
                    }

                } // trick #2: merge render tasks
                  else if (r instanceof Desktop.RenderTask) {
                    if (runnables.size() > 0) {
                        if (runnables.lastElement() instanceof Desktop.RenderTask) {
                            ((Desktop.RenderTask) runnables.lastElement()).merge(((Desktop.RenderTask) r));
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
                // ignore
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
