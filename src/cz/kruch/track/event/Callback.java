// @LICENSE@

package cz.kruch.track.event;

/**
 * Eventing callback.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public interface Callback {
    public void invoke(Object result, Throwable throwable, Object source);
}
