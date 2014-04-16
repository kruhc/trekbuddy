package javax.microedition.lcdui;

//#define __XAML__

public class Command {
    public static final int BACK = 2;
    public static final int CANCEL = 3;
    public static final int EXIT = 7;
    public static final int HELP = 5;
    public static final int ITEM = 8;
    public static final int OK = 4;
    public static final int SCREEN = 1;
    public static final int STOP = 6;

    private String label, longLable;
    private int commandType, priority;

//#ifdef __XAML__
//#else
    private com.codename1.ui.Command cn1Command;
//#endif

    public Command(String label, int commandType, int priority) {
        this(label, null, commandType, priority);
    }

    public Command(String shortLabel, String longLable, int commandType, int priority) {
        this.label = shortLabel;
        this.longLable = longLable;
        this.commandType = commandType;
        this.priority = priority;
//#ifndef __XAML__
        this.cn1Command = new com.codename1.ui.Command(shortLabel, null);
//#endif
    }

//#ifdef __XAML__

    Object getCommand() {
        return null;
    }

//#else

    com.codename1.ui.Command getCommand() {
        return cn1Command;
    }
    
//#endif

    public String getLabel() {
        return label;
    }

    public String getLongLable() {
        return longLable;
    }

    public int getCommandType() {
        return commandType;
    }

    public int getPriority() {
        return priority;
    }
}