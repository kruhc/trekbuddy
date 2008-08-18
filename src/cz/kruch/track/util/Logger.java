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

package cz.kruch.track.util;

import java.util.Date;

/**
 * Logger for debugging - output goes to System.out.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class Logger {
    private static final String LEVEL_DEBUG  = "DEBUG";
    private static final String LEVEL_INFO   = "INFO";
    private static final String LEVEL_WARN   = "WARN";
    private static final String LEVEL_ERROR  = "ERROR";

    private String cname;
    private boolean enabled;

    public Logger(String componentName) {
        this.cname = componentName;
//#ifdef __LOG__
        this.enabled = cz.kruch.track.TrackingMIDlet.isLogEnabled();
//#endif        
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

    private void log(String severity, String message) {
        if (enabled) {
            System.out.println("[" + (new Date()) + "] " + cname + " - " + message);
            System.out.flush();
        }
    }

    private void log(Throwable t) {
        if (enabled) {
            t.printStackTrace();
            System.err.flush();
        }
    }
}
