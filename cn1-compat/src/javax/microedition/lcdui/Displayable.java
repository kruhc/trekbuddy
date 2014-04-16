package javax.microedition.lcdui;

//#define __XAML__

//#ifndef __XAML__
import com.codename1.ui.events.ActionListener;
import com.codename1.ui.events.ActionEvent;
//#endif

public abstract class Displayable 
//#ifndef __XAML__
        implements ActionListener
//#endif
                                    {

    private String title;
    private java.util.List<Command> commands;
    private Ticker ticker;
    private CommandListener listener;

    private boolean shown;   

//#ifndef __XAML__
    private com.codename1.ui.Component component;
//#endif

    protected Displayable() {
        this.commands = new java.util.ArrayList<Command>();
    }

//#ifndef __XAML__

    protected void setDisplayable(com.codename1.ui.Component component) {
        this.component = component;
//        component.setWidth(com.codename1.ui.Display.getInstance().getDisplayWidth());
//        component.setHeight(com.codename1.ui.Display.getInstance().getDisplayHeight());
        component.setWidth(com.codename1.ui.FriendlyAccess.getImplementation().getDisplayWidth());
        component.setHeight(com.codename1.ui.FriendlyAccess.getImplementation().getDisplayHeight());
    }

    protected com.codename1.ui.Component getDisplayable() {
        if (component == null) {
            //throw new IllegalStateException("native displayable is null; " + getClass());
            com.codename1.io.Log.p("Displayable.getDisplayable component is null", com.codename1.io.Log.WARNING);
        }
        return component;
    }

//#else

    protected CommandListener getListener() {
        return listener;
    }

    protected java.util.List<Command> MIDP_getCommands() {
        return commands; 
    }

//#endif

    void setShown(boolean shown) {
        this.shown = shown;
    }

    public void addCommand(final Command cmd) {
//#ifdef __LOG__
        com.codename1.io.Log.p("Displayable.addCommand " + cmd + "|" + cmd.getCommand(), com.codename1.io.Log.DEBUG);
//#endif
        commands.add(cmd);
//#ifndef __XAML__
        if (component instanceof com.codename1.ui.Form) {
//#ifdef __LOG__
            com.codename1.io.Log.p(" - form command", com.codename1.io.Log.DEBUG);
//#endif
            Display.instance.callSerially(new Runnable() {
                public void run() {
                    ((com.codename1.ui.Form)component).addCommand(cmd.getCommand());
                }
            });
        }
//#endif
    }

    public void removeCommand(final Command cmd) {
        commands.remove(cmd);
//#ifndef __XAML__
        if (component instanceof com.codename1.ui.Form) {
            Display.instance.callSerially(new Runnable() {
                public void run() {
                    ((com.codename1.ui.Form)component).removeCommand(cmd.getCommand());
                }
            });
        }
//#endif
    }

    public int getHeight() {
//#ifdef __XAML__
//        return com.codename1.ui.Display.getInstance().getDisplayWidth();
        return com.codename1.ui.FriendlyAccess.getImplementation().getDisplayWidth();
//#else
        return getDisplayable().getHeight();
//#endif
    }

    public int getWidth() {
//#ifdef __XAML__
//        return com.codename1.ui.Display.getInstance().getDisplayHeight();
        return com.codename1.ui.FriendlyAccess.getImplementation().getDisplayHeight();
//#else
        return getDisplayable().getWidth();
//#endif
    }

    public boolean isShown() {
        return shown;
    }

    public void setCommandListener(CommandListener l) {
        listener = l;
//#ifndef __XAML__
        if (component instanceof com.codename1.ui.Form) {
            Display.instance.callSerially(new Runnable() {
                public void run() {
                    ((com.codename1.ui.Form)component).addCommandListener(Displayable.this);
                }
            });
        }
//#endif
    }

    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }

    public Ticker getTicker() {
        com.codename1.io.Log.p("Displayable.getTicker not implemented", com.codename1.io.Log.WARNING);
        return ticker;
    }

    public void setTicker(Ticker ticker) {
        com.codename1.io.Log.p("Displayable.setTicker not implemented", com.codename1.io.Log.WARNING);
        this.ticker = ticker;
    }

//#ifndef __XAML__

    private Command translateCommand(com.codename1.ui.Command c) {
        for (int N = commands.size(), i = 0; i < N; i++) {
            final Command cmd = commands.get(i);
            if (cmd.getCommand() == c) {
//#ifdef __LOG__
                com.codename1.io.Log.p("Displayable.translate " + cmd, com.codename1.io.Log.DEBUG);
//#endif
                return cmd;
            }
        }
        return null;
    }

    public void actionPerformed(final ActionEvent actionEvent) {
//#ifdef __LOG__
        com.codename1.io.Log.p("Displayable.actionPerformed; command: " + actionEvent.getCommand(), com.codename1.io.Log.DEBUG);
//#endif
        Display.instance.callSerially(new Runnable() {
            public void run() {
                listener.commandAction(translateCommand(actionEvent.getCommand()), Displayable.this);
            }
        });
    }

//#endif

}
