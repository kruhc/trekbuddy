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
 * @author kruhc@seznam.cz
 */
public class Waypoint {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("Waypoint");
//#endif

    public static final int LINK_GENERIC_IMAGE = 0;
    public static final int LINK_GENERIC_SOUND = 1;

    private static final Vector tokens = new Vector(16, 16);

    private QualifiedCoordinates coordinates;
    private String name;
    private String comment;
    private String sym;

    public Waypoint(QualifiedCoordinates qc, char[] name, char[] comment, char[] sym) {
        this.coordinates = qc;
        this.texts(name, comment);
        if (sym != null && sym.length != 0) {
            this.sym = cache(new String(sym));
        }
    }

    public Waypoint(QualifiedCoordinates qc, String name, String comment) {
        this.coordinates = qc;
        this.name = name;
        this.comment = comment;
    }

    private void texts(char[] name, char[] comment) {
        if (name != null && comment != null) {
            final int nl = name.length;
            final int cl = comment.length;
            final char[] _raw = new char[nl + cl];
            System.arraycopy(name, 0, _raw, 0, nl);
            System.arraycopy(comment, 0, _raw, nl, cl);
            final String _texts = new String(_raw);
            this.name = _texts.substring(0, nl);
            this.comment = _texts.substring(nl);
        } else if (name != null) {
            this.name = new String(name);
        } else { // comment != null
            this.comment = new String(comment);
        }
    }

    public QualifiedCoordinates getQualifiedCoordinates() {
        return coordinates;
    }

    public void setQualifiedCoordinates(QualifiedCoordinates coordinates) {
        this.coordinates = coordinates;
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

    public long getTimestamp() {
        return 0;
    }

    public Object getUserObject() {
        return null;
    }

    public Vector getLinks() {
        return null;
    }

    public String getLink(int type) {
        return null;
    }

    public String toString() {
        return name;
    }

    private static String cache(String input) {
        final Vector tokens = Waypoint.tokens;
        final int i = tokens.indexOf(input);
        if (i > -1) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("token '" + input + "' is cached");
//#endif
            return (String) tokens.elementAt(i);
        }
        if (tokens.size() < 64) { // paranoia
            tokens.addElement(input);
        }
        return input;
    }
}
