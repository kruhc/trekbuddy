package javax.microedition.lcdui;

import com.codename1.ui.Label;

public class StringItem extends Item {

    private Label cn1Label;

    public StringItem(String label, String text) {
        this(label, text, PLAIN);
    }

    public StringItem(String label, String text, int appearanceMode) {
        super(label);
        this.cn1Label = new Label(text);
        setContent(this.cn1Label);
    }

    public String getText() {
        return cn1Label.getText();
    }

    public void setText(String text) {
        cn1Label.setText(text);
    }

    public void setFont(Font font) {
        System.err.println("ERROR StringItem.setFont not implemented");
//        throw new Error("not implemented");
    }
}
