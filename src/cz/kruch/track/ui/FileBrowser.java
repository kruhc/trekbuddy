// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import cz.kruch.track.event.Callback;
import cz.kruch.track.util.Arrays;

import javax.microedition.io.Connector;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import java.util.Enumeration;
import java.io.IOException;

import api.file.File;

public final class FileBrowser extends List implements CommandListener, Runnable {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("FileBrowser");
//#endif

    private Callback callback;
    private Displayable next;

    private Command cmdCancel;
    private Command cmdBack;
    private Command cmdSelect;

    private volatile File file;
    private volatile String path;
    private volatile int depth;

    public FileBrowser(String title, Callback callback, Displayable next) {
        super(title, List.IMPLICIT);
        this.callback = callback;
        this.next = next;
        this.cmdCancel = new Command("Cancel", Command.CANCEL, 1);
        this.cmdBack = new Command("Back", Command.BACK, 1);
        this.cmdSelect = new Command("Select", Command.ITEM, 1);
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
        try {
            if (depth == 0) {

                // close existing fc
                if (file != null) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("close existing fc");
//#endif
                    try {
                        file.close();
                    } catch (IOException e) {
                        // ignore
                    }

                    // gc hint
                    file = null;
                }

                // list fs roots
                show(api.file.File.listRoots());

//#ifdef __LOG__
                if (log.isEnabled()) log.debug("scanner thread exits");
//#endif

            } else {

                // start browsing
                if (file == null) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("start browsing " + path);
//#endif

                    // open root dir
                    file = File.open(Connector.open(api.file.File.FILE_PROTOCOL + (path.startsWith("/") ? "" : "/") + path, Connector.READ));

                } else { // traverse
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("goto " + path);
//#endif

                    // traverse
                    file.setFileConnection(path);
                }

                // dir flag
                boolean isDir;

                if (file.isBrokenTraversal()) {
                    // we know special traversal code is used underneath
                    isDir = file.isDirectory();
                } else {
                    // detect from URL
                    isDir = file.getURL().endsWith(File.PATH_SEPARATOR);
                }

//#ifdef __LOG__
                if (log.isEnabled()) log.error("isDir? " + isDir);
//#endif

                // list dir content
                if (isDir) {
                    show(file.list());
                } else { // otherwise we got a file selected
                    quit(null);
                }
            }
        } catch (Throwable t) {
//#ifdef __LOG__
            if (log.isEnabled()) log.error("browse error", t);
//#endif

            // quit with throwable
            quit(t);
        }
    }

    private void show(Enumeration entries) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("show; depth = " + depth);
//#endif

        // menu options
        deleteAll();
        removeCommand(cmdCancel);
        removeCommand(cmdBack);
        setSelectCommand(null);
        if (depth > 0) {
            append(api.file.File.PARENT_DIR, null);
        }

        // append items
        Arrays.sort2list(this, entries);

//#ifdef __LOG__
        if (log.isEnabled()) log.debug(size() + " entries");
//#endif
        if (size() > 0) {
            setSelectCommand(cmdSelect);
        }
        addCommand(depth == 0 ? cmdCancel : cmdBack);
    }

    public void commandAction(Command command, Displayable displayable) {
        if (Command.ITEM == command.getCommandType()) {
            path = null; // gc hint
            path = getString(getSelectedIndex());
            if (api.file.File.PARENT_DIR.equals(path)) {
                depth--;
            } else {
                depth++;
            }
            browse();
        } else if (Command.BACK == command.getCommandType()) {
            depth--;
            if (depth > 0) {
                path = null; // gc hint
                path = api.file.File.PARENT_DIR;
                browse();
            } else {
                browse();
            }
        } else {
            quit(null);
        }
    }

    private void quit(Throwable throwable) {
        // gc hint
        deleteAll();
        
        // show parent
        Desktop.display.setCurrent(next);

        // we are done
        callback.invoke(file, throwable, this);

        // gc hint
        file = null;
    }
}
