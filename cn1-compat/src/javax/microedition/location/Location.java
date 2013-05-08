package javax.microedition.location;

public class Location {

    private long timestamp;
    private QualifiedCoordinates coordinates;
    private float speed;
    private float course;

    public Location(long timestamp, QualifiedCoordinates coordinates, float speed, float course) {
        this.timestamp = timestamp;
        this.coordinates = coordinates;
        this.speed = speed;
        this.course = course;
    }

    public boolean isValid() {
        return true;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public QualifiedCoordinates getQualifiedCoordinates() {
        return coordinates;
    }

    public float getSpeed() {
        return speed;
    }

    public float getCourse() {
        return course;
    }

    public String getExtraInfo(String mimetype) {
        return null;
    }
}
