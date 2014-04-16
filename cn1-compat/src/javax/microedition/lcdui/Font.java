package javax.microedition.lcdui;

public final class Font {
    public static final int FACE_MONOSPACE = 32;
    public static final int FACE_PROPORTIONAL = 64;
    public static final int FACE_SYSTEM = 0;
    public static final int FONT_INPUT_TEXT = 1;
    public static final int FONT_STATIC_TEXT = 0;
    public static final int SIZE_LARGE = 16;
    public static final int SIZE_MEDIUM = 0;
    public static final int SIZE_SMALL = 8;
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int STYLE_PLAIN = 0;
    public static final int STYLE_UNDERLINED = 4;

    private int face, style, size;
    private com.codename1.ui.Font cn1Font;

    private Font(com.codename1.ui.Font cn1Font, int face, int style, int size) {
        this.face = face;
        this.style = style;
        this.size = size;
        this.cn1Font = cn1Font;
    }

    public int getBaselinePosition() {
        // TODO
        return 0;
    }

    public int charWidth(char ch) {
        return cn1Font.charWidth(ch);
    }

    public int charsWidth(char[] ch, int offset, int length) {
        return cn1Font.charsWidth(ch, offset, length);
    }

    public static Font getDefaultFont() {
        return new Font(com.codename1.ui.Font.getDefaultFont(), FACE_SYSTEM, STYLE_PLAIN, SIZE_MEDIUM);
    }

    public static Font getFont(int face, int style, int size) {
        return new Font(com.codename1.ui.Font.createSystemFont(face, style, size), face, style, size);
    }

    public int getHeight() {
        return cn1Font.getHeight();
    }

    public int getFace() {
        return face;
    }

    public int getSize() {
        return size;
    }

    public int getStyle() {
        return style;
    }

    public int stringWidth(String str) {
        return cn1Font.stringWidth(str);
    }

    public com.codename1.ui.Font getNativeFont() {
        return cn1Font;
    }
}
