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

package cz.kruch.track.configuration;

/**
 * Configuration exception.
 * 
 * @author Ales Pour <kruhc@seznam.cz>
 */
public class ConfigurationException extends Exception {

    public ConfigurationException(String string) {
        super(string);
    }

    public ConfigurationException(Throwable t) {
        super(t.toString());
    }

    public ConfigurationException(String message, Throwable throwable) {
        super(message + ": " + throwable.toString());
    }
}
