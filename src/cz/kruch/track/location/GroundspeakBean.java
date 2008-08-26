// @LICENSE@

package cz.kruch.track.location;

/**
 * Groundspeak GPX wpt extension holder.
 */
public final class GroundspeakBean {
    public String id, name;
    public String type, container, difficulty, terrain, country;
    public String shortListing, longListing;
    public String encodedHints;

    public GroundspeakBean(String id) {
        this.id = id;
    }

    public String classify() {
        final StringBuffer sb = new StringBuffer(8);
        if (type.startsWith("Traditional")) {
            sb.append('T');
        } else if (type.startsWith("Multi")) {
            sb.append('M');
        } else if (type.startsWith("Webcam")) {
            sb.append('W');
        } else if (type.startsWith("Earth")) {
            sb.append('E');
        } else if (type.startsWith("Letter")) {
            sb.append('L');
        } else if (type.startsWith("Letter")) {
            sb.append('L');
        } else {
            sb.append('U');
        }
        if (container.startsWith("Micro")) {
            sb.append('M');
        } else if (container.startsWith("Small")) {
            sb.append('S');
        } else if (container.startsWith("Regular")) {
            sb.append('R');
        } else if (container.startsWith("Large")) {
            sb.append('L');
        } else {
            sb.append('U');
        }
        sb.append(difficulty).append('/').append(terrain);

        return sb.toString();
    }

    public String toString() {
        return name;
    }
}
