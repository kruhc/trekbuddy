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

    private final static String PARENT_DIR      = "..";

    private Callback callback;
    private Displayable next;

    private Command cmdCancel;
    private Command cmdBack;
    private Command cmdSelect;

    private volatile api.file.File file;
    private volatile String path;
    private volatile int depth = 0;

    public FileBrowser(String title, Callback callback, Displayable next) {
        super(title, List.IMPLICIT);
        this.callback = callback;
        this.next = next;
        this.cmdCancel = new Command("Cancel", Command.BACK, 1);
        this.cmdBack = new Command("Back", Command.BACK, 1);
        this.cmdSelect = new Command("Select", Command.SCREEN, 1);
        setCommandListener(this);
        Desktop.display.setCurrent(this);
    }

    public void show() {
        browse();
    }

    public void browse() {
        (new Thread(this)).start();
    }

    public void run() {
        if (depth == 0) {
            try {
                if (file != null) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("close existing fc");
//#endif
                    try {
                        file.close();
                    } catch (IOException e) {
                    }
                }
                file = null;

                show(api.file.File.listRoots());

//#ifdef __LOG__
                if (log.isEnabled()) log.debug("scanner thread exits");
//#endif
            } catch (Throwable t) {
                quit(t);
            }
        } else {
            try {
                boolean isDir = false;
                String url = null;

                if (file == null) {
                    file = new api.file.File(Connector.open("file:///" + path, Connector.READ));
                    url = file.getURL();
                    isDir = file.isDirectory();
                } else {
                    file.setFileConnection(path);
                    url = file.getURL();
                    isDir = url.endsWith(api.file.File.FILE_SEPARATOR);
                }

                if (isDir) {
                    show(file.list());
                } else {
                    quit(null);
                }
            } catch (Throwable t) {
//#ifdef __LOG__
                if (log.isEnabled()) log.error("browse error", t);
//#endif

                quit(t);
            }
        }
    }

    private void show(Enumeration entries) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("show; depth = " + depth);
//#endif

        deleteAll();
        removeCommand(cmdCancel);
        removeCommand(cmdBack);
        setSelectCommand(null);
        if (depth > 0) {
            append(PARENT_DIR, null);
        }
        while (entries.hasMoreElements()) {
            append(entries.nextElement().toString(), null);
        }
//#ifdef __LOG__
        if (log.isEnabled()) log.debug(size() + " entries");
//#endif
        if (size() > 0) {
            setSelectCommand(cmdSelect);
        }
        addCommand(depth == 0 ? cmdCancel :cmdBack);
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command.getCommandType() == Command.SCREEN) {
            path = getString(getSelectedIndex());
            if (PARENT_DIR.equals(path)) {
                depth--;
            } else {
                depth++;
            }
            browse();
        } else if (command.getCommandType() == Command.BACK) {
            depth--;
            if (depth > 0) {
                path = PARENT_DIR;
                browse();
            } else {
                browse();
            }
        } else {
            quit(null);
        }
    }

    private void quit(Throwable throwable) {
        // show parent
        Desktop.display.setCurrent(next);

        // we are done
        callback.invoke(file, throwable);
    }
}
