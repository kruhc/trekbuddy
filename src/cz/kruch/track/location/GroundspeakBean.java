// @LICENSE@

package cz.kruch.track.location;

import java.util.Vector;
import java.io.UnsupportedEncodingException;

/**
 * Groundspeak or Australian GPX wpt extension holder.
 */
public final class GroundspeakBean {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("GroundspaekBean");
//#endif

    private static final String UTF_8 = "UTF-8";
    private static final Vector tokens = new Vector(16, 16);

    private static final int IDX_TYPE       = 0;
    private static final int IDX_CONTAINER  = 1;
    private static final int IDX_DIFF       = 2;
    private static final int IDX_TERRAIN    = 3;
    private static final int IDX_COUNTRY    = 4;
    private static final int IDX_SHORTL     = 5;
    private static final int IDX_LONGL      = 6;
    private static final int IDX_HINTS      = 7;
    private static final int IDX_LOGS       = 8;

    private static final int N_PROPERTIES   = 9;

    private String ns;
    private String id, name;
    private Object properties;

    public GroundspeakBean(String ns, String id) {
        this.ns = ns;
        this.id = id;
    }

    public void ctor() {
        this.properties = new Object[N_PROPERTIES];
    }

    public boolean isParsed() {
        return properties instanceof Object[];
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

    public int getFileOffset() {
        if (this.properties instanceof Integer) {
            return ((Integer) this.properties).intValue();
        }
        throw new IllegalStateException("File offset not set (" + properties + ")");
    }

    public void setFileOffset(int offset) {
        this.properties = new Integer(offset);
    }

    public String getEncodedHints() {
        return (String) getProperties()[IDX_HINTS];
    }

    public void setEncodedHints(String encodedHints) {
        this.getProperties()[IDX_HINTS] = encodedHints;
    }

    public String getType() {
        return (String) getProperties()[IDX_TYPE];
    }

    public void setType(String type) {
        this.getProperties()[IDX_TYPE] = cache(type);
    }

    public String getContainer() {
        return (String) getProperties()[IDX_CONTAINER];
    }

    public void setContainer(String container) {
        this.getProperties()[IDX_CONTAINER] = cache(container);
    }

    public String getDifficulty() {
        return (String) getProperties()[IDX_DIFF];
    }

    public void setDifficulty(String difficulty) {
        this.getProperties()[IDX_DIFF] = cache(difficulty);
    }

    public String getTerrain() {
        return (String) getProperties()[IDX_TERRAIN];
    }

    public void setTerrain(String terrain) {
        this.getProperties()[IDX_TERRAIN] = cache(terrain);
    }

    public String getCountry() {
        return (String) getProperties()[IDX_COUNTRY];
    }

    public void setCountry(String country) {
        this.getProperties()[IDX_COUNTRY] = cache(country);
    }

    public String getShortListing() {
        if (getProperties()[IDX_SHORTL] instanceof byte[]) {
            try {
                return new String((byte[]) getProperties()[IDX_SHORTL], UTF_8);
            } catch (UnsupportedEncodingException e) {
                // should never happen
            }
        }
        return (String) getProperties()[IDX_SHORTL];
    }

    public void setShortListing(String shortListing) {
        try {
            this.getProperties()[IDX_SHORTL] = shortListing.getBytes(UTF_8);
        } catch (UnsupportedEncodingException e) {
            this.getProperties()[IDX_SHORTL] = shortListing;
        }
    }

    public String getLongListing() {
        if (getProperties()[IDX_LONGL] instanceof byte[]) {
            try {
                return new String((byte[]) getProperties()[IDX_LONGL], UTF_8);
            } catch (UnsupportedEncodingException e) {
                // should never happen
            }
        }
        return (String) getProperties()[IDX_LONGL];
    }

    public void setLongListing(String longListing) {
        try {
            this.getProperties()[IDX_LONGL] = longListing.getBytes(UTF_8);
        } catch (UnsupportedEncodingException e) {
            this.getProperties()[IDX_LONGL] = longListing;
        }
    }

    public Vector getLogs() {
        return (Vector) getProperties()[IDX_LOGS];
    }

    public void addLog(Log log) {
        final Object[] p = getProperties();
        if (p[IDX_LOGS] == null) {
            p[IDX_LOGS] = new Vector(4);
        }
        ((Vector) p[IDX_LOGS]).addElement(log);
    }

    public String classify() {
        final Object[] p = getProperties();
        final StringBuffer sb = new StringBuffer(8);
        sb.append(charAt0((String) p[IDX_TYPE]))
                .append(charAt0((String) p[IDX_CONTAINER]))
                .append(((String) p[IDX_DIFF]))
                .append('/')
                .append(((String) p[IDX_TERRAIN]));

        return sb.toString();
    }

    public String toString() {
        return name;
    }

    private Object[] getProperties() {
        return (Object[]) properties;
    }

    private char charAt0(final String property) {
        if (property != null) {
            return property.charAt(0);
        }
        return '?';
    }

    private static String cache(String input) {
        final Vector tokens = GroundspeakBean.tokens;
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

    public static class Log {
        public static final String[] TYPES = { "Found it", "Didn't find it", "Write note", "Needs Maintenance", "Owner Maintenance" };
        public static final javax.microedition.lcdui.Image[] ICONS = new javax.microedition.lcdui.Image[TYPES.length];
        public static javax.microedition.lcdui.Image UNKNOWN = null;

        static {
            try {
                ICONS[0] = javax.microedition.lcdui.Image.createImage("/resources/log.foundit.png");
                ICONS[1] = javax.microedition.lcdui.Image.createImage("/resources/log.dnf.png");
                ICONS[2] = javax.microedition.lcdui.Image.createImage("/resources/log.wn.png");
                ICONS[3] = javax.microedition.lcdui.Image.createImage("/resources/log.nm.png");
                ICONS[4] = javax.microedition.lcdui.Image.createImage("/resources/log.om.png");
                UNKNOWN = javax.microedition.lcdui.Image.createImage("/resources/log.any.png");
            } catch (Exception e) {
//#ifdef __ANDROID__
                android.util.Log.w("TrekBuddy", "Failed to load GC icon", e);
//#endif
            }
        }

        private String id;
        private String date, type, finder;
        private String text;
        private javax.microedition.lcdui.Image icon;

        public Log(String id) {
            this.id = id;
            this.icon = UNKNOWN;
        }

        public String getId() {
            return id;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            if (date != null) {
                final int idx = date.indexOf("T00:00:00");
                if (idx > -1) {
                    date = date.substring(0, idx);
                }
            }
            this.date = date;
        }

        public String getFinder() {
            return finder;
        }

        public void setFinder(String finder) {
            this.finder = finder;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = cache(text);
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            javax.microedition.lcdui.Image icon = UNKNOWN;
            final String[] types = TYPES;
            for (int i = types.length; --i >= 0; ) {
                if (types[i].equals(type)) {
                    type = types[i];
                    icon = ICONS[i];
                    break;
                }
            }
            this.type = type;
            this.icon = icon;
        }

        public javax.microedition.lcdui.Image getIcon() {
            return icon;
        }

        public String toString() {
            return date;
        }
    }
}
