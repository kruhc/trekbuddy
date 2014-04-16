package javax.microedition.lcdui;

import com.codename1.ui.FriendlyAccess;

//#define __XAML__

public class Alert extends Screen {

    public static final Command DISMISS_COMMAND = new Command("OK", Command.OK, 0);
    public static final int FOREVER = -2;

//#ifdef __XAML__
    private String text;
    private int timeout = FOREVER;
    private AlertType type;
//#else
    private com.codename1.ui.Dialog cn1Alert;
//#endif

    // hack for Display.setCurrent(alert, displayable);
    Displayable nextDisplayable;

    public Alert(String title) {
        this(title, null, null, null);
    }

    public Alert(String title, String alertText, Image alertImage, AlertType alertType) {
        init(title, alertText, alertImage, alertType);
        super.addCommand(DISMISS_COMMAND);
//#ifdef __XAML__
//#else
        cn1Alert.placeButtonCommands(new com.codename1.ui.Command[]{ DISMISS_COMMAND.getCommand() });
//#endif
    }

    // non-MIDP
    public Alert(String title, String alertText, Image alertImage, AlertType alertType,
                 Command[] commands) {
        init(title, alertText, alertImage, alertType);
//#ifdef __XAML__
//#else
        com.codename1.ui.Command[] cmds = new com.codename1.ui.Command[commands.length];
//#endif
        for (int N = commands.length, i = 0; i < N; i++) {
            super.addCommand(commands[i]);
//#ifdef __XAML__
//#else
            cmds[i] = commands[i].getCommand();
//#endif
        }
//#ifdef __XAML__
//#else
        cn1Alert.placeButtonCommands(cmds);
//#endif
    }

//#ifdef __XAML__

    public void show() {
        FriendlyAccess.execute("show-alert", new Object[]{ this });
    }

    public int MIDP_getTimeout() {
        return timeout;
    }

    public AlertType MIDP_getType() {
        return type;
    }

//#endif

    private void init(String title, String alertText, Image alertImage, AlertType alertType) {
        super.setTitle(title);
//#ifdef __XAML__
        this.text = alertText;
        this.type = alertType;
//#else
        this.cn1Alert = new com.codename1.ui.Dialog(title) {

            protected void actionCommand(com.codename1.ui.Command command) {
//#ifdef __LOG__
                com.codename1.io.Log.p("Alert.actionCommand; " + command + "; nd? " + nextDisplayable, com.codename1.io.Log.WARNING);
//#endif
                super.actionCommand(command); // disposes the dialog
                setCurrent();
            }

            public void dispose() {
                super.dispose();
                setCurrent();
            }

            private void setCurrent() {
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
//#endif
    }

    public void addCommand(Command cmd) {
        super.addCommand(cmd);
        com.codename1.io.Log.p("WARN Alert.addCommand fix it", com.codename1.io.Log.WARNING);
    }

    public void removeCommand(Command cmd) {
        super.removeCommand(cmd);
        com.codename1.io.Log.p("WARN Alert.removeCommand fix it", com.codename1.io.Log.WARNING);
    }

    public void setTimeout(int time) {
        if (time != FOREVER) {
//#ifdef __XAML__
            timeout = time;
//#else
            cn1Alert.setTimeout(time);
//#endif
        }
    }

    public void setIndicator(Gauge indicator) {
        com.codename1.io.Log.p("Form.setIndicator not implemented", com.codename1.io.Log.ERROR);
    }

    public String getString() {
//#ifdef __XAML__
        return text;
//#else
        return ((com.codename1.ui.TextArea)cn1Alert.getContentPane().getComponentAt(0)).getText();
//#endif
    }
}
