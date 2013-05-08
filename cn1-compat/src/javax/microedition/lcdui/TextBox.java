package javax.microedition.lcdui;

public class TextBox extends Screen {
    private String title, text;
    private int maxSize, constraints;

    public TextBox(String title, String text, int maxSize, int constraints) {
        this.title = title;
        this.text = text;
        this.maxSize = maxSize;
        this.constraints = constraints;
    }

    public String getString() {
        return text;
    }
}
