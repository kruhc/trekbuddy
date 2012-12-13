// @LICENSE@

package api.location;

/**
 * Location listener.
 *
 * @author kruhc@seznam.cz
 */
public interface LocationListener {
    public void locationUpdated(LocationProvider provider,
                                Location location);

    public void providerStateChanged(LocationProvider provider,
                                     int newState);

    public void tracklogStateChanged(LocationProvider provider,
                                     boolean isRecording);

    public void orientationChanged(LocationProvider provider,
                                   int heading);
}
