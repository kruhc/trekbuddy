// @LICENSE@

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

    public InvalidMapException(String string, String name) {
        super(string);
        this.name = name;
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
