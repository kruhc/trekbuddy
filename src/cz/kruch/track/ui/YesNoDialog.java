// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Alert;

public final class YesNoDialog implements CommandListener {

    public interface AnswerListener {
        public void response(int answer);
    }

    public static final int YES = 0;
    public static final int NO  = 1;

    private Displayable next;
    private AnswerListener callback;

    public YesNoDialog(Displayable next, AnswerListener callback) {
        this.next = next;
        this.callback = callback;
    }

    public void show(String question, String item) {
        Alert dialog = new Alert(null, question, null, null);
        dialog.addCommand(new Command("Yes", Command.OK, 1));
        dialog.addCommand(new Command("No", Command.CANCEL, 1));
        dialog.setCommandListener(this);
        Desktop.display.setCurrent(dialog);
    }

    public void commandAction(final Command command, Displayable displayable) {
        // 'close' the dialog window
        Desktop.display.setCurrent(next);

        // 'return' response code
        callback.response("Yes".equals(command.getLabel()) ? YES : NO);
    }
}
