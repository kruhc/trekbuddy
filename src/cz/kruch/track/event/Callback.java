// @LICENSE@

package cz.kruch.track.event;

/**
 * Eventing callback.
 *
 * @author kruhc@seznam.cz
 */
public interface Callback {
    public void invoke(Object result, Throwable throwable, Object source);
}
