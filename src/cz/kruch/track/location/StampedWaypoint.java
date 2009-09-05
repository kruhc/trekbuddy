// @LICENSE@

package cz.kruch.track.location;

import api.location.QualifiedCoordinates;

/**
 * Waypoint with timestamp.
 */
public class StampedWaypoint extends Waypoint {
    private long timestamp;

    public StampedWaypoint(QualifiedCoordinates qc, char[] name, char[] comment, char[] sym,
                           long timestamp) {
        super(qc, name, comment, sym);
        this.timestamp = timestamp;
    }

    public StampedWaypoint(QualifiedCoordinates qc, String name, String comment,
                           long timestamp) {
        super(qc, name, comment);
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
