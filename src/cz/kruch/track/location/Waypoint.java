// @LICENSE@

package cz.kruch.track.location;

import api.location.QualifiedCoordinates;

import java.util.Vector;

/**
 * Waypoint representation.
 *
 * TODO This is in fact <b>Landmark</b> from JSR-179, and so it should be moved
 * to {@link api.location} package.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class Waypoint {
    public static final int LINK_GENERIC_IMAGE = 0;
    public static final int LINK_GENERIC_SOUND = 1;

    private QualifiedCoordinates coordinates;
    private long timestamp;
    private String name;
    private String comment;
    private String sym;

    private Object userObject;
    private Vector links;

    public Waypoint(QualifiedCoordinates qc, char[] name, char[] comment, char[] sym) {
        this.coordinates = qc;
        this.timestamp = -1;
        texts(name, comment, sym);
    }

    public Waypoint(QualifiedCoordinates qc, String name, String comment, long timestamp) {
        this.coordinates = qc;
        this.timestamp = timestamp;
        this.name = name;
        this.comment = comment;
    }

    private void texts(char[] name, char[] comment, char[] sym) {
        int l = 0;
        if (name != null) {
            l += name.length;
        }
        if (comment != null) {
            l += comment.length;
        }
        if (sym != null) {
            l += sym.length;
        }
        char[] _raw = new char[l];
        l = 0;
        if (name != null) {
            System.arraycopy(name, 0, _raw, 0, name.length);
            l += name.length;
        }
        if (comment != null) {
            System.arraycopy(comment, 0, _raw, l, comment.length);
            l += comment.length;
        }
        if (sym != null) {
            System.arraycopy(sym, 0, _raw, l, sym.length);
        }
        String _texts = new String(_raw);
        l = 0;
        if (name != null) {
            this.name = _texts.substring(0, name.length);
            l += name.length;
        }
        if (comment != null) {
            this.comment = _texts.substring(l, l + comment.length);
            l += comment.length;
        }
        if (sym != null) {
            this.sym = _texts.substring(l);
        }
    }

    public QualifiedCoordinates getQualifiedCoordinates() {
        return coordinates;
    }

    public void setQualifiedCoordinates(QualifiedCoordinates coordinates) {
        this.coordinates = coordinates;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getSym() {
        return sym;
    }

    public Vector getLinks() {
        return links;
    }

    public void setLinks(Vector links) {
        this.links = links;
    }

    public void addLink(String link) {
        if (this.links == null) {
            this.links = new Vector(4, 4);
        }
        this.links.addElement(link);
    }

    public void removeLink(String link) {
        if (this.links != null) {
            this.links.removeElement(link);
        }
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
                        if (link.endsWith(".amr") || link.endsWith(".wav") || link.endsWith(".mp3")) {
                            return link;
                        }
                    } break;
                }
            }
        }

        return null;
    }

    public Object getUserObject() {
        return userObject;
    }

    public void setUserObject(Object userObject) {
        this.userObject = userObject;
    }

    public String toString() {
        if (userObject == null || !cz.kruch.track.configuration.Config.preferGsName) // TODO ugly dependency
            return name;
        return userObject.toString();
    }
}
