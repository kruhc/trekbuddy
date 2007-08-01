// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.configuration;

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
