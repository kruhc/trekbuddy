// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import api.location.LocationException;

import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Item;
import java.util.TimeZone;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.maps.Map;

public final class InfoForm extends Form implements CommandListener/*, Callback*/ {
/* OBSOLETE
    private Vector caches;
    private StringItem cachesItem;
*/

    public InfoForm() {
        super(cz.kruch.track.TrackingMIDlet.wm ? "Info (TrekBuddy)" : "Info");
/* OBSOLETE
        this.caches = new Vector();
*/
    }

    public void show(Desktop desktop, LocationException le, Object ps, Map map) {
        // gc (for memory info to be correct)
        System.gc();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();

/* OBSOLETE
        // find caches
        fill();
*/

        // items
        append(newItem("Platform", cz.kruch.track.TrackingMIDlet.getPlatform()));
        append(newItem("Memory", Long.toString(totalMemory) + "/" + Long.toString(freeMemory)));
        append(newItem("ExtraJsr", (api.file.File.fsType == api.file.File.FS_JSR75 ? "75 " : "")
                                   + (cz.kruch.track.TrackingMIDlet.jsr82 ? "82 " : "")
                                   + (cz.kruch.track.TrackingMIDlet.jsr120 ? "120 " : "")
                                   + (cz.kruch.track.TrackingMIDlet.jsr135 ? "135 " : "")
                                   + (cz.kruch.track.TrackingMIDlet.jsr179 ? "179" : "")));
        append(newItem("AppFlags", cz.kruch.track.TrackingMIDlet.getFlags()));
        append(newItem("Fs", "type? " + Integer.toString(api.file.File.fsType) + "; resetable? " + Integer.toString(cz.kruch.track.maps.Map.fileInputStreamResetable) + "; graphics? " + (System.getProperty("fileconn.dir.graphics") == null ? System.getProperty("filconn.dir.graphics") : System.getProperty("fileconn.dir.graphics"))));
        append(newItem("Ports", System.getProperty("microedition.commports")));
        append(newItem("TimeZone", TimeZone.getDefault().getID() + "; " + TimeZone.getDefault().useDaylightTime() + "; " + TimeZone.getDefault().getRawOffset()));
        append(newItem("Desktop", "S60 renderer? " + Config.S60renderer + "; hasRepeatEvents? " + desktop.hasRepeatEvents() + "; " + Desktop.screen.getWidth() + "x" + Desktop.screen.getHeight()));
        if (map == null) {
            append(newItem("Map", ""));
        } else {
            append(newItem("Map", "datum: " + map.getDatum() + " projection: " + map.getProjection()));
        }
/* OBSOLETE
        cachesItem = new StringItem("Caches", Integer.toString(caches.size()));
        cachesItem.setLayout(Item.LAYOUT_NEWLINE_AFTER);
        append(cachesItem);
*/
        append(new StringItem("ProviderStatus", (ps == null ? "" : ps.toString()) + "; syncs=" + cz.kruch.track.location.StreamReadingLocationProvider.syncs + "; mismatches=" + cz.kruch.track.location.StreamReadingLocationProvider.mismatches));
        append(new StringItem("ProviderError", le == null ? "" : le.toString()));
        if (cz.kruch.track.TrackingMIDlet.supportsVideoCapture()) {
            append(new StringItem("SnapshotEncodings", System.getProperty("video.snapshot.encodings")));
        }
/* OBSOLETE
        if (caches.size() > 0) {
            addCommand(new Command("Caches", Command.SCREEN, 1));
        }
*/
        addCommand(new Command("Close", Command.BACK, 1));
        setCommandListener(this);

        // show
        Desktop.display.setCurrent(this);
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command.getCommandType() == Command.BACK) {
            // restore desktop UI
            Desktop.display.setCurrent(Desktop.screen);
/* OBSOLETE
            // gc hints
            caches.removeAllElements();
            caches = null;
*/
        }
/* OBSOLETE
        else {
            // show cacheOffline mgmt console
            (new ItemSelection(this, "Caches", "Delete",this)).show(caches.elements());
        }
*/
    }

/* OBSOLETE
    public void invoke(Object result, Throwable throwable) {
        if (result != null) {
            try {
                RecordStore.deleteRecordStore("cache_" + (String) result + ".bin");
                RecordStore.deleteRecordStore("cache_" + (String) result + ".set");
                fill();
                cachesItem.setText(Integer.toString(caches.size()));
                Desktop.showInfo("Cache " + (String) result + " deleted", this);
            } catch (RecordStoreException e) {
                Desktop.showError("Failed to delete cacheOffline " + (String) result, e, this);
            }
        }
    }

    private void fill() {
        caches.removeAllElements();
        String[] rsList = RecordStore.listRecordStores();
        if (rsList != null && rsList.length > 0) {
            for (int N = rsList.length, i = 0; i < N; i++) {
                if (rsList[i].startsWith("cache_")) {
                    String cname = rsList[i].substring("cache_".length(),
                                                       rsList[i].length() - 4);
                    if (!caches.contains(cname)) {
                        caches.addElement(cname);
                    }
                }
            }
        }
    }
*/

    private static StringItem newItem(String label, String text) {
        StringItem item = new StringItem(label, text);
        item.setLayout(Item.LAYOUT_NEWLINE_AFTER);
        return item;
    }
}
