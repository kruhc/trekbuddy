// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.util;

import cz.kruch.track.TrackingMIDlet;

public final class Logger {
    private static final String LEVEL_DEBUG  = "DEBUG";
    private static final String LEVEL_INFO   = "INFO";
    private static final String LEVEL_WARN   = "WARN";
    private static final String LEVEL_ERROR  = "ERROR";

    private String cname;
    private boolean enabled;

    public Logger(String componentName) {
        this.cname = componentName;
        this.enabled = TrackingMIDlet.isLogEnabled();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void debug(String message) {
        log(LEVEL_DEBUG, message);
    }

    public void debug(String message, Throwable t) {
        log(LEVEL_DEBUG, message);
        log(t);
    }

    public void info(String message) {
        log(LEVEL_INFO, message);
    }

    public void warn(String message) {
        log(LEVEL_WARN, message);
    }

    public void warn(String message, Throwable t) {
        log(LEVEL_WARN, message);
        log(t);
    }

    public void error(String message) {
        log(LEVEL_ERROR, message);
    }

    public void error(String message, Throwable t) {
        log(LEVEL_ERROR, message);
        log(t);
    }

    private void log(String severity, String message) /*throws IOException*/ {
        if (enabled) {
            System.out.println("[" + severity + "] " + cname + " - " + message);
            System.out.flush();
        }
    }

    private void log(Throwable t) /*throws IOException*/ {
        if (enabled) {
            t.printStackTrace();
            System.err.flush();
        }
    }
}
