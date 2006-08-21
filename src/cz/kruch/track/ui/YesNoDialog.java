// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;

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
        Form form = new Form("YesNo");
        form.append(new StringItem(question, item));
        form.addCommand(new Command("Yes", Command.OK, 1));
        form.addCommand(new Command("No", Command.CANCEL, 1));
        form.setCommandListener(this);
        Desktop.display.setCurrent(form);
    }

    public void commandAction(final Command command, Displayable displayable) {
        // 'close' the dialog window
        Desktop.display.setCurrent(next);

        // 'return' response code
        callback.response("Yes".equals(command.getLabel()) ? YES : NO);
    }
}
