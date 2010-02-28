// @LICENSE@

package cz.kruch.track.ui;

import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Item;
import java.util.TimeZone;

import cz.kruch.track.configuration.Config;
import cz.kruch.track.maps.Map;
import cz.kruch.track.Resources;

import api.file.File;

/**
 * Info form.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class InfoForm implements CommandListener {

    private Throwable le, te;
    private Object ps;
    private Map map;

    InfoForm() {
    }

    public void show(Desktop desktop, Throwable le, Throwable te,
                     Object ps, Map map) {
        // members
        this.le = le;
        this.te = te;
        this.ps = ps;
        this.map = map;

        // items
        final Form pane = new Form(Resources.prefixed(Resources.getString(Resources.DESKTOP_CMD_INFO)));
        pane.append(newItem(Resources.getString(Resources.INFO_ITEM_VENDOR), Resources.getString(Resources.INFO_ITEM_VENDOR_VALUE)));
        pane.append(newItem(Resources.getString(Resources.INFO_ITEM_VERSION), cz.kruch.track.TrackingMIDlet.version));
        pane.append(newItem(Resources.getString(Resources.INFO_ITEM_KEYS), ""));
        pane.append(Resources.getString((short) (Resources.INFO_ITEM_KEYS_MS + desktop.getMode())));
        pane.addCommand(new Command(Resources.getString(Resources.INFO_CMD_DETAILS), Desktop.POSITIVE_CMD_TYPE, 0));
        pane.addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Desktop.BACK_CMD_TYPE, 1));
        pane.setCommandListener(this);

        // show
        Desktop.display.setCurrent(pane);
    }

    private void details(final Form pane,
                         final Throwable le, final Throwable te,
                         final Object ps, final Map map) {
        // gc - for memory info to be correct...
        System.gc(); // unconditional!!!
        final long totalMemory = Runtime.getRuntime().totalMemory();
        final long freeMemory = Runtime.getRuntime().freeMemory();

        // items
        pane.append(newItem("Platform", cz.kruch.track.TrackingMIDlet.getPlatform()));
        final StringBuffer sb = new StringBuffer(32);
        sb.append(totalMemory).append('/').append(freeMemory);
        pane.append(newItem("Memory", sb.toString()));
        sb.delete(0, sb.length());
        if (File.fsType == File.FS_JSR75 || File.fsType == File.FS_SXG75)
            sb.append("75 ");
        if (cz.kruch.track.TrackingMIDlet.jsr82)
            sb.append("82 ");
        if (cz.kruch.track.TrackingMIDlet.jsr120)
            sb.append("120 ");
        if (cz.kruch.track.TrackingMIDlet.jsr135)
            sb.append("135 ");
        if (cz.kruch.track.TrackingMIDlet.jsr179)
            sb.append("179 ");
        if (cz.kruch.track.TrackingMIDlet.jsr234)
            sb.append("234 ");
        pane.append(newItem("ExtraJsr", sb.toString()));
        if (cz.kruch.track.TrackingMIDlet.getFlags() != null) {
            pane.append(newItem("AppFlags", cz.kruch.track.TrackingMIDlet.getFlags()));
        }
        sb.delete(0, sb.length()).append(System.getProperty("microedition.locale")).append(' ').append(System.getProperty("microedition.encoding"));
        pane.append(newItem("I18n", sb.toString()));
        sb.delete(0, sb.length()).append(File.fsType).append("; resetable? ").append(cz.kruch.track.maps.Map.fileInputStreamResetable).append("; network stream? ").append(cz.kruch.track.maps.Map.networkInputStreamAvailable);
        pane.append(newItem("Fs", sb.toString()));
        sb.delete(0, sb.length()).append(cz.kruch.track.ui.nokia.DeviceControl.getName());
        sb.append(' ').append(cz.kruch.track.ui.nokia.DeviceControl.getGsmCellId());
        sb.append('/').append(cz.kruch.track.ui.nokia.DeviceControl.getGsmLac());
        pane.append(newItem("DeviceCtrl", sb.toString()));
        sb.delete(0, sb.length()).append(cz.kruch.track.TrackingMIDlet.hasPorts()).append("; ").append(System.getProperty("microedition.commports"));
        pane.append(newItem("Ports", sb.toString()));
        sb.delete(0, sb.length()).append(TimeZone.getDefault().getID()).append("; ").append(TimeZone.getDefault().useDaylightTime()).append("; ").append(TimeZone.getDefault().getRawOffset());
        pane.append(newItem("TimeZone", sb.toString()));
        sb.delete(0, sb.length()).append("safe renderer? ").append(Config.S60renderer).append("; hasRepeatEvents? ").append(Desktop.screen.hasRepeatEvents()).append("; hasPointerEvents? ").append(Desktop.screen.hasPointerEvents()).append("; ").append(Desktop.screen.getWidth()).append('x').append(Desktop.screen.getHeight()).append("; skips? ").append(Desktop.skips);
        pane.append(newItem("Desktop", sb.toString()));
        if (map == null) {
            pane.append(newItem("Map", ""));
        } else {
            sb.delete(0, sb.length()).append("datum: ").append(map.getDatum()).append("; projection: ").append(map.getProjection());
            pane.append(newItem("Map", sb.toString()));
        }
        sb.delete(0, sb.length()).append((ps == null ? "" : ps.toString()))
                .append("; stalls=").append(api.location.LocationProvider.stalls)
                .append("; restarts=").append(api.location.LocationProvider.restarts)
                .append("; syncs=").append(api.location.LocationProvider.syncs)
                .append("; mismatches=").append(api.location.LocationProvider.mismatches)
                .append("; checksums=").append(api.location.LocationProvider.checksums)
                .append("; errors=").append(api.location.LocationProvider.errors)
                .append("; pings=").append(api.location.LocationProvider.pings)
                .append("; maxavail=").append(api.location.LocationProvider.maxavail);
        pane.append(new StringItem("ProviderStatus", sb.toString()));
        if (le != null) {
            pane.append(new StringItem("ProviderError", le.toString()));
        }
        if (te != null) {
            pane.append(new StringItem("TracklogError", te.toString()));
        }
        sb.delete(0, sb.length());
        sb.append("pauses: ").append(cz.kruch.track.TrackingMIDlet.pauses)
                .append("; uncaught: ").append(SmartRunnable.uncaught)
                .append("; mergedRT: ").append(SmartRunnable.mergedRT)
                .append("; mergedKT: ").append(SmartRunnable.mergedKT);
        pane.append(newItem("Debug", sb.toString()));
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command.getCommandType() == Desktop.BACK_CMD_TYPE) {
            // gc hint
            this.le = null;
            this.te = null;
            this.ps = null;
            this.map = null;
            // restore desktop UI
            Desktop.display.setCurrent(Desktop.screen);
        } else {
            // form
            final Form pane = (Form) displayable;
            // delete basic info
            pane.deleteAll();
            // remove 'Details' command
            pane.removeCommand(command);
            // show technical details
            details(pane, le, te, ps, map);
        }
    }

    private static StringItem newItem(final String label, final String text) {
        final StringItem item = new StringItem(label, text);
        item.setLayout(Item.LAYOUT_2 | Item.LAYOUT_NEWLINE_AFTER);
        return item;
    }
}
