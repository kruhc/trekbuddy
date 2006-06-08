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
import java.util.EmptyStackException;
import java.io.IOException;

public class FileBrowser implements CommandListener {
    private Display display;
    private Displayable previous;

    private List list;
    private Command cmdCancel;
    private Command cmdBack;

    private FileConnection fc;
    private String selection;
    private int depth;

    public FileBrowser(Display display) {
        this.display = display;
        this.previous = display.getCurrent();
    }

    public String getSelection() {
        return selection;
    }

    public void browse() {
        Enumeration roots = FileSystemRegistry.listRoots();
        if (roots.hasMoreElements()) {
            list = new List("FileBrowser", List.IMPLICIT);
            cmdCancel = new Command("Cancel", Command.CANCEL, 1);
            cmdBack = new Command("Back", Command.BACK, 1);
            depth = 0;
            Desktop.Event.getDisplay().setCurrent(list);
            show(roots);
        } else {
            (new Desktop.Event(Desktop.Event.EVENT_FILE_BROWSER_FINISHED, "No filesystem roots.", new EmptyStackException())).fire();
        }
    }

    private void show(Enumeration entries) {
        list.deleteAll();
        list.removeCommand(cmdCancel);
        list.removeCommand(cmdBack);
        for (Enumeration e = entries; e.hasMoreElements(); ) {
            list.append((String) e.nextElement(), null);
        }
        list.addCommand(List.SELECT_COMMAND);
        list.addCommand(depth == 0 ? cmdCancel :cmdBack);
        list.setCommandListener(this);
        System.out.println("show depth " + depth);
    }

    public void commandAction(Command command, Displayable displayable) {
        boolean quit = true;
        Throwable throwable = null;

        try {
            if (command == List.SELECT_COMMAND) {
                depth++;
                quit = !scan(list.getString(list.getSelectedIndex()));
            } else if (command.getCommandType() == Command.BACK) {
                depth--;
                if (depth > 0) {
                    quit = !scan("..");
                } else {
                    quit = false;
                    fc.close();
                    fc = null;
                    show(FileSystemRegistry.listRoots());
                }
            }
        } catch (IOException e) {
            throwable = e;
        }

        if (quit) {
            // close connection
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException e) {
                }
            }

            // gc
            fc = null;
            list = null;

            // restore previous screen
            display.setCurrent(previous);

            // we are done
            (new Desktop.Event(Desktop.Event.EVENT_FILE_BROWSER_FINISHED, selection, throwable)).fire();
        }
    }

    private boolean scan(String path) throws IOException {
        boolean deeper = false;

        if (fc == null) {
            fc = (FileConnection) Connector.open("file:///" + path, Connector.READ);
        } else {
            fc.setFileConnection(path);
        }

        if (fc.isDirectory()) {
            show(fc.list("*", false));
            deeper = true;
        } else {
            selection = fc.getURL();
        }

        return deeper;
    }
}
