// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.Resources;

import javax.microedition.lcdui.Command;

final class ActionCommand extends Command {
    private int action;

    public ActionCommand(int action, int commandType, int priority) {
        super(Resources.getString((short) action), commandType, priority);
        this.action = action;
    }

    public ActionCommand(String prefix, int action, int commandType, int priority) {
        super(prefix  + " " + Resources.getString((short) action), commandType, priority);
        this.action = action;
    }

    public int getAction() {
        return action;
    }
}
