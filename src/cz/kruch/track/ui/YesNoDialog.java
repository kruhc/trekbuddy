// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;

public class YesNoDialog implements CommandListener {

    public interface AnswerListener {
        public void response(int answer);
    }

    public static final int YES = 0;
    public static final int NO  = 1;

    private Display display;
    private Displayable previous;
    private AnswerListener callback;

    public YesNoDialog(Display display, AnswerListener callback) {
        this.display = display;
        this.previous = display.getCurrent();
        this.callback = callback;
    }

    public void show(String question, String item) {
        Form form = new Form("YesNo");
        form.append(new StringItem(question, item));
        form.addCommand(new Command("Yes", Command.OK, 1));
        form.addCommand(new Command("No", Command.CANCEL, 1));
        form.setCommandListener(this);
        display.setCurrent(form);
    }

    public void commandAction(final Command command, Displayable displayable) {
        // stop handling key presses immediately
        displayable.setCommandListener(null);

        // 'close' the dialog window
        display.setCurrent(previous);

        // 'return' response code
        callback.response("Yes".equals(command.getLabel()) ? YES : NO);
    }
}
