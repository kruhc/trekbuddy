// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import api.location.LocationException;

import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import java.util.TimeZone;
import java.util.Vector;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.event.Callback;

public final class InfoForm extends Form implements CommandListener, Callback {
    private Vector caches;
    private StringItem cachesItem;

    public InfoForm() {
        super("Info");
        this.caches = new Vector();
    }

    public void show(Desktop desktop, LocationException le, Object ps) {
        // gc (for memory info to be correct)
        System.gc();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();

        // find caches
        fill();

        // items
        append(new StringItem("Platform", cz.kruch.track.TrackingMIDlet.getPlatform()));
        append(new StringItem("Memory", Long.toString(totalMemory) + "/" + Long.toString(freeMemory)));
        append(new StringItem("AppFlags", cz.kruch.track.TrackingMIDlet.getFlags()));
        append(new StringItem("TimeZone", TimeZone.getDefault().getID() + "; " + TimeZone.getDefault().useDaylightTime() + "; " + TimeZone.getDefault().getRawOffset()));
        append(new StringItem("Jsr75/Fc", "resetable? " + Integer.toString(cz.kruch.track.maps.Map.fileInputStreamResetable) + "; read-skip? " + com.ice.tar.TarInputStream.useReadSkip));
        append(new StringItem("Desktop", "S60 renderer? " + Config.getSafeInstance().isS60renderer() + "; hasRepeatEvents? " + desktop.hasRepeatEvents()));
        cachesItem = new StringItem("Caches", Integer.toString(caches.size()));
        append(cachesItem);
        append(new StringItem("ProviderStatus", (ps == null ? "" : ps.toString()) + "; syncs=" + cz.kruch.track.location.StreamReadingLocationProvider.syncs + "; mismatches=" + cz.kruch.track.location.StreamReadingLocationProvider.mismatches));
        append(new StringItem("ProviderError", le == null ? "" : le.toString()));
        append(new StringItem("SnapshotEncodings", System.getProperty("video.snapshot.encodings")));
        if (caches.size() > 0) {
            addCommand(new Command("Caches", Command.SCREEN, 1));
        }
        addCommand(new Command("Close", Command.BACK, 1));
        setCommandListener(this);

        // show
        Desktop.display.setCurrent(this);
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command.getCommandType() == Command.BACK) {
            // restore desktop UI
            Desktop.display.setCurrent(Desktop.screen);
            // gc hints
            caches.removeAllElements();
            caches = null;
        } else {
            // show cacheOffline mgmt console
            (new ItemSelection(this, "Caches", "Delete",this)).show(caches.elements());
        }
    }

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
}
