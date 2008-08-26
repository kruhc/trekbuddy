// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.Resources;

import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Alert;

/**
 * Generic YES-NO dialog.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class YesNoDialog extends Alert implements CommandListener {

    public interface AnswerListener {
        public void response(int answer, Object closure);
    }

    public static final int YES = 0;
    public static final int NO  = 1;

    private final Displayable next;
    private final AnswerListener callback;
    private final Object closure;

    public YesNoDialog(Displayable next, AnswerListener callback, Object closure,
                       String question, String item) {
        super(null, question, null, null);
        this.next = next != null ? next : Desktop.display.getCurrent();
        this.callback = callback;
        this.closure = closure;
    }

    public void show() {
        addCommand(new Command(Resources.getString(Resources.CMD_YES), Command.OK, 1));
        addCommand(new Command(Resources.getString(Resources.CMD_NO), Command.CANCEL, 1));
        setCommandListener(this);
        Desktop.display.setCurrent(this);
    }

    public void commandAction(Command command, Displayable displayable) {
        // advance to next screen
        Desktop.display.setCurrent(next);

        // return response code
        callback.response(command.getCommandType() == Command.OK ? YES : NO, closure);
    }
}
