// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

//#ifdef __LOG__
import cz.kruch.track.util.Logger;
//#endif
import cz.kruch.track.event.Callback;

import javax.microedition.io.Connector;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import java.util.Enumeration;
import java.io.IOException;

public final class FileBrowser extends List implements CommandListener, Runnable {
//#ifdef __LOG__
    private static final Logger log = new Logger("FileBrowser");
//#endif

    private Callback callback;

    private Command cmdCancel;
    private Command cmdBack;
    private Command cmdSelect;

    private volatile api.file.File fc;
    private volatile String path;
    private volatile String selection;
    private volatile int depth = 0;

    public FileBrowser(String title, Callback callback) {
        super(title, List.IMPLICIT);
        this.callback = callback;
        this.cmdCancel = new Command("Cancel", Command.CANCEL, 1);
        this.cmdBack = new Command("Back", Command.BACK, 1);
        this.cmdSelect = new Command("Select", Command.SCREEN, 1);
        setCommandListener(this);
        Desktop.display.setCurrent(this);
    }

    public void show() {
        browse();
    }

    public String getSelection() {
        return selection;
    }

    public void browse() {
        (new Thread(this)).start();
    }

    public void run() {
        if (depth == 0) {
            try {
                if (fc != null) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("close existing fc");
//#endif
                    try {
                        fc.close();
                    } catch (IOException e) {
                    }
                }
                fc = null;

                show(api.file.File.listRoots());

//#ifdef __LOG__
                if (log.isEnabled()) log.debug("scanner thread exits");
//#endif
            } catch (Throwable t) {
                quit(t);
            }
        } else {
            try {
                if (fc == null) {
                    fc = new api.file.File(Connector.open("file:///" + path, Connector.READ));
                } else {
                    fc.setFileConnection(path);
                }

                if (fc.isDirectory()) {
                    show(fc.list());
                } else {
                    selection = fc.getURL();
                    quit(null);
                }
            } catch (Throwable t) {
                quit(t);
            }
        }
    }

    private void show(Enumeration entries) {
        deleteAll();
        removeCommand(cmdCancel);
        removeCommand(cmdBack);
        setSelectCommand(null);
        for (Enumeration e = entries; e.hasMoreElements(); ) {
            append((String) e.nextElement(), null);
        }
        if (size() == 0) {
            append(depth == 0 ? "<no roots>" : "<empty>", null);
        } else {
            setSelectCommand(cmdSelect);
        }
        addCommand(depth == 0 ? cmdCancel :cmdBack);
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command.getCommandType() == Command.SCREEN) {
            depth++;
            path = getString(getSelectedIndex());
            browse();
        } else if (command.getCommandType() == Command.BACK) {
            depth--;
            if (depth > 0) {
                path = "..";
                browse();
            } else {
                browse();
            }
        } else {
            quit(null);
        }
    }

    private void quit(Throwable throwable) {
        // close connection
        if (fc != null) {
//#ifdef __LOG__
            if (log.isEnabled()) log.debug("join left fc");
//#endif
            try {
                fc.close();
            } catch (IOException e) {
            }
        }

        // gc hint
        fc = null;

        // restore desktop
        Desktop.display.setCurrent(Desktop.screen);

        // we are done
        callback.invoke(selection, throwable);
    }
}
