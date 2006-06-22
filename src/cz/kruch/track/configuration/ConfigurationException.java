// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.configuration;

public class ConfigurationException extends Exception {
    public ConfigurationException(String string) {
        super(string);
    }

    public ConfigurationException(Exception exception) {
        super(exception.toString());
    }
}
