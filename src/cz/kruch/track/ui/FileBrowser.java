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
        // browse
        browse();
    }

    public void browse() {
        // on background
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
                show(File.listRoots());

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

        // menu options
        deleteAll();
        removeCommand(cmdCancel);
        removeCommand(cmdBack);
        setSelectCommand(null);
        if (depth > 0) {
            append(File.PARENT_DIR, null);
        }

        // append items
        sort2list(this, entries);

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
        deleteAll();
        
        // show parent
        Desktop.display.setCurrent(next);

        // we are done
        callback.invoke(file, throwable, this);

        // gc hint
        file = null;
    }

    /*
     * Helper method for filesystem-friendly sorting.
     * 2006-09-04 made public
     */

    // TODO optimize
    /**
     * Sorts enumeration of strings and populates instance of List directly(!).
     * @param list list
     * @param items enumeration of strings
     */
    public static void sort2list(List list, Enumeration items) {
        // enum to list
        Vector v = new Vector(8, 8);
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
        sort(array);

        // add items sorted
        for (int N = array.length, i = 0; i < N; i++) {
            list.append(array[i], null);
        }

        // gc hint
        for (int i = array.length; --i >= 0; ) {
            array[i] = null;
        }
    }

    /*
     * String array sorting. From JDK.
     */

    private static void sort(final String[] a) {
        String aux[] = new String[a.length];
        System.arraycopy(a, 0, aux, 0, a.length);
        mergeSort(aux, a, 0, a.length);
    }

    private static void mergeSort(final String src[], final String dest[],
                                  final int low, final int high) {
        int length = high - low;

        // small arrays sorting
        if (length < 7) {
            for (int i = low; i < high; i++) {
                for (int j = i; j > low && compareAsFiles(dest[j - 1], dest[j]) > 0; j--) {
                    swap(dest, j, j - 1);
                }
            }
            return;
        }

        // half
        final int mid = (low + high) >> 1;
        mergeSort(dest, src, low, mid);
        mergeSort(dest, src, mid, high);

        /*
         * If list is already sorted, just copy from src to dest.  This is an
         * optimization that results in faster sorts for nearly ordered lists.
         */
        if (compareAsFiles(src[mid - 1], src[mid]) <= 0) {
            System.arraycopy(src, low, dest, low, length);
            return;
        }

        // merge sorted halves (now in src) into dest
        for (int i = low, p = low, q = mid; i < high; i++) {
            if (q >= high || p < mid && compareAsFiles(src[p], src[q]) <= 0) {
                dest[i] = src[p++];
            } else {
                dest[i] = src[q++];
            }
        }
    }

    private static void swap(final String x[], final int a, final int b) {
        String t = x[a];
        x[a] = x[b];
        x[b] = t;
    }

    /*
     * ~
     */

    /*
     * Compares objects as filenames, with directories first.
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
