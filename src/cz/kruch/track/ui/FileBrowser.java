/*
 * Copyright 2006-2007 Ales Pour <kruhc@seznam.cz>.
 * All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package cz.kruch.track.ui;

import cz.kruch.track.event.Callback;
import cz.kruch.track.Resources;
import cz.kruch.track.configuration.Config;

import javax.microedition.io.Connector;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import java.util.Enumeration;
import java.util.Vector;
import java.io.IOException;

import api.file.File;

/**
 * Generic file browser.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class FileBrowser implements CommandListener, Runnable {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("FileBrowser");
//#endif

    private final String title;
    private final Callback callback;
    private final Displayable next;

    private final Command cmdCancel, cmdBack, cmdSelect;

    private volatile List list;
    private volatile File file;
    private volatile String path;
    private volatile int depth;
    private volatile int history = -1;

    public FileBrowser(String title, Callback callback, Displayable next) {
        this.title = Resources.prefixed(title);
        this.callback = callback;
        this.next = next;
        this.cmdCancel = new Command(Resources.getString(Resources.CMD_CANCEL), Command.CANCEL, 1);
        this.cmdBack = new Command(Resources.getString(Resources.CMD_BACK), Command.BACK, 1);
        this.cmdSelect = new Command(Resources.getString(Resources.DESKTOP_CMD_SELECT), Command.ITEM, 1);
    }

    public void show() {
        // browse
        browse();
    }

    private void browse() {
        // on background
        (new Thread(this)).start();
    }

    public void run() {
        history++;
        try {
            if (depth == 0) {
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("fresh start");
//#endif

                // fresh start
                if (history == 0) {

                    // maximum robustness here needed
                    try {
                        // try DataDir first
                        final String dataDir = Config.getDataDir();
                        file = File.open(Connector.open(dataDir, Connector.READ));
                        if (file.exists()) {

                            // calculate and fix depth
                            for (int i = dataDir.length(); --i >= 0; ) {
                                if (dataDir.charAt(i) == File.PATH_SEPCHAR) {
                                    depth++;
                                }
                            }

                            // discount sepchars from file protocol
                            depth -= 3;

                            // list directory
                            show(file.list());

                            return;
                        }
                    } catch (Throwable t) {
                        // ignore
                    }

                    // as selected
                    run();

                } else {

                    // close existing fc
                    if (file != null) {
                        try {
                            file.close();
                        } catch (IOException e) {
                            // ignore
                        }
                        file = null; // gc hint
                    }

                    // list fs roots
                    show(File.listRoots());

                }
//#ifdef __LOG__
                if (log.isEnabled()) log.debug("scanner thread exits");
//#endif
            } else {

                // hack (J9 only?)
                if (depth == 1 && "/".equals(path)) {
                    path = "//";
                }

                // start browsing
                if (file == null) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("start browsing " + path);
//#endif

                    // open root dir
                    file = File.open(Connector.open(File.FILE_PROTOCOL + (path.startsWith("/") ? "" : "/") + path, Connector.READ));

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

        // release current list
        if (list != null) {
            list.deleteAll();
            list = null;
        }

        // append items
        list = sort2list(title, entries, depth > 0 ? File.PARENT_DIR : null);
        list.setCommandListener(this);
//#ifdef __LOG__
        if (log.isEnabled()) log.debug(list.size() + " entries");
//#endif

        // add commands
        if (list.size() > 0) {
            list.setSelectCommand(cmdSelect);
        } else {
            list.setSelectCommand(null);
        }
        list.addCommand(depth == 0 ? cmdCancel : cmdBack);

        // show
        Desktop.display.setCurrent(list);
    }

    public void commandAction(Command command, Displayable displayable) {
        if (Command.ITEM == command.getCommandType()) {
            path = null; // gc hint
            path = list.getString(list.getSelectedIndex());
            if (File.PARENT_DIR.equals(path)) {
                depth--;
            } else {
                depth++;
            }
            browse();
        } else if (Command.BACK == command.getCommandType()) {
            depth--;
            if (depth > 0) {
                path = null; // gc hint
                path = File.PARENT_DIR;
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
        if (list != null) {
            list.deleteAll();
            list = null;
        }
        
        // show parent
        Desktop.display.setCurrent(next);

        // we are done
        callback.invoke(file, throwable, this);

        // gc hint
        file = null;
    }

    /**
     * Sorts enumeration of strings and creates list.
     * TODO optimize - how to find out size of enumeration???
     * @param title list title
     * @param items enumeration of strings
     * @param head first entry; can be <tt>null</tt>
     * @return list
     */
    public static List sort2list(String title, Enumeration items, String head) {
        // enum to list
        Vector v = new Vector(16, 16);
        if (head != null) {
            v.addElement(head);
        }
        while (items.hasMoreElements()) {
            v.addElement((String) items.nextElement());
        }

        // list to array
        String[] array = new String[v.size()];
        v.copyInto(array);

        // gc hint
        v.removeAllElements();
        v = null;

        // sort array
        quicksort(array, 0, array.length - 1);

        // return list
        return new List(title, List.IMPLICIT, array, null);
    }

    /**
     * String array sorting - in-place quicksort. See http://en.wikipedia.org/wiki/Quicksort.
     * @param array array of strings
     * @param left left boundary
     * @param right right boundary
     */
    public static void quicksort(String[] array, final int left, final int right) {
        if (right > left) {
            final int pivotIndex = left;
            final int pivotNewIndex = partition(array, left, right, pivotIndex);
            quicksort(array, left, pivotNewIndex - 1);
            quicksort(array, pivotNewIndex + 1, right);
        }
    }

    private static int partition(String[] array, final int left, final int right, final int pivotIndex) {
        String pivotValue = array[pivotIndex];
        // swap
        String _o = array[pivotIndex];
        array[pivotIndex] = array[right];
        array[right] = _o;
        // ~swap
        int storeIndex = left;
        for (int i = left; i < right; i++) { // left ? i < right
            if (compareAsFiles(array[i], pivotValue) < 0) {
                // swap
                _o = array[i];
                array[i] = array[storeIndex];
                array[storeIndex] = _o;
                // ~swap
                storeIndex++;
            }
        }
        // swap
        _o = array[storeIndex];
        array[storeIndex] = array[right];
        array[right] = _o;
        // ~swap
        return storeIndex;
    }

    /**
     * Compares objects as filenames, with directories first.
     * @param s1 first string
     * @param s2 second string
     * @return {@link String#compareTo(String)}
     */
    private static int compareAsFiles(String s1, String s2) {
        boolean isDir1 = File.isDir(s1);
        boolean isDir2 = File.isDir(s2);
        if (isDir1) {
            if (isDir2) {
                return s1.compareTo(s2);
            } else {
                return -1;
            }
        } else {
            if (isDir2) {
                return 1;
            } else {
                return s1.compareTo(s2);
            }
        }
    }
}
