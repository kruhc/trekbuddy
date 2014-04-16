package javax.microedition.lcdui;

//#define __XAML__

public class StringItem extends Item {

//#ifdef __XAML__
    private String text;
//#else
    private com.codename1.ui.TextArea cn1Label;
//#endif

    public StringItem(String label, String text) {
        this(label, text, PLAIN);
    }

    public StringItem(String label, String text, int appearanceMode) {
        super(label);
//#ifdef __XAML__
        this.text = text;
//#else
        this.cn1Label = new com.codename1.ui.TextArea(text == null ? "" : text);
        cn1Label.setEditable(false);
        setContent(this.cn1Label);
//#endif
    }

    public String getText() {
//#ifdef __XAML__
        return text;
//#else
        return cn1Label.getText();
//#endif
    }

    public void setText(String text) {
//#ifdef __XAML__
        this.text = text;
//#else
        cn1Label.setText(text);
//#endif
    }

    public void setFont(Font font) {
        com.codename1.io.Log.p("StringItem.setFont not implemented", com.codename1.io.Log.ERROR);
//        throw new Error("StringItem.setFont not implemented");
    }
}
