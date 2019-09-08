// @LICENSE@

package api.location;

/**
 * Location exception.
 *
 * @author kruhc@seznam.cz
 */
public class LocationException extends Exception {
    public LocationException(String string) {
        super(string);
    }

    public LocationException(Exception exception) {
        super(exception.toString());
    }
}
