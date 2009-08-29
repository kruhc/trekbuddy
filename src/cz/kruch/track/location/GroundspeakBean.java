// @LICENSE@

package cz.kruch.track.location;

import java.util.Vector;
import java.io.UnsupportedEncodingException;

/**
 * Groundspeak and Australian GPX wpt extension holder.
 */
public final class GroundspeakBean {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("GroundspaekBean");
//#endif

    private static final String UTF_8 = "UTF-8";
    private static final Vector tokens = new Vector(16, 16);

    private String ns;
    private String id, name;
    private String encodedHints;
    private String type, container, difficulty, terrain, country;
    private Object shortListing, longListing;

    public GroundspeakBean(String ns, String id) {
        this.ns = ns;
        this.id = id;
    }

    public String getNs() {
        return ns;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEncodedHints() {
        return encodedHints;
    }

    public void setEncodedHints(String encodedHints) {
        this.encodedHints = encodedHints;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = cache(type);
    }

    public String getContainer() {
        return container;
    }

    public void setContainer(String container) {
        this.container = cache(container);
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = cache(difficulty);
    }

    public String getTerrain() {
        return terrain;
    }

    public void setTerrain(String terrain) {
        this.terrain = cache(terrain);
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = cache(country);
    }

    public String getShortListing() {
        if (shortListing instanceof byte[]) {
            try {
                return new String((byte[]) shortListing, UTF_8);
            } catch (UnsupportedEncodingException e) {
                // should never happen
            }
        }
        return (String) shortListing;
    }

    public void setShortListing(String shortListing) {
        try {
            this.shortListing = shortListing.getBytes(UTF_8);
        } catch (UnsupportedEncodingException e) {
            this.shortListing = shortListing;
        }
    }

    public String getLongListing() {
        if (longListing instanceof byte[]) {
            try {
                return new String((byte[]) longListing, UTF_8);
            } catch (UnsupportedEncodingException e) {
                // should never happen
            }
        }
        return (String) longListing;
    }

    public void setLongListing(String longListing) {
        try {
            this.longListing = longListing.getBytes(UTF_8);
        } catch (UnsupportedEncodingException e) {
            this.longListing = longListing;
        }
    }

    public String classify() {
        final StringBuffer sb = new StringBuffer(8);
        sb.append(type.charAt(0)).append(container.charAt(0));
        sb.append(difficulty).append('/').append(terrain);

        return sb.toString();
    }

    public String toString() {
        return name;
    }

    private String cache(String input) {
        final Vector tokens = GroundspeakBean.tokens;
        final int i = tokens.indexOf(input);
        if (i > -1) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("token '" + input + "' is cached");
//#endif
            return (String) tokens.elementAt(i);
        }
        if (tokens.size() < 256) { // paranoia
            tokens.addElement(input);
        }
        return input;
    }
}
