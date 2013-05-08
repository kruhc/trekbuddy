package javax.microedition.lcdui;

import com.codename1.ui.events.ActionListener;
import com.codename1.ui.events.ActionEvent;

import java.util.Vector;

public abstract class Displayable implements ActionListener {

    private String title;
    private Vector<Command> commands;
    private Ticker ticker;
    private CommandListener listener;

    private com.codename1.ui.Component component;

    protected Displayable() {
        this.commands = new Vector<Command>();
    }

    protected void setDisplayable(com.codename1.ui.Component component) {
        this.component = component;
        component.setWidth(com.codename1.ui.Display.getInstance().getDisplayWidth());
        component.setHeight(com.codename1.ui.Display.getInstance().getDisplayHeight());
    }

    protected com.codename1.ui.Component getDisplayable() {
        if (component == null) {
            throw new IllegalStateException("native displayable is null; " + getClass());
        }
        return component;
    }

    protected CommandListener getListener() {
        return listener;
    }

    public void addCommand(Command cmd) {
        System.out.println("WARN Displayable.addCommand " + cmd + "|" + cmd.getCommand());
        if (component instanceof com.codename1.ui.Form) {
            System.out.println(" - form command");
            ((com.codename1.ui.Form)component).addCommand(cmd.getCommand());
        }
        commands.addElement(cmd);
    }

    public void removeCommand(Command cmd) {
        if (component instanceof com.codename1.ui.Form) {
            ((com.codename1.ui.Form)component).removeCommand(cmd.getCommand());
        }
        commands.removeElement(cmd);
    }

    public int getHeight() {
        return getDisplayable().getHeight();
    }

    public int getWidth() {
        return getDisplayable().getWidth();
    }

    public boolean isShown() {
        throw new Error("Displayable.isShown not implemented");
    }

    public void setCommandListener(CommandListener l) {
        if (component instanceof com.codename1.ui.Form) {
            listener = l;
            ((com.codename1.ui.Form)component).addCommandListener(this);
        } else {
            throw new IllegalArgumentException(component.getClass().getName());
        }
    }

    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }

    public Ticker getTicker() {
        System.err.println("ERROR Displayable.getTicker not implemented");
//        throw new Error("not implemented");
        return ticker;
    }

    public void setTicker(Ticker ticker) {
        System.err.println("ERROR Displayable.setTicker not implemented");
//        throw new Error("not implemented");
        this.ticker = ticker;
    }

    private Command translateCommand(com.codename1.ui.Command c) {
        for (int N = commands.size(), i = 0; i < N; i++) {
            final Command cmd = (Command) commands.elementAt(i);
            if (cmd.getCommand() == c) {
                System.out.println("INFO Displayable.translate " + cmd);
                return cmd;
            }
        }
        return null;
    }

    public void actionPerformed(ActionEvent actionEvent) {
        System.out.println("Displayable$CommandListenerBridge.actionPerformed; command: " + actionEvent.getCommand());
        listener.commandAction(translateCommand(actionEvent.getCommand()), this);
    }
}
