// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import javax.microedition.io.file.FileSystemRegistry;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.Connector;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import java.util.Enumeration;
import java.util.Stack;
import java.util.EmptyStackException;
import java.io.IOException;

public class FileBrowser implements CommandListener {
    private List list;
    private Stack dirs;
    private Command cmdCancel;
    private Command cmdBack;

    private String selection;

    public FileBrowser() {
    }

    public String getSelection() {
        return selection;
    }

    public void browse() {
        Enumeration roots = FileSystemRegistry.listRoots();
        if (roots.hasMoreElements()) {
            dirs = new Stack();
            list = new List("FileBrowser", List.IMPLICIT);
            cmdCancel = new Command("Cancel", Command.CANCEL, 1);
            cmdBack = new Command("Back", Command.BACK, 1);
            Desktop.Event.getDisplay().setCurrent(list);
            show(roots, true);
        } else {
            (new Desktop.Event(Desktop.Event.EVENT_FILE_BROWSER_FINISHED, "No filesystem roots.", new EmptyStackException())).fire();
        }
    }

    private void show(Enumeration entries, boolean noParent) {
        list.deleteAll();
        list.removeCommand(cmdCancel);
        list.removeCommand(cmdBack);
        for (Enumeration e = entries; e.hasMoreElements(); ) {
            list.append((String) e.nextElement(), null);
        }
        list.addCommand(List.SELECT_COMMAND);
        list.addCommand(noParent ? cmdCancel :cmdBack);
        list.setCommandListener(this);
    }

    public void commandAction(Command command, Displayable displayable) {
        boolean quit = true;
        Throwable throwable = null;

        try {
            if (command == List.SELECT_COMMAND) {
                quit = !scan(buildPath(list.getString(list.getSelectedIndex())));
            } else if (command.getCommandType() == Command.BACK) {
                dirs.pop();
                try {
                    quit = !scan((String) dirs.pop());
                } catch (EmptyStackException e) {
                    show(FileSystemRegistry.listRoots(), true);
                    quit = false;
                }
            }
        } catch (IOException e) {
            throwable = e;
        }

        if (quit) {
            // gc
            list = null;

            // we are done
            (new Desktop.Event(Desktop.Event.EVENT_FILE_BROWSER_FINISHED, selection, throwable)).fire();
        }
    }

    private boolean scan(String path) throws IOException {
        boolean deeper = false;

        FileConnection fc = (FileConnection) Connector.open(path, Connector.READ);
        if (fc.isDirectory()) {
            dirs.push(fc.getURL());
            show(fc.list(), false);
            deeper = true;
        } else {
            selection = path;
            System.out.println("selection: " + selection);
        }
        fc.close();

        return deeper;
    }

    private String buildPath(String leaf) {
        StringBuffer sb = new StringBuffer();
        if (dirs.size() > 0) {
            sb.append(dirs.elementAt(dirs.size() - 1));
        } else {
            sb.append("file:///");
        }
        sb.append(leaf);

        return sb.toString();
    }
}
