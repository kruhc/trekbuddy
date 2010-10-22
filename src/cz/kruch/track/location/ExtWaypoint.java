// @LICENSE@

package cz.kruch.track.location;

import api.location.QualifiedCoordinates;

import java.util.Vector;

/**
 * Extende waypoint with links and groundspeak extensions support.
 */
public class ExtWaypoint extends StampedWaypoint {
    private Object userObject;
    private Vector links;

    public ExtWaypoint(QualifiedCoordinates qc, String name, String comment, long timestamp) {
        super(qc, name, comment, timestamp);
    }

    public ExtWaypoint(QualifiedCoordinates qc, char[] name, char[] comment, char[] sym,
                       long timestamp, GroundspeakBean bean, Vector links) {
        super(qc, name, comment, sym, timestamp);
        this.userObject = bean;
        this.links = links;
    }

    public Object getUserObject() {
        return userObject;
    }

    public Vector getLinks() {
        return links;
    }

    public void addLink(String link) {
        if (this.links == null) {
            this.links = new Vector(4, 4);
        }
        this.links.addElement(link);
    }

    public String getLink(int type) {
        if (links != null) {
            for (int N = links.size(), i = 0; i < N; i++) {
                final String link = (String) links.elementAt(i);
                switch (type) {
                    case LINK_GENERIC_IMAGE: {
                        if (link.endsWith(".jpg") || link.endsWith(".png")) {
                            return link;
                        }
                    } break;
                    case LINK_GENERIC_SOUND: {
                        if (link.endsWith(".amr") || link.endsWith(".wav") || link.endsWith(".mp3") || link.endsWith(".aac") || link.endsWith(".m4a") || link.endsWith(".3gp")) {
                            return link;
                        }
                    } break;
                }
            }
        }

        return null;
    }

    public String toString() {
        if (userObject == null || !cz.kruch.track.configuration.Config.preferGsName) { // TODO ugly dependency
            return super.toString();
        }
        return userObject.toString();
    }
}
