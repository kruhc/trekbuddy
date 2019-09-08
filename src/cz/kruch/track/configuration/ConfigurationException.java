// @LICENSE@

package cz.kruch.track.configuration;

/**
 * Configuration exception.
 * 
 * @author kruhc@seznam.cz
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
