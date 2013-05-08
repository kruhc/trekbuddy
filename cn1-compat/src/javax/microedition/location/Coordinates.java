package javax.microedition.location;

public class Coordinates {
    public static final int DD_MM_SS = 1;
    public static final int DD_MM = 2;

    private double latitude;
    private double longitude;
    private float altitude;

    public Coordinates(double latitude, double longitude, float altitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
    }

    public double getLatitude() {
        return this.latitude;
    }

    public double getLongitude() {
        return this.longitude;
    }

    public float getAltitude() {
        return this.altitude;
    }

    public void setAltitude(float altitude) {
        this.altitude = altitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public float azimuthTo(Coordinates to) {
        throw new Error("not implemented");
    }

    public float distance(Coordinates to) {
        throw new Error("not implemented");
    }
}
