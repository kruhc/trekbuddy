// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

//#ifdef __LOG__
import cz.kruch.track.util.Logger;
//#endif
import cz.kruch.track.event.Callback;
import cz.kruch.track.util.Arrays;
import cz.kruch.track.util.Comparator;

import javax.microedition.io.Connector;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import java.util.Enumeration;
import java.util.Vector;
import java.io.IOException;

public final class FileBrowser extends List implements CommandListener, Runnable {
//#ifdef __LOG__
    private static final Logger log = new Logger("FileBrowser");
//#endif

    private static final String PARENT_DIR = "..";
    private static final String FILE_PROTOCOL = "file://";

    private Callback callback;
    private Displayable next;

    private Command cmdCancel;
    private Command cmdBack;
    private Command cmdSelect;

    private volatile api.file.File file;
    private volatile String path;
    private volatile int depth = 0;
    private FilenameComparator comparator;

    public FileBrowser(String title, Callback callback, Displayable next) {
        super(title, List.IMPLICIT);
        this.callback = callback;
        this.next = next;
        this.cmdCancel = new Command("Cancel", Command.CANCEL, 1);
        this.cmdBack = new Command("Back", Command.BACK, 1);
        this.cmdSelect = new Command("Select", Command.SCREEN, 1);
        this.comparator = new FilenameComparator();
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

                // dir flag
                boolean isDir;

                // start browsing
                if (file == null) {
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("start browsing " + path);
//#endif

                    // open root dir
                    file = new api.file.File(Connector.open(FILE_PROTOCOL + (path.startsWith("/") ? "" : "/") + path, Connector.READ));
                    isDir = file.isDirectory();

                } else { // traverse
//#ifdef __LOG__
                    if (log.isEnabled()) log.debug("goto " + path);
//#endif
                    // handle broken devices
                    boolean brokenTraversal = false;
//#ifdef __A780__
                    brokenTraversal = cz.kruch.track.TrackingMIDlet.isA780();
//#else
                    brokenTraversal = cz.kruch.track.TrackingMIDlet.isSxg75();
//#endif
                    if (brokenTraversal) {
                        String url = file.getURL();
                        if (PARENT_DIR.equals(path)) {
                            path = "";
                            url = url.substring(0, url.length() - 1);
                            url = url.substring(0, url.lastIndexOf('/') + 1);
                        }
//#ifdef __A780__
                          else {
                            if (url.endsWith("/") && path.startsWith("/")) {
                                url = url.substring(0, url.length() - 1);
                            }
                        }
//#endif
//#ifdef __LOG__
                        if (log.isEnabled()) log.error("SXG75 hack; url: " + url + "+" + path);
//#endif

                        // close existing file
                        try {
                            file.close();
                        } catch (IOException e) {
                            // ignore
                        }
                        file = null; // gc hint

                        // open new fc
                        file = new api.file.File(Connector.open(url + path, Connector.READ));
                        isDir = file.isDirectory();

                    } else {

                        // use traversal capability
                        file.setFileConnection(path);
                        isDir = file.getURL().endsWith(api.file.File.FILE_SEPARATOR);

                    }
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
            append(PARENT_DIR, null);
        }

        /*
         * Sort items - directories first, then files.
         */

        // 1. enumeration to vector
        Vector v = new Vector();
        while (entries.hasMoreElements()) {
            v.addElement((String) entries.nextElement());
        }

        // 2. vector to array
        String[] array = new String[v.size()];
        v.copyInto(array);
        v = null; // gc hint

        // sort array
        Arrays.sort(array, comparator);
        for (int N = array.length, i = 0; i < N; i++) {
            append(array[i], null);
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
        // gc hint
        comparator = null;

        // show parent
        Desktop.display.setCurrent(next);

        // we are done
        callback.invoke(file, throwable);
    }

    private class FilenameComparator implements Comparator {
        public int compare(Object o1, Object o2) {
            String s1 = ((String) o1).toLowerCase();
            String s2 = ((String) o2).toLowerCase();
            boolean isDir1 = s1.endsWith("/");
            boolean isDir2 = s2.endsWith("/");
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
}
