// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.Resources;

import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;

/**
 * Feedback dialog.
 *
 * @author kruhc@seznam.cz
 */
public final class YesNoDialog implements CommandListener, Runnable {

    public interface AnswerListener {
        public void response(int answer, Object closure);
    }

    public static final int YES = 0;
    public static final int NO  = 1;

    private final Displayable next;
    private final AnswerListener callback;
    private final Object closure, item;
    private final String question;

    private int response;

    public YesNoDialog(AnswerListener callback, Object closure, String question, Object item) {
        this(Desktop.display.getCurrent(), callback, closure, question, item);
    }

    public YesNoDialog(Displayable next, AnswerListener callback, Object closure,
                       String question, Object item) {
        this.next = next;
        this.callback = callback;
        this.closure = closure;
        this.question = question;
        this.item = item;
    }

    public void show() {
        final Displayable dialog;
        if (item instanceof StringBuffer) {
            dialog = new TextBox(question, item.toString(), 64, TextField.URL);
//#ifdef __ANDROID__
            ((TextBox) dialog).setString(item.toString());
//#endif
            dialog.addCommand(new Command(Resources.getString(Resources.CMD_OK), Command.OK, 1));
        } else {
            dialog = new Alert("TrekBuddy");
            ((Alert) dialog).setString(question);
            ((Alert) dialog).setTimeout(Alert.FOREVER);
            dialog.addCommand(new Command(Resources.getString(Resources.CMD_YES), Command.OK, 1));
            dialog.addCommand(new Command(Resources.getString(Resources.CMD_NO), Command.CANCEL, 1));
        }
        dialog.setCommandListener(this);
        Desktop.display.setCurrent(dialog);
    }

    public void commandAction(Command command, Displayable displayable) {
        // grab input
        if (item instanceof StringBuffer) {
            final StringBuffer sb = (StringBuffer) item;
            sb.delete(0, sb.length());
            sb.append(((TextBox) displayable).getString());
        }

        // advance to next screen
        Desktop.showNext(displayable, next);

        // return response code
//        callback.response(command.getCommandType() == Command.OK ? YES : NO, closure);
        response = command.getCommandType() == Command.OK ? YES : NO;
        Desktop.getEventWorker().enqueue(this);
    }

    public void run() {
        callback.response(response, closure);
    }
}
