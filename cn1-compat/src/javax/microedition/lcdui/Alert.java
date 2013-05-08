package javax.microedition.lcdui;

public class Alert extends Screen {

    public static final Command DISMISS_COMMAND = new Command("OK", Command.OK, 0);
    public static final int FOREVER = -2;

    private String text;

    private com.codename1.ui.Dialog cn1Alert;

    // hack for Display.setCurrent(alert, displayable);
    Displayable nextDisplayable;

    public Alert(String title) {
        this(title, null, null, null);
    }

    public Alert(String title, String alertText, Image alertImage, AlertType alertType) {
        this.text = alertText;
        this.cn1Alert = new com.codename1.ui.Dialog(title) {
            protected void actionCommand(com.codename1.ui.Command command) {
                System.out.println("WARN Alert.actionCommand; " + command);
                super.actionCommand(command); // disposes the dialog
                if (nextDisplayable != null) {
                    Display.instance.setCurrent(nextDisplayable);
                    nextDisplayable = null;
                }
            }
        };
        this.cn1Alert.setDialogUIID("Alert");
        this.cn1Alert.addComponent(new com.codename1.ui.TextArea(alertText, 5, 24, com.codename1.ui.TextArea.UNEDITABLE));
        if (alertType == AlertType.INFO) {
            cn1Alert.setDialogType(com.codename1.ui.Dialog.TYPE_INFO);
        } else if (alertType == AlertType.WARNING) {
            cn1Alert.setDialogType(com.codename1.ui.Dialog.TYPE_WARNING);
        } else if (alertType == AlertType.ERROR) {
            cn1Alert.setDialogType(com.codename1.ui.Dialog.TYPE_ERROR);
        } else if (alertType == AlertType.CONFIRMATION) {
            cn1Alert.setDialogType(com.codename1.ui.Dialog.TYPE_CONFIRMATION);
        } else if (alertType == AlertType.ALARM) {
            cn1Alert.setDialogType(com.codename1.ui.Dialog.TYPE_ALARM);
        } else {
            cn1Alert.setDialogType(com.codename1.ui.Dialog.TYPE_NONE);
        }
        super.setDisplayable(cn1Alert);
        cn1Alert.placeButtonCommands(new com.codename1.ui.Command[]{ DISMISS_COMMAND.getCommand() });
    }

    public void addCommand(Command cmd) {
        super.addCommand(cmd);
        throw new Error("fix it");
    }

    public void removeCommand(Command cmd) {
        super.removeCommand(cmd);
        throw new Error("fix it");
    }

    public void setTimeout(int time) {
        if (time != FOREVER) {
            cn1Alert.setTimeout(time);
        }
    }

    public void setIndicator(Gauge indicator) {
        throw new Error("Alert.setIndicator not implemented");
    }

    public String getString() {
        return text;
    }
}
