package javax.microedition.lcdui;

public class AlertType {
    public static final AlertType INFO = new AlertType();
    public static final AlertType WARNING = new AlertType();
    public static final AlertType ERROR = new AlertType();
    public static final AlertType ALARM = new AlertType();
    public static final AlertType CONFIRMATION = new AlertType();

    protected AlertType() {
    }

    public boolean playSound(Display display) {
        System.err.println("ERROR AlertType.playSound not implemented");
//        throw new Error("AlertType.playSound not implemented");
        return false;
    }
}
