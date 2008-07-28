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

package cz.kruch.track.maps;

import java.io.IOException;

/**
 * Calibration processing and map handling exception.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public class InvalidMapException extends IOException {

    private String name;

    public InvalidMapException(String string) {
        super(string);
    }

    public InvalidMapException(Throwable throwable) {
        super(throwable.toString());
    }

    public InvalidMapException(String message, Throwable throwable) {
        super(message + ": " + throwable.toString());
    }

    public void setName(String name) {
        this.name = name;
    }

    public String toString() {
        return "InvalidMapException [" + name + "]: " + getMessage();
    }
}
