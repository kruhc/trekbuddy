package javax.microedition.location;

public interface LocationListener {

    void locationUpdated(LocationProvider provider, Location location);

    void providerStateChanged(LocationProvider provider, int newState);
}
