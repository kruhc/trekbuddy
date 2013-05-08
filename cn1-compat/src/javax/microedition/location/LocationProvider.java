package javax.microedition.location;

public class LocationProvider implements com.codename1.location.LocationListener {
    public static final int AVAILABLE = 1;
    public static final int TEMPORARILY_UNAVAILABLE = 2;
    public static final int OUT_OF_SERVICE = 3;

    private static com.codename1.location.LocationManager cn1Manager;

    private LocationListener listener;

    private LocationProvider() {
    }

    public static LocationProvider getInstance(Criteria criteria) throws LocationException {
        if (cn1Manager == null) {
            cn1Manager = com.codename1.location.LocationManager.getLocationManager();
        }
        return new LocationProvider();
    }

    public void setLocationListener(LocationListener listener, int interval, int timeout, int maxAge) {
        this.listener = listener;
        if (listener != null) {
            cn1Manager.setLocationListener(this);
        } else {
            cn1Manager.setLocationListener(null);
        }
    }

    public void reset() {
        System.err.println("ERROR LocationProvider.reset not implemented");
    }

    public void locationUpdated(com.codename1.location.Location location) {
        System.err.println("WARN LocationProvider.locationUpdated; " + location);
        if (listener != null) {
            QualifiedCoordinates qc = new QualifiedCoordinates(location.getLatitude(), location.getLongitude(),
                                                               (float) location.getAltitude(),
                                                               location.getAccuracy(), 0f);
            Location l = new Location(location.getTimeStamp(), qc, location.getVelocity(), location.getDirection());
            listener.locationUpdated(this, l);
        }
    }

    public void providerStateChanged(int newState) {
        System.err.println("WARN LocationProvider.providerStateChanged; " + newState);
        if (listener != null) {
            listener.providerStateChanged(this, newState);
        }
    }
}
