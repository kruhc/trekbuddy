// Copyright 2001-2006 Systinet Corp. All rights reserved.
// Use is subject to license terms.

package cz.kruch.track.ui;

import api.location.LocationException;

import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import java.util.TimeZone;

public final class InfoForm extends Form implements CommandListener {
    public InfoForm() {
        super("Info");
    }

    public void show(Desktop desktop, LocationException le, Object ps) {
        // gc (for memory info to be correct)
        System.gc();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();

        append(new StringItem("Memory", Long.toString(totalMemory) + "/" + Long.toString(freeMemory)));
        append(new StringItem("AppFlags", cz.kruch.track.TrackingMIDlet.getFlags()));
        append(new StringItem("Platform", cz.kruch.track.TrackingMIDlet.getPlatform()));
        append(new StringItem("TimeZone", TimeZone.getDefault().getID() + "; " + TimeZone.getDefault().useDaylightTime() + "; " + TimeZone.getDefault().getRawOffset()));
        append(new StringItem("Jsr75/Fc", "resetable? " + Integer.toString(cz.kruch.track.maps.Map.fileInputStreamResetable) + "; read-skip? " + com.ice.tar.TarInputStream.useReadSkip));
        append(new StringItem("Desktop", "S60 renderer? " + cz.kruch.track.TrackingMIDlet.isNokia() + "; hasRepeatEvents? " + desktop.hasRepeatEvents()));
        append(new StringItem("ProviderStatus", (ps == null ? "" : ps.toString()) + "; syncs=" + cz.kruch.track.location.StreamReadingLocationProvider.syncs + "; mismatches=" + cz.kruch.track.location.StreamReadingLocationProvider.mismatches));
        append(new StringItem("ProviderError", le == null ? "" : le.toString()));
        append(new StringItem("SnapshotEncodings", System.getProperty("video.snapshot.encodings")));
        addCommand(new Command("Close", Command.BACK, 1));
        setCommandListener(this);

        // show
        Desktop.display.setCurrent(this);
    }

    public void commandAction(Command command, Displayable displayable) {
        Desktop.display.setCurrent(Desktop.screen);
    }
}
