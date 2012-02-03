package net.trekbuddy.midlet;

import api.location.LocationProvider;
import api.location.LocationException;

public interface IRuntime {
    boolean isRunning();

    int startTracking(LocationProvider provider) throws LocationException;
    void quickstartTracking(LocationProvider provider);
    void restartTracking();
    void stopTracking() throws LocationException;
    void afterTracking();

    LocationProvider getProvider();
}
