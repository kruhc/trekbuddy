// @LICENSE@

package cz.kruch.track.ui;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.event.Callback;
import cz.kruch.track.Resources;

import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;

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

    private Command cmdClose, cmdBack, cmdSelect;
//#ifdef __B2B__
    private Command cmdDir;
//#endif

    private volatile List list;
    private volatile File file;
    private volatile String path, folder;
    private volatile String[] filter;
    private volatile int depth;
    private volatile int history = -1;
//#ifdef __B2B__
    private boolean quitDir;
//#endif

    public FileBrowser(final String title, final Callback callback,
                       final Displayable next, final String folder,
                       final String[] filter) {
        this.title = Resources.prefixed(title);
        this.callback = callback;
        this.next = next;
        this.folder = folder;
        this.filter = filter;
        this.cmdClose = new Command(Resources.getString(Resources.CMD_CLOSE), Desktop.BACK_CMD_TYPE, 1);
        this.cmdBack = new Command(Resources.getString(Resources.CMD_BACK), Desktop.BACK_CMD_TYPE, 1);
        this.cmdSelect = new Command(Resources.getString(Resources.DESKTOP_CMD_OPEN), Desktop.SELECT_CMD_TYPE, 0);
//#ifdef __B2B__
        if (filter == null) {
            this.cmdDir = new Command(Resources.getString(Resources.VENDOR_CMD_OPEN_GUIDE), Desktop.SELECT_CMD_TYPE, 1);
        }
//#endif
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
                        final String folder = this.folder;
                        final String dir = Config.getFolderURL(folder);
                        file = File.open(dir);
                        if (file.exists()) {

                            // calculate and fix depth
                            for (int i = dir.length(); --i >= 0; ) {
                                if (dir.charAt(i) == File.PATH_SEPCHAR) {
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
                if (File.isBrokenTraversal()) {
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
//#ifdef __B2B__
                    if (quitDir)
                        quit(null);
                    if (!quitDir)
//#endif                    
                        show(file);
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
        if (command == cmdSelect) {
            path = null; // gc hint
            path = list.getString(list.getSelectedIndex());
            if (File.PARENT_DIR.equals(path)) {
                depth--;
            } else {
                depth++;
            }
            browse();
//#ifdef __B2B__
        } else if (command == cmdDir) {
            path = list.getString(list.getSelectedIndex());
            quitDir = true;
            browse();
//#endif
        } else {
            depth--;
            if (depth < 0) {
                quit(null);
            } else {
                if (depth > 0) {
                    path = null; // gc hint
                    path = File.PARENT_DIR;
                }
                browse();
            }
        }
    }

    private void show(final File holder) {
//#ifdef __LOG__
        if (log.isEnabled()) log.debug("show; depth = " + depth);
//#endif

        // append items
        list = null; // gc hint
        try {
            final Enumeration items = holder == null ? File.listRoots() : file.list();
            final String head = depth > 0 ? File.PARENT_DIR : null;
            list = new List(title, List.IMPLICIT, sort2array(items, head, filter), null);
//#ifdef __LOG__
            if (log.isEnabled()) log.debug(list.size() + " entries");
//#endif

            // add commands
            if (list.size() > 0) {
                list.setSelectCommand(cmdSelect);
//#ifdef __B2B__
                if (cmdDir != null) {
                    list.addCommand(cmdDir);
                }
//#endif
            } else {
                list.setSelectCommand(null);
            }
            list.addCommand(depth == 0 ? cmdClose : cmdBack);
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
        callback.invoke(throwable == null ? file : null, throwable, this);

        // gc hint
        file = null;
    }

    /**
     * Sorts enumeration of strings and creates array.
     *
     * @param items enumeration of strings
     * @param head first entry; can be <tt>null</tt>
     * @param allowed array of allowed extensions
     * @return list
     */
    public static String[] sort2array(final Enumeration items,
                                      final String head,
                                      final String[] allowed) {
        // enum to list
        Vector v = new Vector(64, 64);
        if (head != null) {
            v.addElement(head);
        }
        while (items.hasMoreElements()) {
            final String item = (String) items.nextElement();
            if (File.isDir(item)) {
                v.addElement(item);
            } else if (allowed != null) {
                final String itemlc = item.toLowerCase();
                for (int i = allowed.length; --i >= 0; ) {
                    if (itemlc.endsWith(allowed[i])) {
                        v.addElement(item);
                    }
                }
            } else {
                v.addElement(item);
            }
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
