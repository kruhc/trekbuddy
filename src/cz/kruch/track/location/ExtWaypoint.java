// @LICENSE@

package cz.kruch.track.location;

import api.location.QualifiedCoordinates;

import java.util.Vector;

import cz.kruch.track.util.NakedVector;

/**
 * Extended waypoint with links and groundspeak extensions support.
 */
public class ExtWaypoint extends StampedWaypoint {
    private Object userObject;
    private /*Vector*/Object links;

    public ExtWaypoint(QualifiedCoordinates qc, String name, String comment, long timestamp) {
        super(qc, name, comment, timestamp);
    }

    public ExtWaypoint(QualifiedCoordinates qc, char[] name, char[] comment, char[] sym,
                       long timestamp, GroundspeakBean bean, Vector links) {
        super(qc, name, comment, sym, timestamp);
        this.userObject = bean;
        if (links != null && links.size() != 0) {
            if (links.size() == 1) {
                this.links = links.elementAt(0);
            } else {
                this.links = new NakedVector((NakedVector) links);
            }
        }
    }

    public Object getUserObject() {
        return userObject;
    }

    public Vector getLinks() {
        if (links == null || links instanceof Vector) {
            return (Vector) links;
        }
        final Vector v = new Vector(1);
        v.addElement(links);
        return v;
    }

    public void addLink(String link) {
        if (this.links == null) {
            this.links = link;
        } else {
            if (this.links instanceof String) {
                final String s = (String) this.links;
                this.links = new Vector(2, 2);
                ((Vector) this.links).addElement(s);
            }
            ((Vector) this.links).addElement(link);
        }
    }

    public String getLink(int type) {
        if (links != null) {
            if (links instanceof String) {
                final String link = (String) this.links;
                if (isTypedLink(link.toLowerCase(), type)) {
                    return link;
                }
            } else {
                final Vector links = (Vector) this.links;
                for (int N = links.size(), i = 0; i < N; i++) {
                    final String link = (String) links.elementAt(i);
                    if (isTypedLink(link.toLowerCase(), type)) {
                        return link;
                    }
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

    private static boolean isTypedLink(final String link, final int type) {
        boolean result = false;
        switch (type) {
            case LINK_GENERIC_IMAGE: {
                result = link.endsWith(".jpg") || link.endsWith(".png");
            } break;
            case LINK_GENERIC_SOUND: {
                result = link.endsWith(".amr") || link.endsWith(".wav") || link.endsWith(".mp3") || link.endsWith(".aac") || link.endsWith(".m4a") || link.endsWith(".3gp");
            } break;
        }
        return result;
    }
}
