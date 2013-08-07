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

    private Object[] extras;
    private Map map;

    InfoForm() {
    }

    public void show(Desktop desktop, Map map, Object[] extras) {
        // members
        this.map = map;
        this.extras = extras;

        // items
        final Form pane = new Form(Resources.prefixed(Resources.getString(Resources.DESKTOP_CMD_INFO)));
        pane.append(newItem(Resources.getString(Resources.INFO_ITEM_VENDOR), Resources.getString(Resources.INFO_ITEM_VENDOR_VALUE), Item.HYPERLINK));
        pane.append(newItem(Resources.getString(Resources.INFO_ITEM_VERSION), cz.kruch.track.TrackingMIDlet.version));
        pane.append(newItem(Resources.getString(Resources.INFO_ITEM_KEYS), ""));
        pane.append(newItem(null, Resources.getString((short) (Resources.INFO_ITEM_KEYS_MS + desktop.getMode()))));
        pane.addCommand(new Command(Resources.getString(Resources.INFO_CMD_DETAILS), Desktop.POSITIVE_CMD_TYPE, 0));
        pane.addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Desktop.BACK_CMD_TYPE, 1));
        pane.setCommandListener(this);

        // show
        Desktop.display.setCurrent(pane);
    }

    private void details(final Form pane) {
        // gc - for memory info to be correct...
//#if __SYMBIAN__ || __RIM__ || __ANDROID__ || __CN1__
        // GC is useless
//#else
        System.gc(); // unconditional!!!
//#endif
//#ifndef __CN1__
        final long freeMemory = Runtime.getRuntime().freeMemory();
        final long totalMemory = Runtime.getRuntime().totalMemory();
//#endif

        // items
        pane.append(newItem("Platform", cz.kruch.track.TrackingMIDlet.getPlatform()));
//#ifdef __ANDROID__
        pane.append(newItem("Build", android.os.Build.MANUFACTURER + "|" + android.os.Build.MODEL + "|" + android.os.Build.VERSION.RELEASE));
//#endif
        final StringBuffer sb = new StringBuffer(32);
//#ifndef __CN1__
        sb.append(totalMemory).append('/').append(freeMemory);
        pane.append(newItem("Memory", sb.toString()));
//#endif
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
/*
        if (cz.kruch.track.TrackingMIDlet.jsr256)
            sb.append("256 ");
*/
        pane.append(newItem("Jsr", sb.toString()));
        if (cz.kruch.track.TrackingMIDlet.getFlags() != null) {
            pane.append(newItem("AppFlags", cz.kruch.track.TrackingMIDlet.getFlags()));
        }
        sb.delete(0, sb.length())
                .append(System.getProperty("microedition.locale"))
                .append(' ').append(System.getProperty("microedition.encoding"));
        pane.append(newItem("I18n", sb.toString()));
        sb.delete(0, sb.length())
                .append(TimeZone.getDefault().getID())
                .append("; ").append(TimeZone.getDefault().useDaylightTime())
                .append("; ").append(TimeZone.getDefault().getRawOffset());
        pane.append(newItem("TimeZone", sb.toString()));
        sb.delete(0, sb.length())
                .append(cz.kruch.track.ui.nokia.DeviceControl.getName())
//#ifndef __ANDROID__
                .append(' ').append(System.getProperty("com.nokia.mid.ui.version"))
                .append('/').append(System.getProperty("com.nokia.mid.ui.layout"))
//#endif
                .append(" (").append(cz.kruch.track.ui.nokia.DeviceControl.getLevel()).append(')');
//#ifndef __ANDROID__
        if (Config.gpxGsmInfo) {
            sb.append(' ').append(cz.kruch.track.ui.nokia.DeviceControl.getGsmCellId());
            sb.append('/').append(cz.kruch.track.ui.nokia.DeviceControl.getGsmLac());
        }
//#endif
        pane.append(newItem("DeviceCtrl", sb.toString()));
        sb.delete(0, sb.length())
                .append(File.fsType)
                .append("; resetable? ").append(cz.kruch.track.maps.Map.fileInputStreamResetable)
                .append("; buffer size: ").append(cz.kruch.track.configuration.Config.inputBufferSize)
                .append("; fsavail? ").append(cz.kruch.track.configuration.Config.filesizeAvail)
                .append("; input class: ").append(cz.kruch.track.maps.Map.fileInputStreamClass)
//#ifdef __SYMBIAN__
                .append("; network stream? ").append(Config.useNativeService && cz.kruch.track.maps.Map.networkInputStreamAvailable)
//#endif
                .append("; card: ").append(System.getProperty("fileconn.dir.memorycard"));
        pane.append(newItem("Fs", sb.toString()));
//#ifndef __ANDROID__
        pane.append(newItem("Orientation", cz.kruch.track.ui.nokia.DeviceControl.getSensorStatus()));
//#endif
        sb.delete(0, sb.length())
                .append(Desktop.width).append('x').append(Desktop.height)
//#ifdef __ANDROID__
                .append("; dpi? ").append((int)DeviceScreen.xdpi).append('/')
                                  .append((int)DeviceScreen.ydpi)
//#endif
//#ifdef __ALT_RENDERER__
                .append("; safe renderer? ").append(Config.S60renderer)
//#endif                
                .append("; hasRepeatEvents? ").append(Desktop.screen.hasRepeatEvents())
                .append("; hasPointerEvents? ").append(Desktop.screen.hasPointerEvents())
                .append("; isDoubleBuffered? ").append(Desktop.screen.isDoubleBuffered())
                .append("; skips? ").append(Desktop.skips);
        pane.append(newItem("Desktop", sb.toString()));
        if (map == null) {
            pane.append(newItem("Map", ""));
        } else {
            sb.delete(0, sb.length())
                    .append(map.getName())
                    .append("; datum: ").append(map.getDatum())
                    .append("; projection: ").append(map.getProjection())
                    .append("; tmi/tmc? ");
            map.isTmx(sb);
//#ifdef __BUILDER__
            sb.append("; checksum: ").append(Integer.toHexString(cz.kruch.track.io.CrcInputStream.getChecksum()));
//#endif
            pane.append(newItem("Map", sb.toString()));
        }
        sb.delete(0, sb.length())
                .append((extras[0] == null ? "" : extras[0].toString()))
                .append("; st=").append(api.location.LocationProvider.stalls)
                .append("; rs=").append(api.location.LocationProvider.restarts)
                .append("; sc=").append(api.location.LocationProvider.syncs)
                .append("; ms=").append(api.location.LocationProvider.mismatches)
                .append("; mf=").append(api.location.LocationProvider.invalids)
                .append("; ch=").append(api.location.LocationProvider.checksums)
                .append("; er=").append(api.location.LocationProvider.errors)
                .append("; pg=").append(api.location.LocationProvider.pings)
                .append("; mx=").append(api.location.LocationProvider.maxavail)
//#ifdef __RIM50__
                .append("; bbs=").append(cz.kruch.track.location.Jsr179LocationProvider.bbStatus)
                .append("; bbe=").append(cz.kruch.track.location.Jsr179LocationProvider.bbError)
//#endif                
                ;
        pane.append(newItem("ProviderStatus", sb.toString()));
        if (extras[1] != null) {
            pane.append(newItem("ProviderError", extras[1].toString()));
        }
        if (extras[2] != null) {
            pane.append(newItem("TracklogError", extras[2].toString()));
        }
//#ifdef __ANDROID__
        pane.append(newItem("BtSocketType", cz.kruch.track.location.AndroidBluetoothLocationProvider.sockType));
//#ifndef __BACKPORT__
        sb.delete(0, sb.length())
                .append("supported? ").append(cz.kruch.track.sensor.ANTPlus.isSupported());
        pane.append(newItem("ANT+", sb.toString()));
//#endif
//#endif
        sb.delete(0, sb.length())
                .append(cz.kruch.track.fun.Camera.type)
                .append("; encodings: ").append(System.getProperty("video.snapshot.encodings"))
                .append("; resolutions: ").append(System.getProperty("camera.resolutions"));
        pane.append(newItem("Camera", sb.toString()));
        if (cz.kruch.track.fun.Camera.state != null) {
            pane.append(newItem("Camera", cz.kruch.track.fun.Camera.state.toString()));
        }
        if (cz.kruch.track.fun.Playback.state != null) {
            pane.append(newItem("Playback", cz.kruch.track.fun.Playback.state.toString()));
        }
        sb.delete(0, sb.length())
                .append(cz.kruch.track.TrackingMIDlet.hasPorts())
                .append("; ").append(System.getProperty("microedition.commports"));
        pane.append(newItem("Ports", sb.toString()));
        sb.delete(0, sb.length())
                .append("pauses: ").append(cz.kruch.track.TrackingMIDlet.pauses)
                .append("; uncaught: ").append(SmartRunnable.uncaught)
                .append("; maxtasks: ").append(SmartRunnable.maxQT)
                .append("; merged: ").append(SmartRunnable.mergedRT).append('/').append(SmartRunnable.mergedKT)
                .append("; events: ").append(Desktop.getEventWorker().getQueueSize()).append('/').append(Desktop.getDiskWorker().getQueueSize());
        pane.append(newItem("Debug", sb.toString()));
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command.getCommandType() == Desktop.BACK_CMD_TYPE) {
            // gc hints
            this.map = null;
            this.extras = null;
            // restore desktop UI
            Desktop.restore(displayable);
        } else {
//#ifndef __CN1__
            // form
            final Form pane = (Form) displayable;
            // delete basic info
            pane.deleteAll();
            // remove 'Details' command
            pane.removeCommand(command);
            // show technical details
            details(pane);
//#else
            // reuse does not work well in CN1 due to paint events cummulation
            final Form pane = new Form(Resources.prefixed(Resources.getString(Resources.DESKTOP_CMD_INFO)));
            details(pane);
            pane.addCommand(new Command(Resources.getString(Resources.CMD_CLOSE), Desktop.BACK_CMD_TYPE, 1));
            pane.setCommandListener(this);
            Desktop.display.setCurrent(pane);
//#endif
        }
    }

    private static StringItem newItem(final String label, final String text) {
        return newItem(label, text, Item.PLAIN);
    }

    private static StringItem newItem(final String label, final String text,
                                      final int appearance) {
        final StringItem item = new StringItem(label != null ? label + ": " : null, text, appearance);
        item.setLayout(Item.LAYOUT_2 | Item.LAYOUT_NEWLINE_AFTER);
        item.setFont(Desktop.fontStringItems);
        return item;
    }
}
