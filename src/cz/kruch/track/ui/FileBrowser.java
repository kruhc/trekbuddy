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

import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Ticker;
import java.util.Enumeration;
import java.util.Vector;
import java.io.IOException;

import api.file.File;
import api.util.Comparator;

/**
 * Generic file browser.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class FileBrowser implements CommandListener, Runnable, Comparator {
//#ifdef __LOG__
    private static final cz.kruch.track.util.Logger log = new cz.kruch.track.util.Logger("FileBrowser");
//#endif

    private String title;
    private Callback callback;
    private Displayable next;

    private Command cmdCancel, cmdBack, cmdSelect;

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

    /* when used as filenames comparator */
    private FileBrowser() {
    }

    public void show() {
        // browse
        browse();
    }

    private void browse() {
        // on background
        (new Thread(this)).start();
    }

    public int compare(Object o1, Object o2) {
        return compareAsFiles((String) o1, (String) o2);
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
                        file = File.open(dataDir);
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
                            show(file);

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
                    show(null);
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
                    file = File.open(File.FILE_PROTOCOL + (path.startsWith("/") ? "" : "/") + path);

                } else { // traverse
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("goto " + path);
//#endif

                    // traverse
                    file.setFileConnection(path);
                }

                // dir flag
                final boolean isDir;
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
                if (isDir) { try {
                    show(file); } catch (Exception e) { throw new IllegalStateException("3. [" + file.getURL() + "];" + e.toString()); }
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
            }
            browse();
        } else {
            quit(null);
        }
    }

    private void show(final File holder) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("show; depth = " + depth);
//#endif

        // reuse current list to show what is going on
        if (list != null) {
            list.setCommandListener(null);
            list.deleteAll();
            list.removeCommand(cmdCancel);
            list.removeCommand(cmdBack);
            list.setTicker(new Ticker(Resources.getString(Resources.NAV_MSG_TICKER_LISTING)));
            Thread.yield();
        }

        // append items
        list = null; // gc hint
        try {
            list = sort2list(title, holder == null ? File.listRoots() : file.list(), depth > 0 ? File.PARENT_DIR : null);
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
            list.setCommandListener(this);

            // show
            Desktop.display.setCurrent(list);

        } catch (Throwable t) {
            quit(t);
        }
    }

    private void quit(final Throwable throwable) {
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
     * Sorts enumeration of strings and creates array.
     * TODO optimize - how to find out size of enumeration???
     * @param items enumeration of strings
     * @param head first entry; can be <tt>null</tt>
     * @return list
     */
    public static String[] sort2array(final Enumeration items, final String head) {
        // enum to list
        Vector v = new Vector(64, 64);
        if (head != null) {
            v.addElement(head);
        }
        while (items.hasMoreElements()) {
            v.addElement(items.nextElement());
        }

        // list to array
        final String[] array = new String[v.size()];
        v.copyInto(array);

        // gc hint
        v.removeAllElements();
        v = null;

        // sort array
        quicksort(array, 0, array.length - 1);

        // result
        return array;
    }

    /**
     * Sorts enumeration of strings and creates list.
     * TODO optimize - how to find out size of enumeration???
     *
     * @param title list title
     * @param items enumeration of strings
     * @param head first entry; can be <tt>null</tt>
     * @return list
     */
    public static List sort2list(final String title, final Enumeration items, final String head) {
        return new List(title, List.IMPLICIT, sort2array(items, head), null);
    }

    /**
     * Array sorting - in-place quicksort. See http://en.wikipedia.org/wiki/Quicksort.
     *
     * @param array array of objects
     * @param left left boundary
     * @param right right boundary
     */
    public static void quicksort(final Object[] array, final int left, final int right) {
        quicksort(array, new FileBrowser(), left, right);
    }

    /**
     * Array sorting - in-place quicksort. See http://en.wikipedia.org/wiki/Quicksort.
     *
     * @param array array of objects
     * @param comparator comparator
     * @param left left boundary
     * @param right right boundary
     */
    public static void quicksort(final Object[] array, final Comparator comparator,
                                 final int left, final int right) {
        if (right > left) {
            final int pivotIndex = left;
            final int pivotNewIndex = partition(array, comparator, left, right, pivotIndex);
            quicksort(array, comparator, left, pivotNewIndex - 1);
            quicksort(array, comparator, pivotNewIndex + 1, right);
        }
    }

    private static int partition(final Object[] array, final Comparator comparator,
                                 final int left, final int right, final int pivotIndex) {
        final Object pivotValue = array[pivotIndex];
/*
        // swap inlined
        Object _o = array[pivotIndex];
        array[pivotIndex] = array[right];
        array[right] = _o;
        // ~swap
*/      swap(array, pivotIndex, right);
        int storeIndex = left;
        for (int i = left; i < right; i++) { // left ? i < right
            final int cmp = comparator.compare(array[i], pivotValue);
            if (cmp < 0) {
/*
                // swap
                _o = array[i];
                array[i] = array[storeIndex];
                array[storeIndex] = _o;
                // ~swap
*/              swap(array, i, storeIndex);
                storeIndex++;
            }
        }
/*
        // swap
        _o = array[storeIndex];
        array[storeIndex] = array[right];
        array[right] = _o;
        // ~swap
*/      swap(array, storeIndex, right);
        return storeIndex;
    }

    private static void swap(final Object array[], final int a, final int b) {
        final Object o = array[a];
        array[a] = array[b];
        array[b] = o;
    }

    /**
     * Compares objects as filenames, with directories first.
     *
     * @param s1 first string
     * @param s2 second string
     * @return same as {@link String#compareTo(String)}
     */
    private static int compareAsFiles(final String s1, final String s2) {
        final boolean isDir1 = File.isDir(s1) || s1.indexOf(File.PATH_SEPCHAR) > -1;
        final boolean isDir2 = File.isDir(s2) || s2.indexOf(File.PATH_SEPCHAR) > -1;
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
