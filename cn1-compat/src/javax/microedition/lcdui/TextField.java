package javax.microedition.lcdui;

//#define __XAML__

public class TextField extends Item {
    public static final int ANY = 0;
    public static final int CONSTRAINT_MASK = 65535;
    public static final int DECIMAL = 5;
    public static final int EMAILADDR = 1;
    public static final int INITIAL_CAPS_SENTENCE = 2097152;
    public static final int INITIAL_CAPS_WORD = 1048576;
    public static final int NON_PREDICTIVE = 524288;
    public static final int NUMERIC = 2;
    public static final int PASSWORD = 65536;
    public static final int PHONENUMBER = 3;
    public static final int SENSITIVE = 262144;
    public static final int UNEDITABLE = 131072;
    public static final int URL = 4;

//#ifdef __XAML__
    private String text;
//#else
    private com.codename1.ui.TextField cn1Field;
//#endif

    public TextField(String label, String text, int maxSize, int constraints) {
        super(label);
//#ifdef __XAML__
        this.text = text;
//#else
        this.cn1Field = new com.codename1.ui.TextField(text);
        setContent(this.cn1Field);
//#endif
    }

    public String getString() {
//#ifdef __XAML__
        return text;
//#else
        return cn1Field.getText();
//#endif
    }

    public void setString(String text) {
//#ifdef __XAML__
        this.text = text;
//#else
        cn1Field.setText(text);
//#endif
    }
}
