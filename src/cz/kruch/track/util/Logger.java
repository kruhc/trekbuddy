// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.util;

import cz.kruch.track.TrackingMIDlet;

public class Logger {
    private static final String LEVEL_DEBUG  = "DEBUG";
    private static final String LEVEL_INFO   = "INFO";
    private static final String LEVEL_WARN   = "WARN";
    private static final String LEVEL_ERROR  = "ERROR";

    private String appname = null;

    public Logger(String appname) {
        this.appname = appname;
    }

    public void debug(String message) {
        log(LEVEL_DEBUG, message);
    }

    public void debug(String message, Throwable t) {
        log(LEVEL_DEBUG, message);
        log(t.toString());
    }

    public void info(String message) {
        log(LEVEL_INFO, message);
    }

    public void warn(String message) {
        log(LEVEL_WARN, message);
    }

    public void error(String message) {
        log(LEVEL_ERROR, message);
    }

    public void error(String message, Throwable t) {
        log(LEVEL_ERROR, message);
        log(t.toString());
    }

    private void log(String severity, String message) /*throws IOException*/ {
        if (TrackingMIDlet.isEmulator()) {
            System.out.println("[" + severity + "] " + appname + " - " + message);
            System.out.flush();
        }
    }

    private void log(String stacktrace) /*throws IOException*/ {
        if (TrackingMIDlet.isEmulator()) {
            System.out.println(stacktrace);
            System.out.flush();
        }
    }
}
