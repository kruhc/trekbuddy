// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.util.Logger;
import cz.kruch.track.event.Callback;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileSystemRegistry;
import javax.microedition.io.file.FileConnection;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import java.util.Enumeration;
import java.io.IOException;

public class FileBrowser extends List implements CommandListener {
    private static final Logger log = new Logger("FileBrowser");

    private Display display;
    private Callback callback;
    private Displayable previous;

    private Command cmdCancel;
    private Command cmdBack;

    private volatile FileConnection fc;
    private volatile String path;
    private volatile String selection;
    private volatile int depth = 0;

    public FileBrowser(String title, Display display, Callback callback) {
        super(title, List.IMPLICIT);
        this.display = display;
        this.callback = callback;
        this.previous = display.getCurrent();
        this.cmdCancel = new Command("Cancel", Command.CANCEL, 1);
        this.cmdBack = new Command("Back", Command.BACK, 1);
        setCommandListener(this);
        display.setCurrent(this);
    }

    public void show() {
        browse(ShowRoots);
    }

    public String getSelection() {
        return selection;
    }

    public void browse(Runnable r) {
        (new Thread(r)).start();
    }

    private void show(Enumeration entries) {
        deleteAll();
        removeCommand(cmdCancel);
        removeCommand(cmdBack);
        for (Enumeration e = entries; e.hasMoreElements(); ) {
            append((String) e.nextElement(), null);
        }
        if (size() == 0 && log.isEnabled()) {
            append("<empty>", null);
        }
        addCommand(List.SELECT_COMMAND);
        addCommand(depth == 0 ? cmdCancel :cmdBack);
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command == List.SELECT_COMMAND) {
            depth++;
            path = getString(getSelectedIndex());
            browse(ShowDirectory);
        } else if (command.getCommandType() == Command.BACK) {
            depth--;
            if (depth > 0) {
                path = "..";
                browse(ShowDirectory);
            } else {
                browse(ShowRoots);
            }
        } else {
            quit(null);
        }
    }

    private void quit(Throwable throwable) {
        // close connection
        if (fc != null) {
            if (log.isEnabled()) log.debug("join left fc");
            try {
                fc.close();
            } catch (IOException e) {
            }
        }

        // gc hint
        fc = null;

        // restore previous screen
        display.setCurrent(previous);

        // we are done
        callback.invoke(selection, throwable);
    }

    private Runnable ShowDirectory = new Runnable() {
        public void run() {
            try {
                if (fc == null) {
                    fc = (FileConnection) Connector.open("file:///" + path, Connector.READ);
                } else {
                    fc.setFileConnection(path);
                }

                if (fc.isDirectory()) {
                    show(fc.list("*", false));
                } else {
                    selection = fc.getURL();
                    quit(null);
                }
            } catch (Throwable t) {
                quit(t);
            }

            if (log.isEnabled()) log.debug("scanner thread exits");
        }
    };

    private Runnable ShowRoots = new Runnable() {
        public void run() {
            try {
                if (fc != null) {
                    if (log.isEnabled()) log.debug("close existing fc");
                    try {
                        fc.close();
                    } catch (IOException e) {
                    }
                }
                fc = null;

                show(FileSystemRegistry.listRoots());

                if (log.isEnabled()) log.debug("scanner thread exits");
            } catch (Throwable t) {
                quit(t);
            }
        }
    };
}
